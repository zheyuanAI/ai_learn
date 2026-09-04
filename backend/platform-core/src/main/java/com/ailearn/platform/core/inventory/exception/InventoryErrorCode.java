package com.ailearn.platform.core.inventory.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 库存内核稳定业务错误码。
 * <p>
 * 平台统一异常的数值 code 仍映射到 HTTP 状态；业务方通过 {@link #businessCode()} 识别 INV_* 码。
 * </p>
 */
public enum InventoryErrorCode implements ErrorCode {
    /** 可用库存不足或库位不可分配。 */
    INV_001("INV_001", 409, HttpStatus.CONFLICT, "可用库存不足"),
    /** 幂等键已被占用或命中历史结果。 */
    INV_002("INV_002", 409, HttpStatus.CONFLICT, "重复命令命中幂等记录"),
    /** 余额或预留版本冲突。 */
    INV_003("INV_003", 409, HttpStatus.CONFLICT, "余额版本冲突，请重试"),
    /** 库位不存在、停用或类型不合法。 */
    INV_004("INV_004", 422, HttpStatus.UNPROCESSABLE_ENTITY, "目标库位不可用或库位类型不合法"),
    /** 预留与来源业务明细不匹配。 */
    INV_005("INV_005", 422, HttpStatus.UNPROCESSABLE_ENTITY, "预留与来源业务明细不匹配");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    InventoryErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
        this.businessCode = businessCode;
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * 获取领域业务码。
     *
     * @return INV_* 文本码
     */
    public String businessCode() {
        return businessCode;
    }

    /**
     * 获取 HTTP 映射数值。
     *
     * @return HTTP 状态码
     */
    @Override
    public int getCode() {
        return code;
    }

    /**
     * 获取默认错误提示。
     *
     * @return 中文错误提示
     */
    @Override
    public String getMessage() {
        return message;
    }

    /**
     * 获取异常的 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
