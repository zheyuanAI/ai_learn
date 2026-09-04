package com.ailearn.platform.core.manufacturing.foundation.domain;

/** BOM 生命周期状态；只有 ACTIVE 版本可以被工单选用。 */
public enum BomStatus {
    DRAFT,
    ACTIVE,
    DISABLED
}
