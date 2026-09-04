package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.web.TrustedFactsQueryContextFactory;
import com.ailearn.platform.shared.context.RequestContext;
import com.ailearn.platform.shared.context.RequestContextHolder;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S7 Controller 上下文只从共享认证上下文取租户和权限的 focused 测试。 */
class TrustedFactsQueryContextFactoryTest {
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @AfterEach
    void clearContext() {
        RequestContextHolder.clear();
    }

    @Test
    void shouldBuildStableServerSidePermissionFingerprintAndIgnoreClientTenantConcept() {
        RequestContext context = new RequestContext();
        context.setTenantId(TENANT);
        context.setUserId(UUID.randomUUID());
        context.setPermissions(Set.of("gis:map:view", "trace:chain:view"));
        context.setRequestId("request-s7");
        RequestContextHolder.setContext(context);

        TrustedFactsQueryContextFactory factory = new TrustedFactsQueryContextFactory();
        FactsQueryContext first = factory.current();
        context.setPermissions(Set.of("trace:chain:view", "gis:map:view"));
        FactsQueryContext reordered = factory.current();

        assertEquals(TENANT, first.tenantId());
        assertEquals(Set.of("gis:map:view", "trace:chain:view"), first.permissions());
        assertEquals("request-s7", first.requestId());
        assertEquals(first.permissionFingerprint(), reordered.permissionFingerprint());
        assertTrue(first.permissionFingerprint().matches("[0-9a-f]{64}"));
        assertNotEquals("client-tenant", first.tenantId().toString());
    }
}
