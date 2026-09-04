package com.ailearn.platform.core.traceability.web;

import java.time.ZoneId;
import java.util.UUID;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 从服务端配置读取租户时区。
 * <p>
 * 配置键为 {@code platform.tenant-zones.<tenant_uuid>}，缺省使用
 * {@code platform.default-time-zone}；这样在租户资料表尚未提供时也不会把客户端值当作事实。
 * </p>
 */
@Component
public class ConfiguredTenantZoneResolver implements TenantZoneResolver {
    private static final String DEFAULT_ZONE_PROPERTY = "platform.default-time-zone";
    private static final String DEFAULT_ZONE = "Asia/Shanghai";
    private final Environment environment;

    public ConfiguredTenantZoneResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public ZoneId resolve(UUID tenantId) {
        String configured = environment.getProperty("platform.tenant-zones." + tenantId);
        if (configured == null || configured.isBlank()) {
            configured = environment.getProperty(DEFAULT_ZONE_PROPERTY, DEFAULT_ZONE);
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("租户时区配置不合法: " + configured, exception);
        }
    }
}
