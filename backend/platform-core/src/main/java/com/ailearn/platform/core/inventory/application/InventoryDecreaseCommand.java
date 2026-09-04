package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import java.math.BigDecimal;

/**
 * 减少实物库存命令，典型来源为发货、报废或生产领料。
 *
 * @param metadata 审计、来源、交易和幂等元数据
 * @param dimension 减少来源库存维度
 * @param quantity 减少数量
 * @param expectedBalanceVersion 可选的调用方期望余额版本；非空时必须在行锁后仍然匹配
 */
public record InventoryDecreaseCommand(
        InventoryCommandMetadata metadata,
        InventoryDimension dimension,
        BigDecimal quantity,
        Long expectedBalanceVersion) implements InventoryCommand {

    /**
     * 构造不携带余额版本的普通减少命令，兼容销售、制造等常规出库调用方。
     *
     * @param metadata 审计、来源、交易和幂等元数据
     * @param dimension 减少来源库存维度
     * @param quantity 减少数量
     */
    public InventoryDecreaseCommand(InventoryCommandMetadata metadata,
                                    InventoryDimension dimension,
                                    BigDecimal quantity) {
        this(metadata, dimension, quantity, null);
    }

    /**
     * 返回公共校验使用的主维度。
     *
     * @return 减少来源维度
     */
    @Override
    public InventoryDimension primaryDimension() {
        return dimension;
    }
}
