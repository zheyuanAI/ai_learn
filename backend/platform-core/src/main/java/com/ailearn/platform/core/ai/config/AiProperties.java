package com.ailearn.platform.core.ai.config;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Provider 服务端配置。
 * 客户端请求不得覆盖这里的地址、模型、密钥和工具调用上限。
 */
@ConfigurationProperties(prefix = "platform.ai")
public class AiProperties {
    private boolean enabled;
    private String provider = "open-webui";
    private URI baseUrl = URI.create("https://api.deepseek.com");
    private String model = "deepseek-v4-flash";
    private String apiKey = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration streamTimeout = Duration.ofSeconds(60);
    private Duration toolTimeout = Duration.ofSeconds(10);
    private int maxToolRounds = 4;
    private int maxToolCalls = 8;
    private Set<String> enabledTools = new LinkedHashSet<>(Set.of(
            "queryTrace", "queryOperationAudit", "querySalesOrderStatus", "queryPurchaseOrderStatus",
            "queryInventoryByProductAndWarehouse", "queryWorkOrderProgress", "queryQualityStatistics",
            "queryDeviceAlarm", "queryLowStock", "generateDailyOperationReport"));
    private URI openWebuiBaseUrl = URI.create("http://127.0.0.1:3000");
    private String openWebuiApiKey = "";
    private String openWebuiModel = "wms-assistant";
    private String openWebuiToolServerId = "server:wms";
    private String toolServiceSecret = "";
    private Duration invocationContextTtl = Duration.ofMinutes(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getStreamTimeout() {
        return streamTimeout;
    }

    public void setStreamTimeout(Duration streamTimeout) {
        this.streamTimeout = streamTimeout;
    }

    public Duration getToolTimeout() {
        return toolTimeout;
    }

    public void setToolTimeout(Duration toolTimeout) {
        this.toolTimeout = toolTimeout;
    }

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int maxToolRounds) {
        this.maxToolRounds = maxToolRounds;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public Set<String> getEnabledTools() {
        return Set.copyOf(enabledTools);
    }

    /**
     * 用途：设置部署环境允许启用的 AI 查询工具编码。
     * 入参：工具编码集合；流程：仅保存配置值，注册表会继续校验其必须属于代码固定工具集合。
     */
    public void setEnabledTools(Set<String> enabledTools) {
        this.enabledTools = enabledTools == null ? new LinkedHashSet<>() : new LinkedHashSet<>(enabledTools);
    }

    public URI getOpenWebuiBaseUrl() {
        return openWebuiBaseUrl;
    }

    public void setOpenWebuiBaseUrl(URI openWebuiBaseUrl) {
        this.openWebuiBaseUrl = openWebuiBaseUrl;
    }

    public String getOpenWebuiApiKey() {
        return openWebuiApiKey;
    }

    public void setOpenWebuiApiKey(String openWebuiApiKey) {
        this.openWebuiApiKey = openWebuiApiKey;
    }

    public String getOpenWebuiModel() {
        return openWebuiModel;
    }

    public void setOpenWebuiModel(String openWebuiModel) {
        this.openWebuiModel = openWebuiModel;
    }

    public String getOpenWebuiToolServerId() {
        return openWebuiToolServerId;
    }

    public void setOpenWebuiToolServerId(String openWebuiToolServerId) {
        this.openWebuiToolServerId = openWebuiToolServerId;
    }

    public String getToolServiceSecret() {
        return toolServiceSecret;
    }

    public void setToolServiceSecret(String toolServiceSecret) {
        this.toolServiceSecret = toolServiceSecret;
    }

    public Duration getInvocationContextTtl() {
        return invocationContextTtl;
    }

    public void setInvocationContextTtl(Duration invocationContextTtl) {
        this.invocationContextTtl = invocationContextTtl;
    }
}
