package com.ailearn.platform.core.transfer.domain;

import com.ailearn.platform.shared.exception.ValidationException;

/**
 * 调拨单状态机：草稿只能确认一次，确认后保留业务事实。
 */
public enum TransferStatus {
    /** 尚未执行库存移动的草稿。 */
    Draft,
    /** 已完成库存移动的调拨单。 */
    Confirmed;

    /**
     * 解析持久化状态并拒绝未冻结值。
     *
     * @param value 状态文本
     * @return 调拨状态
     */
    public static TransferStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("调拨状态不能为空");
        }
        for (TransferStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        throw new ValidationException("不支持的调拨状态: " + value);
    }
}
