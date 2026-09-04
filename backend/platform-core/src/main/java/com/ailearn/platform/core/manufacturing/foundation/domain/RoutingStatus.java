package com.ailearn.platform.core.manufacturing.foundation.domain;

/** Routing 生命周期状态；只有 ACTIVE 版本可以被工单选用。 */
public enum RoutingStatus {
    DRAFT,
    ACTIVE,
    DISABLED
}
