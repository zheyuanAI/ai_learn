package com.ailearn.platform.core.traceability.infrastructure;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.PointStatusFacts;
import com.ailearn.platform.core.traceability.ports.ReferencedEntity;
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import com.ailearn.platform.core.traceability.ports.TraceLink;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import com.ailearn.platform.core.traceability.ports.TraceQuery;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Core 到 IoT 的只读 Facts HTTP 适配器。
 * <p>
 * 仅在显式配置并启用时创建；每次调用都签名完整路径、查询串和服务时间戳，IoT 服务再校验时效。
 * Core 不依赖 IoT 模块和数据库表，远端不可用统一转换成事实源不可用异常。
 * </p>
 */
public class HttpIotFactsAdapter implements IotFactsPort {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final byte[] hmacSecret;
    private final Clock clock;

    public HttpIotFactsAdapter(String baseUrl, String hmacSecret) {
        this(baseUrl, hmacSecret, new RestTemplate(), Clock.systemUTC());
    }

    HttpIotFactsAdapter(String baseUrl, String hmacSecret, RestTemplate restTemplate, Clock clock) {
        if (baseUrl == null || baseUrl.isBlank() || hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("IoT Facts base-url 和 HMAC 密钥不能为空");
        }
        this.baseUrl = trimTrailingSlash(baseUrl.trim());
        this.hmacSecret = hmacSecret.trim().getBytes(StandardCharsets.UTF_8);
        this.restTemplate = restTemplate;
        this.clock = clock;
    }

    @Override
    public FactsSummary device(FactsQueryRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenant_id", request.context().tenantId().toString());
        addFilter(params, request, "device_id");
        SummaryResponse response = get("/internal/s7/facts/summary/device", params, SummaryResponse.class);
        return response.toSummary();
    }

    @Override
    public FactsSummary alarm(FactsQueryRequest request) {
        Map<String, String> params = commonSummaryParams(request);
        addFilter(params, request, "device_id");
        SummaryResponse response = get("/internal/s7/facts/summary/alarm", params, SummaryResponse.class);
        return response.toSummary();
    }

    @Override
    public FactsSummary traceSummary(FactsQueryRequest request) {
        FactsSummary devices = device(request);
        FactsSummary alarms = alarm(request);
        Map<String, java.math.BigDecimal> metrics = new LinkedHashMap<>(devices.metrics());
        alarms.metrics().forEach((key, value) -> metrics.merge(key, value, java.math.BigDecimal::add));
        Instant updated = later(devices.sourceUpdatedAt(), alarms.sourceUpdatedAt());
        return new FactsSummary(metrics, "iot device: " + devices.sourceSummary()
                + ", iot alarm: " + alarms.sourceSummary(), updated);
    }

    @Override
    public TraceFacts trace(TraceQuery query) {
        String type = query.entityType().trim();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenant_id", query.context().tenantId().toString());
        TraceResponse response = get("/internal/s7/facts/trace/" + type + "/" + query.entityId(),
                params, TraceResponse.class);
        List<TraceNode> nodes = response.nodes() == null ? List.of() : response.nodes().stream()
                .map(HttpIotFactsAdapter::node).toList();
        List<TraceLink> links = response.links() == null ? List.of() : response.links().stream()
                .map(HttpIotFactsAdapter::link).toList();
        return new TraceFacts(nodes, links, instant(response.sourceUpdatedAt()), response.sourceSummary());
    }

