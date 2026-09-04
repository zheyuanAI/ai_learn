package com.ailearn.platform.core.quality.application;

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
 * 质量命令幂等协调器，保证决定和执行重试不会重复写质量或库存事实。
 */
final class QualityIdempotencyExecutor {

    private static final Duration TTL = Duration.ofHours(24);
    private final IdempotencyStorage storage;
    private final ObjectMapper objectMapper;

    QualityIdempotencyExecutor(IdempotencyStorage storage, ObjectMapper objectMapper) {
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /**
     * 按租户、操作和幂等键执行一次质量命令；同载荷重试重放首个成功响应。
     */
    <T> T execute(String operation, UUID tenantId, String key, String requestHash,
                  Class<T> responseType, Supplier<T> action) {
        IdempotentRecord existing = storage.getRecord(operation, key, tenantId).orElse(null);
        if (existing != null) {
            return replay(existing, key, requestHash, responseType);
        }
        Optional<IdempotencyClaim> claimed = storage.tryAcquireClaim(operation, key, tenantId, TTL, requestHash);
        if (claimed.isEmpty()) {
            IdempotentRecord raced = storage.getRecord(operation, key, tenantId).orElse(null);
            if (raced == null) {
                throw new PurchasingException(PurchasingErrorCode.PO_001, "质量幂等命令正在处理中");
            }
            return replay(raced, key, requestHash, responseType);
        }
        IdempotencyClaim claim = claimed.get();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        storage.fail(operation, key, tenantId, claim.token(), "质量事务未提交");
                    }
                }
            });
        }
        try {
            T result = action.get();
            String response = serialize(result);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void beforeCommit(boolean readOnly) {
                        if (!readOnly) {
                            complete(operation, key, tenantId, claim, response);
                        }
                    }
                });
            } else {
                complete(operation, key, tenantId, claim, response);
            }
            return result;
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                storage.fail(operation, key, tenantId, claim.token(), exception.getMessage());
            }
            throw exception;
        }
    }

    private <T> T replay(IdempotentRecord record, String key, String requestHash, Class<T> responseType) {
        if (!requestHash.equals(record.getRequestHash())) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "同一幂等键的 payloadDigest 不一致: " + key);
        }
        if (record.getStatus() != IdempotentRecord.Status.SUCCESS
                || record.getResponseBody() == null || record.getResponseBody().isBlank()) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "质量幂等命令正在处理中");
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("质量幂等结果不可解析", exception);
        }
    }

    private void complete(String operation, String key, UUID tenantId, IdempotencyClaim claim, String response) {
        if (!storage.complete(operation, key, tenantId, claim.token(), response, TTL)) {
            throw new ServiceUnavailableException("质量幂等记录所有权已变化");
        }
    }

    private String serialize(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("质量幂等结果无法缓存", exception);
        }
    }
}
