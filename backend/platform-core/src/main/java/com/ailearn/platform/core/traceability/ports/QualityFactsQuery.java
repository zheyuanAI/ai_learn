package com.ailearn.platform.core.traceability.ports;

/** 质量领域 Facts 查询端口；质量摘要和追溯关系均由源应用服务提供。 */
public interface QualityFactsQuery {
    FactsSummary quality(FactsQueryRequest request);
    FactsSummary traceSummary(FactsQueryRequest request);
    TraceFacts trace(TraceQuery query);
}
