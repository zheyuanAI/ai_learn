package com.ailearn.platform.core.purchasing.application;

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
 * 采购操作域幂等协调器。
 * <p>
 * 复用共享数据库/内存幂等语义，但将冲突和处理中状态映射为采购稳定错误码；claim token、事务提交前完成和
 * 回滚失效共同避免重复收货或旧请求覆盖新结果。
 * </p>
 */
final class PurchasingIdempotencyExecutor {

    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyStorage storage;
    private final ObjectMapper objectMapper;

    /**
     * 创建采购幂等执行器。
     */
    PurchasingIdempotencyExecutor(IdempotencyStorage storage, ObjectMapper objectMapper) {
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行采购命令或重放同载荷的成功结果。
     *
     * @param operation 采购操作域
     * @param tenantId 可信租户
     * @param key HTTP 幂等键
     * @param requestHash 服务端载荷摘要
     * @param responseType 响应类型
     * @param action 业务动作
     * @param <T> 响应类型
     * @return 首次结果或重放结果
     */
    <T> T execute(String operation, UUID tenantId, String key, String requestHash,
                  Class<T> responseType, Supplier<T> action) {
        IdempotentRecord existing = storage.getRecord(operation, key, tenantId).orElse(null);
        if (existing != null) {
            return replayOrReject(existing, key, requestHash, responseType);
        }
        Optional<IdempotencyClaim> claim = storage.tryAcquireClaim(operation, key, tenantId, TTL, requestHash);
        if (claim.isEmpty()) {
            IdempotentRecord raced = storage.getRecord(operation, key, tenantId).orElse(null);
            if (raced == null) {
                throw new PurchasingException(PurchasingErrorCode.PO_001, "采购幂等命令正在处理中");
            }
            return replayOrReject(raced, key, requestHash, responseType);
        }
        IdempotencyClaim ownership = claim.get();
        registerRollbackCleanup(ownership);
        try {
            T result = action.get();
            completeBeforeCommit(ownership, result);
            return result;
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                storage.fail(operation, key, tenantId, ownership.token(), exception.getMessage());
            }
            throw exception;
        }
    }

    private <T> T replayOrReject(IdempotentRecord record, String key, String requestHash,
                                 Class<T> responseType) {
        if (!requestHash.equals(record.getRequestHash())) {
            throw new PurchasingException(PurchasingErrorCode.PO_001,
                    "同一幂等键的 payloadDigest 不一致: " + key);
        }
        if (record.getStatus() != IdempotentRecord.Status.SUCCESS
                || record.getResponseBody() == null || record.getResponseBody().isBlank()) {
            throw new PurchasingException(PurchasingErrorCode.PO_001, "采购幂等命令正在处理中");
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("采购幂等结果不可解析", exception);
        }
    }

    private void registerRollbackCleanup(IdempotencyClaim claim) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.fail(claim.operation(), claim.key(), claim.tenantId(), claim.token(), "采购事务未提交");
                }
            }
        });
    }

    private void completeBeforeCommit(IdempotencyClaim claim, Object result) {
        String responseBody = serialize(result);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            complete(claim, responseBody);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                if (!readOnly) {
                    complete(claim, responseBody);
                }
            }
        });
    }

    private void complete(IdempotencyClaim claim, String responseBody) {
        if (!storage.complete(claim.operation(), claim.key(), claim.tenantId(), claim.token(), responseBody, TTL)) {
            throw new ServiceUnavailableException("采购幂等记录所有权已变化");
        }
    }

    private String serialize(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("采购幂等结果无法缓存", exception);
        }
    }
}
