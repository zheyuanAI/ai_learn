package com.ailearn.platform.shared.api;

/**
 * 平台通用错误码枚举。
 * <p>
 * 涵盖标准 HTTP 状态映射及系统核心通用错误码定义。
 * </p>
 */
public enum CommonErrorCode implements ErrorCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 客户端请求参数错误
     */
    BAD_REQUEST(400, "请求参数错误"),

    /**
     * 未认证或登录会话已过期
     */
    UNAUTHORIZED(401, "未登录或登录已过期"),

    /**
     * 无权限访问该资源
     */
    FORBIDDEN(403, "没有操作权限"),

    /**
     * 请求的资源或接口不存在
     */
    NOT_FOUND(404, "请求资源不存在"),

    /**
     * HTTP 请求方法不支持
     */
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),

    /**
     * 资源状态冲突或业务前置条件不满足
     */
    CONFLICT(409, "数据冲突或状态不满足要求"),

    /**
     * 幂等性冲突（请求正在处理中或重复提交）
     */
    IDEMPOTENT_CONFLICT(409, "请求正在处理或重复提交"),

    /**
     * 数据校验失败（Unprocessable Entity）
     */
    VALIDATION_ERROR(422, "数据校验不通过"),

    /**
     * 服务端内部异常
     */
    INTERNAL_SERVER_ERROR(500, "系统内部繁忙，请稍后重试"),

    /**
     * 服务不可用或正在维护
     */
    SERVICE_UNAVAILABLE(503, "服务暂时不可用");

    private final int code;
    private final String message;

    /**
     * 构造通用错误码枚举项。
     *
     * @param code    错误状态码
     * @param message 错误描述信息
     */
    CommonErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取状态码。
     *
     * @return 整数型状态码
     */
    @Override
    public int getCode() {
        return code;
    }

    /**
     * 获取错误描述文案。
     *
     * @return 中文描述信息
     */
    @Override
    public String getMessage() {
        return message;
    }
}
