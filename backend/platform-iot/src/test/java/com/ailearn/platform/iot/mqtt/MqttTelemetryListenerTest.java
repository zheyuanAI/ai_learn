package com.ailearn.platform.iot.mqtt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class MqttTelemetryListenerTest {

    @Test
    void missingBrokerAddressDoesNotFailApplicationLifecycle() {
        MqttBrokerProperties properties = new MqttBrokerProperties();
        properties.setEnabled(true);
        MqttTelemetryListener listener = new MqttTelemetryListener(properties,
                mock(MqttTelemetryMessageParser.class));

        listener.start();

        assertTrue(listener.isRunning());
        listener.stop();
        assertFalse(listener.isRunning());
    }

    @Test
    void missingSubscriberCredentialsDoesNotConnect() {
        MqttBrokerProperties properties = new MqttBrokerProperties();
        properties.setEnabled(true);
        properties.setServerUri("tcp://127.0.0.1:1883");
        MqttTelemetryListener listener = new MqttTelemetryListener(properties,
                mock(MqttTelemetryMessageParser.class));

        listener.start();

        assertTrue(listener.isRunning());
        listener.stop();
    }

    @Test
    void broadTopicFilterIsRejectedWithoutConnecting() {
        MqttBrokerProperties properties = new MqttBrokerProperties();
        properties.setEnabled(true);
        properties.setServerUri("tcp://127.0.0.1:1883");
        properties.setUsername("iot-readonly-account");
        properties.setPassword("external-secret");
        properties.setTopicFilter("#");
        MqttTelemetryListener listener = new MqttTelemetryListener(properties,
                mock(MqttTelemetryMessageParser.class));

        listener.start();

        assertTrue(listener.isRunning());
        listener.stop();
    }
}
