package com.smartparking.vehicle;

import com.smartparking.vehicle.controller.MonthlyCardInternalController;
import com.smartparking.vehicle.handler.GlobalExceptionHandler;
import com.smartparking.vehicle.po.MonthlyCardOrder;
import com.smartparking.vehicle.service.MonthlyCardService;
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
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 月卡内部接口契约测试（provider 侧）。
 *
 * <p>消费者是支付服务：{@code PaymentRecordServiceImpl}
 * <ul>
 *   <li>{@code createMonthlyPayment()} → GET /api/vehicle/internal/monthly/info?monthlyOrderId=
 *       必须能拿到 {@code amount} / {@code payStatus} / {@code userId}：金额以此为准（忽略前端传值），
 *       {@code payStatus=1} 时支付服务拒绝重复支付。</li>
 *   <li>{@code paymentSuccess()} 月卡分支 → POST /api/vehicle/internal/monthly/mark-paid?monthlyOrderId=&payNo=&transactionId=
 *       必须返回 {@code message}（H5 直接展示），且 {@code payNo} 允许缺省（渠道重试时可能丢了交易号）。</li>
 * </ul>
 *
 * <p>路径前缀刻意是 {@code /api/vehicle/internal/monthly}，不是
 * {@code /api/vehicle/monthly/internal/monthly} —— 这是把内部接口与业务控制器分开的原因。
 */
@ExtendWith(MockitoExtension.class)
class MonthlyCardInternalApiContractTest {

    @Mock
    private MonthlyCardService monthlyCardService;

    @InjectMocks
    private MonthlyCardInternalController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MonthlyCardOrder order() {
        MonthlyCardOrder o = new MonthlyCardOrder();
        o.setId(9001L);
        o.setOrderNo("MC20261120120000001");
        o.setUserId(1001L);
        o.setVehicleId(4L);
        o.setMonths(3);
        o.setUnitPrice(new BigDecimal("300.00"));
        o.setAmount(new BigDecimal("900.00"));
        o.setPayStatus(0);
        o.setExpireAfter(LocalDateTime.of(2027, 2, 20, 12, 0));
        return o;
    }

    @Test
    @DisplayName("GET /internal/monthly/info：支付服务据 data.amount 建流水，忽略前端传值")
    void info_returnsAmountAndPayStatus() throws Exception {
        when(monthlyCardService.getMonthlyOrder(9001L)).thenReturn(order());

        mockMvc.perform(get("/api/vehicle/internal/monthly/info").param("monthlyOrderId", "9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(9001))
                .andExpect(jsonPath("$.data.amount").value(900.00))
                .andExpect(jsonPath("$.data.payStatus").value(0))
                .andExpect(jsonPath("$.data.userId").value(1001));

        verify(monthlyCardService).getMonthlyOrder(9001L);
    }

    @Test
    @DisplayName("GET /internal/monthly/info：订单不存在时 data 为空，支付服务据此拒绝建流水")
    void info_notFound_returnsNullData() throws Exception {
        when(monthlyCardService.getMonthlyOrder(9999L)).thenReturn(null);

        mockMvc.perform(get("/api/vehicle/internal/monthly/info").param("monthlyOrderId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("POST /internal/monthly/mark-paid：透传 payNo/transactionId，返回 message 供 H5 展示")
    void markPaid_passesThroughPayNoAndReturnsMessage() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", "MC20261120120000001");
        result.put("payNo", "PAY202611201201011234");
        result.put("amount", new BigDecimal("900.00"));
        result.put("months", 3);
        result.put("expireAfter", LocalDateTime.of(2027, 2, 20, 12, 0));
        result.put("message", "充值成功，月卡有效期至 2027-02-20");
        when(monthlyCardService.markPaid(9001L, "PAY202611201201011234", "2026112022001412345"))
                .thenReturn(result);

        mockMvc.perform(post("/api/vehicle/internal/monthly/mark-paid")
                        .param("monthlyOrderId", "9001")
                        .param("payNo", "PAY202611201201011234")
                        .param("transactionId", "2026112022001412345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.payNo").value("PAY202611201201011234"))
                .andExpect(jsonPath("$.data.months").value(3))
                .andExpect(jsonPath("$.data.message").value("充值成功，月卡有效期至 2027-02-20"));

        verify(monthlyCardService).markPaid(eq(9001L), eq("PAY202611201201011234"), eq("2026112022001412345"));
    }

    @Test
    @DisplayName("POST /internal/monthly/mark-paid：payNo/transactionId 可缺省（渠道重试可能不回传交易号）")
    void markPaid_allowsMissingPayNo() throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", "MC20261120120000001");
        result.put("message", "充值成功，月卡有效期至 2027-02-20");
        when(monthlyCardService.markPaid(eq(9001L), isNull(), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/vehicle/internal/monthly/mark-paid").param("monthlyOrderId", "9001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.message").value("充值成功，月卡有效期至 2027-02-20"));

        verify(monthlyCardService).markPaid(eq(9001L), isNull(), isNull());
    }
}
