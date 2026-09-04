package com.ailearn.platform.core.manufacturing.productionfact.infrastructure;

import com.ailearn.platform.core.manufacturing.productionfact.domain.FinishedGoodsReceipt;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialIssue;
import com.ailearn.platform.core.manufacturing.productionfact.domain.MaterialReturn;
import com.ailearn.platform.core.manufacturing.productionfact.domain.ProductionFactRepository;
import com.ailearn.platform.core.manufacturing.productionfact.domain.QualityInspection;
import com.ailearn.platform.core.manufacturing.productionfact.domain.WorkReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;

/** Task16 focused tests 和单机开发使用的租户隔离内存事实端口。 */
public class InMemoryProductionFactRepository implements ProductionFactRepository {

    private final ConcurrentMap<UUID, MaterialIssue> issues = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, MaterialReturn> returns = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, WorkReport> reports = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, QualityInspection> inspections = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, FinishedGoodsReceipt> receipts = new ConcurrentHashMap<>();

    @Override
    public MaterialIssue saveIssue(MaterialIssue issue) {
        issues.putIfAbsent(issue.id(), issue);
        return issues.get(issue.id());
    }

    @Override
    public Optional<MaterialIssue> findIssue(UUID tenantId, UUID id) {
        return Optional.ofNullable(issues.get(id)).filter(value -> tenantId.equals(value.tenantId()));
    }

    @Override
    public MaterialIssue updateIssue(UUID tenantId, UUID id, UnaryOperator<MaterialIssue> updater) {
        return update(issues, tenantId, id, updater, MaterialIssue::tenantId);
    }

    @Override
    public MaterialReturn saveReturn(MaterialReturn value) {
        returns.putIfAbsent(value.id(), value);
        return returns.get(value.id());
    }

    @Override
    public Optional<MaterialReturn> findReturn(UUID tenantId, UUID id) {
        return Optional.ofNullable(returns.get(id)).filter(value -> tenantId.equals(value.tenantId()));
    }

    @Override
    public MaterialReturn updateReturn(UUID tenantId, UUID id, UnaryOperator<MaterialReturn> updater) {
        return update(returns, tenantId, id, updater, MaterialReturn::tenantId);
    }

    @Override
    public WorkReport saveReport(WorkReport report) {
        reports.putIfAbsent(report.id(), report);
        return reports.get(report.id());
    }

    @Override
    public Optional<WorkReport> findReport(UUID tenantId, UUID id) {
        return Optional.ofNullable(reports.get(id)).filter(value -> tenantId.equals(value.tenantId()));
    }

    @Override
    public List<WorkReport> findReports(UUID tenantId, UUID workOrderId) {
        return reports.values().stream().filter(value -> tenantId.equals(value.tenantId())
                && workOrderId.equals(value.workOrderId())).toList();
    }

    @Override
    public QualityInspection saveInspection(QualityInspection inspection) {
        inspections.putIfAbsent(inspection.id(), inspection);
        return inspections.get(inspection.id());
    }

    @Override
    public Optional<QualityInspection> findInspection(UUID tenantId, UUID id) {
        return Optional.ofNullable(inspections.get(id)).filter(value -> tenantId.equals(value.tenantId()));
    }

    @Override
    public QualityInspection updateInspection(UUID tenantId, UUID id,
                                              UnaryOperator<QualityInspection> updater) {
        return update(inspections, tenantId, id, updater, QualityInspection::tenantId);
    }

    @Override
    public List<QualityInspection> findInspections(UUID tenantId, UUID workOrderId) {
        return inspections.values().stream().filter(value -> tenantId.equals(value.tenantId())
                && workOrderId.equals(value.workOrderId())).toList();
    }

    @Override
    public FinishedGoodsReceipt saveReceipt(FinishedGoodsReceipt receipt) {
        receipts.putIfAbsent(receipt.id(), receipt);
        return receipts.get(receipt.id());
    }

    @Override
    public Optional<FinishedGoodsReceipt> findReceipt(UUID tenantId, UUID id) {
        return Optional.ofNullable(receipts.get(id)).filter(value -> tenantId.equals(value.tenantId()));
    }

    @Override
    public FinishedGoodsReceipt updateReceipt(UUID tenantId, UUID id,
                                              UnaryOperator<FinishedGoodsReceipt> updater) {
        return update(receipts, tenantId, id, updater, FinishedGoodsReceipt::tenantId);
    }

    @Override
    public List<FinishedGoodsReceipt> findReceipts(UUID tenantId, UUID workOrderId) {
        return receipts.values().stream().filter(value -> tenantId.equals(value.tenantId())
                && workOrderId.equals(value.workOrderId())).toList();
    }

    @Override
    public List<MaterialIssue> findIssues(UUID tenantId, UUID workOrderId) {
        return issues.values().stream().filter(value -> tenantId.equals(value.tenantId())
                && workOrderId.equals(value.workOrderId())).toList();
    }

    @Override
    public List<MaterialReturn> findReturns(UUID tenantId, UUID workOrderId) {
        return returns.values().stream().filter(value -> tenantId.equals(value.tenantId())
                && workOrderId.equals(value.workOrderId())).toList();
    }

    /** 在租户范围内用 Map.compute 原子替换聚合，避免确认后重复写库存事实。 */
    private static <T> T update(ConcurrentMap<UUID, T> map, UUID tenantId, UUID id,
                                UnaryOperator<T> updater, java.util.function.Function<T, UUID> tenantOf) {
        List<T> result = new ArrayList<>(1);
        map.computeIfPresent(id, (ignored, current) -> {
            if (!tenantId.equals(tenantOf.apply(current))) {
                return current;
            }
            T updated = updater.apply(current);
            result.add(updated);
            return updated;
        });
        return result.isEmpty() ? null : result.getFirst();
    }
}
