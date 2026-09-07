package com.ailearn.platform.core.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core 持久层基础配置。
 * <p>
 * 仅扫描已冻结的业务 Mapper 包，避免把应用端口误注册成 MyBatis Mapper；分页插件统一使用 PostgreSQL 方言。
 * </p>
 */
@Configuration
@MapperScan(
        basePackages = {
                "com.ailearn.platform.core.masterdata.infrastructure.mapper",
                "com.ailearn.platform.core.inventory.infrastructure",
                "com.ailearn.platform.core.transfer.infrastructure",
                "com.ailearn.platform.core.stocktake.infrastructure"
        },
        annotationClass = Mapper.class)
public class CoreMyBatisConfig {

    /**
     * 配置 Core 查询所需的 MyBatis-Plus 分页拦截器。
     * 入参：无；出参：分页拦截器；流程：注册 PostgreSQL 方言后返回给 Spring 容器。
     *
     * @return MyBatis-Plus 分页拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    /**
     * 注册 Core 统一 UUID 类型处理器。
     * 入参：MyBatis 全局配置；出参：无；流程：把 PostgreSQL/H2 UUID 映射注册到类型处理器仓库，供所有 Core Mapper 复用。
     *
     * @return MyBatis 配置定制器
     */
    @Bean
    public ConfigurationCustomizer uuidTypeHandlerCustomizer() {
        return configuration -> configuration.getTypeHandlerRegistry()
                .register(UUID.class, new UuidTypeHandler());
    }
}
