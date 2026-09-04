package com.ailearn.platform.iot.mqtt;

import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.telemetry.exception.TelemetryException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence;
import org.eclipse.paho.client.mqttv3.MqttClientPersistence;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Paho MQTT 3.x 异步监听器。
 * <p>
 * 只在 {@code iot.mqtt.enabled=true} 的 Spring 条件配置中创建。连接、订阅和重连均为非阻塞操作；Broker 地址未配置或
 * 暂时不可用时记录错误并保持 HTTP 服务可启动，配置修正或 Broker 恢复后自动重试。
 * </p>
 */
public class MqttTelemetryListener implements SmartLifecycle, MqttCallbackExtended {
    private static final Logger log = LoggerFactory.getLogger(MqttTelemetryListener.class);

    private final MqttBrokerProperties properties;
    private final MqttTelemetryMessageParser parser;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final Object lifecycleMonitor = new Object();
    private volatile MqttAsyncClient client;
    private volatile ScheduledExecutorService reconnectExecutor;
    private volatile ScheduledFuture<?> reconnectFuture;

    /**
     * 用途：组装 MQTT 生命周期组件；入参为外部配置和已经复用统一摄取链的消息解析器。
     */
    public MqttTelemetryListener(MqttBrokerProperties properties, MqttTelemetryMessageParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    /** 启动只创建连接尝试，不等待网络，也不因 Broker 未配置抛出 Spring 启动异常。 */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        if (!validConfiguration()) {
            return;
        }
        try {
            ensureClient();
            connect();
        } catch (MqttException | RuntimeException exception) {
            log.error("IoT MQTT 客户端初始化失败，将按配置重试；未记录密码", exception);
            scheduleReconnect();
        }
    }

    /** 停止重连、断开 Broker 并释放 Paho 资源。 */
    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        synchronized (lifecycleMonitor) {
            if (reconnectFuture != null) {
                reconnectFuture.cancel(false);
                reconnectFuture = null;
            }
            if (reconnectExecutor != null) {
                reconnectExecutor.shutdownNow();
                reconnectExecutor = null;
            }
        }
        connecting.set(false);
        MqttAsyncClient current = client;
        client = null;
        if (current != null) {
            try {
                if (current.isConnected()) {
                    current.disconnectForcibly(1000, 1000, false);
                }
                current.close();
            } catch (MqttException exception) {
                log.debug("IoT MQTT 客户端关闭时忽略异常", exception);
            }
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect && running.get()) {
            subscribe(client);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        connecting.set(false);
        if (running.get()) {
            log.warn("IoT MQTT 连接丢失，将自动重连：{}", cause == null ? "unknown" : cause.getMessage());
            scheduleReconnect();
        }
    }

    /** Paho v3 没有发布端认证头；主题中的 credential_reference 是应用侧可见的可信定位键。 */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        try {
            parser.accept(topic, message == null ? null : message.getPayload());
        } catch (IotException | TelemetryException | IllegalArgumentException exception) {
            // 格式、凭证、租户或设备归属错误不可重试；返回后让 Broker 确认该无效消息，避免毒消息阻塞订阅。
            log.warn("IoT MQTT 消息被拒绝，topic={}，原因={}", topic, exception.getMessage());
        } catch (ServiceUnavailableException exception) {
            // 依赖数据库暂不可用时抛出，让 Paho 断开连接并依靠 QoS1 持久会话重新投递。
            log.error("IoT MQTT 消息摄取依赖暂不可用，触发重连，topic={}", topic, exception);
            throw exception;
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 监听器只订阅遥测，不发送发布消息。
    }

