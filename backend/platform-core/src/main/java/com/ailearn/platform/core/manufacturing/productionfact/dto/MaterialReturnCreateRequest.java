package com.ailearn.platform.core.manufacturing.productionfact.dto;

import java.util.List;
import java.util.UUID;

/** 创建生产退料 Draft 请求。 */
public record MaterialReturnCreateRequest(String returnNo, UUID workOrderId,
                                          List<MaterialItemRequest> items) {
}
