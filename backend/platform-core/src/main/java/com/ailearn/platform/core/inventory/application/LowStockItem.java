package com.ailearn.platform.core.inventory.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 按“可用库存小于安全库存”口径生成的只读低库存投影。 */
public record LowStockItem(UUID productId, String sku, String productName, String uom,
                           BigDecimal safetyStock, BigDecimal availableQty,
                           BigDecimal shortfallQty, OffsetDateTime lastTransactionAt) { }
