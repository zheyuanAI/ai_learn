package com.ailearn.platform.iot.contextlink.infrastructure;

import com.ailearn.platform.iot.contextlink.domain.port.ProductionContextQueryPort;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认的未接通适配器。
 * Core 未提供可确认的服务端入口时显式失败，让任务进入重试而不是返回伪造上下文。
 */
@Component
@ConditionalOnMissingBean(ProductionContextQueryPort.class)
public class UnavailableProductionContextQueryAdapter implements ProductionContextQueryPort {
    @Override
    public Optional<com.ailearn.platform.iot.contextlink.domain.ProductionContextView> findActive(
            UUID tenantId, UUID deviceId, OffsetDateTime alarmTime) {
        throw new ServiceUnavailableException("Core ProductionContextQuery 尚未安全装配");
    }
}
