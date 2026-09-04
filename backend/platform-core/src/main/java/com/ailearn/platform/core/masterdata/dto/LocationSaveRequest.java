package com.ailearn.platform.core.masterdata.dto;

import java.util.UUID;

/**
 * 库位主数据创建/修改请求。
 * <p>
 * capacity 使用字符串传输，在应用层转换为 BigDecimal；type 只能使用领域冻结的六种标准库位类型。
 * </p>
 */
public class LocationSaveRequest extends MasterDataSaveRequest {

    private UUID warehouseId;
    private String code;
    private String name;
    private String type;
    private String capacity;
    private String description;

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
