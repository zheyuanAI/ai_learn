package com.ailearn.platform.core.sales.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 销售订单基础能力的稳定错误码。
 */
public enum SalesOrderErrorCode implements ErrorCode {
    /** 生命周期状态不允许当前操作。 */
    SO_001("SO_001", 409, HttpStatus.CONFLICT, "销售单状态不允许当前操作"),
    /** 订单行数量不满足正数或累计链约束。 */
    SO_002("SO_002", 422, HttpStatus.UNPROCESSABLE_ENTITY, "销售订单行数量超过当前可操作上限"),
    /** 订单存在尚未处理的发货暂存数量。 */
    SO_003("SO_003", 409, HttpStatus.CONFLICT, "存在未发货暂存数量，不能完成人工完成"),
    /** 关联主数据不存在、停用或不属于当前租户。 */
    SO_004("SO_004", 422, HttpStatus.UNPROCESSABLE_ENTITY, "客户或产品不可用"),
    /** 销售订单数据版本发生并发变化。 */
    SO_005("SO_005", 409, HttpStatus.CONFLICT, "销售订单版本已变化，请重新读取"),
    /** 销售订单请求结构不合法。 */
    SO_006("SO_006", 422, HttpStatus.UNPROCESSABLE_ENTITY, "销售订单请求校验不通过");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    SalesOrderErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
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
