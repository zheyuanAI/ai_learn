package com.ailearn.platform.shared.config;

import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

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
}
