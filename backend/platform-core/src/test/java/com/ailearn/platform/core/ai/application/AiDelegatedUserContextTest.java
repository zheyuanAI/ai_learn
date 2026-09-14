package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 委托上下文只在当前工具同步调用内有效，结束后必须清除所有线程状态。 */
class AiDelegatedUserContextTest {

    @AfterEach
    void clear() {
        UserContextHolder.clear();
        RequestContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldBindTrustedIdentityAndAlwaysClearAfterCall() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AiRequestContext context = new AiRequestContext(tenantId, userId, "jti-1",
                Set.of("sales:order:view"), ZoneId.of("Asia/Shanghai"), "fingerprint", "request-1");

        String result = new AiDelegatedUserContext().call(context, () -> {
            assertEquals(tenantId, TenantContextHolder.requireTenantId());
            assertEquals(userId, UserContextHolder.requireUserId());
            assertTrue(UserContextHolder.hasPermission("sales:order:view"));
            assertTrue(SecurityContextHolder.getContext().getAuthentication().isAuthenticated());
            return "ok";
        });

        assertEquals("ok", result);
        assertNull(RequestContextHolder.getNullableContext());
        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
    }
}
