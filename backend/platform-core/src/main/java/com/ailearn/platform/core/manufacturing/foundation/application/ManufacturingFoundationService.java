package com.ailearn.platform.core.manufacturing.foundation.application;

import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.dto.BomCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.RoutingCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;

/** foundation 应用端口；仅承载 BOM、Routing 和工单生产意图，不包含 S5 执行链路。 */
public interface ManufacturingFoundationService {

    /** 创建可供查询的 BOM 版本事实。 */
    BomFact createBom(BomCreateRequest request, String idempotencyKey);

    /** 创建可供查询的 Routing 版本事实。 */
    RoutingFact createRouting(RoutingCreateRequest request, String idempotencyKey);

    /** 创建 Draft 工单并校验 BOM、Routing 和可选销售来源。 */
    WorkOrderFact createWorkOrder(WorkOrderCreateRequest request, String idempotencyKey);
}
