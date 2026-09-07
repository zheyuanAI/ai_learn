package com.ailearn.platform.core.manufacturing.foundation.application;

import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import com.ailearn.platform.core.manufacturing.foundation.domain.WorkOrderFact;
import com.ailearn.platform.core.manufacturing.foundation.dto.BomCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.ManufacturingPageQuery;
import com.ailearn.platform.core.manufacturing.foundation.dto.RoutingCreateRequest;
import com.ailearn.platform.core.manufacturing.foundation.dto.WorkOrderCreateRequest;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import java.util.Optional;
import java.util.UUID;

/** foundation 应用端口；仅承载 BOM、Routing 和工单生产意图，不包含 S5 执行链路。 */
public interface ManufacturingFoundationService {

    /** 创建可供查询的 BOM 版本事实。 */
    BomFact createBom(BomCreateRequest request, String idempotencyKey);

    /** 创建可供查询的 Routing 版本事实。 */
    RoutingFact createRouting(RoutingCreateRequest request, String idempotencyKey);

    /** 创建 Draft 工单并校验 BOM、Routing 和可选销售来源。 */
    WorkOrderFact createWorkOrder(WorkOrderCreateRequest request, String idempotencyKey);

    /** 查询当前租户 BOM 分页。 */
    MasterDataPageResult<BomFact> listBoms(ManufacturingPageQuery query);

    /** 查询当前租户单个 BOM；跨租户对象返回空。 */
    Optional<BomFact> findBom(UUID id);

    /** 查询当前租户 Routing 分页。 */
    MasterDataPageResult<RoutingFact> listRoutings(ManufacturingPageQuery query);

    /** 查询当前租户单个 Routing；跨租户对象返回空。 */
    Optional<RoutingFact> findRouting(UUID id);

    /** 修改 Draft/Rejected 工单基础生产意图。 */
    WorkOrderFact updateWorkOrder(UUID id, WorkOrderCreateRequest request, String idempotencyKey);

    /** 查询当前租户工单基础事实分页。 */
    MasterDataPageResult<WorkOrderFact> listWorkOrders(ManufacturingPageQuery query);
}
