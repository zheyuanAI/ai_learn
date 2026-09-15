package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.exception.AiErrorCode;
import com.ailearn.platform.core.ai.exception.AiException;
import com.ailearn.platform.shared.security.PermissionContextReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Open WebUI 工具回调的短期可信上下文存储。
 * Open WebUI 的 chat/message ID 只用于关联，真正授权仍以活跃 JTI、Redis 最新权限和工具注册表为准。
 */
@Component
public class OpenWebUiInvocationContextStore {
    private static final String KEY_PREFIX = "ai:openwebui:invocation:";
    private static final String ACTIVE_SESSION_KEY_PREFIX = "auth:session:";
    private final StringRedisTemplate redisTemplate;
    private final PermissionContextReader permissionContextReader;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final int maxToolCalls;

    /** 注入 Redis、统一权限读取器、JSON 映射器和上下文 TTL 配置。 */
    public OpenWebUiInvocationContextStore(StringRedisTemplate redisTemplate,
                                           PermissionContextReader permissionContextReader,
                                           ObjectMapper objectMapper,
                                           AiProperties properties) {
        this.redisTemplate = redisTemplate;
        this.permissionContextReader = permissionContextReader;
        this.objectMapper = objectMapper;
        this.ttl = positiveTtl(properties.getInvocationContextTtl());
        this.maxToolCalls = positiveLimit(properties.getMaxToolCalls());
    }

    /**
     * 用途：在发起 Open WebUI 生成任务前保存一次性授权关联。
     * 入参：Open WebUI 会话/消息 ID、本地 AI 会话、可信用户上下文和本次允许工具；流程：仅保存最小身份数据并设置短 TTL。
     */
    public void bind(String chatId, String messageId, UUID aiSessionId,
                     AiRequestContext context, Set<String> allowedTools) {
        validateCorrelation(chatId, messageId);
        if (!StringUtils.hasText(context.sessionTokenId())) {
            throw new AiException(AiErrorCode.AI_AUTH_001, "当前登录会话缺少有效 JTI");
        }
        StoredInvocation stored = new StoredInvocation(context.tenantId(), context.userId(),
                context.sessionTokenId(), context.tenantZone().getId(), context.requestId(), aiSessionId,
                normalizeTools(allowedTools));
        try {
            String invocationKey = key(chatId, messageId);
            redisTemplate.opsForValue().set(invocationKey, objectMapper.writeValueAsString(stored), ttl);
            redisTemplate.opsForValue().set(callCountKey(invocationKey), "0", ttl);
        } catch (AiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiException(AiErrorCode.AI_AUTH_001, "AI 授权上下文暂不可用");
        }
    }

    /**
     * 用途：恢复工具回调对应的真实用户并按当前状态重新授权。
     * 入参：Open WebUI 会话/消息 ID 与工具名；出参：最新权限上下文和本地会话号；流程：校验映射、JTI、权限及请求内工具子集。
     */
    public ResolvedInvocation resolve(String chatId, String messageId, String toolName) {
        validateCorrelation(chatId, messageId);
        String normalizedTool = normalizeTool(toolName);
        try {
            String value = redisTemplate.opsForValue().get(key(chatId, messageId));
            if (!StringUtils.hasText(value)) {
                throw new AiException(AiErrorCode.AI_AUTH_001, "AI 工具授权上下文不存在或已过期");
            }
            StoredInvocation stored = objectMapper.readValue(value, StoredInvocation.class);
            Long callCount = redisTemplate.opsForValue().increment(callCountKey(key(chatId, messageId)));
            if (callCount == null || callCount > maxToolCalls) {
                throw new AiException(AiErrorCode.AI_TOOL_001, "AI 工具调用次数超过服务端上限");
            }
            String activeJti = redisTemplate.opsForValue().get(
                    ACTIVE_SESSION_KEY_PREFIX + stored.tenantId() + ":" + stored.userId());
            if (!StringUtils.hasText(activeJti) || !MessageDigest.isEqual(
                    activeJti.getBytes(StandardCharsets.UTF_8), stored.sessionTokenId().getBytes(StandardCharsets.UTF_8))) {
                throw new AiException(AiErrorCode.AI_AUTH_001, "用户登录会话已失效或被替换");
            }
            Set<String> currentPermissions = permissionContextReader.readPermissions(stored.tenantId(), stored.userId());
            AiRequestContext current = new AiRequestContext(stored.tenantId(), stored.userId(), activeJti,
                    currentPermissions, ZoneId.of(stored.tenantZone()), fingerprint(currentPermissions),
                    stored.requestId());
            return new ResolvedInvocation(current, stored.aiSessionId(), stored.allowedTools());
        } catch (AiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AiException(AiErrorCode.AI_AUTH_001, "AI 授权上下文暂不可用");
        }
    }

