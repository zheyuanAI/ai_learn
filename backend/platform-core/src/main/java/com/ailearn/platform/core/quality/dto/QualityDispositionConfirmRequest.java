package com.ailearn.platform.core.quality.dto;

import java.util.UUID;

/**
 * 仓库确认质量处置请求；放行时 toLocationId 必须是 ReceivingStaging。
 */
public class QualityDispositionConfirmRequest {

    private UUID dispositionId;
    private UUID toLocationId;
    private UUID putawayTargetLocationId;

    public UUID getDispositionId() { return dispositionId; }
    public void setDispositionId(UUID value) { this.dispositionId = value; }
    public UUID getToLocationId() { return toLocationId; }
    public void setToLocationId(UUID value) { this.toLocationId = value; }
    public UUID getPutawayTargetLocationId() { return putawayTargetLocationId; }
    public void setPutawayTargetLocationId(UUID value) { this.putawayTargetLocationId = value; }
}
