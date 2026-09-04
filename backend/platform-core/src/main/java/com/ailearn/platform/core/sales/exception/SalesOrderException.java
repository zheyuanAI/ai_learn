package com.ailearn.platform.core.sales.exception;

import com.ailearn.platform.shared.exception.BaseException;

/**
 * 销售订单受控业务异常。
 */
public class SalesOrderException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用销售订单稳定错误码构造异常。
     *
     * @param errorCode 销售订单错误码
     * @param message 具体错误信息
     */
    public SalesOrderException(SalesOrderErrorCode errorCode, String message) {
        super(errorCode, message, errorCode.httpStatus());
    }
}
