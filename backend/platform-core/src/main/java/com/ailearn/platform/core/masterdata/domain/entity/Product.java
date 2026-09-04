package com.ailearn.platform.core.masterdata.domain.entity;

import com.ailearn.platform.shared.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/**
 * 商品主数据领域实体，对应 md_product。
 */
@TableName("md_product")
public class Product extends BaseEntity {

    @TableField("sku")
    private String sku;

    @TableField("name")
    private String name;

    @TableField("spec")
    private String spec;

    @TableField("uom")
    private String uom;

    @TableField("category")
    private String category;

    @TableField("batch_managed")
    private Boolean batchManaged;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("min_stock")
    private BigDecimal minStock;

    @TableField("max_stock")
    private BigDecimal maxStock;

    @TableField("safety_stock")
    private BigDecimal safetyStock;

    @TableField("remark")
    private String remark;

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

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getMinStock() {
        return minStock;
    }

    public void setMinStock(BigDecimal minStock) {
        this.minStock = minStock;
    }

    public BigDecimal getMaxStock() {
        return maxStock;
    }

    public void setMaxStock(BigDecimal maxStock) {
        this.maxStock = maxStock;
    }

    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(BigDecimal safetyStock) {
        this.safetyStock = safetyStock;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
