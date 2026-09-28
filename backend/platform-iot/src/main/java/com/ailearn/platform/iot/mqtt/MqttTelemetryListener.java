package com.ailearn.platform.iot.mqtt;

import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.telemetry.exception.TelemetryException;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
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
    private static final int CONSUMER_THREADS = 4;
    private static final int MAX_PENDING_MESSAGES = 64;

    private final MqttBrokerProperties properties;
    private final MqttTelemetryMessageParser parser;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final Object lifecycleMonitor = new Object();
    private volatile MqttAsyncClient client;
    private volatile ScheduledExecutorService reconnectExecutor;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile ProcessingDispatcher dispatcher;

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
        // 每次启动使用新的有界调度器，避免旧连接的工作项在重启后确认新的 packet id。
        dispatcher = new ProcessingDispatcher();
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
        ProcessingDispatcher previous = dispatcher;
        dispatcher = null;
        if (previous != null) {
            previous.close();
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

    /** Spring 生命周期回调：停止动作后执行容器回调，保证资源释放完成再通知上层。 */
    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    /** 返回监听器本地运行标记，不代表 Broker 当前一定已连接。 */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /** 允许 Spring 在应用启动阶段自动尝试连接；失败由监听器自身重试，不阻断 HTTP 服务。 */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /** 让 MQTT 监听器启动靠后、停止靠前，避免抢先依赖未就绪的业务组件或拖延应用关闭。 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /** Broker 重连成功后重新订阅固定遥测主题，订阅失败仍进入统一重连流程。 */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (reconnect && running.get()) {
            subscribe(client);
        }
    }

    /** 连接断开时只安排后台重连，不把 Broker 短暂故障传播为 Spring 应用退出。 */
    @Override
    public void connectionLost(Throwable cause) {
        connecting.set(false);
        ProcessingDispatcher current = dispatcher;
        if (current != null) {
            current.invalidate();
        }
        if (running.get()) {
            log.warn("IoT MQTT 连接丢失，将自动重连：{}", cause == null ? "unknown" : cause.getMessage());
            scheduleReconnect();
        }
    }

    /** Paho v3 没有发布端认证头；主题中的 credential_reference 是应用侧可见的可信定位键。 */
    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        ProcessingDispatcher current = dispatcher;
        if (current == null || message == null) {
            throw new ServiceUnavailableException("MQTT QoS 1 消费调度器不可用");
        }
        if (message.getQos() != 1) {
            // 发布端降级到 QoS 0 时没有可靠重投能力，不让它进入一期 QoS 1 事实链。
            log.warn("IoT MQTT 拒绝非 QoS 1 消息，topic={}", topic);
            return;
        }
        // 回调线程先占用全局容量，再解析可信设备；容量耗尽时反压到 Paho，绝不提前 PUBACK。
        long deliveryEpoch = current.acquire(message.getId(), topic, message.getPayload());
        if (deliveryEpoch < 0) {
            // 同连接同 packet id 的 QoS 1 重传由首次入队工作项确认，重复回调不能产生第二次晚到 ACK。
            return;
        }
        boolean submitted = false;
        try {
            TelemetryIngestionCommand command = parser.prepare(topic, message.getPayload());
            current.submit(command, message.getId(), message.getQos(), deliveryEpoch);
            submitted = true;
        } catch (IotException | TelemetryException | IllegalArgumentException exception) {
            // 格式、凭证、租户或设备归属错误不可重试；手动确认无效消息，避免毒消息阻塞订阅。
            log.warn("IoT MQTT 消息被拒绝，topic={}，原因={}", topic, exception.getMessage());
            current.acknowledge(message.getId(), message.getQos(), deliveryEpoch);
        } catch (ServiceUnavailableException exception) {
            // 凭证查询或本地调度不可用时仍在 Paho 回调线程，抛出并让持久会话重投。
            log.error("IoT MQTT 消息摄取依赖暂不可用，触发重连，topic={}", topic, exception);
            throw exception;
        } finally {
            if (!submitted) {
                current.release();
            }
        }
    }

    /** 监听器只订阅遥测，不发布消息，因此该回调无需推进业务状态。 */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 监听器只订阅遥测，不发送发布消息。
    }

    /** 校验 MQTT 最小运行配置及一期固定 QoS/主题约束；失败时保持等待状态，不阻断 HTTP 启动。 */
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
                || properties.isCleanSession()
                || properties.getConnectionTimeoutSeconds() < 1
                || properties.getKeepAliveSeconds() < 1
                || properties.getReconnectDelaySeconds() < 1) {
            log.error("IoT MQTT 必须使用 QoS 1、持久会话，且超时和重连参数必须为正数；监听器保持等待，不阻断应用启动");
            return false;
        }
        return true;
    }

    /** 延迟创建 MQTT 客户端；初始化失败后允许下一轮重试重新创建持久化对象和客户端。 */
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
        // Paho 默认在 messageArrived 返回后自动 PUBACK；异步摄取必须改为提交事务后手动确认。
        created.setManualAcks(true);
        client = created;
    }

    /** 发起非阻塞 Broker 连接，使用 connecting 标记避免并发重连重复建立连接。 */
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
                    ProcessingDispatcher currentDispatcher = dispatcher;
                    if (currentDispatcher != null) {
                        currentDispatcher.activate();
                    }
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

    /** 按项目基线生成 MQTT 连接参数，显式关闭 Paho 自动重连并交由本类统一调度。 */
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

    /** 在连接有效且监听器仍运行时订阅固定遥测主题；订阅失败主动断开并安排重试。 */
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

    /** 创建单线程重连调度器并保证同一时刻只有一个待执行重连任务。 */
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

    /** 用途：用可信租户与设备标识聚合同设备消息；入参和出参均为摄取命令中的可信身份。 */
    private record DeviceKey(UUID tenantId, UUID deviceId) { }

    /** 用途：保存有界队列内的单条 QoS 1 消息；入参包含认证命令、packet id 和连接代次。 */
    private record PendingMessage(TelemetryIngestionCommand command, int packetId, int qos, long epoch) { }

    /** 用途：辨认同一连接上未确认 packet id 的重复投递；入参为主题与载荷，保留有界副本。 */
    private record PacketIdentity(String topic, byte[] payload) {
        private boolean same(String otherTopic, byte[] otherPayload) {
            return topic.equals(otherTopic) && Arrays.equals(payload, otherPayload);
        }
    }

    /**
     * 用途：对 MQTT 消息施加全局反压并按设备顺序调度；入参来自已认证的摄取命令；处理完成后才发送 PUBACK。
     * 同设备始终只有一个 drain 任务，不同设备最多由四个 worker 并行；失败使本代未确认消息由持久会话重投。
     */
    private final class ProcessingDispatcher {
        private final Object capacityMonitor = new Object();
        private final Map<DeviceKey, ArrayDeque<PendingMessage>> lanes = new HashMap<>();
        private final Map<Integer, PacketIdentity> inFlightPackets = new HashMap<>();
        private final Object sessionMonitor = new Object();
        private final ThreadPoolExecutor workers;
        private volatile long epoch;
        private volatile boolean active = true;
        private int usedSlots;

        /** 用途：创建容量固定的工作池；无入参和出参；任务与每设备队列均受同一信号量上限约束。 */
        private ProcessingDispatcher() {
            ThreadFactory factory = runnable -> {
                Thread thread = new Thread(runnable, "iot-mqtt-consumer");
                thread.setDaemon(true);
                return thread;
            };
            workers = new ThreadPoolExecutor(CONSUMER_THREADS, CONSUMER_THREADS, 0L,
                    TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_PENDING_MESSAGES), factory);
        }

        /** 用途：占用一条待处理消息容量；无入参；返回当前连接代次，容量耗尽时阻塞 Paho 回调形成反压。 */
        private long acquire(int packetId, String topic, byte[] payload) throws InterruptedException {
            long arrivalEpoch = epoch;
            synchronized (capacityMonitor) {
                while (usedSlots >= MAX_PENDING_MESSAGES && active && arrivalEpoch == epoch && running.get()) {
                    capacityMonitor.wait();
                }
                if (!active || arrivalEpoch != epoch || !running.get()) {
                    throw new ServiceUnavailableException("MQTT 连接已失效，等待未确认消息重投");
                }
                usedSlots++;
            }
            synchronized (sessionMonitor) {
                if (active && arrivalEpoch == epoch && running.get()) {
                    PacketIdentity previous = inFlightPackets.get(packetId);
                    if (previous != null) {
                        release();
                        if (previous.same(topic, payload)) {
                            return -1;
                        }
                        throw new ServiceUnavailableException("未确认的 MQTT packet id 被不同消息复用");
                    }
                    inFlightPackets.put(packetId, new PacketIdentity(topic, payload.clone()));
                    return epoch;
                }
            }
            release();
            throw new ServiceUnavailableException("MQTT 连接已失效，等待未确认消息重投");
        }

        /** 用途：归还未成功入队的消息容量；无入参与出参；已入队的容量由 worker 在结束时归还。 */
        private void release() {
            synchronized (capacityMonitor) {
                usedSlots--;
                capacityMonitor.notifyAll();
            }
        }

        /** 用途：按可信设备身份排入 FIFO；入参为已认证命令、packet id、QoS 和连接代次；无返回值。 */
        private void submit(TelemetryIngestionCommand command, int packetId, int qos, long deliveryEpoch) {
            synchronized (sessionMonitor) {
                if (!active || deliveryEpoch != epoch) {
                    throw new ServiceUnavailableException("MQTT 连接已失效，等待未确认消息重投");
                }
            }
            DeviceKey key = new DeviceKey(command.credentialContext().tenantId(), command.deviceId());
            PendingMessage pending = new PendingMessage(command, packetId, qos, deliveryEpoch);
            synchronized (lanes) {
                ArrayDeque<PendingMessage> lane = lanes.computeIfAbsent(key, ignored -> new ArrayDeque<>());
                boolean first = lane.isEmpty();
                lane.addLast(pending);
                if (first) {
                    try {
                        workers.execute(() -> drain(key));
                    } catch (RejectedExecutionException exception) {
                        lane.removeLast();
                        lanes.remove(key);
                        throw new ServiceUnavailableException("MQTT 消费工作池不可用", exception);
                    }
                }
            }
        }

        /** 用途：顺序消费一台设备的待处理消息；入参为可信设备键；事务返回且连接代次仍有效时手动确认。 */
        private void drain(DeviceKey key) {
            while (true) {
                PendingMessage pending;
                synchronized (lanes) {
                    ArrayDeque<PendingMessage> lane = lanes.get(key);
                    if (lane == null || lane.isEmpty()) {
                        lanes.remove(key);
                        return;
                    }
                    pending = lane.peekFirst();
                }
                try {
                    if (isActive(pending.epoch())) {
                        try {
                            // consume 经 Spring 事务代理返回后，遥测、状态和本地告警任务才算提交。
                            try {
                                parser.consume(pending.command());
                            } catch (IotException | TelemetryException | IllegalArgumentException exception) {
                                log.warn("IoT MQTT 消息被拒绝，deviceId={}，原因={}",
                                        key.deviceId(), exception.getMessage());
                            }
                            acknowledge(pending.packetId(), pending.qos(), pending.epoch());
                        } catch (RuntimeException | MqttException exception) {
                            failAndRedeliver(pending.epoch(), exception);
                        }
                    }
                } finally {
                    synchronized (lanes) {
                        ArrayDeque<PendingMessage> lane = lanes.get(key);
                        if (lane != null && lane.peekFirst() == pending) {
                            lane.removeFirst();
                            if (lane.isEmpty()) {
                                lanes.remove(key);
                            }
                        }
                    }
                    release();
                }
            }
        }

        /** 用途：检查工作项是否仍属于当前活动连接；入参为入队代次；返回可否继续处理。 */
        private boolean isActive(long deliveryEpoch) {
            synchronized (sessionMonitor) {
                return active && deliveryEpoch == epoch && running.get();
            }
        }

        /** 用途：完成 QoS 1 手动确认；入参为 packet id、QoS 和连接代次；旧连接工作项绝不确认新连接的 id。 */
        private void acknowledge(int packetId, int qos, long deliveryEpoch) throws MqttException {
            synchronized (sessionMonitor) {
                if (active && deliveryEpoch == epoch && running.get() && client != null) {
                    client.messageArrivedComplete(packetId, qos);
                    inFlightPackets.remove(packetId);
                }
            }
        }

        /** 用途：处理工作线程摄取或 ACK 故障；入参为失败原因；断开持久会话触发所有未确认 QoS 1 消息重投。 */
        private void failAndRedeliver(long deliveryEpoch, Exception exception) {
            synchronized (sessionMonitor) {
                if (!active || deliveryEpoch != epoch) {
                    return;
                }
                epoch++;
                active = false;
                inFlightPackets.clear();
            }
            synchronized (capacityMonitor) {
                capacityMonitor.notifyAll();
            }
            log.error("IoT MQTT 消费失败，断开连接并重投未确认消息", exception);
            MqttAsyncClient current = client;
            if (current != null) {
                try {
                    current.disconnectForcibly(1000, 1000, false);
                } catch (MqttException disconnectException) {
                    log.warn("IoT MQTT 消费失败后的断连调用失败，将继续安排重连", disconnectException);
                }
            }
            scheduleReconnect();
        }

        /** 用途：新连接建立后启用新代次；无入参与出参；旧代次未确认消息须等 Broker 重投。 */
        private void activate() {
            synchronized (sessionMonitor) {
                epoch++;
                active = true;
                inFlightPackets.clear();
            }
            synchronized (capacityMonitor) {
                capacityMonitor.notifyAll();
            }
        }

        /** 用途：让断开连接的旧工作项失效；无入参与出参；禁止其在重连后误确认同号新消息。 */
        private void invalidate() {
            synchronized (sessionMonitor) {
                epoch++;
                active = false;
                inFlightPackets.clear();
            }
            // 唤醒因队列满而停在 Paho 回调内的线程，防止 stop/断连等待回调时死锁。
            synchronized (capacityMonitor) {
                capacityMonitor.notifyAll();
            }
        }

        /** 用途：关闭工作池并丢弃内存中未确认任务；无入参与出参；Broker 持久会话负责重投。 */
        private void close() {
            invalidate();
            workers.shutdownNow();
            synchronized (lanes) {
                lanes.clear();
            }
        }
    }

    /** 判断配置字符串是否为空白，避免把空白地址、账号或密码交给 Paho。 */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
