package com.ailearn.platform.core.manufacturing.contextquery.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** 生产上下文查询稳定错误码。 */
public enum ProductionContextErrorCode implements ErrorCode {
    MES_CONTEXT_001("MES_CONTEXT_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "生产上下文查询参数不合法"),
    MES_CONTEXT_002("MES_CONTEXT_002", 409, HttpStatus.CONFLICT, "告警时刻存在多个活动生产上下文");

    private final String businessCode;
    private final int code;
    private final HttpStatus status;
    private final String message;

    ProductionContextErrorCode(String businessCode, int code, HttpStatus status, String message) {
        this.businessCode = businessCode;
        this.code = code;
        this.status = status;
        this.message = message;
    }
    public String businessCode() { return businessCode; }
    @Override public int getCode() { return code; }
    @Override public String getMessage() { return message; }
    public HttpStatus httpStatus() { return status; }
}
