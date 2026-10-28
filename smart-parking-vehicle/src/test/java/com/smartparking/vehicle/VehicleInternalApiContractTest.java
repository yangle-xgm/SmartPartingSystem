package com.smartparking.vehicle;

import com.smartparking.vehicle.controller.VehicleController;
import com.smartparking.vehicle.handler.GlobalExceptionHandler;
import com.smartparking.vehicle.po.Vehicle;
import com.smartparking.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 车辆服务内部接口契约测试（provider 侧，standalone MockMvc 范式）。
 *
 * <p>覆盖的消费者：
 * <ul>
 *   <li>order/client/VehicleClient.java getVehicleByPlate()  → GET /api/vehicle/internal/info/{plateNo}
 *       （URLEncoder.encode 中文车牌后拼接路径）</li>
 *   <li>order/client/VehicleClient.java listVehicleIdsByOwner() → GET /api/vehicle/internal/list-by-owner?ownerId=
 *       （订单列表数据隔离：query.setVehicleIds 逗号拼接）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class VehicleInternalApiContractTest {

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private VehicleController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/vehicle/internal/info/{plateNo}：中文车牌路径变量可正确绑定，data 为 Vehicle")
    void getInfoByPlate_urlEncodedChinesePlate_returnsVehicle() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(2001L);
        vehicle.setPlateNo("京A12345");
        vehicle.setOwnerId(5001L);
        when(vehicleService.getByPlateNo("京A12345")).thenReturn(vehicle);

        // 消费者（VehicleClient/OrderMessageConsumer）URLEncoder.encode 后发 HTTP，Servlet 容器 decode 后
        // controller 收到的就是原始中文车牌；URL 编解码由容器负责，MockMvc 不模拟该层，直接以原始中文请求，
        // 验证的是「provider 对中文路径变量的绑定契约」。
        mockMvc.perform(get("/api/vehicle/internal/info/京A12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2001))
                .andExpect(jsonPath("$.data.plateNo").value("京A12345"))
                .andExpect(jsonPath("$.data.ownerId").value(5001));

        verify(vehicleService).getByPlateNo("京A12345");
    }

    @Test
    @DisplayName("GET /api/vehicle/internal/info/{plateNo}：未注册车辆返回 fail 契约（code=5501，data 缺省）")
    void getInfoByPlate_unregistered_returnsFailContract() throws Exception {
        when(vehicleService.getByPlateNo("京B99999")).thenReturn(null);

        mockMvc.perform(get("/api/vehicle/internal/info/京B99999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(5501))
                .andExpect(jsonPath("$.message").value("车辆不存在"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/vehicle/internal/list-by-owner：data 为裸 Long 数组（订单隔离拼接 vehicleIds）")
    void listIdsByOwner_returnsLongArray() throws Exception {
        Vehicle v1 = new Vehicle();
        v1.setId(2001L);
        Vehicle v2 = new Vehicle();
        v2.setId(2002L);
        when(vehicleService.listByUserId(5001L)).thenReturn(List.of(v1, v2));

        mockMvc.perform(get("/api/vehicle/internal/list-by-owner").param("ownerId", "5001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0]").value(2001))
                .andExpect(jsonPath("$.data[1]").value(2002));

        verify(vehicleService).listByUserId(eq(5001L));
    }

    @Test
    @DisplayName("PUT /api/vehicle/internal/{id}/status：Result<Void> 成功契约")
    void updateStatus_returnsResultSuccess() throws Exception {
        mockMvc.perform(put("/api/vehicle/internal/2001/status").param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(vehicleService).updateStatus(eq(2001L), eq(2));
    }
}
