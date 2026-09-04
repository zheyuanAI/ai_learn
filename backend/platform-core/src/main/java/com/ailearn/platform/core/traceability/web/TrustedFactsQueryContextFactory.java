package com.ailearn.platform.core.traceability.web;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 从共享认证上下文构造 S7 查询上下文。
 * <p>
 * 租户、权限和请求号只取自 Gateway/下游安全过滤器建立的 ThreadLocal；查询参数不能覆盖租户。
 * 权限指纹由可信权限集合在服务端计算，供看板缓存隔离使用。
 * </p>
 */
@Component
public class TrustedFactsQueryContextFactory {
    private static final TenantZoneResolver DEFAULT_ZONE_RESOLVER = tenantId -> ZoneId.of("Asia/Shanghai");
    private final TenantZoneResolver tenantZoneResolver;

    /** 便于 focused 测试和非 Spring 调用使用平台默认时区。 */
    public TrustedFactsQueryContextFactory() {
        this.tenantZoneResolver = DEFAULT_ZONE_RESOLVER;
    }

    /**
     * Spring 生产入口；解析器从服务端租户配置读取时区，缺失配置时使用平台默认值。
     * 入参：Spring Bean 提供器；出参：可读取租户时区的上下文工厂；流程：不从请求参数读取时区。
     */
    @Autowired
    public TrustedFactsQueryContextFactory(ObjectProvider<TenantZoneResolver> resolverProvider) {
        this.tenantZoneResolver = resolverProvider.getIfAvailable(() -> DEFAULT_ZONE_RESOLVER);
    }

    /**
     * 创建当前请求的事实查询上下文。
     * 入参：无；出参：可信租户、权限快照、权限指纹和请求号；流程：要求租户存在，复制权限集合并计算指纹。
     *
     * @return 当前请求事实查询上下文
     */
    public FactsQueryContext current() {
        UUID tenantId = TenantContextHolder.requireTenantId();
        Set<String> permissions = Set.copyOf(UserContextHolder.getPermissions());
        return new FactsQueryContext(tenantId, fingerprint(permissions), permissions,
                tenantZoneResolver.resolve(tenantId), requestId());
    }

    private static String requestId() {
        String requestId = RequestContextHolder.getRequestId();
        return requestId == null || requestId.isBlank() ? "unknown-request" : requestId;
    }

    private static String fingerprint(Set<String> permissions) {
        List<String> normalized = permissions.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).sorted().toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(String.join("\n", normalized).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JDK 必须提供 SHA-256", exception);
        }
    }
}
