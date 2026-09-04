package com.ailearn.platform.iot.alarm.infrastructure;

import com.ailearn.platform.iot.alarm.domain.port.AlarmRuleFactsPort;
import com.ailearn.platform.iot.device.domain.port.DeviceRepository;
import com.ailearn.platform.iot.profile.domain.AlarmRule;
import com.ailearn.platform.iot.profile.domain.port.DeviceProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 复用既有设备/模型事实端口读取告警规则；告警域不直接依赖规则表或跨服务查询。
 */
@Component
public class DeviceProfileAlarmRuleFactsAdapter implements AlarmRuleFactsPort {
    private final DeviceRepository deviceRepository;
    private final DeviceProfileRepository profileRepository;

    public DeviceProfileAlarmRuleFactsAdapter(DeviceRepository deviceRepository,
                                              DeviceProfileRepository profileRepository) {
        this.deviceRepository = deviceRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    public List<AlarmRule> findActiveRules(UUID tenantId, UUID deviceId) {
        return deviceRepository.findDeviceById(tenantId, deviceId)
                .map(device -> profileRepository.findRules(tenantId, device.deviceProfileId(), 0, 1000).stream()
                        .filter(rule -> "ACTIVE".equalsIgnoreCase(rule.status()))
                        .filter(rule -> rule.deviceId() == null || rule.deviceId().equals(deviceId))
                        .toList())
                .orElseGet(List::of);
    }
}
