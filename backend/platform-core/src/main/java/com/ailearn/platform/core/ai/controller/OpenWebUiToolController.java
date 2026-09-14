package com.ailearn.platform.core.ai.controller;

import com.ailearn.platform.core.ai.application.AiToolExecutor;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.application.AiDelegatedUserContext;
import com.ailearn.platform.core.ai.application.OpenWebUiInvocationContextStore;
import com.ailearn.platform.core.ai.config.AiProperties;
import com.ailearn.platform.core.ai.dto.AiOperationAuditToolRequest;
import com.ailearn.platform.core.ai.dto.AiDailyOperationReportToolRequest;
import com.ailearn.platform.core.ai.dto.AiTraceToolRequest;
import com.ailearn.platform.core.ai.dto.AiInventoryToolRequest;
import com.ailearn.platform.core.ai.dto.AiLowStockToolRequest;
import com.ailearn.platform.core.ai.dto.AiOrderStatusToolRequest;
import com.ailearn.platform.core.ai.dto.AiWorkOrderToolRequest;
import com.ailearn.platform.core.ai.dto.AiDeviceAlarmToolRequest;
import com.ailearn.platform.core.ai.dto.AiTimeRangeToolRequest;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

/** Open WebUI 专用只读工具入口；不接受用户身份 Header，只接受服务认证和短期消息关联。 */
@RestController
@RequestMapping("/internal/ai/tools")
public class OpenWebUiToolController {
    public static final String SERVICE_KEY_HEADER = "X-WMS-AI-Service-Key";
    public static final String CHAT_ID_HEADER = "X-WMS-OpenWebUI-Chat-Id";
    public static final String MESSAGE_ID_HEADER = "X-WMS-OpenWebUI-Message-Id";
    private final OpenWebUiInvocationContextStore contextStore;
    private final AiToolExecutor toolExecutor;
    private final AiProperties properties;
    private final AiDelegatedUserContext delegatedUserContext;

    /** 注入短期授权映射、统一工具执行器及服务间密钥配置。 */
    public OpenWebUiToolController(OpenWebUiInvocationContextStore contextStore,
                                   AiToolExecutor toolExecutor, AiProperties properties,
                                   AiDelegatedUserContext delegatedUserContext) {
        this.contextStore = contextStore;
        this.toolExecutor = toolExecutor;
        this.properties = properties;
        this.delegatedUserContext = delegatedUserContext;
    }

    /** 查询当前用户有权查看的黄金业务闭环追溯事实。 */
    @PostMapping("/queryTrace")
    @Operation(operationId = "queryTrace", summary = "查询授权范围内的业务闭环追溯事实")
    public AiToolResult queryTrace(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                   @RequestHeader(CHAT_ID_HEADER) String chatId,
                                   @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                   @RequestBody AiTraceToolRequest arguments) {
        return execute(serviceKey, chatId, messageId, "queryTrace", arguments.toArguments());
    }

    /** 查询当前用户有权查看业务对象的脱敏操作时间线。 */
    @PostMapping("/queryOperationAudit")
    @Operation(operationId = "queryOperationAudit", summary = "查询授权范围内的业务操作审计时间线")
    public AiToolResult queryOperationAudit(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                            @RequestHeader(CHAT_ID_HEADER) String chatId,
                                            @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                            @RequestBody AiOperationAuditToolRequest arguments) {
        return execute(serviceKey, chatId, messageId, "queryOperationAudit", arguments.toArguments());
    }

    /** 查询销售订单状态与履约数量。 */
    @PostMapping("/querySalesOrderStatus")
    @Operation(operationId = "querySalesOrderStatus", summary = "查询授权范围内的销售订单状态和履约数量")
    public AiToolResult querySalesOrderStatus(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                              @RequestHeader(CHAT_ID_HEADER) String chatId,
                                              @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                              @RequestBody AiOrderStatusToolRequest arguments) {
        return execute(serviceKey, chatId, messageId, "querySalesOrderStatus", arguments.toArguments());
    }

    /** 查询采购订单状态与累计收货数量。 */
    @PostMapping("/queryPurchaseOrderStatus")
    @Operation(operationId = "queryPurchaseOrderStatus", summary = "查询授权范围内的采购订单状态和收货数量")
    public AiToolResult queryPurchaseOrderStatus(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                                 @RequestHeader(CHAT_ID_HEADER) String chatId,
                                                 @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                                 @RequestBody AiOrderStatusToolRequest arguments) {
        return execute(serviceKey, chatId, messageId, "queryPurchaseOrderStatus", arguments.toArguments());
    }

