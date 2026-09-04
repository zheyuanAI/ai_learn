package com.ailearn.platform.core.traceability.infrastructure;

import com.ailearn.platform.core.quality.domain.QualityDispositionFact;
import com.ailearn.platform.core.quality.domain.QualityDispositionType;
import com.ailearn.platform.core.quality.domain.QualityInspectionFact;
import com.ailearn.platform.core.quality.domain.PurchaseQualityRepository;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.QualityFactsQuery;
import com.ailearn.platform.core.traceability.ports.TraceFacts;
import com.ailearn.platform.core.traceability.ports.TraceLink;
import com.ailearn.platform.core.traceability.ports.TraceNode;
import com.ailearn.platform.core.traceability.ports.TraceQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 采购质量 Facts 生产适配器。
 * <p>
 * 质量看板和追溯只复用采购质量仓储暴露的事实端口；本适配器不复制质量表，也不把缺失数据转换为零值成功。
 * </p>
 */
public class CoreQualityFactsAdapter implements QualityFactsQuery {
    private final PurchaseQualityRepository repository;

    public CoreQualityFactsAdapter(PurchaseQualityRepository repository) {
        this.repository = repository;
    }

    @Override
    public FactsSummary quality(FactsQueryRequest request) {
        try {
            List<QualityInspectionFact> inspections = repository.listInspections(request.context().tenantId());
            List<QualityDispositionFact> dispositions = repository.listDispositions(request.context().tenantId());
            Map<String, BigDecimal> metrics = new LinkedHashMap<>();
            BigDecimal inspected = BigDecimal.ZERO;
            BigDecimal qualified = BigDecimal.ZERO;
            BigDecimal unqualified = BigDecimal.ZERO;
            long inspectionCount = 0;
            long passedCount = 0;
            long failedCount = 0;
            Instant updated = null;
            for (QualityInspectionFact inspection : inspections) {
                if (!inRange(inspection.inspectedAt(), inspection.createdAt(), request)) {
                    continue;
                }
                inspectionCount++;
                inspected = FactsAdapterSupport.add(inspected, inspection.inspectedQty());
                qualified = FactsAdapterSupport.add(qualified, inspection.qualifiedQty());
                unqualified = FactsAdapterSupport.add(unqualified, inspection.unqualifiedQty());
                if ("PASSED".equalsIgnoreCase(inspection.status())) {
                    passedCount++;
                } else if ("FAILED".equalsIgnoreCase(inspection.status())) {
                    failedCount++;
                }
                updated = FactsAdapterSupport.later(updated,
                        FactsAdapterSupport.instant(inspection.inspectedAt() == null
                                ? inspection.createdAt() : inspection.inspectedAt()));
            }
            long dispositionCount = 0;
            BigDecimal released = BigDecimal.ZERO;
            BigDecimal returned = BigDecimal.ZERO;
            BigDecimal scrapped = BigDecimal.ZERO;
            for (QualityDispositionFact disposition : dispositions) {
                if (!inRange(disposition.decidedAt(), disposition.createdAt(), request)) {
                    continue;
                }
                dispositionCount++;
                switch (disposition.type()) {
                    case Release -> released = FactsAdapterSupport.add(released, disposition.quantity());
                    case Return -> returned = FactsAdapterSupport.add(returned, disposition.quantity());
                    case Scrap -> scrapped = FactsAdapterSupport.add(scrapped, disposition.quantity());
                }
                updated = FactsAdapterSupport.later(updated,
                        FactsAdapterSupport.instant(disposition.decidedAt() == null
                                ? disposition.createdAt() : disposition.decidedAt()));
            }
            metrics.put("inspection_count", BigDecimal.valueOf(inspectionCount));
            metrics.put("inspected_qty", inspected);
            metrics.put("qualified_qty", qualified);
            metrics.put("unqualified_qty", unqualified);
            metrics.put("passed_count", BigDecimal.valueOf(passedCount));
            metrics.put("failed_count", BigDecimal.valueOf(failedCount));
            metrics.put("disposition_count", BigDecimal.valueOf(dispositionCount));
            metrics.put("released_qty", released);
            metrics.put("returned_qty", returned);
            metrics.put("scrapped_qty", scrapped);
            return new FactsSummary(metrics, "purchase quality", updated);
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("quality", exception);
        }
    }

