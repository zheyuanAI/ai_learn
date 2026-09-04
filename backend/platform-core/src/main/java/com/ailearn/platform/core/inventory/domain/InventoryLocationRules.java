package com.ailearn.platform.core.inventory.domain;

import com.ailearn.platform.core.inventory.application.InventoryCommandMetadata;
import com.ailearn.platform.core.inventory.exception.InventoryErrorCode;
import com.ailearn.platform.core.inventory.exception.InventoryException;

/**
 * 一期库位动作白名单。
 * <p>
 * 这里仅表达库存内核能确认的通用边界，采购质量处置、销售履约等领域的业务前置条件
 * 仍由各自应用服务负责。
 * </p>
 */
public final class InventoryLocationRules {

    private InventoryLocationRules() {
    }

    /**
     * 校验库位已属于当前租户且可用。
     *
     * @param snapshot 库位快照
     * @param expectedLocationId 命令中的库位 ID
     * @param expectedTenantId 命令可信租户 ID
     */
    public static void requireActive(LocationSnapshot snapshot,
                                     java.util.UUID expectedLocationId,
                                     java.util.UUID expectedTenantId) {
        if (snapshot == null
                || !expectedLocationId.equals(snapshot.id())
                || !expectedTenantId.equals(snapshot.tenantId())
                || !snapshot.isActive()) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "库位不存在、跨租户或未启用");
        }
    }

    /**
     * 校验增加实物库存的目标库位类型。
     *
     * @param snapshot 目标库位
     * @param metadata 命令元数据
     */
    public static void requireIncreaseTarget(LocationSnapshot snapshot, InventoryCommandMetadata metadata) {
        if (snapshot.type() == LocationType.Adjustment
                && !"ADJUSTMENT".equalsIgnoreCase(metadata.transactionType())) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "调整位只允许受控调整交易增加库存");
        }
    }

    /**
     * 校验减少实物库存的来源库位类型。
     *
     * @param snapshot 来源库位
     * @param metadata 命令元数据
     */
    public static void requireDecreaseSource(LocationSnapshot snapshot, InventoryCommandMetadata metadata) {
        if (snapshot.type() == LocationType.Adjustment
                && !"ADJUSTMENT".equalsIgnoreCase(metadata.transactionType())) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "调整位只允许受控调整交易减少库存");
        }
    }

    /**
     * 校验普通实物位置移动的来源和目标类型。
     *
     * @param from 来源库位
     * @param to 目标库位
     */
    public static void requireMove(LocationSnapshot from, LocationSnapshot to) {
        if (from.type() == LocationType.Adjustment || to.type() == LocationType.Adjustment) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "调整位不能通过普通位置移动绕过受控调整");
        }
        if (to.type() == LocationType.QualityHold) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "普通位置移动不能进入质量隔离位");
        }
        if (from.type() == LocationType.QualityHold && to.type() != LocationType.ReceivingStaging) {
            throw new InventoryException(InventoryErrorCode.INV_004,
                    "质量隔离位只能放行移动到收货暂存位");
        }
    }

    /**
     * 校验库位允许产生预留。
     *
     * @param snapshot 待预留库位
     */
    public static void requireAllocatable(LocationSnapshot snapshot) {
        if (snapshot.type() == LocationType.QualityHold
                || snapshot.type() == LocationType.ReceivingStaging
                || snapshot.type() == LocationType.Adjustment) {
            throw new InventoryException(InventoryErrorCode.INV_001,
                    "当前库位的库存不可参与正常预留");
        }
    }
}
