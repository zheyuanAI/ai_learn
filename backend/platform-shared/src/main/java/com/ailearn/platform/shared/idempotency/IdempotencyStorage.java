package com.ailearn.platform.shared.idempotency;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
     * 竞争获取一次带所有权 token 的幂等执行。
     * 旧实现未实现该重载时仍可通过旧 boolean 接口工作，供历史测试/适配器兼容；生产实现必须覆写并执行 CAS。
     *
     * @param operation 操作域
     * @param key 原始幂等键
     * @param tenantId 可信租户
     * @param ttl PENDING 保留时间
     * @param requestHash 服务端载荷摘要
     * @return 成功占用时返回 claim，已占用时为空
     */
    default Optional<IdempotencyClaim> tryAcquireClaim(String operation, String key, UUID tenantId,
                                                        Duration ttl, String requestHash) {
        if (!tryAcquire(operation, key, tenantId, ttl, requestHash)) {
            return Optional.empty();
        }
        return Optional.of(new IdempotencyClaim(operation, key, tenantId, UUID.randomUUID()));
    }

    /**
     * 按 token、PENDING 状态完成幂等记录；旧实现默认转发到兼容入口。
     *
     * @return CAS 成功返回 true；明确拒绝旧 claim 返回 false
     */
    default boolean complete(String operation, String key, UUID tenantId, UUID claimToken,
                             String responseBody, Duration ttl) {
        complete(operation, key, tenantId, responseBody, ttl);
        return true;
    }

    /**
     * 按 token、PENDING 状态释放幂等记录；旧实现默认转发到兼容入口。
     *
     * @return CAS 成功返回 true；明确拒绝旧 claim 返回 false
     */
    default boolean fail(String operation, String key, UUID tenantId, UUID claimToken,
                         String errorMessage) {
        fail(operation, key, tenantId, errorMessage);
        return true;
    }

    /**
     * 按业务操作域获取幂等执行锁。
     * <p>
     * 兼容实现默认把操作域编码进存储键；数据库实现可以覆写该重载，以便同时写入 operation 列。
     *
     * @param operation 操作域，例如 {@code inventory:increase}
     * @param key 原始 HTTP 幂等键
     * @param tenantId 可信租户
     * @param ttl 幂等窗口
     * @param requestHash 服务端载荷摘要
     * @return 是否成功占用
     */
    default boolean tryAcquire(String operation, String key, UUID tenantId,
                                Duration ttl, String requestHash) {
        return tryAcquire(scopedKey(operation, key), tenantId, ttl, requestHash);
    }

    /**
     * 按业务操作域完成幂等记录。
     *
     * @param operation 操作域
     * @param key 原始 HTTP 幂等键
     * @param tenantId 可信租户
     * @param responseBody 成功响应
     * @param ttl 成功结果保留时间
     */
    default void complete(String operation, String key, UUID tenantId,
                          String responseBody, Duration ttl) {
        complete(scopedKey(operation, key), tenantId, responseBody, ttl);
    }

    /**
     * 按业务操作域释放幂等占用。
     *
     * @param operation 操作域
     * @param key 原始 HTTP 幂等键
     * @param tenantId 可信租户
     * @param errorMessage 失败原因
     */
    default void fail(String operation, String key, UUID tenantId, String errorMessage) {
        fail(scopedKey(operation, key), tenantId, errorMessage);
    }

    /**
     * 按业务操作域读取幂等记录。
     *
     * @param operation 操作域
     * @param key 原始 HTTP 幂等键
     * @param tenantId 可信租户
     * @return 操作域内的幂等记录
     */
    default Optional<IdempotentRecord> getRecord(String operation, String key, UUID tenantId) {
        return getRecord(scopedKey(operation, key), tenantId);
    }

    /**
     * 为未覆写操作域的实现生成稳定且不超过数据库列长度的键。
     * 入参：操作域和原始幂等键；出参：不会与其他操作域混淆的存储键；流程：短键保留可读前缀，
     * 超长键使用 SHA-256 尾部避免截断碰撞。
     *
     * @param operation 操作域
     * @param key 原始幂等键
     * @return 操作域作用域键
     */
    static String scopedKey(String operation, String key) {
        String normalizedOperation = operation == null || operation.isBlank() ? "default" : operation.trim();
        String normalizedKey = key == null ? "" : key;
        String combined = normalizedOperation + "|" + normalizedKey;
        if (combined.length() <= 128) {
            return combined;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(combined.getBytes(StandardCharsets.UTF_8));
            String suffix = java.util.HexFormat.of().formatHex(digest);
            int prefixLength = Math.max(1, 128 - suffix.length() - 1);
            return combined.substring(0, prefixLength) + "|" + suffix;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }

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
