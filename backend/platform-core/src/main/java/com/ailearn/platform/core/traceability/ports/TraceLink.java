package com.ailearn.platform.core.traceability.ports;

import java.util.UUID;

/** 由源事实字段构造的追溯关系，不代表 S7 自有业务事实。 */
public record TraceLink(String fromType, UUID fromId, String toType, UUID toId, String relation) {
}
