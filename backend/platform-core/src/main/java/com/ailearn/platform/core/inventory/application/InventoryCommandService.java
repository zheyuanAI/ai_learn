package com.ailearn.platform.core.inventory.application;

/**
 * 库存唯一写应用端口。
 * <p>
 * 采购、销售、制造、调拨和盘点只能依赖本端口，不得直接注入库存 Mapper 或 Repository。
 * 每个命令在实现层内以事务、租户校验、幂等控制和余额行锁保护库存事实。
 * </p>
 */
public interface InventoryCommandService {

    /**
     * 在库存命令事务内锁定并校验余额版本；盘点零差异确认也必须经过该入口，避免绕过余额行锁。
     * focused mock 未实现时可返回 null，由调用方回退到查询端口；生产 InventoryApplicationService 返回已锁定余额。
     *
     * @param dimension 待校验库存维度
     * @param expectedVersion 快照记录的余额版本
     * @return 已锁定余额，兼容适配器可返回 null
     */
    default com.ailearn.platform.core.inventory.domain.InventoryBalance assertBalanceVersion(
            com.ailearn.platform.core.inventory.domain.InventoryDimension dimension, long expectedVersion) {
        return null;
    }

    /**
     * 增加一个库存维度的实物数量并追加增加流水。
     *
     * @param command 增加命令，包含来源、维度、数量和审计幂等元数据
     * @return 更新后的余额和追加流水
     */
    InventoryMutationResult increase(InventoryIncreaseCommand command);

    /**
     * 减少一个库存维度的实物数量并追加减少流水。
     *
     * @param command 减少命令，包含来源、维度、数量和审计幂等元数据
     * @return 更新后的余额和追加流水
     */
    InventoryMutationResult decrease(InventoryDecreaseCommand command);

    /**
     * 在两个库存维度间移动实物；可选地与同量预留分配耦合移动。
     *
     * @param command 移动命令，包含来源/目标维度、数量和审计幂等元数据
     * @return 两侧更新后的余额和追加流水
     */
    InventoryMutationResult move(InventoryMoveCommand command);

    /**
     * 创建业务预留并在指定库存维度生成首个分配。
     *
     * @param command 预留命令，包含来源明细、维度、数量和审计幂等元数据
     * @return 更新余额、预留、分配和流水
     */
    InventoryMutationResult reserve(InventoryReserveCommand command);

    /**
     * 释放一个预留在指定分配维度上的有效数量。
     *
     * @param command 释放命令，包含预留、分配维度、数量和审计幂等元数据
     * @return 更新余额、预留、分配和流水
     */
    InventoryMutationResult release(InventoryReleaseCommand command);

    /**
     * 只移动有效预留分配，不改变企业实物总量。
     *
     * @param command 分配移动命令，包含预留、分配、来源/目标维度和数量
     * @return 两侧更新后的余额、分配和流水
     */
    InventoryMutationResult moveReservationAllocation(InventoryAllocationMoveCommand command);
}
