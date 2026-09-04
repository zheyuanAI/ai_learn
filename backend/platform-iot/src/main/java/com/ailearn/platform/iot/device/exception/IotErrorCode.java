package com.ailearn.platform.iot.device.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * IoT 设备基础域稳定错误码；文本业务码用于跨服务和前端识别，HTTP 状态由平台统一处理。
 */
public enum IotErrorCode implements ErrorCode {
    PROFILE_INVALID("IOT_PROFILE_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "设备模型不存在、已失效或指标定义无效"),
    DEVICE_INVALID("IOT_DEV_001", 404, HttpStatus.NOT_FOUND, "设备不存在、已禁用或不属于当前租户"),
    PROTOCOL_UNSUPPORTED("IOT_DEV_002", 422, HttpStatus.UNPROCESSABLE_ENTITY, "一期仅支持 MQTT 协议"),
    CREDENTIAL_INVALID("IOT_CRED_001", 401, HttpStatus.UNAUTHORIZED, "设备凭证无效、已撤销或与设备不匹配"),
    TENANT_VIOLATION("IOT_TENANT_001", 404, HttpStatus.NOT_FOUND, "关联对象不存在或不属于当前租户"),
    ALARM_RULE_INVALID("IOT_ALM_002", 422, HttpStatus.UNPROCESSABLE_ENTITY, "告警规则无效、指标不匹配或阈值配置不合法"),
    CONTEXT_INVALID("IOT_CTX_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "告警业务上下文不存在、不一致或不属于当前租户"),
    SIMULATION_DISABLED("IOT_TLM_004", 403, HttpStatus.FORBIDDEN, "MQTT 模拟入口未启用"),
    IDEMPOTENCY_CONFLICT("IOT_IDEMP_001", 409, HttpStatus.CONFLICT, "重复命令命中幂等记录");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    IotErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
        this.businessCode = businessCode;
        this.code = code;
        this.httpStatus = httpStatus;
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
        return httpStatus;
    }
}
