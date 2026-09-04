package com.ailearn.platform.core.masterdata.domain.model;

import java.math.BigDecimal;

/**
 * 库位停用前的库存使用快照。
 *
 * @param onHandQty   库位实物数量
 * @param reservedQty 库位有效预留数量
 */
public record LocationUsageSnapshot(BigDecimal onHandQty, BigDecimal reservedQty) {

    /**
     * 将空数量按零处理，供停用校验使用。
     *
     * @return 标准化后的快照
     */
    public LocationUsageSnapshot normalized() {
        return new LocationUsageSnapshot(
                onHandQty == null ? BigDecimal.ZERO : onHandQty,
                reservedQty == null ? BigDecimal.ZERO : reservedQty);
    }
}
