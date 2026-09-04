package com.ailearn.platform.iot.device.application;

import com.ailearn.platform.iot.device.exception.IotErrorCode;
import com.ailearn.platform.iot.device.exception.IotException;
import com.ailearn.platform.shared.idempotency.IdempotencyClaim;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * IoT 管理写操作的幂等协调器。
 * 入参：操作域、可信租户、原始幂等键、服务端载荷摘要及动作；出参：首次结果或同载荷重放结果。
 * 流程：先占用带操作域的幂等键，再执行事务动作，提交前写成功响应，回滚时释放占用。
 */
@Component
public class IotIdempotencyExecutor {
    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyStorage storage;
    private final ObjectMapper objectMapper;

    public IotIdempotencyExecutor(IdempotencyStorage storage, ObjectMapper objectMapper) {
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /** 执行一次带操作域的 IoT 写命令。 */
    public <T> T execute(String operation, UUID tenantId, String key, String requestHash,
                         Class<T> responseType, Supplier<T> action) {
        return execute(operation, tenantId, key, requestHash, responseType, action, UnaryOperator.identity());
    }

    /**
     * 执行一次带操作域的 IoT 写命令，并允许为幂等缓存提供脱敏结果。
     * 入参：操作域、可信租户、原始键、服务端摘要、响应类型、业务动作和缓存结果转换器；
     * 出参：首次执行的完整结果或后续请求的缓存结果；流程：claim CAS 占用、执行业务、事务提交前保存缓存。
     * 凭证创建使用该入口，确保一次性明文只返回首次结果且不会进入幂等存储。
     */
    public <T> T execute(String operation, UUID tenantId, String key, String requestHash,
                         Class<T> responseType, Supplier<T> action, UnaryOperator<T> cacheResult) {
        IdempotentRecord existing = storage.getRecord(operation, key, tenantId).orElse(null);
        if (existing != null) {
            return replay(existing, key, requestHash, responseType);
        }
        IdempotencyClaim claim = storage.tryAcquireClaim(operation, key, tenantId, TTL, requestHash)
                .orElse(null);
        if (claim == null) {
            IdempotentRecord raced = storage.getRecord(operation, key, tenantId).orElse(null);
            if (raced == null) {
                throw new IotException(IotErrorCode.IDEMPOTENCY_CONFLICT, "幂等命令正在处理中");
            }
            return replay(raced, key, requestHash, responseType);
        }
        registerRollback(claim);
        try {
            T result = action.get();
            completeBeforeCommit(claim, cacheResult.apply(result));
            return result;
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                storage.fail(claim.operation(), claim.key(), claim.tenantId(), claim.token(), exception.getMessage());
            }
            throw exception;
        }
    }

    private <T> T replay(IdempotentRecord record, String key, String requestHash, Class<T> responseType) {
        if (!requestHash.equals(record.getRequestHash())
                || record.getStatus() != IdempotentRecord.Status.SUCCESS
                || record.getResponseBody() == null) {
            throw new IotException(IotErrorCode.IDEMPOTENCY_CONFLICT, "同一幂等键载荷不一致或仍在处理中: " + key);
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new IotException(IotErrorCode.IDEMPOTENCY_CONFLICT, "幂等结果不可解析");
        }
    }

    private void registerRollback(IdempotencyClaim claim) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.fail(claim.operation(), claim.key(), claim.tenantId(), claim.token(), "IoT 事务未提交");
                }
            }
        });
    }

    private void completeBeforeCommit(IdempotencyClaim claim, Object result) {
        final String response;
        try {
            response = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            storage.fail(claim.operation(), claim.key(), claim.tenantId(), claim.token(), "IoT 幂等结果无法序列化");
            throw new IotException(IotErrorCode.IDEMPOTENCY_CONFLICT, "幂等结果无法缓存");
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (!storage.complete(claim.operation(), claim.key(), claim.tenantId(), claim.token(), response, TTL)) {
                throw new IotException(IotErrorCode.IDEMPOTENCY_CONFLICT, "幂等记录完成状态写入失败");
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                if (!readOnly) {
                    if (!storage.complete(claim.operation(), claim.key(), claim.tenantId(), claim.token(), response, TTL)) {
                        throw new IotException(IotErrorCode.IDEMPOTENCY_CONFLICT, "幂等记录完成状态写入失败");
                    }
                }
            }
        });
    }
}
