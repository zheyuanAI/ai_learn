package com.ailearn.platform.core.manufacturing.dispatch.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** 派工受控业务异常。 */
public class DispatchException extends BaseException {
    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /** 创建稳定业务码异常。 */
    public DispatchException(DispatchErrorCode code, String detail) {
        super(code.getCode(), code.businessCode() + " " +
                (detail == null || detail.isBlank() ? code.getMessage() : detail), code.httpStatus());
        this.businessCode = code.businessCode();
    }

    public String getBusinessCode() { return businessCode; }
}
