package com.ailearn.platform.core.masterdata.dto;

/**
 * 商品主数据响应视图。
 * <p>
 * HTTP 数值字段保持字符串，避免前端浮点精度丢失；内部实体仍使用 BigDecimal。
 * </p>
 */
public class ProductView extends MasterDataView {

    private String sku;
    private String spec;
    private String uom;
    private String category;
    private boolean batchManaged;
    /** 兼容既有前端字段，值与 batchManaged 一致。 */
    private boolean batchMgmt;
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

    public boolean isBatchManaged() {
        return batchManaged;
    }

    public void setBatchManaged(boolean batchManaged) {
        this.batchManaged = batchManaged;
    }

    public boolean isBatchMgmt() {
        return batchMgmt;
    }

    public void setBatchMgmt(boolean batchMgmt) {
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
