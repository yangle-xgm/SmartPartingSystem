package com.smartparking.order.controller;

import com.smartparking.order.client.AuditClient;
import com.smartparking.order.client.VehicleClient;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.ParkingOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单控制器测试（纯 MockMvc standalone，不加载 Spring 上下文 / 不连 MyBatis / 不连 DB）。
 * 直接构造 Controller 并注入 Mock 依赖，验证入场/出场/实时计费三条核心链路的 HTTP 映射与统一响应契约。
 */
@ExtendWith(MockitoExtension.class)
class ParkingOrderControllerTest {

    @Mock
    private ParkingOrderService parkingOrderService;
    @Mock
    private AuditClient auditClient;
    @Mock
    private VehicleClient vehicleClient;

    @InjectMocks
    private ParkingOrderController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void entry_createsOrder() throws Exception {
        ParkingOrder order = new ParkingOrder();
        order.setOrderNo("P202610130001");
        when(parkingOrderService.createEntryOrder(anyLong(), anyString(), anyLong(), anyString(), any(), any()))
                .thenReturn(order);

        mockMvc.perform(post("/api/order/entry")
                        .param("vehicleId", "1")
                        .param("plateNo", "浙A12345")
                        .param("spaceId", "1")
                        .param("spaceNo", "A01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("P202610130001"));
    }

    @Test
    void exit_completesOrder() throws Exception {
        ParkingOrder order = new ParkingOrder();
        order.setOrderNo("P202610130001");
        when(parkingOrderService.completeOrder("P202610130001")).thenReturn(order);

        mockMvc.perform(post("/api/order/exit/P202610130001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("P202610130001"));
    }

    @Test
    void calculate_returnsAmount() throws Exception {
        when(parkingOrderService.calculateAmount(anyString())).thenReturn(BigDecimal.TEN);

        mockMvc.perform(get("/api/order/calculate").param("orderNo", "P202610130001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(10));
    }
}
