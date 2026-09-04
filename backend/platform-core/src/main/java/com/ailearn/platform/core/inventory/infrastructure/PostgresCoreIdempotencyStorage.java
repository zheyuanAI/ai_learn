package com.ailearn.platform.core.inventory.infrastructure;

import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.IdempotencyClaim;
import com.ailearn.platform.shared.idempotency.IdempotentRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Core 幂等记录的 PostgreSQL 实现。
 * <p>
 * 数据库部分唯一索引负责并发竞争，应用服务在事务提交前写 SUCCESS，失败则将记录逻辑失效。
 * </p>
 */
@Component
@Primary
public class PostgresCoreIdempotencyStorage implements IdempotencyStorage {

    private final CoreIdempotencyMapper mapper;

    /**
     * 创建 Core PostgreSQL 幂等存储。
     *
     * @param mapper Core 幂等记录 Mapper
     */
    public PostgresCoreIdempotencyStorage(CoreIdempotencyMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 先失效同键过期记录，再竞争创建处理中记录。
     *
     * @param key 幂等键
     * @param tenantId 可信租户
     * @param ttl 幂等窗口
     * @param requestHash 请求摘要
     * @return 数据库成功插入时返回 true
     */
    @Override
    public boolean tryAcquire(String key, UUID tenantId, Duration ttl, String requestHash) {
        return tryAcquireClaim("core:legacy", key, tenantId, ttl, requestHash).isPresent();
    }

    /**
     * 按 Core 操作域竞争创建幂等记录，并把操作域写入数据库审计列。
     * 入参：操作域、原始 Key、可信租户、TTL 和服务端摘要；出参：是否占用成功；流程：先失效作用域内
     * 过期记录，再使用数据库部分唯一索引竞争插入。
     *
     * @param operation 操作域
     * @param key 原始幂等键
     * @param tenantId 可信租户
     * @param ttl 幂等窗口
     * @param requestHash 服务端摘要
     * @return 是否占用成功
     */
    @Override
    public Optional<IdempotencyClaim> tryAcquireClaim(String operation, String key, UUID tenantId,
                                                      Duration ttl, String requestHash) {
        String scopedKey = IdempotencyStorage.scopedKey(operation, key);
        mapper.expire(tenantId, scopedKey);
        long ttlSeconds = Math.max(1L, ttl == null ? 1L : ttl.getSeconds());
        UUID claimToken = UUID.randomUUID();
        boolean acquired = mapper.tryAcquire(UUID.randomUUID(), claimToken, tenantId, scopedKey, operation,
                requestHash, ttlSeconds, operatorId()) == 1;
        return acquired ? Optional.of(new IdempotencyClaim(operation, key, tenantId, claimToken)) : Optional.empty();
    }

    /**
     * 旧 boolean 入口兼容适配器；新业务由 CoreIdempotencyExecutor 使用带 token 的入口。
     */
    @Override
    public boolean tryAcquire(String operation, String key, UUID tenantId, Duration ttl, String requestHash) {
        return tryAcquireClaim(operation, key, tenantId, ttl, requestHash).isPresent();
    }

    /**
     * 保存成功响应并刷新幂等窗口。
     *
     * @param key 幂等键
     * @param tenantId 可信租户
     * @param responseBody 成功响应 JSON
     * @param ttl 成功结果保留时间
     */
    @Override
    public void complete(String key, UUID tenantId, String responseBody, Duration ttl) {
        String scopedKey = IdempotencyStorage.scopedKey("core:legacy", key);
        long ttlSeconds = Math.max(1L, ttl == null ? 1L : ttl.getSeconds());
        if (mapper.completeLegacy(tenantId, scopedKey, responseBody, ttlSeconds, operatorId()) != 1) {
            throw new ServiceUnavailableException("Core 兼容幂等记录完成状态写入失败");
        }
    }

    /**
     * 按 Core 操作域完成幂等记录；更新不到唯一的 PENDING 记录时立即暴露依赖异常，避免事实提交后静默丢失。
     *
     * @param operation 操作域
     * @param key 原始幂等键
     * @param tenantId 可信租户
     * @param responseBody 成功响应
     * @param ttl 成功结果保留时间
     */
    @Override
    public void complete(String operation, String key, UUID tenantId, String responseBody, Duration ttl) {
        String scopedKey = IdempotencyStorage.scopedKey(operation, key);
        long ttlSeconds = Math.max(1L, ttl == null ? 1L : ttl.getSeconds());
        if (mapper.completeLegacy(tenantId, scopedKey, responseBody, ttlSeconds, operatorId()) != 1) {
            throw new ServiceUnavailableException("Core 兼容幂等记录完成状态写入失败");
        }
    }

    /**
     * 以 token + PENDING 条件完成幂等记录，CAS 失败即拒绝旧执行者写入。
     */
    @Override
    public boolean complete(String operation, String key, UUID tenantId, UUID claimToken,
                            String responseBody, Duration ttl) {
        String scopedKey = IdempotencyStorage.scopedKey(operation, key);
        long ttlSeconds = Math.max(1L, ttl == null ? 1L : ttl.getSeconds());
        return mapper.complete(tenantId, scopedKey, claimToken, responseBody, ttlSeconds, operatorId()) == 1;
    }

    /**
     * 失败时逻辑失效当前幂等记录。
     *
     * @param key 幂等键
     * @param tenantId 可信租户
     * @param errorMessage 失败原因
     */
    @Override
    public void fail(String key, UUID tenantId, String errorMessage) {
        mapper.failLegacy(tenantId, IdempotencyStorage.scopedKey("core:legacy", key), errorMessage, operatorId());
    }

    /**
     * 按 Core 操作域失效幂等记录。
     *
     * @param operation 操作域
     * @param key 原始幂等键
     * @param tenantId 可信租户
     * @param errorMessage 失败原因
     */
    @Override
    public void fail(String operation, String key, UUID tenantId, String errorMessage) {
        mapper.failLegacy(tenantId, IdempotencyStorage.scopedKey(operation, key), errorMessage, operatorId());
    }

    /**
     * 以 token + PENDING 条件释放幂等记录，CAS 失败不会影响后续重入请求。
     */
    @Override
    public boolean fail(String operation, String key, UUID tenantId, UUID claimToken, String errorMessage) {
        return mapper.fail(tenantId, IdempotencyStorage.scopedKey(operation, key), claimToken,
                errorMessage, operatorId()) == 1;
    }

    /**
     * 查询租户内未过期幂等记录并转换为共享模型。
     *
     * @param key 幂等键
     * @param tenantId 可信租户
     * @return 共享幂等记录
     */
    @Override
    public Optional<IdempotentRecord> getRecord(String key, UUID tenantId) {
        return getRecord("core:legacy", key, tenantId);
    }

    /**
     * 按 Core 操作域读取幂等记录。
     *
     * @param operation 操作域
     * @param key 原始幂等键
     * @param tenantId 可信租户
     * @return 作用域内的幂等记录
     */
    @Override
    public Optional<IdempotentRecord> getRecord(String operation, String key, UUID tenantId) {
        CoreIdempotencyRow row = mapper.findActive(tenantId, IdempotencyStorage.scopedKey(operation, key));
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new IdempotentRecord(row.getIdempotencyKey(), row.getTenantId(),
                IdempotentRecord.Status.valueOf(row.getStatus()), row.getRequestHash(), row.getResponseBody(),
                OffsetDateTime.now(), row.getExpiresAt(), row.getClaimToken()));
    }

    /**
     * 获取当前可信操作人，用于幂等记录审计字段。
     *
     * @return 当前用户 ID
     */
    private UUID operatorId() {
        return UserContextHolder.requireUserId();
    }
}
