package com.ailearn.platform.core.manufacturing.execution.exception;

import org.springframework.http.HttpStatus;

/** WorkOrder 执行生命周期的稳定业务错误码。 */
public enum WorkOrderExecutionErrorCode {
    MES_WO_001(409, HttpStatus.CONFLICT, "WorkOrder 当前状态不允许该操作"),
    MES_WO_002(409, HttpStatus.CONFLICT, "WorkOrder 未下达，禁止开始生产"),
    MES_WO_003(422, HttpStatus.UNPROCESSABLE_ENTITY, "累计报工数量超出工单允许上限"),
    MES_WO_004(422, HttpStatus.UNPROCESSABLE_ENTITY, "来源销售订单行不存在、已失效或产品不一致"),
    MES_WO_005(422, HttpStatus.UNPROCESSABLE_ENTITY, "WorkOrder 基础字段或生产版本校验失败"),
    MES_TENANT_001(422, HttpStatus.UNPROCESSABLE_ENTITY, "关联对象不属于当前租户");

    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    WorkOrderExecutionErrorCode(int code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String message() {
        return message;
    }
}
