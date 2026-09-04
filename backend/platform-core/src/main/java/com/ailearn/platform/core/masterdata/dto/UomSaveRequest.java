package com.ailearn.platform.core.masterdata.dto;

/**
 * 计量单位创建/修改请求。
 *
 * @param code         租户内唯一单位编码
 * @param name         单位名称
 * @param symbol       展示符号，可为空
 * @param decimalScale 小数位数，范围 0-6
 */
public class UomSaveRequest extends MasterDataSaveRequest {

    private String code;
    private String name;
    private String symbol;
    private Integer decimalScale;

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
}
