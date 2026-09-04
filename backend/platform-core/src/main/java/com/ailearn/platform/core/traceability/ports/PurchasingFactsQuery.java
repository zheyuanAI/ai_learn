package com.ailearn.platform.core.traceability.ports;

/** 采购领域 Facts 查询端口；S7 只消费采购应用服务提供的摘要。 */
public interface PurchasingFactsQuery {
    FactsSummary fulfillment(FactsQueryRequest request);
    FactsSummary traceSummary(FactsQueryRequest request);
    TraceFacts trace(TraceQuery query);
}
