package com.ailearn.platform.core.quality.dto;

import com.ailearn.platform.core.quality.domain.QualityDispositionType;
import java.util.UUID;

/**
 * 质量处置决定请求。
 */
public class QualityDispositionRequest {

    private UUID inspectionId;
    private QualityDispositionType dispositionType;
    private String dispositionQty;
    private String reason;

    public UUID getInspectionId() { return inspectionId; }
    public void setInspectionId(UUID value) { this.inspectionId = value; }
    public QualityDispositionType getDispositionType() { return dispositionType; }
    public void setDispositionType(QualityDispositionType value) { this.dispositionType = value; }
    public String getDispositionQty() { return dispositionQty; }
    public void setDispositionQty(String value) { this.dispositionQty = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { this.reason = value; }
}
