package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.ai.domain.ToolAuditEntry;
import com.ailearn.platform.core.ai.exception.AiErrorCode;
import com.ailearn.platform.core.ai.exception.AiException;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 执行 AI 只读工具并在所有结果路径写入脱敏审计。 */
@Service
public class AiToolExecutor {
    private final AiToolRegistry registry;
    private final AiToolAuditStore auditStore;
    private final AiAuditSanitizer sanitizer;
    private final AiProperties properties;
    private final Clock clock;
    private final ExecutorService executorService;
    private final AiDelegatedUserContext delegatedUserContext;
    private final Duration toolTimeout;

    /** 注入工具注册表、审计存储、脱敏器和服务端模型配置。 */
    @Autowired
    public AiToolExecutor(AiToolRegistry registry, AiToolAuditStore auditStore,
                          AiAuditSanitizer sanitizer, AiProperties properties,
                          ExecutorService executorService,
                          AiDelegatedUserContext delegatedUserContext) {
        this(registry, auditStore, sanitizer, properties, Clock.systemUTC(),
                executorService, delegatedUserContext);
    }

    /** 测试入口允许注入固定时钟。 */
    AiToolExecutor(AiToolRegistry registry, AiToolAuditStore auditStore,
                   AiAuditSanitizer sanitizer, AiProperties properties, Clock clock) {
        this(registry, auditStore, sanitizer, properties, clock, null, null);
    }

    /** 超时测试入口允许注入隔离执行器和用户上下文恢复器。 */
    AiToolExecutor(AiToolRegistry registry, AiToolAuditStore auditStore,
                   AiAuditSanitizer sanitizer, AiProperties properties, Clock clock,
                   ExecutorService executorService, AiDelegatedUserContext delegatedUserContext) {
        this.registry = registry;
        this.auditStore = auditStore;
        this.sanitizer = sanitizer;
        this.properties = properties;
        this.clock = clock;
        this.executorService = executorService;
        this.delegatedUserContext = delegatedUserContext;
        this.toolTimeout = positiveTimeout(properties.getToolTimeout());
    }

    /**
     * 用途：执行一次受控只读工具并统一审计。
     * 入参：可信上下文、可选聊天会话、工具名和模型参数；出参：标准工具结果；流程：注册校验、权限校验、执行、脱敏审计与错误映射。
     */
    public AiToolResult execute(AiRequestContext context, UUID chatSessionId,
                                String toolName, Map<String, Object> arguments) {
        return execute(context, chatSessionId, toolName, arguments, null);
    }

