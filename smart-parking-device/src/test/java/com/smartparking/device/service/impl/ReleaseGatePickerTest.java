package com.smartparking.device.service.impl;

import com.smartparking.device.po.Device;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 支付成功联动开闸的「选闸」单元测试。
 *
 * <p>支付对应的是出场场景，所以必须优先选出口道闸；但单闸场地没有「出口」字样，
 * 此时必须回退到唯一那台闸，否则会出现"付了钱抬不了杆"。
 * 这段选择逻辑直接决定现场能不能出去，用单测把它锁住。
 */
class ReleaseGatePickerTest {

    private static Device gate(String deviceNo, String deviceName) {
        Device device = new Device();
        device.setDeviceNo(deviceNo);
        device.setDeviceName(deviceName);
        device.setDeviceType(1);
        device.setStatus(1);
        return device;
    }

    @Test
    @DisplayName("无在线道闸：返回 null，由调用方返回 success=false 而不是抛异常")
    void pick_nullOrEmpty_returnsNull() {
        assertNull(DeviceServiceImpl.pickReleaseGate(null));
        assertNull(DeviceServiceImpl.pickReleaseGate(Collections.emptyList()));
    }

    @Test
    @DisplayName("有名称为「出口」的道闸：优先选中它，即使入口道闸排在前面")
    void pick_prefersExitGate() {
        Device entry = gate("GATE-A-001", "A区入口道闸");
        Device exit = gate("GATE-A-002", "A区出口道闸");

        Device picked = DeviceServiceImpl.pickReleaseGate(Arrays.asList(entry, exit));

        assertSame(exit, picked);
        assertEquals("GATE-A-002", picked.getDeviceNo());
    }

    @Test
    @DisplayName("单闸场地（无出口标记）：回退到第一台在线道闸，避免付了钱不放行")
    void pick_noExitGate_fallsBackToFirst() {
        Device onlyGate = gate("GATE-C-001", "C区入口道闸");
        List<Device> gates = new ArrayList<>();
        gates.add(onlyGate);

        assertSame(onlyGate, DeviceServiceImpl.pickReleaseGate(gates));
    }

    @Test
    @DisplayName("多台出口道闸：取列表第一台，保证结果稳定可预期")
    void pick_multipleExitGates_returnsFirst() {
        Device exitA = gate("GATE-A-002", "A区出口道闸");
        Device exitB = gate("GATE-B-002", "B区出口道闸");

        assertSame(exitA, DeviceServiceImpl.pickReleaseGate(Arrays.asList(exitA, exitB)));
    }

    @Test
    @DisplayName("设备名为空：不抛 NPE，按普通闸回退处理")
    void pick_nullName_doesNotThrow() {
        Device noName = gate("GATE-X-001", null);
        Device exit = gate("GATE-A-002", "A区出口道闸");

        assertSame(exit, DeviceServiceImpl.pickReleaseGate(Arrays.asList(noName, exit)));
        assertSame(noName, DeviceServiceImpl.pickReleaseGate(Collections.singletonList(noName)));
    }
}
