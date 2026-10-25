package com.smartparking.stats;

import com.smartparking.stats.controller.ParkingStatsController;
import com.smartparking.stats.handler.GlobalExceptionHandler;
import com.smartparking.stats.service.ParkingStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 统计服务内部接口契约测试（provider 侧，standalone MockMvc 范式）。
 *
 * <p>POST /api/stats/internal/update 供各服务（MQ 消费/业务链路）回写统计数据：
 * type 走 query，业务数据走 JSON body，两侧必须保持一致才能保证 updateStats(type, data) 正确装配。
 */
@ExtendWith(MockitoExtension.class)
class StatsInternalApiContractTest {

    @Mock
    private ParkingStatsService parkingStatsService;

    @InjectMocks
    private ParkingStatsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /internal/update：type 走 query、data 走 body，Result<Void> 成功契约")
    void updateStats_queryTypeAndJsonBody() throws Exception {
        mockMvc.perform(post("/api/stats/internal/update")
                        .param("type", "income")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25.50,\"spaceId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 验证 body 被正确绑定为 Map 且 type 透传
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(parkingStatsService).updateStats(eq("income"), captor.capture());
        Map<String, Object> data = captor.getValue();
        assertEquals(BigDecimal.valueOf(25.50), new BigDecimal(String.valueOf(data.get("amount"))));
        assertEquals(1001, ((Number) data.get("spaceId")).intValue());
    }

    @Test
    @DisplayName("POST /internal/update：缺 type 时转 Result.fail（调用方按 code!=200 降级）")
    void updateStats_missingType_returnsFailContract() throws Exception {
        mockMvc.perform(post("/api/stats/internal/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":25.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
