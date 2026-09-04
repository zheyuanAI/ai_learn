package com.ailearn.platform.gateway.filter;

import com.ailearn.platform.gateway.config.GatewaySecurityProperties;
import com.ailearn.platform.shared.constants.HeaderConstants;
import com.ailearn.platform.shared.security.RsaKeyUtils;
import com.ailearn.platform.shared.security.jwt.JwtUtils;
import com.ailearn.platform.shared.security.jwt.TokenPayload;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import io.jsonwebtoken.Claims;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("网关全局认证与会话过滤器 JwtAuthGlobalFilter 单元测试")
class JwtAuthGlobalFilterTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private GatewayFilterChain filterChain;

    private GatewaySecurityProperties properties;
    private ObjectMapper objectMapper;
    private JwtAuthGlobalFilter filter;

    private KeyPair keyPair;

    @BeforeEach
    void setUp() {
        properties = new GatewaySecurityProperties();
        properties.setSessionCheckEnabled(true);
        properties.setSessionKeyPrefix("auth:session:");
        properties.setWhitelist(List.of(
                "/api/auth/login",
                "/api/auth/jwks",
                "/api/auth/refresh",
                "/actuator/**",
                "/internal/**",
                "/v3/api-docs/**"
        ));

        keyPair = RsaKeyUtils.generateKeyPair();
        properties.setPublicKey(RsaKeyUtils.toPem(keyPair.getPublic()));

        objectMapper = new ObjectMapper();
        filter = new JwtAuthGlobalFilter(properties, redisTemplate, objectMapper);
        filter.setPublicKey(keyPair.getPublic());
    }

    @Test
    @DisplayName("测试用例1：无 Token 访问受保护接口返回 401 ApiResponse")
    void testProtectedEndpointWithoutTokenReturns401() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/core/inventory/balances")
                .header(HeaderConstants.X_REQUEST_ID, "req-no-token-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertNotNull(responseBody);
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertEquals(401, jsonNode.get("code").asInt());
        assertTrue(jsonNode.get("message").asText().contains("未提供认证令牌"));
        assertEquals("req-no-token-1", jsonNode.get("request_id").asText());
    }

    @Test
    @DisplayName("测试用例2：白名单接口直接放行并透传 Request-Id")
    void testWhitelistEndpointDirectlyPassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/auth/login")
                .header(HeaderConstants.X_REQUEST_ID, "req-whitelist-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(filterChain, times(1)).filter(captor.capture());

        ServerWebExchange capturedExchange = captor.getValue();
        assertEquals("req-whitelist-1", capturedExchange.getRequest().getHeaders().getFirst(HeaderConstants.X_REQUEST_ID));
    }

    @Test
    @DisplayName("测试用例3：有效 Token 验签及 Redis 会话校验成功后，向下游注入受信任 Header")
    void testValidTokenPassesAndInjectsHeaders() {
        String tenantId = "tenant-test-101";
        String userId = "user-test-202";
        String username = "operator_a";
        String jti = "session-uuid-001";
        Set<String> authorities = Set.of("ROLE_WAREHOUSE", "inventory:stock:move");

        TokenPayload payload = new TokenPayload(userId, tenantId, username, jti, authorities);
        String token = JwtUtils.generateToken(payload, keyPair.getPrivate(), Duration.ofHours(2));
        Claims tokenClaims = JwtUtils.parseAndVerify(token, keyPair.getPublic());
        assertNull(tokenClaims.get("authorities"), "JWT 不得携带可变 authorities 集合");
        assertNull(tokenClaims.get("roles"), "JWT 不得携带可变角色集合");
        assertNull(tokenClaims.get("permissions"), "JWT 不得携带可变权限集合");

        String sessionKey = "auth:session:" + tenantId + ":" + userId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(sessionKey)).thenReturn(Mono.just(jti));
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/core/purchase-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HeaderConstants.X_REQUEST_ID, "req-valid-1")
                .header(HeaderConstants.X_AUTHORITIES, "ROLE_ADMIN,inventory:stock:move")
                .header("X-Permissions", "inventory:stock:move")
                .header("X-Roles", "ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(filterChain, times(1)).filter(captor.capture());

        ServerHttpRequest forwardedRequest = captor.getValue().getRequest();
        assertEquals(userId, forwardedRequest.getHeaders().getFirst(HeaderConstants.X_USER_ID));
        assertEquals(tenantId, forwardedRequest.getHeaders().getFirst(HeaderConstants.X_TENANT_ID));
        assertEquals(username, forwardedRequest.getHeaders().getFirst(HeaderConstants.X_USERNAME));
        assertEquals(jti, forwardedRequest.getHeaders().getFirst(HeaderConstants.X_SESSION_ID));
        assertEquals("req-valid-1", forwardedRequest.getHeaders().getFirst(HeaderConstants.X_REQUEST_ID));

        String authHeader = forwardedRequest.getHeaders().getFirst(HeaderConstants.X_AUTHORITIES);
        assertNull(authHeader, "网关不再向请求头中注入 X-Authorities，由下游微服务业务鉴权独立判断");
        assertNull(forwardedRequest.getHeaders().getFirst("X-Permissions"), "网关必须清除客户端伪造的 X-Permissions");
        assertNull(forwardedRequest.getHeaders().getFirst("X-Roles"), "网关必须清除客户端伪造的 X-Roles");
        assertNull(exchange.getResponse().getStatusCode(), "下游 Mono<Void> 正常完成不能被误判为 Redis 会话未命中");
    }

    @Test
    @DisplayName("测试用例3-2：Redis 异常或连接超时，网关直接返回 503 快速失败")
    void testRedisExceptionReturns503() throws Exception {
        String tenantId = "tenant-test-101";
        String userId = "user-test-202";
        String username = "operator_a";
        String jti = "session-uuid-001";

        TokenPayload payload = new TokenPayload(userId, tenantId, username, jti, Set.of("ROLE_WAREHOUSE"));
        String token = JwtUtils.generateToken(payload, keyPair.getPrivate(), Duration.ofHours(2));

        String sessionKey = "auth:session:" + tenantId + ":" + userId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(sessionKey)).thenReturn(Mono.error(new org.springframework.data.redis.RedisConnectionFailureException("Redis connection refused")));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/core/purchase-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HeaderConstants.X_REQUEST_ID, "req-redis-err-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertNotNull(responseBody);
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertEquals(503, jsonNode.get("code").asInt());
        assertTrue(jsonNode.get("message").asText().contains("会话服务暂时不可用"));
    }

    @Test
    @DisplayName("测试用例3-3：Redis 会话首次空读但随后写入有效 JTI 时应重试并放行")
    void testTransientSessionMissRetriesBeforeRejecting() {
        String tenantId = "tenant-test-101";
        String userId = "user-test-202";
        String username = "operator_a";
        String jti = "session-uuid-001";

        TokenPayload payload = new TokenPayload(userId, tenantId, username, jti, Set.of("ROLE_WAREHOUSE"));
        String token = JwtUtils.generateToken(payload, keyPair.getPrivate(), Duration.ofHours(2));

        String sessionKey = "auth:session:" + tenantId + ":" + userId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 模拟会话写入与网关校验请求交错：第一次读取为空，随后读取到本次 Token 的 JTI。
        when(valueOperations.get(sessionKey)).thenReturn(Mono.empty(), Mono.just(jti));
        when(filterChain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/core/purchase-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HeaderConstants.X_REQUEST_ID, "req-transient-session-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        verify(valueOperations, times(2)).get(sessionKey);
        verify(filterChain, times(1)).filter(any(ServerWebExchange.class));
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("测试用例3-4：Redis 会话重试后仍未命中时返回 401")
    void testMissingSessionReturns401AfterRetries() throws Exception {
        String tenantId = "tenant-test-101";
        String userId = "user-test-202";
        String jti = "session-uuid-missing";

        TokenPayload payload = new TokenPayload(userId, tenantId, "operator_a", jti, Set.of());
        String token = JwtUtils.generateToken(payload, keyPair.getPrivate(), Duration.ofHours(2));

        String sessionKey = "auth:session:" + tenantId + ":" + userId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(sessionKey)).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/core/purchase-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HeaderConstants.X_REQUEST_ID, "req-missing-session-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());
        verify(valueOperations, times(3)).get(sessionKey);

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertNotNull(responseBody);
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertEquals(401, jsonNode.get("code").asInt());
        assertTrue(jsonNode.get("message").asText().contains("登录会话已过期或已被注销"));
    }

    @Test
    @DisplayName("测试用例4：被置换或失效的 JTI 访问返回 401 ApiResponse")
    void testReplacedJtiReturns401() throws Exception {
        String tenantId = "tenant-test-101";
        String userId = "user-test-202";
        String username = "operator_a";
        String oldJti = "old-session-uuid-001";
        String newJti = "new-session-uuid-999";

        TokenPayload payload = new TokenPayload(userId, tenantId, username, oldJti, Set.of("ROLE_WAREHOUSE"));
        String token = JwtUtils.generateToken(payload, keyPair.getPrivate(), Duration.ofHours(2));

        String sessionKey = "auth:session:" + tenantId + ":" + userId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Redis 中保存的已是新会话 JTI（即旧 Token 被置换）
        when(valueOperations.get(sessionKey)).thenReturn(Mono.just(newJti));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/core/purchase-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HeaderConstants.X_REQUEST_ID, "req-displaced-1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertNotNull(responseBody);
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertEquals(401, jsonNode.get("code").asInt());
        assertTrue(jsonNode.get("message").asText().contains("会话已在其他设备登录或已失效"));
    }

    @Test
    @DisplayName("测试用例5：过期 Token 访问返回 401 ApiResponse")
    void testExpiredTokenReturns401() throws Exception {
        String tenantId = "tenant-test-101";
        String userId = "user-test-202";
        String jti = "session-uuid-exp";

        TokenPayload payload = new TokenPayload(userId, tenantId, "user_exp", jti, Set.of());
        // 生成已过期的 Token
        String token = JwtUtils.generateToken(payload, keyPair.getPrivate(), Duration.ofSeconds(-60));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/core/purchase-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertNotNull(responseBody);
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertEquals(401, jsonNode.get("code").asInt());
        assertTrue(jsonNode.get("message").asText().contains("已过期"));
    }

    @Test
    @DisplayName("测试用例6：非法签名 Token 访问返回 401 ApiResponse")
    void testInvalidSignatureTokenReturns401() throws Exception {
        KeyPair anotherKeyPair = RsaKeyUtils.generateKeyPair();
        TokenPayload payload = new TokenPayload("u1", "t1", "test", "j1", Set.of());
        // 使用另一个私钥签名
        String token = JwtUtils.generateToken(payload, anotherKeyPair.getPrivate(), Duration.ofHours(1));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/core/purchase-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(filterChain, never()).filter(any());

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertNotNull(responseBody);
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertEquals(401, jsonNode.get("code").asInt());
        assertTrue(jsonNode.get("message").asText().contains("无效或已被篡改"));
    }
}
