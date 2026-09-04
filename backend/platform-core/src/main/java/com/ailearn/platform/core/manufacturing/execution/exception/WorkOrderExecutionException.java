package com.ailearn.platform.core.manufacturing.execution.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** 工单生命周期受控业务异常，保留契约中的 MES_* 业务码。 */
public class WorkOrderExecutionException extends BaseException {

    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /** 创建带稳定业务码和具体说明的工单异常。 */
    public WorkOrderExecutionException(WorkOrderExecutionErrorCode errorCode, String detail) {
        super(errorCode.code(), errorCode.name() + " "
                + (detail == null || detail.isBlank() ? errorCode.message() : detail),
                errorCode.httpStatus());
        this.businessCode = errorCode.name();
    }

    /** 返回供统一异常包装和单元测试识别的业务码。 */
    public String getBusinessCode() {
        return businessCode;
    }
}
