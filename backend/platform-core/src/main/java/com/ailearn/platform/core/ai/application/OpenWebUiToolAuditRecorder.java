package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.ai.domain.ToolAuditEntry;
import com.ailearn.platform.core.ai.exception.AiErrorCode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

/** 将 Gateway 回传的业务 API 调用结果保存为现有 AI 工具审计，不复制或解析业务响应正文。 */
@Component
public class OpenWebUiToolAuditRecorder {
    private final AiToolAuditStore auditStore;
    private final AiProperties properties;
    private final Clock clock;

    @Autowired
    public OpenWebUiToolAuditRecorder(AiToolAuditStore auditStore, AiProperties properties) {
        this(auditStore, properties, Clock.systemUTC());
    }

    OpenWebUiToolAuditRecorder(AiToolAuditStore auditStore, AiProperties properties, Clock clock) {
        this.auditStore = auditStore;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 用途：持久化一次 Open WebUI 问答中已结束的业务 API 调用。
     * 入参：可信用户、AI 会话和 Redis 观察列表；流程：按 callId 去重、映射状态并写入既有审计表。
     */
    public void record(AiRequestContext context, UUID sessionId,
                       List<OpenWebUiInvocationContextStore.ToolObservation> observations) {
        Map<String, OpenWebUiInvocationContextStore.ToolObservation> finished = new LinkedHashMap<>();
        if (observations != null) {
            for (OpenWebUiInvocationContextStore.ToolObservation observation : observations) {
                if (observation == null || "Running".equalsIgnoreCase(observation.status())) {
                    continue;
                }
                String key = StringUtils.hasText(observation.callId())
                        ? observation.callId() : observation.toolName() + "#" + finished.size();
                finished.put(key, observation);
            }
        }
        finished.values().forEach(observation -> save(context, sessionId, observation));
    }

    private void save(AiRequestContext context, UUID sessionId,
                      OpenWebUiInvocationContextStore.ToolObservation observation) {
        AiToolStatus status = status(observation.status());
        String errorCode = status == AiToolStatus.Denied ? AiErrorCode.AI_AUTH_001.businessCode()
                : status == AiToolStatus.Timeout ? AiErrorCode.AI_TOOL_003.businessCode()
                : status == AiToolStatus.Failed ? AiErrorCode.AI_TOOL_001.businessCode() : null;
        auditStore.save(new ToolAuditEntry(UUID.randomUUID(), context.tenantId(), context.userId(),
                sessionId, context.requestId(), safe(observation.toolName()),
                truncate(observation.inputSummary()), truncate(observation.outputSummary()),
                truncate(observation.sourceSummary()), truncate(observation.timeRangeSummary()),
                safe(observation.toolName()), properties.getOpenWebuiModel(), observation.durationMs(), status,
                errorCode, status == AiToolStatus.Success ? null : truncate(observation.outputSummary()),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }

    private static AiToolStatus status(String value) {
        if ("Success".equalsIgnoreCase(value)) {
            return AiToolStatus.Success;
        }
        if ("Denied".equalsIgnoreCase(value)) {
            return AiToolStatus.Denied;
        }
        if ("Timeout".equalsIgnoreCase(value)) {
            return AiToolStatus.Timeout;
        }
        return AiToolStatus.Failed;
    }

    private static String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        String normalized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000) + "…";
    }
}
