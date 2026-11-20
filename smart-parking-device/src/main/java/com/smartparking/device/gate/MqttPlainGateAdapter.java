package com.smartparking.device.gate;

import com.smartparking.device.mqtt.MqttTransport;
import com.smartparking.device.po.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通用 MQTT 纯文本道闸适配器（vendor = mqtt-plain）。
 *
 * <p>兼容本系统早期版本与各类开源 MQTT 道闸模拟器：往 {@code device.mqttTopic}
 * 发一个 {@code OPEN} / {@code CLOSE} 字符串即可。
 *
 * <p>没有 ACK 概念 —— 这类设备协议本身就不回执，所以 transport 只表示"是否投递成功"。
 * 想让设备回执请把 vendor 改成 {@code sandbox}（或按厂商协议自己写适配器）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttPlainGateAdapter implements GateAdapter {

    public static final String VENDOR = "mqtt-plain";

    private final MqttTransport mqttTransport;
    private final GateAckRegistry ackRegistry;

    @Override
    public String vendor() {
        return VENDOR;
    }

    @Override
    public GateResult open(Device device, String reason) {
        return send(device, GateAction.OPEN, reason);
    }

    @Override
    public GateResult close(Device device) {
        return send(device, GateAction.CLOSE, null);
    }

    private GateResult send(Device device, GateAction action, String reason) {
        long start = System.currentTimeMillis();
        String topic = device.getMqttTopic() == null || device.getMqttTopic().isBlank()
                ? "device/gate/" + device.getDeviceNo() + "/cmd"
                : device.getMqttTopic();
        boolean delivered = mqttTransport.publish(topic, action.name(), 1);

        GateResult result = GateResult.builder()
                .success(true)   // 与历史行为一致：纯文本通道不因 broker 抖动阻断放行
                .vendor(VENDOR)
                .action(action)
                .deviceNo(device.getDeviceNo())
                .transport(delivered ? "mqtt" : "local-sim")
                .ackTimeout(true)
                .latencyMs(System.currentTimeMillis() - start)
                .message(delivered ? "指令已投递（纯文本通道无回执）" : "MQTT 不可用，本地模拟受理")
                .build();

        log.info("[道闸指令] {} device={}, topic={}, transport={}",
                action.getLabel(), device.getDeviceNo(), topic, result.getTransport());

        java.util.Map<String, Object> entry = new java.util.LinkedHashMap<>();
        entry.put("time", java.time.LocalDateTime.now().toString());
        entry.put("deviceNo", device.getDeviceNo());
        entry.put("action", action.name());
        // 与 sandbox 通道保持一致：reason 单独成字段，便于区分手动开闸与支付放行
        entry.put("reason", reason);
        entry.put("seq", "-");
        entry.put("topic", topic);
        entry.put("transport", result.getTransport());
        entry.put("success", result.isSuccess());
        entry.put("ackTimeout", true);
        entry.put("latencyMs", result.getLatencyMs());
        entry.put("payload", action.name());
        entry.put("ack", null);
        entry.put("message", result.getMessage());
        ackRegistry.record(entry);
        return result;
    }
}
