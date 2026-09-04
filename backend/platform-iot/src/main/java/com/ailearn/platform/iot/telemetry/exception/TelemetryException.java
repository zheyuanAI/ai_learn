package com.ailearn.platform.iot.telemetry.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** 遥测摄取受控业务异常。 */
public class TelemetryException extends BaseException {

    private final String businessCode;

    /**
     * 用途：构造稳定的遥测业务错误；入参为错误码和具体原因；出参为平台统一异常。
     */
    public TelemetryException(TelemetryErrorCode code, String detail) {
        super(code.getCode(), code.businessCode() + " " + (detail == null || detail.isBlank()
                        ? code.getMessage() : detail), code.httpStatus());
        this.businessCode = code.businessCode();
    }

    /** 获取文本业务错误码。 */
    public String getBusinessCode() {
        return businessCode;
    }
}
