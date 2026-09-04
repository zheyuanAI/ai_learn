package com.ailearn.platform.core.manufacturing.productionfact.dto;

import java.util.List;
import java.util.UUID;

/** 创建生产领料 Draft 请求。 */
public record MaterialIssueCreateRequest(String issueNo, UUID workOrderId,
                                         List<MaterialItemRequest> items) {
}
