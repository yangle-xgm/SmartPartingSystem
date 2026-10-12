package com.smartparking.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartparking.device.mapper.DeviceMapper;
import com.smartparking.device.po.Device;
import com.smartparking.device.query.DeviceQuery;
import com.smartparking.device.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

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

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String mqttBroker;

    @Value("${mqtt.client-id:smart-parking-device-service}")
    private String clientId;

    @Value("${mqtt.username:guest}")
    private String mqttUsername;

    @Value("${mqtt.password:guest}")
    private String mqttPassword;

    /** 订单服务地址（设备联动入场） */
    @Value("${smart-parking.order-url:http://localhost:8084}")
    private String orderUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    private MqttClient mqttClient;
    private final Map<String, Device> deviceCache = new ConcurrentHashMap<>();

    /** 摄像头联动结果缓存（key=deviceNo#plateNo，订阅回调写入，同步接口读取） */
    private final Map<String, Map<String, Object>> cameraResults = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            mqttClient = new MqttClient(mqttBroker, clientId);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(30);
            options.setUserName(mqttUsername);
            options.setPassword(mqttPassword.toCharArray());

            mqttClient.connect(options);
            log.info("MQTT连接成功：{}", mqttBroker);

            // 订阅摄像头识别消息
            subscribeCameraMessages();

        } catch (MqttException e) {
            log.error("MQTT连接失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 订阅摄像头识别消息
     */
    private void subscribeCameraMessages() throws MqttException {
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
        mqttClient.subscribe(topic, listener);
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
                    sendMqttMessage(gate.getMqttTopic(), "OPEN");
                    log.info("道闸已开启：deviceNo={}, topic={}（车牌 {} 入场，车位 {}）",
                            gate.getDeviceNo(), gate.getMqttTopic(), plateNo, entry.get("spaceNo"));
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
                    sendMqttMessage(gate.getMqttTopic(), "OPEN");
                    log.info("出口道闸已开启：deviceNo={}（车牌 {} 出场，费用 {}）",
                            gate.getDeviceNo(), plateNo, exit.get("amount"));
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
     * 门岗手动出场放行：按车牌结算出场并开闸（低置信度人工确认闭环）
     */
    @Override
    public Map<String, Object> manualExit(String deviceNo, String plateNo) {
        return mockExitRecognize(deviceNo, plateNo);
    }

    /**
     * 模拟摄像头识别：发布 MQTT 消息（经 broker 转发，订阅回调触发联动闭环）
     */
    public void publishCameraEvent(String deviceNo, String plateNo, Integer confidence) {
        int conf = confidence == null ? 100 : confidence;
        String topic = "device/camera/" + deviceNo + "/plate";
        String payload = "{\"plateNo\":\"" + plateNo + "\",\"confidence\":" + conf + "}";
        sendMqttMessage(topic, payload);
        log.info("模拟摄像头已发布识别消息：topic={}, payload={}", topic, payload);
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
        if (device != null) {
            device.setStatus(status);
            this.updateById(device);
            deviceCache.put(deviceNo, device);
        }
    }

    @Override
    public void openGate(String deviceNo) {
        Device device = getDeviceByNo(deviceNo);
        if (device == null || device.getDeviceType() != 1) {
            throw new RuntimeException("设备不存在或不是道闸");
        }
        if (device.getStatus() != 1) {
            throw new RuntimeException("道闸离线，无法控制");
        }

        // 发送MQTT指令
        sendMqttMessage(device.getMqttTopic(), "OPEN");
        log.info("发送道闸开启指令：deviceNo={}", deviceNo);
    }

    @Override
    public void closeGate(String deviceNo) {
        Device device = getDeviceByNo(deviceNo);
        if (device == null || device.getDeviceType() != 1) {
            throw new RuntimeException("设备不存在或不是道闸");
        }
        if (device.getStatus() != 1) {
            throw new RuntimeException("道闸离线，无法控制");
        }

        // 发送MQTT指令
        sendMqttMessage(device.getMqttTopic(), "CLOSE");
        log.info("发送道闸关闭指令：deviceNo={}", deviceNo);
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

    private void sendMqttMessage(String topic, String payload) {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                mqttClient.publish(topic, message);
            }
        } catch (MqttException e) {
            log.error("MQTT消息发送失败：topic={}, error={}", topic, e.getMessage(), e);
        }
    }
}
