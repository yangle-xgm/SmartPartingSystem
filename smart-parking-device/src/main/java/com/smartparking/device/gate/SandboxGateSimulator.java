package com.smartparking.device.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.device.mqtt.MqttTransport;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 虚拟道闸（沙箱设备模拟器）。
 *
 * <p>它扮演"厂商设备"这一端：订阅道闸指令主题，校验签名，往 {@code /ack} 回执，
 * 并唤醒等待中的 {@link GateAckRegistry}。有了它，「下发指令 → 设备执行 → 回执确认」
 * 这条链路在没有任何真实硬件的情况下也能端到端跑通。
 *
 * <p>注意：它只做协议应答，不写数据库、不改设备状态 —— 设备在线状态由设备心跳决定，
 * 让模拟器顺手改状态会掩盖真实离线场景（那是要测的东西）。
 *
 * <p>关闭：{@code smart-parking.device.simulator.enabled=false}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxGateSimulator {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** 兼容种子数据的 device/gate/{no}/control，以及未配置 topic 时的 device/gate/{no}/cmd */
    private static final String[] CMD_TOPICS = {
            "device/gate/+/control",
            "device/gate/+/cmd"
    };

    private final MqttTransport mqttTransport;
    private final GateAckRegistry ackRegistry;

    @Value("${smart-parking.device.simulator.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void start() {
        if (!enabled) {
            log.info("沙箱道闸模拟器已关闭（smart-parking.device.simulator.enabled=false）");
            return;
        }
        for (String topic : CMD_TOPICS) {
            mqttTransport.subscribe(topic, (t, message) ->
                    handle(t, new String(message.getPayload(), StandardCharsets.UTF_8)));
        }
        log.info("沙箱道闸模拟器已就绪，监听主题：{}", String.join(" / ", CMD_TOPICS));
    }

    @SuppressWarnings("unchecked")
    private void handle(String topic, String payload) {
        try {
            Map<String, Object> cmd = JSON.readValue(payload, Map.class);
            String action = str(cmd.get("cmd"));
            String deviceNo = str(cmd.get("deviceNo"));
            String seq = str(cmd.get("seq"));
            String sign = str(cmd.get("sign"));

            if (action == null || deviceNo == null || seq == null) {
                log.warn("[沙箱道闸] 指令缺少必要字段，忽略：{}", payload);
                return;
            }
            // 模拟真实设备的安全校验：签名不对一律不动作（防伪造开闸）
            if (!GateSigner.verify(action, deviceNo, seq, sign)) {
                log.warn("[沙箱道闸] 指令验签失败，拒绝执行 device={}, cmd={}, seq={}", deviceNo, action, seq);
                return;
            }

            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("cmd", action);
            ack.put("deviceNo", deviceNo);
            ack.put("seq", seq);
            ack.put("result", "OK");
            ack.put("ts", System.currentTimeMillis());
            ack.put("sign", GateSigner.sign("ACK", deviceNo, seq));
            String ackJson = JSON.writeValueAsString(ack);

            mqttTransport.publish("device/gate/" + deviceNo + "/ack", ackJson, 1);

            // 进程内唤醒等待方（即使 ACK 主题订阅失败也能拿到结果）
            Map<String, String> flat = new LinkedHashMap<>();
            flat.put("cmd", action);
            flat.put("deviceNo", deviceNo);
            flat.put("seq", seq);
            flat.put("result", "OK");
            flat.put("ts", String.valueOf(ack.get("ts")));
            ackRegistry.complete(seq, flat);

            log.info("[沙箱道闸] 已执行 {} 指令：device={}, seq={}, topic={}", action, deviceNo, seq, topic);
        } catch (Exception e) {
            log.error("[沙箱道闸] 处理指令失败 topic={}, payload={}, err={}", topic, payload, e.getMessage());
        }
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
