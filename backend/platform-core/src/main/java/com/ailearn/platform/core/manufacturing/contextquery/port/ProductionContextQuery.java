package com.ailearn.platform.core.manufacturing.contextquery.port;

import com.ailearn.platform.core.manufacturing.contextquery.domain.ProductionContext;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 供 IoT 告警补链使用的内部只读端口；查询键固定为 tenantId + deviceId + alarmTime。
 */
public interface ProductionContextQuery {
    /** 返回告警时刻唯一活动工序上下文；无匹配返回空，多个匹配抛出不唯一异常。 */
    Optional<ProductionContext> findActive(UUID tenantId, UUID deviceId, OffsetDateTime alarmTime);
}
