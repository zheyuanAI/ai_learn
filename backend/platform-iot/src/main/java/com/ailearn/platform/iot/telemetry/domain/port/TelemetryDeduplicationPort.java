package com.ailearn.platform.iot.telemetry.domain.port;

import com.ailearn.platform.iot.telemetry.domain.TelemetryDeduplicationClaim;
import com.ailearn.platform.iot.telemetry.domain.TelemetryMessageKey;
import java.time.OffsetDateTime;

/**
 * 遥测消息去重端口。
 * 实现必须以 tenant_id + device_id + message key 做原子声明，并比较同键载荷摘要。
 */
public interface TelemetryDeduplicationPort {

    /**
     * 用途：原子声明一条消息的写入权；入参为租户隔离键、载荷摘要和接收时间；出参为新消息、重复或冲突。
     */
    TelemetryDeduplicationClaim claim(TelemetryMessageKey key, String payloadHash, OffsetDateTime receivedAt);

    /**
     * 用途：在遥测事实完成追加后补齐消息对应的事实标识，供重复投递复用首次结果。
     */
    void complete(TelemetryMessageKey key, java.util.List<java.util.UUID> telemetryIds);
}
