package com.ailearn.platform.core.masterdata.dto;

import java.util.UUID;

/**
 * 库位主数据响应视图。
 * <p>
 * capacity 使用字符串对外传输；库位 type 原样返回领域冻结的六种枚举值。
 * </p>
 */
public class LocationView extends MasterDataView {

    private UUID warehouseId;
    private String warehouseName;
    private String type;
    private String capacity;
    private String description;

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
