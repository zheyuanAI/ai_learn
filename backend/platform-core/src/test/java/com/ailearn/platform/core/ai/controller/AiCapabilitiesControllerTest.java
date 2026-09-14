package com.ailearn.platform.core.ai.controller;

import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolRegistry;
import com.ailearn.platform.core.ai.application.TrustedAiRequestContextFactory;
import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.dto.AiCapabilitiesView;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 能力接口必须反映当前实际 Provider 的凭据和模型预设。 */
class AiCapabilitiesControllerTest {

    @Test
    void shouldReportOpenWebUiConfiguration() {
        AiProperties properties = new AiProperties();
        properties.setEnabled(true);
        properties.setProvider("open-webui");
        properties.setApiKey("");
        properties.setOpenWebuiApiKey("open-webui-key");
        properties.setOpenWebuiModel("wms-assistant");
        AiRequestContext context = new AiRequestContext(UUID.randomUUID(), UUID.randomUUID(), "jti",
                Set.of(), ZoneId.of("Asia/Shanghai"), "fingerprint", "request-capabilities");
        TrustedAiRequestContextFactory contextFactory = mock(TrustedAiRequestContextFactory.class);
        when(contextFactory.current()).thenReturn(context);
        AiToolRegistry registry = mock(AiToolRegistry.class);
        when(registry.availableToolNames(context)).thenReturn(List.of());

        AiCapabilitiesView result = new AiCapabilitiesController(properties, contextFactory, registry)
                .capabilities().getData();

        assertTrue(result.providerEnabled());
        assertEquals("wms-assistant", result.modelId());
    }
}
