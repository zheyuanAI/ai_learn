package com.ailearn.platform.shared.idempotency;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于本地内存并发 Map 的幂等存储默认实现。
 * <p>
 * 在单机开发、单元测试或未引入 Redis 时提供基础的幂等防重支持。
 * </p>
 */
public class InMemoryIdempotencyStorage implements IdempotencyStorage {

    private final Map<String, IdempotentRecord> storage = new ConcurrentHashMap<>();

    private String buildStorageKey(String key, UUID tenantId) {
        return (tenantId != null ? tenantId.toString() : "global") + ":" + key;
    }

    @Override
    public boolean tryAcquire(String key, UUID tenantId, Duration ttl, String requestHash) {
        String fullKey = buildStorageKey(key, tenantId);
        OffsetDateTime now = OffsetDateTime.now();
        Duration actualTtl = ttl == null ? Duration.ofSeconds(1) : ttl;
        UUID token = UUID.randomUUID();
        AtomicReference<Boolean> acquired = new AtomicReference<>(false);
        storage.compute(fullKey, (ignored, existing) -> {
            if (existing != null && existing.getExpireAt() != null
                    && !existing.getExpireAt().isBefore(now)) {
                return existing;
            }
            acquired.set(true);
            return new IdempotentRecord(key, tenantId, IdempotentRecord.Status.PENDING,
                    requestHash, null, now, now.plus(actualTtl), token);
        });
        return acquired.get();
    }

    /**
     * 原子创建带 token 的 PENDING 记录；过期记录仅在同一次 Map 原子计算中被替换。
     */
    @Override
    public Optional<IdempotencyClaim> tryAcquireClaim(String operation, String key, UUID tenantId,
                                                       Duration ttl, String requestHash) {
        String scopedKey = IdempotencyStorage.scopedKey(operation, key);
        String fullKey = buildStorageKey(scopedKey, tenantId);
        OffsetDateTime now = OffsetDateTime.now();
        Duration actualTtl = ttl == null ? Duration.ofSeconds(1) : ttl;
        UUID token = UUID.randomUUID();
        AtomicReference<IdempotencyClaim> acquired = new AtomicReference<>();
        storage.compute(fullKey, (ignored, existing) -> {
            if (existing != null && existing.getExpireAt() != null
                    && !existing.getExpireAt().isBefore(now)) {
                return existing;
            }
            acquired.set(new IdempotencyClaim(operation, key, tenantId, token));
            return new IdempotentRecord(scopedKey, tenantId, IdempotentRecord.Status.PENDING,
                    requestHash, null, now, now.plus(actualTtl), token);
        });
        return Optional.ofNullable(acquired.get());
    }

    @Override
    public void complete(String key, UUID tenantId, String responseBody, Duration ttl) {
        String fullKey = buildStorageKey(key, tenantId);
        IdempotentRecord record = storage.get(fullKey);
        if (record != null) {
            record.setStatus(IdempotentRecord.Status.SUCCESS);
            record.setResponseBody(responseBody);
            record.setExpireAt(OffsetDateTime.now().plus(ttl));
        }
    }

    /**
     * 仅允许当前 PENDING claim 完成，拒绝过期重入后的旧执行者覆盖新结果。
     */
    @Override
    public boolean complete(String operation, String key, UUID tenantId, UUID claimToken,
                            String responseBody, Duration ttl) {
        String fullKey = buildStorageKey(IdempotencyStorage.scopedKey(operation, key), tenantId);
        AtomicReference<Boolean> completed = new AtomicReference<>(false);
        storage.computeIfPresent(fullKey, (ignored, record) -> {
            if (record.getStatus() == IdempotentRecord.Status.PENDING
                    && claimToken != null && claimToken.equals(record.getClaimToken())
                    && (record.getExpireAt() == null || record.getExpireAt().isAfter(OffsetDateTime.now()))) {
                record.setStatus(IdempotentRecord.Status.SUCCESS);
                record.setResponseBody(responseBody);
                record.setClaimToken(null);
                record.setExpireAt(OffsetDateTime.now().plus(ttl == null ? Duration.ofSeconds(1) : ttl));
                completed.set(true);
            }
            return record;
        });
        return completed.get();
    }

    @Override
    public void fail(String key, UUID tenantId, String errorMessage) {
        String fullKey = buildStorageKey(key, tenantId);
        storage.remove(fullKey);
    }

    /**
     * 仅允许当前 PENDING claim 释放；旧 claim 失败不会删除新请求的记录。
     */
    @Override
    public boolean fail(String operation, String key, UUID tenantId, UUID claimToken,
                        String errorMessage) {
        String fullKey = buildStorageKey(IdempotencyStorage.scopedKey(operation, key), tenantId);
        AtomicReference<Boolean> failed = new AtomicReference<>(false);
        storage.computeIfPresent(fullKey, (ignored, record) -> {
            if (record.getStatus() == IdempotentRecord.Status.PENDING
                    && claimToken != null && claimToken.equals(record.getClaimToken())) {
                failed.set(true);
                return null;
            }
            return record;
        });
        return failed.get();
    }

    @Override
    public Optional<IdempotentRecord> getRecord(String key, UUID tenantId) {
        String fullKey = buildStorageKey(key, tenantId);
        IdempotentRecord record = storage.get(fullKey);
        if (record != null && record.getExpireAt() != null && record.getExpireAt().isBefore(OffsetDateTime.now())) {
            storage.remove(fullKey);
            return Optional.empty();
        }
        return Optional.ofNullable(record);
    }
}
