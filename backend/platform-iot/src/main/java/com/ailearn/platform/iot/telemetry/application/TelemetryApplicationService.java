package com.ailearn.platform.iot.telemetry.application;

import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.TelemetryFact;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 遥测查询和模拟入口；MQTT 真实消费仍通过 {@link TelemetryIngestionService} 复用同一保存链路。
 */
public interface TelemetryApplicationService {

    /** 按当前租户查询设备原始遥测事实。 */
    List<TelemetryFact> telemetry(UUID deviceId, String metricCode, OffsetDateTime from,
                                  OffsetDateTime to, int limit);

    /** 查询当前租户设备状态快照。 */
    DeviceStatus status(UUID deviceId);

    /** 在受控模拟入口中构造可信设备上下文并摄取消息。 */
    TelemetryIngestionResult simulate(TelemetrySimulationRequest request);
}
