package com.ailearn.platform.core.purchasing.exception;

import com.ailearn.platform.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 采购领域稳定业务错误码。
 * <p>
 * Task 10 只使用采购单状态、数量关系、来源和拒收原因相关错误；其余码为同一 V3 质量/上架契约预留。
 * </p>
 */
public enum PurchasingErrorCode implements ErrorCode {
    /** 采购单状态或版本不允许当前操作。 */
    PO_001("PO_001", 409, HttpStatus.CONFLICT, "采购单状态不允许当前操作"),
    /** 收货、质量处置或上架累计数量超过允许上限。 */
    PO_002("PO_002", 409, HttpStatus.CONFLICT, "采购累计数量超过允许上限"),
    /** 质量隔离货物尚未放行，禁止上架。 */
    PO_003("PO_003", 409, HttpStatus.CONFLICT, "质量隔离货物尚未放行"),
    /** 到货/拒收/接收数量关系或来源引用不成立。 */
    PO_004("PO_004", 422, HttpStatus.UNPROCESSABLE_ENTITY, "采购数量关系或来源引用不成立"),
    /** 收货前拒收数量缺少原因。 */
    PO_005("PO_005", 422, HttpStatus.UNPROCESSABLE_ENTITY, "收货前拒收数量必须填写原因"),
    /** 质量处置角色或执行状态不匹配。 */
    PO_006("PO_006", 403, HttpStatus.FORBIDDEN, "质量处置角色或执行状态不匹配");

    private final String businessCode;
    private final int code;
    private final HttpStatus httpStatus;
    private final String message;

    PurchasingErrorCode(String businessCode, int code, HttpStatus httpStatus, String message) {
        this.businessCode = businessCode;
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * 获取采购业务码。
     *
     * @return PO_* 文本码
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
     * 获取 HTTP 状态映射。
     *
     * @return HTTP 状态
     */
    public HttpStatus httpStatus() {
        return httpStatus;
    }
}
