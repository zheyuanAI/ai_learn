package com.ailearn.platform.core.ai.controller;

import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolRegistry;
import com.ailearn.platform.core.ai.application.TrustedAiRequestContextFactory;
import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.dto.AiCapabilitiesView;
import com.ailearn.platform.core.ai.infrastructure.AiBusinessApiCatalogClient;
import com.ailearn.platform.shared.api.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 助手当前用户能力查询接口。 */
@RestController
@RequestMapping("/api/ai")
public class AiCapabilitiesController {
    private final AiProperties properties;
    private final TrustedAiRequestContextFactory contextFactory;
    private final AiToolRegistry toolRegistry;
    private final AiBusinessApiCatalogClient businessApiCatalogClient;

    /** 注入服务端模型配置、可信上下文和工具注册表。 */
    @org.springframework.beans.factory.annotation.Autowired
    public AiCapabilitiesController(AiProperties properties,
                                    TrustedAiRequestContextFactory contextFactory,
                                    AiToolRegistry toolRegistry,
                                    AiBusinessApiCatalogClient businessApiCatalogClient) {
        this.properties = properties;
        this.contextFactory = contextFactory;
        this.toolRegistry = toolRegistry;
        this.businessApiCatalogClient = businessApiCatalogClient;
    }

    /** 兼容既有控制器单元测试；生产构造器会注入 Gateway 业务 API 目录客户端。 */
    public AiCapabilitiesController(AiProperties properties,
                                    TrustedAiRequestContextFactory contextFactory,
                                    AiToolRegistry toolRegistry) {
        this(properties, contextFactory, toolRegistry, null);
    }

    /** 返回当前用户获准的 AI 查询工具；兼容导航字段固定为空。 */
    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public ApiResponse<AiCapabilitiesView> capabilities() {
        AiRequestContext context = contextFactory.current();
        String provider = properties.getProvider();
        boolean openWebUi = provider != null && ("open-webui".equalsIgnoreCase(provider)
                || "openwebui".equalsIgnoreCase(provider));
        String apiKey = openWebUi ? properties.getOpenWebuiApiKey() : properties.getApiKey();
        String model = openWebUi ? properties.getOpenWebuiModel() : properties.getModel();
        boolean providerEnabled = properties.isEnabled() && apiKey != null && !apiKey.isBlank();
        java.util.List<String> operations = openWebUi && businessApiCatalogClient != null
                ? businessApiCatalogClient.operationIds() : toolRegistry.availableToolNames(context);
        return ApiResponse.success(new AiCapabilitiesView(providerEnabled, model, true,
                operations, java.util.List.of()));
    }
}
