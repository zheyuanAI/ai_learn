package com.ailearn.platform.iot.alarm.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** 告警生命周期受控业务异常。 */
public class AlarmException extends BaseException {
    private final String businessCode;

    public AlarmException(AlarmErrorCode code, String detail) {
        super(code.getCode(), code.businessCode() + " "
                        + (detail == null || detail.isBlank() ? code.getMessage() : detail),
                code.httpStatus());
        this.businessCode = code.businessCode();
    }

    public String getBusinessCode() {
        return businessCode;
    }
}
