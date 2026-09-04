package com.ailearn.platform.core.manufacturing.foundation.application;

import com.ailearn.platform.core.manufacturing.foundation.exception.FoundationErrorCode;
import com.ailearn.platform.core.manufacturing.foundation.exception.FoundationException;
import com.ailearn.platform.shared.idempotency.IdempotencyClaim;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * foundation 写命令幂等协调器。
 * <p>
 * 幂等键由操作域和可信租户共同隔离，载荷摘要由服务端根据完整请求计算；事务存在时，成功记录在提交前加入
 * 同一事务，失败在回滚回调中释放，避免来源工单重复创建或失败占用键。
 * </p>
 */
public final class FoundationIdempotencyExecutor {

    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyStorage storage;
    private final ObjectMapper objectMapper;

    /**
     * 创建幂等协调器。
     *
     * @param storage 幂等记录存储
     * @param objectMapper JSON 序列化器
     */
    public FoundationIdempotencyExecutor(IdempotencyStorage storage, ObjectMapper objectMapper) {
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行或重放一次 foundation 写命令。
     *
     * @param operation 操作域
     * @param tenantId 可信租户
     * @param idempotencyKey 原始幂等键
     * @param payload 完整服务端请求对象
     * @param responseType 响应类型
     * @param action 首次执行动作
     * @param <T> 响应类型
     * @return 首次结果或同载荷重放结果
     */
    public <T> T execute(String operation, UUID tenantId, String idempotencyKey, Object payload,
                         Class<T> responseType, Supplier<T> action) {
        requireKey(idempotencyKey);
        String requestHash = digest(payload);
        IdempotentRecord existing = storage.getRecord(operation, idempotencyKey, tenantId).orElse(null);
        if (existing != null) {
            return replayOrReject(existing, idempotencyKey, requestHash, responseType);
        }
        Optional<IdempotencyClaim> claim = storage.tryAcquireClaim(operation, idempotencyKey,
                tenantId, TTL, requestHash);
        if (claim.isEmpty()) {
            IdempotentRecord raced = storage.getRecord(operation, idempotencyKey, tenantId).orElse(null);
            if (raced == null) {
                throw conflict("幂等命令正在处理中");
            }
            return replayOrReject(raced, idempotencyKey, requestHash, responseType);
        }
        IdempotencyClaim ownership = claim.get();
        registerRollbackCleanup(ownership);
        try {
            T result = action.get();
            completeBeforeCommit(ownership, result);
            return result;
        } catch (RuntimeException exception) {
            if (!hasActualTransaction()) {
                storage.fail(operation, idempotencyKey, tenantId, ownership.token(), exception.getMessage());
            }
            throw exception;
        }
    }

    private <T> T replayOrReject(IdempotentRecord record, String key, String requestHash,
                                 Class<T> responseType) {
        if (!requestHash.equals(record.getRequestHash())) {
            throw conflict("同一幂等键的服务端载荷摘要不一致: " + key);
        }
        if (record.getStatus() != IdempotentRecord.Status.SUCCESS
                || record.getResponseBody() == null || record.getResponseBody().isBlank()) {
            throw conflict("幂等命令正在处理中");
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw conflict("foundation 幂等结果不可解析");
        }
    }

    private void registerRollbackCleanup(IdempotencyClaim claim) {
        if (!hasActualTransaction()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.fail(claim.operation(), claim.key(), claim.tenantId(), claim.token(),
                            "foundation 事务未提交");
                }
            }
        });
    }

    private void completeBeforeCommit(IdempotencyClaim claim, Object result) {
        String responseBody;
        try {
            responseBody = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw conflict("foundation 结果无法缓存");
        }
        if (!hasActualTransaction()) {
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
        if (!storage.complete(claim.operation(), claim.key(), claim.tenantId(), claim.token(),
                responseBody, TTL)) {
            throw conflict("foundation 幂等记录所有权已变化");
        }
    }

    private String digest(Object payload) {
        try {
            byte[] bytes = objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw conflict("foundation 请求载荷无法计算摘要");
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128) {
            throw new FoundationException(FoundationErrorCode.MES_FOUNDATION_001,
                    "Idempotency-Key 必须存在且不超过 128 个字符");
        }
    }

    private static FoundationException conflict(String message) {
        return new FoundationException(FoundationErrorCode.MES_FOUNDATION_002, message);
    }

    /** 判断当前线程是否真的处于 Spring 事务，而不是只有残留的同步标记。 */
    private static boolean hasActualTransaction() {
        return TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive();
    }
}
