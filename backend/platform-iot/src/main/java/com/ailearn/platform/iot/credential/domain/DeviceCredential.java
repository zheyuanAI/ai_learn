package com.ailearn.platform.iot.credential.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 设备接入凭证；secret 只存在于创建响应，不进入此领域对象。 */
public record DeviceCredential(UUID id, UUID tenantId, UUID deviceId, String credentialReference,
                               String secretHash, String secretSalt, CredentialStatus status,
                               UUID createdBy, OffsetDateTime createdAt, UUID revokedBy, OffsetDateTime revokedAt) {
}
