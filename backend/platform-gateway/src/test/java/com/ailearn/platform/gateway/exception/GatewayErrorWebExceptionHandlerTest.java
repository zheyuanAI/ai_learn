package com.ailearn.platform.gateway.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("网关异常处理器 GatewayErrorWebExceptionHandler 测试")
class GatewayErrorWebExceptionHandlerTest {

    private ObjectMapper objectMapper;
    private GatewayErrorWebExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        exceptionHandler = new GatewayErrorWebExceptionHandler(objectMapper);
    }

    @Test
    @DisplayName("测试 404 Not Found 异常格式化为 ApiResponse")
    void testHandle404Exception() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/unknown/resource").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Not Found");

        StepVerifier.create(exceptionHandler.handle(exchange, ex))
                .verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertNotNull(responseBody);
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertEquals(404, jsonNode.get("code").asInt());
        assertTrue(jsonNode.get("message").asText().contains("网关未找到对应的服务或路由资源"));
        assertNotNull(jsonNode.get("request_id"));
    }

    @Test
    @DisplayName("测试 500 运行时异常格式化为 ApiResponse")
    void testHandle500Exception() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/core/some-path").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("数据库连接池耗尽");

        StepVerifier.create(exceptionHandler.handle(exchange, ex))
                .verifyComplete();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());

        String responseBody = exchange.getResponse().getBodyAsString().block();
        assertNotNull(responseBody);
        JsonNode jsonNode = objectMapper.readTree(responseBody);
        assertEquals(500, jsonNode.get("code").asInt());
        assertTrue(jsonNode.get("message").asText().contains("网关内部处理异常"));
    }
}
