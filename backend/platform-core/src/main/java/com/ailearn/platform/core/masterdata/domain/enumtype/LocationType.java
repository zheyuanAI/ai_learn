package com.ailearn.platform.core.masterdata.domain.enumtype;

import com.ailearn.platform.shared.exception.ValidationException;
import java.util.Arrays;

/**
 * 一期固定支持的六种标准库位类型。
 */
public enum LocationType {
    QualityHold,
    ReceivingStaging,
    Storage,
    Picking,
    ShippingStaging,
    Adjustment;

    /**
     * 严格解析库位类型，不接受大小写变体或未冻结的自定义值。
     *
     * @param value HTTP 请求中的库位类型
     * @return 对应的标准库位类型
     * @throws ValidationException 类型为空或不在六种固定值中
     */
    public static LocationType require(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("库位类型不能为空");
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equals(value.trim()))
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "库位类型不合法，仅支持 QualityHold、ReceivingStaging、Storage、Picking、ShippingStaging、Adjustment"));
    }
}
