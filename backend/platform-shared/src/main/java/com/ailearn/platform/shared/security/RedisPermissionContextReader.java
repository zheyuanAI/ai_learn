package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

/**
 * 基于 Redis 的权限快照读取器。
 * <p>
 * 权限缓存是下游授权的硬依赖：未命中、连接故障、超时、非法 JSON 或非数组 JSON 都必须 Fail-Closed 为 503。
 * </p>
 */
public class RedisPermissionContextReader implements PermissionContextReader {

    private static final String KEY_PREFIX = "auth:perms:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 创建 Redis 权限读取器。
     *
     * @param redisTemplate 字符串 Redis 操作模板
     * @param objectMapper JSON 解析器
     */
    public RedisPermissionContextReader(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 读取租户用户权限数组并严格校验 JSON 结构。
     * 入参为受信任租户/用户 UUID，出参为不可变权限集合；流程是读取固定键、验证根节点为数组、校验元素为非空字符串。
     *
     * @param tenantId 租户 ID
     * @param userId 用户 ID
     * @return 有效权限集合，`[]` 表示已认证但没有权限
     * @throws ServiceUnavailableException 缓存未命中、连接异常或 JSON 结构非法
     */
    @Override
    public Set<String> readPermissions(UUID tenantId, UUID userId) {
        String key = KEY_PREFIX + tenantId + ":" + userId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (!StringUtils.hasText(json)) {
                throw unavailable("权限缓存未命中", null);
            }

            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isArray()) {
                throw unavailable("权限缓存格式非法，必须为 JSON 数组", null);
            }

            Set<String> permissions = new LinkedHashSet<>();
            for (JsonNode item : root) {
                if (item == null || !item.isTextual() || !StringUtils.hasText(item.asText())) {
                    throw unavailable("权限缓存元素格式非法", null);
                }
                permissions.add(item.asText().trim());
            }
            return permissions.isEmpty()
                    ? Collections.emptySet()
                    : Collections.unmodifiableSet(permissions);
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unavailable("权限服务暂时不可用，请稍后重试", ex);
        }
    }

    /**
     * 创建统一的 503 异常，避免把 Redis/JSON 内部细节泄露给客户端。
     *
     * @param message 内部可审计的稳定提示
     * @param cause 原始异常
     * @return 503 异常
     */
    private ServiceUnavailableException unavailable(String message, Throwable cause) {
        return cause == null
                ? new ServiceUnavailableException(message)
                : new ServiceUnavailableException(message, cause);
    }
}
