package com.ailearn.platform.core.stocktake.domain;

import com.ailearn.platform.shared.exception.ValidationException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * 盘点单聚合，系统快照和调整事实均由应用服务通过库存端口协调。
 *
 * @param id 盘点单 ID
 * @param tenantId 所属租户
 * @param stocktakeNo 租户内盘点编号
 * @param warehouseId 盘点仓库
 * @param locationId 可选的盘点库位范围
 * @param status 盘点状态
 * @param version 乐观版本
 * @param startedBy 开始盘点用户
 * @param startedAt 开始盘点时间
 * @param confirmedBy 确认用户
 * @param confirmedAt 确认时间
 * @param createdBy 创建用户
 * @param createdAt 创建时间
 * @param updatedBy 最近更新用户
 * @param updatedAt 最近更新时间
 * @param lines 盘点快照明细
 */
public record StocktakeOrder(UUID id, UUID tenantId, String stocktakeNo, UUID warehouseId,
                             UUID locationId, StocktakeStatus status, long version,
                             UUID startedBy, OffsetDateTime startedAt,
                             UUID confirmedBy, OffsetDateTime confirmedAt,
                             UUID createdBy, OffsetDateTime createdAt,
                             UUID updatedBy, OffsetDateTime updatedAt,
                             List<StocktakeLine> lines) {

    /**
     * 校验聚合边界并冻结明细集合。
     */
    public StocktakeOrder {
        if (id == null || tenantId == null || warehouseId == null || status == null || createdBy == null) {
            throw new ValidationException("盘点单必要字段不能为空");
        }
        if (stocktakeNo == null || stocktakeNo.isBlank() || stocktakeNo.trim().length() > 64) {
            throw new ValidationException("盘点单号不能为空且不能超过 64 个字符");
        }
        if (version < 0 || lines == null) {
            throw new ValidationException("盘点单版本不能为负且明细不能为 null");
        }
        lines = List.copyOf(lines);
        if (lines.stream().anyMatch(line -> !tenantId.equals(line.tenantId()))) {
            throw new ValidationException("盘点明细租户必须与盘点单一致");
        }
        if (lines.size() != new HashSet<>(lines.stream().map(StocktakeLine::lineNo).toList()).size()) {
            throw new ValidationException("盘点明细行号不能重复");
        }
    }

    /**
     * 将未盘点单推进到盘点中并写入系统快照。
     *
     * @param operatorId 开始盘点用户
     * @param time 开始时间
     * @param snapshotLines 系统余额快照明细
     * @return 盘点中聚合
     */
    public StocktakeOrder started(UUID operatorId, OffsetDateTime time, List<StocktakeLine> snapshotLines) {
        return new StocktakeOrder(id, tenantId, stocktakeNo, warehouseId, locationId, StocktakeStatus.Counting,
                version + 1, operatorId, time, confirmedBy, confirmedAt, createdBy, createdAt,
                operatorId, time, snapshotLines);
    }

    /**
     * 将盘点中聚合推进到已确认并调整。
     *
     * @param operatorId 确认用户
     * @param time 确认时间
     * @param confirmedLines 已录入实盘和调整流水的明细
     * @return 已确认聚合
     */
    public StocktakeOrder confirmed(UUID operatorId, OffsetDateTime time,
                                    List<StocktakeLine> confirmedLines) {
        return new StocktakeOrder(id, tenantId, stocktakeNo, warehouseId, locationId,
                StocktakeStatus.ConfirmedAdjusted, version + 1, startedBy, startedAt,
                operatorId, time, createdBy, createdAt, operatorId, time, confirmedLines);
    }
}
