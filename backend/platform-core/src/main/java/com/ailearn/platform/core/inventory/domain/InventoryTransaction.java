package com.ailearn.platform.core.inventory.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 只追加库存流水事实。
 * <p>
 * 位置移动同时携带来源和目标维度；增加只填写目标维度，扣减只填写来源维度。
 * </p>
 *
 * @param id 流水 ID
 * @param tenantId 所属租户
 * @param transactionNo 流水业务编号
 * @param transactionType 交易类型
 * @param sourceType 来源单据类型
 * @param sourceId 来源单据 ID
 * @param sourceLineId 来源明细 ID
 * @param fromDimension 来源库存维度，可空
 * @param toDimension 目标库存维度，可空
 * @param quantity 事实数量
 * @param occurredAt 业务发生时间
 * @param operatorId 操作用户
 * @param sessionId 操作会话 JTI
 * @param requestId 请求 ID
 * @param idempotencyKey 幂等键
 * @param payloadDigest 请求载荷摘要
 */
public record InventoryTransaction(
        UUID id,
        UUID tenantId,
        String transactionNo,
        String transactionType,
        String sourceType,
        UUID sourceId,
        UUID sourceLineId,
        InventoryDimension fromDimension,
        InventoryDimension toDimension,
        BigDecimal quantity,
        OffsetDateTime occurredAt,
        UUID operatorId,
        String sessionId,
        String requestId,
        String idempotencyKey,
        String payloadDigest) {
}
