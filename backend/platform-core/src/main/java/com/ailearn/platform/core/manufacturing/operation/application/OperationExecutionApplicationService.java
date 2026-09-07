package com.ailearn.platform.core.manufacturing.operation.application;

import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecution;
import com.ailearn.platform.core.manufacturing.operation.dto.OperationExecutionCreateRequest;
import com.ailearn.platform.core.manufacturing.operation.dto.OperationExecutionPageQuery;
import com.ailearn.platform.core.manufacturing.operation.domain.OperationExecutionPage;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/** 工序执行应用端口；开始/暂停/恢复/完成均独立保存时间线事件。 */
public interface OperationExecutionApplicationService {
    OperationExecution create(OperationExecutionCreateRequest request, String idempotencyKey);
    OperationExecution start(UUID executionId, OffsetDateTime occurredAt, String idempotencyKey);
    OperationExecution pause(UUID executionId, String reason, OffsetDateTime occurredAt,
                             String idempotencyKey);
    OperationExecution resume(UUID executionId, OffsetDateTime occurredAt, String idempotencyKey);
    OperationExecution complete(UUID executionId, OffsetDateTime occurredAt, String idempotencyKey);
    Optional<OperationExecution> find(UUID executionId);

    /**
     * 分页查询当前租户的工序执行事实。
     *
     * @param query 工单、工序、状态和分页条件
     * @return 服务端分页结果，包含记录、总数和总页数
     */
    OperationExecutionPage page(OperationExecutionPageQuery query);
}
