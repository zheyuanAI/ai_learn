package com.ailearn.platform.iot.contextlink.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.iot.contextlink.dto.ProductionContextQueryResponse;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class HttpProductionContextQueryAdapterTest {
    private static final UUID TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID DEVICE_ID = UUID.fromString("d0000000-0000-0000-0000-000000000001");
    private static final UUID WORK_ORDER_ID = UUID.fromString("f0000000-0000-0000-0000-000000000001");
    private static final UUID EXECUTION_ID = UUID.fromString("f0000000-0000-0000-0000-000000000002");
    private static final UUID OPERATION_ID = UUID.fromString("f0000000-0000-0000-0000-000000000003");
    private static final OffsetDateTime ALARM_TIME = OffsetDateTime.parse("2026-09-04T10:00:00Z");

    @Test
    void usesServiceHmacHeadersAndDoesNotForwardUserAuthorities() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ProductionContextQueryResponse response = new ProductionContextQueryResponse(TENANT_ID, DEVICE_ID,
                WORK_ORDER_ID, EXECUTION_ID, OPERATION_ID, ALARM_TIME.minusMinutes(5), ALARM_TIME.minusSeconds(1));
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(ProductionContextQueryResponse.class))).thenReturn(ResponseEntity.ok(response));
        HttpProductionContextQueryAdapter adapter = new HttpProductionContextQueryAdapter(
                "http://core.test/internal/production-context", "test-secret", restTemplate,
                Clock.fixed(Instant.parse("2026-09-04T10:01:00Z"), ZoneOffset.UTC));

        Optional<?> result = adapter.findActive(TENANT_ID, DEVICE_ID, ALARM_TIME);

        assertFalse(result.isEmpty());
        ArgumentCaptor<HttpEntity> entity = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(any(URI.class), eq(HttpMethod.GET), entity.capture(),
                eq(ProductionContextQueryResponse.class));
        assertEquals("platform-iot", entity.getValue().getHeaders().getFirst("X-Service-Name"));
        assertFalse(entity.getValue().getHeaders().getFirst("X-Service-Signature").isBlank());
        assertFalse(entity.getValue().getHeaders().containsKey("X-Authorities"));
        assertEquals(TENANT_ID + "\n" + DEVICE_ID + "\n" + ALARM_TIME.toInstant(),
                HttpProductionContextQueryAdapter.canonical(TENANT_ID, DEVICE_ID, ALARM_TIME));
    }
}
