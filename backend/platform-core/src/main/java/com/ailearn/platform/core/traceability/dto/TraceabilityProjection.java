package com.ailearn.platform.core.traceability.dto;

import com.ailearn.platform.core.traceability.ports.TraceLink;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** 按链路权限裁剪后的追溯只读投影。 */
public record TraceabilityProjection(List<TraceNode> nodes, List<TraceLink> links,
                                     @JsonProperty("hidden_node_count") int hiddenNodeCount,
                                     @JsonProperty("missing_sources") List<String> missingSources,
                                     @JsonProperty("generated_at") Instant generatedAt,
                                     @JsonProperty("source_updated_at") Instant sourceUpdatedAt,
                                     @JsonProperty("request_id") String requestId,
                                     boolean truncated) {
    /** 保留旧七参数构造器，历史调用默认表示查询未截断。 */
    public TraceabilityProjection(List<TraceNode> nodes, List<TraceLink> links, int hiddenNodeCount,
                                  List<String> missingSources, Instant generatedAt, Instant sourceUpdatedAt,
                                  String requestId) {
        this(nodes, links, hiddenNodeCount, missingSources, generatedAt, sourceUpdatedAt, requestId, false);
    }

    public TraceabilityProjection {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        links = links == null ? List.of() : List.copyOf(links);
        missingSources = missingSources == null ? List.of() : List.copyOf(missingSources);
    }
}
