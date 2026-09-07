package com.ailearn.platform.core.manufacturing.foundation.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** foundation 阶段的工单生产意图；不包含派工、执行或库存事实。 */
public record WorkOrderFact(UUID id, UUID tenantId, String workOrderNo, UUID productId,
                            BigDecimal plannedQty, OffsetDateTime plannedStartTime,
                            OffsetDateTime plannedFinishTime, UUID bomId, String bomVersion,
                            UUID routingId, String routingVersion, UUID sourceSalesOrderLineId,
                            WorkOrderStatus status, boolean deleted, UUID createdBy,
                            OffsetDateTime createdAt, long version) {

    /** 保留旧调用方构造器；新持久化读写使用显式版本。 */
    public WorkOrderFact(UUID id, UUID tenantId, String workOrderNo, UUID productId,
                         BigDecimal plannedQty, OffsetDateTime plannedStartTime,
                         OffsetDateTime plannedFinishTime, UUID bomId, String bomVersion,
                         UUID routingId, String routingVersion, UUID sourceSalesOrderLineId,
                         WorkOrderStatus status, boolean deleted, UUID createdBy,
                         OffsetDateTime createdAt) {
        this(id, tenantId, workOrderNo, productId, plannedQty, plannedStartTime, plannedFinishTime,
                bomId, bomVersion, routingId, routingVersion, sourceSalesOrderLineId, status,
                deleted, createdBy, createdAt, 0L);
    }

    public WorkOrderFact {
        Objects.requireNonNull(id, "workOrderId 不能为空");
        Objects.requireNonNull(tenantId, "tenantId 不能为空");
        requireText("workOrderNo", workOrderNo);
        Objects.requireNonNull(productId, "productId 不能为空");
        if (plannedQty == null || plannedQty.signum() <= 0) {
            throw new IllegalArgumentException("plannedQty 必须大于 0");
        }
        Objects.requireNonNull(plannedStartTime, "plannedStartTime 不能为空");
        Objects.requireNonNull(plannedFinishTime, "plannedFinishTime 不能为空");
        if (!plannedFinishTime.isAfter(plannedStartTime)) {
            throw new IllegalArgumentException("plannedFinishTime 必须晚于 plannedStartTime");
        }
        Objects.requireNonNull(bomId, "bomId 不能为空");
        requireText("bomVersion", bomVersion);
        Objects.requireNonNull(routingId, "routingId 不能为空");
        requireText("routingVersion", routingVersion);
        Objects.requireNonNull(status, "status 不能为空");
        Objects.requireNonNull(createdBy, "createdBy 不能为空");
        Objects.requireNonNull(createdAt, "createdAt 不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("version 不能为负数");
        }
    }

    /** 转换为采购等下游只读校验所需的最小来源事实。 */
    public WorkOrderSourceFact sourceFact() {
        return new WorkOrderSourceFact(id, tenantId, productId, workOrderNo, plannedQty,
                status, deleted);
    }

    /**
     * 返回只更新基础工单状态的快照，供生命周期双写时保持领域响应与主表状态一致。
     *
     * @param nextStatus 生命周期要写入的下一状态
     * @return 保留其余生产意图字段的新工单快照
     */
    public WorkOrderFact withStatus(WorkOrderStatus nextStatus) {
        Objects.requireNonNull(nextStatus, "nextStatus 不能为空");
        return new WorkOrderFact(id, tenantId, workOrderNo, productId, plannedQty,
                plannedStartTime, plannedFinishTime, bomId, bomVersion, routingId, routingVersion,
                sourceSalesOrderLineId, nextStatus, deleted, createdBy, createdAt, version);
    }

    /** 返回 Draft/Rejected 修改后的工单快照，并递增基础事实版本。 */
    public WorkOrderFact updated(String nextWorkOrderNo, UUID nextProductId, BigDecimal nextPlannedQty,
                                 OffsetDateTime nextPlannedStartTime, OffsetDateTime nextPlannedFinishTime,
                                 UUID nextBomId, String nextBomVersion, UUID nextRoutingId,
                                 String nextRoutingVersion, UUID nextSourceSalesOrderLineId) {
        return new WorkOrderFact(id, tenantId, nextWorkOrderNo, nextProductId, nextPlannedQty,
                nextPlannedStartTime, nextPlannedFinishTime, nextBomId, nextBomVersion, nextRoutingId,
                nextRoutingVersion, nextSourceSalesOrderLineId, status, deleted, createdBy, createdAt,
                version + 1);
    }

    /** 仅替换持久化版本，用于把数据库 CAS 成功后的版本带回应用响应。 */
    public WorkOrderFact withVersion(long nextVersion) {
        return new WorkOrderFact(id, tenantId, workOrderNo, productId, plannedQty,
                plannedStartTime, plannedFinishTime, bomId, bomVersion, routingId, routingVersion,
                sourceSalesOrderLineId, status, deleted, createdBy, createdAt, nextVersion);
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
