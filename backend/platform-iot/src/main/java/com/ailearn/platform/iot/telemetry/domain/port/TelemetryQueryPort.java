package com.ailearn.platform.iot.telemetry.domain.port;

import com.ailearn.platform.iot.telemetry.domain.TelemetryFact;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 遥测事实查询端口；查询只返回当前租户和设备范围内的原始事实，不把状态快照当作历史数据。
 */
public interface TelemetryQueryPort {

    /**
     * 用途：按设备和时间范围读取遥测事实；入参为可信租户、设备、可选指标/时间和上限；出参按设备时间倒序排列。
     */
    List<TelemetryFact> findFacts(UUID tenantId, UUID deviceId, String metricCode,
                                  OffsetDateTime from, OffsetDateTime to, int limit);
}
