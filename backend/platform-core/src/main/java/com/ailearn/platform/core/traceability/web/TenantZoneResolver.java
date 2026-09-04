package com.ailearn.platform.core.traceability.web;

import java.time.ZoneId;
import java.util.UUID;

/**
 * 服务端租户时区解析端口。
 * <p>
 * 时区属于租户配置，不得从请求参数或客户端 Header 读取；没有配置时才使用平台默认时区。
 * </p>
 */
@FunctionalInterface
public interface TenantZoneResolver {

    /**
     * 解析指定租户的业务时区。
     * 入参：可信租户 ID；出参：有效 ZoneId；流程：只读取服务端租户配置，不接受客户端覆盖。
     *
     * @param tenantId 可信租户 ID
     * @return 租户业务时区
     */
    ZoneId resolve(UUID tenantId);
}
