package com.ailearn.platform.core.manufacturing.operation.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** 工序执行稳定错误码。 */
public enum OperationExecutionErrorCode implements ErrorCode {
    MES_OPERATION_001("MES_OPERATION_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "工序执行请求字段不合法"),
    MES_OPERATION_002("MES_OPERATION_002", 404, HttpStatus.NOT_FOUND, "工序执行记录不存在"),
    MES_OPERATION_003("MES_OPERATION_003", 409, HttpStatus.CONFLICT, "工序执行当前状态不允许该操作"),
    MES_OPERATION_004("MES_OPERATION_004", 409, HttpStatus.CONFLICT, "工单当前未 Released，禁止开始工序"),
    MES_OPERATION_005("MES_OPERATION_005", 422, HttpStatus.UNPROCESSABLE_ENTITY, "派工单与工序执行关联不一致"),
    MES_OPERATION_006("MES_OPERATION_006", 409, HttpStatus.CONFLICT, "同一设备存在多个活动工序，生产上下文不唯一");

    private final String businessCode;
    private final int code;
    private final HttpStatus status;
    private final String message;

    OperationExecutionErrorCode(String businessCode, int code, HttpStatus status, String message) {
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
