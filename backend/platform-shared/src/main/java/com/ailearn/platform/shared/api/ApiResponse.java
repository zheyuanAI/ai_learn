package com.ailearn.platform.shared.api;

import com.ailearn.platform.shared.context.RequestContextHolder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * 全局统一 API 响应包装类。
 * <p>
 * 提供标准的响应结构体，包含业务状态码、描述信息、业务数据负载、请求追踪 ID 和时间戳。
 * 遵循平台接口契约规范：写接口及读接口统一按 { code, message, data, request_id, timestamp } 格式响应。
 * </p>
 *
 * @param <T> 响应业务数据类型
 */
@Schema(description = "统一 API 响应结构体")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 默认时区（中国标准时间 Asia/Shanghai）
     */
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    @Schema(description = "业务状态码，200 表示操作成功", example = "200", requiredMode = Schema.RequiredMode.REQUIRED)
    private int code;

    @Schema(description = "响应提示信息", example = "操作成功", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "业务数据负载")
    private T data;

    @JsonProperty("request_id")
    @Schema(description = "全局请求追踪唯一 ID", example = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d")
    private String requestId;

    @Schema(description = "响应生成时间戳（ISO-8601 格式，含 +08:00 偏移）", example = "2026-09-01T15:30:00+08:00")
    private OffsetDateTime timestamp;

    /**
     * 无参构造函数，默认初始化当前时间和请求追踪 ID。
     */
    public ApiResponse() {
        this.timestamp = OffsetDateTime.now(DEFAULT_ZONE);
        this.requestId = RequestContextHolder.getRequestId();
    }

    /**
     * 全参构造函数。
     *
     * @param code      业务状态码
     * @param message   响应提示信息
     * @param data      业务数据负载
     * @param requestId 全局请求追踪 ID
     * @param timestamp 响应时间戳
     */
    public ApiResponse(int code, String message, T data, String requestId, OffsetDateTime timestamp) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = (requestId != null) ? requestId : RequestContextHolder.getRequestId();
        this.timestamp = (timestamp != null) ? timestamp : OffsetDateTime.now(DEFAULT_ZONE);
    }

    /**
     * 构建无数据的成功响应。
     *
     * @param <T> 数据类型泛型
     * @return 状态码为 200 的统一响应对象
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(CommonErrorCode.SUCCESS.getCode(), CommonErrorCode.SUCCESS.getMessage(), null, null, null);
    }

    /**
     * 构建带有业务数据的成功响应。
     *
     * @param data 业务数据载荷
     * @param <T>  数据类型泛型
     * @return 状态码为 200 并包含数据的统一响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(CommonErrorCode.SUCCESS.getCode(), CommonErrorCode.SUCCESS.getMessage(), data, null, null);
    }

    /**
     * 构建带有自定义提示信息与业务数据的成功响应。
     *
     * @param message 自定义成功提示信息
     * @param data    业务数据载荷
     * @param <T>     数据类型泛型
     * @return 包含自定义信息与数据的统一响应对象
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(CommonErrorCode.SUCCESS.getCode(), message, data, null, null);
    }

    /**
     * 构建无数据的成功响应（ok 别名）。
     *
     * @param <T> 数据类型泛型
     * @return 状态码为 200 的统一响应对象
     */
    public static <T> ApiResponse<T> ok() {
        return success();
    }

    /**
     * 构建带有业务数据的成功响应（ok 别名）。
     *
     * @param data 业务数据载荷
     * @param <T>  数据类型泛型
     * @return 包含数据的统一响应对象
     */
    public static <T> ApiResponse<T> ok(T data) {
        return success(data);
    }

    /**
     * 构建带有自定义提示信息与业务数据的成功响应（ok 别名）。
     *
     * @param message 自定义成功提示信息
     * @param data    业务数据载荷
     * @param <T>     数据类型泛型
     * @return 包含自定义信息与数据的统一响应对象
     */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return success(message, data);
    }

    /**
     * 根据错误码和提示信息构建错误响应。
     *
     * @param code    错误状态码
     * @param message 错误提示信息
     * @param <T>     数据类型泛型
     * @return 错误统一响应对象
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, null, null);
    }

    /**
     * 根据错误码枚举构建错误响应。
     *
     * @param errorCode 错误码枚举
     * @param <T>       数据类型泛型
     * @return 错误统一响应对象
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        Objects.requireNonNull(errorCode, "errorCode 不能为 null");
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null, null, null);
    }

    /**
     * 根据错误码枚举和自定义错误描述构建错误响应。
     *
     * @param errorCode 错误码枚举
     * @param message   覆盖默认文案的自定义错误描述
     * @param <T>       数据类型泛型
     * @return 错误统一响应对象
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        Objects.requireNonNull(errorCode, "errorCode 不能为 null");
        return new ApiResponse<>(errorCode.getCode(), message, null, null, null);
    }

    /**
     * 根据错误码、提示信息和错误附带数据构建错误响应。
     *
     * @param code    错误状态码
     * @param message 错误提示信息
     * @param data    错误详情数据（例如字段校验明细）
     * @param <T>     数据类型泛型
     * @return 包含错误详情数据的统一响应对象
     */
    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, null, null);
    }

    /**
     * 判断当前响应是否表示成功状态。
     *
     * @return 若状态码为 200 则返回 true，否则返回 false
     */
    public boolean isSuccess() {
        return this.code == CommonErrorCode.SUCCESS.getCode();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiResponse<?> that)) return false;
        return code == that.code &&
                Objects.equals(message, that.message) &&
                Objects.equals(data, that.data) &&
                Objects.equals(requestId, that.requestId) &&
                Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, message, data, requestId, timestamp);
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                ", requestId='" + requestId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
