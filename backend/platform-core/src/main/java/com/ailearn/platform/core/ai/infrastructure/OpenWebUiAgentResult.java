package com.ailearn.platform.core.ai.infrastructure;

/** Open WebUI 完成一次服务端 Agent 循环后返回给 WMS 编排器的收口结果。 */
public record OpenWebUiAgentResult(String answer, String sourceSummary, String timeRangeSummary,
                                   String toolSummary, String modelId,
                                   int inputTokens, int outputTokens) {
    public OpenWebUiAgentResult {
        answer = answer == null ? "" : answer;
        sourceSummary = sourceSummary == null ? "" : sourceSummary;
        timeRangeSummary = timeRangeSummary == null ? "" : timeRangeSummary;
        toolSummary = toolSummary == null ? "" : toolSummary;
        modelId = modelId == null ? "" : modelId;
    }
}