    /**
     * 用途：在统一审计边界内执行 Open WebUI 本次请求允许名单中的工具。
     * 入参：可信上下文、会话、工具、参数及请求级工具集合；流程：先审计名单拒绝，再执行固定注册表与领域权限校验。
     */
    public AiToolResult execute(AiRequestContext context, UUID chatSessionId,
                                String toolName, Map<String, Object> arguments,
                                Set<String> requestAllowedTools) {
        long startedNanos = System.nanoTime();
        String inputSummary = sanitizer.summarize(arguments);
        if (requestAllowedTools != null && requestAllowedTools.stream()
                .noneMatch(allowed -> allowed.equalsIgnoreCase(toolName == null ? "" : toolName.trim()))) {
            saveAudit(context, chatSessionId, toolName, inputSummary, "", "", "",
                    startedNanos, AiToolStatus.Denied, AiErrorCode.AI_TOOL_002,
                    "工具不在本次请求允许名单中");
            throw new AiException(AiErrorCode.AI_TOOL_002, "工具不在本次请求允许名单中");
        }
        AiReadTool tool = registry.find(toolName).orElse(null);
        if (tool == null) {
            saveAudit(context, chatSessionId, toolName, inputSummary, "", "", "",
                    startedNanos, AiToolStatus.Denied, AiErrorCode.AI_TOOL_002, "工具未注册");
            throw new AiException(AiErrorCode.AI_TOOL_002, "工具未注册或未获许可");
        }
        if (!tool.isAllowed(context)) {
            saveAudit(context, chatSessionId, tool.name(), inputSummary, "", "", "",
                    startedNanos, AiToolStatus.Denied, AiErrorCode.AI_AUTH_001, "缺少领域查看权限");
            throw new AiException(AiErrorCode.AI_AUTH_001, "当前用户无指定工具权限");
        }
        try {
            AiToolResult result = invoke(context, tool, arguments == null ? Map.of() : arguments);
            saveAudit(context, chatSessionId, tool.name(), inputSummary,
                    "工具返回授权事实", result.sourceSummary(), result.timeRangeSummary(),
                    startedNanos, AiToolStatus.Success, null, null);
            return result;
        } catch (TimeoutException exception) {
            saveAudit(context, chatSessionId, tool.name(), inputSummary, "", "", "",
                    startedNanos, AiToolStatus.Timeout, AiErrorCode.AI_TOOL_003, "领域事实查询超时");
            throw new AiException(AiErrorCode.AI_TOOL_003, "领域事实查询超时");
        } catch (IllegalArgumentException exception) {
            saveAudit(context, chatSessionId, tool.name(), inputSummary, "", "", "",
                    startedNanos, AiToolStatus.Failed, AiErrorCode.AI_INPUT_001, exception.getMessage());
            throw new AiException(AiErrorCode.AI_INPUT_001, exception.getMessage());
        } catch (AiException exception) {
            saveAudit(context, chatSessionId, tool.name(), inputSummary, "", "", "",
                    startedNanos, AiToolStatus.Failed, AiErrorCode.AI_TOOL_001, exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            saveAudit(context, chatSessionId, tool.name(), inputSummary, "", "", "",
                    startedNanos, AiToolStatus.Failed, AiErrorCode.AI_TOOL_001, "领域事实查询失败");
            throw new AiException(AiErrorCode.AI_TOOL_001, "领域事实查询失败");
        }
    }

    /** 在隔离线程内恢复可信用户上下文，并对所有工具统一施加服务端超时上限。 */
    private AiToolResult invoke(AiRequestContext context, AiReadTool tool,
                                Map<String, Object> arguments) throws TimeoutException {
        if (executorService == null || delegatedUserContext == null) {
            return tool.execute(context, arguments);
        }
        Future<AiToolResult> future = executorService.submit(
                () -> delegatedUserContext.call(context, () -> tool.execute(context, arguments)));
        try {
            return future.get(toolTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw exception;
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new AiException(AiErrorCode.AI_TOOL_001, "AI 工具查询已中断");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AiException(AiErrorCode.AI_TOOL_001, "领域事实查询失败");
        }
    }

    private static Duration positiveTimeout(Duration configured) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            throw new IllegalStateException("AI 工具查询超时必须为正数");
        }
        return configured;
    }

    private void saveAudit(AiRequestContext context, UUID chatSessionId, String toolName,
                           String inputSummary, String outputSummary, String sourceSummary,
                           String timeRangeSummary, long startedNanos, AiToolStatus status,
                           AiErrorCode errorCode, String errorReason) {
        long durationMs = Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        auditStore.save(new ToolAuditEntry(UUID.randomUUID(), context.tenantId(), context.userId(),
                chatSessionId, context.requestId(), safeToolName(toolName), inputSummary, outputSummary,
                sourceSummary, timeRangeSummary, safeToolName(toolName), configuredModel(), durationMs,
                status, errorCode == null ? null : errorCode.businessCode(), truncate(errorReason),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
    }

    /** 工具审计记录当前实际编排入口的模型标识，而不是固定写入直连 DeepSeek 配置。 */
    private String configuredModel() {
        String provider = properties.getProvider();
        return provider != null && ("open-webui".equalsIgnoreCase(provider)
                || "openwebui".equalsIgnoreCase(provider))
                ? properties.getOpenWebuiModel() : properties.getModel();
    }

    private static String safeToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "unknown";
        }
        String normalized = toolName.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1000 ? value : value.substring(0, 1000) + "…";
    }
}
