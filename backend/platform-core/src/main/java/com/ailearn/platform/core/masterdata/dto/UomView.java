package com.ailearn.platform.core.masterdata.dto;

/**
 * 计量单位响应视图。
 */
public class UomView extends MasterDataView {

    private String symbol;
    private Integer decimalScale;

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
}
