package com.ailearn.platform.shared.context;

import com.ailearn.platform.shared.exception.ForbiddenException;
import java.util.UUID;

/**
 * 当前多租户上下文便捷访问工具类。
 * <p>
 * 用于获取当前请求绑定的租户 ID，支撑多租户数据隔离与 SQL 自动注入。
 * </p>
 */
public final class TenantContextHolder {

    private TenantContextHolder() {
    }

    /**
     * 获取当前租户 ID。
     *
     * @return 租户 UUID，若未设置返回 null
     */
    public static UUID getTenantId() {
        return RequestContextHolder.getTenantId();
    }

    /**
     * 获取当前租户 ID，若不存在则抛出 {@link ForbiddenException}。
     *
     * @return 非空的租户 UUID
     * @throws ForbiddenException 当未获取到租户上下文时抛出
     */
    public static UUID requireTenantId() {
        UUID tenantId = getTenantId();
        if (tenantId == null) {
            throw new ForbiddenException("缺失租户上下文，禁止跨租户或无租户操作");
        }
        return tenantId;
    }

    /**
     * 显式设置当前线程的租户 ID。
     *
     * @param tenantId 租户 UUID
     */
    public static void setTenantId(UUID tenantId) {
        RequestContextHolder.getContext().setTenantId(tenantId);
    }

    /**
     * 清空当前租户 ID。
     */
    public static void clear() {
        RequestContext context = RequestContextHolder.getNullableContext();
        if (context != null) {
            context.setTenantId(null);
        }
    }
}
