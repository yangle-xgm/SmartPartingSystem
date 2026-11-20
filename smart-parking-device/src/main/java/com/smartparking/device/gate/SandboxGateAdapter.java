package com.smartparking.device.gate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.device.mqtt.MqttTransport;
import com.smartparking.device.po.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 沙箱道闸适配器（默认厂商通道）。
 *
 * <p>报文按"厂商协议"的样子来，而不是裸发一个 OPEN 字符串：
 * <pre>
 * cmd/deviceNo/seq/ts/reason/sign  →  device/gate/{deviceNo}/cmd
 * 设备回执                          →  device/gate/{deviceNo}/ack
 * </pre>
 * 这样接入真实厂商时，只需替换报文构造与 topic 规则，等待 ACK、超时判定、
 * 指令流水这些"对接工程问题"已经跑通了。
 *
 * <p>ACK 超时的处理策略可配（{@code smart-parking.device.strict-ack}）：
 * 默认宽松 —— 指令已投递到 broker（QoS1）就算受理，仅标记 ackTimeout 并 WARN。
 * 理由：现实里绝大部分道闸不实现 ACK；若严格模式默认开启，会因"没回执"
 * 拒绝放行，把车堵在出口，这比误放行的运营损失更大。演示/验收想验证
 * 端到端回执时把 strict-ack 打开即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxGateAdapter implements GateAdapter {

    public static final String VENDOR = "sandbox";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MqttTransport mqttTransport;
    private final GateAckRegistry ackRegistry;
    private final GateSigner gateSigner;

    @Value("${smart-parking.device.gate-ack-timeout-ms:1500}")
    private long ackTimeoutMs;

    @Value("${smart-parking.device.strict-ack:false}")
    private boolean strictAck;

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
        String seq = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String cmdTopic = cmdTopic(device);
        String payload = buildPayload(device, action, seq, reason);

        // 先登记再发布：避免设备回执比登记更快（同进程模拟器尤其容易发生）
        ackRegistry.expect(seq);
        boolean delivered = mqttTransport.publish(cmdTopic, payload, 1);
        Map<String, String> ack = ackRegistry.await(seq, delivered ? ackTimeoutMs : 0L);

        GateResult.GateResultBuilder builder = GateResult.builder()
                .vendor(VENDOR)
                .action(action)
                .deviceNo(device.getDeviceNo())
                .seq(seq)
                .latencyMs(System.currentTimeMillis() - start);

        if (ack != null) {
            builder.success(true)
                    .transport("mqtt")
                    .ackTimeout(false)
                    .ackPayload(String.valueOf(ack))
                    .message("道闸回执：" + ack.getOrDefault("result", "OK"));
        } else if (delivered) {
            builder.success(!strictAck)
                    .transport("mqtt")
                    .ackTimeout(true)
                    .message(strictAck
                            ? "指令已投递但设备未回执（strict-ack=true，判定失败）"
                            : "指令已投递（QoS1），设备未在 " + ackTimeoutMs + "ms 内回执");
        } else {
            // broker 不可用：沙箱退化为本地模拟，保证演示链路可走通
            builder.success(true)
                    .transport("local-sim")
                    .ackTimeout(true)
                    .message("MQTT 不可用，沙箱本地模拟受理（仅演示环境）");
        }

        GateResult result = builder.build();
        if (!result.isSuccess()) {
            log.error("[道闸指令] 失败 device={}, action={}, seq={}, msg={}",
                    device.getDeviceNo(), action, seq, result.getMessage());
        } else if (result.isAckTimeout()) {
            log.warn("[道闸指令] {} device={}, action={}, seq={}, transport={}, msg={}",
                    action.getLabel(), device.getDeviceNo(), action, seq, result.getTransport(), result.getMessage());
        } else {
            log.info("[道闸指令] {} 已回执 device={}, action={}, seq={}, 耗时 {}ms",
                    action.getLabel(), device.getDeviceNo(), action, seq, result.getLatencyMs());
        }

        recordLog(device, action, seq, cmdTopic, payload, reason, result);
        return result;
    }

    private void recordLog(Device device, GateAction action, String seq, String topic,
                           String payload, String reason, GateResult result) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("time", LocalDateTime.now().toString());
        entry.put("deviceNo", device.getDeviceNo());
        entry.put("action", action.name());
        // reason 单独提一层：原来只埋在 payload 的 JSON 串里，排障要先解字符串才能区分
        // 「手动开闸」和「支付成功放行」，管理端也没法直接展示
        entry.put("reason", reason);
        entry.put("seq", seq);
        entry.put("topic", topic);
        entry.put("transport", result.getTransport());
        entry.put("success", result.isSuccess());
        entry.put("ackTimeout", result.isAckTimeout());
        entry.put("latencyMs", result.getLatencyMs());
        entry.put("payload", payload);
        entry.put("ack", result.getAckPayload());
        entry.put("message", result.getMessage());
        ackRegistry.record(entry);
    }

    private String cmdTopic(Device device) {
        if (device.getMqttTopic() != null && !device.getMqttTopic().isBlank()) {
            return device.getMqttTopic();
        }
        return "device/gate/" + device.getDeviceNo() + "/cmd";
    }

    private String buildPayload(Device device, GateAction action, String seq, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("cmd", action.name());
        body.put("deviceNo", device.getDeviceNo());
        body.put("seq", seq);
        body.put("ts", System.currentTimeMillis());
        body.put("source", "smart-parking");
        if (reason != null && !reason.isBlank()) {
            body.put("reason", reason);
        }
        body.put("sign", gateSigner.sign(action.name(), device.getDeviceNo(), seq));
        try {
            return JSON.writeValueAsString(body);
        } catch (Exception e) {
            // 序列化失败几乎不可能（纯 Map<String,Object>），兜底用最小报文保住功能
            return "{\"cmd\":\"" + action.name() + "\",\"deviceNo\":\"" + device.getDeviceNo()
                    + "\",\"seq\":\"" + seq + "\"}";
        }
    }

    /**
     * 指令签名：真实厂商协议普遍要求 cmd+设备号+序号 带签名，防止重放/伪造开闸。
     * 沙箱用可配置盐做 MD5 演示报文结构；接真实厂商时替换为厂商算法/密钥，盐由
     * {@code smart-parking.device.gate-sign-salt} 注入，无需改这一处代码。
     */
}
