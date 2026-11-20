package com.smartparking.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.common.core.constant.Constants;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.order.client.AuditClient;
import com.smartparking.order.client.ParkingSpaceClient;
import com.smartparking.order.client.VehicleClient;
import com.smartparking.order.mapper.ParkingOrderMapper;
import com.smartparking.order.po.BillingRule;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.query.ParkingOrderQuery;
import com.smartparking.order.service.BillingRuleService;
import com.smartparking.order.service.ParkingOrderService;
import com.smartparking.order.service.VisitorInviteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 停车订单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingOrderServiceImpl extends ServiceImpl<ParkingOrderMapper, ParkingOrder> implements ParkingOrderService {

    private final BillingRuleService billingRuleService;
    private final RabbitTemplate rabbitTemplate;
    private final AuditClient auditClient;
    private final ParkingSpaceClient parkingSpaceClient;
    private final VehicleClient vehicleClient;
    /**
     * @Lazy 打破循环依赖（字段注入生效）：VisitorInviteServiceImpl 也依赖 ParkingOrderService，
     * 若放进构造器则 @RequiredArgsConstructor 生成构造参数，字段级 @Lazy 不会传递到构造参数。
     */
    @Autowired
    @Lazy
    private VisitorInviteService visitorInviteService;

    /** 车辆服务地址（设备联动：车牌识别） */
    @Value("${smart-parking.vehicle-url:http://localhost:8083}")
    private String vehicleUrl;

    /** 车位服务地址（设备联动：分配空闲车位） */
    @Value("${smart-parking.parking-url:http://localhost:8082}")
    private String parkingUrl;

    /**
     * 出场信用放行额度（坏账容忍上限）：未支付且应付不超过该值时先抬杆、后付费
     * （出口不堵，欠费由 ArrearageScanTask 催缴/记欠费/拉黑）；超过该值道闸保持关闭。
     * 实际生效值见 {@link #effectiveCreditLimit}：不会低于该订单规则的首个计费单位。
     */
    @Value("${smart-parking.gate.credit-limit-amount:6.00}")
    private BigDecimal creditLimitAmount;

    private final RestTemplate restTemplate = buildRestTemplate();

    /** 车位分配轮询计数器（设备入场均衡分配） */
    private final java.util.concurrent.atomic.AtomicLong spaceAllocateCounter = new java.util.concurrent.atomic.AtomicLong();

    private static RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParkingOrder createEntryOrder(Long vehicleId, String plateNo, Long spaceId, String spaceNo,
                                         Integer vehicleType, String plateColor) {
        // 检查是否有未完成的订单
        ParkingOrder existingOrder = this.getCurrentOrder(vehicleId);
        if (existingOrder != null) {
            throw new BizException(ErrorCode.ORDER_ONGOING_EXISTS);
        }

        // 按车辆类型匹配计费规则：月租车→规则2，访客车→规则0，私家/临时车→规则1（支持新能源颜色）
        int billingType;
        if (vehicleType != null && Integer.valueOf(2).equals(vehicleType)) {
            billingType = 2;       // 月租车计费
        } else if (vehicleType != null && Integer.valueOf(1).equals(vehicleType)) {
            billingType = 0;       // 访客车计费
        } else {
            billingType = 1;       // 临时/私家车标准计费
        }
        BillingRule rule = billingRuleService.getApplicableRule(billingType,
                billingType == 1 ? plateColor : null);
        if (rule == null) {
            throw new BizException(ErrorCode.ORDER_BILLING_RULE_MISSING);
        }

        // 原子抢占车位（仅空闲/预约态可占用），抢不到直接失败，避免并发一车多位
        if (!parkingSpaceClient.claimSpace(spaceId)) {
            throw new BizException(ErrorCode.SPACE_UNAVAILABLE, "车位已被抢占，请重新分配");
        }

        // 创建订单
        ParkingOrder order = new ParkingOrder();
        order.setOrderNo(generateOrderNo());
        order.setVehicleId(vehicleId);
        // 车牌号加密存储（与车辆服务一致），返回时再解密
        order.setPlateNo(AesUtils.encrypt(plateNo));
        order.setSpaceId(spaceId);
        order.setSpaceNo(spaceNo);
        order.setEntryTime(LocalDateTime.now());
        order.setBillingRuleId(rule.getId());
        order.setPayStatus(Constants.PAY_STATUS_UNPAID);
        order.setStatus(Constants.ORDER_STATUS_ONGOING);

        this.save(order);

        // MQ + 审计在本地事务提交后执行；车位已在抢占阶段原子占用，无需再 update
        runAfterCommit(() -> {
            try {
                auditClient.record(currentUserId(), null, "车辆入场", "POST /api/order/entry",
                        java.util.Map.of("orderNo", order.getOrderNo(), "plateNo", AesUtils.maskPlate(plateNo), "spaceNo", spaceNo), true);
            } catch (Exception e) { log.warn("入场审计记录失败: {}", e.getMessage()); }
            Map<String, Object> m = new HashMap<>();
            m.put("orderId", order.getId());
            m.put("orderNo", order.getOrderNo());
            m.put("vehicleId", vehicleId);
            m.put("plateNo", plateNo);
            m.put("spaceId", spaceId);
            // 通知消费者按 spaceNo 拼文案（"入场，停放在 %s"）；缺这个字段会渲染成"停放在 "后面空白
            m.put("spaceNo", spaceNo);
            m.put("entryTime", order.getEntryTime());
            m.put("type", "ENTRY");
            try { rabbitTemplate.convertAndSend("parking.order.exchange", "order.entry", m); }
            catch (Exception e) { log.error("发送入场MQ失败: {}", e.getMessage()); }
        });

        // 返回前解密车牌（DB 中仍为密文）—— E2E 报告 P0-4：入场接口此前直接返回密文
        order.setPlateNo(decryptPlateNo(order));
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ParkingOrder completeOrder(String orderNo) {
        ParkingOrder order = this.getOne(new LambdaQueryWrapper<ParkingOrder>()
                .eq(ParkingOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (Constants.ORDER_STATUS_ONGOING != order.getStatus()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR);
        }

        // 计算停车时长和费用
        LocalDateTime exitTime = LocalDateTime.now();
        long minutes = java.time.Duration.between(order.getEntryTime(), exitTime).toMinutes();
        order.setExitTime(exitTime);
        order.setDuration((int) minutes);

        // 计算费用
        BigDecimal amount = calculateBillingAmount(order, minutes);
        order.setAmount(amount);
        order.setStatus(Constants.ORDER_STATUS_COMPLETED);

        this.updateById(order);

        // 远程操作移到提交后。车牌在 DB 是密文，不同用途要不同形态：
        // MQ 需要明文（notify 按车牌反查车主），审计只留脱敏值（避免密文乱码/明文中台留存）
        Long finalSpaceId = order.getSpaceId();
        LocalDateTime finalExitTime = exitTime;
        long finalMinutes = minutes;
        BigDecimal finalAmount = amount;
        final String finalPlateForMq = decryptPlateNo(order);
        final String finalPlateMasked = AesUtils.maskPlate(finalPlateForMq);
        runAfterCommit(() -> {
            try {
                auditClient.record(currentUserId(), null, "车辆出场", "POST /api/order/exit/{orderNo}",
                        java.util.Map.of("orderNo", order.getOrderNo(), "plateNo", finalPlateMasked,
                                "amount", finalAmount, "duration", finalMinutes), true);
            } catch (Exception e) { log.warn("出场审计失败: {}", e.getMessage()); }
            try { parkingSpaceClient.updateStatus(finalSpaceId, 0); }
            catch (Exception e) { log.error("出场后释放车位失败 spaceId={}, err={}", finalSpaceId, e.getMessage()); }
            Map<String, Object> message = new HashMap<>();
            message.put("orderId", order.getId());
            message.put("orderNo", order.getOrderNo());
            message.put("vehicleId", order.getVehicleId());
            message.put("plateNo", finalPlateForMq);
            message.put("spaceId", finalSpaceId);
            message.put("exitTime", finalExitTime);
            message.put("duration", finalMinutes);
            message.put("amount", finalAmount);
            message.put("type", "EXIT");
            try { rabbitTemplate.convertAndSend("parking.order.exchange", "order.exit", message); }
            catch (Exception e) { log.error("发送出场MQ失败: {}", e.getMessage()); }
        });

        // 返回前端前解密车牌（订单表车牌是 AES 密文存储）
        order.setPlateNo(decryptPlateNo(order));
        return order;
    }

    @Override
    public Page<ParkingOrder> pageList(Page<ParkingOrder> page, ParkingOrderQuery query) {
        LambdaQueryWrapper<ParkingOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getStatus() != null, ParkingOrder::getStatus, query.getStatus())
                .eq(query.getPayStatus() != null, ParkingOrder::getPayStatus, query.getPayStatus());

        // 业主端：按我的车辆ID列表过滤（vehicleIds=1,2,3）
        if (StringUtils.hasText(query.getVehicleIds())) {
            List<Long> vehicleIdList = new ArrayList<>();
            for (String id : query.getVehicleIds().split(",")) {
                try {
                    vehicleIdList.add(Long.parseLong(id.trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            if (!vehicleIdList.isEmpty()) {
                wrapper.in(ParkingOrder::getVehicleId, vehicleIdList);
            }
        }

        wrapper.orderByDesc(ParkingOrder::getCreateTime);

        // 关键字检索下沉到 SQL（报告 P2-12）：只过滤当前页会误导成"查无此单"
        String keyword = query.getKeyword();
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            String encryptedPlate = k.length() >= 6 ? AesUtils.encrypt(k) : null;
            wrapper.and(w -> {
                w.like(ParkingOrder::getOrderNo, k)
                        .or().like(ParkingOrder::getSpaceNo, k);
                if (encryptedPlate != null) {
                    w.or().eq(ParkingOrder::getPlateNo, encryptedPlate);
                }
            });
        }

        Page<ParkingOrder> result = this.page(page, wrapper);
        // 车牌解密后返回前端展示（E2E 报告 P0-4：原方法引用 forEach(ParkingOrderServiceImpl::decryptPlateNo)
        // 把 String 返回值当 Consumer 丢弃，对象密文未被替换）
        result.getRecords().forEach(o -> o.setPlateNo(decryptPlateNo(o)));
        return result;
    }

    /**
     * 从网关透传的请求头获取当前操作人ID（服务间调用时无该头，返回null）
     */
    private Long currentUserId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String value = attrs.getRequest().getHeader("X-User-Id");
                if (StringUtils.hasText(value)) {
                    return Long.parseLong(value);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public ParkingOrder getCurrentOrder(Long vehicleId) {
        ParkingOrder order = this.getOne(new LambdaQueryWrapper<ParkingOrder>()
                .eq(ParkingOrder::getVehicleId, vehicleId)
                .eq(ParkingOrder::getStatus, Constants.ORDER_STATUS_ONGOING));
        if (order != null) {
            order.setPlateNo(decryptPlateNo(order));
        }
        return order;
    }

    @Override
    public ParkingOrder getByOrderNo(String orderNo) {
        ParkingOrder order = this.getOne(new LambdaQueryWrapper<ParkingOrder>()
                .eq(ParkingOrder::getOrderNo, orderNo));
        if (order != null) {
            order.setPlateNo(decryptPlateNo(order));
        }
        return order;
    }

    @Override
    public BigDecimal calculateAmount(String orderNo) {
        ParkingOrder order = this.getOne(new LambdaQueryWrapper<ParkingOrder>()
                .eq(ParkingOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        long minutes = java.time.Duration.between(order.getEntryTime(), LocalDateTime.now()).toMinutes();
        return calculateBillingAmount(order, minutes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmPayment(Long orderId, BigDecimal amount) {
        ParkingOrder order = this.getById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        // 幂等：已支付直接返回
        if (Constants.PAY_STATUS_PAID == order.getPayStatus()) {
            return;
        }
        // 已退款订单禁止再次支付
        if (Constants.PAY_STATUS_REFUNDED == order.getPayStatus()) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单已退款，禁止重复支付");
        }
        // 已取消的订单禁止支付；已结算(待支付)或进行中订单均允许确认支付
        if (Constants.ORDER_STATUS_CANCELLED == order.getStatus()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "订单已取消，无法支付");
        }
        // 金额校验：以服务端结算金额为准，拒绝前端任意传值
        BigDecimal serverAmount = order.getAmount() != null ? order.getAmount()
                : calculateBillingAmount(order, java.time.Duration.between(order.getEntryTime(), LocalDateTime.now()).toMinutes());
        if (amount == null || amount.signum() < 0) {
            throw new BizException(ErrorCode.PAY_AMOUNT_INVALID);
        }
        if (serverAmount != null && serverAmount.compareTo(amount) != 0) {
            log.warn("支付金额不一致：orderId={}, 传入={}, 服务端={}", orderId, amount, serverAmount);
            throw new BizException(ErrorCode.PAY_AMOUNT_INVALID, "支付金额与结算金额不一致");
        }

        // 条件更新防 lost-update（报告 P1-8）：并发「支付回调」与「退款回调」同时到达时，
        // 无条件 updateById 会让后写入者把已退款覆盖回已支付；改成只允许 0/3/4 → 1 的状态迁移。
        boolean updated = this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ParkingOrder>()
                .eq(ParkingOrder::getId, orderId)
                .in(ParkingOrder::getPayStatus, Constants.PAY_STATUS_UNPAID,
                        Constants.PAY_STATUS_CLOSED, Constants.PAY_STATUS_FAILED)
                .set(ParkingOrder::getPayStatus, Constants.PAY_STATUS_PAID)
                .set(ParkingOrder::getPayTime, LocalDateTime.now())
                .set(ParkingOrder::getAmount, serverAmount));
        if (!updated) {
            // 并发下已被别的事务改过状态：重新判定，已支付视为幂等成功，其余拒绝
            ParkingOrder latest = this.getById(orderId);
            if (latest != null && Constants.PAY_STATUS_PAID == latest.getPayStatus()) {
                return;
            }
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单支付状态已变更，请刷新后重试");
        }
    }

    /**
     * 计算停车费用
     *
     * 计费逻辑：
     * 1. 免费时长内不收费
     * 2. 超出免费时长后，按计费单位收费
     * 3. 如果设置了每日封顶，则不超过封顶金额
     */
    private BigDecimal calculateBillingAmount(ParkingOrder order, long totalMinutes) {
        // 报告 P2-7：原实现规则缺失时 `return BigDecimal.ZERO` —— 计费规则被删（外键 SET NULL）
        // 或被误删后，该订单直接按 0 元出场，等于静默免费放行，属资损。改为显式报错：
        // 宁可结算失败让门岗走「人工放行」旁路并留痕，也不能把应收算成 0。
        if (order.getBillingRuleId() == null) {
            throw new BizException(ErrorCode.ORDER_BILLING_RULE_MISSING,
                    "订单缺少计费规则，无法结算（可由门岗走人工放行并事后补录）");
        }
        BillingRule rule = billingRuleService.getById(order.getBillingRuleId());
        if (rule == null) {
            throw new BizException(ErrorCode.ORDER_BILLING_RULE_MISSING,
                    "计费规则已不存在，无法结算（可由门岗走人工放行并事后补录）");
        }

        // 月租车（规则 vehicleType=2）：停在自己承租的固定车位 → 租金已含在月卡里，应收 0 元；
        // 月租车停他人车位（含临停车位）→ 按临停费率计时收费
        if (rule.getMonthlyPrice() != null && Integer.valueOf(2).equals(rule.getVehicleType())) {
            boolean parkedOnOwnSpace = false;
            try {
                Map<String, Object> spaceInfo = parkingSpaceClient.getSpaceInfo(order.getSpaceId());
                Map<String, Object> vehicleInfo = vehicleClient.getByPlateNo(decryptPlateNo(order));
                Long spaceOwner = spaceInfo.get("ownerId") == null ? null
                        : Long.valueOf(String.valueOf(spaceInfo.get("ownerId")));
                Long vehicleOwner = vehicleInfo.get("ownerId") == null ? null
                        : Long.valueOf(String.valueOf(vehicleInfo.get("ownerId")));
                parkedOnOwnSpace = spaceOwner != null && vehicleOwner != null && spaceOwner.equals(vehicleOwner);
            } catch (Exception e) {
                log.warn("月租固定车位判定失败，回退月租价：orderNo={}, err={}", order.getOrderNo(), e.getMessage());
            }
            if (parkedOnOwnSpace) {
                return BigDecimal.ZERO; // 已包月，固定车位不再计费
            }
            // 月租车停非固定车位：继续走下方临停费率
        }

        // 免费时长内不计费
        if (totalMinutes <= rule.getFreeMinutes()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        // 日封顶口径 = **自然日**（00:00 分界），逐日切段累计，每日金额取「当日封顶」的较小值。
        // 报告 P1-10 原实现按「入场时刻起 24h 滚动块」：停 24h30m、日费 117.5、封顶 200 会被收 200。
        // 改为自然日后，跨日订单在 0 点重新起算当日额度，符合业主对「日封顶」的直觉（也是物业合同口径）。
        LocalDateTime cursor = order.getEntryTime() != null ? order.getEntryTime() : LocalDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        long remaining = totalMinutes;
        long freeLeft = rule.getFreeMinutes() == null ? 0 : rule.getFreeMinutes();
        // 未满一个计费单位的余数分钟跨段结转，避免「按日分段」在每个 0 点边界各多收一个单位
        long carryMinutes = 0;

        while (remaining > 0) {
            LocalDateTime dayEnd = cursor.toLocalDate().plusDays(1).atStartOfDay();
            long toDayEnd = java.time.Duration.between(cursor, dayEnd).toMinutes();
            long segMinutes = Math.min(remaining, Math.max(1, toDayEnd));

            long segBillable = segMinutes;
            if (freeLeft > 0) {
                long used = Math.min(freeLeft, segBillable);
                segBillable -= used;
                freeLeft -= used;
            }
            remaining -= segMinutes;

            if (segBillable > 0) {
                // 费率按「该段所在自然日」判定周末：周六入场周一出场不会被整单按周末价收
                java.time.DayOfWeek dow = cursor.getDayOfWeek();
                boolean weekend = dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY;
                BigDecimal segPrice = weekend && rule.getWeekendUnitPrice() != null
                        ? rule.getWeekendUnitPrice() : rule.getUnitPrice();
                BigDecimal segCap = weekend && rule.getWeekendDailyCap() != null
                        ? rule.getWeekendDailyCap() : rule.getDailyCap();

                long pool = carryMinutes + segBillable;
                long units = pool / rule.getUnitDuration();
                carryMinutes = pool % rule.getUnitDuration();
                if (remaining <= 0 && carryMinutes > 0) {
                    // 最后一段不足一个单位也按一个单位收（与「不足一个计费单位按一个单位」口径一致）
                    units++;
                    carryMinutes = 0;
                }
                BigDecimal segAmount = segPrice.multiply(BigDecimal.valueOf(units));
                if (segCap != null) {
                    // 当日封顶只作用于当日金额，超出部分不再向后结转
                    segAmount = segAmount.min(segCap);
                }
                total = total.add(segAmount);
            }
            cursor = cursor.plusMinutes(segMinutes);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 生成订单编号
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(ORDER_NO_FORMAT);
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "ORD" + timestamp + random;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        ParkingOrder order = this.getOne(new LambdaQueryWrapper<ParkingOrder>()
                .eq(ParkingOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        closeOrderAndReleaseSpace(order, "取消订单", "POST /api/order/cancel/{orderNo}",
                "{\"orderNo\":\"" + orderNo + "\"}");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void timeoutClose(Long orderId) {
        ParkingOrder order = this.getById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        closeOrderAndReleaseSpace(order, "支付超时关闭订单", "POST /api/order/internal/payment-timeout",
                "{\"orderId\":" + orderId + "}");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(Long orderId) {
        ParkingOrder order = this.getById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        // 已退款幂等
        if (Constants.PAY_STATUS_REFUNDED == order.getPayStatus()) {
            log.info("订单 {} 已处于退款状态，跳过", order.getOrderNo());
            return;
        }
        // 只有已支付订单才允许退款
        if (Constants.PAY_STATUS_PAID != order.getPayStatus()) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "只有已支付订单才能退款");
        }
        // 退款后订单支付状态置为已退款，禁止再次支付。
        // 条件更新（pay_status=1）避免与并发支付回调互相覆盖（报告 P1-8）
        boolean updated = this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ParkingOrder>()
                .eq(ParkingOrder::getId, orderId)
                .eq(ParkingOrder::getPayStatus, Constants.PAY_STATUS_PAID)
                .set(ParkingOrder::getPayStatus, Constants.PAY_STATUS_REFUNDED)
                .set(ParkingOrder::getPayTime, null));
        if (!updated) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单支付状态已变更，退款未生效");
        }
        log.info("订单 {} 已退款，支付状态置为已退款", order.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaymentClosed(Long orderId) {
        return this.update(new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ParkingOrder>()
                .eq(ParkingOrder::getId, orderId)
                .eq(ParkingOrder::getStatus, Constants.ORDER_STATUS_COMPLETED)
                .eq(ParkingOrder::getPayStatus, Constants.PAY_STATUS_UNPAID)
                .set(ParkingOrder::getPayStatus, Constants.PAY_STATUS_CLOSED));
    }

    @Override
    public Map<String, Object> getOrderInfo(Long orderId) {
        ParkingOrder order = this.getById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", order.getId());
        info.put("orderNo", order.getOrderNo());
        info.put("spaceId", order.getSpaceId());
        info.put("spaceNo", order.getSpaceNo());
        info.put("vehicleId", order.getVehicleId());
        info.put("plateNo", decryptPlateNo(order));
        // 订单表没有 user_id，归属只能由车辆反查（报告 P2-9：原实现直接填 vehicleId，
        // 下游拿到的是"车辆ID当用户ID"，任何按 userId 判断归属的逻辑都会失真）
        Long ownerId = null;
        try {
            Map<String, Object> vehicleInfo = vehicleClient.getByPlateNo(decryptPlateNo(order));
            if (vehicleInfo.get("ownerId") != null) {
                ownerId = Long.valueOf(String.valueOf(vehicleInfo.get("ownerId")));
            }
        } catch (Exception e) {
            log.warn("订单 {} 反查车主失败：{}", order.getOrderNo(), e.getMessage());
        }
        info.put("userId", ownerId);
        info.put("status", order.getStatus());
        info.put("payStatus", order.getPayStatus());
        info.put("amount", order.getAmount());
        return info;
    }

    /**
     * 关闭进行中的订单并释放车位
     */
    private void closeOrderAndReleaseSpace(ParkingOrder order, String operation, String method, String params) {
        if (order.getStatus() != null && Constants.ORDER_STATUS_ONGOING != order.getStatus()) {
            throw new BizException(ErrorCode.ORDER_STATUS_ERROR, "订单状态异常，无法关闭");
        }
        order.setStatus(Constants.ORDER_STATUS_CANCELLED);
        this.updateById(order);

        Long spaceId = order.getSpaceId();
        runAfterCommit(() -> {
            try { parkingSpaceClient.updateStatus(spaceId, 0); }
            catch (Exception e) { log.error("关闭订单释放车位失败 spaceId={}, err={}", spaceId, e.getMessage()); }
            try { auditClient.record(currentUserId(), null, operation, method, params, true); }
            catch (Exception e) { log.warn("关闭订单审计失败: {}", e.getMessage()); }
        });
    }

    /**
     * 在当前事务提交后执行回调；无事务时立即执行。
     * 用于把远程调用（车位联动/MQ/审计）从事务里挪出来，避免本地回滚但远端已改。
     */
    private void runAfterCommit(Runnable action) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deviceEntry(String plateNo, Integer confidence, String deviceNo) {
        Map<String, Object> result = new HashMap<>();
        result.put("plateNo", plateNo);
        result.put("deviceNo", deviceNo);
        int conf = confidence == null ? 100 : confidence;

        // 1. 调用车辆服务识别车牌
        Map<String, Object> recognize = recognizePlate(plateNo, conf);
        boolean recognized = Boolean.TRUE.equals(recognize.get("recognized"));
        result.put("recognized", recognized);
        result.put("confidence", conf);

        if (!recognized) {
            // 置信度低 → 模糊识别兜底建议
            if (conf < 80 && recognize.get("suggestList") != null) {
                result.put("message", "识别不清晰，请人工确认（相似车牌：" + recognize.get("suggestList") + "）");
                return result;
            }
            // 车辆服务对黑名单车辆会置 recognized=false 并回传原因：直接透传并拦停，
            // 不能当"未注册车辆"继续走访客白名单，否则欠费黑名单形同虚设。
            Object blockReason = recognize.get("message");
            if (blockReason != null && StringUtils.hasText(String.valueOf(blockReason))) {
                result.put("message", String.valueOf(blockReason));
                auditClient.record(null, null, "入场拦截", "POST /api/order/internal/device-entry",
                        java.util.Map.of("plateNo", AesUtils.maskPlate(plateNo), "deviceNo", deviceNo,
                                "reason", String.valueOf(blockReason)), false);
                return result;
            }
            // 未注册车辆：尝试命中访客白名单（方案B：业主录入车牌，入场自动识别放行）
            try {
                ParkingOrder visitorOrder = visitorInviteService.entryByPlateNo(plateNo);
                result.put("recognized", true);
                result.put("visitor", true);
                result.put("orderNo", visitorOrder.getOrderNo());
                result.put("spaceId", visitorOrder.getSpaceId());
                result.put("spaceNo", visitorOrder.getSpaceNo());
                result.put("message", "识别为访客车辆，已自动入场");
                return result;
            } catch (RuntimeException e) {
                log.info("车牌 {} 不在访客白名单：{}", plateNo, e.getMessage());
                result.put("message", "未识别到注册车辆或访客白名单，请人工确认");
                return result;
            }
        }

        // 2. 检查是否已在场内
        Long vehicleId = Long.valueOf(String.valueOf(recognize.get("vehicleId")));
        ParkingOrder existing = this.getCurrentOrder(vehicleId);
        if (existing != null) {
            result.put("message", "车辆已在场内（订单 " + existing.getOrderNo() + "）");
            result.put("orderNo", existing.getOrderNo());
            result.put("spaceNo", existing.getSpaceNo());
            return result;
        }

        // 3. 分配空闲车位
        Map<String, Object> space = findAvailableSpace();
        if (space == null) {
            result.put("message", "暂无空闲车位，道闸保持关闭");
            return result;
        }
        Long spaceId = Long.valueOf(String.valueOf(space.get("id")));
        String spaceNo = String.valueOf(space.get("spaceNo"));

        // 4. 创建入场订单（传车辆类型/车牌颜色，正确匹配计费规则）
        Integer vehicleType = recognize.get("vehicleType") == null ? 0
                : Integer.valueOf(String.valueOf(recognize.get("vehicleType")));
        String plateColor = recognize.get("plateColor") == null ? null : String.valueOf(recognize.get("plateColor"));
        ParkingOrder order = createEntryOrder(vehicleId, plateNo, spaceId, spaceNo, vehicleType, plateColor);
        result.put("orderNo", order.getOrderNo());
        result.put("spaceId", spaceId);
        result.put("spaceNo", spaceNo);
        result.put("message", "识别成功，已自动入场");

        // 5. 操作审计：设备联动入场
        auditClient.record(null, null, "设备联动入场", "POST /api/order/internal/device-entry",
                java.util.Map.of("plateNo", AesUtils.maskPlate(plateNo), "confidence", conf, "deviceNo", deviceNo,
                        "orderNo", order.getOrderNo(), "spaceNo", spaceNo), true);
        return result;
    }

    /**
     * 出场分级放行判定（纯函数，便于逐分支单测）：
     * 已支付、免费、或未支付但金额不超过信用额度 → 放行（先出场后付费，出口不堵）；
     * 未支付且金额超过额度 → 不抬杆，要求当场扫码支付。
     */
    static boolean canRelease(Integer payStatus, BigDecimal amount, BigDecimal creditLimitAmount) {
        if (Integer.valueOf(Constants.PAY_STATUS_PAID).equals(payStatus)) {
            return true;
        }
        if (amount == null || amount.signum() <= 0) {
            return true;
        }
        return amount.compareTo(creditLimitAmount) <= 0;
    }

    /**
     * 生效的信用额度 = max(配置兜底值, 该规则的单日封顶)。
     *
     * <p>口径为何取「单日封顶」：本系统服务的是小区，停车时长普遍是数小时到一整天，
     * 商场那套「停 1~2 小时」的量级不适用 —— 若额度按小的固定值算，业主回家停一晚就会超，
     * 分级放行等于形同虚设，出口天天堵。以单日封顶为额度，语义变成
     * <b>「一天以内的停车先出场后付，跨日累积超过一天封顶才要求当场结清」</b>：
     * 既覆盖业主日常与访客过夜，也能拦住多日占用/疑似弃车；规则调价自动跟随，无需回头改配置。
     */
    private BigDecimal effectiveCreditLimit(Long billingRuleId) {
        if (billingRuleId == null) {
            return creditLimitAmount;
        }
        BillingRule rule = billingRuleService.getById(billingRuleId);
        if (rule == null) {
            return creditLimitAmount;
        }
        return effectiveCreditLimit(creditLimitAmount, rule.getDailyCap(), rule.getWeekendDailyCap());
    }

    /**
     * 纯函数便于单测：取「平峰日封顶」与「周末日封顶」中较大者，无封顶的规则（如月租）
     * 回退到配置兜底值。取大是为了避免周末费率更高时、节假日出场反而被额度卡住。
     */
    static BigDecimal effectiveCreditLimit(BigDecimal configured, BigDecimal dailyCap, BigDecimal weekendDailyCap) {
        BigDecimal cap = dailyCap;
        if (weekendDailyCap != null && (cap == null || weekendDailyCap.compareTo(cap) > 0)) {
            cap = weekendDailyCap;
        }
        if (cap == null || cap.signum() <= 0) {
            return configured;
        }
        return configured.max(cap);
    }

    /**
     * 设备联动出场：出口摄像头识别车牌 → 自动结算 → 释放车位（是否抬杆按分级放行策略）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deviceExit(String plateNo, String deviceNo) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("plateNo", plateNo);
        try {
            // 车牌密文匹配（新数据），未命中再按明文匹配（兼容历史明文数据）
            ParkingOrder order = this.getOne(new LambdaQueryWrapper<ParkingOrder>()
                    .eq(ParkingOrder::getPlateNo, AesUtils.encrypt(plateNo))
                    .eq(ParkingOrder::getStatus, Constants.ORDER_STATUS_ONGOING)
                    .last("LIMIT 1"));
            if (order == null) {
                order = this.getOne(new LambdaQueryWrapper<ParkingOrder>()
                        .eq(ParkingOrder::getPlateNo, plateNo)
                        .eq(ParkingOrder::getStatus, Constants.ORDER_STATUS_ONGOING)
                        .last("LIMIT 1"));
            }
            if (order == null) {
                // 报告 P1-11「有出无进」：此时道闸必须保持关闭，否则无单车辆可免费出场。
                // 但不能只返回一句失败就结束 —— 现场需要"人工放行"这条旁路，且必须留痕。
                // 旁路复用已有的管理端接口 POST /api/device/gate/open（网关已限定 role 1/2），
                // 此处只负责把"该走哪条旁路"显式告诉调用方，并写审计，便于事后追责。
                result.put("needManualRelease", true);
                result.put("manualReleaseApi", "POST /api/device/gate/open?deviceNo=" + deviceNo);
                result.put("message", "未找到该车牌的进行中订单，道闸保持关闭；"
                        + "如属特殊情况需放行，请由管理端调用道闸手动开启接口（需 role=1/2）");
                auditClient.record(null, null, "设备出场未匹配订单", "POST /api/order/internal/device-exit",
                        java.util.Map.of("plateNo", AesUtils.maskPlate(plateNo), "deviceNo", deviceNo,
                                "needManualRelease", true), false);
                return result;
            }
            // 先结算（锁定金额、释放车位），再按支付状态决定抬不抬杆。
            // 结算与放行解耦的好处：金额不因"过一会儿再付"而变化，也不会重复计费；
            // 未付款被拦时订单已是「待支付」，扫码付款后由支付成功事件抬杆，天然衔接。
            ParkingOrder completed = completeOrder(order.getOrderNo());
            BigDecimal amount = completed.getAmount() == null ? BigDecimal.ZERO : completed.getAmount();
            boolean paid = Integer.valueOf(Constants.PAY_STATUS_PAID).equals(completed.getPayStatus());
            boolean free = amount.signum() <= 0;
            BigDecimal effectiveLimit = effectiveCreditLimit(order.getBillingRuleId());
            boolean release = canRelease(completed.getPayStatus(), amount, effectiveLimit);

            result.put("success", release);
            result.put("orderNo", completed.getOrderNo());
            result.put("amount", amount);
            result.put("spaceNo", completed.getSpaceNo());
            if (release) {
                if (paid) {
                    result.put("message", "出场结算完成（已支付）");
                } else if (free) {
                    result.put("message", "出场结算完成（无需支付）");
                } else {
                    result.put("message", "已信用放行，请在 24 小时内完成支付 ¥" + amount);
                }
            } else {
                result.put("needPay", true);
                result.put("payTip", "应付 ¥" + amount + "，请扫码支付后出场");
                result.put("message", "未支付且超过信用额度 ¥" + effectiveLimit + "，道闸保持关闭");
            }
            auditClient.record(null, null, release ? "设备联动出场" : "设备联动出场（未付费拦截）",
                    "POST /api/order/internal/device-exit",
                    java.util.Map.of("plateNo", AesUtils.maskPlate(plateNo), "deviceNo", deviceNo,
                            "orderNo", completed.getOrderNo(),
                            "amount", amount, "released", release), true);
            return result;
        } catch (Exception e) {
            log.error("设备联动出场失败：plateNo={}, err={}", plateNo, e.getMessage());
            result.put("message", "出场结算失败：" + e.getMessage());
            return result;
        }
    }

    /**
     * 调用车辆服务识别车牌
     */
    private Map<String, Object> recognizePlate(String plateNo, int confidence) {
        try {
            String url = vehicleUrl + "/api/vehicle/recognize?plateNo="
                    + java.net.URLEncoder.encode(plateNo, java.nio.charset.StandardCharsets.UTF_8)
                    + "&confidence=" + confidence;
            String body = restTemplate.postForObject(java.net.URI.create(url), HttpEntity.EMPTY, String.class);
            Map<String, Object> parsed = parseJson(body);
            if (parsed.get("code") != null && "200".equals(String.valueOf(parsed.get("code")))) {
                return (Map<String, Object>) parsed.get("data");
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR, "车牌识别服务异常，请稍后重试");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用车牌识别失败：plateNo={}, err={}", plateNo, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "车牌识别服务不可用，请稍后重试");
        }
    }

    /**
     * 调用车位服务获取空闲车位
     */
    private Map<String, Object> findAvailableSpace() {
        try {
            String url = parkingUrl + "/api/parking/available";
            String body = restTemplate.getForObject(url, String.class);
            Map<String, Object> parsed = parseJson(body);
            if (parsed.get("code") == null || !"200".equals(String.valueOf(parsed.get("code")))) {
                return null;
            }
            List<Map<String, Object>> spaces = (List<Map<String, Object>>) parsed.get("data");
            if (spaces == null || spaces.isEmpty()) {
                return null;
            }
            // 轮询分配：连续入场车辆轮流命中不同空闲车位，避免长期集中占用前几个车位
            int idx = (int) (Math.abs(spaceAllocateCounter.getAndIncrement()) % spaces.size());
            return spaces.get(idx);
        } catch (Exception e) {
            log.error("获取空闲车位失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 解密订单车牌（密文解不出时原样返回，兼容历史明文数据）
     */
    private static String decryptPlateNo(ParkingOrder order) {
        if (order == null || order.getPlateNo() == null) {
            return null;
        }
        try {
            String decrypted = AesUtils.decrypt(order.getPlateNo());
            return decrypted != null ? decrypted : order.getPlateNo();
        } catch (Exception e) {
            return order.getPlateNo();
        }
    }

    private Map<String, Object> parseJson(String body) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Map.class);
    }

    /**
     * 某日各小时入场量。
     *
     * <p>统计服务此前直查 {@code parking_order}（跨 owns 表），表结构一改就静默失效；
     * 改为由订单域自己聚合后经 {@code /internal} 提供，边界回到「谁的表谁查」。
     * 按 {@code entry_time} 的日区间过滤（{@code [00:00, 次日 00:00)}），
     * 不用 {@code DATE(entry_time)=?} 是为了让索引可用。
     */
    @Override
    public List<Map<String, Object>> entryCountByHour(java.time.LocalDate date) {
        if (date == null) {
            return java.util.Collections.emptyList();
        }
        return this.baseMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ParkingOrder>()
                        .select("HOUR(entry_time) AS `hour`", "COUNT(*) AS `count`")
                        .eq("is_deleted", 0)
                        .ge("entry_time", date.atStartOfDay())
                        .lt("entry_time", date.plusDays(1).atStartOfDay())
                        .groupBy("HOUR(entry_time)")
                        .orderByAsc("`hour`"));
    }
}
