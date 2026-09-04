package com.ailearn.platform.core.manufacturing.foundation.domain.port;

import com.ailearn.platform.core.manufacturing.foundation.domain.SalesLineFact;
import java.util.Optional;
import java.util.UUID;

/** 销售订单行只读事实端口；制造不直接写销售表。 */
public interface SalesFactsPort {

    /**
     * 查询当前租户内仍有效的销售订单行。
     *
     * @param tenantId 可信租户
     * @param salesOrderLineId 销售订单行标识
     * @return 同租户且有效的销售行，否则为空
     */
    Optional<SalesLineFact> findActiveLine(UUID tenantId, UUID salesOrderLineId);
}
