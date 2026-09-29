package com.ailearn.platform.auth;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ailearn.platform.auth.config.JwtProperties;
import com.ailearn.platform.auth.domain.dto.LoginRequest;
import com.ailearn.platform.auth.domain.dto.admin.TenantUpdateRequest;
import com.ailearn.platform.auth.domain.entity.Tenant;
import com.ailearn.platform.auth.domain.entity.UserSession;
import com.ailearn.platform.auth.mapper.MenuMapper;
import com.ailearn.platform.auth.mapper.PermissionMapper;
import com.ailearn.platform.auth.mapper.TenantMapper;
import com.ailearn.platform.auth.mapper.UserMapper;
import com.ailearn.platform.auth.mapper.UserSessionMapper;
import com.ailearn.platform.auth.security.jwt.JwtTokenService;
import com.ailearn.platform.auth.service.SessionCacheService;
import com.ailearn.platform.auth.service.admin.impl.TenantAdminServiceImpl;
import com.ailearn.platform.auth.service.impl.AuthServiceImpl;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.exception.AuthException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 租户停用和登录争用同一租户行锁的调用顺序回归，不使用开发数据库或 Redis。 */
class TenantDisableLoginLockTest {
    private static final UUID TENANT_ID = UUID.randomUUID();

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    /** 已读取 ACTIVE 的登录在获得租户锁后须重读状态；若停用已提交，则不查询账号、不创建会话。 */
    @Test
    void staleLoginRejectsDisabledTenantAfterLock() {
        TenantMapper tenants = mock(TenantMapper.class);
        UserMapper users = mock(UserMapper.class);
        UserSessionMapper sessions = mock(UserSessionMapper.class);
        SessionCacheService cache = mock(SessionCacheService.class);
        Tenant active = new Tenant(TENANT_ID, "TEST", "测试租户", "ACTIVE");
        Tenant disabled = new Tenant(TENANT_ID, "TEST", "测试租户", "DISABLED");
        when(cache.isRedisAvailable()).thenReturn(true);
        when(tenants.findByTenantCode("TEST")).thenReturn(active);
        when(users.lockTenantForAdminMutation(TENANT_ID)).thenReturn(TENANT_ID);
        when(tenants.selectById(TENANT_ID)).thenReturn(disabled);
        AuthServiceImpl service = new AuthServiceImpl(tenants, users, mock(PermissionMapper.class),
                mock(MenuMapper.class), sessions, mock(PasswordEncoder.class), mock(JwtTokenService.class),
                mock(JwtProperties.class), cache);

        assertThrows(AuthException.class, () -> service.login(new LoginRequest("TEST", "admin", "password"), null, null));
        InOrder order = inOrder(tenants, users);
        order.verify(tenants).findByTenantCode("TEST");
        order.verify(users).lockTenantForAdminMutation(TENANT_ID);
        order.verify(tenants).selectById(TENANT_ID);
        verify(users, never()).findByTenantIdAndUsername(any(), any());
        verify(sessions, never()).insert(any(UserSession.class));
        verify(cache, never()).saveActiveSession(any(), any(), any(), any());
    }

    /** 停用事务必须先获得与登录共用的租户锁，再撤销会话并更新租户状态。 */
    @Test
    void disableLocksTenantBeforeRevokingSessions() {
        TenantMapper tenants = mock(TenantMapper.class);
        UserMapper users = mock(UserMapper.class);
        UserSessionMapper sessions = mock(UserSessionMapper.class);
        SessionCacheService cache = mock(SessionCacheService.class);
        Tenant active = new Tenant(TENANT_ID, "TEST", "测试租户", "ACTIVE");
        when(users.lockTenantForAdminMutation(TENANT_ID)).thenReturn(TENANT_ID);
        when(tenants.selectById(TENANT_ID)).thenReturn(active);
        when(users.selectList(any())).thenReturn(List.of());
        TenantContextHolder.setTenantId(TENANT_ID);
        TenantAdminServiceImpl service = new TenantAdminServiceImpl(tenants, users, sessions, cache);

        service.updateCurrentTenant(new TenantUpdateRequest("测试租户", "DISABLED"));

        InOrder order = inOrder(users, tenants, sessions);
        order.verify(users).lockTenantForAdminMutation(TENANT_ID);
        order.verify(tenants).selectById(TENANT_ID);
        order.verify(sessions).revokeActiveSessionsByTenantId(eq(TENANT_ID), any(LocalDateTime.class), eq("TENANT_DISABLED"));
        order.verify(tenants).updateById(active);
    }
}
