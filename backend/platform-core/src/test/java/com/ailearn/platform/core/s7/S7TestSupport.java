package com.ailearn.platform.core.s7;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/** S7 测试上下文构造器，明确模拟认证边界已经提供的可信租户和权限。 */
final class S7TestSupport {
    private S7TestSupport() {
    }

    static FactsQueryContext context(UUID tenantId, String fingerprint, String... permissions) {
        return new FactsQueryContext(tenantId, fingerprint, Set.of(permissions),
                ZoneId.of("Asia/Shanghai"), "request-s7");
    }
}
