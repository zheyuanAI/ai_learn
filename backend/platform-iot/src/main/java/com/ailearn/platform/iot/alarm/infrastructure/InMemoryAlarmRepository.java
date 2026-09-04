package com.ailearn.platform.iot.alarm.infrastructure;

import com.ailearn.platform.iot.alarm.domain.AlarmFact;
import com.ailearn.platform.iot.alarm.domain.AlarmStatus;
import com.ailearn.platform.iot.alarm.domain.port.AlarmRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 测试与本地演示用告警存储；生产环境由 PostgreSQL 适配器承接。 */
public class InMemoryAlarmRepository implements AlarmRepository {
    private final Map<UUID, AlarmFact> records = new LinkedHashMap<>();

    @Override
    public synchronized AlarmFact createIfAbsent(AlarmFact fact) {
        return records.values().stream()
                .filter(existing -> existing.tenantId().equals(fact.tenantId())
                        && existing.deviceId().equals(fact.deviceId())
                        && existing.ruleId().equals(fact.ruleId())
                        && existing.status() != AlarmStatus.Recovered)
                .findFirst().orElseGet(() -> {
                    records.put(fact.id(), fact);
                    return fact;
                });
    }

    @Override
    public synchronized Optional<AlarmFact> findById(UUID tenantId, UUID alarmId) {
        return Optional.ofNullable(records.get(alarmId)).filter(fact -> fact.tenantId().equals(tenantId));
    }

    @Override
    public synchronized Optional<AlarmFact> findActive(UUID tenantId, UUID deviceId, UUID ruleId) {
        return records.values().stream()
                .filter(fact -> fact.tenantId().equals(tenantId) && fact.deviceId().equals(deviceId)
                        && fact.ruleId().equals(ruleId) && fact.status() != AlarmStatus.Recovered)
                .findFirst();
    }

    @Override
    public synchronized boolean hasActiveForDevice(UUID tenantId, UUID deviceId) {
        return records.values().stream().anyMatch(fact -> fact.tenantId().equals(tenantId)
                && fact.deviceId().equals(deviceId) && fact.status() != AlarmStatus.Recovered);
    }

    @Override
    public synchronized Optional<AlarmFact> transition(UUID tenantId, UUID alarmId, AlarmStatus expected,
                                                       AlarmStatus target, OffsetDateTime at, UUID userId) {
        return transition(tenantId, alarmId, expected, target, at, userId, null);
    }

    @Override
    public synchronized Optional<AlarmFact> transition(UUID tenantId, UUID alarmId, AlarmStatus expected,
                                                       AlarmStatus target, OffsetDateTime at, UUID userId,
                                                       String ackComment) {
        AlarmFact current = records.get(alarmId);
        if (current == null || !current.tenantId().equals(tenantId) || current.status() != expected) {
            return Optional.empty();
        }
        AlarmFact updated = new AlarmFact(current.id(), current.tenantId(), current.alarmNo(), current.deviceId(),
                current.ruleId(), current.alarmType(), current.alarmLevel(), target, current.triggeredAt(),
                userId == null ? current.ackedAt() : at, userId == null ? current.ackUserId() : userId,
                (target == AlarmStatus.RecoveredUnacked || target == AlarmStatus.Recovered) && userId == null
                        ? at : current.recoveredAt(),
                current.operationExecutionId(), current.workOrderId(), current.contextSource(), current.contextStatus(),
                current.createdAt(), ackComment == null ? current.ackComment() : ackComment, at,
                userId == null ? current.updatedBy() : userId);
        records.put(updated.id(), updated);
        return Optional.of(updated);
    }

    @Override
    public synchronized List<AlarmFact> findPage(UUID tenantId, UUID deviceId, AlarmStatus status, String alarmLevel,
                                                 OffsetDateTime from, OffsetDateTime to, String contextStatus,
                                                 int offset, int limit) {
        List<AlarmFact> filtered = records.values().stream()
                .filter(fact -> fact.tenantId().equals(tenantId))
                .filter(fact -> deviceId == null || fact.deviceId().equals(deviceId))
                .filter(fact -> status == null || fact.status() == status)
                .filter(fact -> alarmLevel == null || alarmLevel.equals(fact.alarmLevel()))
                .filter(fact -> from == null || !fact.triggeredAt().isBefore(from))
                .filter(fact -> to == null || !fact.triggeredAt().isAfter(to))
                .filter(fact -> contextStatus == null || contextStatus.equals(fact.contextStatus()))
                .sorted(Comparator.comparing(AlarmFact::triggeredAt).reversed())
                .toList();
        int start = Math.min(Math.max(0, offset), filtered.size());
        return new ArrayList<>(filtered.subList(start, Math.min(filtered.size(), start + Math.max(0, limit))));
    }

    @Override
    public synchronized long count(UUID tenantId, UUID deviceId, AlarmStatus status, String alarmLevel,
                                   OffsetDateTime from, OffsetDateTime to, String contextStatus) {
        return findPage(tenantId, deviceId, status, alarmLevel, from, to, contextStatus, 0, Integer.MAX_VALUE).size();
    }
}
