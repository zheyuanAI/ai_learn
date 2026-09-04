package com.ailearn.platform.core.manufacturing.contextquery.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** 生产上下文查询受控业务异常。 */
public class ProductionContextException extends BaseException {
    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /** 创建稳定业务码异常。 */
    public ProductionContextException(ProductionContextErrorCode code, String detail) {
        super(code.getCode(), code.businessCode() + " " +
                (detail == null || detail.isBlank() ? code.getMessage() : detail), code.httpStatus());
        this.businessCode = code.businessCode();
    }
    public String getBusinessCode() { return businessCode; }
}
