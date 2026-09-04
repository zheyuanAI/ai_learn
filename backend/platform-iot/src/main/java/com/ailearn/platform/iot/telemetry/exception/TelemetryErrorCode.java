package com.ailearn.platform.iot.telemetry.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 遥测摄取错误码；不修改既有 device 域错误码文件，避免扩大 Task 18 写集。
 */
public enum TelemetryErrorCode implements ErrorCode {
    INVALID_MESSAGE("IOT_TLM_001", 422, "遥测消息格式或指标值校验失败"),
    MISSING_MESSAGE_KEY("IOT_TLM_002", 422, "遥测缺少 message_id 与 sequence，无法建立去重键"),
    PAYLOAD_CONFLICT("IOT_TLM_003", 409, "同一设备去重键对应不同载荷");

    private final String businessCode;
    private final int code;
    private final String message;

    TelemetryErrorCode(String businessCode, int code, String message) {
        this.businessCode = businessCode;
        this.code = code;
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
        return code == 409 ? HttpStatus.CONFLICT : HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
