package com.ailearn.platform.core.manufacturing.operation.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** 工序执行受控业务异常。 */
public class OperationExecutionException extends BaseException {
    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /** 创建稳定业务码异常。 */
    public OperationExecutionException(OperationExecutionErrorCode code, String detail) {
        super(code.getCode(), code.businessCode() + " " +
                (detail == null || detail.isBlank() ? code.getMessage() : detail), code.httpStatus());
        this.businessCode = code.businessCode();
    }
    public String getBusinessCode() { return businessCode; }
}
