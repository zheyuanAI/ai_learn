package com.ailearn.platform.core.manufacturing.productionfact.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** S5 Task16 生产事实稳定错误码。 */
public enum ProductionFactErrorCode implements ErrorCode {
    MES_MAT_001("MES_MAT_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "生产领料库存确认失败或库存不足"),
    MES_MAT_002("MES_MAT_002", 422, HttpStatus.UNPROCESSABLE_ENTITY, "生产退料数量不合法或超出可退范围"),
    MES_WO_003("MES_WO_003", 422, HttpStatus.UNPROCESSABLE_ENTITY, "累计报工数量超出工单允许上限"),
    MES_QC_001("MES_QC_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "质检未通过，禁止对应数量直接成品入库"),
    MES_FG_001("MES_FG_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "成品入库数量超出检验合格且尚未入库的数量"),
    MES_FACT_001("MES_FACT_001", 409, HttpStatus.CONFLICT, "生产事实状态不允许当前操作"),
    MES_FACT_002("MES_FACT_002", 422, HttpStatus.UNPROCESSABLE_ENTITY, "生产事实字段或数量校验失败"),
    MES_TENANT_001("MES_TENANT_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "关联对象不属于当前租户");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    ProductionFactErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
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
