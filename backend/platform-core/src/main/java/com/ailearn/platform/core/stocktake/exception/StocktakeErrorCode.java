package com.ailearn.platform.core.stocktake.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 盘点领域稳定错误码。
 */
public enum StocktakeErrorCode implements ErrorCode {
    /** 盘点单状态或乐观版本不允许当前操作。 */
    ST_001("ST_001", 409, HttpStatus.CONFLICT, "盘点单状态不允许当前操作"),
    /** 盘点差异缺少原因或调整流水未生成。 */
    ST_002("ST_002", 422, HttpStatus.UNPROCESSABLE_ENTITY, "盘点差异缺少原因或调整流水生成失败");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    StocktakeErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
        this.businessCode = businessCode;
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * 获取 ST_* 业务码。
     *
     * @return 业务码
     */
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

    /**
     * 获取错误对应 HTTP 状态。
     *
     * @return HTTP 状态
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
