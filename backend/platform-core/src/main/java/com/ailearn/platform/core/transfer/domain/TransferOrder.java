package com.ailearn.platform.core.transfer.domain;

import com.ailearn.platform.shared.exception.ValidationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 调拨单聚合，确认动作通过库存应用端口完成双边移动。
 *
 * @param id 调拨单 ID
 * @param tenantId 所属租户
 * @param transferNo 租户内业务编号
 * @param fromWarehouseId 来源仓库
 * @param fromLocationId 来源库位
 * @param toWarehouseId 目标仓库
 * @param toLocationId 目标库位
 * @param status 调拨状态
 * @param version 乐观版本
 * @param confirmedBy 确认用户
 * @param confirmedAt 确认时间
 * @param createdBy 创建用户
 * @param createdAt 创建时间
 * @param updatedBy 最近更新用户
 * @param updatedAt 最近更新时间
 * @param lines 调拨明细
 */
public record TransferOrder(UUID id, UUID tenantId, String transferNo,
                            UUID fromWarehouseId, UUID fromLocationId,
                            UUID toWarehouseId, UUID toLocationId,
                            TransferStatus status, long version,
                            UUID confirmedBy, OffsetDateTime confirmedAt,
                            UUID createdBy, OffsetDateTime createdAt,
                            UUID updatedBy, OffsetDateTime updatedAt,
                            List<TransferLine> lines) {

    /**
     * 校验调拨聚合的来源/目标和租户边界，并冻结明细集合。
     */
    public TransferOrder {
        if (id == null || tenantId == null || fromWarehouseId == null || fromLocationId == null
                || toWarehouseId == null || toLocationId == null || status == null || createdBy == null) {
            throw new ValidationException("调拨单必要字段不能为空");
        }
        if (fromLocationId.equals(toLocationId)) {
            throw new ValidationException("调拨来源和目标库位不能相同");
        }
        if (transferNo == null || transferNo.isBlank() || transferNo.trim().length() > 64) {
            throw new ValidationException("调拨单号不能为空且不能超过 64 个字符");
        }
        if (version < 0 || lines == null || lines.isEmpty()) {
            throw new ValidationException("调拨版本不能为负且至少需要一条明细");
        }
        lines = List.copyOf(lines);
        if (lines.stream().anyMatch(line -> !tenantId.equals(line.tenantId()))) {
            throw new ValidationException("调拨明细不能跨租户");
        }
    }

    /**
     * 根据确认事实构造新版本聚合。
     *
     * @param operatorId 可信确认用户
     * @param confirmedAt 确认时间
     * @return 已确认的新聚合
     */
    public TransferOrder confirmed(UUID operatorId, OffsetDateTime confirmedAt) {
        return new TransferOrder(id, tenantId, transferNo, fromWarehouseId, fromLocationId,
                toWarehouseId, toLocationId, TransferStatus.Confirmed, version + 1,
                operatorId, confirmedAt, createdBy, createdAt, operatorId, confirmedAt, lines);
    }
}
