package com.ailearn.platform.core.ai.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** AI 应用层异常，向上保留稳定的 AI_* 业务码。 */
public class AiException extends BaseException {
    private static final long serialVersionUID = 1L;
    private final String businessCode;

    public AiException(AiErrorCode errorCode, String detail) {
        super(errorCode.getCode(), errorCode.businessCode() + " "
                        + (detail == null || detail.isBlank() ? errorCode.getMessage() : detail),
                errorCode.httpStatus());
        this.businessCode = errorCode.businessCode();
    }

    public String getBusinessCode() {
        return businessCode;
    }
}
