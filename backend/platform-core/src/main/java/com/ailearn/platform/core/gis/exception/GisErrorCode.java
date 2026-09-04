package com.ailearn.platform.core.gis.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/** GIS、看板和追溯查询共用的稳定业务错误码。 */
public enum GisErrorCode implements ErrorCode {
    GIS_AUTH_001("GIS_AUTH_001", 403, HttpStatus.FORBIDDEN, "当前用户无地图或看板查询权限"),
    GIS_TENANT_001("GIS_TENANT_001", 404, HttpStatus.NOT_FOUND, "筛选实体不属于当前租户"),
    GIS_QUERY_001("GIS_QUERY_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "统计时间范围不支持"),
    GIS_QUERY_002("GIS_QUERY_002", 503, HttpStatus.SERVICE_UNAVAILABLE, "源领域查询暂时不可用"),
    GIS_POINT_001("GIS_POINT_001", 404, HttpStatus.NOT_FOUND, "点位不存在或当前用户不可见"),
    GIS_POINT_002("GIS_POINT_002", 409, HttpStatus.CONFLICT, "点位幂等键载荷不一致"),
    GIS_CONFIG_001("GIS_CONFIG_001", 422, HttpStatus.UNPROCESSABLE_ENTITY, "地图或底图配置不合法");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    GisErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
        this.businessCode = businessCode;
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

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

    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
