package com.ailearn.platform.iot.profile.domain.port;

import com.ailearn.platform.iot.profile.domain.AlarmRule;
import com.ailearn.platform.iot.profile.domain.DeviceProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 设备模型持久化端口；端口方法必须显式携带可信租户。 */
public interface DeviceProfileRepository {
    boolean existsProfileByCode(UUID tenantId, String profileCode);
    DeviceProfile insert(DeviceProfile profile);
    Optional<DeviceProfile> findProfileById(UUID tenantId, UUID id);
    List<DeviceProfile> findPage(UUID tenantId, String code, int offset, int limit);
    long count(UUID tenantId, String code);
    boolean existsMetric(UUID tenantId, UUID profileId, String metricCode);
    AlarmRule insertRule(AlarmRule rule);
    boolean existsRuleByCode(UUID tenantId, String ruleCode);
    List<AlarmRule> findRules(UUID tenantId, UUID profileId, int offset, int limit);
}
