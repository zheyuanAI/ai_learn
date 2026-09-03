package com.ailearn.platform.auth.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.LocalDateTime;
import java.util.UUID;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 核心配置与 Mapper 扫描。
 */
@Configuration
@MapperScan("com.ailearn.platform.auth.mapper")
public class MyBatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 插件拦截器（含分页插件）。
     *
     * @return MybatisPlusInterceptor 实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    /**
     * 自定义注册 UUID 类型处理器，统一处理 PostgreSQL / H2 UUID 类型映射。
     *
     * @return ConfigurationCustomizer
     */
    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            configuration.getTypeHandlerRegistry().register(UUID.class, new UuidTypeHandler());
            // PostgreSQL 使用 TIMESTAMPTZ，不能直接映射到项目沿用的 LocalDateTime。
            configuration.getTypeHandlerRegistry().register(
                    LocalDateTime.class,
                    new TimestampWithTimeZoneLocalDateTimeTypeHandler()
            );
        };
    }
}
