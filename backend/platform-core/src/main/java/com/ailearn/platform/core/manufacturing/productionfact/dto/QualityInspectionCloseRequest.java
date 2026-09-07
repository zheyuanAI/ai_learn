package com.ailearn.platform.core.manufacturing.productionfact.dto;

/** 不合格质检处置关闭请求；关闭只记录质量决定，不直接执行仓储移动。 */
public record QualityInspectionCloseRequest(String disposition) {
}
