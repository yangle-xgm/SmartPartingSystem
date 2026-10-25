package com.smartparking.payment;

import com.smartparking.payment.controller.PaymentRecordController;
import com.smartparking.payment.handler.GlobalExceptionHandler;
import com.smartparking.payment.service.PaymentRecordService;
import com.smartparking.payment.task.PaymentTimeoutTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 支付服务内部接口契约测试（provider 侧，standalone MockMvc 范式）。
 *
 * <p>覆盖的消费者：
 * <ul>
 *   <li>stats/ParkingStatsServiceImpl.queryPaidIncome() → GET /api/payment/internal/income-summary?startDate=&endDate=
 *       （data.total 必须是 Number，用 new BigDecimal(total.toString()) 解析）</li>
 *   <li>PaymentTimeoutTask 演示触发 → POST /api/payment/internal/timeout-scan（data.message 固定文案）</li>
 *   <li>POST /internal/success、/internal/refund：Result&lt;Void&gt; 成功契约（运维/回调链路）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PaymentInternalApiContractTest {

    @Mock
    private PaymentRecordService paymentRecordService;

    @Mock
    private PaymentTimeoutTask paymentTimeoutTask;

    @InjectMocks
    private PaymentRecordController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /internal/income-summary：data.total 为 Number 且回显日期区间")
    void incomeSummary_totalIsNumber() throws Exception {
        // stats 侧：LocalDate.toString() 直接拼 URL（yyyy-MM-dd）
        when(paymentRecordService.sumPaidAmount(
                LocalDate.parse("2026-10-01").atStartOfDay(),
                LocalDate.parse("2026-10-31").atTime(LocalTime.MAX)))
                .thenReturn(new BigDecimal("1280.00"));

        mockMvc.perform(get("/api/payment/internal/income-summary")
                        .param("startDate", "2026-10-01")
                        .param("endDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1280.00))
                .andExpect(jsonPath("$.data.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.data.endDate").value("2026-10-31"));

        verify(paymentRecordService).sumPaidAmount(
                LocalDateTime.parse("2026-10-01T00:00"),
                LocalDateTime.parse("2026-10-31T23:59:59.999999999"));
    }

    @Test
    @DisplayName("GET /internal/income-summary：非法日期按 RuntimeException 转 Result.fail（消费者回退统计表）")
    void incomeSummary_invalidDate_returnsFailContract() throws Exception {
        mockMvc.perform(get("/api/payment/internal/income-summary")
                        .param("startDate", "not-a-date")
                        .param("endDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("POST /internal/success 与 /internal/refund：Result<Void> 成功契约")
    void successAndRefund_returnsResultSuccess() throws Exception {
        mockMvc.perform(post("/api/payment/internal/success")
                        .param("payNo", "PAY202610250001")
                        .param("transactionId", "WX4200001234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(paymentRecordService).paymentSuccess(eq("PAY202610250001"), eq("WX4200001234"));

        mockMvc.perform(post("/api/payment/internal/refund").param("payNo", "PAY202610250001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(paymentRecordService).refund(eq("PAY202610250001"));
    }

    @Test
    @DisplayName("POST /internal/timeout-scan：data.message 固定文案")
    void timeoutScan_returnsFixedMessage() throws Exception {
        mockMvc.perform(post("/api/payment/internal/timeout-scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.message").value("支付超时扫描已执行"));

        verify(paymentTimeoutTask).scanNow();
    }
}
