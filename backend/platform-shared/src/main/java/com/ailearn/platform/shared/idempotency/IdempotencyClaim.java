package com.ailearn.platform.shared.idempotency;

import java.util.UUID;

/**
 * 一次幂等执行的所有权凭证。
 * <p>
 * acquire 成功后只能由持有该 token 的执行者完成或释放记录；过期重入后，旧执行者的 token 不再匹配。
 * </p>
 *
 * @param operation 操作域
 * @param key 原始幂等键
 * @param tenantId 可信租户
 * @param token 本次占用凭证
 */
public record IdempotencyClaim(String operation, String key, UUID tenantId, UUID token) {

    /**
     * 校验 claim 必要字段，避免把空凭证带入完成/失败 CAS。
     */
    public IdempotencyClaim {
        if (operation == null || operation.isBlank() || key == null || key.isBlank()
                || tenantId == null || token == null) {
            throw new IllegalArgumentException("幂等 claim 字段不能为空");
        }
    }
}
