package com.ailearn.platform.core.manufacturing.productionfact.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** S5 Task16 生产事实受控业务异常。 */
public class ProductionFactException extends BaseException {

    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /** 创建带稳定业务码和补充说明的生产事实异常。 */
    public ProductionFactException(ProductionFactErrorCode errorCode, String detail) {
        super(errorCode.getCode(), errorCode.businessCode() + " "
                + (detail == null || detail.isBlank() ? errorCode.getMessage() : detail),
                errorCode.httpStatus());
        this.businessCode = errorCode.businessCode();
    }

    /** 返回稳定业务码。 */
    public String getBusinessCode() {
        return businessCode;
    }
}
