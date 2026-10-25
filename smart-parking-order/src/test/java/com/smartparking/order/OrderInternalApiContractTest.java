package com.smartparking.order;

import com.smartparking.order.client.AuditClient;
import com.smartparking.order.client.VehicleClient;
import com.smartparking.order.controller.ParkingOrderController;
import com.smartparking.order.handler.GlobalExceptionHandler;
import com.smartparking.order.service.ParkingOrderService;
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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单服务内部接口契约测试（provider 侧，standalone MockMvc 范式）。
 *
 * <p>覆盖的消费者：
 * <ul>
 *   <li>payment/PaymentRecordServiceImpl.paymentSuccess()  → POST /internal/confirm-payment?orderId=&amount=
 *       （amount 用 BigDecimal.toString() 拼接，provider 必须按 BigDecimal 绑定）</li>
 *   <li>payment/PaymentTimeoutTask.processTimeout()        → POST /internal/payment-timeout?orderId=</li>
 *   <li>payment 退款回调                                    → POST /internal/refund-callback?orderId=</li>
 *   <li>payment/handleShareIncome()                        → GET /internal/order-info?orderId=
 *       （data.spaceId 必须是 Number，用 ((Number) map.get("spaceId")).longValue() 解析）</li>
 *   <li>device/DeviceServiceImpl.handlePlateRecognition()  → POST /internal/device-entry?plateNo=&confidence=&deviceNo=
 *       （data 层读 recognized/orderNo/spaceNo/message 四字段控制道闸）</li>
 *   <li>device/DeviceServiceImpl.mockExitRecognize()       → POST /internal/device-exit?plateNo=&deviceNo=</li>
 *   <li>stats/ParkingStatsServiceImpl.queryOngoingCount()  → GET /internal/ongoing-count
 *       （data 必须是裸 Number，用 ((Number) data).longValue() 解析）</li>
 *   <li>业主端 confirm-plate                                → POST /internal/confirm-plate?plateNo=</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class OrderInternalApiContractTest {

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
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /internal/confirm-payment：BigDecimal 金额按消费者字符串形式绑定")
    void confirmPayment_bigDecimalAmount_passesToService() throws Exception {
        // 消费者：gatewayUrl + "...confirm-payment?orderId=" + orderId + "&amount=" + record.getAmount()
        mockMvc.perform(post("/api/order/internal/confirm-payment")
                        .param("orderId", "10086")
                        .param("amount", "25.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(parkingOrderService).confirmPayment(eq(10086L), eq(new BigDecimal("25.50")));
    }

    @Test
    @DisplayName("POST /internal/payment-timeout 与 refund-callback：Result<Void> 成功契约")
    void paymentTimeoutAndRefund_returnsResultSuccess() throws Exception {
        mockMvc.perform(post("/api/order/internal/payment-timeout").param("orderId", "10086"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(parkingOrderService).timeoutClose(eq(10086L));

        mockMvc.perform(post("/api/order/internal/refund-callback").param("orderId", "10086"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(parkingOrderService).refundOrder(eq(10086L));
    }

    @Test
    @DisplayName("GET /internal/order-info：data.spaceId 为 Number（分润链路强依赖）")
    void orderInfo_spaceIdIsNumber() throws Exception {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("orderId", 10086L);
        info.put("orderNo", "P202610250001");
        info.put("spaceId", 1001L);
        info.put("spaceNo", "A-001");
        info.put("amount", new BigDecimal("25.50"));
        info.put("status", 1);
        when(parkingOrderService.getOrderInfo(10086L)).thenReturn(info);

        mockMvc.perform(get("/api/order/internal/order-info").param("orderId", "10086"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderId").value(10086))
                .andExpect(jsonPath("$.data.spaceId").value(1001))
                .andExpect(jsonPath("$.data.spaceNo").value("A-001"))
                .andExpect(jsonPath("$.data.amount").value(25.50));

        verify(parkingOrderService).getOrderInfo(eq(10086L));
    }

    @Test
    @DisplayName("GET /internal/ongoing-count：data 为裸数字（stats 按 Number 解析）")
    void ongoingCount_dataIsNumber() throws Exception {
        // ParkingOrderService 继承 IService，count(...) 默认返回 0；契约上 data 必须是裸数字
        mockMvc.perform(get("/api/order/internal/ongoing-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isNumber());
    }

    @Test
    @DisplayName("POST /internal/device-entry：data 含 recognized/orderNo/spaceNo/message（道闸联动依据）")
    void deviceEntry_returnsGateControlFields() throws Exception {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("recognized", true);
        entry.put("orderNo", "P202610250001");
        entry.put("spaceNo", "A-001");
        entry.put("spaceId", 1001L);
        entry.put("message", "自动入场成功");
        when(parkingOrderService.deviceEntry(eq("京A12345"), eq(95), eq("CAM-001"))).thenReturn(entry);

        // device 侧 URLEncoder.encode 车牌后拼接 query
        mockMvc.perform(post("/api/order/internal/device-entry")
                        .param("plateNo", "京A12345")
                        .param("confidence", "95")
                        .param("deviceNo", "CAM-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.recognized").value(true))
                .andExpect(jsonPath("$.data.orderNo").value("P202610250001"))
                .andExpect(jsonPath("$.data.spaceNo").value("A-001"))
                .andExpect(jsonPath("$.data.message").value("自动入场成功"));

        verify(parkingOrderService).deviceEntry("京A12345", 95, "CAM-001");
    }

    @Test
    @DisplayName("POST /internal/device-exit：data 层透传结算结果")
    void deviceExit_returnsSettleResult() throws Exception {
        Map<String, Object> exit = new LinkedHashMap<>();
        exit.put("plateNo", "京A12345");
        exit.put("orderNo", "P202610250001");
        exit.put("amount", new BigDecimal("25.50"));
        exit.put("durationMinutes", 95);
        when(parkingOrderService.deviceExit(eq("京A12345"), eq("CAM-002"))).thenReturn(exit);

        mockMvc.perform(post("/api/order/internal/device-exit")
                        .param("plateNo", "京A12345")
                        .param("deviceNo", "CAM-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("P202610250001"))
                .andExpect(jsonPath("$.data.amount").value(25.50))
                .andExpect(jsonPath("$.data.durationMinutes").value(95));

        verify(parkingOrderService).deviceExit("京A12345", "CAM-002");
    }

    @Test
    @DisplayName("POST /internal/confirm-plate：deviceNo 缺省 HUMAN，走 deviceEntry 同一链路")
    void confirmPlate_defaultsToHumanDevice() throws Exception {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("recognized", true);
        entry.put("orderNo", "P202610250002");
        entry.put("spaceNo", "B-002");
        entry.put("message", "人工确认入场成功");
        when(parkingOrderService.deviceEntry(eq("京A54321"), eq(100), eq("HUMAN"))).thenReturn(entry);

        mockMvc.perform(post("/api/order/internal/confirm-plate").param("plateNo", "京A54321"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("P202610250002"));

        verify(parkingOrderService).deviceEntry("京A54321", 100, "HUMAN");
    }

    @Test
    @DisplayName("POST /internal/confirm-payment：缺 orderId 时转 Result.fail（消费者按 code!=200 降级）")
    void confirmPayment_missingOrderId_returnsFailContract() throws Exception {
        mockMvc.perform(post("/api/order/internal/confirm-payment").param("amount", "25.50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
