package com.smartparking.notify;

import com.smartparking.notify.controller.NotificationController;
import com.smartparking.notify.handler.GlobalExceptionHandler;
import com.smartparking.notify.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知服务内部接口契约测试（provider 侧，standalone MockMvc 范式）。
 *
 * <p>覆盖的消费者（订单/设备链路在入场、出场、待支付时推送通知）：
 * <ul>
 *   <li>POST /api/notify/internal/entry   → type=1，content 含车牌与车位号</li>
 *   <li>POST /api/notify/internal/exit    → type=2，content 含时长与费用</li>
 *   <li>POST /api/notify/internal/payment → type=3，content 含订单号与金额</li>
 * </ul>
 * 三个接口均为 Result&lt;Void&gt; 成功契约，消费者仅以 HTTP 200 + code=200 判定送达。
 */
@ExtendWith(MockitoExtension.class)
class NotifyInternalApiContractTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /internal/entry：Result<Void> 成功契约，通知类型=1 且内容含车牌/车位")
    void sendEntryNotification_returnsResultSuccess() throws Exception {
        mockMvc.perform(post("/api/notify/internal/entry")
                        .param("userId", "5001")
                        .param("plateNo", "京A12345")
                        .param("spaceNo", "A-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(notificationService).sendNotification(
                eq(5001L), eq("车辆入场通知"),
                ArgumentMatchers.argThat((String content) ->
                        content.contains("京A12345") && content.contains("A-001")),
                eq(1));
    }

    @Test
    @DisplayName("POST /internal/exit：Result<Void> 成功契约，通知类型=2 且内容含时长/费用")
    void sendExitNotification_returnsResultSuccess() throws Exception {
        mockMvc.perform(post("/api/notify/internal/exit")
                        .param("userId", "5001")
                        .param("plateNo", "京A12345")
                        .param("duration", "95")
                        .param("amount", "25.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(notificationService).sendNotification(
                eq(5001L), eq("车辆出场通知"),
                ArgumentMatchers.argThat((String content) ->
                        content.contains("京A12345") && content.contains("95") && content.contains("25.50")),
                eq(2));
    }

    @Test
    @DisplayName("POST /internal/payment：Result<Void> 成功契约，通知类型=3 且内容含订单号/金额")
    void sendPaymentNotification_returnsResultSuccess() throws Exception {
        mockMvc.perform(post("/api/notify/internal/payment")
                        .param("userId", "5001")
                        .param("orderNo", "P202610250001")
                        .param("amount", "25.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(notificationService).sendNotification(
                eq(5001L), eq("缴费提醒"),
                ArgumentMatchers.argThat((String content) ->
                        content.contains("P202610250001") && content.contains("25.50")),
                eq(3));
    }

    @Test
    @DisplayName("POST /internal/entry：缺 userId 时转 Result.fail（调用方按 code!=200 降级）")
    void sendEntryNotification_missingUserId_returnsFailContract() throws Exception {
        mockMvc.perform(post("/api/notify/internal/entry")
                        .param("plateNo", "京A12345")
                        .param("spaceNo", "A-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
