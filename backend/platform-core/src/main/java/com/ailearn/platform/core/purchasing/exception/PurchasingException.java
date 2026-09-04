package com.ailearn.platform.core.purchasing.exception;

import com.ailearn.platform.shared.exception.BaseException;

/**
 * 采购领域受控业务异常，保留 PO_* 稳定业务码。
 */
public class PurchasingException extends BaseException {

    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /**
     * 使用采购错误码和具体说明创建异常。
     *
     * @param errorCode 采购错误码
     * @param detail 具体说明
     */
    public PurchasingException(PurchasingErrorCode errorCode, String detail) {
        super(errorCode, detail == null || detail.isBlank()
                ? errorCode.getMessage() : errorCode.businessCode() + " " + detail,
                errorCode.httpStatus());
        this.businessCode = errorCode.businessCode();
    }

    /**
     * 获取 PO_* 业务码，供统一异常包装和调用方稳定识别。
     *
     * @return 采购业务码
     */
    public String getBusinessCode() {
        return businessCode;
    }
}
