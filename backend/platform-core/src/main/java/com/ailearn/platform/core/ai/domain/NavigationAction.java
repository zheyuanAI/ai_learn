package com.ailearn.platform.core.ai.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import java.util.UUID;

/** 经服务端权限校验后返回给前端的页面导航建议。 */
public record NavigationAction(@JsonProperty("action_code") AiActionCode actionCode,
                               String label,
                               @JsonProperty("entity_type") String entityType,
                               @JsonProperty("entity_id") UUID entityId,
                               String reason,
                               @JsonProperty("confirm_text") String confirmText) {
    public NavigationAction {
        Objects.requireNonNull(actionCode, "actionCode 不能为空");
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label 不能为空");
        }
        entityType = entityType == null ? "" : entityType.trim();
        reason = reason == null ? "" : reason.trim();
        confirmText = confirmText == null ? "" : confirmText.trim();
    }
}
