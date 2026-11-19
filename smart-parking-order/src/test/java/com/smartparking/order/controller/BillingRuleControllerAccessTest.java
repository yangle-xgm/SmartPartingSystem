package com.smartparking.order.controller;

import com.smartparking.order.client.AuditClient;
import com.smartparking.order.handler.GlobalExceptionHandler;
import com.smartparking.order.service.BillingRuleService;
import com.smartparking.order.service.ParkingOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计费规则写接口闸门。
 *
 * <p>单价/封顶直接决定应收金额，是本系统最容易「一键改出资损」的地方。这里锁：
 * 业主不能改；日封顶低于一个计费单位要拒；有进行中订单引用时不能删。</p>
 */
@ExtendWith(MockitoExtension.class)
class BillingRuleControllerAccessTest {

    @Mock
    private BillingRuleService billingRuleService;

    @Mock
    private ParkingOrderService parkingOrderService;

    @Mock
    private AuditClient auditClient;

    private MockMvc mockMvc;

    private static final String VALID_BODY = "{\"ruleName\":\"临时车标准\",\"vehicleType\":1,"
            + "\"freeMinutes\":30,\"unitPrice\":2.5,\"unitDuration\":60,\"dailyCap\":30,\"status\":1}";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BillingRuleController(billingRuleService, parkingOrderService, auditClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("业主新增计费规则：403 且不落库")
    void owner_cannotCreate() throws Exception {
        mockMvc.perform(post("/api/order/billing-rules")
                        .header("X-User-Id", "1001")
                        .header("X-User-Role", "0")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(billingRuleService, never()).save(any());
    }

    @Test
    @DisplayName("管理员新增合法规则：放行并落库")
    void admin_canCreate() throws Exception {
        when(billingRuleService.save(any())).thenReturn(true);

        mockMvc.perform(post("/api/order/billing-rules")
                        .header("X-User-Id", "1")
                        .header("X-User-Role", "2")
                        .contentType("application/json")
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(billingRuleService).save(any());
    }

    @Test
    @DisplayName("日封顶低于一个计费单位的价格：拒绝")
    void dailyCapBelowUnitPrice_rejected() throws Exception {
        String body = "{\"ruleName\":\"怪规则\",\"vehicleType\":1,\"freeMinutes\":0,"
                + "\"unitPrice\":10,\"unitDuration\":60,\"dailyCap\":5}";

        mockMvc.perform(post("/api/order/billing-rules")
                        .header("X-User-Role", "1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(billingRuleService, never()).save(any());
    }

    @Test
    @DisplayName("月租车规则缺月租价格：拒绝")
    void monthlyRuleWithoutPrice_rejected() throws Exception {
        String body = "{\"ruleName\":\"月租\",\"vehicleType\":2,\"freeMinutes\":0,"
                + "\"unitPrice\":2.5,\"unitDuration\":60}";

        mockMvc.perform(post("/api/order/billing-rules")
                        .header("X-User-Role", "1")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(billingRuleService, never()).save(any());
    }

    @Test
    @DisplayName("仍有进行中订单引用时不许删除规则")
    void deleteRuleInUse_rejected() throws Exception {
        when(billingRuleService.getById(9L)).thenReturn(new com.smartparking.order.po.BillingRule());
        when(parkingOrderService.count(any())).thenReturn(3L);

        mockMvc.perform(delete("/api/order/billing-rules/9")
                        .header("X-User-Role", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(billingRuleService, never()).removeById(9L);
    }
}
