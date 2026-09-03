package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.context.UserContext;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MethodSecurityIntegrationTest.TestConfig.class)
@DisplayName("Spring Security 方法级权限校验 @PreAuthorize 测试")
class MethodSecurityIntegrationTest {

    @Configuration
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestConfig {
        @Bean
        public SecuredTestService securedTestService() {
            return new SecuredTestService();
        }
    }

    public static class SecuredTestService {
        @PreAuthorize("hasAuthority('purchase:order:approve')")
        public String approvePurchaseOrder(String orderId) {
            return "Approved: " + orderId;
        }

        @PreAuthorize("hasRole('TENANT_ADMIN')")
        public String adminOperation() {
            return "Admin Success";
        }
    }

    @Autowired
    private SecuredTestService service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("测试具备对应权限时方法正常执行")
    void testAuthorizedAccess() {
        UserContext context = new UserContext(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "test-user",
                "jti-1",
                "req-1",
                Set.of("purchase:order:approve", "ROLE_TENANT_ADMIN")
        );
        UserAuthenticationToken auth = new UserAuthenticationToken(
                context,
                List.of(new SimpleGrantedAuthority("purchase:order:approve"), new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        String result = service.approvePurchaseOrder("PO-1001");
        assertEquals("Approved: PO-1001", result);

        String adminResult = service.adminOperation();
        assertEquals("Admin Success", adminResult);
    }

    @Test
    @DisplayName("测试缺少对应权限时抛出 AccessDeniedException")
    void testUnauthorizedAccess() {
        UserContext context = new UserContext(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "test-user",
                "jti-2",
                "req-2",
                Set.of("sales:order:create")
        );
        UserAuthenticationToken auth = new UserAuthenticationToken(
                context,
                List.of(new SimpleGrantedAuthority("sales:order:create"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(AccessDeniedException.class, () -> service.approvePurchaseOrder("PO-1002"));
        assertThrows(AccessDeniedException.class, () -> service.adminOperation());
    }
}
