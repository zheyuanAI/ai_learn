package com.ailearn.platform.core.traceability.config;

import com.ailearn.platform.core.inventory.application.InventoryApplicationService;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.manufacturing.execution.domain.WorkOrderLifecycleRepository;
import com.ailearn.platform.core.manufacturing.foundation.domain.FoundationRepository;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionRepository;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactRepository;
import com.ailearn.platform.core.masterdata.domain.entity.Warehouse;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.purchasing.domain.PurchaseOrderRepository;
import com.ailearn.platform.core.quality.domain.PurchaseQualityRepository;
import com.ailearn.platform.core.sales.domain.SalesOrderRepository;
import com.ailearn.platform.core.traceability.infrastructure.CoreInventoryFactsAdapter;
import com.ailearn.platform.core.traceability.infrastructure.CoreManufacturingFactsAdapter;
import com.ailearn.platform.core.traceability.infrastructure.CorePurchasingFactsAdapter;
import com.ailearn.platform.core.traceability.infrastructure.CoreQualityFactsAdapter;
import com.ailearn.platform.core.traceability.infrastructure.CoreSalesFactsAdapter;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import com.ailearn.platform.core.traceability.ports.PurchasingFactsQuery;
import com.ailearn.platform.core.traceability.ports.QualityFactsQuery;
import com.ailearn.platform.core.traceability.ports.SalesFactsQuery;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core 内部 Facts 端口的生产装配。
 * <p>
 * S7 只依赖这些应用端口适配器，不直接注入 Mapper 或拼接跨领域 SQL；缺少任一生产来源时，
 * 对应端口不会被伪造为零值实现，最终由 S7 条件装配保持接口不可用。
 * </p>
 */
@Configuration(proxyBeanMethods = false)
public class CoreFactsAdapterConfiguration {

    /** 装配库存 Facts，复用库存查询应用服务和租户主数据仓储。 */
    @Bean
    @ConditionalOnMissingBean(InventoryFactsQuery.class)
    @ConditionalOnBean({InventoryQueryService.class, MasterDataRepository.class})
    public InventoryFactsQuery coreInventoryFacts(InventoryQueryService inventoryQuery,
                                                   MasterDataRepository<Warehouse> warehouses) {
        return new CoreInventoryFactsAdapter(inventoryQuery, warehouses);
    }

    /** 装配采购订单 Facts，复用采购订单仓储的租户过滤结果。 */
    @Bean
    @ConditionalOnMissingBean(PurchasingFactsQuery.class)
    @ConditionalOnBean(PurchaseOrderRepository.class)
    public PurchasingFactsQuery corePurchasingFacts(PurchaseOrderRepository repository) {
        return new CorePurchasingFactsAdapter(repository);
    }

    /** 装配销售履约 Facts，复用销售订单仓储及订单行累计量。 */
    @Bean
    @ConditionalOnMissingBean(SalesFactsQuery.class)
    @ConditionalOnBean(SalesOrderRepository.class)
    public SalesFactsQuery coreSalesFacts(SalesOrderRepository repository) {
        return new CoreSalesFactsAdapter(repository);
    }

    /** 装配采购质量 Facts，复用质量查询仓储。 */
    @Bean
    @ConditionalOnMissingBean(QualityFactsQuery.class)
    @ConditionalOnBean(PurchaseQualityRepository.class)
    public QualityFactsQuery coreQualityFacts(PurchaseQualityRepository repository) {
        return new CoreQualityFactsAdapter(repository);
    }

    /** 装配制造执行 Facts，复用基础工单、生命周期、工序和生产事实端口。 */
    @Bean
    @ConditionalOnMissingBean(ManufacturingFactsQuery.class)
    @ConditionalOnBean({FoundationRepository.class, WorkOrderLifecycleRepository.class,
            OperationExecutionRepository.class, ProductionFactRepository.class})
    public ManufacturingFactsQuery coreManufacturingFacts(FoundationRepository foundation,
                                                           WorkOrderLifecycleRepository lifecycleRepository,
                                                           OperationExecutionRepository operationRepository,
                                                           ProductionFactRepository productionRepository) {
        return new CoreManufacturingFactsAdapter(foundation, lifecycleRepository,
                operationRepository, productionRepository);
    }

    /** 显式启用 Core 到 IoT 的 HMAC 只读 Facts 适配器；默认关闭以避免错误配置伪造生产成功。 */
    @Bean
    @ConditionalOnMissingBean(IotFactsPort.class)
    @ConditionalOnProperty(prefix = "core.facts.iot", name = "enabled", havingValue = "true")
    public IotFactsPort coreIotFacts(
            @Value("${core.facts.iot.base-url:}") String baseUrl,
            @Value("${core.facts.iot.hmac-secret:}") String hmacSecret) {
        return new com.ailearn.platform.core.traceability.infrastructure.HttpIotFactsAdapter(baseUrl, hmacSecret);
    }
}
