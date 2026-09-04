package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 仅移动有效预留分配位置的命令。
 *
 * @param metadata 审计、来源、交易和幂等元数据
 * @param reservationId 预留 ID
 * @param allocationId 分配 ID
 * @param fromDimension 当前分配维度
 * @param toDimension 目标分配维度
 * @param quantity 移动的有效分配数量
 */
public record InventoryAllocationMoveCommand(
        InventoryCommandMetadata metadata,
        UUID reservationId,
        UUID allocationId,
        InventoryDimension fromDimension,
        InventoryDimension toDimension,
        BigDecimal quantity) implements InventoryCommand {

    /**
     * 返回公共校验使用的来源维度。
     *
     * @return 来源分配维度
     */
    @Override
    public InventoryDimension primaryDimension() {
        return fromDimension;
    }
}
