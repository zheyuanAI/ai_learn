package com.ailearn.platform.core.dashboard.exceptioncenter.application;

import com.ailearn.platform.core.dashboard.domain.DashboardTimeRange;
import com.ailearn.platform.core.dashboard.exceptioncenter.domain.ExceptionCenterPage;
import com.ailearn.platform.core.dashboard.exceptioncenter.domain.ExceptionCenterRecord;
import com.ailearn.platform.core.gis.exception.GisErrorCode;
import com.ailearn.platform.core.gis.exception.GisException;
import com.ailearn.platform.core.traceability.ports.FactQueryUnavailableException;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.IotFactsPort;
import com.ailearn.platform.core.traceability.ports.ManufacturingFactsQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 异常中心应用服务。
 * <p>它只读取三个真实 Facts 端口并由指标派生异常，不创建第二份库存、生产或设备告警事实表。</p>
 */
public class ExceptionCenterApplicationServiceImpl implements ExceptionCenterApplicationService {
    private final InventoryFactsQuery inventoryFacts;
    private final ManufacturingFactsQuery manufacturingFacts;
    private final IotFactsPort iotFacts;
    private final Clock clock;

    /** 生产构造器。 */
    public ExceptionCenterApplicationServiceImpl(InventoryFactsQuery inventoryFacts,
                                                 ManufacturingFactsQuery manufacturingFacts,
                                                 IotFactsPort iotFacts) {
        this(inventoryFacts, manufacturingFacts, iotFacts, Clock.systemUTC());
    }

    /** 测试构造器；时间窗口使用可控时钟。 */
    public ExceptionCenterApplicationServiceImpl(InventoryFactsQuery inventoryFacts,
                                                 ManufacturingFactsQuery manufacturingFacts,
                                                 IotFactsPort iotFacts, Clock clock) {
        this.inventoryFacts = inventoryFacts;
        this.manufacturingFacts = manufacturingFacts;
        this.iotFacts = iotFacts;
        this.clock = clock;
    }

    /**
     * 从库存、制造和 IoT 告警 Facts 派生异常记录并分页返回；本服务不落第二份业务事实。
     * 任一事实源不可用时返回受控查询错误，不把依赖失败伪装成“无异常”。
     */
    @Override
    public ExceptionCenterPage query(FactsQueryContext context, String timeRange, String source,
                                     String severity, int page, int size) {
        requirePermission(context);
        if (page < 1 || size < 1 || size > 200) {
            throw new GisException(GisErrorCode.GIS_QUERY_001, "page 必须大于等于 1，size 必须在 1 到 200 之间");
        }
        String normalizedSource = normalize(source);
        String normalizedSeverity = normalize(severity);
        DashboardTimeRange range = DashboardTimeRange.parse(timeRange, context.tenantZone(), clock.instant());
        FactsQueryRequest request = new FactsQueryRequest(context, range.from(), range.to(), Map.of());
        List<ExceptionCenterRecord> all = new ArrayList<>();
        Instant updated = null;
        try {
            FactsSummary inventory = inventoryFacts.inventory(request);
            updated = later(updated, inventory.sourceUpdatedAt());
            addInventory(all, inventory, range.to());
            FactsSummary manufacturing = manufacturingFacts.manufacturing(request);
            updated = later(updated, manufacturing.sourceUpdatedAt());
            addManufacturing(all, manufacturing, range.to());
            FactsSummary alarm = iotFacts.alarm(request);
            updated = later(updated, alarm.sourceUpdatedAt());
            addAlarms(all, alarm, range.to());
        } catch (FactQueryUnavailableException exception) {
            throw new GisException(GisErrorCode.GIS_QUERY_002, "异常中心事实源暂时不可用");
        } catch (RuntimeException exception) {
            throw new GisException(GisErrorCode.GIS_QUERY_002, "异常中心事实源暂时不可用");
        }
        List<ExceptionCenterRecord> filtered = all.stream()
                .filter(value -> normalizedSource == null || normalizedSource.equalsIgnoreCase(value.source()))
                .filter(value -> normalizedSeverity == null || normalizedSeverity.equalsIgnoreCase(value.severity()))
                .sorted(Comparator.comparing(ExceptionCenterRecord::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int from = (int) Math.min((long) (page - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new ExceptionCenterPage(filtered.subList(from, to), filtered.size(), page, size,
                clock.instant(), updated);
    }

    /** 从库存余额摘要派生可用量和预留超额异常。 */
    private static void addInventory(List<ExceptionCenterRecord> result, FactsSummary facts, Instant at) {
        BigDecimal available = facts.metrics().getOrDefault("available_qty", BigDecimal.ZERO);
        BigDecimal reserved = facts.metrics().getOrDefault("reserved_qty", BigDecimal.ZERO);
        BigDecimal onHand = facts.metrics().getOrDefault("on_hand_qty", BigDecimal.ZERO);
        if (available.signum() < 0) {
            result.add(new ExceptionCenterRecord("inventory", "available_negative", "HIGH", available,
                    "库存可用量小于 0", at));
        }
        if (reserved.compareTo(onHand) > 0) {
            result.add(new ExceptionCenterRecord("inventory", "reservation_overage", "HIGH",
                    reserved.subtract(onHand), "库存预留量超过在手量", at));
        }
    }

    /** 从制造事实摘要派生质量阻塞和不良数量异常。 */
    private static void addManufacturing(List<ExceptionCenterRecord> result, FactsSummary facts, Instant at) {
        BigDecimal blocked = facts.metrics().getOrDefault("quality_blocked_count", BigDecimal.ZERO);
        BigDecimal defect = facts.metrics().getOrDefault("defect_qty", BigDecimal.ZERO);
        if (blocked.signum() > 0) {
            result.add(new ExceptionCenterRecord("production", "quality_blocked", "HIGH", blocked,
                    "存在未闭环质量阻塞工单", at));
        }
        if (defect.signum() > 0) {
            result.add(new ExceptionCenterRecord("production", "defect_qty", "MEDIUM", defect,
                    "制造报工存在不良数量", at));
        }
    }

    /** 从 IoT 告警摘要派生触发中和未确认恢复告警。 */
    private static void addAlarms(List<ExceptionCenterRecord> result, FactsSummary facts, Instant at) {
        BigDecimal triggered = facts.metrics().getOrDefault("triggered_count", BigDecimal.ZERO);
        BigDecimal recovered = facts.metrics().getOrDefault("recovered_unacked_count", BigDecimal.ZERO);
        if (triggered.signum() > 0) {
            result.add(new ExceptionCenterRecord("device_alarm", "triggered_alarm", "HIGH", triggered,
                    "存在未处理设备告警", at));
        }
        if (recovered.signum() > 0) {
            result.add(new ExceptionCenterRecord("device_alarm", "recovered_unacked_alarm", "MEDIUM", recovered,
                    "存在已恢复但未确认设备告警", at));
        }
    }

    /** 校验异常中心总权限或专属权限，前端菜单可见性不替代服务端权限判断。 */
    private static void requirePermission(FactsQueryContext context) {
        if (context == null || (!context.hasPermission("dashboard:view")
                && !context.hasPermission("dashboard:exception:view"))) {
            throw new GisException(GisErrorCode.GIS_AUTH_001, "当前用户无异常中心查询权限");
        }
    }

    /** 规范化可选来源/级别筛选，空白值按未筛选处理。 */
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /** 合并多个 Facts 源的最新更新时间，用于标识异常投影的新鲜度。 */
    private static Instant later(Instant left, Instant right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }
}
