package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 实物位置移动命令。
 * <p>
 * 普通移动只改变实物位置；当同时填写 reservationId/allocationId 时，应用服务会把实物和等量有效预留
 * 在同一余额更新中移动，避免 PostgreSQL 余额约束在销售拣货的中间步骤被破坏。该可选耦合字段不改变
 * {@link InventoryAllocationMoveCommand} 对纯预留分配移动的独立能力。
 * </p>
 *
 * @param metadata 审计、来源、交易和幂等元数据
 * @param fromDimension 来源维度
 * @param toDimension 目标维度
 * @param quantity 移动数量
 * @param reservationId 可选的耦合预留 ID
 * @param allocationId 可选的耦合分配 ID
 */
public record InventoryMoveCommand(
        InventoryCommandMetadata metadata,
        InventoryDimension fromDimension,
        InventoryDimension toDimension,
        BigDecimal quantity,
        UUID reservationId,
        UUID allocationId) implements InventoryCommand {

    /**
     * 创建不迁移预留的普通实物移动命令。
     *
     * @param metadata 命令元数据
     * @param fromDimension 来源维度
     * @param toDimension 目标维度
     * @param quantity 移动数量
     */
    public InventoryMoveCommand(InventoryCommandMetadata metadata,
                                InventoryDimension fromDimension,
                                InventoryDimension toDimension,
                                BigDecimal quantity) {
        this(metadata, fromDimension, toDimension, quantity, null, null);
    }

    /**
     * 返回公共校验使用的来源维度。
     *
     * @return 来源维度
     */
    @Override
    public InventoryDimension primaryDimension() {
        return fromDimension;
    }
}
