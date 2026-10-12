package com.smartparking.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.payment.client.AuditClient;
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

    private static final DateTimeFormatter PAY_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 网关地址（用于直连通知订单服务确认支付，兜底MQ） */
    @Value("${smart-parking.gateway-url:http://localhost:8080}")
    private String gatewayUrl;

    private final RestTemplate restTemplate = buildRestTemplate();

    private static org.springframework.web.client.RestTemplate buildRestTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new org.springframework.web.client.RestTemplate(factory);
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
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("校验订单状态失败 orderId={}：{}", orderId, e.getMessage());
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单服务暂不可用，请稍后再试");
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

        record.setPayStatus(1);
        record.setTransactionId(transactionId);
        record.setPayTime(LocalDateTime.now());
        this.updateById(record);

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
            ShareIncome share = new ShareIncome();
            share.setPaymentId(record.getId());
            share.setSpaceId(spaceId);
            share.setOwnerId(ownerId);
            share.setTotalAmount(record.getAmount());
            share.setOwnerRatio(new java.math.BigDecimal("0.7"));
            share.setOwnerAmount(record.getAmount().multiply(new java.math.BigDecimal("0.7")));
            share.setPropertyAmount(record.getAmount().multiply(new java.math.BigDecimal("0.3")));
            shareIncomeMapper.insert(share);
            log.info("共享车位分润：paymentId={}, space={}, owner={}, 业主={}, 物业={}",
                    record.getId(), spaceNo, ownerId, share.getOwnerAmount(), share.getPropertyAmount());
        } catch (Exception e) {
            log.warn("分润计算失败（不阻断支付）：orderId={}, err={}", record.getOrderId(), e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paymentFail(String payNo, String reason) {
        PaymentRecord record = this.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getPayNo, payNo));
        if (record == null) {
            throw new BizException(ErrorCode.PAY_NOT_FOUND);
        }

        record.setPayStatus(4); // 支付失败（独立状态，与已退款2区分）
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

        // 模拟第三方退款成功（演示环境不调真实网关）；状态机：未支付0 → 已支付1 → 已退款2
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
