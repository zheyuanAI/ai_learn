package com.ailearn.platform.iot.alarm.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** 告警生命周期稳定错误码。 */
public enum AlarmErrorCode implements ErrorCode {
    STATE_INVALID("IOT_ALM_001", 409, HttpStatus.CONFLICT, "告警当前状态不允许执行该操作");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    AlarmErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
        this.businessCode = businessCode;
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String businessCode() {
        return businessCode;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
