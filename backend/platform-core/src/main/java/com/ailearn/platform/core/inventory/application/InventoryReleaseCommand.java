package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 释放一笔有效库存预留命令。
 * <p>
 * 当前契约要求携带分配维度；allocationId 可选。未指定 allocationId 时，内核会在该维度下按 ID 稳定选择
 * 有效分配，允许上层对同一订单行多次分配分别释放。
 * </p>
 *
 * @param metadata 审计、来源、交易和幂等元数据
 * @param reservationId 预留 ID
 * @param dimension 预留分配所在库存维度
 * @param quantity 释放数量
 * @param allocationId 可选分配 ID
 */
public record InventoryReleaseCommand(
        InventoryCommandMetadata metadata,
        UUID reservationId,
        InventoryDimension dimension,
        BigDecimal quantity,
        UUID allocationId) implements InventoryCommand {

    /**
     * 创建按维度自动选择分配的释放命令。
     *
     * @param metadata 命令元数据
     * @param reservationId 预留 ID
     * @param dimension 分配维度
     * @param quantity 释放数量
     */
    public InventoryReleaseCommand(InventoryCommandMetadata metadata,
                                   UUID reservationId,
                                   InventoryDimension dimension,
                                   BigDecimal quantity) {
        this(metadata, reservationId, dimension, quantity, null);
    }

    /**
     * 返回公共校验使用的主维度。
     *
     * @return 释放维度
     */
    @Override
    public InventoryDimension primaryDimension() {
        return dimension;
    }
}
