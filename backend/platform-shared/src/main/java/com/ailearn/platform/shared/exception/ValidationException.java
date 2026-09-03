package com.ailearn.platform.shared.exception;

import com.ailearn.platform.shared.api.CommonErrorCode;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 请求参数与数据校验异常。
 * <p>
 * 当请求入参不符合业务格式或 Bean Validation 规则时抛出，支持携带字段级错误列表，统一映射为 HTTP 422 Unprocessable Entity。
 * </p>
 */
public class ValidationException extends BaseException {

    private static final long serialVersionUID = 1L;

    /**
     * 字段级错误信息映射表 (fieldName -> errorMessage)
     */
    private final Map<String, String> fieldErrors;

    /**
     * 使用指定错误提示构造校验异常。
     *
     * @param message 校验失败描述
     */
    public ValidationException(String message) {
        super(CommonErrorCode.VALIDATION_ERROR.getCode(), message, HttpStatus.UNPROCESSABLE_ENTITY);
        this.fieldErrors = null;
    }

    /**
     * 携带字段明细的校验异常构造函数。
     *
     * @param message     错误汇总描述
     * @param fieldErrors 字段名与具体错误信息的映射表
     */
    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(CommonErrorCode.VALIDATION_ERROR.getCode(), message, HttpStatus.UNPROCESSABLE_ENTITY, fieldErrors, null);
        this.fieldErrors = fieldErrors;
    }

    /**
     * 获取字段级校验错误详情。
     *
     * @return 字段名与错误信息映射表
     */
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
