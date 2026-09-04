package com.ailearn.platform.core.manufacturing.dispatch.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 派工安排聚合，只保存工单/工序与可选设备的安排信息，不复制 IoT 遥测或工序执行事实。
 */
public record DispatchOrder(UUID id, UUID tenantId, UUID workOrderId, UUID operationId,
                            UUID operatorId, BigDecimal dispatchQty, UUID deviceId,
                            DispatchStatus status, UUID createdBy,
                            OffsetDateTime createdAt, UUID releasedBy, OffsetDateTime releasedAt,
                            UUID processingBy, OffsetDateTime processingAt, UUID completedBy,
                            OffsetDateTime completedAt, long version) {

    public DispatchOrder {
        if (id == null || tenantId == null || workOrderId == null || operationId == null
                || operatorId == null || dispatchQty == null || dispatchQty.signum() <= 0
                || status == null || createdBy == null || createdAt == null || version < 0) {
            throw new IllegalArgumentException("派工单基础字段不合法");
        }
    }

    /**
     * 兼容旧的内部构造调用；旧数据以创建人作为派工人、数量按 1 处理。
     *
     * @param id 派工标识
     * @param tenantId 租户标识
     * @param workOrderId 工单标识
     * @param operationId 工序标识
     * @param deviceId 可选设备标识
     * @param status 派工状态
     * @param createdBy 创建人
     * @param createdAt 创建时间
     * @param releasedBy 发布人
     * @param releasedAt 发布时间
     * @param processingBy 处理人
     * @param processingAt 处理时间
     * @param completedBy 完成人
     * @param completedAt 完成时间
     * @param version 乐观锁版本
     */
    public DispatchOrder(UUID id, UUID tenantId, UUID workOrderId, UUID operationId,
                         UUID deviceId, DispatchStatus status, UUID createdBy,
                         OffsetDateTime createdAt, UUID releasedBy, OffsetDateTime releasedAt,
                         UUID processingBy, OffsetDateTime processingAt, UUID completedBy,
                         OffsetDateTime completedAt, long version) {
        this(id, tenantId, workOrderId, operationId, createdBy, BigDecimal.ONE, deviceId, status,
                createdBy, createdAt, releasedBy, releasedAt, processingBy, processingAt,
                completedBy, completedAt, version);
    }

    /** 创建草稿派工安排。 */
    public static DispatchOrder draft(UUID id, UUID tenantId, UUID workOrderId, UUID operationId,
                                      UUID deviceId, UUID userId, OffsetDateTime now) {
        return draft(id, tenantId, workOrderId, operationId, userId, BigDecimal.ONE, deviceId, userId, now);
    }

    /** 创建带派工人和数量的草稿安排；数量由应用服务校验后进入事实。 */
    public static DispatchOrder draft(UUID id, UUID tenantId, UUID workOrderId, UUID operationId,
                                      UUID operatorId, BigDecimal dispatchQty, UUID deviceId,
                                      UUID createdBy, OffsetDateTime now) {
        return new DispatchOrder(id, tenantId, workOrderId, operationId, operatorId, dispatchQty, deviceId,
                DispatchStatus.Draft, createdBy, now, null, null, null, null, null, null, 0);
    }

    /** 发布派工安排，供后续工序执行引用。 */
    public DispatchOrder released(UUID userId, OffsetDateTime now) {
        require(DispatchStatus.Draft, "只有 Draft 派工单可以发布");
        return new DispatchOrder(id, tenantId, workOrderId, operationId, operatorId, dispatchQty, deviceId,
                DispatchStatus.Released, createdBy, createdAt, userId, now,
                processingBy, processingAt, completedBy, completedAt, version + 1);
    }

    /** 标记派工安排进入执行中；不代表工序执行事实已开始。 */
    public DispatchOrder processing(UUID userId, OffsetDateTime now) {
        require(DispatchStatus.Released, "只有 Released 派工单可以进入 Processing");
        return new DispatchOrder(id, tenantId, workOrderId, operationId, operatorId, dispatchQty, deviceId,
                DispatchStatus.Processing, createdBy, createdAt, releasedBy, releasedAt,
                userId, now, completedBy, completedAt, version + 1);
    }

    /** 标记派工安排完成；具体执行时间线由 OperationExecution 保存。 */
    public DispatchOrder completed(UUID userId, OffsetDateTime now) {
        require(DispatchStatus.Processing, "只有 Processing 派工单可以完成");
        return new DispatchOrder(id, tenantId, workOrderId, operationId, operatorId, dispatchQty, deviceId,
                DispatchStatus.Completed, createdBy, createdAt, releasedBy, releasedAt,
                processingBy, processingAt, userId, now, version + 1);
    }

    private void require(DispatchStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }
}
