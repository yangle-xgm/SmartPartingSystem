package com.smartparking.payment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.smartparking.payment.dto.InvoiceApplyDTO;
import com.smartparking.payment.po.Invoice;

import java.util.List;

/**
 * 电子发票服务。
 */
public interface InvoiceService extends IService<Invoice> {

    /**
     * 业主申请开票：校验订单已支付且属于本人，一单一票。
     */
    Invoice apply(InvoiceApplyDTO dto, Long currentUserId);

    /**
     * 我的发票列表。
     */
    List<Invoice> myInvoices(Long userId);

    /**
     * 管理端分页列表（status 为空查全部）。
     */
    List<Invoice> adminList(Integer status, String keyword);

    /**
     * 开票：生成发票号并把状态从「待开票」推进到「已开票」。
     */
    Invoice issue(Long id, Long operatorId);

    /**
     * 红冲：仅已开票可红冲，红冲后该订单不再重开。
     */
    Invoice redFlush(Long id, String reason, Long operatorId);
}
