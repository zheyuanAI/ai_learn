package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.dto.DashboardQuery;
import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 查询设备告警汇总事实的 AI 只读工具。 */
@Component
public class QueryDeviceAlarmAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("time_range", "device_id");
    private static final Set<String> ALLOWED_TIME_RANGES = Set.of("today", "7d", "30d");
    private final DashboardApplicationService dashboardApplicationService;

    /** 注入现有七类看板查询应用服务。 */
    public QueryDeviceAlarmAiTool(DashboardApplicationService dashboardApplicationService) {
        this.dashboardApplicationService = dashboardApplicationService;
    }

    @Override
    public String name() {
        return "queryDeviceAlarm";
    }

    @Override
    public String description() {
        return "查询今天、最近 7 天或最近 30 天的设备告警汇总，可限定一个当前租户可见的设备";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "time_range", Map.of("type", "string",
                                "enum", List.of("today", "7d", "30d"), "default", "today"),
                        "device_id", Map.of("type", "string", "format", "uuid")));
    }

    @Override
    public Set<String> anyOfPermissions() {
        return Set.of("dashboard:view", "dashboard:alarm:view");
    }

    /** 先严格校验筛选参数，再由看板服务校验设备归属并查询告警事实。 */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safe = AiToolArguments.safe(arguments);
        AiToolArguments.rejectUnknown(safe, ALLOWED_ARGUMENTS, name());
        String timeRange = AiToolArguments.timeRange(safe, ALLOWED_TIME_RANGES, "today");
        UUID deviceId = AiToolArguments.optionalUuid(safe, "device_id");
        Map<String, String> filters = deviceId == null ? Map.of() : Map.of("device_id", deviceId.toString());
        DashboardSummaryProjection result = dashboardApplicationService.query(DashboardSummaryType.ALARM,
                new DashboardQuery(context.toFactsContext(), timeRange, filters));
        String summary = result.timeRange().from() + " 至 " + result.timeRange().to()
                + (result.stale() ? "；当前为明确标记的陈旧缓存" : "");
        return new AiToolResult(name(), result, result.sourceSummary(), summary,
                List.of(), AiToolStatus.Success);
    }
}
