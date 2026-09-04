package com.ailearn.platform.core.purchasing.putaway.application;

import com.ailearn.platform.core.purchasing.exception.PurchasingErrorCode;
import com.ailearn.platform.core.purchasing.exception.PurchasingException;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.idempotency.IdempotencyClaim;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 上架确认幂等协调器，避免重试重复移动同一批实物。
 */
final class PutawayIdempotencyExecutor {

    private static final Duration TTL = Duration.ofHours(24);
    private final IdempotencyStorage storage;
    private final ObjectMapper objectMapper;

    PutawayIdempotencyExecutor(IdempotencyStorage storage, ObjectMapper objectMapper) {
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次上架命令或重放同载荷成功结果。
     */
    <T> T execute(String operation, UUID tenantId, String key, String hash,
                  Class<T> responseType, Supplier<T> action) {
        IdempotentRecord existing = storage.getRecord(operation, key, tenantId).orElse(null);
        if (existing != null) return replay(existing, key, hash, responseType);
        Optional<IdempotencyClaim> acquired = storage.tryAcquireClaim(operation, key, tenantId, TTL, hash);
        if (acquired.isEmpty()) {
            IdempotentRecord raced = storage.getRecord(operation, key, tenantId).orElse(null);
            if (raced == null) throw new PurchasingException(PurchasingErrorCode.PO_001, "上架幂等命令正在处理中");
            return replay(raced, key, hash, responseType);
        }
        IdempotencyClaim claim = acquired.get();
        try {
            T result = action.get();
            String body = serialize(result);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        if (!readOnly) complete(operation, key, tenantId, claim, body);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) storage.fail(operation, key, tenantId, claim.token(), "上架事务未提交");
                    }
                });
            } else {
                complete(operation, key, tenantId, claim, body);
            }
            return result;
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                storage.fail(operation, key, tenantId, claim.token(), exception.getMessage());
            }
            throw exception;
        }
    }

    private <T> T replay(IdempotentRecord record, String key, String hash, Class<T> type) {
        if (!hash.equals(record.getRequestHash())) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "同一幂等键的 payloadDigest 不一致: " + key);
        }
        if (record.getStatus() != IdempotentRecord.Status.SUCCESS
                || record.getResponseBody() == null || record.getResponseBody().isBlank()) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "上架幂等命令正在处理中");
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), type);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("上架幂等结果不可解析", exception);
        }
    }

    private void complete(String operation, String key, UUID tenantId, IdempotencyClaim claim, String body) {
        if (!storage.complete(operation, key, tenantId, claim.token(), body, TTL)) {
            throw new ServiceUnavailableException("上架幂等记录所有权已变化");
        }
    }

    private String serialize(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("上架幂等结果无法缓存", exception);
        }
    }
}
