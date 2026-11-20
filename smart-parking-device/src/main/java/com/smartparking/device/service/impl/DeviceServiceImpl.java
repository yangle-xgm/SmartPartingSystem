package com.smartparking.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.device.gate.GateAdapter;
import com.smartparking.device.gate.GateAdapterFactory;
import com.smartparking.device.gate.GateResult;
import com.smartparking.device.gate.GateAckRegistry;
import com.smartparking.device.mapper.DeviceMapper;
import com.smartparking.device.mqtt.MqttTransport;
import com.smartparking.device.po.Device;
import com.smartparking.device.query.DeviceQuery;
import com.smartparking.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    /** 订单服务地址（设备联动入场） */
    @Value("${smart-parking.order-url:http://localhost:8084}")
    private String orderUrl;

    /** MQTT 传输层（摄像头联动 + 道闸指令共用一条连接） */
    private final MqttTransport mqttTransport;

    /** 道闸厂商适配器路由（按 device.vendor 选择协议实现） */
    private final GateAdapterFactory gateAdapterFactory;

    /** 道闸指令流水（演示/排障可见） */
    private final GateAckRegistry gateAckRegistry;

    private final RestTemplate restTemplate = com.smartparking.common.core.http.InternalHttp.attach(new RestTemplate());

    private final Map<String, Device> deviceCache = new ConcurrentHashMap<>();

    /** 摄像头联动结果缓存（key=deviceNo#plateNo，订阅回调写入，同步接口读取） */
    private final Map<String, Map<String, Object>> cameraResults = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 连接由 MqttTransport 统一维护（含自动重连与订阅恢复）
        subscribeCameraMessages();
    }

    /**
     * 订阅摄像头识别消息
     */
    private void subscribeCameraMessages() {
        String topic = "device/camera/+/plate";
        IMqttMessageListener listener = (t, message) -> {
            try {
                String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
                log.info("收到摄像头识别消息：topic={}, payload={}", t, payload);

                // 解析设备编号
                String[] parts = t.split("/");
                String deviceNo = parts[2];

                // 处理车牌识别结果
                handlePlateRecognition(deviceNo, payload);

            } catch (Exception e) {
                log.error("处理摄像头消息失败：{}", e.getMessage(), e);
            }
        };
        mqttTransport.subscribe(topic, listener);
    }

    /**
     * 处理车牌识别结果（MQTT联动闭环）：
     * 解析车牌 → 调订单服务识别+自动入场 → 道闸开启
     */
    private void handlePlateRecognition(String deviceNo, String payload) {
        log.info("处理车牌识别：deviceNo={}, payload={}", deviceNo, payload);
        try {
            // 1. 解析 payload：{"plateNo":"京A11111","confidence":95}
            Map<String, Object> data = parseJson(payload);
            String plateNo = String.valueOf(data.get("plateNo"));
            int confidence = data.get("confidence") == null ? 100 : Integer.parseInt(String.valueOf(data.get("confidence")));
            if (plateNo == null || "null".equals(plateNo) || plateNo.isBlank()) {
                log.warn("识别消息缺少车牌号：{}", payload);
                return;
            }

            // 2. 调用订单服务：识别 + 自动入场 + 分配车位
            String url = orderUrl + "/api/order/internal/device-entry?plateNo="
                    + java.net.URLEncoder.encode(plateNo, StandardCharsets.UTF_8)
                    + "&confidence=" + confidence + "&deviceNo=" + deviceNo;
            String body = restTemplate.postForObject(java.net.URI.create(url), HttpEntity.EMPTY, String.class);
            Map<String, Object> result = parseJson(body);
            Object dataObj = result.get("data");
            if (dataObj == null) {
                log.warn("设备联动入场失败：{}", result.get("message"));
                return;
            }
            Map<String, Object> entry = (Map<String, Object>) dataObj;
            log.info("设备联动入场结果：plateNo={}, recognized={}, message={}",
                    plateNo, entry.get("recognized"), entry.get("message"));

            // 写入联动结果缓存（供同步接口取回展示）
            cameraResults.put(deviceNo + "#" + plateNo, entry);

            // 3. 识别成功并已入场 → 开启道闸（发布 MQTT 控制指令）
            if (Boolean.TRUE.equals(entry.get("recognized")) && entry.get("orderNo") != null) {
                Device gate = findGateDevice();
                if (gate != null) {
                    GateResult gateResult = dispatchOpen(gate, "入口识别放行：" + plateNo);
                    log.info("道闸已开启：deviceNo={}, topic={}, transport={}, 回执={}（车牌 {} 入场，车位 {}）",
                            gate.getDeviceNo(), gate.getMqttTopic(), gateResult.getTransport(),
                            !gateResult.isAckTimeout(), plateNo, entry.get("spaceNo"));
                } else {
                    log.warn("未找到在线道闸设备，跳过开闸");
                }
            } else {
                log.info("未自动入场（{}），道闸保持关闭", entry.get("message"));
            }
        } catch (Exception e) {
            log.error("处理车牌识别失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 模拟摄像头识别（同步联动）：发布 MQTT 消息 → 等待订阅回调处理完成 → 返回联动结果
     */
    public Map<String, Object> mockCameraRecognize(String deviceNo, String plateNo, Integer confidence) {
        plateNo = decodePlateNo(plateNo);
        String key = deviceNo + "#" + plateNo;
        cameraResults.remove(key);

        // 1. 发布识别消息（真实经过 MQTT broker，订阅回调异步处理）
        publishCameraEvent(deviceNo, plateNo, confidence);

        // 2. 轮询等待联动结果（最多 3 秒）
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> result = cameraResults.get(key);
            if (result != null) {
                cameraResults.remove(key);
                return result;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 3. 超时兜底
        Map<String, Object> timeout = new HashMap<>();
        timeout.put("plateNo", plateNo);
        timeout.put("recognized", false);
        timeout.put("message", "MQTT联动处理超时，请检查 device 服务日志");
        return timeout;
    }

    /**
     * 模拟出口摄像头识别（联动出场）：直接调订单服务结算出场，成功后开闸
     */
    @Override
    public Map<String, Object> mockExitRecognize(String deviceNo, String plateNo) {
        plateNo = decodePlateNo(plateNo);
        Map<String, Object> result = new HashMap<>();
        result.put("plateNo", plateNo);
        result.put("deviceNo", deviceNo);
        try {
            // 1. 调用订单服务：出口识别 → 自动结算出场
            String url = orderUrl + "/api/order/internal/device-exit?plateNo="
                    + java.net.URLEncoder.encode(plateNo, StandardCharsets.UTF_8)
                    + "&deviceNo=" + deviceNo;
            String body = restTemplate.postForObject(java.net.URI.create(url), HttpEntity.EMPTY, String.class);
            Map<String, Object> resp = parseJson(body);
            Object dataObj = resp.get("data");
            if (dataObj == null) {
                result.put("success", false);
                result.put("message", String.valueOf(resp.get("message")));
                return result;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> exit = (Map<String, Object>) dataObj;
            result.putAll(exit);

            // 2. 结算成功 → 开启出口道闸放行
            if (Boolean.TRUE.equals(exit.get("success"))) {
                Device gate = findGateDevice();
                if (gate != null) {
                    GateResult gateResult = dispatchOpen(gate, "出口结算放行：" + plateNo);
                    result.put("gateAck", !gateResult.isAckTimeout());
                    result.put("gateTransport", gateResult.getTransport());
                    log.info("出口道闸已开启：deviceNo={}, transport={}（车牌 {} 出场，费用 {}）",
                            gate.getDeviceNo(), gateResult.getTransport(), plateNo, exit.get("amount"));
                } else {
                    log.warn("未找到在线道闸设备，跳过开闸");
                }
            }
            return result;
        } catch (Exception e) {
            log.error("出口联动出场失败：plateNo={}, err={}", plateNo, e.getMessage());
            result.put("success", false);
            result.put("message", "出场联动失败：" + e.getMessage());
            return result;
        }
    }

    /**
     * 门岗手动出场放行：按车牌结算出场并开闸（低置信度人工确认闭环）。
     *
     * 报告 P1-11「有出无进」：原实现只是转调 mockExitRecognize，而后者在
     * "查不到进行中订单"时一律不开闸 —— 于是识别异常、订单丢失、入场未建单的车
     * 会被物理堵在出口，现场无任何旁路。门岗接口的语义本就应该允许"先放行后追账"，
     * 因此这里补一条真正的旁路：无订单时也开闸，但结果里显式标记 manualRelease=true，
     * 并打 WARN 日志留痕（订单服务侧已同时写了一条 needManualRelease 的审计记录），
     * 便于事后按车牌追缴。注意：该接口在网关侧属于 /api/device/**，仅 role=1/2 可调。
     */
    @Override
    public Map<String, Object> manualExit(String deviceNo, String plateNo) {
        Map<String, Object> result = mockExitRecognize(deviceNo, plateNo);
        if (Boolean.TRUE.equals(result.get("success"))) {
            return result;
        }
        // 只有"订单侧明确表示无进行中订单"才走人工放行；联动异常/服务不可用时不放行，
        // 避免把"订单服务挂了"误判成"这车没订单"而大面积免费放行。
        if (!Boolean.TRUE.equals(result.get("needManualRelease"))) {
            return result;
        }
        Device gate = findGateDevice();
        if (gate == null) {
            result.put("message", "无进行中订单，且未找到在线道闸设备，无法人工放行");
            return result;
        }
        GateResult gateResult = dispatchOpen(gate, "门岗人工放行：" + plateNo);
        log.warn("[人工放行] 车场出口无进行中订单，门岗手动开闸：plateNo={}, deviceNo={}, gate={}, transport={}",
                plateNo, deviceNo, gate.getDeviceNo(), gateResult.getTransport());
        result.put("success", true);
        result.put("manualRelease", true);
        result.put("gateDeviceNo", gate.getDeviceNo());
        result.put("gateTransport", gateResult.getTransport());
        result.put("gateAck", !gateResult.isAckTimeout());
        result.put("message", "该车牌无进行中订单，已人工放行开闸（已留痕，请按车牌事后追缴）");
        return result;
    }

    /**
     * 模拟摄像头识别：发布 MQTT 消息（经 broker 转发，订阅回调触发联动闭环）
     */
    public void publishCameraEvent(String deviceNo, String plateNo, Integer confidence) {
        int conf = confidence == null ? 100 : confidence;
        String topic = "device/camera/" + deviceNo + "/plate";
        String payload = "{\"plateNo\":\"" + plateNo + "\",\"confidence\":" + conf + "}";
        mqttTransport.publish(topic, payload, 1);
        log.info("模拟摄像头已发布识别消息：topic={}, payload={}", topic, payload);
    }

    /**
     * 通过厂商适配器下发开闸指令。
     *
     * <p>适配器返回 success=false（仅 strict-ack 模式可能出现）时不抛出：
     * 自动放行/人工放行链路里，抛异常会让调用方认为"结算失败"从而重复结算。
     * 失败信息已由适配器 ERROR 日志与指令流水记录，运维可见。
     */
    private GateResult dispatchOpen(Device gate, String reason) {
        GateAdapter adapter = gateAdapterFactory.select(gate);
        return adapter.open(gate, reason);
    }

    /**
     * 查找在线的道闸设备（type=1）
     */
    private Device findGateDevice() {
        List<Device> gates = this.lambdaQuery()
                .eq(Device::getDeviceType, 1)
                .eq(Device::getStatus, 1)
                .last("LIMIT 1")
                .list();
        return gates.isEmpty() ? null : gates.get(0);
    }

    private Map<String, Object> parseJson(String body) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, Map.class);
    }

    /**
     * 车牌参数幂等解码兜底：部分环境/网关下中文 query 参数可能以 %E6... 形式原样进入，
     * 统一做一次 URLDecoder 解码；若本身已是明文则原样返回，保证两种场景都正确。
     */
    private String decodePlateNo(String plateNo) {
        if (plateNo == null || plateNo.isEmpty()) {
            return plateNo;
        }
        try {
            return java.net.URLDecoder.decode(plateNo, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return plateNo;
        }
    }

    @Override
    public Page<Device> pageList(Page<Device> page, DeviceQuery query) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getDeviceType() != null, Device::getDeviceType, query.getDeviceType())
                .eq(query.getStatus() != null, Device::getStatus, query.getStatus())
                .orderByAsc(Device::getDeviceNo);
        return this.page(page, wrapper);
    }

    @Override
    public Device registerDevice(Device device) {
        device.setStatus(0); // 默认离线
        device.setLastHeartbeat(LocalDateTime.now());
        this.save(device);
        deviceCache.put(device.getDeviceNo(), device);
        return device;
    }

    @Override
    public void updateHeartbeat(String deviceNo) {
        Device device = this.getOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceNo, deviceNo));
        if (device != null) {
            device.setLastHeartbeat(LocalDateTime.now());
            device.setStatus(1); // 在线
            this.updateById(device);
            deviceCache.put(deviceNo, device);
        }
    }

    @Override
    public void updateStatus(String deviceNo, Integer status) {
        Device device = this.getOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceNo, deviceNo));
        // 设备不存在时不能静默成功，否则调用方以为状态已更新
        if (device == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND);
        }
        device.setStatus(status);
        this.updateById(device);
        deviceCache.put(deviceNo, device);
    }

    @Override
    public void openGate(String deviceNo) {
        Device device = getDeviceByNo(deviceNo);
        if (device == null || device.getDeviceType() != 1) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND, "设备不存在或不是道闸");
        }
        if (device.getStatus() != 1) {
            throw new BizException(ErrorCode.GATE_ERROR, "道闸离线，无法控制");
        }

        GateResult result = dispatchOpen(device, "管理端手动开闸");
        log.info("发送道闸开启指令：deviceNo={}, vendor={}, transport={}, 回执={}",
                deviceNo, result.getVendor(), result.getTransport(), !result.isAckTimeout());
    }

    @Override
    public void closeGate(String deviceNo) {
        Device device = getDeviceByNo(deviceNo);
        if (device == null || device.getDeviceType() != 1) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND, "设备不存在或不是道闸");
        }
        if (device.getStatus() != 1) {
            throw new BizException(ErrorCode.GATE_ERROR, "道闸离线，无法控制");
        }

        GateResult result = gateAdapterFactory.select(device).close(device);
        log.info("发送道闸关闭指令：deviceNo={}, vendor={}, transport={}, 回执={}",
                deviceNo, result.getVendor(), result.getTransport(), !result.isAckTimeout());
    }

    @Override
    public List<Map<String, Object>> recentGateCommands(int limit) {
        return gateAckRegistry.recent(limit);
    }

    /**
     * 支付成功联动开闸。
     *
     * <p>设计取舍：
     * <ul>
     *   <li><b>不抛异常</b> —— 钱已经收了，开闸失败只能记日志等人工/重试，绝不能让
     *       调用方以为核销失败而回滚；</li>
     *   <li><b>自动挑出口道闸</b> —— 支付对应的是出场场景，优先选名称含「出口」的在线道闸；
     *       只部署单闸的场地没有出口标记时回退到任意在线道闸，避免"付了钱不放行"；</li>
     *   <li><b>无闸在线也返回 success=false</b> 而不是报错 —— 现场闸机离线是运维问题，
     *       由 command-log 与日志定位。</li>
     * </ul>
     */
    @Override
    public Map<String, Object> releaseForPayment(String payNo, String orderId, String reason, String deviceNo) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("payNo", payNo);
        result.put("orderId", orderId);

        Device gate;
        if (deviceNo != null && !deviceNo.isBlank()) {
            gate = getDeviceByNo(deviceNo);
            if (gate == null || !Integer.valueOf(1).equals(gate.getDeviceType())) {
                result.put("success", false);
                result.put("message", "指定设备不存在或不是道闸：" + deviceNo);
                return result;
            }
            if (!Integer.valueOf(1).equals(gate.getStatus())) {
                result.put("success", false);
                result.put("deviceNo", deviceNo);
                result.put("message", "指定道闸离线，未下发开闸指令：" + deviceNo);
                return result;
            }
        } else {
            List<Device> onlineGates = this.lambdaQuery()
                    .eq(Device::getDeviceType, 1)
                    .eq(Device::getStatus, 1)
                    .list();
            gate = pickReleaseGate(onlineGates);
            if (gate == null) {
                result.put("success", false);
                result.put("message", "无在线道闸，未下发开闸指令");
                return result;
            }
        }

        String why = reason == null || reason.isBlank() ? "支付成功放行" : reason;
        GateResult gateResult = dispatchOpen(gate, why);
        result.put("success", gateResult.isSuccess());
        result.put("deviceNo", gate.getDeviceNo());
        result.put("deviceName", gate.getDeviceName());
        result.put("vendor", gateResult.getVendor());
        result.put("transport", gateResult.getTransport());
        result.put("seq", gateResult.getSeq());
        result.put("gateAck", !gateResult.isAckTimeout());
        result.put("latencyMs", gateResult.getLatencyMs());
        result.put("message", gateResult.getMessage());

        if (gateResult.isSuccess()) {
            log.info("[支付联动开闸] payNo={}, orderId={}, device={}({}), vendor={}, transport={}, 回执={}, 耗时={}ms",
                    payNo, orderId, gate.getDeviceNo(), gate.getDeviceName(), gateResult.getVendor(),
                    gateResult.getTransport(), !gateResult.isAckTimeout(), gateResult.getLatencyMs());
        } else {
            log.error("[支付联动开闸] 下发失败 payNo={}, orderId={}, device={}, msg={}",
                    payNo, orderId, gate.getDeviceNo(), gateResult.getMessage());
        }
        return result;
    }

    /**
     * 从在线道闸中挑一台用于支付放行：优先名称含「出口」的，否则取第一台。
     *
     * <p>抽成静态方法便于单测覆盖（不需要 Spring 容器与数据库）。
     */
    static Device pickReleaseGate(List<Device> onlineGates) {
        if (onlineGates == null || onlineGates.isEmpty()) {
            return null;
        }
        for (Device gate : onlineGates) {
            if (gate.getDeviceName() != null && gate.getDeviceName().contains("出口")) {
                return gate;
            }
        }
        return onlineGates.get(0);
    }

    @Override
    public Map<String, Object> getStatusOverview() {
        List<Device> allDevices = this.list();

        Map<String, Object> overview = new HashMap<>();
        overview.put("total", allDevices.size());

        long online = allDevices.stream().filter(d -> d.getStatus() == 1).count();
        long offline = allDevices.stream().filter(d -> d.getStatus() == 0).count();
        long fault = allDevices.stream().filter(d -> d.getStatus() == 2).count();

        overview.put("online", online);
        overview.put("offline", offline);
        overview.put("fault", fault);

        // 按设备类型统计
        Map<String, Long> typeCount = new HashMap<>();
        allDevices.forEach(d -> {
            String typeKey = "type_" + d.getDeviceType();
            typeCount.merge(typeKey, 1L, Long::sum);
        });
        overview.put("typeCount", typeCount);

        return overview;
    }

    private Device getDeviceByNo(String deviceNo) {
        return this.getOne(new LambdaQueryWrapper<Device>()
                .eq(Device::getDeviceNo, deviceNo));
    }
}
