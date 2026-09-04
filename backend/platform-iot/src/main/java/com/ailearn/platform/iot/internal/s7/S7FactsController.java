package com.ailearn.platform.iot.internal.s7;

import com.ailearn.platform.iot.alarm.domain.AlarmFact;
import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.domain.port.AlarmRepository;
import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Core S7 使用的 IoT 内部只读 Facts 接口。
 * <p>
 * 该接口不复用用户 JWT，也不信任用户身份 Header；每次请求必须使用服务名、时间戳和 HMAC 签名，
 * 且租户只作为签名后的查询范围传入。返回值是 IoT 自有事实的最小投影，不暴露凭证秘密。
 * </p>
 */
@RestController
@RequestMapping("/internal/s7/facts")
public class S7FactsController {
    private static final long MAX_CLOCK_SKEW_SECONDS = 120;
    private final DeviceRepository deviceRepository;
    private final AlarmRepository alarmRepository;
    private final DeviceStatusPort statusPort;
    private final String hmacSecret;
    private final Clock clock;

    /** 生产装配入口；密钥必须通过外部配置提供，不能写入仓库。 */
    public S7FactsController(DeviceRepository deviceRepository,
                             AlarmRepository alarmRepository,
                             DeviceStatusPort statusPort,
                             @Value("${iot.internal.s7-hmac-secret:}") String hmacSecret) {
        this(deviceRepository, alarmRepository, statusPort, hmacSecret, Clock.systemUTC());
    }

    /** 测试入口，允许固定时钟验证重放保护。 */
    S7FactsController(DeviceRepository deviceRepository, AlarmRepository alarmRepository,
                      DeviceStatusPort statusPort, String hmacSecret, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.alarmRepository = alarmRepository;
        this.statusPort = statusPort;
        this.hmacSecret = hmacSecret == null ? "" : hmacSecret.trim();
        this.clock = clock;
    }

