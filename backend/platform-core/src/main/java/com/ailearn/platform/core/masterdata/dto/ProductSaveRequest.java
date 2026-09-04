package com.ailearn.platform.core.masterdata.dto;

/**
 * 商品主数据创建/修改请求。
 * <p>
 * 金额和库存阈值通过字符串传输，避免 JSON 浮点数造成精度损失；应用层负责转换和 NUMERIC(19,6) 范围校验。
 * </p>
 */
public class ProductSaveRequest extends MasterDataSaveRequest {

    private String sku;
    private String name;
    private String spec;
    private String uom;
    private String category;
    private Boolean batchManaged;
    /** 兼容已有前端字段 batchMgmt，canonical 字段仍为 batchManaged。 */
    private Boolean batchMgmt;
    private String unitPrice;
    private String minStock;
    private String maxStock;
    private String safetyStock;

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getUom() {
        return uom;
    }

    public void setUom(String uom) {
        this.uom = uom;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Boolean getBatchManaged() {
        return batchManaged;
    }

    public void setBatchManaged(Boolean batchManaged) {
        this.batchManaged = batchManaged;
    }

    public Boolean getBatchMgmt() {
        return batchMgmt;
    }

    public void setBatchMgmt(Boolean batchMgmt) {
        this.batchMgmt = batchMgmt;
    }

    public String getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(String unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getMinStock() {
        return minStock;
    }

    public void setMinStock(String minStock) {
        this.minStock = minStock;
    }

    public String getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(String maxStock) {
        this.maxStock = maxStock;
    }

    public String getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(String safetyStock) {
        this.safetyStock = safetyStock;
    }
}
