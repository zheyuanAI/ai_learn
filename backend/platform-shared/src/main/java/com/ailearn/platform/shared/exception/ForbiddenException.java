package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.CommonErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 访问控制与鉴权权限不足异常。
 * <p>
 * 当已认证的用户尝试访问未授权的接口、角色不匹配或操作跨租户数据时抛出，统一映射为 HTTP 403 Forbidden。
 * </p>
 */
public class ForbiddenException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 使用默认无权限提示构造异常。
     */
    public ForbiddenException() {
        super(CommonErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN);
    }

    /**
     * 使用指定提示信息构造权限不足异常。
     *
     * @param message 权限不足提示信息
     */
    public ForbiddenException(String message) {
        super(CommonErrorCode.FORBIDDEN.getCode(), message, HttpStatus.FORBIDDEN);
    }
}
