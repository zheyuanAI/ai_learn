package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.core.inventory.domain.InventoryDimension;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 库存写命令统一视图，供应用服务校验可信审计字段。
 */
public interface InventoryCommand {

    /**
     * 获取统一命令元数据。
     *
     * @return 命令元数据
     */
    InventoryCommandMetadata metadata();

    /**
     * 获取命令主维度，用于公共租户和格式校验。
     *
     * @return 主库存维度
     */
    InventoryDimension primaryDimension();

    /**
     * 获取本次正数数量。
     *
     * @return 命令数量
     */
    BigDecimal quantity();

    /**
     * 便捷获取命令租户。
     *
     * @return 元数据租户 ID
     */
    default UUID tenantId() {
        return metadata().tenantId();
    }

    /**
     * 便捷获取命令操作用户。
     *
     * @return 元数据用户 ID
     */
    default UUID userId() {
        return metadata().userId();
    }
}
