package com.ailearn.platform.core.manufacturing.dispatch.application;

import com.ailearn.platform.core.manufacturing.dispatch.domain.DispatchOrder;
import com.ailearn.platform.core.manufacturing.dispatch.dto.DispatchCreateRequest;
import java.util.Optional;
import java.util.UUID;

/** 派工应用端口；不暴露库存或 IoT 写能力。 */
public interface DispatchApplicationService {
    DispatchOrder create(DispatchCreateRequest request, String idempotencyKey);
    DispatchOrder release(UUID dispatchId, String idempotencyKey);
    DispatchOrder startProcessing(UUID dispatchId, String idempotencyKey);
    DispatchOrder complete(UUID dispatchId, String idempotencyKey);
    Optional<DispatchOrder> find(UUID dispatchId);
}
