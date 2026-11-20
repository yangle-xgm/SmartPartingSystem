package com.smartparking.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.common.core.utils.AesUtils;
import com.smartparking.vehicle.client.AuditClient;
import com.smartparking.vehicle.client.BillingClient;
import com.smartparking.vehicle.mapper.MonthlyCardOrderMapper;
import com.smartparking.vehicle.mapper.VehicleMapper;
import com.smartparking.vehicle.po.MonthlyCardOrder;
import com.smartparking.vehicle.po.Vehicle;
import com.smartparking.vehicle.service.MonthlyCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 月卡充值实现。
 *
 * <p>下单 → 支付 → 生效三段式：下单只锁定价格与到期承诺，支付成功才延长有效期。
 * 到期时间以「下单时的基准」为准写进订单，支付时直接落库这个承诺值，
 * 避免用户停留几分钟再支付导致到期时间漂移、与报价不一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyCardServiceImpl implements MonthlyCardService {

    private final MonthlyCardOrderMapper orderMapper;
    private final VehicleMapper vehicleMapper;
    private final BillingClient billingClient;
    private final AuditClient auditClient;

    /** 允许购买的月数档位 */
    private static final Set<Integer> ALLOWED_MONTHS = Set.of(1, 3, 6, 12);

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Override
    public Map<String, Object> quote(Long vehicleId, Integer months, Long currentUserId, String currentRole) {
        int m = normalizeMonths(months);
        Vehicle v = requireOwnedVehicle(vehicleId, currentUserId, currentRole);
        BigDecimal unitPrice = billingClient.monthlyPrice();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = v.getMonthlyExpire() != null && v.getMonthlyExpire().isAfter(now)
                ? v.getMonthlyExpire() : now;
        LocalDateTime after = base.plusMonths(m);

        String plain = AesUtils.decrypt(v.getPlateNo());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("vehicleId", v.getId());
        result.put("plateNo", plain);
        result.put("plateNoMasked", AesUtils.maskPlate(plain));
        result.put("months", m);
        result.put("unitPrice", unitPrice);
        result.put("amount", unitPrice.multiply(BigDecimal.valueOf(m)));
        result.put("expireBefore", v.getMonthlyExpire());
        result.put("expireAfter", after);
        result.put("isRenewal", v.getMonthlyExpire() != null && v.getMonthlyExpire().isAfter(now));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MonthlyCardOrder createOrder(Long vehicleId, Integer months, Integer payType,
                                        Long currentUserId, String currentRole) {
        int m = normalizeMonths(months);
        Vehicle v = requireOwnedVehicle(vehicleId, currentUserId, currentRole);
        BigDecimal unitPrice = billingClient.monthlyPrice();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = v.getMonthlyExpire() != null && v.getMonthlyExpire().isAfter(now)
                ? v.getMonthlyExpire() : now;

        MonthlyCardOrder order = new MonthlyCardOrder();
        order.setOrderNo(nextOrderNo());
        order.setUserId(v.getOwnerId());
        order.setVehicleId(v.getId());
        order.setPlateNo(v.getPlateNo());            // 与 vehicle 表同源，仍为密文
        order.setMonths(m);
        order.setUnitPrice(unitPrice);
        order.setAmount(unitPrice.multiply(BigDecimal.valueOf(m)));
        order.setPayType(payType);
        order.setPayStatus(0);
        order.setExpireBefore(v.getMonthlyExpire());
        order.setExpireAfter(base.plusMonths(m));
        orderMapper.insert(order);

        auditClient.record(v.getOwnerId(), null, "月卡下单", "POST /api/vehicle/monthly/create",
                "{\"orderNo\":\"" + order.getOrderNo() + "\",\"months\":" + m
                        + ",\"amount\":" + order.getAmount() + "}", true);
        return order;
    }

    @Override
    public MonthlyCardOrder getMonthlyOrder(Long monthlyOrderId) {
        if (monthlyOrderId == null) {
            return null;
        }
        return orderMapper.selectById(monthlyOrderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> markPaid(Long monthlyOrderId, String payNo, String transactionId) {
        MonthlyCardOrder order = orderMapper.selectById(monthlyOrderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "月卡订单不存在");
        }
        // 幂等：渠道会重复回调 / 前端会重复补查，已支付直接返回，绝不二次续期
        if (order.getPayStatus() != null && order.getPayStatus() == 1) {
            if (payNo != null && !payNo.equals(order.getPayNo())) {
                log.warn("月卡订单 {} 已由流水 {} 支付，本次回调流水 {} 忽略",
                        monthlyOrderId, order.getPayNo(), payNo);
            }
            return paidResult(order);
        }
        if (order.getPayStatus() != null && order.getPayStatus() == 3) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "该月卡订单已取消，无法续期");
        }

        LocalDateTime now = LocalDateTime.now();
        // 原子条件更新：只有仍是待支付才能落成功，防并发重复回调导致多次续期
        int affected = orderMapper.update(null, new LambdaUpdateWrapper<MonthlyCardOrder>()
                .eq(MonthlyCardOrder::getId, order.getId())
                .eq(MonthlyCardOrder::getPayStatus, 0)
                .set(MonthlyCardOrder::getPayStatus, 1)
                .set(MonthlyCardOrder::getPayNo, payNo)
                .set(MonthlyCardOrder::getTransactionId, transactionId)
                .set(MonthlyCardOrder::getPayTime, now)
                .set(MonthlyCardOrder::getUpdateTime, now));
        if (affected == 0) {
            // 并发回调：别人先置成功了，按幂等返回而不是报错
            MonthlyCardOrder latest = orderMapper.selectById(monthlyOrderId);
            if (latest != null && latest.getPayStatus() != null && latest.getPayStatus() == 1) {
                return paidResult(latest);
            }
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "月卡订单状态已变更，无法续期");
        }

        // 车辆月卡生效：到期时间取下单时的承诺值（下单已按基准算好），避免支付延迟导致漂移
        Vehicle v = vehicleMapper.selectById(order.getVehicleId());
        if (v == null) {
            throw new BizException(ErrorCode.VEHICLE_NOT_FOUND);
        }
        v.setMonthlyExpire(order.getExpireAfter());
        v.setType(2);
        vehicleMapper.updateById(v);

        auditClient.record(order.getUserId(), null, "月卡支付", "POST /api/vehicle/internal/monthly/mark-paid",
                "{\"orderNo\":\"" + order.getOrderNo() + "\",\"payNo\":\"" + payNo
                        + "\",\"amount\":" + order.getAmount() + "}", true);

        log.info("月卡续期成功：orderNo={}, vehicleId={}, months={}, expireAfter={}",
                order.getOrderNo(), order.getVehicleId(), order.getMonths(), order.getExpireAfter());

        order.setPayStatus(1);
        order.setPayNo(payNo);
        order.setTransactionId(transactionId);
        order.setPayTime(now);
        return paidResult(order);
    }

    private Map<String, Object> paidResult(MonthlyCardOrder order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", order.getOrderNo());
        result.put("payNo", order.getPayNo());
        result.put("amount", order.getAmount());
        result.put("months", order.getMonths());
        result.put("expireAfter", order.getExpireAfter());
        result.put("message", "充值成功，月卡有效期至 " + order.getExpireAfter().toLocalDate());
        return result;
    }

    @Override
    public List<MonthlyCardOrder> myOrders(Long userId) {
        if (userId == null) {
            return java.util.Collections.emptyList();
        }
        List<MonthlyCardOrder> list = orderMapper.selectList(new LambdaQueryWrapper<MonthlyCardOrder>()
                .eq(MonthlyCardOrder::getUserId, userId)
                .orderByDesc(MonthlyCardOrder::getCreateTime));
        // 车牌解密后返回，避免把密文吐给前端
        list.forEach(o -> o.setPlateNo(AesUtils.decrypt(o.getPlateNo())));
        return list;
    }

    // ==================== 内部方法 ====================

    private int normalizeMonths(Integer months) {
        if (months == null || !ALLOWED_MONTHS.contains(months)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "购买月数只支持 1/3/6/12 个月");
        }
        return months;
    }

    private Vehicle requireOwnedVehicle(Long vehicleId, Long currentUserId, String currentRole) {
        if (vehicleId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少车辆ID");
        }
        Vehicle v = vehicleMapper.selectById(vehicleId);
        if (v == null) {
            throw new BizException(ErrorCode.VEHICLE_NOT_FOUND);
        }
        requireOwner(v.getOwnerId(), currentUserId, currentRole);
        return v;
    }

    /**
     * 归属校验：业主只能操作自己的车辆；物业(1)/超管(2)可代操作。
     * 历史漏洞：续费接口只按 vehicleId 续期，任何登录用户都能篡改他人月卡。
     */
    private void requireOwner(Long ownerId, Long currentUserId, String currentRole) {
        boolean isAdmin = "1".equals(currentRole) || "2".equals(currentRole);
        if (isAdmin) {
            return;
        }
        if (currentUserId == null || !currentUserId.equals(ownerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能为自己的车辆充值");
        }
    }

    private String nextOrderNo() {
        return "MC" + LocalDateTime.now().format(ORDER_NO_FMT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }
}
