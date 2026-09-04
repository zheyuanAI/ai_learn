package com.ailearn.platform.iot.credential.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 凭证非敏感视图，永不回显明文或摘要。 */
public record CredentialView(UUID id, @JsonProperty("device_id") UUID deviceId,
                             @JsonProperty("credential_reference") String credentialReference,
                             @JsonProperty("credential_status") String credentialStatus,
                             @JsonProperty("created_at") OffsetDateTime createdAt,
                             @JsonProperty("revoked_at") OffsetDateTime revokedAt) {
}
