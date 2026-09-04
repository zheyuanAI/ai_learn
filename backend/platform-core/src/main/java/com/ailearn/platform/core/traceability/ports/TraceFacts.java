package com.ailearn.platform.core.traceability.ports;

import java.time.Instant;
import java.util.List;

/** 单个领域返回的追溯节点和真实来源关系。 */
public record TraceFacts(List<TraceNode> nodes, List<TraceLink> links, Instant sourceUpdatedAt,
                         String sourceSummary) {

    public TraceFacts {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        links = links == null ? List.of() : List.copyOf(links);
        sourceSummary = sourceSummary == null ? "" : sourceSummary;
    }

    public static TraceFacts empty(String sourceSummary) {
        return new TraceFacts(List.of(), List.of(), null, sourceSummary);
    }
}
