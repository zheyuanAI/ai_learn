package com.ailearn.platform.core.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import com.ailearn.platform.shared.idempotency.IdempotencyClaim;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.core.inventory.exception.InventoryErrorCode;
import com.ailearn.platform.core.inventory.exception.InventoryException;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Core 写应用服务共用的幂等执行协调器。
 * <p>
 * 业务服务只负责生成稳定的载荷摘要；本类统一处理租户隔离、处理中冲突、成功结果重放以及事务提交/回滚
     * 后的幂等记录状态，避免调拨、盘点和库存各自实现出不一致的边界。
 * </p>
 */
public class CoreIdempotencyExecutor {

    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyStorage storage;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Core 幂等执行器。
     *
     * @param storage 幂等记录存储
     * @param objectMapper 结果序列化器
     */
    public CoreIdempotencyExecutor(IdempotencyStorage storage, ObjectMapper objectMapper) {
        this.storage = storage;
        this.objectMapper = objectMapper;
    }

    /**
     * 使用默认 Core 操作域执行幂等命令，保留早期调用方的兼容入口。
     *
     * @param tenantId 可信租户
     * @param key 原始幂等键
     * @param requestHash 请求载荷摘要
     * @param responseType 响应类型
     * @param action 业务动作
     * @param <T> 响应类型
     * @return 首次成功或重放结果
     */
    public <T> T execute(UUID tenantId, String key, String requestHash,
                         Class<T> responseType, Supplier<T> action) {
        return execute("core:default", tenantId, key, requestHash, responseType, action);
    }

    /**
     * 执行或重放一次 Core 写命令。
     * 入参：可信租户、幂等键、载荷摘要、响应类型和业务动作；出参：首次成功或同载荷重放结果；流程：
     * 查询并校验历史记录，原子占用带 token 的幂等键，业务成功后在提交前缓存，事务失败时按 token 释放。
     *
     * @param operation 操作域
     * @param tenantId 可信租户
     * @param key 幂等键
     * @param requestHash 请求载荷摘要
     * @param responseType 响应类型
     * @param action 业务动作
     * @param <T> 响应类型
     * @return 首次成功或重放结果
     */
    public <T> T execute(String operation, UUID tenantId, String key, String requestHash,
                         Class<T> responseType, Supplier<T> action) {
        IdempotentRecord existing = storage.getRecord(operation, key, tenantId).orElse(null);
        if (existing != null) {
            return replayOrReject(existing, key, requestHash, responseType);
        }
        IdempotencyClaim claim = storage.tryAcquireClaim(operation, key, tenantId, TTL, requestHash)
                .orElse(null);
        if (claim == null) {
            IdempotentRecord raced = storage.getRecord(operation, key, tenantId).orElse(null);
            if (raced == null) {
                throw new InventoryException(InventoryErrorCode.INV_002, "幂等命令正在处理中");
            }
            return replayOrReject(raced, key, requestHash, responseType);
        }
        registerRollbackCleanup(claim);
        try {
            T result = action.get();
            completeBeforeCommit(claim, result);
            return result;
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                storage.fail(claim.operation(), claim.key(), claim.tenantId(), claim.token(), exception.getMessage());
            }
            throw exception;
        }
    }

    /**
     * 反序列化同载荷成功响应，摘要不一致或处理中状态统一返回 INV_002。
     *
     * @param record 历史幂等记录
     * @param key 幂等键
     * @param requestHash 当前载荷摘要
     * @param responseType 响应类型
     * @param <T> 响应类型
     * @return 成功响应
     */
    private <T> T replayOrReject(IdempotentRecord record, String key, String requestHash,
                                 Class<T> responseType) {
        if (!requestHash.equals(record.getRequestHash())) {
            throw new InventoryException(InventoryErrorCode.INV_002,
                    "同一幂等键的 payloadDigest 不一致: " + key);
        }
        if (record.getStatus() != IdempotentRecord.Status.SUCCESS
                || record.getResponseBody() == null || record.getResponseBody().isBlank()) {
            throw new InventoryException(InventoryErrorCode.INV_002, "幂等命令正在处理中");
        }
        try {
            return objectMapper.readValue(record.getResponseBody(), responseType);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("Core 幂等结果缓存不可解析", exception);
        }
    }

    /**
     * 注册事务回滚清理，防止失败请求永久占用幂等键。
     *
     * @param tenantId 可信租户
     * @param key 幂等键
     */
    private void registerRollbackCleanup(IdempotencyClaim claim) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.fail(claim.operation(), claim.key(), claim.tenantId(), claim.token(), "Core 事务未提交");
                }
            }
        });
    }

    /**
     * 在事务提交前缓存成功结果，使数据库幂等记录和业务事实一并提交；无事务调用立即完成记录。
     *
     * @param tenantId 可信租户
     * @param key 幂等键
     * @param result 成功结果
     */
    private void completeBeforeCommit(IdempotencyClaim claim, Object result) {
        String responseBody = serialize(result);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (!storage.complete(claim.operation(), claim.key(), claim.tenantId(), claim.token(), responseBody, TTL)) {
                throw new ServiceUnavailableException("Core 幂等记录完成状态写入失败");
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                if (!readOnly) {
                    if (!storage.complete(claim.operation(), claim.key(), claim.tenantId(), claim.token(), responseBody, TTL)) {
                        throw new ServiceUnavailableException("Core 幂等记录完成状态写入失败");
                    }
                }
            }
        });
    }

    /**
     * 序列化成功结果供幂等重放。
     *
     * @param result 业务响应
     * @return JSON 文本
     */
    private String serialize(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new ServiceUnavailableException("Core 幂等结果无法缓存", exception);
        }
    }
}
