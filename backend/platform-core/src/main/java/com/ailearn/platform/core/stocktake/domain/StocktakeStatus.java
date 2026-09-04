package com.ailearn.platform.core.stocktake.domain;

import com.ailearn.platform.shared.exception.ValidationException;
import java.util.Arrays;

/**
 * 盘点单固定生命周期。
 */
public enum StocktakeStatus {
    NotStarted,
    Counting,
    ConfirmedAdjusted;

    /**
     * 严格解析数据库状态。
     *
     * @param value 数据库状态
     * @return 盘点状态
     */
    public static StocktakeStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException("盘点状态不能为空");
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equals(value.trim()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("盘点状态不合法"));
    }
}
