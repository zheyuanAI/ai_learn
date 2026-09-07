package com.ailearn.platform.core.manufacturing.dispatch.port;

import java.util.UUID;
import java.math.BigDecimal;
import java.util.Optional;

/** 工单状态内部查询端口；派工/工序包不直接改写工单执行聚合。 */
public interface WorkOrderReleasePort {
    /** 判断指定租户工单当前是否已下达（Released）。 */
    boolean isReleased(UUID tenantId, UUID workOrderId);

    /** 判断工序是否存在于工单冻结 Routing；旧夹具默认允许，生产适配器必须覆盖。 */
    default boolean isOperationInRouting(UUID tenantId, UUID workOrderId, UUID operationId) {
        return true;
    }

    /** 读取工单计划数量，缺少该事实时由应用层跳过计划上限检查。 */
    default Optional<BigDecimal> plannedQty(UUID tenantId, UUID workOrderId) {
        return Optional.empty();
    }

    /** 判断工序前置工序是否均已完成；旧夹具默认通过。 */
    default boolean arePredecessorsCompleted(UUID tenantId, UUID workOrderId, UUID operationId) {
        return true;
    }

    /** 锁定工单主事实，保证派工累计数量检查串行化。 */
    default void lockWorkOrder(UUID tenantId, UUID workOrderId) {
        // focused 测试适配器无需数据库行锁；生产适配器覆盖该方法。
    }
}
