package com.ailearn.platform.core.traceability.ports;

/** 销售领域 Facts 查询端口；S7 不复制销售订单或履约事实。 */
public interface SalesFactsQuery {
    FactsSummary fulfillment(FactsQueryRequest request);
    FactsSummary traceSummary(FactsQueryRequest request);
    TraceFacts trace(TraceQuery query);
}
