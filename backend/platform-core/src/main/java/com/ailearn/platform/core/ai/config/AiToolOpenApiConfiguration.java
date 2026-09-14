package com.ailearn.platform.core.ai.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 只向 Open WebUI 发布受控 AI 工具路径，避免其读取整个 WMS OpenAPI。 */
@Configuration(proxyBeanMethods = false)
public class AiToolOpenApiConfiguration {
    /** 生成 /v3/api-docs/ai-tools，仅包含内部只读工具端点。 */
    @Bean
    public GroupedOpenApi aiToolsOpenApi() {
        return GroupedOpenApi.builder()
                .group("ai-tools")
                .pathsToMatch("/internal/ai/tools/**")
                .build();
    }
}
