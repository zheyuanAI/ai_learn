package com.ailearn.platform.core.ai.application;

import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.web.TrustedFactsQueryContextFactory;
import com.ailearn.platform.shared.context.UserContextHolder;
import org.springframework.stereotype.Component;

/** 从共享安全上下文创建 AI 请求上下文，禁止客户端覆盖租户、用户和权限。 */
@Component
public class TrustedAiRequestContextFactory {
    private final TrustedFactsQueryContextFactory factsContextFactory;

    /** 注入阶段 7 可信事实上下文工厂，复用权限指纹与租户时区。 */
    public TrustedAiRequestContextFactory(TrustedFactsQueryContextFactory factsContextFactory) {
        this.factsContextFactory = factsContextFactory;
    }

    /**
     * 用途：构造当前 AI 请求可信上下文。
     * 入参：无；出参：租户、用户、会话、权限和请求号；流程：所有身份字段只读取服务端 ThreadLocal。
     */
    public AiRequestContext current() {
        FactsQueryContext facts = factsContextFactory.current();
        return new AiRequestContext(facts.tenantId(), UserContextHolder.requireUserId(),
                UserContextHolder.getSessionId(), facts.permissions(), facts.tenantZone(),
                facts.permissionFingerprint(), facts.requestId());
    }
}
