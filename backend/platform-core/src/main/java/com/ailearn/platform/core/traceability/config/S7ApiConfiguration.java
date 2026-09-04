package com.ailearn.platform.core.traceability.config;

import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.infrastructure.PostgresDashboardCache;
import com.ailearn.platform.core.dashboard.ports.DashboardCache;
import com.ailearn.platform.core.dashboard.ports.InMemoryDashboardCache;
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
 * S7 只消费各领域 Facts 端口。缺少任一真实适配器时，不创建应用服务和 Controller，以免把空实现、
 * 零值或测试 fake 暴露为生产成功响应；接入全部事实 Bean 后由本配置自动启用。
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
                                                       IotFactsPort iotFacts) {
        return new GisApplicationService(store, inventoryFacts, manufacturingFacts, iotFacts);
    }

    /** 看板依赖全部摘要事实和追溯应用服务，缺一不可。 */
    @Bean
    @ConditionalOnMissingBean(DashboardApplicationService.class)
    @ConditionalOnBean({InventoryFactsQuery.class, PurchasingFactsQuery.class, SalesFactsQuery.class,
            ManufacturingFactsQuery.class, QualityFactsQuery.class, IotFactsPort.class,
            TraceabilityApplicationService.class, DashboardCache.class})
    public DashboardApplicationService dashboardApplicationService(
            InventoryFactsQuery inventoryFacts, PurchasingFactsQuery purchasingFacts,
            SalesFactsQuery salesFacts, ManufacturingFactsQuery manufacturingFacts,
            QualityFactsQuery qualityFacts, IotFactsPort iotFacts,
            TraceabilityApplicationService traceability, DashboardCache cache) {
        return new DashboardApplicationService(inventoryFacts, purchasingFacts, salesFacts,
                manufacturingFacts, qualityFacts, iotFacts, traceability, cache,
                java.time.Clock.systemUTC());
    }
}
