package com.ailearn.platform.iot.telemetry.application;

import java.util.UUID;

/**
 * MQTT 凭证验证后的可信上下文。
 * 租户和设备身份由接入认证链路提供，摄取服务只接受该上下文，不信任载荷中的租户字段。
 */
public record TelemetryCredentialContext(UUID tenantId, UUID deviceId, String credentialReference) {
}
