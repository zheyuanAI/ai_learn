package com.ailearn.platform.core.operationaudit;

import com.ailearn.platform.core.operationaudit.application.OperationAuditApplicationService;
import com.ailearn.platform.core.operationaudit.application.OperationAuditQuery;
import com.ailearn.platform.core.operationaudit.controller.OperationAuditController;
import com.ailearn.platform.shared.context.RequestContextHolder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 通过真实 Spring 方法权限代理验证采购/销售操作记录不能相互越权查询。 */
class OperationAuditControllerPermissionTest {
    private AnnotationConfigApplicationContext context;
    private OperationAuditController controller;
    private OperationAuditApplicationService applicationService;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();

    /** 建立最小方法安全容器及可信租户，不启动 HTTP 服务或数据库。 */
    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(OperationAuditController.class);
        applicationService = context.getBean(OperationAuditApplicationService.class);
        RequestContextHolder.getContext().setTenantId(tenantId);
    }

    /** 清除线程身份并关闭测试容器，避免污染后续账号用例。 */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.clear();
        context.close();
    }

    /** 采购查看权限仅能读取采购实体，规范化参数不丢失可信租户。 */
    @Test
    void purchasePermissionQueriesNormalizedPurchaseEntity() {
        authenticate("pur:order:view");
        when(applicationService.query(any())).thenReturn(List.of());
        controller.query(" purchase_order ", entityId, null, null, 50);
        verify(applicationService).query(new OperationAuditQuery(tenantId, "PURCHASE_ORDER", entityId,
                null, null, 50));
    }

    /** 销售查看权限不能查看采购记录，拒绝发生在应用服务查询前。 */
    @Test
    void salesPermissionCannotQueryPurchase() {
        authenticate("sales:order:view");
        assertThrows(AccessDeniedException.class,
                () -> controller.query("PURCHASE_ORDER", entityId, null, null, 50));
        verifyNoInteractions(applicationService);
    }

    /** 采购查看权限不能查看销售记录，拒绝发生在应用服务查询前。 */
    @Test
    void purchasePermissionCannotQuerySales() {
        authenticate("pur:order:view");
        assertThrows(AccessDeniedException.class,
                () -> controller.query("SALES_ORDER", entityId, null, null, 50));
        verifyNoInteractions(applicationService);
    }

    /** 已有销售读取能力在扩展采购后继续生效。 */
    @Test
    void salesPermissionStillQueriesSales() {
        authenticate("sales:order:view");
        when(applicationService.query(any())).thenReturn(List.of());
        controller.query("SALES_ORDER", entityId, null, null, 50);
        verify(applicationService).query(new OperationAuditQuery(tenantId, "SALES_ORDER", entityId,
                null, null, 50));
    }

    /** 注入指定业务查看权限；入参为权限编码，出参为无，不创建真实会话。 */
    private void authenticate(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-account", null, List.of(new SimpleGrantedAuthority(authority))));
    }

    @Configuration
    @EnableMethodSecurity
    static class Config {
        /** 返回只用于观察调用的应用服务替身。 */
        @Bean
        OperationAuditApplicationService applicationService() {
            return mock(OperationAuditApplicationService.class);
        }

        /** 创建待验证的正式 Controller，由 Spring 注入方法安全代理。 */
        @Bean
        OperationAuditController controller(OperationAuditApplicationService applicationService) {
            return new OperationAuditController(applicationService);
        }
    }
}
