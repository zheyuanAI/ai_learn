package com.ailearn.platform.gateway.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

/** 验证 Open WebUI 服务调用、匹配唯一白名单并恢复原 WMS 用户委托身份。 */
@Service
public class AiDelegatedGatewayService {
    public static final String SERVICE_KEY_HEADER = "X-WMS-AI-Service-Key";
    public static final String CHAT_ID_HEADER = "X-WMS-OpenWebUI-Chat-Id";
    public static final String MESSAGE_ID_HEADER = "X-WMS-OpenWebUI-Message-Id";
    private static final String INVOCATION_KEY_PREFIX = "ai:openwebui:invocation:";
    private static final String ACTIVE_SESSION_KEY_PREFIX = "auth:session:";

    private final GatewayAiProperties properties;
    private final AiApiWhitelistCatalog whitelistCatalog;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AiDelegatedGatewayService(GatewayAiProperties properties,
                                     AiApiWhitelistCatalog whitelistCatalog,
                                     ReactiveStringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper) {
        this.properties = properties;
        this.whitelistCatalog = whitelistCatalog;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 任一 AI 专用 Header 出现即视为委托调用，禁止缺少部分 Header 时退回普通 JWT 分支。 */
    public boolean isAttempt(ServerHttpRequest request) {
        return StringUtils.hasText(request.getHeaders().getFirst(SERVICE_KEY_HEADER))
                || StringUtils.hasText(request.getHeaders().getFirst(CHAT_ID_HEADER))
                || StringUtils.hasText(request.getHeaders().getFirst(MESSAGE_ID_HEADER));
    }

    /**
     * 用途：认证一次 Open WebUI 业务 API 调用。
     * 入参：原请求与 Gateway requestId；出参：可信委托身份和白名单操作；流程：服务密钥、路径、短期上下文、次数及有效 JTI 逐层校验。
     */
    public Mono<DelegatedRequest> authenticate(ServerHttpRequest request, String gatewayRequestId) {
        // 修改用途：把同步校验也纳入响应式错误链，确保 GlobalFilter 能稳定转换为 4xx/5xx JSON。
        return Mono.defer(() -> {
            if (!properties.isEnabled()) {
                return Mono.error(new AiGatewayException(HttpStatus.SERVICE_UNAVAILABLE, "WMS AI 未启用"));
            }
            verifyServiceKey(request.getHeaders().getFirst(SERVICE_KEY_HEADER));
            String chatId = requiredOpaqueId(request.getHeaders().getFirst(CHAT_ID_HEADER), "Open WebUI ChatId");
            String messageId = requiredOpaqueId(request.getHeaders().getFirst(MESSAGE_ID_HEADER), "Open WebUI MessageId");
            String method = request.getMethod() == null ? "" : request.getMethod().name();
            AiApiWhitelistCatalog.Operation operation = whitelistCatalog.find(method, request.getURI().getPath())
                    .orElseThrow(() -> new AiGatewayException(HttpStatus.FORBIDDEN,
                            "AI 不允许调用该业务接口"));
            String invocationKey = invocationKey(chatId, messageId);
            return redisTemplate.opsForValue().get(invocationKey)
                    .switchIfEmpty(Mono.error(new AiGatewayException(HttpStatus.UNAUTHORIZED,
                            "AI 工具授权上下文不存在或已过期")))
                    .flatMap(json -> parseStored(json, operation, invocationKey, gatewayRequestId))
                    .flatMap(delegated -> incrementAndCheck(invocationKey)
                            .then(checkActiveSession(delegated))
                            .then(recordObservation(invocationKey, operation.id(), delegated.callId(),
                                    operation.method() + " " + operation.path(), "", 0,
                                    "Running", ""))
                            .thenReturn(delegated));
        });
    }

    /** 工具调用完成后写入与 Core 现有 SSE 轮询兼容的结果观察；记录失败不覆盖业务响应。 */
    public Mono<Void> recordFinished(DelegatedRequest request, HttpStatus status) {
        String result = status != null && status.is2xxSuccessful() ? "Success"
                : status == HttpStatus.FORBIDDEN ? "Denied" : "Failed";
        String source = request.operation().method() + " " + request.operation().path()
                + " -> HTTP " + (status == null ? 500 : status.value());
        long durationMs = Math.max(0, Duration.ofNanos(System.nanoTime() - request.startedNanos()).toMillis());
        return recordObservation(request.invocationKey(), request.operation().id(), request.callId(),
                request.operation().method() + " " + request.operation().path(),
                "HTTP " + (status == null ? 500 : status.value()), durationMs, result, source)
                .onErrorResume(ignored -> Mono.empty());
    }

    private Mono<DelegatedRequest> parseStored(String json, AiApiWhitelistCatalog.Operation operation,
                                                String invocationKey, String gatewayRequestId) {
        try {
            JsonNode stored = objectMapper.readTree(json);
            UUID tenantId = UUID.fromString(stored.path("tenantId").asText());
            UUID userId = UUID.fromString(stored.path("userId").asText());
            String jti = requiredText(stored.path("sessionTokenId").asText(), "委托会话 JTI");
            Set<String> allowed = objectMapper.convertValue(stored.path("allowedTools"),
                    objectMapper.getTypeFactory().constructCollectionType(Set.class, String.class));
            if (allowed != null && !allowed.isEmpty()
                    && allowed.stream().map(value -> value.toLowerCase(Locale.ROOT))
                    .noneMatch(operation.id().toLowerCase(Locale.ROOT)::equals)) {
                throw new AiGatewayException(HttpStatus.FORBIDDEN, "接口不在本次问答允许范围内");
            }
            String storedRequestId = stored.path("requestId").asText("");
            String requestId = StringUtils.hasText(storedRequestId) ? storedRequestId : gatewayRequestId;
            return Mono.just(new DelegatedRequest(tenantId, userId, jti, requestId,
                    invocationKey, UUID.randomUUID().toString(), System.nanoTime(), operation));
        } catch (AiGatewayException exception) {
            return Mono.error(exception);
        } catch (Exception exception) {
            return Mono.error(new AiGatewayException(HttpStatus.UNAUTHORIZED, "AI 授权上下文格式无效"));
        }
    }

    private Mono<Void> incrementAndCheck(String invocationKey) {
        return redisTemplate.opsForValue().increment(invocationKey + ":calls")
                .flatMap(count -> count != null && count <= properties.getMaxToolCalls()
                        ? Mono.<Void>empty()
                        : Mono.error(new AiGatewayException(HttpStatus.TOO_MANY_REQUESTS,
                        "AI 工具调用次数超过服务端上限")));
    }

    private Mono<Void> checkActiveSession(DelegatedRequest request) {
        String key = ACTIVE_SESSION_KEY_PREFIX + request.tenantId() + ":" + request.userId();
        return redisTemplate.opsForValue().get(key)
                .switchIfEmpty(Mono.error(new AiGatewayException(HttpStatus.UNAUTHORIZED,
                        "用户登录会话已失效")))
                .flatMap(activeJti -> MessageDigest.isEqual(activeJti.getBytes(StandardCharsets.UTF_8),
                        request.jti().getBytes(StandardCharsets.UTF_8))
                        ? Mono.<Void>empty()
                        : Mono.error(new AiGatewayException(HttpStatus.UNAUTHORIZED,
                        "用户登录会话已失效或被替换")));
    }

    private Mono<Void> recordObservation(String invocationKey, String operationId, String callId,
                                         String inputSummary, String outputSummary, long durationMs,
                                         String status, String sourceSummary) {
        try {
            String value = objectMapper.writeValueAsString(new ToolObservation(operationId,
                    sourceSummary, "", status, callId, inputSummary, outputSummary, durationMs));
            String resultKey = invocationKey + ":results";
            return redisTemplate.opsForList().rightPush(resultKey, value)
                    .then(redisTemplate.getExpire(invocationKey))
                    .flatMap(ttl -> ttl != null && !ttl.isNegative() && !ttl.isZero()
                            ? redisTemplate.expire(resultKey, ttl).then() : Mono.empty());
        } catch (Exception exception) {
            return Mono.empty();
        }
    }

    private void verifyServiceKey(String presented) {
        String configured = properties.getToolServiceSecret();
        if (!StringUtils.hasText(configured) || !StringUtils.hasText(presented)
                || !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8))) {
            throw new AiGatewayException(HttpStatus.UNAUTHORIZED, "AI 工具服务认证失败");
        }
    }

    private static String requiredOpaqueId(String value, String name) {
        String text = requiredText(value, name);
        if (text.length() > 128 || !text.matches("[A-Za-z0-9_:-]+")) {
            throw new AiGatewayException(HttpStatus.UNAUTHORIZED, name + " 格式无效");
        }
        return text;
    }

    private static String requiredText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new AiGatewayException(HttpStatus.UNAUTHORIZED, name + " 缺失");
        }
        return value.trim();
    }

    private static String invocationKey(String chatId, String messageId) {
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((chatId + "\n" + messageId).getBytes(StandardCharsets.UTF_8)));
            return INVOCATION_KEY_PREFIX + hash;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

    public record DelegatedRequest(UUID tenantId, UUID userId, String jti, String requestId,
                                   String invocationKey, String callId, long startedNanos,
                                   AiApiWhitelistCatalog.Operation operation) {
    }

    private record ToolObservation(String toolName, String sourceSummary,
                                   String timeRangeSummary, String status, String callId,
                                   String inputSummary, String outputSummary, long durationMs) {
    }

    /** 带稳定 HTTP 状态的 AI Gateway 拒绝，避免把内部异常细节返回给 Open WebUI。 */
    public static class AiGatewayException extends RuntimeException {
        private final HttpStatus status;

        public AiGatewayException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }
    }
}
