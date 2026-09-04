package com.ailearn.platform.core.manufacturing.foundation.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** foundation 阶段稳定错误码；MES 执行阶段沿用这些来源校验语义。 */
public enum FoundationErrorCode implements ErrorCode {
    MES_WO_001("MES_WO_001", 409, HttpStatus.CONFLICT, "WorkOrder 当前状态不允许该操作"),
    MES_WO_004("MES_WO_004", 422, HttpStatus.UNPROCESSABLE_ENTITY, "来源销售订单行不存在、已失效或产品不一致"),
    MES_WO_005("MES_WO_005", 422, HttpStatus.UNPROCESSABLE_ENTITY, "WorkOrder 基础字段或生产版本校验失败"),
    MES_TENANT_001("MES_TENANT_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "关联对象不属于当前租户"),
    MES_FOUNDATION_001("MES_FOUNDATION_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "制造基础数据校验失败"),
    MES_FOUNDATION_002("MES_FOUNDATION_002", 409, HttpStatus.CONFLICT, "幂等键载荷冲突或命令正在处理中");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    FoundationErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
        this.businessCode = businessCode;
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String businessCode() {
        return businessCode;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
