package com.ailearn.platform.core.ai.controller;

import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolExecutor;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.application.TrustedAiRequestContextFactory;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 受控只读工具的直接验证接口。 */
@RestController
@RequestMapping("/api/ai/tools")
public class AiToolController {
    private final TrustedAiRequestContextFactory contextFactory;
    private final AiToolExecutor toolExecutor;

    /** 注入可信上下文和统一工具执行器。 */
    public AiToolController(TrustedAiRequestContextFactory contextFactory, AiToolExecutor toolExecutor) {
        this.contextFactory = contextFactory;
        this.toolExecutor = toolExecutor;
    }

    /** 查询当前用户有权查看的黄金业务闭环追溯事实；POST 仅承载结构化查询条件。 */
    @PostMapping("/queryTrace")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public ApiResponse<AiToolResult> queryTrace(@RequestBody Map<String, Object> arguments) {
        AiRequestContext context = contextFactory.current();
        return ApiResponse.success(toolExecutor.execute(context, null, "queryTrace", arguments));
    }

    /** 查询当前用户有权查看业务对象的脱敏操作时间线；实体范围由服务端白名单限定。 */
    @PostMapping("/queryOperationAudit")
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public ApiResponse<AiToolResult> queryOperationAudit(@RequestBody Map<String, Object> arguments) {
        AiRequestContext context = contextFactory.current();
        return ApiResponse.success(toolExecutor.execute(context, null, "queryOperationAudit", arguments));
    }
}
