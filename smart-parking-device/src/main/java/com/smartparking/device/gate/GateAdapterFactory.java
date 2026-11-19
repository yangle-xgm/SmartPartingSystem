package com.smartparking.device.gate;

import com.smartparking.device.po.Device;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 道闸适配器路由：按设备所属厂商通道选择实现。
 *
 * <p>选择顺序：device.vendor → 配置默认值 smart-parking.device.gate-vendor → sandbox
 * <p>接入新厂商 = 新增一个 {@link GateAdapter} Bean，无需改动本类与业务流程。
 */
@Slf4j
@Component
public class GateAdapterFactory {

    private final Map<String, GateAdapter> adapters = new LinkedHashMap<>();
    private final String defaultVendor;

    public GateAdapterFactory(List<GateAdapter> adapterList,
                             @Value("${smart-parking.device.gate-vendor:sandbox}") String defaultVendor) {
        this.defaultVendor = defaultVendor;
        for (GateAdapter adapter : adapterList) {
            adapters.put(adapter.vendor(), adapter);
        }
        log.info("道闸适配器注册完成：{}，默认通道={}", adapters.keySet(), defaultVendor);
    }

    public GateAdapter select(Device device) {
        String vendor = device.getVendor() == null || device.getVendor().isBlank()
                ? defaultVendor : device.getVendor().trim();
        GateAdapter adapter = adapters.get(vendor);
        if (adapter == null) {
            log.warn("设备 {} 声明的厂商通道 [{}] 尚未实现，回落到 {}",
                    device.getDeviceNo(), vendor, SandboxGateAdapter.VENDOR);
            adapter = adapters.get(SandboxGateAdapter.VENDOR);
        }
        if (adapter == null) {
            // 理论上不会发生（sandbox 恒注册）；兜底避免 NPE
            throw new IllegalStateException("无可用道闸适配器，请检查 smart-parking.device.gate-vendor 配置");
        }
        return adapter;
    }

    public Set<String> vendors() {
        return adapters.keySet();
    }
}
