package com.ailearn.platform.gateway.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Open WebUI 委托调用必须同时通过服务认证、精确白名单、短期上下文和当前登录会话校验。 */
class AiDelegatedGatewayServiceTest {
    private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String JTI = "active-jti";

    @TempDir
    Path tempDir;

    private GatewayAiProperties properties;
    private ReactiveValueOperations<String, String> valueOperations;
    private AiDelegatedGatewayService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        Path whitelist = tempDir.resolve("wms-ai-api-whitelist.yml");
        Files.writeString(whitelist, """
                version: 1
                services:
                  core:
                    openapi-url: http://127.0.0.1:10003/v3/api-docs
                    catalog-path: /v3/api-docs/ai-tools/core
                    name: Core
                groups:
                  sales:
                    description: 销售查询
                    operations:
                      - id: salesOrderDetail
                        service: core
                        method: GET
                        path: /api/sales-orders/{id}
                        summary: 查询销售订单详情
                """);
        properties = new GatewayAiProperties();
        properties.setEnabled(true);
        properties.setToolServiceSecret("service-secret");
        properties.setApiWhitelistPath(whitelist.toString());
        AiApiWhitelistCatalog catalog = new AiApiWhitelistCatalog(properties);
        catalog.load();

        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        ReactiveListOperations<String, String> listOperations = mock(ReactiveListOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForList()).thenReturn(listOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L));
        when(listOperations.rightPush(anyString(), anyString())).thenReturn(Mono.just(1L));
        when(redis.getExpire(anyString())).thenReturn(Mono.just(Duration.ofMinutes(2)));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        service = new AiDelegatedGatewayService(properties, catalog, redis, new ObjectMapper());
    }

    @Test
    void shouldRestoreTrustedIdentityForWhitelistedApi() {
        mockInvocationAndSession(JTI);

        StepVerifier.create(service.authenticate(request(HttpMethod.GET,
                        "/api/sales-orders/30000000-0000-0000-0000-000000000003"), "gateway-request"))
                .assertNext(delegated -> {
                    assertEquals(TENANT_ID, delegated.tenantId());
                    assertEquals(USER_ID, delegated.userId());
                    assertEquals(JTI, delegated.jti());
                    assertEquals("chat-request", delegated.requestId());
                    assertEquals("salesOrderDetail", delegated.operation().id());
                })
                .verifyComplete();
    }

    @Test
    void shouldRejectApiOutsideWhitelistBeforeReadingInvocation() {
        StepVerifier.create(service.authenticate(request(HttpMethod.POST,
                        "/api/sales-orders/30000000-0000-0000-0000-000000000003"), "gateway-request"))
                .expectErrorSatisfies(error -> {
                    AiDelegatedGatewayService.AiGatewayException exception =
                            (AiDelegatedGatewayService.AiGatewayException) error;
                    assertEquals(HttpStatus.FORBIDDEN, exception.status());
                })
                .verify();
    }

    @Test
    void shouldRejectReplacedUserSession() {
        mockInvocationAndSession("new-jti");

        StepVerifier.create(service.authenticate(request(HttpMethod.GET,
                        "/api/sales-orders/30000000-0000-0000-0000-000000000003"), "gateway-request"))
                .expectErrorSatisfies(error -> {
                    AiDelegatedGatewayService.AiGatewayException exception =
                            (AiDelegatedGatewayService.AiGatewayException) error;
                    assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
                })
                .verify();
    }

    private void mockInvocationAndSession(String activeJti) {
        String invocation = """
                {"tenantId":"%s","userId":"%s","sessionTokenId":"%s",
                 "requestId":"chat-request","allowedTools":[]}
                """.formatted(TENANT_ID, USER_ID, JTI);
        when(valueOperations.get(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            return key.startsWith("auth:session:") ? Mono.just(activeJti) : Mono.just(invocation);
        });
    }

    private MockServerHttpRequest request(HttpMethod method, String path) {
        return MockServerHttpRequest.method(method, path)
                .header(AiDelegatedGatewayService.SERVICE_KEY_HEADER, "service-secret")
                .header(AiDelegatedGatewayService.CHAT_ID_HEADER, "chat-1")
                .header(AiDelegatedGatewayService.MESSAGE_ID_HEADER, "message-1")
                .build();
    }
}
