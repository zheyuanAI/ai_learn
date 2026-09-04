package com.ailearn.platform.iot.credential.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 凭证首次创建结果；明文 secret 仅由该响应携带一次。 */
public record CredentialCreatedView(UUID id, @JsonProperty("device_id") UUID deviceId,
                                    @JsonProperty("credential_reference") String credentialReference,
                                    @JsonProperty("credential_status") String credentialStatus,
                                    @JsonProperty("plain_secret") String plainSecret,
                                    @JsonProperty("created_at") OffsetDateTime createdAt) {
}
