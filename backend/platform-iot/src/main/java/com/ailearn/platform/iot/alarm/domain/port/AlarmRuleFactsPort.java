package com.ailearn.platform.iot.alarm.domain.port;

import com.ailearn.platform.iot.profile.domain.AlarmRule;
import java.util.List;
import java.util.UUID;

/**
 * 告警规则事实端口；告警模块只通过它读取规则，不直接依赖规则表实现。
 */
public interface AlarmRuleFactsPort {
    List<AlarmRule> findActiveRules(UUID tenantId, UUID deviceId);
}
