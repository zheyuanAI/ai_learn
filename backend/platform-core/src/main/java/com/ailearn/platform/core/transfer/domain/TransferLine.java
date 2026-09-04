package com.ailearn.platform.core.transfer.domain;

import com.ailearn.platform.core.inventory.domain.InventoryInvariant;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 调拨单明细事实。
 *
 * @param id 明细 ID
 * @param tenantId 所属租户
 * @param lineNo 行号
 * @param productId 产品 ID
 * @param lotNo 批次号
 * @param uom 计量单位
 * @param quantity 调拨数量
 */
public record TransferLine(UUID id, UUID tenantId, int lineNo, UUID productId,
                           String lotNo, String uom, BigDecimal quantity) {

    /**
     * 校验调拨明细的租户、行号、产品、单位和数量精度。
     */
    public TransferLine {
        if (id == null || tenantId == null || productId == null) {
            throw new com.ailearn.platform.shared.exception.ValidationException("调拨明细主键、租户和产品不能为空");
        }
        if (lineNo < 1) {
            throw new com.ailearn.platform.shared.exception.ValidationException("调拨明细行号必须大于 0");
        }
        if (uom == null || uom.isBlank() || uom.trim().length() > 64) {
            throw new com.ailearn.platform.shared.exception.ValidationException("调拨明细计量单位不能为空且不能超过 64 个字符");
        }
        uom = uom.trim();
        lotNo = lotNo == null || lotNo.isBlank() ? "" : lotNo.trim();
        if (lotNo.length() > 128) {
            throw new com.ailearn.platform.shared.exception.ValidationException("调拨批次号不能超过 128 个字符");
        }
        quantity = InventoryInvariant.requirePositive("quantity", quantity);
    }
}
