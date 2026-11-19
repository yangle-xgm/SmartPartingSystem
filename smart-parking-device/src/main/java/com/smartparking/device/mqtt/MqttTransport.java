package com.smartparking.device.mqtt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * MQTT 传输层（设备服务内唯一连接）。
 *
 * <p>把「连接/重连/订阅恢复/发布」从业务里抽出来，摄像头联动和道闸指令共用一条连接。
 *
 * <p>两个曾经踩到的坑在这里一并解决：
 * <ol>
 *   <li><b>磁盘持久化残留目录</b>：Paho 默认用 MqttDefaultFilePersistence，会在工作目录生成
 *       {@code smart-parking-device-service-tcplocalhost18830/} 这类空目录且进程退出后残留。
 *       改用 {@link MemoryPersistence}，cleanSession 场景下本来也不需要本地落盘队列。</li>
 *   <li><b>断了不自愈</b>：原实现只在启动时连一次，broker 重启后整个设备域永久失联。
 *       现在开启 automaticReconnect，并在 reconnect 回调里恢复订阅（cleanSession=true 时
 *        broker 不会替你记住订阅关系）。</li>
 * </ol>
 */
@Slf4j
@Component
public class MqttTransport {

    @Value("${mqtt.broker:tcp://localhost:1883}")
    private String broker;

    @Value("${mqtt.client-id:smart-parking-device-service}")
    private String clientId;

    @Value("${mqtt.username:guest}")
    private String mqttUsername;

    @Value("${mqtt.password:guest}")
    private String mqttPassword;

    @Value("${mqtt.connect-timeout:10}")
    private int connectTimeout;

    /** 重连节流：避免 broker 不可用时每次调用都卡满 connect-timeout */
    private static final long RECONNECT_INTERVAL_MS = 10_000;

    private record Subscription(String topic, IMqttMessageListener listener) {
    }

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    private volatile MqttClient client;
    private volatile long lastAttemptAt = 0L;

    @PostConstruct
    public void init() {
        ensureConnected();
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /**
     * 确保连接可用。失败返回 false，不抛异常 —— 设备域在 MQTT 不可用时仍应提供
     * 设备管理/沙箱模拟等接口，不能被一条消息通道拖垮。
     */
    public synchronized boolean ensureConnected() {
        if (isConnected()) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastAttemptAt < RECONNECT_INTERVAL_MS) {
            return false;
        }
        lastAttemptAt = now;
        try {
            if (client == null) {
                client = new MqttClient(broker, clientId, new MemoryPersistence());
                client.setCallback(new MqttCallbackExtended() {
                    @Override
                    public void connectComplete(boolean reconnect, String serverURI) {
                        if (reconnect) {
                            log.info("MQTT 已重连：{}，恢复 {} 个订阅", serverURI, subscriptions.size());
                            resubscribe();
                        }
                    }

                    @Override
                    public void connectionLost(Throwable cause) {
                        log.warn("MQTT 连接断开：{}", cause == null ? "unknown" : cause.getMessage());
                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage message) {
                        // 由 per-topic listener 处理，这里无需实现
                    }

                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {
                        // QoS1 送达确认，无需处理
                    }
                });
            }
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(connectTimeout);
            options.setKeepAliveInterval(30);
            if (mqttUsername != null && !mqttUsername.isBlank()) {
                options.setUserName(mqttUsername);
                options.setPassword(mqttPassword == null ? new char[0] : mqttPassword.toCharArray());
            }
            client.connect(options);
            log.info("MQTT 连接成功：{}（clientId={}）", broker, clientId);
            resubscribe();
            return true;
        } catch (MqttException e) {
            log.warn("MQTT 连接失败：{}（{}），设备联动与道闸指令将降级处理", broker, e.getMessage());
            return false;
        }
    }

    /** 发布消息，返回是否成功投递到 broker */
    public boolean publish(String topic, String payload, int qos) {
        if (topic == null || topic.isBlank()) {
            log.warn("MQTT 发布被跳过：topic 为空，payload={}", payload);
            return false;
        }
        if (!ensureConnected()) {
            return false;
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(qos);
            client.publish(topic, message);
            return true;
        } catch (MqttException e) {
            log.error("MQTT 发布失败 topic={}, err={}", topic, e.getMessage());
            return false;
        }
    }

    /** 订阅主题（记录下来，重连后自动恢复） */
    public void subscribe(String topic, IMqttMessageListener listener) {
        subscriptions.add(new Subscription(topic, listener));
        if (ensureConnected()) {
            try {
                client.subscribe(topic, listener);
                log.info("MQTT 订阅成功：{}", topic);
            } catch (MqttException e) {
                log.error("MQTT 订阅失败 topic={}, err={}", topic, e.getMessage());
            }
        }
    }

    private void resubscribe() {
        for (Subscription subscription : subscriptions) {
            try {
                client.subscribe(subscription.topic(), subscription.listener());
            } catch (MqttException e) {
                log.warn("MQTT 恢复订阅失败 topic={}, err={}", subscription.topic(), e.getMessage());
            }
        }
    }
}
