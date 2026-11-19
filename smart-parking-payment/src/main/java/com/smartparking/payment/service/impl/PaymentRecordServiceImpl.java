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
     * 服务间内部令牌：网关对 /api/**&#47;internal/** 强制校验该 Header，
     * 缺省值与网关 gateway.internal-token 保持一致，生产用 SP_INTERNAL_TOKEN 统一覆盖。
     */
    @Value("${smart-parking.internal-token:SmartParking-Internal-2026}")
    private String internalToken;

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
            java.math.BigDecimal ownerRatio = new java.math.BigDecimal("0.7");
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
