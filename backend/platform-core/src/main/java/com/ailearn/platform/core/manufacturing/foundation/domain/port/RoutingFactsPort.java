package com.ailearn.platform.core.manufacturing.foundation.domain.port;

import com.ailearn.platform.core.manufacturing.foundation.domain.RoutingFact;
import java.util.Optional;
import java.util.UUID;

/** Routing 只读事实端口；查询必须带可信租户，且只返回有效版本。 */
public interface RoutingFactsPort {

    /**
     * 查询当前租户指定产品的有效 Routing 版本。
     *
     * @param tenantId 可信租户
     * @param routingId Routing 标识
     * @return 同租户、未删除且 ACTIVE 的 Routing，否则为空
     */
    Optional<RoutingFact> findActiveRouting(UUID tenantId, UUID routingId);
}
