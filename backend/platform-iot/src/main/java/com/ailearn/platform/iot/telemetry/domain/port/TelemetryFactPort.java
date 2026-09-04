package com.ailearn.platform.iot.telemetry.domain.port;

import com.ailearn.platform.iot.telemetry.domain.TelemetryFact;
import java.util.List;
import java.util.UUID;

/**
 * 遥测原始事实追加端口。
 * 实现不得覆盖既有指标事实；所有读写必须显式携带 tenantId。
 */
public interface TelemetryFactPort {

    /**
     * 用途：一次追加同一消息的全部指标；入参为租户隔离的事实集合；出参为新事实 ID。
     */
    List<UUID> append(List<TelemetryFact> facts);
}
