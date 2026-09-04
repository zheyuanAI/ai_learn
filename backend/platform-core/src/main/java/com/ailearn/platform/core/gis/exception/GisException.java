package com.ailearn.platform.core.gis.exception;

import com.ailearn.platform.shared.exception.BaseException;

/** GIS/看板查询应用层异常，向上保留 GIS_* 稳定业务码。 */
public class GisException extends BaseException {

    private static final long serialVersionUID = 1L;
    private final String businessCode;

    public GisException(GisErrorCode errorCode, String detail) {
        super(errorCode.getCode(), errorCode.businessCode() + " "
                + (detail == null || detail.isBlank() ? errorCode.getMessage() : detail),
                errorCode.httpStatus());
        this.businessCode = errorCode.businessCode();
    }

    public String getBusinessCode() {
        return businessCode;
    }
}
