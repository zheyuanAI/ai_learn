package com.ailearn.platform.gateway.filter;

import com.ailearn.platform.gateway.config.GatewaySecurityProperties;
import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.constants.HeaderConstants;
import com.ailearn.platform.shared.security.RsaKeyUtils;
import com.ailearn.platform.shared.security.jwt.JwtUtils;
import com.ailearn.platform.shared.security.jwt.TokenPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * 网关全局 JWT 认证与会话有效性校验过滤器。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>放行 OPTIONS 预检请求与白名单路径；</li>
 *   <li>提取 Authorization Bearer Token；</li>
 *   <li>使用 RSA 公钥验证 JWT 签名与有效期；</li>
 *   <li>校验 Token 中的 JTI 与 Redis 中的有效会话是否一致（单账号单会话控制）；</li>
 *   <li>将受信任的身份上下文注入下游请求头（X-User-Id, X-Tenant-Id, X-Username, X-Session-Id, X-Authorities, X-Request-Id）。</li>
 * </ol>
 * </p>
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);
    /** Redis 会话刚写入时允许短暂空读，最多重试两次，避免登录并发请求被误判为已注销。 */
    private static final int SESSION_CACHE_MISS_RETRIES = 2;
    private static final Duration SESSION_CACHE_MISS_RETRY_DELAY = Duration.ofMillis(100);

    private final GatewaySecurityProperties properties;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private volatile PublicKey publicKey;
    private volatile Mono<PublicKey> jwksLoadMono;

    public JwtAuthGlobalFilter(
            GatewaySecurityProperties properties,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    @PostConstruct
    public void initPublicKey() {
        String keyPem = StringUtils.hasText(properties.getPublicKey())
                ? properties.getPublicKey()
                : com.ailearn.platform.shared.security.DevRsaKeyDefaults.DEV_PUBLIC_KEY_PEM;
        try {
            this.publicKey = RsaKeyUtils.parsePublicKey(keyPem);
            log.info("网关已成功初始化 RSA 验签公钥（支持稳定开发公钥或配置公钥）");
        } catch (Exception e) {
            log.error("解析配置的 RSA 公钥失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 设置/动态更新验签公钥（主要用于测试或公钥轮换）。
     *
     * @param publicKey RSA 公钥
     */
    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 生成或继承链路追踪 ID
        String requestId = request.getHeaders().getFirst(HeaderConstants.X_REQUEST_ID);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString();
        }
        final String finalRequestId = requestId;

        // 设置响应头中的 Request-Id
        exchange.getResponse().getHeaders().set(HeaderConstants.X_REQUEST_ID, finalRequestId);

        // 2. 跨域预检 OPTIONS 请求直接放行
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // 3. 检查白名单放行
        if (isWhitelisted(path)) {
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(HeaderConstants.X_REQUEST_ID, finalRequestId)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // 4. 提取 Authorization: Bearer <token>
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(HeaderConstants.BEARER_PREFIX)) {
            log.warn("未提供认证令牌或格式不正确, path: {}", path);
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "未提供认证令牌或令牌格式不正确", finalRequestId);
        }

        String token = authHeader.substring(HeaderConstants.BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "认证令牌不能为空", finalRequestId);
        }

        // 5. 使用 RSA 公钥校验 JWT；优先使用缓存公钥，失败时支持主动刷新 JWKS 重试一次
        return resolvePublicKey(false)
                .onErrorResume(ex -> {
                    log.error("加载 Auth JWKS 验签公钥失败: {}", ex.getMessage(), ex);
                    return Mono.empty();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("网关尚未取得 RSA 验签公钥，无法校验 Token");
                    return Mono.error(new IllegalStateException("网关验签密钥未就绪"));
                }))
                .flatMap(key -> verifyAndForward(exchange, chain, token, key, finalRequestId, true));
    }

    /**
     * 解析固定公钥或按需从 Auth JWKS 获取公钥。
     * 入参：forceRefresh 是否强制刷新；出参：响应式 RSA 公钥；流程：优先使用配置公钥，若强制刷新则重新拉取 JWKS。
     *
     * @param forceRefresh 是否强制刷新 JWKS 缓存
     * @return RSA 公钥响应式结果
     */
    private Mono<PublicKey> resolvePublicKey(boolean forceRefresh) {
        if (forceRefresh) {
            this.publicKey = null;
            this.jwksLoadMono = null;
        }

        PublicKey currentKey = this.publicKey;
        if (currentKey != null) {
            return Mono.just(currentKey);
        }

        Mono<PublicKey> currentLoad = this.jwksLoadMono;
        if (currentLoad != null) {
            return currentLoad;
        }

        synchronized (this) {
            currentLoad = this.jwksLoadMono;
            if (currentLoad == null) {
                currentLoad = webClient.get()
                        .uri(properties.getJwksUrl())
                        .retrieve()
                        .bodyToMono(String.class)
                        .map(this::parseJwksPublicKey)
                        .doOnNext(key -> this.publicKey = key)
                        .onErrorResume(ex -> {
                            log.warn("向 Auth 拉取 JWKS 失败，尝试使用本地静态开发公钥兜底: {}", ex.getMessage());
                            try {
                                PublicKey devKey = com.ailearn.platform.shared.security.DevRsaKeyDefaults.loadDevPublicKey();
                                this.publicKey = devKey;
                                return Mono.just(devKey);
                            } catch (Exception e) {
                                return Mono.error(ex);
                            }
                        })
                        // 公钥缓存 60 秒 TTL，出现未知 kid 时可通过重试主动刷新
                        .cache(Duration.ofSeconds(60));
                this.jwksLoadMono = currentLoad;
            }
            return currentLoad;
        }
    }

    /**
     * 从 RFC 7517 JWKS JSON 中提取 RSA 公钥。
     * 入参：Auth JWKS JSON 文本；出参：RSA 公钥；流程：读取首个 RSA JWK，解码 n/e 后组装 X.509 公钥对象。
     *
     * @param jwksJson JWKS JSON 文本
     * @return RSA 公钥
     */
    private PublicKey parseJwksPublicKey(String jwksJson) {
        try {
            JsonNode keys = objectMapper.readTree(jwksJson).path("keys");
            for (JsonNode key : keys) {
                if (!"RSA".equals(key.path("kty").asText())) {
                    continue;
                }
                String modulus = key.path("n").asText(null);
                String exponent = key.path("e").asText(null);
                if (!StringUtils.hasText(modulus) || !StringUtils.hasText(exponent)) {
                    continue;
                }
                byte[] modulusBytes = Base64.getUrlDecoder().decode(modulus);
                byte[] exponentBytes = Base64.getUrlDecoder().decode(exponent);
                RSAPublicKeySpec keySpec = new RSAPublicKeySpec(
                        new BigInteger(1, modulusBytes),
                        new BigInteger(1, exponentBytes)
                );
                return KeyFactory.getInstance("RSA").generatePublic(keySpec);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Auth JWKS JSON 解析失败", e);
        }
        throw new IllegalStateException("Auth JWKS 中未找到可用 RSA 公钥");
    }

    /**
     * 验签并执行 Redis 会话校验后转发请求。
     * 入参：请求交换机、过滤器链、JWT、验签公钥、链路 ID 与是否允许重试；出参：响应式请求结果；
     * 流程：若验签失败且允许重试，强制刷新 JWKS 重新验签一次，防止 Auth 重启公钥失效。
     *
     * @param exchange 当前交换机
     * @param chain 过滤器链
     * @param token JWT 字符串
     * @param verificationKey RSA 验签公钥
     * @param finalRequestId 链路追踪 ID
     * @param allowRetry 是否允许在验签失败时强制刷新重试一次
     * @return 响应式请求结果
     */
    private Mono<Void> verifyAndForward(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String token,
            PublicKey verificationKey,
            String finalRequestId,
            boolean allowRetry) {
        Claims claims;
        try {
            claims = JwtUtils.parseAndVerify(token, verificationKey);
        } catch (ExpiredJwtException e) {
            log.warn("JWT 令牌已过期: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "认证令牌已过期，请重新登录", finalRequestId);
        } catch (JwtException | IllegalArgumentException e) {
            if (allowRetry) {
                log.info("JWT 验签初次失败，尝试主动刷新 Auth JWKS 公钥并重试一次...");
                return resolvePublicKey(true)
                        .flatMap(refreshedKey -> verifyAndForward(exchange, chain, token, refreshedKey, finalRequestId, false))
                        .onErrorResume(ex -> {
                            log.warn("刷新 JWKS 后重试验签仍失败: {}", ex.getMessage());
                            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "认证令牌签名无效或已被篡改", finalRequestId);
                        });
            }
            log.warn("JWT 令牌签名或格式无效: {}", e.getMessage());
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "认证令牌签名无效或已被篡改", finalRequestId);
        }

        TokenPayload payload = JwtUtils.extractPayload(claims);
        String userId = payload.getUserId();
        String tenantId = payload.getTenantId();
        String jti = payload.getJti();

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(jti)) {
            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "令牌缺少必要的身份或会话标识", finalRequestId);
        }

        // 6. 校验 Redis 中的有效会话（单账号单有效会话控制，硬依赖 Redis）
        if (properties.isSessionCheckEnabled() && redisTemplate != null) {
            String sessionKey = properties.getSessionKeyPrefix() + (StringUtils.hasText(tenantId) ? tenantId : "default") + ":" + userId;
            // 先处理 Redis 读取结果再转发；下游 Mono<Void> 正常完成本身为空，不能再用空结果判断会话未命中。
            return getSessionJtiWithMissRetry(sessionKey)
                    .timeout(Duration.ofMillis(2000))
                    .flatMap(cachedJti -> {
                        if (!jti.equals(cachedJti)) {
                            log.warn("用户 {} 的会话已被置换或失效, token_jti={}, redis_jti={}", userId, jti, cachedJti);
                            return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "会话已在其他设备登录或已失效", finalRequestId);
                        }
                        return forwardToDownstream(exchange, chain, payload, finalRequestId);
                    })
                    .onErrorResume(SessionCacheMissException.class, ignored -> {
                        log.warn("Redis 中未找到有效会话: key={}", sessionKey);
                        return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED, 401, "登录会话已过期或已被注销", finalRequestId);
                    })
                    .onErrorResume(ex -> {
                        log.error("校验 Redis 会话失败或超时: {}", ex.getMessage());
                        // Redis 异常时快速失败返回 503，禁止数据库回查与内存兜底
                        return writeErrorResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE, 503, "会话服务暂时不可用，请稍后重试", finalRequestId);
                    });
        }

        // 若未启用 Redis 会话校验，直接转发下游
        return forwardToDownstream(exchange, chain, payload, finalRequestId);
    }

    /**
     * 读取 Redis 会话 JTI，并对会话写入窗口内的空结果做有限重试。
     * 入参：完整会话 key；出参：当前 JTI，重试耗尽仍未命中时返回空 Mono；流程：将空读转换为内部哨兵异常重试，
     * Redis 连接异常保持原样向上抛出，交由调用方返回 503。
     *
     * @param sessionKey 完整 Redis 会话 key
     * @return 会话 JTI 或空结果
     */
    private Mono<String> getSessionJtiWithMissRetry(String sessionKey) {
        return Mono.defer(() -> redisTemplate.opsForValue().get(sessionKey))
                .switchIfEmpty(Mono.defer(() -> Mono.error(new SessionCacheMissException())))
                .retryWhen(Retry.fixedDelay(SESSION_CACHE_MISS_RETRIES, SESSION_CACHE_MISS_RETRY_DELAY)
                        .filter(SessionCacheMissException.class::isInstance)
                        .onRetryExhaustedThrow((retrySpec, retrySignal) -> retrySignal.failure()));
    }

    /** 仅用于区分“会话尚未写入”的空读与 Redis 连接异常。 */
    private static final class SessionCacheMissException extends RuntimeException {
        private SessionCacheMissException() {
            super("Redis session cache miss");
        }
    }

    /**
     * 将解析后的受信任身份注入请求头并转发至下游微服务。
     * <p>
     * 注意：严格遵循身份传递规范，仅透传用户 ID、租户 ID、用户名、会话 JTI 及链路跟踪 ID，
     * 不再从 JWT 中伪造或写入 X-Authorities 权限头（由下游微服务结合自身数据范围与权限表进行业务鉴权）。
     * </p>
     *
     * @param exchange       当前交换机
     * @param chain          过滤器链
     * @param payload        已验签的 Token 载荷
     * @param finalRequestId 链路追踪 ID
     * @return 响应式 Mono
     */
    private Mono<Void> forwardToDownstream(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            TokenPayload payload,
            String finalRequestId) {

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .header(HeaderConstants.X_REQUEST_ID, finalRequestId)
                .header(HeaderConstants.X_USER_ID, payload.getUserId() != null ? payload.getUserId() : "")
                .header(HeaderConstants.X_SESSION_ID, payload.getJti() != null ? payload.getJti() : "");

        if (StringUtils.hasText(payload.getTenantId())) {
            requestBuilder.header(HeaderConstants.X_TENANT_ID, payload.getTenantId());
        }
        if (StringUtils.hasText(payload.getUsername())) {
            requestBuilder.header(HeaderConstants.X_USERNAME, payload.getUsername());
        }

        // 阶段 0/1 规范：Gateway 不再注入 X-Authorities，由下游业务模块独立处理方法级权限与数据隔离

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    /**
     * 校验请求路径是否匹配白名单规则。
     *
     * @param path 请求路径
     * @return 若匹配白名单返回 true
     */
    private boolean isWhitelisted(String path) {
        List<String> whitelist = properties.getWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String pattern : whitelist) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 向客户端输出结构化的统一 JSON 错误响应。
     *
     * @param exchange   当前交换机
     * @param httpStatus HTTP 响应状态码
     * @param code       业务状态码
     * @param message    错误描述
     * @param requestId  请求追踪 ID
     * @return 响应式 Mono
     */
    public Mono<Void> writeErrorResponse(
            ServerWebExchange exchange,
            HttpStatus httpStatus,
            int code,
            String message,
            String requestId) {

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }

        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HeaderConstants.X_REQUEST_ID, requestId);

        ApiResponse<Void> apiResponse = ApiResponse.error(code, message);
        apiResponse.setRequestId(requestId);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsString(apiResponse).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":" + code + ",\"message\":\"" + message + "\",\"request_id\":\"" + requestId + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // 确保在绝大多数内置过滤器之前执行认证拦截
        return -100;
    }
}
