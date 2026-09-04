package com.ailearn.platform.core.manufacturing.execution.domain;

/** 工单完成方式；Normal 表示按事实完成，Manual 表示授权人工终止剩余生产。 */
public enum WorkOrderCompletionType {
    Normal,
    Manual
}
