package com.smartparking.payment.task;

import com.smartparking.payment.po.PaymentRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 支付超时补偿判定单元测试（Phase B）。
 * 镜像 {@link PaymentTimeoutTask#scanTimeoutPayments()} 的超时规则：
 * 仅当 payStatus==0（待支付）且 createTime 早于 (now - timeoutMinutes) 时判定为超时。
 */
class PaymentTimeoutRuleTest {

    private boolean isTimeout(PaymentRecord r, int timeoutMinutes, LocalDateTime now) {
        if (r.getPayStatus() != 0) {
            return false;
        }
        return r.getCreateTime() != null
                && r.getCreateTime().isBefore(now.minusMinutes(timeoutMinutes));
    }

    private PaymentRecord rec(int status, LocalDateTime createTime) {
        PaymentRecord r = new PaymentRecord();
        r.setPayStatus(status);
        r.setCreateTime(createTime);
        return r;
    }

    @Test
    void unpaidAndOld_isTimeout() {
        LocalDateTime now = LocalDateTime.now();
        assertTrue(isTimeout(rec(0, now.minusMinutes(31)), 30, now));
    }

    @Test
    void unpaidButRecent_notTimeout() {
        LocalDateTime now = LocalDateTime.now();
        assertFalse(isTimeout(rec(0, now.minusMinutes(29)), 30, now));
    }

    @Test
    void paidNeverTimeout() {
        LocalDateTime now = LocalDateTime.now();
        assertFalse(isTimeout(rec(1, now.minusMinutes(60)), 30, now));
    }

    @Test
    void nullCreateTime_notTimeout() {
        assertFalse(isTimeout(rec(0, null), 30, LocalDateTime.now()));
    }
}
