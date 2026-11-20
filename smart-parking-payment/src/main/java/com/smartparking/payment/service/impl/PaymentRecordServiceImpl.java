package com.smartparking.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.payment.client.AuditClient;
import com.smartparking.common.core.constant.Constants;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.payment.mapper.PaymentRecordMapper;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.po.ShareIncome;
import com.smartparking.payment.query.PaymentRecordQuery;
import com.smartparking.payment.service.PaymentRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 支付记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecordServiceImpl extends ServiceImpl<PaymentRecordMapper, PaymentRecord> implements PaymentRecordService {

    private final RabbitTemplate rabbitTemplate;
    private final AuditClient auditClient;
    private final com.smartparking.payment.mapper.ShareIncomeMapper shareIncomeMapper;
    private final com.smartparking.payment.channel.alipay.AlipaySandboxService alipaySandboxService;

    private static final DateTimeFormatter PAY_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 网关地址（用于直连通知订单服务确认支付，兜底MQ） */
    @Value("${smart-parking.gateway-url:http://localhost:8080}")
    private String gatewayUrl;

    /**
     * 服务间内部令牌：网关对 /api/**&#47;internal/** 强制校验该 Header。
     *
     * <p>占位符必须与 {@code InternalApiGuardConfig} 完全一致（同样优先 SP_INTERNAL_TOKEN）：
     * 否则只配环境变量时，守门器取到新令牌而本类仍用旧默认值，内部调用恒 403。
     */
    @Value("${SP_INTERNAL_TOKEN:${smart-parking.internal-token:" + Constants.DEFAULT_INTERNAL_TOKEN + "}}")
    private String internalToken;

    /**
     * 共享车位分润比例（业主分成占比），物业占比 = 1 - 该值。
     *
     * <p>小区按 7:3 分成，但不同小区的谈定比例不同，写死会导致换场地必须改代码重新发版。
     * 配置非法（≤0、≥1、非数字）时回退 {@link #DEFAULT_OWNER_SHARE_RATIO} 并告警，
     * 而不是抛错：分润失败虽不阻断支付，但会造成静默漏账，运维更需要看到一条明确的 WARN。
     */
    @Value("${smart-parking.share.owner-ratio:0.7}")
    private BigDecimal ownerShareRatio;

    /** 分润比例的兜底值，与历史数据保持一致，避免配置手误后业主分成金额突变 */
    static final BigDecimal DEFAULT_OWNER_SHARE_RATIO = new BigDecimal("0.7");

    /**
     * 核销成功后是否联动开闸（默认开）。关掉后支付只入账不动闸，
     * 适合「先缴费后人工放行」或闸机尚未接入的场地。
     */
    @Value("${smart-parking.gate.release-on-payment:true}")
    private boolean releaseGateOnPayment;

    /**
     * 开闸线程池：指令要等设备 ACK（默认 1.5s 超时），不能占用支付确认线程 ——
     * H5 轮询 /alipay/query 期望的是"到账了没有"，不该被闸机回执拖慢。
     */
    private static final java.util.concurrent.ExecutorService GATE_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "payment-gate-release");
                thread.setDaemon(true);
                return thread;
            });

    @jakarta.annotation.PreDestroy
    void shutdownGateExecutor() {
        GATE_EXECUTOR.shutdown();
    }

    private final RestTemplate restTemplate = buildRestTemplate();

    /**
     * 内部调用一律带上 X-Internal-Token，否则会被网关以 403「内部接口禁止外部访问」拦截，
     * 表现为支付创建恒返回 5402「订单服务暂不可用」。
     * 拦截器内延迟读取 internalToken，保证 @Value 注入完成后再取值。
     */
    private org.springframework.web.client.RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        org.springframework.web.client.RestTemplate template =
                new org.springframework.web.client.RestTemplate(factory);
        template.getInterceptors().add((request, body, execution) -> {
            if (internalToken != null && !internalToken.isBlank()) {
                request.getHeaders().set(Constants.INTERNAL_TOKEN_HEADER, internalToken);
            }
            return execution.execute(request, body);
        });
        return template;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord createPayment(Long orderId, Long userId, BigDecimal amount, Integer payType) {
        // 校验订单状态：订单必须存在、未支付、未退款（防止对已退款/已支付订单重复发起支付）
        try {
            String url = gatewayUrl + "/api/order/internal/order-info?orderId=" + orderId;
            @SuppressWarnings("unchecked")
            Map<String, Object> orderInfo = restTemplate.getForObject(url, Map.class);
            if (orderInfo == null || orderInfo.get("data") == null) {
                throw new BizException(ErrorCode.ORDER_NOT_FOUND);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) orderInfo.get("data");
            if (data.get("id") == null) {
                throw new BizException(ErrorCode.ORDER_NOT_FOUND);
            }
            int orderStatus = data.get("status") == null ? 0 : ((Number) data.get("status")).intValue();
            int payStatus = data.get("payStatus") == null ? 0 : ((Number) data.get("payStatus")).intValue();
            if (payStatus == 2) { // 已退款
                throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单已退款，禁止再次支付");
            }
            if (payStatus == 1) { // 已支付
                throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单已支付，请勿重复支付");
            }
            if (orderStatus == 2) { // 已取消
                throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "订单已取消，无法支付");
            }
            // 金额以订单应付金额为准，忽略前端传值（E2E 报告 P0-5：此前任意 amount 均可支付成功）
            BigDecimal orderAmount = data.get("amount") == null ? null
                    : new BigDecimal(String.valueOf(data.get("amount")));
            if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
                // 0 元/负金额订单（免费时长内出场）不生成流水，直接拒绝
                throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单无需支付");
            }
            amount = orderAmount;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("校验订单状态失败 orderId={}：{}", orderId, e.getMessage());
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单服务暂不可用，请稍后再试");
        }

        // 幂等（报告 P1-7）：同一订单已有待支付流水时直接复用，避免双击/重试生成多笔
        // 可各自 mockPay 成功的流水，形成"一笔订单扣多次"的入口。
        PaymentRecord pending = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .eq(PaymentRecord::getPayStatus, 0)
                .orderByDesc(PaymentRecord::getCreateTime)
                .last("LIMIT 1"));
        if (pending != null) {
            log.info("订单 {} 已有待支付流水 {}，复用", orderId, pending.getPayNo());
            return pending;
        }

        PaymentRecord record = new PaymentRecord();
        record.setPayNo(generatePayNo());
        record.setBizType(Constants.BIZ_TYPE_PARKING_ORDER);
        record.setBizId(orderId);
        record.setOrderId(orderId);
        record.setUserId(userId);
        record.setAmount(amount);
        record.setPayType(payType);
        record.setPayStatus(0); // 待支付

        this.save(record);

        // 操作审计：发起支付
        auditClient.record(userId, null, "发起支付", "POST /api/payment/create",
                java.util.Map.of("payNo", record.getPayNo(), "orderId", orderId,
                        "amount", amount, "payType", payType), true);
        return record;
    }

    /**
     * 创建月卡支付流水。
     *
     * <p><b>为什么必须走这里而不是 vehicle 自己置成功</b>：月卡是小区主体收入，
     * 此前 vehicle 的 {@code /monthly/pay} 自造 {@code MOCK-} 流水直接置已支付，
     * 不产生 payment_record —— 收入统计（只 SUM payment_record）、发票（按
     * payment_record.orderId 找流水）、对账三条链路全部漏账。纳入本表后，
     * 「钱」只有一个记账口径。
     *
     * <p>金额与归属以车辆服务的月卡订单为权威（忽略前端传的 amount），
     * 与停车订单同一原则：支付服务的职责是收钱，不是定价。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord createMonthlyPayment(Long monthlyOrderId, Long userId, Integer payType) {
        if (monthlyOrderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少月卡订单ID");
        }
        BigDecimal amount;
        Long ownerId;
        try {
            String url = gatewayUrl + "/api/vehicle/internal/monthly/info?monthlyOrderId=" + monthlyOrderId;
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(url, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = resp == null || !(resp.get("data") instanceof Map)
                    ? null : (Map<String, Object>) resp.get("data");
            if (data == null || data.get("id") == null) {
                throw new BizException(ErrorCode.ORDER_NOT_FOUND, "月卡订单不存在");
            }
            int payStatus = data.get("payStatus") == null ? 0 : ((Number) data.get("payStatus")).intValue();
            if (payStatus == Constants.PAY_STATUS_PAID) {
                throw new BizException(ErrorCode.PAY_STATUS_ERROR, "该月卡订单已支付，请勿重复支付");
            }
            if (payStatus == Constants.PAY_STATUS_CLOSED) {
                throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "该月卡订单已取消");
            }
            amount = data.get("amount") == null ? null : new BigDecimal(String.valueOf(data.get("amount")));
            if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCode.PAY_STATUS_ERROR, "月卡订单金额异常，无法支付");
            }
            ownerId = data.get("userId") == null ? null : ((Number) data.get("userId")).longValue();
            // 业主只能为自己的月卡付款（管理员代付时 userId 为 null，不受此限）
            if (userId != null && ownerId != null && !userId.equals(ownerId)) {
                throw new BizException(ErrorCode.FORBIDDEN, "只能为自己的月卡订单付款");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("校验月卡订单失败 monthlyOrderId={}：{}", monthlyOrderId, e.getMessage());
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "车辆服务暂不可用，请稍后再试");
        }

        // 幂等：同一月卡订单已有待支付流水时复用，避免双击生成多笔可各自成功的流水
        PaymentRecord pending = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getBizType, Constants.BIZ_TYPE_MONTHLY_CARD)
                .eq(PaymentRecord::getBizId, monthlyOrderId)
                .eq(PaymentRecord::getPayStatus, Constants.PAY_STATUS_UNPAID)
                .orderByDesc(PaymentRecord::getCreateTime)
                .last("LIMIT 1"));
        if (pending != null) {
            log.info("月卡订单 {} 已有待支付流水 {}，复用", monthlyOrderId, pending.getPayNo());
            return pending;
        }

        PaymentRecord record = new PaymentRecord();
        record.setPayNo(generatePayNo());
        record.setBizType(Constants.BIZ_TYPE_MONTHLY_CARD);
        record.setBizId(monthlyOrderId);
        record.setOrderId(null);   // 月卡不属于停车订单，order_id 留空（列已允许 NULL）
        record.setUserId(userId != null ? userId : ownerId);
        record.setAmount(amount);
        record.setPayType(payType);
        record.setPayStatus(Constants.PAY_STATUS_UNPAID);
        this.save(record);

        auditClient.record(record.getUserId(), null, "发起月卡支付", "POST /api/payment/create",
                java.util.Map.of("payNo", record.getPayNo(), "monthlyOrderId", monthlyOrderId,
                        "amount", amount, "payType", payType), true);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paymentSuccess(String payNo, String transactionId) {
        PaymentRecord record = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPayNo, payNo));
        if (record == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        if (record.getPayStatus() == 1) {
            log.warn("支付记录{}已成功，重复回调", payNo);
            return;
        }

        // 条件更新实现幂等（报告 P0-9）：并发回调时只有一个事务能命中下面的状态集合，
        // 另一个 affected=0 直接返回，避免重复分润与重复通知订单服务。
        //
        // 关于 CLOSED(3)：PAY_STATUS_CLOSED 只是本地"超时关单"标记（渠道侧并未关单，
        // 用户在超时边界付款完全可能）。渠道回调 = 钱已到账的事实，优先级必须高于本地
        // 超时判断，否则会出现"用户付了钱、系统记 0 元/记关闭"的掉单。因此这里允许
        // 0/3/4 → 1；只把 PAID(1) 与 REFUNDED(2) 挡在外面（已结算 / 已退款不可回跳）。
        boolean fromClosed = record.getPayStatus() != null && record.getPayStatus() == Constants.PAY_STATUS_CLOSED;
        boolean paid = this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<PaymentRecord>()
                .eq(PaymentRecord::getId, record.getId())
                .in(PaymentRecord::getPayStatus, Constants.PAY_STATUS_UNPAID,
                        Constants.PAY_STATUS_CLOSED, Constants.PAY_STATUS_FAILED)
                .set(PaymentRecord::getPayStatus, Constants.PAY_STATUS_PAID)
                .set(PaymentRecord::getTransactionId, transactionId)
                .set(PaymentRecord::getPayTime, LocalDateTime.now()));
        if (!paid) {
            PaymentRecord latest = this.getById(record.getId());
            if (latest != null && latest.getPayStatus() == 1) {
                log.warn("支付记录{}已被并发回调置为成功，跳过", payNo);
                return;
            }
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "支付记录状态已变更，无法置为成功");
        }
        if (fromClosed) {
            log.warn("支付记录{}本地已超时关单，但收到渠道成功回调，按渠道为准置为已支付", payNo);
        }

        // 操作审计：支付成功
        auditClient.record(record.getUserId(), null, "支付成功", "POST /api/payment/{channel}/pay",
                java.util.Map.of("payNo", payNo, "transactionId", transactionId,
                        "amount", record.getAmount()), true);

        // 月卡：只需通知车辆服务续期，不走订单确认 / MQ / 分润 / 开闸（月卡不对应停车订单）
        if (isMonthlyBiz(record.getBizType())) {
            confirmMonthlyPaid(record, transactionId);
            return;
        }

        // 发送MQ消息通知订单服务确认支付
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", record.getOrderId());
            message.put("payNo", payNo);
            message.put("userId", record.getUserId());
            message.put("amount", record.getAmount());
            message.put("type", "PAYMENT_SUCCESS");
            rabbitTemplate.convertAndSend("parking.payment.exchange", "payment.success", message);
        } catch (Exception e) {
            log.error("发送支付成功MQ消息失败：{}", e.getMessage(), e);
        }

        // 直连网关通知订单服务确认支付（兜底，保证演示闭环可靠）
        try {
            String url = gatewayUrl + "/api/order/internal/confirm-payment"
                    + "?orderId=" + record.getOrderId()
                    + "&amount=" + record.getAmount();
            restTemplate.postForEntity(url, HttpEntity.EMPTY, String.class);
            log.info("已通知订单服务确认支付：orderId={}, amount={}", record.getOrderId(), record.getAmount());
        } catch (Exception e) {
            log.error("通知订单服务支付成功失败：{}", e.getMessage(), e);
        }

        // 共享车位分润：如果该车位是共享车位（业主长期不用开放），将临停费按7:3分给业主
        try {
            handleShareIncome(record);
        } catch (Exception e) {
            log.error("分润计算失败：{}", e.getMessage());
        }

        // 支付闭环最后一跳：进入设备厂商通道开闸（出口扫码付场景）
        releaseGateOnPaid(record);
    }

    /**
     * 是否月卡业务流水。
     *
     * <p>这行判断就是「钱的两条去向」的分岔口：判错会把月卡流水当成停车订单，
     * 拿 {@code orderId=null} 去调 order 的确认 / 开闸 / 分润，全链路空转且月卡不生效。
     * 抽成静态方法是为了被单测直接锁定，不依赖整个 service。
     */
    static boolean isMonthlyBiz(Integer bizType) {
        return Integer.valueOf(Constants.BIZ_TYPE_MONTHLY_CARD).equals(bizType);
    }

    /**
     * 月卡到账后通知车辆服务续期。
     *
     * <p>与开闸相反，这里失败<b>必须抛出</b>：开闸失败时钱已收、车已在出口，记日志放行是对的；
     * 月卡续期失败时钱已收、卡未生效，如果也吞掉异常，用户会看到「付了钱但月卡没到账」且系统
     * 毫无感知。抛出让本地事务回滚 —— 流水保持待支付，渠道的 {@code query} 或回调重试会
     * 再次触发一次续期（钱在渠道，可无限重试），比"记成功但没生效"可恢复得多。
     */
    private void confirmMonthlyPaid(PaymentRecord record, String transactionId) {
        String url = gatewayUrl + "/api/vehicle/internal/monthly/mark-paid"
                + "?monthlyOrderId=" + record.getBizId()
                + "&payNo=" + record.getPayNo()
                + "&transactionId=" + transactionId;
        try {
            restTemplate.postForEntity(url, HttpEntity.EMPTY, String.class);
            log.info("月卡续期成功：monthlyOrderId={}, payNo={}", record.getBizId(), record.getPayNo());
        } catch (Exception e) {
            log.error("月卡续期失败（本地流水保持待支付，等待渠道重试）monthlyOrderId={}, payNo={}：{}",
                    record.getBizId(), record.getPayNo(), e.getMessage());
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "月卡续期失败，请稍后重试");
        }
    }

    /**
     * 核销成功后联动开闸：payment → 网关 → device 内部接口 → GateAdapter（厂商通道）。
     *
     * <p>三个约束：
     * <ol>
     *   <li><b>必须在本地事务提交后</b>再下发 —— 否则闸机抬杆了而支付记录回滚，
     *       就成了"免费放行"；</li>
     *   <li><b>异步执行</b> —— 设备不回执时要等满 ack-timeout，同步会拖慢支付确认接口；</li>
     *   <li><b>任何失败都不外抛</b> —— 钱已经到账，开闸失败只能留日志由运维处理，
     *       绝不能让它影响入账结果。</li>
     * </ol>
     */
    private void releaseGateOnPaid(PaymentRecord record) {
        if (!releaseGateOnPayment) {
            log.info("支付联动开闸已关闭（smart-parking.gate.release-on-payment=false），跳过 payNo={}",
                    record.getPayNo());
            return;
        }
        Runnable task = () -> {
            try {
                String url = gatewayUrl + "/api/device/internal/gate/release"
                        + "?payNo=" + record.getPayNo()
                        + "&orderId=" + record.getOrderId()
                        + "&reason=" + java.net.URLEncoder.encode(
                                "支付成功放行", java.nio.charset.StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.postForObject(
                        java.net.URI.create(url), HttpEntity.EMPTY, Map.class);
                Object dataObj = resp == null ? null : resp.get("data");
                @SuppressWarnings("unchecked")
                Map<String, Object> data = dataObj instanceof Map ? (Map<String, Object>) dataObj : null;
                if (data != null && Boolean.TRUE.equals(data.get("success"))) {
                    log.info("[支付联动开闸] 成功 payNo={}, orderId={}, device={}, vendor={}, transport={}, 回执={}",
                            record.getPayNo(), record.getOrderId(), data.get("deviceNo"),
                            data.get("vendor"), data.get("transport"), data.get("gateAck"));
                } else {
                    log.warn("[支付联动开闸] 设备通道未受理 payNo={}, 返回={}", record.getPayNo(), data);
                }
            } catch (Exception e) {
                log.warn("[支付联动开闸] 调用设备服务失败（不影响入账）payNo={}：{}",
                        record.getPayNo(), e.getMessage());
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            GATE_EXECUTOR.submit(task);
                        }
                    });
        } else {
            GATE_EXECUTOR.submit(task);
        }
    }

    /**
     * 共享车位分润：只有车位 is_shared=1（业主开放共享）时，才按 7:3 记录分润。
     * 分润链路：payment -> order(spaceId) -> parking(isShared/ownerId)，缺一不可，杜绝空壳分润。
     */
    private void handleShareIncome(PaymentRecord record) {
        try {
            // 1. 从订单服务拿车位ID（Result 包装：字段在 data 层）
            @SuppressWarnings("unchecked")
            Map<String, Object> orderInfo = restTemplate.getForObject(
                    gatewayUrl + "/api/order/internal/order-info?orderId=" + record.getOrderId(), Map.class);
            if (orderInfo == null || orderInfo.get("data") == null) {
                log.info("订单 {} 无信息，跳过分润", record.getOrderId());
                return;
            }
            Map<String, Object> orderData = (Map<String, Object>) orderInfo.get("data");
            if (orderData.get("spaceId") == null) {
                log.info("订单 {} 无车位信息，跳过分润", record.getOrderId());
                return;
            }
            Long spaceId = ((Number) orderData.get("spaceId")).longValue();

            // 2. 从车位服务查共享状态与车位业主（Result 包装：字段在 data 层）
            @SuppressWarnings("unchecked")
            Map<String, Object> spaceInfo = restTemplate.getForObject(
                    gatewayUrl + "/api/parking/internal/space-info/" + spaceId, Map.class);
            Map<String, Object> spaceData = null;
            if (spaceInfo != null && spaceInfo.get("data") instanceof Map) {
                spaceData = (Map<String, Object>) spaceInfo.get("data");
            }
            if (spaceData == null || spaceData.get("isShared") == null || !Boolean.TRUE.equals(spaceData.get("isShared"))) {
                log.info("车位 {} 未开启共享，跳过分润", spaceId);
                return;
            }
            if (spaceData.get("ownerId") == null) {
                log.info("共享车位 {} 无归属业主，跳过分润", spaceId);
                return;
            }
            Long ownerId = ((Number) spaceData.get("ownerId")).longValue();
            String spaceNo = String.valueOf(spaceData.get("spaceNo"));

            // 3. 记录真实分润
            BigDecimal ownerRatio = effectiveOwnerShareRatio(ownerShareRatio);
            java.math.BigDecimal[] split = splitShareAmount(record.getAmount(), ownerRatio);
            ShareIncome share = new ShareIncome();
            share.setPaymentId(record.getId());
            share.setSpaceId(spaceId);
            share.setOwnerId(ownerId);
            share.setTotalAmount(record.getAmount());
            share.setOwnerRatio(ownerRatio);
            share.setOwnerAmount(split[0]);
            share.setPropertyAmount(split[1]);
            shareIncomeMapper.insert(share);
            log.info("共享车位分润：paymentId={}, space={}, owner={}, 业主={}, 物业={}",
                    record.getId(), spaceNo, ownerId, share.getOwnerAmount(), share.getPropertyAmount());
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // uk_payment_id 兜底：并发回调重复分润时数据库直接拒绝，属预期行为
            log.warn("分润已存在，跳过重复写入：paymentId={}", record.getId());
        } catch (Exception e) {
            log.warn("分润计算失败（不阻断支付）：orderId={}, err={}", record.getOrderId(), e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean settleFromChannel(String payNo, String transactionId, BigDecimal channelAmount) {
        PaymentRecord record = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPayNo, payNo));
        if (record == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        assertAmountMatches(payNo, record.getAmount(), channelAmount);
        paymentSuccess(payNo, transactionId);
        return true;
    }

    /**
     * 渠道金额一致性校验（资金安全闸门）。
     *
     * <p>渠道回传的实收金额必须与本地应付完全相等。少收（改价攻击）和多收（串单/渠道误操作）
     * 一律拒绝入账并在 ERROR 日志里留下双方金额，由人工对账处理 —— 宁可挡住一笔真实支付，
     * 也不接受账实不符的核销。
     *
     * <p>用 compareTo 而不是 equals：{@code BigDecimal("100")} 与 {@code "100.00"} 数值相等、
     * 标度不同，equals 会误判。
     *
     * <p>抽成静态方法是为了能被单测直接锁定，不需要构造整个 service 的依赖。
     */
    static void assertAmountMatches(String payNo, BigDecimal localAmount, BigDecimal channelAmount) {
        if (channelAmount == null) {
            log.error("[资金安全] 渠道未回传金额，拒绝入账 payNo={}", payNo);
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "渠道未回传支付金额，已拒绝入账");
        }
        if (localAmount == null || localAmount.compareTo(channelAmount) != 0) {
            log.error("[资金安全] 渠道金额与本地应付不一致 payNo={}, 本地={}, 渠道={}",
                    payNo, localAmount, channelAmount);
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "支付金额与订单应付不一致，已拒绝入账");
        }
    }

    /**
     * 分润金额拆分：{@code [业主所得, 物业所得]}。
     *
     * <p><b>必须保证两者之和恒等于总额</b>。若两边各自乘比例再交给数据库按
     * {@code decimal(10,2)} 舍入，会出现账不平：实测总额 0.05 元时
     * {@code 0.035→0.04} 与 {@code 0.015→0.02} 相加为 0.06，凭空多出 1 分钱。
     * 因此业主那份先四舍五入到分，物业那份用「总额 − 业主」反算。
     *
     * <p>抽成静态方法是为了能被单测直接锁定，不需要构造整个 service 的依赖。
     */
    /**
     * 生效的分润比例：配置合法则用配置，否则回退默认值并告警。
     *
     * <p>合法区间是 (0, 1) 开区间 —— 0 表示业主一分不拿、1 表示物业一分不拿，
     * 两者都说明配置写错了（多半是写成百分数 70 而不是 0.7）。
     * 做成静态方法是为了让单测直接验证回退规则，不必启动 Spring 上下文。
     */
    static BigDecimal effectiveOwnerShareRatio(BigDecimal configured) {
        if (configured == null || configured.signum() <= 0
                || configured.compareTo(BigDecimal.ONE) >= 0) {
            log.warn("分润比例配置非法（smart-parking.share.owner-ratio={}），回退默认值 {}；取值应形如 0.7",
                    configured, DEFAULT_OWNER_SHARE_RATIO);
            return DEFAULT_OWNER_SHARE_RATIO;
        }
        return configured;
    }

    static BigDecimal[] splitShareAmount(BigDecimal total, BigDecimal ownerRatio) {
        BigDecimal owner = total.multiply(ownerRatio).setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal[]{owner, total.subtract(owner)};
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paymentFail(String payNo, String reason) {
        PaymentRecord record = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPayNo, payNo));
        if (record == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }

        record.setPayStatus(Constants.PAY_STATUS_FAILED); // 支付失败（独立状态，与已退款2/已关闭3区分）
        this.updateById(record);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(String payNo) {
        PaymentRecord record = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPayNo, payNo));
        if (record == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }
        if (record.getPayStatus() != 1) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "只有成功支付的记录才能退款");
        }

        // 渠道退款先行（支付宝沙箱 payType=2）：渠道退成功后才改本地状态，
        // 避免"本地已退款、渠道未退"形成资金差。渠道侧 out_request_no 固定为 payNo-R1，
        // 因此本地更新失败后用同一笔重试不会重复退款（渠道幂等）。
        if (Integer.valueOf(2).equals(record.getPayType()) && alipaySandboxService.isEnabled()) {
            if (!alipaySandboxService.isReady()) {
                throw new BizException(ErrorCode.PAY_STATUS_ERROR,
                        "支付宝沙箱已开启但密钥未配置，无法原路退款；请补齐配置或关闭 smart-parking.alipay.enabled");
            }
            alipaySandboxService.refund(payNo, record.getAmount());
        }

        // 状态机：未支付0 → 已支付1 → 已退款2（微信/现金通道仍为本地模拟退款）
        record.setPayStatus(2); // 已退款
        this.updateById(record);

        Long orderId = record.getOrderId();
        if (orderId == null) {
            // 月卡流水不关联停车订单：退款只改本表状态并原路退回，
            // 已生效的月卡权益不回滚（可能已使用，属人工处理范畴，不要在这里自动扣减）
            log.warn("支付流水 {} 为月卡业务（bizType={}），跳过订单服务退款回调", payNo, record.getBizType());
            return;
        }
        // 本地事务提交后通知订单服务重置支付状态，避免本地回滚但远端已改
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                restTemplate.postForEntity(gatewayUrl + "/api/order/internal/refund-callback?orderId=" + orderId,
                                        org.springframework.http.HttpEntity.EMPTY, String.class);
                                log.info("退款成功，已通知订单服务重置支付状态 orderId={}", orderId);
                            } catch (Exception e) {
                                log.error("退款后通知订单服务失败 orderId={}, err={}", orderId, e.getMessage());
                            }
                        }
                    });
        }
    }

    @Override
    public Page<PaymentRecord> pageList(Page<PaymentRecord> page, PaymentRecordQuery query) {
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getUserId() != null, PaymentRecord::getUserId, query.getUserId())
                .eq(query.getPayStatus() != null, PaymentRecord::getPayStatus, query.getPayStatus())
                .orderByDesc(PaymentRecord::getCreateTime);
        return this.page(page, wrapper);
    }

    @Override
    public PaymentRecord getByOrderId(Long orderId) {
        return this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .orderByDesc(PaymentRecord::getCreateTime)
                .last("LIMIT 1"));
    }

    @Override
    public PaymentRecord getByPayNo(String payNo) {
        return this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPayNo, payNo));
    }

    @Override
    public BigDecimal sumPaidAmount(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        java.util.List<PaymentRecord> records = this.list(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPayStatus, 1) // 已支付
                .ge(PaymentRecord::getPayTime, start)
                .le(PaymentRecord::getPayTime, end));
        return records.stream()
                .map(PaymentRecord::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String generatePayNo() {
        String timestamp = LocalDateTime.now().format(PAY_NO_FORMAT);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "PAY" + timestamp + random;
    }
}
