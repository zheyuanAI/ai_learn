package com.ailearn.platform.core.ai.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** AI 助手稳定业务错误码。 */
public enum AiErrorCode implements ErrorCode {
    AI_AUTH_001("AI_AUTH_001", 403, HttpStatus.FORBIDDEN, "当前用户无 AI 或指定工具权限"),
    AI_TENANT_001("AI_TENANT_001", 404, HttpStatus.NOT_FOUND, "目标数据不属于当前租户"),
    AI_INPUT_001("AI_INPUT_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "AI 查询条件不合法"),
    AI_TOOL_001("AI_TOOL_001", 503, HttpStatus.SERVICE_UNAVAILABLE, "AI 受控工具调用失败"),
    AI_TOOL_002("AI_TOOL_002", 403, HttpStatus.FORBIDDEN, "AI 工具未注册或未获许可"),
    AI_TOOL_003("AI_TOOL_003", 504, HttpStatus.GATEWAY_TIMEOUT, "AI 工具调用超时"),
    AI_SOURCE_001("AI_SOURCE_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "没有可用于回答的授权事实来源"),
    AI_PROVIDER_001("AI_PROVIDER_001", 503, HttpStatus.SERVICE_UNAVAILABLE, "AI 模型服务不可用");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    AiErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
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
