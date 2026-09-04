package com.ailearn.platform.core.stocktake.dto;

import java.util.UUID;

/**
 * 盘点单创建请求；租户、操作人、状态和审计字段由服务端补齐。
 */
public class StocktakeCreateRequest {

    private String stocktakeNo;
    private UUID warehouseId;
    private UUID locationId;

    public String getStocktakeNo() {
        return stocktakeNo;
    }

    public void setStocktakeNo(String stocktakeNo) {
        this.stocktakeNo = stocktakeNo;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }
}
