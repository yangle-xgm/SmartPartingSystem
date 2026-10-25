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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
