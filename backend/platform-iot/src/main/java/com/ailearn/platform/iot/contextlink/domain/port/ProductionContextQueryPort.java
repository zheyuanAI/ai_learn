package com.ailearn.platform.iot.contextlink.domain.port;

import com.ailearn.platform.iot.contextlink.domain.ProductionContextView;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Core ProductionContextQuery 的 IoT 侧防腐端口。
 * 查询键固定为可信 tenantId、deviceId 和 alarmTime；Core 返回多个活动工序时必须抛错。
 */
public interface ProductionContextQueryPort {
    /**
     * 用途：按告警时刻读取唯一活动工序；无匹配返回空，调用方不得把空值转成业务上下文。
     */
    Optional<ProductionContextView> findActive(UUID tenantId, UUID deviceId, OffsetDateTime alarmTime);
}
