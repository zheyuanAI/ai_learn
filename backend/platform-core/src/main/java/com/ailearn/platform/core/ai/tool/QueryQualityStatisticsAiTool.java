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
import org.springframework.stereotype.Component;

/** 查询授权时间范围内质量事实汇总的 AI 只读工具。 */
@Component
public class QueryQualityStatisticsAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("time_range");
    private static final Set<String> ALLOWED_TIME_RANGES = Set.of("today", "7d", "30d");
    private final DashboardApplicationService dashboardApplicationService;

    /** 注入现有七类看板查询应用服务。 */
    public QueryQualityStatisticsAiTool(DashboardApplicationService dashboardApplicationService) {
        this.dashboardApplicationService = dashboardApplicationService;
    }

    @Override
    public String name() {
        return "queryQualityStatistics";
    }

    @Override
    public String description() {
        return "查询今天、最近 7 天或最近 30 天内的质量检验事实汇总，并标明来源时间和是否为陈旧缓存";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("time_range", Map.of("type", "string",
                        "enum", List.of("today", "7d", "30d"), "default", "today")));
    }

    @Override
    public Set<String> anyOfPermissions() {
        return Set.of("dashboard:view", "dashboard:quality:view");
    }

    /** 使用可信租户和权限快照查询质量摘要，不接受客户端租户或自定义时间边界。 */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safe = AiToolArguments.safe(arguments);
        AiToolArguments.rejectUnknown(safe, ALLOWED_ARGUMENTS, name());
        String timeRange = AiToolArguments.timeRange(safe, ALLOWED_TIME_RANGES, "today");
        DashboardSummaryProjection result = dashboardApplicationService.query(DashboardSummaryType.QUALITY,
                new DashboardQuery(context.toFactsContext(), timeRange, Map.of()));
        return new AiToolResult(name(), result, result.sourceSummary(), describeTimeRange(result),
                List.of(), AiToolStatus.Success);
    }

    private static String describeTimeRange(DashboardSummaryProjection result) {
        return result.timeRange().from() + " 至 " + result.timeRange().to()
                + (result.stale() ? "；当前为明确标记的陈旧缓存" : "");
    }
}
