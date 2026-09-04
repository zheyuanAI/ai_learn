package com.ailearn.platform.core.stocktake.dto;

import java.util.UUID;

/**
 * 盘点确认明细请求；数量使用字符串，避免前端浮点精度损失。
 */
public class StocktakeCountLineRequest {

    private UUID lineId;
    private String countedQty;
    private String varianceReason;

    public UUID getLineId() {
        return lineId;
    }

    public void setLineId(UUID lineId) {
        this.lineId = lineId;
    }

    public String getCountedQty() {
        return countedQty;
    }

    public void setCountedQty(String countedQty) {
        this.countedQty = countedQty;
    }

    public String getVarianceReason() {
        return varianceReason;
    }

    public void setVarianceReason(String varianceReason) {
        this.varianceReason = varianceReason;
    }
}
