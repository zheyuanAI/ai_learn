package com.ailearn.platform.core.inventory.application;

import com.ailearn.platform.shared.exception.ValidationException;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存命令统一审计元数据。
 * <p>
 * 业务命令必须携带该对象；应用服务会把其中的租户、用户、JTI 和请求 ID 与共享可信上下文逐项比对，
 * 不接受客户端借命令字段切换租户或操作人。
 * </p>
 *
 * @param tenantId 租户 ID
 * @param userId 操作用户 ID
 * @param sessionId 当前会话 JTI
 * @param requestId 当前请求 ID
 * @param idempotencyKey 幂等键
 * @param payloadDigest 请求载荷摘要
 * @param sourceType 来源单据类型
 * @param sourceId 来源单据 ID
 * @param sourceLineId 来源单据明细 ID，可空
 * @param transactionType 库存交易类型
 * @param businessTime 业务发生时间
 */
public record InventoryCommandMetadata(
        UUID tenantId,
        UUID userId,
        String sessionId,
        String requestId,
        String idempotencyKey,
        String payloadDigest,
        String sourceType,
        UUID sourceId,
        UUID sourceLineId,
        String transactionType,
        OffsetDateTime businessTime) {

    /**
     * 校验命令所需的审计和幂等字段，不读取线程上下文。
     *
     * @throws ValidationException 任一必填字段缺失或超过数据库字段长度
     */
    public void validate() {
        if (tenantId == null || userId == null) {
            throw new ValidationException("库存命令必须携带 tenantId 和 userId");
        }
        requireText("sessionId", sessionId, 128);
        requireText("requestId", requestId, 128);
        requireText("idempotencyKey", idempotencyKey, 128);
        requireText("payloadDigest", payloadDigest, 128);
        requireText("sourceType", sourceType, 64);
        if (sourceId == null) {
            throw new ValidationException("sourceId 不能为空");
        }
        requireText("transactionType", transactionType, 64);
        if (businessTime == null) {
            throw new ValidationException("businessTime 不能为空");
        }
    }

    /**
     * 校验文本字段非空且不超出持久化长度。
     *
     * @param fieldName 字段名
     * @param value 字段值
     * @param maxLength 最大长度
     */
    private static void requireText(String fieldName, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " 不能为空");
        }
        if (value.length() > maxLength) {
            throw new ValidationException(fieldName + " 长度不能超过 " + maxLength);
        }
    }
}
