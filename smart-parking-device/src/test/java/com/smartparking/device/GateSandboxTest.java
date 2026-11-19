package com.smartparking.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.device.gate.GateAckRegistry;
import com.smartparking.device.gate.GateAction;
import com.smartparking.device.gate.GateAdapter;
import com.smartparking.device.gate.GateAdapterFactory;
import com.smartparking.device.gate.GateResult;
import com.smartparking.device.gate.GateSigner;
import com.smartparking.device.gate.MqttPlainGateAdapter;
import com.smartparking.device.gate.SandboxGateAdapter;
import com.smartparking.device.mqtt.MqttTransport;
import com.smartparking.device.po.Device;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 设备厂商通道沙箱化回归测试。
 *
 * <p>锁三件事：
 * <ol>
 *   <li>指令签名可验证、被篡改即失败（防伪造开闸）；</li>
 *   <li>设备回执时判定"已回执"，不回执时按宽松策略仍受理但标记 ackTimeout；</li>
 *   <li>未实现的厂商码回落到 sandbox，不会因为填错 vendor 直接炸掉开闸链路。</li>
 * </ol>
 */
class GateSandboxTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MqttTransport transport;
    private GateAckRegistry registry;

    @BeforeEach
    void setUp() {
        transport = mock(MqttTransport.class);
        registry = new GateAckRegistry();
    }

    @Test
    @DisplayName("指令签名：正确可验，篡改设备号即失败")
    void signRoundTrip() {
        String sign = GateSigner.sign("OPEN", "GATE-A-001", "seq001");
        assertTrue(GateSigner.verify("OPEN", "GATE-A-001", "seq001", sign));
        assertFalse(GateSigner.verify("OPEN", "GATE-A-002", "seq001", sign));
        assertFalse(GateSigner.verify("CLOSE", "GATE-A-001", "seq001", sign));
        assertFalse(GateSigner.verify("OPEN", "GATE-A-001", "seq001", null));
    }

    @Test
    @DisplayName("MQTT 不可用 → 沙箱本地模拟受理，标记 transport=local-sim")
    void localSimWhenMqttOffline() {
        when(transport.publish(anyString(), anyString(), anyInt())).thenReturn(false);
        SandboxGateAdapter adapter = newAdapter(false);

        GateResult result = adapter.open(gate("GATE-A-001", "sandbox"), "单测放行");

        assertTrue(result.isSuccess(), "沙箱应在无 broker 时降级受理，保证演示链路不中断");
        assertEquals("local-sim", result.getTransport());
        assertTrue(result.isAckTimeout());
        assertEquals(GateAction.OPEN, result.getAction());
    }

    @Test
    @DisplayName("设备回执 ACK → 判定已回执（ackTimeout=false）")
    void ackReceived() throws Exception {
        // publish 时模拟设备立刻回执：从报文里取 seq，写入登记表
        when(transport.publish(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            String payload = invocation.getArgument(1);
            Map<String, Object> cmd = JSON.readValue(payload, Map.class);
            Map<String, String> ack = new LinkedHashMap<>();
            ack.put("seq", String.valueOf(cmd.get("seq")));
            ack.put("result", "OK");
            registry.complete(String.valueOf(cmd.get("seq")), ack);
            return true;
        });
        SandboxGateAdapter adapter = newAdapter(false);

        GateResult result = adapter.open(gate("GATE-A-001", "sandbox"), "单测放行");

        assertTrue(result.isSuccess());
        assertEquals("mqtt", result.getTransport());
        assertFalse(result.isAckTimeout(), "设备已回执，不应标记超时");
        assertNotNull(result.getAckPayload());
        assertNotNull(result.getSeq());
    }

    @Test
    @DisplayName("严格模式下无回执 → 判定失败（现场设备支持回执时才开）")
    void strictAckFailsWithoutAck() {
        when(transport.publish(anyString(), anyString(), anyInt())).thenReturn(true);
        SandboxGateAdapter adapter = newAdapter(true);

        GateResult result = adapter.close(gate("GATE-A-001", "sandbox"));

        assertFalse(result.isSuccess());
        assertTrue(result.isAckTimeout());
    }

    @Test
    @DisplayName("未实现的厂商码 → 回落到 sandbox 适配器")
    void unknownVendorFallsBack() {
        GateAdapterFactory factory = new GateAdapterFactory(
                List.of(newAdapter(false), plainAdapter()), "sandbox");

        GateAdapter adapter = factory.select(gate("GATE-A-001", "zkteco-unknown"));

        assertNotNull(adapter);
        assertEquals(SandboxGateAdapter.VENDOR, adapter.vendor());
    }

    @Test
    @DisplayName("device.vendor=mqtt-plain → 选中纯文本适配器")
    void plainVendorSelected() {
        when(transport.publish(anyString(), anyString(), anyInt())).thenReturn(true);
        GateAdapterFactory factory = new GateAdapterFactory(
                List.of(newAdapter(false), plainAdapter()), "sandbox");

        GateAdapter adapter = factory.select(gate("GATE-A-001", "mqtt-plain"));

        assertEquals(MqttPlainGateAdapter.VENDOR, adapter.vendor());
    }

    // ------------------------------------------------------------------

    private SandboxGateAdapter newAdapter(boolean strictAck) {
        SandboxGateAdapter adapter = new SandboxGateAdapter(transport, registry);
        ReflectionTestUtils.setField(adapter, "ackTimeoutMs", 200L);
        ReflectionTestUtils.setField(adapter, "strictAck", strictAck);
        return adapter;
    }

    private MqttPlainGateAdapter plainAdapter() {
        return new MqttPlainGateAdapter(transport, registry);
    }

    private Device gate(String deviceNo, String vendor) {
        Device device = new Device();
        device.setDeviceNo(deviceNo);
        device.setDeviceName("测试道闸");
        device.setDeviceType(1);
        device.setLocation("单测");
        device.setMqttTopic("device/gate/" + deviceNo + "/control");
        device.setStatus(1);
        device.setVendor(vendor);
        return device;
    }
}
