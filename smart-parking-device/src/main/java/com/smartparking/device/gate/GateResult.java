package com.smartparking.device.gate;

import lombok.Builder;
import lombok.Data;

/**
 * 一次道闸指令的执行结果。
 *
 * <p>{@code success} 的语义是「指令已被通道受理」，不等于「闸杆物理动作已完成」。
 * 是否有设备回执由 {@code ackTimeout} 区分：
 * <ul>
 *   <li>ackTimeout=false 且 ackPayload 非空：设备回 ACK，端到端确认；</li>
 *   <li>ackTimeout=true：指令已投递（MQTT QoS1）但设备未回执，通常设备未上线/未实现 ACK；</li>
 *   <li>transport=local-sim：MQTT 不可用，沙箱本地模拟受理（仅演示环境）。</li>
 * </ul>
 */
@Data
@Builder
public class GateResult {

    private boolean success;

    /** 实际执行指令的适配器厂商码 */
    private String vendor;

    /** 通道类型：mqtt / local-sim */
    private String transport;

    private GateAction action;

    private String deviceNo;

    /** 指令序号，用于和 ACK 对账 */
    private String seq;

    /** 设备回执原文 */
    private String ackPayload;

    private boolean ackTimeout;

    private long latencyMs;

    private String message;

    public static GateResult fail(String vendor, GateAction action, String deviceNo, String message) {
        return GateResult.builder()
                .success(false).vendor(vendor).action(action).deviceNo(deviceNo).message(message).build();
    }
}
