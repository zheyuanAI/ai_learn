package com.ailearn.platform.gateway.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Gateway 侧 Open WebUI 委托调用和唯一 API 白名单配置。 */
@Component
@ConfigurationProperties(prefix = "gateway.ai")
public class GatewayAiProperties {
    private boolean enabled;
    private String apiWhitelistPath = "../deploy/openwebui/wms-ai-api-whitelist.yml";
    private String toolServiceSecret = "";
    private String publicBaseUrl = "http://127.0.0.1:20001";
    private int maxToolCalls = 8;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiWhitelistPath() {
        return apiWhitelistPath;
    }

    public void setApiWhitelistPath(String apiWhitelistPath) {
        this.apiWhitelistPath = apiWhitelistPath;
    }

    public String getToolServiceSecret() {
        return toolServiceSecret;
    }

    public void setToolServiceSecret(String toolServiceSecret) {
        this.toolServiceSecret = toolServiceSecret;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }
}
