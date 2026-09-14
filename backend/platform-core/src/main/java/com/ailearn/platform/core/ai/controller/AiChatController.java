package com.ailearn.platform.core.ai.controller;

import com.ailearn.platform.core.ai.application.AiChatOrchestrator;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.TrustedAiRequestContextFactory;
import com.ailearn.platform.core.ai.dto.AiChatRequest;
import com.ailearn.platform.core.ai.dto.AiChatResponse;
import com.ailearn.platform.core.ai.exception.AiErrorCode;
import com.ailearn.platform.core.ai.exception.AiException;
import com.ailearn.platform.shared.api.ApiResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** AI 非流式兼容接口与浏览器 SSE 流式接口。 */
@RestController
@RequestMapping("/api/ai/chat")
public class AiChatController {
    private final TrustedAiRequestContextFactory contextFactory;
    private final AiChatOrchestrator orchestrator;
    private final ExecutorService aiStreamExecutor;

    /** 注入可信上下文、聊天编排器和虚拟线程执行器。 */
    public AiChatController(TrustedAiRequestContextFactory contextFactory,
                            AiChatOrchestrator orchestrator, ExecutorService aiStreamExecutor) {
        this.contextFactory = contextFactory;
        this.orchestrator = orchestrator;
        this.aiStreamExecutor = aiStreamExecutor;
    }

    /** 非流式兼容入口，适合自动化验证；真实页面默认使用 stream。 */
    @PostMapping
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public ApiResponse<AiChatResponse> chat(@RequestBody AiChatRequest request) {
        AiRequestContext context = contextFactory.current();
        return ApiResponse.success(orchestrator.chat(context, request, null));
    }

    /** 以固定命名事件返回模型正文、工具进度和最终元数据。 */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('ai:chat:query')")
    public SseEmitter stream(@RequestBody AiChatRequest request) {
        AiRequestContext context = contextFactory.current();
        SseEmitter emitter = new SseEmitter(0L);
        aiStreamExecutor.submit(() -> {
            try {
                orchestrator.chat(context, request, (eventName, data) -> send(emitter, eventName, data));
                emitter.complete();
            } catch (RuntimeException exception) {
                try {
                    String code = exception instanceof AiException aiException
                            ? aiException.getBusinessCode() : AiErrorCode.AI_PROVIDER_001.businessCode();
                    send(emitter, "error", Map.of("request_id", context.requestId(),
                            "code", code, "message", safeMessage(exception)));
                    emitter.complete();
                } catch (RuntimeException disconnected) {
                    emitter.completeWithError(disconnected);
                }
            }
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException exception) {
            throw new AiException(AiErrorCode.AI_PROVIDER_001, "浏览器流式连接已中断");
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "AI 服务暂不可用" : message;
    }
}
