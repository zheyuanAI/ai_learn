package com.ailearn.platform.core.manufacturing.foundation.domain.port;

import com.ailearn.platform.core.manufacturing.foundation.domain.BomFact;
import java.util.Optional;
import java.util.UUID;

/** BOM 只读事实端口；查询必须带可信租户，且只返回有效版本。 */
public interface BomFactsPort {

    /**
     * 查询当前租户指定产品的有效 BOM 版本。
     *
     * @param tenantId 可信租户
     * @param bomId BOM 标识
     * @return 同租户、未删除且 ACTIVE 的 BOM，否则为空
     */
    Optional<BomFact> findActiveBom(UUID tenantId, UUID bomId);
}
