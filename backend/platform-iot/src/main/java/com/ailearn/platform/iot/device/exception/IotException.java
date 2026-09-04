package com.ailearn.platform.iot.device.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** IoT 基础域受控业务异常。 */
public class IotException extends BaseException {

    private final String businessCode;

    /**
     * 用途：构造稳定 IoT 业务错误；入参为错误码和补充说明，出参为平台统一异常。
     * 流程：保留文本业务码并沿用错误码声明的 HTTP 状态。
     */
    public IotException(IotErrorCode code, String detail) {
        super(code.getCode(), code.businessCode() + " " + (detail == null || detail.isBlank() ? code.getMessage() : detail),
                code.httpStatus());
        this.businessCode = code.businessCode();
    }

    public String getBusinessCode() {
        return businessCode;
    }
}
