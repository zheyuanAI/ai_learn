package com.ailearn.platform.core.stocktake.exception;

import com.ailearn.platform.shared.exception.BaseException;

/**
 * 盘点领域受控异常，保留 ST_* 业务码供 API 层识别。
 */
public class StocktakeException extends BaseException {

    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /**
     * 使用盘点错误码和补充说明构造异常。
     *
     * @param errorCode 盘点错误码
     * @param detail 补充说明
     */
    public StocktakeException(StocktakeErrorCode errorCode, String detail) {
        super(errorCode.getCode(), errorCode.businessCode() + " "
                + (detail == null || detail.isBlank() ? errorCode.getMessage() : detail),
                errorCode.httpStatus());
        this.businessCode = errorCode.businessCode();
    }

    /**
     * 获取 ST_* 业务码。
     *
     * @return 业务码
     */
    public String getBusinessCode() {
        return businessCode;
    }
}
