package com.ailearn.platform.iot.telemetry.infrastructure;

import com.ailearn.platform.iot.telemetry.domain.DeviceStatus;
import com.ailearn.platform.iot.telemetry.domain.TelemetryDeduplicationClaim;
import com.ailearn.platform.iot.telemetry.domain.TelemetryFact;
import com.ailearn.platform.iot.telemetry.domain.TelemetryMessageKey;
import com.ailearn.platform.iot.telemetry.domain.port.DeviceStatusPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryDeduplicationPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryFactPort;
import com.ailearn.platform.iot.telemetry.domain.port.TelemetryQueryPort;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Task 18 最小内存适配器。
 * 用途：为单元测试和后续 MQTT/模拟入口提供租户隔离的去重、遥测追加和状态快照；不代表生产 PostgreSQL 实现。
 */
public class InMemoryTelemetryStore implements TelemetryDeduplicationPort, TelemetryFactPort, DeviceStatusPort,
        TelemetryQueryPort {

    private final Map<TelemetryMessageKey, DedupRecord> dedupRecords = new HashMap<>();
    private final List<TelemetryFact> facts = new ArrayList<>();
    private final Map<DeviceScope, DeviceStatus> statuses = new HashMap<>();

    /**
     * 用途：原子比较并声明消息去重键；入参为完整键、载荷摘要和接收时间；出参标明新消息、重复或冲突。
     */
    @Override
    public synchronized TelemetryDeduplicationClaim claim(TelemetryMessageKey key, String payloadHash,
                                                           OffsetDateTime receivedAt) {
        DedupRecord existing = dedupRecords.get(key);
        if (existing == null) {
            dedupRecords.put(key, new DedupRecord(payloadHash, receivedAt, List.of()));
            return new TelemetryDeduplicationClaim(TelemetryDeduplicationClaim.Decision.NEW, key,
                    payloadHash, List.of());
        }
        if (existing.payloadHash().equals(payloadHash)) {
            return new TelemetryDeduplicationClaim(TelemetryDeduplicationClaim.Decision.DUPLICATE, key,
                    existing.payloadHash(), existing.telemetryIds());
        }
        return new TelemetryDeduplicationClaim(TelemetryDeduplicationClaim.Decision.CONFLICT, key,
                existing.payloadHash(), existing.telemetryIds());
    }

    /**
     * 用途：把已追加遥测的事实标识写回去重记录，供相同载荷重复消息返回首次结果。
     */
    @Override
    public synchronized void complete(TelemetryMessageKey key, List<UUID> telemetryIds) {
        DedupRecord existing = dedupRecords.get(key);
        if (existing == null) {
            throw new IllegalStateException("去重记录不存在");
        }
        dedupRecords.put(key, new DedupRecord(existing.payloadHash(), existing.receivedAt(), List.copyOf(telemetryIds)));
    }

    /**
     * 用途：追加同一消息的全部指标事实；入参为已完成校验的事实；出参为生成的事实 ID。
     */
    @Override
    public synchronized List<UUID> append(List<TelemetryFact> newFacts) {
        facts.addAll(newFacts);
        return newFacts.stream().map(TelemetryFact::id).toList();
    }

    /**
     * 用途：提供测试和本地演示的遥测事实查询；入参显式带租户和设备边界；出参按设备时间倒序排列。
     */
    @Override
    public synchronized List<TelemetryFact> findFacts(UUID tenantId, UUID deviceId, String metricCode,
                                                       OffsetDateTime from, OffsetDateTime to, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        return facts.stream()
                .filter(fact -> tenantId.equals(fact.tenantId()) && deviceId.equals(fact.deviceId()))
                .filter(fact -> metricCode == null || metricCode.isBlank() || metricCode.equals(fact.metricCode()))
                .filter(fact -> from == null || !fact.timestamp().isBefore(from))
                .filter(fact -> to == null || !fact.timestamp().isAfter(to))
                .sorted(java.util.Comparator.comparing(TelemetryFact::timestamp).reversed()
                        .thenComparing(TelemetryFact::id))
                .limit(limit)
                .toList();
    }

    /**
     * 用途：读取租户内设备状态；不存在时返回 Offline/Idle/Normal 初始快照。
     */
    @Override
    public synchronized DeviceStatus find(UUID tenantId, UUID deviceId) {
        return statuses.getOrDefault(new DeviceScope(tenantId, deviceId), DeviceStatus.initial(tenantId, deviceId));
    }

    /**
     * 用途：按设备采集时间单调更新状态；入参为候选快照；出参包含是否推进及最终快照。
     */
    @Override
    public synchronized StatusUpdateResult updateIfNewer(DeviceStatus candidate) {
        DeviceScope scope = new DeviceScope(candidate.tenantId(), candidate.deviceId());
        DeviceStatus current = statuses.get(scope);
        if (current != null && current.sourceTimestamp() != null
                && !candidate.sourceTimestamp().isAfter(current.sourceTimestamp())) {
            return new StatusUpdateResult(false, current);
        }
        statuses.put(scope, candidate);
        return new StatusUpdateResult(true, candidate);
    }

    /** 更新告警状态快照；只修改当前租户设备的状态字段。 */
    @Override
    public synchronized void updateAlarmStatus(UUID tenantId, UUID deviceId, String alarmStatus) {
        DeviceScope scope = new DeviceScope(tenantId, deviceId);
        DeviceStatus current = statuses.getOrDefault(scope, DeviceStatus.initial(tenantId, deviceId));
        statuses.put(scope, new DeviceStatus(current.tenantId(), current.deviceId(), current.onlineStatus(),
                current.runningStatus(), alarmStatus, current.lastSeenAt(), current.lastMessageKey(),
                current.sourceTimestamp()));
    }

    /**
     * 用途：暴露测试夹具中的遥测数量，验证重复消息和整条拒绝不会产生部分事实。
     */
    public synchronized int telemetryCount(UUID tenantId, UUID deviceId) {
        return (int) facts.stream().filter(fact -> fact.tenantId().equals(tenantId)
                && fact.deviceId().equals(deviceId)).count();
    }

    private record DedupRecord(String payloadHash, OffsetDateTime receivedAt, List<UUID> telemetryIds) {
    }

    private record DeviceScope(UUID tenantId, UUID deviceId) {
    }
}
