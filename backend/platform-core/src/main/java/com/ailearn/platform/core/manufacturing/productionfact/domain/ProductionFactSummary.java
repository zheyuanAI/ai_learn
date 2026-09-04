package com.ailearn.platform.core.manufacturing.productionfact.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Task16 命令结果，返回事实和库存流水关联，避免调用方自行推导库存结果。 */
public record ProductionFactSummary(String operation, UUID factId, Object fact,
                                    BigDecimal quantity, List<UUID> inventoryTransactionIds) {

    public ProductionFactSummary {
        if (operation == null || operation.isBlank() || factId == null || fact == null) {
            throw new IllegalArgumentException("生产事实结果不能为空");
        }
        inventoryTransactionIds = inventoryTransactionIds == null
                ? List.of() : List.copyOf(inventoryTransactionIds);
    }
}
