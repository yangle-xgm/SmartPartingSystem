package com.smartparking.payment.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.payment.mapper.PaymentReconciliationMapper;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.po.PaymentReconciliation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日支付对账任务：统计前一日支付/退款数据，记录对账结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationTask {

    private final PaymentReconciliationMapper reconciliationMapper;
    private final com.baomidou.mybatisplus.core.mapper.BaseMapper<PaymentRecord> paymentRecordMapper;

    /** 每天凌晨1点执行，对账前一天数据 */
    @Scheduled(cron = "0 0 1 * * ?")
    public void reconcileYesterday() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime start = yesterday.atStartOfDay();
        LocalDateTime end = yesterday.plusDays(1).atStartOfDay();

        List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .ge(PaymentRecord::getCreateTime, start)
                        .lt(PaymentRecord::getCreateTime, end)
                        .eq(PaymentRecord::getIsDeleted, 0));

        int payCount = 0, refundCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO, refundAmount = BigDecimal.ZERO;

        for (PaymentRecord r : records) {
            if (r.getPayStatus() == 1) {
                payCount++;
                totalAmount = totalAmount.add(r.getAmount());
            } else if (r.getPayStatus() == 3) {
                refundCount++;
                refundAmount = refundAmount.add(r.getAmount());
            }
        }

        PaymentReconciliation recon = new PaymentReconciliation();
        recon.setReconDate(yesterday);
        recon.setPayCount(payCount);
        recon.setTotalAmount(totalAmount);
        recon.setRefundCount(refundCount);
        recon.setRefundAmount(refundAmount);
        recon.setDiffAmount(BigDecimal.ZERO);
        recon.setStatus(payCount == 0 && refundCount == 0 ? 0 : (refundCount > 0 ? 1 : 0));
        recon.setRemark(refundCount > 0 ? "存在退款记录" : "正常");

        reconciliationMapper.insert(recon);
        log.info("支付对账完成 {}：支付{}笔/{}元，退款{}笔/{}元", yesterday, payCount, totalAmount, refundCount, refundAmount);
    }
}