    @Override
    public Optional<ReferencedEntity> findDevice(FactsQueryContext context, UUID deviceId) {
        EntityResponse response = get("/internal/s7/facts/device/" + deviceId,
                Map.of("tenant_id", context.tenantId().toString()), EntityResponse.class);
        if (!response.present() || !context.tenantId().equals(response.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(new ReferencedEntity(response.tenantId(), response.entityType(), response.entityId(),
                response.displayName(), response.displayStatus(), response.linkedPage(),
                instant(response.sourceUpdatedAt()), true));
    }

    @Override
    public Optional<PointStatusFacts> pointStatus(FactsQueryContext context, UUID deviceId) {
        PointStatusResponse response = get("/internal/s7/facts/status/" + deviceId,
                Map.of("tenant_id", context.tenantId().toString()), PointStatusResponse.class);
        if (!response.present() || !context.tenantId().equals(response.tenantId())) {
            return Optional.empty();
        }
        return Optional.of(new PointStatusFacts(response.alarmId(), response.alarm(), response.offline(),
                response.warning(), response.alarmLevel(), response.alarmStatus(),
                instant(response.occurredAt()), instant(response.sourceUpdatedAt())));
    }

    private Map<String, String> commonSummaryParams(FactsQueryRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("tenant_id", request.context().tenantId().toString());
        params.put("from", request.from().toString());
        params.put("to", request.to().toString());
        return params;
    }

    private static void addFilter(Map<String, String> params, FactsQueryRequest request, String key) {
        String value = request.filters().get(key);
        if (value != null && !value.isBlank()) {
            params.put(key, value.trim());
        }
    }

    private <T> T get(String path, Map<String, String> params, Class<T> type) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).path(path);
            params.forEach(builder::queryParam);
            URI uri = builder.build().encode().toUri();
            long timestamp = clock.instant().getEpochSecond();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Service-Name", "platform-core");
            headers.set("X-Service-Timestamp", Long.toString(timestamp));
            headers.set("X-Service-Signature", signature(timestamp + "\nGET\n" + uri.getRawPath() + "\n"
                    + (uri.getRawQuery() == null ? "" : uri.getRawQuery())));
            T response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), type).getBody();
            if (response == null) {
                throw new IllegalStateException("IoT Facts 返回为空");
            }
            return response;
        } catch (RestClientException | IllegalArgumentException | IllegalStateException exception) {
            throw FactsAdapterSupport.unavailable("iot", exception);
        }
    }

    private String signature(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret, "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("IoT Facts HMAC 签名失败", exception);
        }
    }

    private static TraceNode node(NodeResponse value) {
        return new TraceNode(value.tenantId(), value.entityType(), value.entityId(), value.label(), value.status(),
                value.requiredPermission(), instant(value.sourceUpdatedAt()), value.complete());
    }

    private static TraceLink link(LinkResponse value) {
        return new TraceLink(value.fromType(), value.fromId(), value.toType(), value.toId(), value.relation());
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static Instant later(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public record SummaryResponse(Map<String, java.math.BigDecimal> metrics,
                                  @JsonProperty("source_summary") String sourceSummary,
                                  @JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt) {
        FactsSummary toSummary() {
            return new FactsSummary(metrics, sourceSummary, instant(sourceUpdatedAt));
        }
    }

    public record EntityResponse(boolean present,
                                 @JsonProperty("tenant_id") UUID tenantId,
                                 @JsonProperty("entity_type") String entityType,
                                 @JsonProperty("entity_id") UUID entityId,
                                 @JsonProperty("display_name") String displayName,
                                 @JsonProperty("display_status") String displayStatus,
                                 @JsonProperty("linked_page") String linkedPage,
                                 @JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt) {
    }

    public record PointStatusResponse(boolean present,
                                      @JsonProperty("tenant_id") UUID tenantId,
                                      @JsonProperty("device_id") UUID deviceId,
                                      boolean alarm,
                                      boolean offline,
                                      boolean warning,
                                      @JsonProperty("alarm_id") UUID alarmId,
                                      @JsonProperty("alarm_level") String alarmLevel,
                                      @JsonProperty("alarm_status") String alarmStatus,
                                      @JsonProperty("occurred_at") OffsetDateTime occurredAt,
                                      @JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt) {
    }

    public record TraceResponse(List<NodeResponse> nodes, List<LinkResponse> links,
                                @JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt,
                                @JsonProperty("source_summary") String sourceSummary) {
    }

    public record NodeResponse(@JsonProperty("tenant_id") UUID tenantId,
                               @JsonProperty("entity_type") String entityType,
                               @JsonProperty("entity_id") UUID entityId,
                               String label, String status,
                               @JsonProperty("required_permission") String requiredPermission,
                               @JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt,
                               boolean complete) {
    }

    public record LinkResponse(@JsonProperty("from_type") String fromType,
                               @JsonProperty("from_id") UUID fromId,
                               @JsonProperty("to_type") String toType,
                               @JsonProperty("to_id") UUID toId,
                               String relation) {
    }
}
