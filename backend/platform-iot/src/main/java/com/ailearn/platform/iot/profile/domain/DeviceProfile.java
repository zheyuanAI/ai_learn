package com.ailearn.platform.iot.profile.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 设备模型聚合；用途是定义设备类型和遥测指标白名单，不保存某台设备的运行事实。
 */
public record DeviceProfile(UUID id, UUID tenantId, String profileCode, String profileName,
                            String status, int offlineTimeoutSeconds, List<MetricDefinition> metrics,
                            UUID createdBy, OffsetDateTime createdAt, UUID updatedBy, OffsetDateTime updatedAt) {
    public DeviceProfile {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    /** 设备模型允许上报的单个指标定义。 */
    public record MetricDefinition(String metricCode, String metricName, MetricValueType valueType,
                                   String unit, boolean required) {
    }
}
