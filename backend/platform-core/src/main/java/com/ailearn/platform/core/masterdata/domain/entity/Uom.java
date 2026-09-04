package com.ailearn.platform.core.masterdata.domain.entity;

import com.ailearn.platform.shared.domain.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 计量单位领域实体，对应 md_uom。
 */
@TableName("md_uom")
public class Uom extends BaseEntity {

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("symbol")
    private String symbol;

    @TableField("decimal_scale")
    private Integer decimalScale;

    @TableField("remark")
    private String remark;

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

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public Integer getDecimalScale() {
        return decimalScale;
    }

    public void setDecimalScale(Integer decimalScale) {
        this.decimalScale = decimalScale;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
