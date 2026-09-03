package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.constants.HeaderConstants;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.UserContext;
import com.ailearn.platform.shared.context.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("下游安全过滤器 DownstreamSecurityFilter 测试")
class DownstreamSecurityFilterTest {

    private DownstreamSecurityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new DownstreamSecurityFilter();
        UserContextHolder.clear();
        RequestContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        UserContextHolder.clear();
        RequestContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("测试当携带完整身份 Header 时成功还原 UserContext 与 SecurityContext")
    void testFilterWithValidHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String userId = UUID.randomUUID().toString();
        String tenantId = UUID.randomUUID().toString();
        String username = "wms_operator";
        String sessionId = UUID.randomUUID().toString();
        String requestId = "req-123456";
        String authorities = "ROLE_WAREHOUSE,inventory:balance:view,inventory:stock:transfer";

        request.addHeader(HeaderConstants.X_USER_ID, userId);
        request.addHeader(HeaderConstants.X_TENANT_ID, tenantId);
        request.addHeader(HeaderConstants.X_USERNAME, username);
        request.addHeader(HeaderConstants.X_SESSION_ID, sessionId);
        request.addHeader(HeaderConstants.X_REQUEST_ID, requestId);
        request.addHeader(HeaderConstants.X_AUTHORITIES, authorities);

        FilterChain filterChain = (req, res) -> {
            // 验证在 FilterChain 内部，用户上下文与 Spring Security 认证均已正确注入
            UserContext context = UserContextHolder.get();
            assertNotNull(context, "UserContext 应非空");
            assertEquals(userId, context.getUserId());
            assertEquals(tenantId, context.getTenantId());
            assertEquals(username, context.getUsername());
            assertEquals(sessionId, context.getSessionId());
            assertEquals(requestId, context.getRequestId());
            assertTrue(context.hasRole("WAREHOUSE"));
            assertTrue(context.hasPermission("inventory:balance:view"));
            assertTrue(context.hasAuthority("inventory:stock:transfer"));
            assertFalse(context.hasPermission("purchase:order:delete"));

            // 验证 Spring Security Context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication, "Authentication 应非空");
            assertTrue(authentication.isAuthenticated());
            assertEquals(username, authentication.getName());
            assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_WAREHOUSE")));
            assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("inventory:balance:view")));
        };

        filter.doFilter(request, response, filterChain);

        // 验证响应头携带 X-Request-Id
        assertEquals(requestId, response.getHeader(HeaderConstants.X_REQUEST_ID));

        // 验证 Filter 结束后 ThreadLocal 与 SecurityContext 均已被自动清理
        assertNull(UserContextHolder.get(), "请求结束后 UserContextHolder 应被清理");
        assertNull(RequestContextHolder.getNullableContext(), "请求结束后 RequestContextHolder 应被清理");
        assertNull(SecurityContextHolder.getContext().getAuthentication(), "请求结束后 SecurityContext 应被清理");
    }

    @Test
    @DisplayName("测试未携带用户身份 Header 时不会注入身份认证并生成 Request-Id")
    void testFilterWithoutHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {
            assertNull(UserContextHolder.get());
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            assertNotNull(RequestContextHolder.getRequestId());
        };

        filter.doFilter(request, response, filterChain);

        assertNotNull(response.getHeader(HeaderConstants.X_REQUEST_ID), "应自动生成 X-Request-Id 响应头");
        assertNull(UserContextHolder.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
