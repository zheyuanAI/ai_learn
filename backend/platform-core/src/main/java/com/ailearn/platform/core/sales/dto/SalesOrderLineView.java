package com.ailearn.platform.core.sales.dto;

import com.ailearn.platform.core.masterdata.domain.entity.Product;
import com.ailearn.platform.core.sales.domain.SalesOrderLine;
import java.util.UUID;

/**
 * 销售订单明细响应，累计数量和派生数量统一以字符串返回。
 */
public record SalesOrderLineView(UUID id, int lineNo, UUID productId, String uom,
                                 String sku, String productName,
                                 String orderedQty, String reservedQty, String pickedQty,
                                 String shippedQty, String unreservedQty, String unpickedQty,
                                 String shippingStagedQty, String activeReservedQty,
                                 String unshippedQty) {

    /**
     * 将领域明细转换为 HTTP 明细。
     *
     * @param line 领域明细
     * @return 数量口径完整的明细响应
     */
    public static SalesOrderLineView from(SalesOrderLine line) {
        return from(line, null);
    }

    /**
     * 将领域明细和物料主数据转换为 HTTP 明细，避免页面只能看到产品 UUID。
     *
     * @param line 领域明细
     * @param product 当前租户内的物料主数据，可为空（兼容旧调用方）
     * @return 包含物料编码、名称和数量事实的明细响应
     */
    public static SalesOrderLineView from(SalesOrderLine line, Product product) {
        return new SalesOrderLineView(line.id(), line.lineNo(), line.productId(), line.uom(),
                product == null ? null : product.getSku(),
                product == null ? null : product.getName(),
                text(line.orderedQty()), text(line.reservedQty()), text(line.pickedQty()), text(line.shippedQty()),
                text(line.unreservedQty()), text(line.unpickedQty()), text(line.shippingStagedQty()),
                text(line.activeReservedQty()), text(line.unshippedQty()));
    }

    private static String text(java.math.BigDecimal value) {
        return value.setScale(6).toPlainString();
    }
}
