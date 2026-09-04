package com.ailearn.platform.iot.telemetry.application;

import com.ailearn.platform.iot.device.domain.Device;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.TelemetryFact;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryQueryPort;
import com.ailearn.platform.iot.telemetry.exception.TelemetryErrorCode;
import com.ailearn.platform.iot.telemetry.exception.TelemetryException;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 遥测查询/模拟应用服务。
 * <p>
 * 查询先以可信租户确认设备，再通过遥测事实端口读取；模拟入口只接收 device_code，租户、设备 ID、接收时间和
 * payload hash 均由服务端确定，最后委托统一摄取服务，避免 HTTP 模拟链路绕过 MQTT 校验规则。
 * </p>
 */
@Service
public class TelemetryApplicationServiceImpl implements TelemetryApplicationService {
    private final DeviceRepository deviceRepository;
    private final TelemetryQueryPort queryPort;
    private final DeviceStatusPort statusPort;
    private final TelemetryIngestionService ingestionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean simulationEnabled;

    /**
     * 用途：组装遥测查询和模拟入口；入参均为设备/事实端口和统一摄取服务。
     */
    public TelemetryApplicationServiceImpl(DeviceRepository deviceRepository, TelemetryQueryPort queryPort,
                                            DeviceStatusPort statusPort, TelemetryIngestionService ingestionService,
                                            ObjectMapper objectMapper) {
        this(deviceRepository, queryPort, statusPort, ingestionService, objectMapper, Clock.systemUTC(), true);
    }

    /** Spring 生产构造器；模拟入口默认关闭，只有显式配置为 true 才可使用。 */
    @Autowired
    public TelemetryApplicationServiceImpl(DeviceRepository deviceRepository, TelemetryQueryPort queryPort,
                                            DeviceStatusPort statusPort, TelemetryIngestionService ingestionService,
                                            ObjectMapper objectMapper,
                                            @Value("${iot.telemetry.simulation.enabled:false}") boolean simulationEnabled) {
        this(deviceRepository, queryPort, statusPort, ingestionService, objectMapper, Clock.systemUTC(),
                simulationEnabled);
    }

    /** 测试构造入口，允许固定平台时钟。 */
    TelemetryApplicationServiceImpl(DeviceRepository deviceRepository, TelemetryQueryPort queryPort,
                                    DeviceStatusPort statusPort, TelemetryIngestionService ingestionService,
                                    ObjectMapper objectMapper, Clock clock) {
        this(deviceRepository, queryPort, statusPort, ingestionService, objectMapper, clock, true);
    }

    /** 测试构造入口，显式控制模拟开关并允许固定平台时钟。 */
    TelemetryApplicationServiceImpl(DeviceRepository deviceRepository, TelemetryQueryPort queryPort,
                                    DeviceStatusPort statusPort, TelemetryIngestionService ingestionService,
                                    ObjectMapper objectMapper, Clock clock, boolean simulationEnabled) {
        this.deviceRepository = deviceRepository;
        this.queryPort = queryPort;
        this.statusPort = statusPort;
        this.ingestionService = ingestionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.simulationEnabled = simulationEnabled;
    }

    /** 按当前可信租户查询原始遥测，不把 DeviceStatus 当作历史事实。 */
    @Override
    @PreAuthorize("hasAuthority('iot:telemetry:view')")
    public List<TelemetryFact> telemetry(UUID deviceId, String metricCode, OffsetDateTime from,
                                         OffsetDateTime to, int limit) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        requireDevice(tenantId, deviceId);
        validateRange(from, to);
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit 必须在 1 到 200 之间");
        }
        return queryPort.findFacts(tenantId, deviceId, normalize(metricCode), from, to, limit);
    }

    /** 查询当前租户设备的状态快照；不存在或跨租户设备统一按不可见处理。 */
    @Override
    @PreAuthorize("hasAuthority('iot:telemetry:view')")
    public DeviceStatus status(UUID deviceId) {
        UUID tenantId = TenantContextHolder.requireTenantId();
        requireDevice(tenantId, deviceId);
        return statusPort.find(tenantId, deviceId);
    }

    /**
     * 在演示/测试入口计算服务端摘要并委托统一摄取服务；不接受客户端租户、设备 ID、接收时间或摘要。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @PreAuthorize("hasAuthority('iot:device:simulate')")
    public TelemetryIngestionResult simulate(TelemetrySimulationRequest request) {
        if (!simulationEnabled) {
            throw new IotException(IotErrorCode.SIMULATION_DISABLED, "MQTT 模拟入口未启用");
        }
        UUID tenantId = TenantContextHolder.requireTenantId();
        if (request == null || request.deviceCode() == null || request.deviceCode().isBlank()) {
            throw new TelemetryException(TelemetryErrorCode.INVALID_MESSAGE, "device_code 不能为空");
        }
        String deviceCode = request.deviceCode().trim();
        Device device = deviceRepository.findByCode(tenantId, deviceCode)
                .orElseThrow(() -> new IotException(IotErrorCode.DEVICE_INVALID, "设备不存在或不属于当前租户"));
        OffsetDateTime receivedAt = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        String payloadHash = hash(new SimulationPayload(device.id(), device.deviceCode(), request.timestamp(),
                request.messageId(), request.sequence(), request.metrics()));
        TelemetryIngestionCommand command = new TelemetryIngestionCommand(
                new TelemetryCredentialContext(tenantId, device.id(), "simulate"), device.id(), device.deviceCode(),
                request.timestamp(), receivedAt, request.messageId(), request.sequence(), request.metrics(), payloadHash);
        return ingestionService.ingest(command);
    }

    private Device requireDevice(UUID tenantId, UUID deviceId) {
        if (deviceId == null) {
            throw new IotException(IotErrorCode.DEVICE_INVALID, "设备不存在或不属于当前租户");
        }
        return deviceRepository.findDeviceById(tenantId, deviceId)
                .orElseThrow(() -> new IotException(IotErrorCode.DEVICE_INVALID, "设备不存在或不属于当前租户"));
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("date_from 不能晚于 date_to");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String hash(Object value) {
        try {
            byte[] payload = objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成模拟遥测载荷摘要", exception);
        }
    }

    private record SimulationPayload(UUID deviceId, String deviceCode, OffsetDateTime timestamp,
                                     String messageId, Long sequence, List<TelemetryMetric> metrics) {
    }
}
