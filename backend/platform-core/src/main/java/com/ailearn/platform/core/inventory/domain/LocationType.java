package com.ailearn.platform.core.inventory.domain;

import com.ailearn.platform.shared.exception.ValidationException;

/**
 * 一期库存库位类型白名单。
 * <p>
 * 枚举值与主数据表 {@code md_location.type} 及领域规格保持一致，
 * 库存内核不接受未定义的库位类型。
 * </p>
 */
public enum LocationType {
    /** 收货暂存位。 */
    ReceivingStaging,
    /** 普通存储位。 */
    Storage,
    /** 拣货位。 */
    Picking,
    /** 发货暂存位。 */
    ShippingStaging,
    /** 质量隔离位。 */
    QualityHold,
    /** 受控调整位。 */
    Adjustment;

    /**
     * 把数据库或主数据传入的库位类型转换为白名单枚举。
     *
     * @param value 库位类型文本
     * @return 对应的库位类型
     * @throws ValidationException 类型为空或不在一期白名单中
     */
    public static LocationType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("库位类型不能为空");
        }
        try {
            return value.trim().equals(value)
                    ? valueOf(value)
                    : valueOf(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ValidationException("不支持的库位类型: " + value);
        }
    }
}
