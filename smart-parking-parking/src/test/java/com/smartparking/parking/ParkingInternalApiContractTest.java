package com.smartparking.parking;

import com.smartparking.parking.controller.ParkingSpaceController;
import com.smartparking.parking.handler.GlobalExceptionHandler;
import com.smartparking.parking.po.ParkingSpace;
import com.smartparking.parking.service.ParkingSpaceService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 车位服务内部接口契约测试（provider 侧，standalone MockMvc 范式）。
 *
 * <p>覆盖的消费者（order/client/ParkingSpaceClient.java）：
 * <ul>
 *   <li>updateStatus()  → PUT /api/parking/internal/space/{id}/status?status=</li>
 *   <li>getSpaceInfo()  → GET /api/parking/internal/space-info/{id}
 *       （payment 分润链路 handleShareIncome 依赖 data 层的 id/spaceNo/ownerId/isShared 四个字段名，
 *        且 isShared 必须是 Boolean，用 Boolean.TRUE.equals 判断）</li>
 *   <li>claimSpace()    → POST /api/parking/internal/space/{id}/claim（data 必须是裸 Boolean）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ParkingInternalApiContractTest {

    @Mock
    private ParkingSpaceService parkingSpaceService;

    @InjectMocks
    private ParkingSpaceController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("PUT /api/parking/internal/space/{id}/status：Result<Void> 成功契约")
    void updateStatus_returnsResultSuccess() throws Exception {
        mockMvc.perform(put("/api/parking/internal/space/1001/status").param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(parkingSpaceService).updateStatus(eq(1001L), eq(1));
    }

    @Test
    @DisplayName("POST /api/parking/internal/space/{id}/claim：data 为裸 Boolean（claimSpace 用 Boolean.TRUE.equals 解析）")
    void claimSpace_returnsBooleanData() throws Exception {
        when(parkingSpaceService.claimSpace(1001L)).thenReturn(true);

        mockMvc.perform(post("/api/parking/internal/space/1001/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));

        // 占用失败（非空闲态）时同样包装为 Result，data=false，消费者据此跳过入场
        when(parkingSpaceService.claimSpace(1002L)).thenReturn(false);
        mockMvc.perform(post("/api/parking/internal/space/1002/claim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    @DisplayName("GET /api/parking/internal/space-info/{id}：data 含 id/spaceNo/ownerId/isShared 四字段（分润链路依赖）")
    void spaceInfo_returnsShareFields() throws Exception {
        ParkingSpace space = new ParkingSpace();
        space.setId(1001L);
        space.setSpaceNo("A-001");
        space.setOwnerId(5001L);
        space.setIsShared(1);
        when(parkingSpaceService.getById(1001L)).thenReturn(space);

        mockMvc.perform(get("/api/parking/internal/space-info/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.spaceNo").value("A-001"))
                .andExpect(jsonPath("$.data.ownerId").value(5001))
                // payment 侧 isShared 判 Boolean.TRUE.equals，未共享时必须是 false 而非 null
                .andExpect(jsonPath("$.data.isShared").value(true));
    }

    @Test
    @DisplayName("GET /api/parking/internal/space-info/{id}：车位不存在时 code=5201 且 data 缺省（消费者跳过分润）")
    void spaceInfo_notFound_returnsFailContract() throws Exception {
        when(parkingSpaceService.getById(9999L)).thenReturn(null);

        mockMvc.perform(get("/api/parking/internal/space-info/9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5201))
                .andExpect(jsonPath("$.message").value("车位不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/parking/internal/space-info/{id}：未共享车位 isShared=false")
    void spaceInfo_notShared_returnsFalse() throws Exception {
        ParkingSpace space = new ParkingSpace();
        space.setId(1002L);
        space.setSpaceNo("B-002");
        space.setOwnerId(5002L);
        space.setIsShared(0);
        when(parkingSpaceService.getById(1002L)).thenReturn(space);

        mockMvc.perform(get("/api/parking/internal/space-info/1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isShared").value(false));
    }

    @Test
    @DisplayName("GET /api/parking/internal/occupancy-summary：data 含 total/occupied 两个数字字段（stats 回填占用率依赖）")
    void occupancySummary_returnsTotalAndOccupied() throws Exception {
        when(parkingSpaceService.getOccupancySummary(null))
                .thenReturn(java.util.Map.of("total", 87, "occupied", 12));

        mockMvc.perform(get("/api/parking/internal/occupancy-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(87))
                .andExpect(jsonPath("$.data.occupied").value(12));

        verify(parkingSpaceService).getOccupancySummary(null);
    }
}
