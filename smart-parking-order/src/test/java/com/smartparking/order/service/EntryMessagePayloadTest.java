package com.smartparking.order.service;

import com.smartparking.order.client.AuditClient;
import com.smartparking.order.client.ParkingSpaceClient;
import com.smartparking.order.client.VehicleClient;
import com.smartparking.order.mapper.ParkingOrderMapper;
import com.smartparking.order.po.BillingRule;
import com.smartparking.order.po.ParkingOrder;
import com.smartparking.order.service.impl.ParkingOrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 入场 MQ 报文防回归测试（2026-11-17 真机验证发现的缺陷）：
 * <p>
 * 通知服务按 {@code spaceNo} 拼「入场，停放在 %s」，而生产方此前只投递了 {@code spaceId}，
 * 导致真机上看到的通知是「…入场，停放在 」后面空白。这条断言锁住报文契约。
 */
@ExtendWith(MockitoExtension.class)
class EntryMessagePayloadTest {

    @Mock
    private BillingRuleService billingRuleService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private AuditClient auditClient;
    @Mock
    private ParkingSpaceClient parkingSpaceClient;
    @Mock
    private VehicleClient vehicleClient;
    @Mock
    private ParkingOrderMapper parkingOrderMapper;

    private ParkingOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ParkingOrderServiceImpl(billingRuleService, rabbitTemplate, auditClient,
                parkingSpaceClient, vehicleClient);
        ReflectionTestUtils.setField(service, "baseMapper", parkingOrderMapper);

        BillingRule rule = new BillingRule();
        rule.setId(1L);
        when(billingRuleService.getApplicableRule(anyInt(), any())).thenReturn(rule);
        when(parkingSpaceClient.claimSpace(anyLong())).thenReturn(true);
        when(parkingOrderMapper.selectOne(any(), anyBoolean())).thenReturn(null);
    }

    @Test
    @DisplayName("入场报文必须携带 spaceNo，且车牌为明文（消费者按车牌反查车主）")
    void entryMessageCarriesSpaceNoAndPlainPlate() {
        AtomicReference<String> savedPlate = new AtomicReference<>();
        when(parkingOrderMapper.insert(any(ParkingOrder.class))).thenAnswer(inv -> {
            savedPlate.set(((ParkingOrder) inv.getArgument(0)).getPlateNo());
            return 1;
        });

        ParkingOrder returned = service.createEntryOrder(1L, "京B81242", 10L, "A-005", 0, "蓝");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("parking.order.exchange"), eq("order.entry"), payload.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> msg = (Map<String, Object>) payload.getValue();
        assertEquals("A-005", msg.get("spaceNo"), "报文缺 spaceNo 会让通知文案变成『停放在 』");
        assertEquals(10L, msg.get("spaceId"));
        assertEquals("ENTRY", msg.get("type"));
        assertEquals("京B81242", msg.get("plateNo"), "通知服务按车牌反查车主，必须是明文");
        assertNotNull(msg.get("orderNo"));
    }

    @Test
    @DisplayName("入库车牌仍为密文，接口返回明文")
    void plateIsEncryptedInDbAndPlainInResponse() {
        AtomicReference<String> savedPlate = new AtomicReference<>();
        when(parkingOrderMapper.insert(any(ParkingOrder.class))).thenAnswer(inv -> {
            savedPlate.set(((ParkingOrder) inv.getArgument(0)).getPlateNo());
            return 1;
        });

        ParkingOrder returned = service.createEntryOrder(2L, "京A80946", 11L, "A-004", 0, "蓝");

        assertNotNull(savedPlate.get());
        assertNotEquals("京A80946", savedPlate.get(), "库里不能存明文车牌");
        assertTrue(savedPlate.get().length() > 16, "密文长度应明显长于车牌");
        assertEquals("京A80946", returned.getPlateNo(), "接口出参必须是明文");
        assertEquals("A-004", returned.getSpaceNo());
    }
}
