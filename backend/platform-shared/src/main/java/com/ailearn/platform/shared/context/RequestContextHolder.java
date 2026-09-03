package com.ailearn.platform.shared.context;

import java.util.Optional;
import java.util.UUID;

/**
 * 请求上下文 ThreadLocal 持有者。
 * <p>
 * 基于线程本地变量管理当前线程生命周期内的 {@link RequestContext}，提供线程安全的存取与清空操作。
 * 在请求结束（Filter/Interceptor 的 finally 或 afterCompletion 中）必须调用 {@link #clear()} 防止线程池污染。
 * </p>
 */
public final class RequestContextHolder {

    private static final ThreadLocal<RequestContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private RequestContextHolder() {
    }

    /**
     * 获取当前线程绑定的请求上下文。若不存在则自动创建一个新的上下文并绑定。
     *
     * @return 非空的 {@link RequestContext} 实例
     */
    public static RequestContext getContext() {
        RequestContext context = CONTEXT_HOLDER.get();
        if (context == null) {
            context = new RequestContext();
            CONTEXT_HOLDER.set(context);
        }
        return context;
    }

    /**
     * 获取当前线程绑定的请求上下文，若不存在返回 null。
     *
     * @return 当前上下文或 null
     */
    public static RequestContext getNullableContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 显式设置当前线程绑定的请求上下文。
     *
     * @param context 要绑定的请求上下文
     */
    public static void setContext(RequestContext context) {
        if (context == null) {
            CONTEXT_HOLDER.remove();
        } else {
            CONTEXT_HOLDER.set(context);
        }
    }

    /**
     * 清理当前线程的上下文信息，防止线程池复用导致的内存泄漏与数据污染。
     */
    public static void clear() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * 快捷获取当前请求追踪 ID (X-Request-Id)。
     *
     * @return 请求追踪 ID，若无则返回 null
     */
    public static String getRequestId() {
        return Optional.ofNullable(CONTEXT_HOLDER.get())
                .map(RequestContext::getRequestId)
                .orElse(null);
    }

    /**
     * 快捷设置当前请求追踪 ID。
     *
     * @param requestId 请求追踪 ID
     */
    public static void setRequestId(String requestId) {
        getContext().setRequestId(requestId);
    }

    /**
     * 快捷获取当前租户 ID。
     *
     * @return 租户 UUID 或 null
     */
    public static UUID getTenantId() {
        return Optional.ofNullable(CONTEXT_HOLDER.get())
                .map(RequestContext::getTenantId)
                .orElse(null);
    }

    /**
     * 快捷获取当前用户 ID。
     *
     * @return 用户 UUID 或 null
     */
    public static UUID getUserId() {
        return Optional.ofNullable(CONTEXT_HOLDER.get())
                .map(RequestContext::getUserId)
                .orElse(null);
    }

    /**
     * 快捷获取当前用户名。
     *
     * @return 账号名或 null
     */
    public static String getUsername() {
        return Optional.ofNullable(CONTEXT_HOLDER.get())
                .map(RequestContext::getUsername)
                .orElse(null);
    }
}
