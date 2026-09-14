package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.dashboard.application.DashboardApplicationService;
import com.ailearn.platform.core.dashboard.domain.DashboardSummaryType;
import com.ailearn.platform.core.dashboard.dto.DashboardQuery;
import com.ailearn.platform.core.dashboard.dto.DashboardSummaryProjection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 组合今日看板事实生成模型可解释数据底稿的 AI 只读工具。 */
@Component
public class GenerateDailyOperationReportAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("time_range");
    private static final List<DashboardSummaryType> REPORT_TYPES = List.of(
            DashboardSummaryType.INVENTORY, DashboardSummaryType.FULFILLMENT,
            DashboardSummaryType.MANUFACTURING, DashboardSummaryType.QUALITY,
            DashboardSummaryType.DEVICE, DashboardSummaryType.ALARM);
    private final DashboardApplicationService dashboardApplicationService;

    /** 注入现有看板查询应用服务，日报不新建或持久化正式业务报表。 */
    public GenerateDailyOperationReportAiTool(DashboardApplicationService dashboardApplicationService) {
        this.dashboardApplicationService = dashboardApplicationService;
    }

    @Override
    public String name() {
        return "generateDailyOperationReport";
    }

    @Override
    public String description() {
        return "组合今日库存、履约、制造、质量、设备和告警摘要，供模型生成文字运营日报；不会保存报表或修改业务数据";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of("time_range", Map.of("type", "string",
                        "enum", List.of("today"), "default", "today")));
    }

    @Override
    public Set<String> anyOfPermissions() {
        return Set.of("dashboard:view");
    }

    /** 仅组合系统已定义的今日摘要，不允许模型指定统计公式、租户或任意日期范围。 */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safe = AiToolArguments.safe(arguments);
        AiToolArguments.rejectUnknown(safe, ALLOWED_ARGUMENTS, name());
        String timeRange = AiToolArguments.timeRange(safe, Set.of("today"), "today");
        Map<String, DashboardSummaryProjection> sections = new LinkedHashMap<>();
        for (DashboardSummaryType type : REPORT_TYPES) {
            sections.put(type.key(), dashboardApplicationService.query(type,
                    new DashboardQuery(context.toFactsContext(), timeRange, Map.of())));
        }
        DashboardSummaryProjection first = sections.values().iterator().next();
        String sources = sections.values().stream().map(DashboardSummaryProjection::sourceSummary)
                .filter(value -> value != null && !value.isBlank()).distinct()
                .reduce((left, right) -> left + "; " + right).orElse("");
        boolean stale = sections.values().stream().anyMatch(DashboardSummaryProjection::stale);
        String summary = first.timeRange().from() + " 至 " + first.timeRange().to()
                + (stale ? "；部分数据为明确标记的陈旧缓存" : "");
        return new AiToolResult(name(), new DailyOperationReportFacts("today", sections),
                sources, summary, List.of(), AiToolStatus.Success);
    }

    /** 面向模型的今日运营事实底稿；文字表述由模型完成，本结构不进入正式报表库。 */
    public record DailyOperationReportFacts(String timeRange,
                                            Map<String, DashboardSummaryProjection> sections) {
        public DailyOperationReportFacts {
            sections = Map.copyOf(sections);
        }
    }
}
