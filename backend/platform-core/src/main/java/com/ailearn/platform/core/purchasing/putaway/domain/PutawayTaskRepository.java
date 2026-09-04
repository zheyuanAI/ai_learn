package com.ailearn.platform.core.purchasing.putaway.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 上架任务持久化端口；不直接写库存表。
 */
public interface PutawayTaskRepository {

    /** 读取同仓默认 Storage 目标，兼容 V3 的非空 to_location_id。 */
    Optional<UUID> findDefaultStorageLocation(UUID tenantId, UUID warehouseId);

    /** 写入质量放行产生的待上架任务。 */
    PutawayTaskFact insert(PutawayTaskFact task);

    /** 锁定当前租户的上架任务。 */
    Optional<PutawayTaskFact> findById(UUID tenantId, UUID taskId, boolean forUpdate);

    /** 确认上架任务并记录库存流水标识。 */
    PutawayTaskFact complete(PutawayTaskFact task, UUID operatorId,
                             java.time.OffsetDateTime confirmedAt,
                             UUID inventoryTransactionId);

    /** 修改待执行任务的目标库位。 */
    PutawayTaskFact updateTarget(PutawayTaskFact task, UUID targetLocationId, UUID operatorId);

    /** 查询当前租户任务。 */
    List<PutawayTaskFact> findPage(UUID tenantId, String status, int page, int size);

    /** 统计当前租户任务总数。 */
    long count(UUID tenantId, String status);
}
