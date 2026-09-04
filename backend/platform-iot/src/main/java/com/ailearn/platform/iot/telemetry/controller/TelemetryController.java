package com.ailearn.platform.iot.telemetry.controller;

import com.ailearn.platform.iot.telemetry.application.TelemetryApplicationService;
import com.ailearn.platform.iot.telemetry.application.TelemetryIngestionResult;
import com.ailearn.platform.iot.telemetry.application.TelemetrySimulationRequest;
import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.TelemetryFact;
import com.ailearn.platform.shared.api.ApiResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 遥测事实、设备状态和受控 MQTT 模拟 REST API。 */
@RestController
@RequestMapping("/api")
public class TelemetryController {
    private final TelemetryApplicationService service;

    /** 注入遥测应用端口。 */
    public TelemetryController(TelemetryApplicationService service) {
        this.service = service;
    }

    /** 查询当前租户设备的原始遥测事实。 */
    @GetMapping("/devices/{id}/telemetry")
    public ApiResponse<List<TelemetryFact>> telemetry(
            @PathVariable UUID id,
            @RequestParam(name = "metric_code", required = false) String metricCode,
            @RequestParam(name = "date_from", required = false) OffsetDateTime from,
            @RequestParam(name = "date_to", required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.success(service.telemetry(id, metricCode, from, to, limit));
    }

    /** 查询当前租户设备状态快照。 */
    @GetMapping("/devices/{id}/status")
    public ApiResponse<DeviceStatus> status(@PathVariable UUID id) {
        return ApiResponse.success(service.status(id));
    }

    /** 仅供演示/测试环境使用的 MQTT 模拟入口；内部复用统一遥测摄取服务。 */
    @PostMapping("/protocol-adapters/mqtt/simulate")
    public ApiResponse<TelemetryIngestionResult> simulate(@RequestBody TelemetrySimulationRequest request) {
        return ApiResponse.success(service.simulate(request));
    }
}
