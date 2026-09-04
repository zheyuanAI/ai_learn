package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.controller.DashboardController;
import com.ailearn.platform.core.dashboard.ports.DashboardCache;
import com.ailearn.platform.core.gis.application.GisApplicationService;
import com.ailearn.platform.core.gis.controller.GisController;
import com.ailearn.platform.core.gis.ports.GisConfigurationStore;
import com.ailearn.platform.core.gis.ports.InMemoryGisConfigurationStore;
import com.ailearn.platform.core.traceability.application.TraceabilityApplicationService;
import com.ailearn.platform.core.traceability.config.S7ApiConfiguration;
import com.ailearn.platform.core.traceability.controller.TraceabilityController;
import com.ailearn.platform.core.traceability.web.TrustedFactsQueryContextFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 缺少真实 Facts 适配器时 S7 不创建可返回伪造成功数据的 Bean。 */
class S7ApiConfigurationTest {

    @Test
    void shouldKeepS7ServicesAndControllersAbsentUntilAllFactsAdaptersAreAvailable() {
        new WebApplicationContextRunner()
                .withUserConfiguration(S7ApiConfiguration.class, GisController.class,
                        TraceabilityController.class, DashboardController.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GisApplicationService.class);
                    assertThat(context).doesNotHaveBean(TraceabilityApplicationService.class);
                    assertThat(context).doesNotHaveBean(DashboardApplicationService.class);
                    assertThat(context).doesNotHaveBean(GisController.class);
                    assertThat(context).doesNotHaveBean(TraceabilityController.class);
                    assertThat(context).doesNotHaveBean(DashboardController.class);
                    assertThat(context).hasSingleBean(DashboardCache.class);
                });
    }

    @Test
    void shouldAssembleS7ServicesWhenAllFactsPortsAreExplicitlyProvided() {
        new WebApplicationContextRunner()
                .withBean(S7FactsFake.class)
                .withBean(GisConfigurationStore.class, InMemoryGisConfigurationStore::new)
                .withUserConfiguration(S7ApiConfiguration.class, GisController.class,
                        TraceabilityController.class, DashboardController.class,
                        TrustedFactsQueryContextFactory.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(GisApplicationService.class);
                    assertThat(context).hasSingleBean(TraceabilityApplicationService.class);
                    assertThat(context).hasSingleBean(DashboardApplicationService.class);
                    assertThat(context).hasSingleBean(GisController.class);
                    assertThat(context).hasSingleBean(TraceabilityController.class);
                    assertThat(context).hasSingleBean(DashboardController.class);
                });
    }
}
