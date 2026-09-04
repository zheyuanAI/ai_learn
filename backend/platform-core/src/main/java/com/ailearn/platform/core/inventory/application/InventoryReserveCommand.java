package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import java.math.BigDecimal;

/**
 * 创建一笔业务库存预留及其首个库位分配的命令。
 *
 * @param metadata 审计、来源、交易和幂等元数据
 * @param dimension 预留所在库存维度
 * @param quantity 预留数量
 * @param reservationNo 可选业务编号，为空时由内核生成
 */
public record InventoryReserveCommand(
        InventoryCommandMetadata metadata,
        InventoryDimension dimension,
        BigDecimal quantity,
        String reservationNo) implements InventoryCommand {

    /**
     * 创建使用自动业务编号的预留命令。
     *
     * @param metadata 命令元数据
     * @param dimension 预留维度
     * @param quantity 预留数量
     */
    public InventoryReserveCommand(InventoryCommandMetadata metadata,
                                   InventoryDimension dimension,
                                   BigDecimal quantity) {
        this(metadata, dimension, quantity, null);
    }

    /**
     * 返回公共校验使用的主维度。
     *
     * @return 预留维度
     */
    @Override
    public InventoryDimension primaryDimension() {
        return dimension;
    }
}
