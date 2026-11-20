package com.smartparking.device;

import com.smartparking.device.controller.DeviceController;
import com.smartparking.device.handler.GlobalExceptionHandler;
import com.smartparking.device.service.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 设备服务内部接口契约测试（provider 侧，standalone MockMvc 范式）。
 *
 * <p>覆盖的消费者（MQTT 设备链路）：
 * <ul>
 *   <li>设备心跳上报 → POST /api/device/internal/heartbeat?deviceNo=</li>
 *   <li>设备状态变更 → PUT /api/device/internal/{deviceNo}/status?status=</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DeviceInternalApiContractTest {

    @Mock
    private DeviceService deviceService;

    @InjectMocks
    private DeviceController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /internal/heartbeat：Result<Void> 成功契约且 deviceNo 透传")
    void heartbeat_returnsResultSuccess() throws Exception {
        mockMvc.perform(post("/api/device/internal/heartbeat").param("deviceNo", "CAM-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(deviceService).updateHeartbeat(eq("CAM-001"));
    }

    @Test
    @DisplayName("PUT /internal/{deviceNo}/status：路径变量与 query 参数均正确绑定")
    void updateStatus_bindsPathVariableAndParam() throws Exception {
        mockMvc.perform(put("/api/device/internal/CAM-001/status").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(deviceService).updateStatus(eq("CAM-001"), eq(1));
    }

    @Test
    @DisplayName("PUT /internal/{deviceNo}/status：缺 status 时转 Result.fail（调用方按 code!=200 降级）")
    void updateStatus_missingStatus_returnsFailContract() throws Exception {
        mockMvc.perform(put("/api/device/internal/CAM-001/status"))
                .andExpect(status().isOk())
                // DEF-02：缺参数统一 PARAM_INVALID(400)
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("POST /internal/gate/release：支付联动开闸参数透传，通道结果放在 data.success 而非 HTTP 状态码")
    void releaseForPayment_passesParamsAndKeepsHttp200() throws Exception {
        // 约定：开闸失败也是 200 + data.success=false —— 钱已收了，调用方不该按 4xx/5xx 重试
        when(deviceService.releaseForPayment(eq("PAY202611191605014929"), eq("9003"), eq("支付成功放行"), isNull()))
                .thenReturn(Map.of("success", true, "deviceNo", "GATE-A-002", "vendor", "sandbox"));

        mockMvc.perform(post("/api/device/internal/gate/release")
                        .param("payNo", "PAY202611191605014929")
                        .param("orderId", "9003")
                        .param("reason", "支付成功放行"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.deviceNo").value("GATE-A-002"))
                .andExpect(jsonPath("$.data.vendor").value("sandbox"));

        verify(deviceService).releaseForPayment(eq("PAY202611191605014929"), eq("9003"), eq("支付成功放行"), isNull());
    }

    @Test
    @DisplayName("POST /internal/gate/release：所有参数都可省略（payment 只传 payNo 也能走通）")
    void releaseForPayment_allParamsOptional() throws Exception {
        when(deviceService.releaseForPayment(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Map.of("success", false, "message", "无在线道闸，未下发开闸指令"));

        mockMvc.perform(post("/api/device/internal/gate/release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.success").value(false));
    }
}