    /**
     * 用途：记录 Open WebUI 已开始调用某个受控工具。
     * 入参：关联标识和工具名；出参：本次工具调用标识；流程：只保存工具名、调用标识和运行状态。
     */
    public String recordStarted(String chatId, String messageId, String toolName) {
        validateCorrelation(chatId, messageId);
        String callId = UUID.randomUUID().toString();
        recordObservation(chatId, messageId, new ToolObservation(
                toolName, "", "", "Running", callId, "", "", 0));
        return callId;
    }

    /**
     * 用途：暂存一次成功工具调用的非敏感来源元数据。
     * 入参：Open WebUI 关联标识、调用标识和标准工具结果；流程：只保存工具名、来源、时间范围与状态，不缓存业务数据正文。
     */
    public void recordResult(String chatId, String messageId, String callId, AiToolResult result) {
        validateCorrelation(chatId, messageId);
        if (result == null) {
            return;
        }
        recordObservation(chatId, messageId, new ToolObservation(result.toolName(), result.sourceSummary(),
                result.timeRangeSummary(), result.status().name(), callId, "", "", 0));
    }

    /** 兼容独立调用与既有测试：没有开始事件时仍可直接记录成功结果。 */
    public void recordResult(String chatId, String messageId, AiToolResult result) {
        recordResult(chatId, messageId, "", result);
    }

    /** 记录工具执行失败，使下游能够结束对应的运行中状态，不保存异常堆栈或业务正文。 */
    public void recordFailure(String chatId, String messageId, String callId, String toolName) {
        validateCorrelation(chatId, messageId);
        recordObservation(chatId, messageId, new ToolObservation(
                toolName, "", "", "Failed", callId, "", "", 0));
    }

