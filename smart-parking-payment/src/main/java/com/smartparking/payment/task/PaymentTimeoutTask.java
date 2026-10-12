package com.smartparking.payment.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.payment.client.AuditClient;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.service.PaymentRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付超时补偿定时任务（计划书5.4异常流程）
 *
 * 每60秒扫描"待支付"超过阈值的支付记录：
 * 1. 将支付记录标记为失败（pay_status=2）
 * 2. 调用订单服务内部接口关闭对应订单、释放车位
 * 3. 记录操作审计
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutTask {

    /** 超时阈值（分钟），演示时可调小，如 1 */
    @Value("${smart-parking.payment-timeout-minutes:30}")
    private int timeoutMinutes;

    /** 订单服务地址 */
    @Value("${smart-parking.order-url:http://localhost:8084}")
    private String orderUrl;

    private final PaymentRecordService paymentRecordService;
    private final AuditClient auditClient;

    private final RestTemplate restTemplate = new RestTemplate();

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void scanTimeoutPayments() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
            List<PaymentRecord> timeoutList = paymentRecordService.list(
                    new LambdaQueryWrapper<PaymentRecord>()
                            .eq(PaymentRecord::getPayStatus, 0)   // 待支付
                            .lt(PaymentRecord::getCreateTime, threshold));

            if (timeoutList.isEmpty()) {
                return;
            }
            log.info("支付超时扫描：发现 {} 笔待支付超时记录（阈值 {} 分钟）", timeoutList.size(), timeoutMinutes);

            for (PaymentRecord record : timeoutList) {
                processTimeout(record);
            }
        } catch (Exception e) {
            log.error("支付超时扫描异常：{}", e.getMessage(), e);
        }
    }

    /**
     * 手动触发扫描（供管理端演示/运维调用）
     */
    public void scanNow() {
        scanTimeoutPayments();
    }

    private void processTimeout(PaymentRecord record) {
        // 1. 标记支付失败
        record.setPayStatus(2);
        paymentRecordService.updateById(record);

        // 2. 通知订单服务关闭订单、释放车位
        boolean orderClosed = false;
        try {
            String url = orderUrl + "/api/order/internal/payment-timeout?orderId=" + record.getOrderId();
            restTemplate.postForEntity(url, HttpEntity.EMPTY, String.class);
            orderClosed = true;
        } catch (Exception e) {
            log.error("关闭超时订单失败：payNo={}, orderId={}, err={}",
                    record.getPayNo(), record.getOrderId(), e.getMessage());
        }

        // 3. 操作审计
        auditClient.record(record.getUserId(), null, "支付超时补偿", "SCHEDULED /payment-timeout",
                "{\"payNo\":\"" + record.getPayNo() + "\",\"orderId\":" + record.getOrderId()
                        + ",\"amount\":" + record.getAmount() + ",\"orderClosed\":" + orderClosed + "}",
                orderClosed);
    }
}
