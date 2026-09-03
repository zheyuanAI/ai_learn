package com.ailearn.platform.shared.idempotency;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * 幂等执行记录存储抽象接口。
 * <p>
 * 定义幂等加锁、完成状态记录、失败回退及查询的统一规范，可由 Redis、数据库表或内存缓存实现。
 * </p>
 */
public interface IdempotencyStorage {

    /**
     * 尝试获取幂等执行锁并记录 PENDING 状态。
     *
     * @param key         幂等 Key
     * @param tenantId    租户 UUID
     * @param ttl         锁及记录有效期
     * @param requestHash 请求参数哈希摘要
     * @return 若加锁成功返回 true；若已存在相同 key 则返回 false
     */
    boolean tryAcquire(String key, UUID tenantId, Duration ttl, String requestHash);

    /**
     * 标记幂等记录为 SUCCESS，并缓存响应报文。
     *
     * @param key          幂等 Key
     * @param tenantId     租户 UUID
     * @param responseBody 响应报文 JSON 文本
     * @param ttl          缓存保留时间
     */
    void complete(String key, UUID tenantId, String responseBody, Duration ttl);

    /**
     * 处理失败：将幂等状态置为 FAILED 或释放锁以便允许客户端重试。
     *
     * @param key          幂等 Key
     * @param tenantId     租户 UUID
     * @param errorMessage 失败原因描述
     */
    void fail(String key, UUID tenantId, String errorMessage);

    /**
     * 查询指定租户和 Key 的幂等执行记录。
     *
     * @param key      幂等 Key
     * @param tenantId 租户 UUID
     * @return 幂等记录 Optional
     */
    Optional<IdempotentRecord> getRecord(String key, UUID tenantId);
}
