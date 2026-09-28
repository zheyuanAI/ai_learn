package com.ailearn.platform.iot.mqtt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.telemetry.application.TelemetryCredentialContext;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionCommand;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MqttTelemetryListenerDispatchTest {
    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_A = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_B = UUID.fromString("d0000000-0000-0000-0000-000000000002");
    private MqttTelemetryMessageParser parser;
    private MqttAsyncClient client;
    private MqttTelemetryListener listener;

    /** 用途：创建不连接真实 Broker 的监听器；无入参与出参；以模拟客户端检查手动确认时机。 */
    @BeforeEach
    void setUp() throws Exception {
        parser = mock(MqttTelemetryMessageParser.class);
        client = mock(MqttAsyncClient.class);
        listener = new MqttTelemetryListener(new MqttBrokerProperties(), parser);
        listener.start();
        Field field = MqttTelemetryListener.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(listener, client);
    }

    /** 用途：释放测试监听器与工作线程；无入参与出参。 */
    @AfterEach
    void tearDown() {
        listener.stop();
    }

    /** 用途：证明不同设备可同时进入摄取，而提交前均无 PUBACK；无入参与出参。 */
    @Test
    void differentDevicesRunInParallelAndAckOnlyAfterReturn() throws Exception {
        when(parser.prepare(eq("devices/a/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_A, "a", "a-1"));
        when(parser.prepare(eq("devices/b/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_B, "b", "b-1"));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return null;
        }).when(parser).consume(any());

        listener.messageArrived("devices/a/telemetry", message(11));
        listener.messageArrived("devices/b/telemetry", message(12));
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        verify(client, never()).messageArrivedComplete(11, 1);
        verify(client, never()).messageArrivedComplete(12, 1);

        release.countDown();
        verify(client, org.mockito.Mockito.timeout(3000)).messageArrivedComplete(11, 1);
        verify(client, org.mockito.Mockito.timeout(3000)).messageArrivedComplete(12, 1);
    }

    /** 用途：证明同一设备即便使用两个凭证也按回调入队顺序执行和确认；无入参与出参。 */
    @Test
    void sameDeviceAcrossCredentialsStaysSequential() throws Exception {
        when(parser.prepare(eq("devices/old/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_A, "old", "first"));
        when(parser.prepare(eq("devices/new/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_A, "new", "second"));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicBoolean secondStartedEarly = new AtomicBoolean();
        doAnswer(invocation -> {
            TelemetryIngestionCommand command = invocation.getArgument(0);
            if ("first".equals(command.messageId())) {
                firstEntered.countDown();
                assertTrue(firstRelease.await(3, TimeUnit.SECONDS));
            } else {
                secondStartedEarly.set(firstRelease.getCount() != 0);
                secondEntered.countDown();
            }
            return null;
        }).when(parser).consume(any());

        listener.messageArrived("devices/old/telemetry", message(21));
        assertTrue(firstEntered.await(3, TimeUnit.SECONDS));
        listener.messageArrived("devices/new/telemetry", message(22));
        verify(client, never()).messageArrivedComplete(21, 1);
        verify(client, never()).messageArrivedComplete(22, 1);
        firstRelease.countDown();
        assertTrue(secondEntered.await(3, TimeUnit.SECONDS));
        assertFalse(secondStartedEarly.get());
        verify(client, org.mockito.Mockito.timeout(3000)).messageArrivedComplete(22, 1);
        InOrder order = inOrder(client);
        order.verify(client).messageArrivedComplete(21, 1);
        order.verify(client).messageArrivedComplete(22, 1);
    }

    /** 用途：证明消费异常不发 ACK，且主动断连以启动持久会话重投；无入参与出参。 */
    @Test
    void failedConsumptionDisconnectsWithoutAck() throws Exception {
        when(parser.prepare(eq("devices/a/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_A, "a", "failed"));
        doAnswer(invocation -> { throw new ServiceUnavailableException("数据库暂不可用"); })
                .when(parser).consume(any());

        listener.messageArrived("devices/a/telemetry", message(31));

        verify(client, org.mockito.Mockito.timeout(3000)).disconnectForcibly(1000, 1000, false);
        verify(client, never()).messageArrivedComplete(31, 1);
    }

    /** 用途：证明回调线程内的凭证/解析依赖故障原样抛给 Paho 且没有 PUBACK；无入参与出参。 */
    @Test
    void preparationFailurePropagatesWithoutAck() throws Exception {
        when(parser.prepare(eq("devices/a/telemetry"), any(byte[].class)))
                .thenThrow(new ServiceUnavailableException("凭证库暂不可用"));

        assertThrows(ServiceUnavailableException.class,
                () -> listener.messageArrived("devices/a/telemetry", message(32)));

        verify(client, never()).messageArrivedComplete(32, 1);
        verify(parser, never()).consume(any());
    }

    /** 用途：证明同连接重复 packet id 只保留首个工作项，避免晚到的重复 ACK 误确认后续消息。 */
    @Test
    void duplicatePacketIdSharesFirstAck() throws Exception {
        when(parser.prepare(eq("devices/a/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_A, "a", "same"));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return null;
        }).when(parser).consume(any());

        listener.messageArrived("devices/a/telemetry", message(33));
        assertTrue(entered.await(3, TimeUnit.SECONDS));
        listener.messageArrived("devices/a/telemetry", message(33));
        verify(parser, times(1)).prepare(eq("devices/a/telemetry"), any(byte[].class));
        release.countDown();
        verify(client, org.mockito.Mockito.timeout(3000)).messageArrivedComplete(33, 1);
        verify(client, times(1)).messageArrivedComplete(33, 1);
    }

    /** 用途：证明旧事务完成后不会确认重连后复用的 packet id；无入参与出参。 */
    @Test
    void oldGenerationCannotAckReusedPacketId() throws Exception {
        when(parser.prepare(eq("devices/a/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_A, "a", "old"), command(DEVICE_A, "a", "after"));
        when(parser.prepare(eq("devices/b/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_B, "b", "new"));
        CountDownLatch oldEntered = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        doAnswer(invocation -> {
            TelemetryIngestionCommand command = invocation.getArgument(0);
            if ("old".equals(command.messageId())) {
                oldEntered.countDown();
                assertTrue(releaseOld.await(3, TimeUnit.SECONDS));
            }
            return null;
        }).when(parser).consume(any());

        listener.messageArrived("devices/a/telemetry", message(41));
        assertTrue(oldEntered.await(3, TimeUnit.SECONDS));
        listener.connectionLost(new RuntimeException("test disconnect"));
        Field dispatcherField = MqttTelemetryListener.class.getDeclaredField("dispatcher");
        dispatcherField.setAccessible(true);
        Object dispatcher = dispatcherField.get(listener);
        Method activate = dispatcher.getClass().getDeclaredMethod("activate");
        activate.setAccessible(true);
        activate.invoke(dispatcher);
        listener.messageArrived("devices/b/telemetry", message(41));
        verify(client, org.mockito.Mockito.timeout(3000)).messageArrivedComplete(41, 1);
        releaseOld.countDown();
        listener.messageArrived("devices/a/telemetry", message(42));
        verify(client, org.mockito.Mockito.timeout(3000)).messageArrivedComplete(42, 1);
        verify(client, times(1)).messageArrivedComplete(41, 1);
    }

    /** 用途：证明待处理槽位达到上限时回调受反压且不会预先确认；无入参与出参。 */
    @Test
    void boundedQueueBackpressuresCallback() throws Exception {
        when(parser.prepare(eq("devices/a/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_A, "a", "queued"));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstEntered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return null;
        }).doReturn(null).when(parser).consume(any());
        for (int id = 1; id <= 64; id++) {
            listener.messageArrived("devices/a/telemetry", message(id));
        }
        assertTrue(firstEntered.await(3, TimeUnit.SECONDS));
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<?> blocked = caller.submit(() -> {
                try {
                    listener.messageArrived("devices/a/telemetry", message(65));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            assertThrows(TimeoutException.class, () -> blocked.get(100, TimeUnit.MILLISECONDS));
            verify(client, never()).messageArrivedComplete(1, 1);
            release.countDown();
            blocked.get(3, TimeUnit.SECONDS);
            verify(client, org.mockito.Mockito.timeout(3000)).messageArrivedComplete(65, 1);
        } finally {
            release.countDown();
            caller.shutdownNow();
        }
    }

    /** 用途：队列满时停止监听器必须唤醒阻塞回调，未提交事务仍不可 PUBACK；无入参与出参。 */
    @Test
    void stopWakesBackpressuredCallbackWithoutAck() throws Exception {
        when(parser.prepare(eq("devices/a/telemetry"), any(byte[].class)))
                .thenReturn(command(DEVICE_A, "a", "waiting"));
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstEntered.countDown();
            while (release.getCount() != 0) {
                try {
                    release.await(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // 模拟已经进入数据库提交阶段且不能被 shutdownNow 立即中断的工作项。
                }
            }
            return null;
        }).when(parser).consume(any());
        for (int id = 1; id <= 64; id++) {
            listener.messageArrived("devices/a/telemetry", message(id));
        }
        assertTrue(firstEntered.await(3, TimeUnit.SECONDS));
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> backpressured = callers.submit(() -> {
                try {
                    listener.messageArrived("devices/a/telemetry", message(65));
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            assertThrows(TimeoutException.class, () -> backpressured.get(100, TimeUnit.MILLISECONDS));
            Future<?> stopped = callers.submit((Runnable) listener::stop);
            stopped.get(3, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> backpressured.get(3, TimeUnit.SECONDS));
            release.countDown();
            verify(client, never()).messageArrivedComplete(1, 1);
            verify(client, never()).messageArrivedComplete(65, 1);
        } finally {
            release.countDown();
            callers.shutdownNow();
        }
    }

    /** 用途：创建测试用可信命令；入参为设备、凭证及消息标识；返回同租户遥测命令。 */
    private TelemetryIngestionCommand command(UUID deviceId, String credential, String messageId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-09-28T00:00:00Z");
        return new TelemetryIngestionCommand(new TelemetryCredentialContext(TENANT, deviceId, credential),
                deviceId, "D-1", now, now, messageId, null, List.of(), "hash");
    }

    /** 用途：创建带真实 QoS 1 packet id 的测试消息；入参为 id；返回模拟入站消息。 */
    private MqttMessage message(int id) {
        MqttMessage message = new MqttMessage(new byte[] {1});
        message.setQos(1);
        message.setId(id);
        return message;
    }
}
