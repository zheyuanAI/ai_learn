package com.ailearn.platform.core.manufacturing.productionfact.dto;

import java.util.List;
import java.util.UUID;

/** 创建生产领料 Draft 请求。 */
public record MaterialIssueCreateRequest(String issueNo, UUID workOrderId,
                                         List<MaterialItemRequest> items,
                                         String overageReason) {

    /** 保留未启用 BOM 数量管控时的旧 Java 调用入口；HTTP 正式契约使用 overageReason。 */
    public MaterialIssueCreateRequest(String issueNo, UUID workOrderId, List<MaterialItemRequest> items) {
        this(issueNo, workOrderId, items, null);
    }
}
