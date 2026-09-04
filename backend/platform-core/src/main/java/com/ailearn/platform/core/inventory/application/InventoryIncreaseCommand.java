package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import java.math.BigDecimal;

/**
 * 增加实物库存命令，典型来源为采购收货、生产退料或成品入库。
 *
 * @param metadata 审计、来源、交易和幂等元数据
 * @param dimension 增加到的库存维度
 * @param quantity 增加数量
 * @param expectedBalanceVersion 可选的调用方期望余额版本；非空时必须在行锁后仍然匹配
 */
public record InventoryIncreaseCommand(
        InventoryCommandMetadata metadata,
        InventoryDimension dimension,
        BigDecimal quantity,
        Long expectedBalanceVersion) implements InventoryCommand {

    /**
     * 构造不携带余额版本的普通增加命令，兼容采购、生产等常规入库调用方。
     *
     * @param metadata 审计、来源、交易和幂等元数据
     * @param dimension 增加到的库存维度
     * @param quantity 增加数量
     */
    public InventoryIncreaseCommand(InventoryCommandMetadata metadata,
                                    InventoryDimension dimension,
                                    BigDecimal quantity) {
        this(metadata, dimension, quantity, null);
    }

    /**
     * 返回公共校验使用的主维度。
     *
     * @return 增加目标维度
     */
    @Override
    public InventoryDimension primaryDimension() {
        return dimension;
    }
}
