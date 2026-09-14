package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.shared.context.RequestContext;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.UserContext;
import com.ailearn.platform.shared.context.UserContextHolder;
import com.ailearn.platform.shared.security.UserAuthenticationToken;
import java.util.function.Supplier;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 在一次已验证的 Open WebUI 工具回调内恢复 WMS 用户上下文，供既有只读应用服务和方法权限复用。 */
@Component
public class AiDelegatedUserContext {

    /**
     * 用途：在严格限定的同步代码块内绑定已重新验证的用户、租户、JTI 与权限。
     * 入参：可信 AI 上下文和只读调用；出参：调用结果；流程：绑定 ThreadLocal/SecurityContext，执行后无条件清理，防止线程复用串租户。
     */
    public <T> T call(AiRequestContext context, Supplier<T> action) {
        if (context == null || action == null) {
            throw new IllegalArgumentException("AI 委托上下文和调用不能为空");
        }
        RequestContext request = new RequestContext();
        request.setTenantId(context.tenantId());
        request.setUserId(context.userId());
        request.setJti(context.sessionTokenId());
        request.setRequestId(context.requestId());
        request.setPermissions(context.permissions());
        RequestContextHolder.setContext(request);
        UserContext user = new UserContext(context.userId().toString(), context.tenantId().toString(),
                "ai-delegated-" + context.userId(), context.sessionTokenId(), context.requestId(),
                context.permissions());
        UserContextHolder.set(user);
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UserAuthenticationToken(user, context.permissions().stream()
                .map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(securityContext);
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
            UserContextHolder.clear();
            RequestContextHolder.clear();
        }
    }
}
