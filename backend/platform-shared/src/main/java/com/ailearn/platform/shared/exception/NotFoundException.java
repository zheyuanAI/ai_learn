package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.CommonErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 业务资源未找到异常。
 * <p>
 * 当查询目标实体（如订单、商品、用户、设备等）不存在或已被逻辑删除时抛出，统一映射为 HTTP 404 Not Found。
 * </p>
 */
public class NotFoundException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用默认资源未找到提示构造异常。
     */
    public NotFoundException() {
        super(CommonErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    /**
     * 使用指定提示信息构造资源未找到异常。
     *
     * @param message 错误提示信息（例如 "指定的采购订单不存在"）
     */
    public NotFoundException(String message) {
        super(CommonErrorCode.NOT_FOUND.getCode(), message, HttpStatus.NOT_FOUND);
    }

    /**
     * 便捷构建资源及标识信息的未找到异常。
     *
     * @param resourceName 资源名称（例如 "采购订单"）
     * @param identifier   资源唯一标识
     */
    public NotFoundException(String resourceName, Object identifier) {
        super(CommonErrorCode.NOT_FOUND.getCode(), String.format("%s不存在: %s", resourceName, identifier), HttpStatus.NOT_FOUND);
    }
}
