package com.smartparking.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.common.core.http.InternalHttp;
import com.smartparking.payment.client.AuditClient;
import com.smartparking.payment.dto.InvoiceApplyDTO;
import com.smartparking.payment.mapper.InvoiceMapper;
import com.smartparking.payment.po.Invoice;
import com.smartparking.payment.po.PaymentRecord;
import com.smartparking.payment.service.InvoiceService;
import com.smartparking.payment.service.PaymentRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 电子发票服务实现。
 *
 * <p>开票金额一律取「实际收款流水」而不是订单应收金额：零元放行单、减免单不应产生发票，
 * 且订单金额可能被后续调价改动，只有 payment_record 是真金白银。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl extends ServiceImpl<InvoiceMapper, Invoice> implements InvoiceService {

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_ISSUED = 1;
    private static final int STATUS_RED_FLUSH = 2;

    private final PaymentRecordService paymentRecordService;
    private final AuditClient auditClient;

    private final RestTemplate restTemplate = InternalHttp.attach(new RestTemplate());

    @Value("${smart-parking.gateway-url:http://localhost:8080}")
    private String gatewayUrl;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Invoice apply(InvoiceApplyDTO dto, Long currentUserId) {
        if (currentUserId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录再申请发票");
        }
        if (Integer.valueOf(2).equals(dto.getTitleType()) && !StringUtils.hasText(dto.getTaxNo())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "企业抬头必须填写纳税人识别号");
        }

        // 1. 订单必须已支付（订单服务的 payStatus 是权威口径）
        Long orderId = dto.getOrderId();
        Map<String, Object> orderData = fetchOrderInfo(orderId);
        int orderPayStatus = orderData.get("payStatus") == null ? 0
                : ((Number) orderData.get("payStatus")).intValue();
        if (orderPayStatus != 1) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "订单尚未支付完成，无法开票");
        }

        // 2. 取该订单的已支付流水：金额、归属都以此为准
        PaymentRecord paid = paymentRecordService.getOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, orderId)
                .eq(PaymentRecord::getPayStatus, 1)
                .orderByDesc(PaymentRecord::getId)
                .last("LIMIT 1"));
        if (paid == null) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "未找到该订单的支付流水，无法开票");
        }
        if (paid.getUserId() != null && !paid.getUserId().equals(currentUserId)) {
            // 业主只能给自己付的钱开票
            throw new BizException(ErrorCode.FORBIDDEN, "只能对本人支付的订单申请发票");
        }
        BigDecimal amount = paid.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该订单实付金额为 0，无需开票");
        }

        // 3. 一单一票（数据库唯一键兜底并发，这里先给出友好提示）
        Long exists = this.count(new LambdaQueryWrapper<Invoice>().eq(Invoice::getOrderId, orderId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该订单已申请过发票，请勿重复申请");
        }

        Invoice invoice = new Invoice();
        invoice.setOrderId(orderId);
        invoice.setPaymentId(paid.getId());
        invoice.setUserId(currentUserId);
        invoice.setTitleType(dto.getTitleType());
        invoice.setTitle(dto.getTitle().trim());
        invoice.setTaxNo(StringUtils.hasText(dto.getTaxNo()) ? dto.getTaxNo().trim().toUpperCase() : null);
        invoice.setEmail(StringUtils.hasText(dto.getEmail()) ? dto.getEmail().trim() : null);
        invoice.setAmount(amount);
        invoice.setStatus(STATUS_PENDING);
        invoice.setApplyTime(LocalDateTime.now());
        try {
            this.save(invoice);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：唯一键 uk_order_id 命中
            throw new BizException(ErrorCode.PARAM_INVALID, "该订单已申请过发票，请勿重复申请");
        }

        auditClient.record(currentUserId, null, "申请电子发票", "POST /api/payment/invoice/apply",
                "{\"invoiceId\":" + invoice.getId() + ",\"orderId\":" + orderId + ",\"amount\":" + amount + "}", true);
        log.info("发票申请已受理：id={}, orderId={}, amount={}", invoice.getId(), orderId, amount);
        return invoice;
    }

    @Override
    public List<Invoice> myInvoices(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return this.list(new LambdaQueryWrapper<Invoice>()
                .eq(Invoice::getUserId, userId)
                .orderByDesc(Invoice::getId));
    }

    @Override
    public List<Invoice> adminList(Integer status, String keyword) {
        LambdaQueryWrapper<Invoice> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(Invoice::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            wrapper.and(w -> w.like(Invoice::getTitle, k)
                    .or().like(Invoice::getInvoiceNo, k)
                    .or().like(Invoice::getTaxNo, k));
        }
        wrapper.orderByDesc(Invoice::getId);
        return this.list(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Invoice issue(Long id, Long operatorId) {
        Invoice invoice = this.getById(id);
        if (invoice == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票记录不存在");
        }
        if (invoice.getStatus() != null && invoice.getStatus() != STATUS_PENDING) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有待开票的申请可以开票");
        }
        LocalDateTime now = LocalDateTime.now();
        String invoiceNo = "INV" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

        // 条件更新防并发重复开票（两个管理员同时点开票只会成功一次）
        boolean ok = this.update(new LambdaUpdateWrapper<Invoice>()
                .eq(Invoice::getId, id)
                .eq(Invoice::getStatus, STATUS_PENDING)
                .set(Invoice::getStatus, STATUS_ISSUED)
                .set(Invoice::getInvoiceNo, invoiceNo)
                .set(Invoice::getIssueTime, now));
        if (!ok) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "发票状态已变更，请刷新后重试");
        }
        auditClient.record(operatorId, null, "开具电子发票", "POST /api/payment/invoice/{id}/issue",
                "{\"invoiceId\":" + id + ",\"invoiceNo\":\"" + invoiceNo + "\"}", true);
        return this.getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Invoice redFlush(Long id, String reason, Long operatorId) {
        Invoice invoice = this.getById(id);
        if (invoice == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "发票记录不存在");
        }
        if (invoice.getStatus() == null || invoice.getStatus() != STATUS_ISSUED) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只有已开票的发票可以红冲");
        }
        boolean ok = this.update(new LambdaUpdateWrapper<Invoice>()
                .eq(Invoice::getId, id)
                .eq(Invoice::getStatus, STATUS_ISSUED)
                .set(Invoice::getStatus, STATUS_RED_FLUSH)
                .set(Invoice::getRemark, StringUtils.hasText(reason) ? reason.trim() : "管理端红冲"));
        if (!ok) {
            throw new BizException(ErrorCode.PAY_STATUS_ERROR, "发票状态已变更，请刷新后重试");
        }
        auditClient.record(operatorId, null, "红冲电子发票", "POST /api/payment/invoice/{id}/red-flush",
                "{\"invoiceId\":" + id + ",\"reason\":\"" + (reason == null ? "" : reason) + "\"}", true);
        return this.getById(id);
    }

    /**
     * 取订单信息（Result 包装：字段在 data 层）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchOrderInfo(Long orderId) {
        try {
            Map<String, Object> resp = restTemplate.getForObject(
                    gatewayUrl + "/api/order/internal/order-info?orderId=" + orderId, Map.class);
            if (resp == null || !(resp.get("data") instanceof Map)) {
                throw new BizException(ErrorCode.ORDER_NOT_FOUND);
            }
            return (Map<String, Object>) resp.get("data");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("查询订单信息失败：orderId={}, err={}", orderId, e.getMessage());
            throw new BizException(ErrorCode.ORDER_NOT_FOUND, "订单信息查询失败，请稍后重试");
        }
    }
}
