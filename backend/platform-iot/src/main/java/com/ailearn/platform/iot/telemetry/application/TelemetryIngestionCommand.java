package com.ailearn.platform.iot.telemetry.application;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 遥测摄取命令。
 * 入参包含设备身份、已认证凭证上下文、设备采集时间、平台接收时间、消息标识、指标和原始载荷摘要。
 */
public record TelemetryIngestionCommand(
        TelemetryCredentialContext credentialContext,
        UUID deviceId,
        String deviceCode,
        OffsetDateTime timestamp,
        OffsetDateTime receivedAt,
        String messageId,
        Long sequence,
        List<TelemetryMetric> metrics,
        String payloadHash) {

    /**
     * 用途：冻结命令中的指标集合，避免摄取过程中被调用方修改。
     * 入参：外部提交的指标集合；出参：不可变集合；流程：空集合统一转为空列表。
     */
    public TelemetryIngestionCommand {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }
}