    /** 返回设备或告警摘要；时间范围只约束告警事实，设备状态仍返回当前快照。 */
    @GetMapping("/summary/{kind}")
    public SummaryResponse summary(@PathVariable String kind,
                                   @RequestParam("tenant_id") UUID tenantId,
                                   @RequestParam(name = "device_id", required = false) UUID deviceId,
                                   @RequestParam(name = "from", required = false) OffsetDateTime from,
                                   @RequestParam(name = "to", required = false) OffsetDateTime to,
                                   HttpServletRequest request,
                                   @RequestHeader("X-Service-Name") String serviceName,
                                   @RequestHeader("X-Service-Timestamp") String timestamp,
                                   @RequestHeader("X-Service-Signature") String signature) {
        verify(request, serviceName, timestamp, signature);
        if ("device".equalsIgnoreCase(kind)) {
            return deviceSummary(tenantId, deviceId);
        }
        if ("alarm".equalsIgnoreCase(kind)) {
            return alarmSummary(tenantId, deviceId, from, to);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 Facts 摘要类型");
    }

    /** 返回设备主数据及当前状态引用，供 GIS 校验点位实体。 */
    @GetMapping("/device/{deviceId}")
    public EntityResponse device(@PathVariable UUID deviceId,
                                  @RequestParam("tenant_id") UUID tenantId,
                                  HttpServletRequest request,
                                  @RequestHeader("X-Service-Name") String serviceName,
                                  @RequestHeader("X-Service-Timestamp") String timestamp,
                                  @RequestHeader("X-Service-Signature") String signature) {
        verify(request, serviceName, timestamp, signature);
        return deviceRepository.findDeviceById(tenantId, deviceId)
                .map(value -> entity(value, statusPort.find(tenantId, deviceId)))
                .orElseGet(() -> EntityResponse.missing(tenantId, deviceId));
    }

    /** 返回点位所需的设备状态和当前活动告警摘要。 */
    @GetMapping("/status/{deviceId}")
    public PointStatusResponse status(@PathVariable UUID deviceId,
                                      @RequestParam("tenant_id") UUID tenantId,
                                      HttpServletRequest request,
                                      @RequestHeader("X-Service-Name") String serviceName,
                                      @RequestHeader("X-Service-Timestamp") String timestamp,
                                      @RequestHeader("X-Service-Signature") String signature) {
        verify(request, serviceName, timestamp, signature);
        if (deviceRepository.findDeviceById(tenantId, deviceId).isEmpty()) {
            return PointStatusResponse.missing(tenantId, deviceId);
        }
        DeviceStatus status = statusPort.find(tenantId, deviceId);
        List<AlarmFact> active = activeAlarms(tenantId, deviceId);
        AlarmFact latest = active.stream().max(Comparator.comparing(S7FactsController::alarmTime)).orElse(null);
        InstantHolder source = new InstantHolder(status.sourceTimestamp());
        for (AlarmFact alarm : active) {
            source = source.later(alarmTime(alarm));
        }
        return new PointStatusResponse(true, tenantId, deviceId, !active.isEmpty(),
                "Offline".equalsIgnoreCase(status.onlineStatus()),
                latest != null && isWarning(latest.alarmLevel()),
                latest == null ? null : latest.id(), latest == null ? null : latest.alarmLevel(),
                latest == null ? status.alarmStatus() : latest.status().name(),
                latest == null ? null : alarmTime(latest), source.value());
    }

    /** 返回设备或告警的 IoT 事实关系；上下文引用标记为 incomplete，避免伪称 Core 事实已存在。 */
    @GetMapping("/trace/{entityType}/{entityId}")
    public TraceResponse trace(@PathVariable String entityType,
                               @PathVariable UUID entityId,
                               @RequestParam("tenant_id") UUID tenantId,
                               HttpServletRequest request,
                               @RequestHeader("X-Service-Name") String serviceName,
                               @RequestHeader("X-Service-Timestamp") String timestamp,
                               @RequestHeader("X-Service-Signature") String signature) {
        verify(request, serviceName, timestamp, signature);
        String type = entityType == null ? "" : entityType.trim().toLowerCase();
        if (type.equals("device") || type.equals("iot_device")) {
            return traceDevice(tenantId, entityId);
        }
        if (type.equals("alarm") || type.equals("device_alarm") || type.equals("iot_alarm")) {
            return traceAlarm(tenantId, entityId);
        }
        return TraceResponse.empty("iot");
    }

    private SummaryResponse deviceSummary(UUID tenantId, UUID deviceId) {
        List<Device> devices = deviceId == null
                ? deviceRepository.findPage(tenantId, null, null, 0, 10000)
                : deviceRepository.findDeviceById(tenantId, deviceId).stream().toList();
        long online = 0;
        long offline = 0;
        long active = 0;
        OffsetDateTime updated = null;
        for (Device device : devices) {
            if (device.lifecycleStatus().name().equalsIgnoreCase("Active")) {
                active++;
            }
            DeviceStatus status = statusPort.find(tenantId, device.id());
            if ("Online".equalsIgnoreCase(status.onlineStatus())) {
                online++;
            } else {
                offline++;
            }
            updated = later(updated, status.sourceTimestamp());
            updated = later(updated, device.updatedAt());
        }
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("device_count", BigDecimal.valueOf(devices.size()));
        metrics.put("active_device_count", BigDecimal.valueOf(active));
        metrics.put("online_device_count", BigDecimal.valueOf(online));
        metrics.put("offline_device_count", BigDecimal.valueOf(offline));
        return new SummaryResponse(metrics, "iot device", updated);
    }

    private SummaryResponse alarmSummary(UUID tenantId, UUID deviceId,
                                         OffsetDateTime from, OffsetDateTime to) {
        List<AlarmFact> alarms = alarmRepository.findPage(tenantId, deviceId, null, null,
                from, to, null, 0, 10000);
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("alarm_count", BigDecimal.valueOf(alarms.size()));
        metrics.put("triggered_count", count(alarms, AlarmStatus.Triggered));
        metrics.put("acked_count", count(alarms, AlarmStatus.Acked));
        metrics.put("recovered_unacked_count", count(alarms, AlarmStatus.RecoveredUnacked));
        metrics.put("recovered_count", count(alarms, AlarmStatus.Recovered));
        OffsetDateTime updated = null;
        for (AlarmFact alarm : alarms) {
            updated = later(updated, alarmTime(alarm));
            updated = later(updated, alarm.updatedAt());
        }
        return new SummaryResponse(metrics, "iot alarm", updated);
    }

    private TraceResponse traceDevice(UUID tenantId, UUID deviceId) {
        OptionalDevice device = deviceRepository.findDeviceById(tenantId, deviceId)
                .map(value -> new OptionalDevice(value)).orElse(null);
        if (device == null) {
            return TraceResponse.empty("iot");
        }
        DeviceStatus status = statusPort.find(tenantId, deviceId);
        List<TraceNodeResponse> nodes = new ArrayList<>();
        List<TraceLinkResponse> links = new ArrayList<>();
        OffsetDateTime updated = status.sourceTimestamp();
        nodes.add(deviceNode(device.value(), status, true));
        for (AlarmFact alarm : activeAlarms(tenantId, deviceId)) {
            nodes.add(alarmNode(alarm));
            links.add(new TraceLinkResponse("device", deviceId, "alarm", alarm.id(), "device_alarm"));
            updated = later(updated, alarmTime(alarm));
            updated = later(updated, alarm.updatedAt());
        }
        return new TraceResponse(nodes, links, updated, "iot device");
    }

    private TraceResponse traceAlarm(UUID tenantId, UUID alarmId) {
        AlarmFact alarm = alarmRepository.findById(tenantId, alarmId).orElse(null);
        if (alarm == null) {
            return TraceResponse.empty("iot");
        }
        List<TraceNodeResponse> nodes = new ArrayList<>();
        List<TraceLinkResponse> links = new ArrayList<>();
        nodes.add(alarmNode(alarm));
        Device device = deviceRepository.findDeviceById(tenantId, alarm.deviceId()).orElse(null);
        if (device != null) {
            nodes.add(deviceNode(device, statusPort.find(tenantId, device.id()), true));
            links.add(new TraceLinkResponse("device", device.id(), "alarm", alarm.id(), "device_alarm"));
        }
        if (alarm.operationExecutionId() != null) {
            nodes.add(referenceNode(tenantId, "operation_execution", alarm.operationExecutionId(),
                    "operation-context", "mes:execution:manage"));
            links.add(new TraceLinkResponse("alarm", alarm.id(), "operation_execution",
                    alarm.operationExecutionId(), "business_context"));
        }
        if (alarm.workOrderId() != null) {
            nodes.add(referenceNode(tenantId, "work_order", alarm.workOrderId(),
                    "work-order-context", "mes:workorder:view"));
            links.add(new TraceLinkResponse("alarm", alarm.id(), "work_order",
                    alarm.workOrderId(), "business_context"));
        }
        return new TraceResponse(nodes, links, later(alarmTime(alarm), alarm.updatedAt()), "iot alarm");
    }

    private EntityResponse entity(Device device, DeviceStatus status) {
        OffsetDateTime updated = later(status.sourceTimestamp(), device.updatedAt());
        return new EntityResponse(true, device.tenantId(), "DEVICE", device.id(), device.deviceName(),
                "Offline".equalsIgnoreCase(status.onlineStatus()) ? "Offline" : "Normal",
                "/api/devices/" + device.id(), updated);
    }

    private TraceNodeResponse deviceNode(Device device, DeviceStatus status, boolean complete) {
        return new TraceNodeResponse(device.tenantId(), "device", device.id(), device.deviceName(),
                "Offline".equalsIgnoreCase(status.onlineStatus()) ? "Offline" : "Normal",
                "iot:device:view", later(status.sourceTimestamp(), device.updatedAt()), complete);
    }

    private static TraceNodeResponse alarmNode(AlarmFact alarm) {
        return new TraceNodeResponse(alarm.tenantId(), "alarm", alarm.id(), alarm.alarmNo(), alarm.status().name(),
                "iot:alarm:view", later(alarmTime(alarm), alarm.updatedAt()), true);
    }

    private static TraceNodeResponse referenceNode(UUID tenantId, String type, UUID id,
                                                   String label, String permission) {
        return new TraceNodeResponse(tenantId, type, id, label, "CONTEXT_REFERENCE", permission, null, false);
    }

    private List<AlarmFact> activeAlarms(UUID tenantId, UUID deviceId) {
        return alarmRepository.findPage(tenantId, deviceId, null, null, null, null, null, 0, 10000)
                .stream().filter(value -> value.status() == AlarmStatus.Triggered
                        || value.status() == AlarmStatus.Acked
                        || value.status() == AlarmStatus.RecoveredUnacked).toList();
    }

    private static BigDecimal count(List<AlarmFact> alarms, AlarmStatus status) {
        return BigDecimal.valueOf(alarms.stream().filter(value -> value.status() == status).count());
    }

    private static boolean isWarning(String level) {
        return level != null && (level.equalsIgnoreCase("WARNING") || level.equalsIgnoreCase("WARN"));
    }

    private static OffsetDateTime alarmTime(AlarmFact alarm) {
        return alarm.triggeredAt() == null ? alarm.createdAt() : alarm.triggeredAt();
    }

    private static OffsetDateTime later(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    /** 校验内部调用签名、服务名和有限时钟偏差，拒绝空密钥和重放请求。 */
    private void verify(HttpServletRequest request, String serviceName,
                        String timestampText, String signature) {
        if (hmacSecret.isBlank() || !"platform-core".equals(serviceName)
                || timestampText == null || signature == null || signature.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务认证失败");
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务时间戳无效");
        }
        if (Math.abs(clock.instant().getEpochSecond() - timestamp) > MAX_CLOCK_SKEW_SECONDS) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务请求已过期");
        }
        String query = request.getQueryString() == null ? "" : request.getQueryString();
        String canonical = timestampText + "\n" + request.getMethod() + "\n"
                + request.getRequestURI() + "\n" + query;
        byte[] expected = hmac(canonical);
        byte[] actual;
        try {
            actual = HexFormat.of().parseHex(signature.trim());
        } catch (IllegalArgumentException exception) {
            try {
                actual = Base64.getDecoder().decode(signature.trim());
            } catch (IllegalArgumentException ignored) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务签名格式无效");
            }
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "内部服务签名无效");
        }
    }

    private byte[] hmac(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算内部服务签名", exception);
        }
    }

    public record SummaryResponse(Map<String, BigDecimal> metrics,
                                  @com.fasterxml.jackson.annotation.JsonProperty("source_summary") String sourceSummary,
                                  @com.fasterxml.jackson.annotation.JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt) {
        public SummaryResponse {
            metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
            sourceSummary = sourceSummary == null ? "" : sourceSummary;
        }
    }

    public record EntityResponse(boolean present,
                                 @com.fasterxml.jackson.annotation.JsonProperty("tenant_id") UUID tenantId,
                                 @com.fasterxml.jackson.annotation.JsonProperty("entity_type") String entityType,
                                 @com.fasterxml.jackson.annotation.JsonProperty("entity_id") UUID entityId,
                                 @com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName,
                                 @com.fasterxml.jackson.annotation.JsonProperty("display_status") String displayStatus,
                                 @com.fasterxml.jackson.annotation.JsonProperty("linked_page") String linkedPage,
                                 @com.fasterxml.jackson.annotation.JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt) {
        static EntityResponse missing(UUID tenantId, UUID deviceId) {
            return new EntityResponse(false, tenantId, "DEVICE", deviceId, "", "", "", null);
        }
    }

    public record PointStatusResponse(boolean present,
                                      @com.fasterxml.jackson.annotation.JsonProperty("tenant_id") UUID tenantId,
                                      @com.fasterxml.jackson.annotation.JsonProperty("device_id") UUID deviceId,
                                      boolean alarm,
                                      boolean offline,
                                      boolean warning,
                                      @com.fasterxml.jackson.annotation.JsonProperty("alarm_id") UUID alarmId,
                                      @com.fasterxml.jackson.annotation.JsonProperty("alarm_level") String alarmLevel,
                                      @com.fasterxml.jackson.annotation.JsonProperty("alarm_status") String alarmStatus,
                                      @com.fasterxml.jackson.annotation.JsonProperty("occurred_at") OffsetDateTime occurredAt,
                                      @com.fasterxml.jackson.annotation.JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt) {
        static PointStatusResponse missing(UUID tenantId, UUID deviceId) {
            return new PointStatusResponse(false, tenantId, deviceId, false, false, false,
                    null, null, null, null, null);
        }
    }

    public record TraceResponse(List<TraceNodeResponse> nodes, List<TraceLinkResponse> links,
                                @com.fasterxml.jackson.annotation.JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt,
                                @com.fasterxml.jackson.annotation.JsonProperty("source_summary") String sourceSummary) {
        public TraceResponse {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            links = links == null ? List.of() : List.copyOf(links);
            sourceSummary = sourceSummary == null ? "" : sourceSummary;
        }

        static TraceResponse empty(String source) {
            return new TraceResponse(List.of(), List.of(), null, source);
        }
    }

    public record TraceNodeResponse(@com.fasterxml.jackson.annotation.JsonProperty("tenant_id") UUID tenantId,
                                     @com.fasterxml.jackson.annotation.JsonProperty("entity_type") String entityType,
                                     @com.fasterxml.jackson.annotation.JsonProperty("entity_id") UUID entityId,
                                     String label,
                                     String status,
                                     @com.fasterxml.jackson.annotation.JsonProperty("required_permission") String requiredPermission,
                                     @com.fasterxml.jackson.annotation.JsonProperty("source_updated_at") OffsetDateTime sourceUpdatedAt,
                                     boolean complete) {
    }

    public record TraceLinkResponse(@com.fasterxml.jackson.annotation.JsonProperty("from_type") String fromType,
                                    @com.fasterxml.jackson.annotation.JsonProperty("from_id") UUID fromId,
                                    @com.fasterxml.jackson.annotation.JsonProperty("to_type") String toType,
                                    @com.fasterxml.jackson.annotation.JsonProperty("to_id") UUID toId,
                                    String relation) {
    }

    private record OptionalDevice(Device value) {
    }

    private record InstantHolder(OffsetDateTime value) {
        private InstantHolder later(OffsetDateTime candidate) {
            return new InstantHolder(S7FactsController.later(value, candidate));
        }
    }
}
