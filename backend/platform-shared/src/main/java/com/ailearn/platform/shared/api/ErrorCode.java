package com.ailearn.platform.shared.api;

/**
 * 统一业务错误码接口规范。
 * <p>
 * 各业务模块的错误码枚举均应实现此接口，以便统一通过 {@link ApiResponse} 和异常处理器进行结构化响应。
 * </p>
 */
public interface ErrorCode {

    /**
     * 获取业务错误码或 HTTP 状态码。
     *
     * @return 整数型错误码（例如 200, 400, 401, 403, 404, 409, 422, 500 等）
     */
    int getCode();

    /**
     * 获取用户友好的默认错误描述信息。
     *
     * @return 错误提示文案
     */
    String getMessage();
}
