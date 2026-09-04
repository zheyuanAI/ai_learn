package com.ailearn.platform.core.masterdata.infrastructure;

import com.ailearn.platform.core.masterdata.domain.model.LocationUsageSnapshot;
import com.ailearn.platform.core.masterdata.domain.port.LocationUsagePort;
import com.ailearn.platform.shared.exception.ServiceUnavailableException;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 库存内核接入前的安全兜底适配器。
 * <p>
 * 库位停用属于高风险写操作；在 inventory 查询端口尚未接入时拒绝操作，而不是假设库存为零。
 * </p>
 */
@Component
@ConditionalOnMissingBean(LocationUsagePort.class)
public class LocationUsageUnavailableAdapter implements LocationUsagePort {

    /**
     * 在库存查询端口尚未接入时拒绝库位停用前置检查。
     *
     * @param tenantId 可信租户 ID
     * @param locationId 当前库位 ID
     * @return 不会正常返回
     * @throws ServiceUnavailableException 库存查询端口不可用
     */
    @Override
    public LocationUsageSnapshot getUsage(UUID tenantId, UUID locationId) {
        throw new ServiceUnavailableException("库存内核尚未提供库位使用量查询，禁止停用库位");
    }
}
