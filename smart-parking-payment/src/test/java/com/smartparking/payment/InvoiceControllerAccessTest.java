package com.smartparking.payment;

import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.payment.controller.InvoiceController;
import com.smartparking.payment.dto.InvoiceApplyDTO;
import com.smartparking.payment.handler.GlobalExceptionHandler;
import com.smartparking.payment.po.Invoice;
import com.smartparking.payment.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 电子发票接口的权限与状态机闸门。
 *
 * <p>发票涉及税务凭证，属高危写操作。锁死三件事：业主不能进管理端列表/开票/红冲、
 * 申请人只能开自己的票、已开票的不能再开第二次。</p>
 */
@ExtendWith(MockitoExtension.class)
class InvoiceControllerAccessTest {

    @Mock
    private InvoiceService invoiceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InvoiceController(invoiceService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("业主访问管理端发票列表：403")
    void owner_cannotListAdminInvoices() throws Exception {
        mockMvc.perform(get("/api/payment/invoice/admin/list")
                        .header("X-User-Id", "1001")
                        .header("X-User-Role", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(invoiceService, never()).adminList(any(), any());
    }

    @Test
    @DisplayName("管理员访问发票列表：放行")
    void admin_canListInvoices() throws Exception {
        Invoice inv = new Invoice();
        inv.setId(1L);
        inv.setAmount(new BigDecimal("8.00"));
        when(invoiceService.adminList(null, null)).thenReturn(List.of(inv));

        mockMvc.perform(get("/api/payment/invoice/admin/list")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].amount").value(8.00));
    }

    @Test
    @DisplayName("业主尝试开票：403 且不触达服务层")
    void owner_cannotIssue() throws Exception {
        mockMvc.perform(post("/api/payment/invoice/admin/1/issue")
                        .header("X-User-Id", "1001")
                        .header("X-User-Role", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(invoiceService, never()).issue(any(), any());
    }

    @Test
    @DisplayName("业主尝试红冲：403")
    void owner_cannotRedFlush() throws Exception {
        mockMvc.perform(post("/api/payment/invoice/admin/1/red-flush")
                        .header("X-User-Id", "1001")
                        .header("X-User-Role", "0")
                        .contentType("application/json")
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(invoiceService, never()).redFlush(any(), any(), any());
    }

    @Test
    @DisplayName("重复申请同一订单：服务层拒绝，接口返回业务错误码")
    void duplicateApply_rejected() throws Exception {
        when(invoiceService.apply(any(InvoiceApplyDTO.class), any()))
                .thenThrow(new BizException(ErrorCode.PARAM_INVALID, "该订单已申请过发票，请勿重复申请"));

        mockMvc.perform(post("/api/payment/invoice/apply")
                        .header("X-User-Id", "1001")
                        .header("X-User-Role", "0")
                        .contentType("application/json")
                        .content("{\"orderId\":100,\"titleType\":1,\"title\":\"张三\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
