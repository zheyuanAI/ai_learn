package com.ailearn.platform.iot.mqtt;

import com.ailearn.platform.iot.credential.application.DeviceCredentialVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 真实 MQTT 客户端条件装配。
 * <p>
 * 没有 {@code iot.mqtt.enabled=true} 时不创建 Paho 客户端和连接线程，因此默认环境即使没有 Broker 也不会启动失败。
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "iot.mqtt", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MqttBrokerProperties.class)
public class MqttTelemetryConfiguration {

    /**
     * 用途：把可信凭证、消息解析和统一遥测消费端连接起来；出参供 Paho 监听器使用。
     */
    @Bean
    public MqttTelemetryMessageParser mqttTelemetryMessageParser(DeviceCredentialVerifier credentialVerifier,
                                                                  MqttTelemetryConsumer consumer,
                                                                  ObjectMapper objectMapper) {
        return new MqttTelemetryMessageParser(credentialVerifier, consumer, objectMapper);
    }

    /**
     * 用途：装配具备非阻塞连接和自动重试能力的真实 MQTT 客户端；生命周期交由 Spring 管理。
     */
    @Bean(destroyMethod = "stop")
    public MqttTelemetryListener mqttTelemetryListener(MqttBrokerProperties properties,
                                                       MqttTelemetryMessageParser parser) {
        return new MqttTelemetryListener(properties, parser);
    }
}