    @Override
    public FactsSummary traceSummary(FactsQueryRequest request) {
        return quality(request);
    }

    @Override
    public TraceFacts trace(TraceQuery query) {
        try {
            String type = query.entityType().trim().toLowerCase();
            if (type.equals("quality_inspection") || type.equals("purchase_quality_inspection")) {
                Optional<QualityInspectionFact> inspection = repository.findInspection(
                        query.context().tenantId(), query.entityId(), false);
                return inspection.map(value -> inspectionFacts(value,
                        repository.findDispositionsByInspection(query.context().tenantId(), value.id(), false)))
                        .orElseGet(() -> TraceFacts.empty("quality"));
            }
            if (type.equals("quality_disposition") || type.equals("purchase_quality_disposition")) {
                Optional<QualityDispositionFact> disposition = repository.findDisposition(
                        query.context().tenantId(), query.entityId(), false);
                if (disposition.isEmpty()) {
                    return TraceFacts.empty("quality");
                }
                QualityDispositionFact value = disposition.get();
                List<TraceNode> nodes = new ArrayList<>();
                List<TraceLink> links = new ArrayList<>();
                nodes.add(dispositionNode(value));
                repository.findInspection(query.context().tenantId(), value.inspectionId(), false)
                        .ifPresent(inspection -> {
                            nodes.add(inspectionNode(inspection));
                            links.add(new TraceLink("quality_inspection", inspection.id(),
                                    "quality_disposition", value.id(), "disposition"));
                        });
                return new TraceFacts(nodes, links, dispositionTime(value), "purchase quality");
            }
            return TraceFacts.empty("quality");
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("quality", exception);
        }
    }

    private TraceFacts inspectionFacts(QualityInspectionFact inspection,
                                       List<QualityDispositionFact> dispositions) {
        List<TraceNode> nodes = new ArrayList<>();
        List<TraceLink> links = new ArrayList<>();
        nodes.add(inspectionNode(inspection));
        if (inspection.purchaseReceiptId() != null) {
            nodes.add(new TraceNode(inspection.tenantId(), "purchase_receipt", inspection.purchaseReceiptId(),
                    "receipt-" + inspection.purchaseReceiptId(), "RECEIVED", "pur:receipt:view",
                    FactsAdapterSupport.instant(inspection.inspectedAt()), true));
            links.add(new TraceLink("purchase_receipt", inspection.purchaseReceiptId(),
                    "quality_inspection", inspection.id(), "quality_check"));
        }
        for (QualityDispositionFact disposition : dispositions) {
            nodes.add(dispositionNode(disposition));
            links.add(new TraceLink("quality_inspection", inspection.id(),
                    "quality_disposition", disposition.id(), "disposition"));
        }
        Instant updated = FactsAdapterSupport.instant(inspection.inspectedAt());
        for (QualityDispositionFact disposition : dispositions) {
            updated = FactsAdapterSupport.later(updated, dispositionTime(disposition));
        }
        return new TraceFacts(nodes, links, updated, "purchase quality");
    }

    private TraceNode inspectionNode(QualityInspectionFact value) {
        Instant updated = FactsAdapterSupport.instant(value.inspectedAt() == null
                ? value.createdAt() : value.inspectedAt());
        return new TraceNode(value.tenantId(), "quality_inspection", value.id(),
                "inspection-" + value.id(), value.status(), "pur:quality:inspect", updated, true);
    }

    private TraceNode dispositionNode(QualityDispositionFact value) {
        return new TraceNode(value.tenantId(), "quality_disposition", value.id(),
                value.type().name(), value.status(), "pur:disposition:confirm",
                dispositionTime(value), true);
    }

    private static Instant dispositionTime(QualityDispositionFact value) {
        return FactsAdapterSupport.instant(value.executedAt() == null
                ? (value.decidedAt() == null ? value.createdAt() : value.decidedAt())
                : value.executedAt());
    }

    private static boolean inRange(java.time.OffsetDateTime preferred,
                                   java.time.OffsetDateTime fallback, FactsQueryRequest request) {
        return FactsAdapterSupport.inRange(preferred == null ? fallback : preferred, request);
    }
}