    /** 查询产品与仓库维度的库存余额。 */
    @PostMapping("/queryInventoryByProductAndWarehouse")
    @Operation(operationId = "queryInventoryByProductAndWarehouse",
            summary = "查询授权范围内产品、仓库、库位和批次库存")
    public AiToolResult queryInventory(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                       @RequestHeader(CHAT_ID_HEADER) String chatId,
                                       @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                       @RequestBody AiInventoryToolRequest arguments) {
        return execute(serviceKey, chatId, messageId,
                "queryInventoryByProductAndWarehouse", arguments.toArguments());
    }

    /** 查询工单生命周期与执行进度。 */
    @PostMapping("/queryWorkOrderProgress")
    @Operation(operationId = "queryWorkOrderProgress", summary = "查询授权范围内的工单状态与执行进度")
    public AiToolResult queryWorkOrderProgress(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                               @RequestHeader(CHAT_ID_HEADER) String chatId,
                                               @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                               @RequestBody AiWorkOrderToolRequest arguments) {
        return execute(serviceKey, chatId, messageId, "queryWorkOrderProgress", arguments.toArguments());
    }

    /** 查询质量检验统计摘要。 */
    @PostMapping("/queryQualityStatistics")
    @Operation(operationId = "queryQualityStatistics", summary = "查询授权范围内的质量检验统计摘要")
    public AiToolResult queryQualityStatistics(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                               @RequestHeader(CHAT_ID_HEADER) String chatId,
                                               @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                               @RequestBody AiTimeRangeToolRequest arguments) {
        return execute(serviceKey, chatId, messageId, "queryQualityStatistics", arguments.toArguments());
    }

    /** 查询设备告警统计摘要。 */
    @PostMapping("/queryDeviceAlarm")
    @Operation(operationId = "queryDeviceAlarm", summary = "查询授权范围内的设备告警统计摘要")
    public AiToolResult queryDeviceAlarm(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                         @RequestHeader(CHAT_ID_HEADER) String chatId,
                                         @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                         @RequestBody AiDeviceAlarmToolRequest arguments) {
        return execute(serviceKey, chatId, messageId, "queryDeviceAlarm", arguments.toArguments());
    }

    /** 组合今日经营事实底稿，由模型生成文字日报。 */
    @PostMapping("/generateDailyOperationReport")
    @Operation(operationId = "generateDailyOperationReport", summary = "组合授权范围内的今日运营事实底稿")
    public AiToolResult generateDailyOperationReport(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                                     @RequestHeader(CHAT_ID_HEADER) String chatId,
                                                     @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                                     @RequestBody AiDailyOperationReportToolRequest arguments) {
        return execute(serviceKey, chatId, messageId,
                "generateDailyOperationReport", arguments.toArguments());
    }

    /** 查询安全库存阈值以下的产品。 */
    @PostMapping("/queryLowStock")
    @Operation(operationId = "queryLowStock", summary = "查询授权范围内的低库存产品")
    public AiToolResult queryLowStock(@RequestHeader(SERVICE_KEY_HEADER) String serviceKey,
                                      @RequestHeader(CHAT_ID_HEADER) String chatId,
                                      @RequestHeader(MESSAGE_ID_HEADER) String messageId,
                                      @RequestBody AiLowStockToolRequest arguments) {
        return execute(serviceKey, chatId, messageId, "queryLowStock", arguments.toArguments());
    }

    private AiToolResult execute(String serviceKey, String chatId, String messageId,
                                 String toolName, Map<String, Object> arguments) {
        verifyServiceKey(serviceKey);
        OpenWebUiInvocationContextStore.ResolvedInvocation resolved = contextStore.resolve(
                chatId, messageId, toolName);
        String callId = contextStore.recordStarted(chatId, messageId, toolName);
        try {
            AiToolResult result = delegatedUserContext.call(resolved.context(), () -> toolExecutor.execute(
                    resolved.context(), resolved.aiSessionId(), toolName, arguments, resolved.allowedTools()));
            contextStore.recordResult(chatId, messageId, callId, result);
            return result;
        } catch (RuntimeException exception) {
            // 修改用途：把工具失败状态映射回当前 SSE 调用，但不缓存异常正文或改变既有错误响应。
            contextStore.recordFailure(chatId, messageId, callId, toolName);
            throw exception;
        }
    }

    private void verifyServiceKey(String presented) {
        String configured = properties.getToolServiceSecret();
        if (!StringUtils.hasText(configured) || !StringUtils.hasText(presented)
                || !MessageDigest.isEqual(configured.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8))) {
            throw new AuthenticationCredentialsNotFoundException("AI 工具服务认证失败");
        }
    }
}