    /** 查询当前问答已完成工具的非敏感来源元数据，供最终回答汇总。 */
    public List<ToolObservation> results(String chatId, String messageId) {
        validateCorrelation(chatId, messageId);
        try {
            List<String> values = redisTemplate.opsForList().range(resultKey(key(chatId, messageId)), 0, -1);
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return values.stream().map(value -> {
                try {
                    return objectMapper.readValue(value, ToolObservation.class);
                } catch (Exception ignored) {
                    return null;
                }
            }).filter(java.util.Objects::nonNull).toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private void recordObservation(String chatId, String messageId, ToolObservation observation) {
        try {
            String resultKey = resultKey(key(chatId, messageId));
            redisTemplate.opsForList().rightPush(resultKey, objectMapper.writeValueAsString(observation));
            redisTemplate.expire(resultKey, ttl);
        } catch (RuntimeException ignored) {
            // 过程元数据缓存失败不能覆盖已经完成的只读工具结果；数据库工具审计仍保留完整记录。
        } catch (Exception ignored) {
            // JSON 序列化异常同样按摘要降级处理，禁止影响主业务问答。
        }
    }

    /** 当前问答结束后主动删除短期关联；删除异常不覆盖主流程结果，TTL 仍会完成最终清理。 */
    public void remove(String chatId, String messageId) {
        if (!StringUtils.hasText(chatId) || !StringUtils.hasText(messageId)) {
            return;
        }
        try {
            String invocationKey = key(chatId, messageId);
            redisTemplate.delete(List.of(invocationKey, callCountKey(invocationKey), resultKey(invocationKey)));
        } catch (RuntimeException ignored) {
            // Redis 短暂故障时依靠 TTL 收口，禁止因清理失败篡改已经完成的业务回答。
        }
    }

    private static Duration positiveTtl(Duration configured) {
        if (configured == null || configured.isZero() || configured.isNegative()) {
            throw new IllegalStateException("AI 工具授权上下文 TTL 必须为正数");
        }
        return configured;
    }

    private static int positiveLimit(int configured) {
        if (configured <= 0) {
            throw new IllegalStateException("AI 工具调用次数上限必须为正数");
        }
        return configured;
    }

    private static void validateCorrelation(String chatId, String messageId) {
        if (!validOpaqueId(chatId) || !validOpaqueId(messageId)) {
            throw new AiException(AiErrorCode.AI_AUTH_001, "Open WebUI 关联标识无效");
        }
    }

    private static boolean validOpaqueId(String value) {
        return StringUtils.hasText(value) && value.length() <= 128
                && value.chars().allMatch(character -> Character.isLetterOrDigit(character)
                || character == '-' || character == '_' || character == ':');
    }

    private static String key(String chatId, String messageId) {
        return KEY_PREFIX + sha256(chatId + "\n" + messageId);
    }

    private static String callCountKey(String invocationKey) {
        return invocationKey + ":calls";
    }

    private static String resultKey(String invocationKey) {
        return invocationKey + ":results";
    }

    private static Set<String> normalizeTools(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream().map(OpenWebUiInvocationContextStore::normalizeTool)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static String normalizeTool(String value) {
        if (!StringUtils.hasText(value) || value.length() > 128) {
            throw new AiException(AiErrorCode.AI_TOOL_002, "AI 工具名称无效");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String fingerprint(Set<String> permissions) {
        List<String> normalized = permissions.stream().filter(StringUtils::hasText)
                .map(String::trim).sorted().toList();
        return sha256(String.join("\n", normalized));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

    /** 工具回调可使用的最新可信上下文以及 WMS 本地聊天会话。 */
    public record ResolvedInvocation(AiRequestContext context, UUID aiSessionId,
                                     Set<String> allowedTools) {
        public ResolvedInvocation {
            allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        }
    }

    /** Open WebUI 回答收口和 SSE 进度所需的非敏感工具观察结果。 */
    public record ToolObservation(String toolName, String sourceSummary,
                                  String timeRangeSummary, String status, String callId,
                                  String inputSummary, String outputSummary, long durationMs) {
        public ToolObservation(String toolName, String sourceSummary,
                               String timeRangeSummary, String status) {
            this(toolName, sourceSummary, timeRangeSummary, status, "", "", "", 0);
        }

        public ToolObservation(String toolName, String sourceSummary,
                               String timeRangeSummary, String status, String callId) {
            this(toolName, sourceSummary, timeRangeSummary, status, callId, "", "", 0);
        }

        public ToolObservation {
            toolName = toolName == null ? "" : toolName;
            sourceSummary = sourceSummary == null ? "" : sourceSummary;
            timeRangeSummary = timeRangeSummary == null ? "" : timeRangeSummary;
            status = status == null ? "" : status;
            callId = callId == null ? "" : callId;
            inputSummary = inputSummary == null ? "" : inputSummary;
            outputSummary = outputSummary == null ? "" : outputSummary;
            durationMs = Math.max(0, durationMs);
        }
    }

    private record StoredInvocation(UUID tenantId, UUID userId, String sessionTokenId,
                                    String tenantZone, String requestId, UUID aiSessionId,
                                    Set<String> allowedTools) {
        private StoredInvocation {
            allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        }
    }
}
