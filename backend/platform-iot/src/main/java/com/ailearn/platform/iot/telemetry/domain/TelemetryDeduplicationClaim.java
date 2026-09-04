package com.ailearn.platform.iot.telemetry.domain;

import java.util.List;
import java.util.UUID;

/**
 * 去重端口声明结果。
 * NEW 表示当前调用取得写入权，DUPLICATE 返回首次写入的事实标识，CONFLICT 由应用层转换为业务异常。
 */
public record TelemetryDeduplicationClaim(Decision decision, TelemetryMessageKey key,
                                          String payloadHash, List<UUID> telemetryIds) {

    /** 去重声明结果。 */
    public enum Decision {
        NEW,
        DUPLICATE,
        CONFLICT
    }

    /**
     * 用途：冻结重复消息返回的遥测标识。
     */
    public TelemetryDeduplicationClaim {
        telemetryIds = telemetryIds == null ? List.of() : List.copyOf(telemetryIds);
    }
}
