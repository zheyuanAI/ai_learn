package com.ailearn.platform.core.manufacturing.dispatch.domain;

/** 派工单状态；派工安排与工序执行事实保持独立。 */
public enum DispatchStatus {
    Draft,
    Released,
    Processing,
    Completed
}
