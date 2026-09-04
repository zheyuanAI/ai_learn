package com.ailearn.platform.core.manufacturing.dispatch.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** 派工边界稳定错误码。 */
public enum DispatchErrorCode implements ErrorCode {
    MES_DISPATCH_001("MES_DISPATCH_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "派工请求字段不合法"),
    MES_DISPATCH_002("MES_DISPATCH_002", 409, HttpStatus.CONFLICT, "工单当前未 Released，禁止派工或开始"),
    MES_DISPATCH_003("MES_DISPATCH_003", 404, HttpStatus.NOT_FOUND, "派工单不存在"),
    MES_DISPATCH_004("MES_DISPATCH_004", 409, HttpStatus.CONFLICT, "派工单当前状态不允许该操作"),
    MES_DISPATCH_005("MES_DISPATCH_005", 422, HttpStatus.UNPROCESSABLE_ENTITY, "关联对象不属于当前租户");

    private final String businessCode;
    private final int code;
    private final HttpStatus status;
    private final String message;

    DispatchErrorCode(String businessCode, int code, HttpStatus status, String message) {
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
