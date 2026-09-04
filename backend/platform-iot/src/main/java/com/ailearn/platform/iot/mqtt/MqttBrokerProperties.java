package com.ailearn.platform.iot.mqtt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 真实 MQTT 消费端配置。
 * <p>
 * enabled 默认由 application.yml 关闭；地址、订阅账号和持久化目录均由运行环境注入，类中不保存真实密钥。
 * </p>
 */
@ConfigurationProperties(prefix = "iot.mqtt")
public class MqttBrokerProperties {
    private boolean enabled;
    private String serverUri;
    private String topicFilter = "devices/+/telemetry";
    private String clientId = "platform-iot-telemetry-consumer";
    private String username;
    private String password;
    private int qos = 1;
    private int connectionTimeoutSeconds = 10;
    private int keepAliveSeconds = 30;
    private int reconnectDelaySeconds = 5;
    private boolean cleanSession;
    private String persistenceDirectory;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServerUri() {
        return serverUri;
    }

    public void setServerUri(String serverUri) {
        this.serverUri = serverUri;
    }

    public String getTopicFilter() {
        return topicFilter;
    }

    public void setTopicFilter(String topicFilter) {
        this.topicFilter = topicFilter;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public int getReconnectDelaySeconds() {
        return reconnectDelaySeconds;
    }

    public void setReconnectDelaySeconds(int reconnectDelaySeconds) {
        this.reconnectDelaySeconds = reconnectDelaySeconds;
    }

    public boolean isCleanSession() {
        return cleanSession;
    }

    public void setCleanSession(boolean cleanSession) {
        this.cleanSession = cleanSession;
    }

    public String getPersistenceDirectory() {
        return persistenceDirectory;
    }

    public void setPersistenceDirectory(String persistenceDirectory) {
        this.persistenceDirectory = persistenceDirectory;
    }
}
