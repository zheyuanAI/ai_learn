package com.ailearn.platform.core.inventory.exception;

import com.ailearn.platform.shared.exception.BaseException;

/**
 * 库存内核受控业务异常，保留可被 API 层识别的 INV_* 业务码。
 */
public class InventoryException extends BaseException {

    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /**
     * 使用库存错误码和补充说明构造异常。
     *
     * @param errorCode 库存错误码
     * @param detail 补充说明
     */
    public InventoryException(InventoryErrorCode errorCode, String detail) {
        super(errorCode.getCode(), errorCode.businessCode() + " "
                + (detail == null || detail.isBlank() ? errorCode.getMessage() : detail),
                errorCode.httpStatus());
        this.businessCode = errorCode.businessCode();
    }

    /**
     * 获取 INV_* 业务码。
     *
     * @return 业务码
     */
    public String getBusinessCode() {
        return businessCode;
    }
}
