package com.ailearn.platform.core.masterdata.domain.port;

import com.ailearn.platform.core.masterdata.domain.model.LocationUsageSnapshot;
import java.util.UUID;

/**
 * 库位使用量查询端口。
 * <p>
 * 主数据领域不直接注入 inventory Mapper；实现由库存内核接入，库位停用前只允许通过此只读端口取得实物和预留快照。
 * </p>
 */
public interface LocationUsagePort {

    /**
     * 查询指定租户库位当前实物和有效预留数量。
     *
     * @param tenantId 可信租户 ID
     * @param locationId 当前库位 ID
     * @return 库位使用快照，空数量由应用层按零处理
     */
    LocationUsageSnapshot getUsage(UUID tenantId, UUID locationId);
}
