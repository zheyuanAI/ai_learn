package com.ailearn.platform.shared.idempotency;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

        IdempotentRecord existing = storage.get(fullKey);
        if (existing != null) {
            // 检查是否已过期
            if (existing.getExpireAt() != null && existing.getExpireAt().isBefore(now)) {
                storage.remove(fullKey);
            } else {
                return false;
            }
        }

        IdempotentRecord record = new IdempotentRecord(
                key,
                tenantId,
                IdempotentRecord.Status.PENDING,
                requestHash,
                null,
                now,
                now.plus(ttl)
        );

        return storage.putIfAbsent(fullKey, record) == null;
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

    @Override
    public void fail(String key, UUID tenantId, String errorMessage) {
        String fullKey = buildStorageKey(key, tenantId);
        storage.remove(fullKey);
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
