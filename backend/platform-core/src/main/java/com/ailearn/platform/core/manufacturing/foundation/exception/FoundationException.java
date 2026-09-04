package com.ailearn.platform.core.manufacturing.foundation.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** foundation 受控业务异常，保留 MES_* 业务码供统一异常包装识别。 */
public class FoundationException extends BaseException {

    private static final long serialVersionUID = 1L;
    private final String businessCode;

    /**
     * 构造 foundation 业务异常。
     *
     * @param errorCode 稳定错误码
     * @param detail 具体失败说明
     */
    public FoundationException(FoundationErrorCode errorCode, String detail) {
        super(errorCode.getCode(), errorCode.businessCode() + " "
                + (detail == null || detail.isBlank() ? errorCode.getMessage() : detail),
                errorCode.httpStatus());
        this.businessCode = errorCode.businessCode();
    }

    public String getBusinessCode() {
        return businessCode;
    }
}
