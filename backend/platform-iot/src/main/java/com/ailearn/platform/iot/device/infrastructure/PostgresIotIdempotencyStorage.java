package com.ailearn.platform.iot.device.infrastructure;

import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.idempotency.IdempotencyClaim;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * IoT 管理写操作的 PostgreSQL 幂等存储。
 * 入参：操作域、原始幂等键、可信租户和 claim；出参：共享幂等记录或 CAS 结果；流程：
 * 以租户+操作域+原始键的部分唯一索引竞争占用，完成/失败均要求当前 claim token。
 */
@Component
@Primary
public class PostgresIotIdempotencyStorage implements IdempotencyStorage {
    private final JdbcTemplate jdbc;

    public PostgresIotIdempotencyStorage(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 兼容旧版未带操作域的占用入口。入参为原始幂等键、租户、TTL 和载荷摘要；出参为是否占用成功。 */
    @Override
    public boolean tryAcquire(String key, UUID tenantId, Duration ttl, String requestHash) {
        return tryAcquireClaim("iot:legacy", key, tenantId, ttl, requestHash).isPresent();
    }

    /**
     * 按租户、操作域和原始 Key 竞争 PENDING 记录。
     * 入参：操作域、Key、可信租户、TTL 和服务端载荷摘要；出参：带所有权 token 的 claim；流程：先失效过期记录，再由部分唯一索引原子竞争。
     */
    @Override
    public Optional<IdempotencyClaim> tryAcquireClaim(String operation, String key, UUID tenantId,
                                                       Duration ttl, String requestHash) {
        String normalizedOperation = normalizeOperation(operation);
        long ttlSeconds = Math.max(1L, ttl == null ? 1L : ttl.getSeconds());
        UUID claimToken = UUID.randomUUID();
        UUID operatorId = UserContextHolder.requireUserId();
        return Optional.ofNullable(db(() -> {
            jdbc.update("""
                    UPDATE iot_idempotency_record
                       SET status = 'FAILED', isdel = 1, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?
                       AND expires_at <= CURRENT_TIMESTAMP AND isdel = 0
                    """, operatorId, tenantId, normalizedOperation, key);
            int inserted = jdbc.update("""
                    INSERT INTO iot_idempotency_record
                        (id, tenant_id, operation, idempotency_key, request_hash, claim_token, status,
                         expires_at, created_by, created_at, updated_by, updated_at, isdel)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING',
                            CURRENT_TIMESTAMP + (CAST(? AS BIGINT) * INTERVAL '1 second'),
                            ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, 0)
                    ON CONFLICT (tenant_id, operation, idempotency_key)
                        WHERE isdel = 0 DO NOTHING
                    """, UUID.randomUUID(), tenantId, normalizedOperation, key, requestHash, claimToken,
                    ttlSeconds, operatorId, operatorId);
            return inserted == 1 ? new IdempotencyClaim(normalizedOperation, key, tenantId, claimToken) : null;
        }));
    }

    /** 兼容旧版带操作域的 boolean 占用入口，实际仍使用 token 竞争。 */
    @Override
    public boolean tryAcquire(String operation, String key, UUID tenantId, Duration ttl, String requestHash) {
        return tryAcquireClaim(operation, key, tenantId, ttl, requestHash).isPresent();
    }

    /** 兼容旧版未带操作域的完成入口；成功响应会刷新幂等保留时间。 */
    @Override
    public void complete(String key, UUID tenantId, String responseBody, Duration ttl) {
        complete("iot:legacy", key, tenantId, responseBody, ttl);
    }

    /** 兼容旧版带操作域的完成入口；更新失败时抛出 503，避免静默丢失幂等结果。 */
    @Override
    public void complete(String operation, String key, UUID tenantId, String responseBody, Duration ttl) {
        int changed = completeWithoutClaim(operation, key, tenantId, responseBody, ttl);
        if (changed != 1) {
            throw new ServiceUnavailableException("IoT 兼容幂等记录完成状态写入失败");
        }
    }

    /**
     * 按 claim token CAS 完成 PENDING 记录。
     * 入参：操作域、Key、租户、claim token、响应和 TTL；出参：仅当前执行者成功时为 true；流程：校验 token、状态和有效期后更新 SUCCESS。
     */
    @Override
    public boolean complete(String operation, String key, UUID tenantId, UUID claimToken,
                            String responseBody, Duration ttl) {
        long ttlSeconds = Math.max(1L, ttl == null ? 1L : ttl.getSeconds());
        UUID operatorId = UserContextHolder.requireUserId();
        return db(() -> jdbc.update("""
                UPDATE iot_idempotency_record
                   SET status = 'SUCCESS', response_body = ?, claim_token = NULL,
                       expires_at = CURRENT_TIMESTAMP + (CAST(? AS BIGINT) * INTERVAL '1 second'),
                       updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?
                   AND claim_token = ? AND status = 'PENDING' AND isdel = 0
                   AND expires_at > CURRENT_TIMESTAMP
                """, responseBody, ttlSeconds, operatorId, tenantId,
                normalizeOperation(operation), key, claimToken) == 1);
    }

    /** 兼容旧版未带操作域的失败入口。 */
    @Override
    public void fail(String key, UUID tenantId, String errorMessage) {
        fail("iot:legacy", key, tenantId, errorMessage);
    }

    /** 兼容旧版带操作域的失败入口，将记录逻辑失效以允许安全重试。 */
    @Override
    public void fail(String operation, String key, UUID tenantId, String errorMessage) {
        UUID operatorId = UserContextHolder.requireUserId();
        db(() -> jdbc.update("""
                UPDATE iot_idempotency_record
                   SET status = 'FAILED', error_message = ?, isdel = 1,
                       updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND operation = ? AND idempotency_key = ? AND isdel = 0
                """, errorMessage, operatorId, tenantId,
                normalizeOperation(operation), key));
    }

    /** 按 claim token CAS 释放 PENDING 记录，旧执行者不能释放已被过期重入者接管的记录。 */
    @Override
    public boolean fail(String operation, String key, UUID tenantId, UUID claimToken, String errorMessage) {
        UUID operatorId = UserContextHolder.requireUserId();
        return db(() -> jdbc.update("""
                UPDATE iot_idempotency_record
                   SET status = 'FAILED', error_message = ?, isdel = 1,
                       updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?
                   AND claim_token = ? AND status = 'PENDING' AND isdel = 0
                """, errorMessage, operatorId, tenantId,
                normalizeOperation(operation), key, claimToken) == 1);
    }

    /** 兼容旧版未带操作域的查询入口，仅返回当前租户内未过期记录。 */
    @Override
    public Optional<IdempotentRecord> getRecord(String key, UUID tenantId) {
        return getRecord("iot:legacy", key, tenantId);
    }

    /**
     * 按租户和操作域读取未过期记录。
     * 入参：操作域、原始 Key 和可信租户；出参：共享幂等记录；流程：过滤逻辑失效/过期数据并保留 claim 状态供执行器判定。
     */
    @Override
    public Optional<IdempotentRecord> getRecord(String operation, String key, UUID tenantId) {
        return db(() -> jdbc.query("""
                SELECT tenant_id, operation, idempotency_key, claim_token, request_hash, status,
                       response_body, created_at, expires_at
                  FROM iot_idempotency_record
                 WHERE tenant_id = ? AND operation = ? AND idempotency_key = ?
                   AND expires_at > CURRENT_TIMESTAMP AND isdel = 0
                 LIMIT 1
                """, (rs, row) -> new IdempotentRecord(rs.getString("idempotency_key"),
                rs.getObject("tenant_id", UUID.class), IdempotentRecord.Status.valueOf(rs.getString("status")),
                rs.getString("request_hash"), rs.getString("response_body"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("expires_at", OffsetDateTime.class),
                rs.getObject("claim_token", UUID.class)), tenantId, normalizeOperation(operation), key)
                .stream().findFirst());
    }

    /** 兼容完成入口的内部更新；返回实际更新行数供调用方判断数据库状态是否一致。 */
    private int completeWithoutClaim(String operation, String key, UUID tenantId, String responseBody, Duration ttl) {
        long ttlSeconds = Math.max(1L, ttl == null ? 1L : ttl.getSeconds());
        UUID operatorId = UserContextHolder.requireUserId();
        return db(() -> jdbc.update("""
                UPDATE iot_idempotency_record
                   SET status = 'SUCCESS', response_body = ?, claim_token = NULL,
                       expires_at = CURRENT_TIMESTAMP + (CAST(? AS BIGINT) * INTERVAL '1 second'),
                       updated_by = ?, updated_at = CURRENT_TIMESTAMP
                 WHERE tenant_id = ? AND operation = ? AND idempotency_key = ? AND isdel = 0
                """, responseBody, ttlSeconds, operatorId, tenantId,
                normalizeOperation(operation), key));
    }

    /** 规范化操作域，避免空操作域绕过租户+操作联合唯一键。 */
    private static String normalizeOperation(String operation) {
        return operation == null || operation.isBlank() ? "iot:legacy" : operation.trim();
    }

    /** 将 JDBC 运行时异常统一转换为平台 503，避免把数据库细节泄露到业务响应。 */
    private <T> T db(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ServiceUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("IoT 幂等数据库暂时不可用", exception);
        }
    }
}
