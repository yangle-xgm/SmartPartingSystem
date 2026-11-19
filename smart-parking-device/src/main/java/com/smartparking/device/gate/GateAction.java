package com.smartparking.device.gate;

/**
 * 道闸指令类型
 */
public enum GateAction {
    OPEN("开闸"),
    CLOSE("关闸"),
    STATUS("状态查询");

    private final String label;

    GateAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