    private boolean validConfiguration() {
        if (blank(properties.getServerUri())) {
            log.error("已启用 IoT MQTT，但未配置 iot.mqtt.server-uri；监听器保持等待，不阻断应用启动");
            return false;
        }
        if (blank(properties.getClientId())) {
            log.error("已启用 IoT MQTT，但未配置 iot.mqtt.client-id；监听器保持等待，不阻断应用启动");
            return false;
        }
        if (blank(properties.getUsername()) || blank(properties.getPassword())) {
            log.error("已启用 IoT MQTT，但未配置独立订阅账号；监听器保持等待，不阻断应用启动");
            return false;
        }
        if (!"devices/+/telemetry".equals(properties.getTopicFilter())) {
            log.error("IoT MQTT topic-filter 必须固定为 devices/+/telemetry，当前值被拒绝");
            return false;
        }
        if (properties.getQos() != 1
                || properties.getConnectionTimeoutSeconds() < 1
                || properties.getKeepAliveSeconds() < 1
                || properties.getReconnectDelaySeconds() < 1) {
            log.error("IoT MQTT 必须使用 QoS 1，且超时和重连参数必须为正数；监听器保持等待，不阻断应用启动");
            return false;
        }
        return true;
    }

    private void ensureClient() throws MqttException {
        if (client != null) {
            return;
        }
        MqttClientPersistence persistence = blank(properties.getPersistenceDirectory())
                ? new MemoryPersistence()
                : new MqttDefaultFilePersistence(properties.getPersistenceDirectory().trim());
        MqttAsyncClient created = new MqttAsyncClient(properties.getServerUri().trim(),
                properties.getClientId().trim(), persistence);
        created.setCallback(this);
        client = created;
    }

    private void connect() {
        if (!running.get()) {
            return;
        }
        try {
            // 初始化失败也要允许后续重试重新创建客户端，例如外部持久化目录首次不可用后被修复。
            ensureClient();
            MqttAsyncClient current = client;
            if (current == null || current.isConnected() || !connecting.compareAndSet(false, true)) {
                return;
            }
            current.connect(connectOptions(), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    connecting.set(false);
                    subscribe(current);
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    connecting.set(false);
                    if (running.get()) {
                        log.warn("IoT MQTT Broker 连接失败，将自动重试：{}",
                                exception == null ? "unknown" : exception.getMessage());
                        scheduleReconnect();
                    }
                }
            });
        } catch (MqttException | RuntimeException exception) {
            connecting.set(false);
            log.warn("IoT MQTT Broker 连接调用失败，将自动重试：{}", exception.getMessage());
            scheduleReconnect();
        }
    }

    private MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(properties.isCleanSession());
        options.setAutomaticReconnect(false);
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
        options.setKeepAliveInterval(properties.getKeepAliveSeconds());
        options.setMaxInflight(1000);
        if (!blank(properties.getUsername())) {
            options.setUserName(properties.getUsername().trim());
        }
        if (!blank(properties.getPassword())) {
            options.setPassword(properties.getPassword().toCharArray());
        }
        return options;
    }

    private void subscribe(MqttAsyncClient current) {
        if (!running.get() || current == null || !current.isConnected()) {
            return;
        }
        try {
            current.subscribe(properties.getTopicFilter(), properties.getQos(), null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    log.info("IoT MQTT 已订阅遥测主题 {}，QoS={}", properties.getTopicFilter(), properties.getQos());
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    log.warn("IoT MQTT 订阅失败，将断开并重试：{}",
                            exception == null ? "unknown" : exception.getMessage());
                    try {
                        current.disconnectForcibly(1000, 1000, false);
                    } catch (MqttException disconnectException) {
                        log.debug("IoT MQTT 订阅失败后的断开异常", disconnectException);
                    }
                    scheduleReconnect();
                }
            });
        } catch (MqttException | RuntimeException exception) {
            log.warn("IoT MQTT 订阅调用失败，将断开并重试：{}", exception.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running.get()) {
            return;
        }
        synchronized (lifecycleMonitor) {
            if (!running.get()) {
                return;
            }
            if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
                ThreadFactory factory = runnable -> {
                    Thread thread = new Thread(runnable, "iot-mqtt-reconnect");
                    thread.setDaemon(true);
                    return thread;
                };
                reconnectExecutor = Executors.newSingleThreadScheduledExecutor(factory);
            }
            if (reconnectFuture == null || reconnectFuture.isDone() || reconnectFuture.isCancelled()) {
                reconnectFuture = reconnectExecutor.schedule(this::connect,
                        properties.getReconnectDelaySeconds(), TimeUnit.SECONDS);
            }
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
