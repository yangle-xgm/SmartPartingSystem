package com.smartparking.payment.task;

import com.smartparking.payment.po.PaymentRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付对账聚合单元测试（Phase B）。
 * 镜像 {@link ReconciliationTask#reconcileYesterday()} 的聚合规则：
 * 按 payStatus 累加（1=成功 / 3=退款），差额 = 成功总额 - 退款总额，
 * 存在退款记录则标记 status=1。
 */
class ReconciliationAggregationTest {

    private record ReconSummary(int payCount, int refundCount, BigDecimal totalAmount,
                                BigDecimal refundAmount, BigDecimal diffAmount, int status) {
    }

    private ReconSummary aggregate(List<PaymentRecord> records) {
        int payCount = 0, refundCount = 0;
        BigDecimal total = BigDecimal.ZERO, refund = BigDecimal.ZERO;
        for (PaymentRecord r : records) {
            if (r.getPayStatus() == 1) {
                payCount++;
                total = total.add(r.getAmount());
            } else if (r.getPayStatus() == 3) {
                refundCount++;
                refund = refund.add(r.getAmount());
            }
        }
        BigDecimal diff = total.subtract(refund);
        int status = (payCount == 0 && refundCount == 0) ? 0 : (refundCount > 0 ? 1 : 0);
        return new ReconSummary(payCount, refundCount, total, refund, diff, status);
    }

    private PaymentRecord rec(BigDecimal amount, int status) {
        PaymentRecord r = new PaymentRecord();
        r.setAmount(amount);
        r.setPayStatus(status);
        r.setCreateTime(LocalDateTime.now());
        return r;
    }

    @Test
    void aggregatesPaidAndRefunded() {
        List<PaymentRecord> list = List.of(
                rec(new BigDecimal("10.00"), 1),
                rec(new BigDecimal("20.50"), 1),
                rec(new BigDecimal("5.50"), 3),
                rec(new BigDecimal("99.00"), 0)); // 待支付不计入
        ReconSummary s = aggregate(list);
        assertEquals(2, s.payCount());
        assertEquals(1, s.refundCount());
        assertEquals(new BigDecimal("30.50"), s.totalAmount());
        assertEquals(new BigDecimal("5.50"), s.refundAmount());
        assertEquals(new BigDecimal("25.00"), s.diffAmount());
        assertEquals(1, s.status()); // 有退款 -> 状态1
    }

    @Test
    void emptyRecords_statusZero() {
        ReconSummary s = aggregate(List.of());
        assertEquals(0, s.payCount());
        assertEquals(0, s.status());
        assertEquals(BigDecimal.ZERO, s.diffAmount());
    }

    @Test
    void noRefund_statusZero() {
        List<PaymentRecord> list = List.of(
                rec(new BigDecimal("10.00"), 1),
                rec(new BigDecimal("10.00"), 1));
        ReconSummary s = aggregate(list);
        assertEquals(0, s.status());
        assertEquals(BigDecimal.ZERO, s.diffAmount());
    }
}
