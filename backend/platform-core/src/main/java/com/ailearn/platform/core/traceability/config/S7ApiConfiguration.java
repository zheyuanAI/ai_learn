package com.ailearn.platform.core.traceability.config;

import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.infrastructure.PostgresDashboardCache;
import com.ailearn.platform.core.dashboard.ports.DashboardCache;
import com.ailearn.platform.core.dashboard.ports.InMemoryDashboardCache;
import com.ailearn.platform.core.dashboard.exceptioncenter.application.ExceptionCenterApplicationService;
import com.ailearn.platform.core.dashboard.exceptioncenter.application.ExceptionCenterApplicationServiceImpl;
import com.ailearn.platform.core.gis.application.GisApplicationService;
import com.ailearn.platform.core.gis.ports.GisConfigurationStore;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import com.ailearn.platform.core.traceability.ports.PurchasingFactsQuery;
import com.ailearn.platform.core.traceability.ports.QualityFactsQuery;
import com.ailearn.platform.core.traceability.ports.SalesFactsQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ailearn.platform.shared.idempotency.IdempotencyStorage;
import com.ailearn.platform.shared.idempotency.InMemoryIdempotencyStorage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * S7 查询应用服务的条件装配。
 * <p>
 * S7 只消费各领域 Facts 端口。追溯和 GIS 缺少必需适配器时保持不装配；看板入口始终存在，
 * 每个摘要只检查自己的事实来源并独立返回陈旧投影或受控错误，禁止用零值或测试 fake 伪装成功。
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class S7ApiConfiguration {

    /** 提供只保存可丢弃投影的缓存；有 JDBC 时使用 PostgreSQL，测试或无数据库时退回内存。 */
    @Bean
    @ConditionalOnMissingBean(DashboardCache.class)
    public DashboardCache dashboardCache(ObjectProvider<JdbcTemplate> jdbcProvider,
                                          ObjectProvider<ObjectMapper> mapperProvider) {
        JdbcTemplate jdbcTemplate = jdbcProvider.getIfAvailable();
        if (jdbcTemplate == null) {
            return new InMemoryDashboardCache();
        }
        ObjectMapper mapper = mapperProvider.getIfAvailable(() ->
                new ObjectMapper().registerModule(new JavaTimeModule()));
        return new PostgresDashboardCache(jdbcTemplate, mapper);
    }

    /** 只有所有跨域事实端口可用时才装配追溯应用服务。 */
    @Bean
    @ConditionalOnMissingBean(TraceabilityApplicationService.class)
    @ConditionalOnBean({InventoryFactsQuery.class, PurchasingFactsQuery.class, SalesFactsQuery.class,
            ManufacturingFactsQuery.class, QualityFactsQuery.class, IotFactsPort.class})
    public TraceabilityApplicationService traceabilityApplicationService(
            InventoryFactsQuery inventoryFacts, PurchasingFactsQuery purchasingFacts,
            SalesFactsQuery salesFacts, ManufacturingFactsQuery manufacturingFacts,
            QualityFactsQuery qualityFacts, IotFactsPort iotFacts) {
        return new TraceabilityApplicationService(inventoryFacts, purchasingFacts, salesFacts,
                manufacturingFacts, qualityFacts, iotFacts);
    }

    /** GIS 只在自有配置存储和全部引用事实端口可用时装配。 */
    @Bean
    @ConditionalOnMissingBean(GisApplicationService.class)
    @ConditionalOnBean({GisConfigurationStore.class, InventoryFactsQuery.class,
            ManufacturingFactsQuery.class, IotFactsPort.class})
    public GisApplicationService gisApplicationService(GisConfigurationStore store,
                                                       InventoryFactsQuery inventoryFacts,
                                                       ManufacturingFactsQuery manufacturingFacts,
                                                       IotFactsPort iotFacts,
                                                       ObjectProvider<IdempotencyStorage> storageProvider,
                                                       ObjectProvider<ObjectMapper> mapperProvider) {
        IdempotencyStorage storage = storageProvider.getIfAvailable(InMemoryIdempotencyStorage::new);
        ObjectMapper mapper = mapperProvider.getIfAvailable(() ->
                new ObjectMapper().registerModule(new JavaTimeModule()));
        return new GisApplicationService(store, inventoryFacts, manufacturingFacts, iotFacts,
                java.time.Clock.systemUTC(), storage, mapper);
    }

    /**
     * 看板入口始终装配，各摘要在查询时只检查自己的事实端口。
     * 缺失的设备、告警或追溯来源只让对应接口返回受控不可用，不连带关闭 Core 内部摘要。
     */
    @Bean
    @ConditionalOnMissingBean(DashboardApplicationService.class)
    public DashboardApplicationService dashboardApplicationService(
            ObjectProvider<InventoryFactsQuery> inventoryFacts,
            ObjectProvider<PurchasingFactsQuery> purchasingFacts,
            ObjectProvider<SalesFactsQuery> salesFacts,
            ObjectProvider<ManufacturingFactsQuery> manufacturingFacts,
            ObjectProvider<QualityFactsQuery> qualityFacts,
            ObjectProvider<IotFactsPort> iotFacts,
            ObjectProvider<TraceabilityApplicationService> traceability,
            DashboardCache cache) {
        return new DashboardApplicationService(inventoryFacts.getIfAvailable(),
                purchasingFacts.getIfAvailable(), salesFacts.getIfAvailable(),
                manufacturingFacts.getIfAvailable(), qualityFacts.getIfAvailable(),
                iotFacts.getIfAvailable(), traceability.getIfAvailable(), cache,
                java.time.Clock.systemUTC());
    }

    /** 全部三类源 Facts 可用时装配异常中心；异常记录由实时摘要派生，不新增事实表。 */
    @Bean
    @ConditionalOnMissingBean(ExceptionCenterApplicationService.class)
    @ConditionalOnBean({InventoryFactsQuery.class, ManufacturingFactsQuery.class, IotFactsPort.class})
    public ExceptionCenterApplicationService exceptionCenterApplicationService(
            InventoryFactsQuery inventoryFacts, ManufacturingFactsQuery manufacturingFacts,
            IotFactsPort iotFacts) {
        return new ExceptionCenterApplicationServiceImpl(inventoryFacts, manufacturingFacts, iotFacts);
    }
}
