package com.ailearn.platform.shared.config;

import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import com.ailearn.platform.shared.security.RedisPermissionContextReader;
import com.ailearn.platform.shared.security.PermissionContextReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Platform-Shared 公共模块 Spring Boot 3 自动配置根入口。
 * <p>
 * 导入公共 Jackson 配置，并提供基础默认的内存幂等存储 Bean。
 * </p>
 */
@AutoConfiguration
@Import(JacksonConfig.class)
public class SharedAutoConfiguration {

    /**
     * 提供默认的内存版幂等存储策略实现（当外部未配置 Redis 等集中式存储时启用）。
     *
     * @return 内存幂等存储实现
     */
    @Bean
    @ConditionalOnMissingBean(IdempotencyStorage.class)
    public IdempotencyStorage idempotencyStorage() {
        return new InMemoryIdempotencyStorage();
    }

    /**
     * 装配下游权限读取端口。
     * 入参为可选 Redis 与 JSON 依赖；存在 Redis 时使用严格读取器，否则装配一个始终 503 的 Fail-Closed 实现。
     *
     * @param redisTemplate Redis 字符串模板，可为空
     * @param objectMapper JSON 对象映射器
     * @return 权限上下文读取器
     */
    @Bean
    @ConditionalOnMissingBean(PermissionContextReader.class)
    public PermissionContextReader permissionContextReader(
            @org.springframework.lang.Nullable StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        if (redisTemplate != null) {
            return new RedisPermissionContextReader(redisTemplate, objectMapper);
        }
        return (tenantId, userId) -> {
            throw new com.ailearn.platform.shared.exception.ServiceUnavailableException(
                    "权限服务暂时不可用，请稍后重试");
        };
    }
}
