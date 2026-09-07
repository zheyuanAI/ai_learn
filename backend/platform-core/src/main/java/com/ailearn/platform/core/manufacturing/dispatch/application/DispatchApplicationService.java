package com.ailearn.platform.core.manufacturing.dispatch.application;

import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchCreateRequest;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchPageQuery;
import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchPage;
import java.util.Optional;
import java.util.UUID;

/** 派工应用端口；不暴露库存或 IoT 写能力。 */
public interface DispatchApplicationService {
    DispatchOrder create(DispatchCreateRequest request, String idempotencyKey);
    DispatchOrder release(UUID dispatchId, String idempotencyKey);
    DispatchOrder startProcessing(UUID dispatchId, String idempotencyKey);
    DispatchOrder complete(UUID dispatchId, String idempotencyKey);
    Optional<DispatchOrder> find(UUID dispatchId);

    /**
     * 分页查询当前租户的派工事实。
     *
     * @param query 派工状态、工单和分页条件
     * @return 服务端分页结果，包含记录、总数和总页数
     */
    DispatchPage page(DispatchPageQuery query);
}
