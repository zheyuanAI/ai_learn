package com.ailearn.platform.shared.security;

import com.ailearn.platform.shared.constants.HeaderConstants;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.UserContext;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.Set;
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

        // 测试权限必须来自受控读取器，不能来自客户端 Header。
        filter = new DownstreamSecurityFilter(
                (ignoredTenantId, ignoredUserId) -> Set.of("ROLE_WAREHOUSE", "inventory:balance:view", "inventory:stock:transfer"),
                new ObjectMapper().registerModule(new JavaTimeModule()));

        request.addHeader(HeaderConstants.X_USER_ID, userId);
        request.addHeader(HeaderConstants.X_TENANT_ID, tenantId);
        request.addHeader(HeaderConstants.X_USERNAME, username);
        request.addHeader(HeaderConstants.X_SESSION_ID, sessionId);
        request.addHeader(HeaderConstants.X_REQUEST_ID, requestId);

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

    @Test
    @DisplayName("测试客户端伪造的权限、角色 Header 不得恢复为下游权限")
    void testClientSuppliedPermissionHeadersAreIgnored() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(HeaderConstants.X_USER_ID, UUID.randomUUID().toString());
        request.addHeader(HeaderConstants.X_TENANT_ID, UUID.randomUUID().toString());
        request.addHeader(HeaderConstants.X_USERNAME, "forged-user");
        request.addHeader(HeaderConstants.X_SESSION_ID, UUID.randomUUID().toString());
        request.addHeader(HeaderConstants.X_AUTHORITIES, "ROLE_TENANT_ADMIN,inventory:balance:view");
        request.addHeader(HeaderConstants.X_PERMISSIONS, "inventory:balance:view");
        request.addHeader(HeaderConstants.X_ROLES, "TENANT_ADMIN");

        FilterChain filterChain = (req, res) -> {
            // 该测试验证输入边界：客户端 Header 只能表达身份，不能成为权限来源。
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(authentication, "有身份请求仍应建立认证上下文");
            assertTrue(authentication.getAuthorities().isEmpty(), "伪造权限 Header 不得进入 SecurityContext");
            assertFalse(UserContextHolder.hasRole("TENANT_ADMIN"));
            assertFalse(UserContextHolder.hasPermission("inventory:balance:view"));
        };

        filter.doFilter(request, response, filterChain);
    }

    @Test
    @DisplayName("测试权限缓存异常时返回 503 且不调用业务链")
    void testPermissionCacheFailureFailsClosedBeforeBusinessChain() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        filter = new DownstreamSecurityFilter(
                (ignoredTenantId, ignoredUserId) -> {
                    throw new com.ailearn.platform.shared.exception.ServiceUnavailableException("权限缓存不可用");
                },
                new ObjectMapper().registerModule(new JavaTimeModule()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstants.X_USER_ID, userId.toString());
        request.addHeader(HeaderConstants.X_TENANT_ID, tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        final boolean[] businessCalled = {false};
        filter.doFilter(request, response, (req, res) -> businessCalled[0] = true);

        assertEquals(503, response.getStatus());
        assertFalse(businessCalled[0], "权限缓存异常时不得调用业务方法");
    }
}
