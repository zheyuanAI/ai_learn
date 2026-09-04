package com.ailearn.platform.core.traceability.infrastructure;

import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.application.InventoryReservationPage;
import com.ailearn.platform.core.inventory.application.InventoryReservationQuery;
import com.ailearn.platform.core.inventory.application.InventoryTransactionPage;
import com.ailearn.platform.core.inventory.application.InventoryTransactionQuery;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import com.ailearn.platform.core.inventory.domain.InventoryReservation;
import com.ailearn.platform.core.inventory.domain.InventoryTransaction;
import com.ailearn.platform.core.masterdata.domain.entity.Warehouse;
import com.ailearn.platform.core.masterdata.domain.port.MasterDataRepository;
import com.ailearn.platform.core.traceability.ports.FactsQueryContext;
import com.ailearn.platform.core.traceability.ports.FactsQueryRequest;
import com.ailearn.platform.core.traceability.ports.FactsSummary;
import com.ailearn.platform.core.traceability.ports.InventoryFactsQuery;
import com.ailearn.platform.core.traceability.ports.ReferencedEntity;
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
import java.util.UUID;

/**
 * 库存 Facts 生产适配器。
 * <p>
 * 只调用库存查询应用端口和仓库主数据端口，不注入 Mapper、不拼接库存 SQL；因此 S7 的指标和追溯节点
 * 始终来自库存内核已经校验过的租户范围数据。
 * </p>
 */
public class CoreInventoryFactsAdapter implements InventoryFactsQuery {
    private static final int PAGE_SIZE = 200;
    private final InventoryQueryService inventoryQuery;
    private final MasterDataRepository<Warehouse> warehouses;

    public CoreInventoryFactsAdapter(InventoryQueryService inventoryQuery,
                                     MasterDataRepository<Warehouse> warehouses) {
        this.inventoryQuery = inventoryQuery;
        this.warehouses = warehouses;
    }

    @Override
    public FactsSummary inventory(FactsQueryRequest request) {
        try {
            UUID tenantId = request.context().tenantId();
            UUID warehouseId = FactsAdapterSupport.filterUuid(request, "warehouse_id");
            List<InventoryBalance> balances = balances(tenantId, warehouseId);
            Map<String, BigDecimal> metrics = new LinkedHashMap<>();
            BigDecimal onHand = BigDecimal.ZERO;
            BigDecimal reserved = BigDecimal.ZERO;
            BigDecimal available = BigDecimal.ZERO;
            Instant updated = null;
            for (InventoryBalance balance : balances) {
                onHand = FactsAdapterSupport.add(onHand, balance.onHandQty());
                reserved = FactsAdapterSupport.add(reserved, balance.reservedQty());
                available = FactsAdapterSupport.add(available, balance.availableQty());
                updated = FactsAdapterSupport.later(updated,
                        FactsAdapterSupport.instant(balance.lastTransactionAt()));
            }
            metrics.put("balance_count", BigDecimal.valueOf(balances.size()));
            metrics.put("on_hand_qty", onHand);
            metrics.put("reserved_qty", reserved);
            metrics.put("available_qty", available);
            return new FactsSummary(metrics, "inventory balance", updated);
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("inventory", exception);
        }
    }

    @Override
    public FactsSummary traceSummary(FactsQueryRequest request) {
        try {
            List<InventoryTransaction> transactions = transactions(request);
            BigDecimal quantity = BigDecimal.ZERO;
            Instant updated = null;
            for (InventoryTransaction transaction : transactions) {
                quantity = FactsAdapterSupport.add(quantity, transaction.quantity());
                updated = FactsAdapterSupport.later(updated,
                        FactsAdapterSupport.instant(transaction.occurredAt()));
            }
            return new FactsSummary(Map.of("transaction_count", BigDecimal.valueOf(transactions.size()),
                    "transaction_qty", quantity), "inventory transaction", updated);
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("inventory", exception);
        }
    }

    @Override
    public TraceFacts trace(TraceQuery query) {
        try {
            UUID tenantId = query.context().tenantId();
            String type = query.entityType().trim().toLowerCase();
            if (type.equals("inventory_transaction")) {
                return transactions(new FactsQueryRequest(query.context(),
                                java.time.Instant.EPOCH, java.time.Instant.now(), Map.of())).stream()
                        .filter(value -> value.id().equals(query.entityId()))
                        .findFirst()
                        .map(this::transactionFacts)
                        .orElseGet(() -> TraceFacts.empty("inventory transaction"));
            }
            if (type.equals("inventory_reservation")) {
                return reservations(tenantId).stream()
                        .filter(value -> value.reservation().id().equals(query.entityId()))
                        .findFirst()
                        .map(value -> reservationFacts(value.reservation()))
                        .orElseGet(() -> TraceFacts.empty("inventory reservation"));
            }
            if (type.equals("inventory_balance")) {
                return balances(tenantId, null).stream()
                        .filter(value -> value.id().equals(query.entityId()))
                        .findFirst()
                        .map(this::balanceFacts)
                        .orElseGet(() -> TraceFacts.empty("inventory balance"));
            }
            return TraceFacts.empty("inventory");
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("inventory", exception);
        }
    }

    @Override
    public Optional<ReferencedEntity> findWarehouse(FactsQueryContext context, UUID warehouseId) {
        try {
            return warehouses.findById(context.tenantId(), warehouseId)
                    .filter(value -> context.tenantId().equals(value.getTenantId()))
                    .map(value -> new ReferencedEntity(value.getTenantId(), "WAREHOUSE", value.getId(),
                            value.getName() == null || value.getName().isBlank() ? value.getCode() : value.getName(),
                            value.getStatus(), "/master-data/warehouses/" + value.getId(),
                            value.getUpdatedAt() == null ? FactsAdapterSupport.instant(value.getCreatedAt())
                                    : FactsAdapterSupport.instant(value.getUpdatedAt()),
                            "ACTIVE".equalsIgnoreCase(value.getStatus())));
        } catch (RuntimeException exception) {
            throw FactsAdapterSupport.unavailable("inventory warehouse", exception);
        }
    }

    private List<InventoryBalance> balances(UUID tenantId, UUID warehouseId) {
        List<InventoryBalance> result = new ArrayList<>();
        InventoryBalancePage page = inventoryQuery.queryBalances(
                new InventoryBalanceQuery(tenantId, null, warehouseId, null, null, 1, PAGE_SIZE));
        result.addAll(page.content());
        for (int current = 2; page.hasNext(); current++) {
            page = inventoryQuery.queryBalances(
                    new InventoryBalanceQuery(tenantId, null, warehouseId, null, null, current, PAGE_SIZE));
            result.addAll(page.content());
        }
        return result;
    }

    private List<InventoryTransaction> transactions(FactsQueryRequest request) {
        UUID tenantId = request.context().tenantId();
        UUID warehouseId = FactsAdapterSupport.filterUuid(request, "warehouse_id");
        List<InventoryTransaction> result = new ArrayList<>();
        InventoryTransactionPage page = inventoryQuery.queryTransactions(new InventoryTransactionQuery(
                tenantId, null, null, null, null, null, warehouseId, null, null,
                FactsAdapterSupport.utc(request.from()), FactsAdapterSupport.utc(request.to()), 1, PAGE_SIZE));
        result.addAll(page.content());
        for (int current = 2; page.hasNext(); current++) {
            page = inventoryQuery.queryTransactions(new InventoryTransactionQuery(
                    tenantId, null, null, null, null, null, warehouseId, null, null,
                    FactsAdapterSupport.utc(request.from()), FactsAdapterSupport.utc(request.to()), current, PAGE_SIZE));
            result.addAll(page.content());
        }
        return result;
    }

    private List<com.ailearn.platform.core.inventory.application.InventoryReservationView> reservations(UUID tenantId) {
        List<com.ailearn.platform.core.inventory.application.InventoryReservationView> result = new ArrayList<>();
        InventoryReservationPage page = inventoryQuery.queryReservations(new InventoryReservationQuery(
                tenantId, null, null, null, null, null, null, null, null, null, 1, PAGE_SIZE));
        result.addAll(page.content());
        for (int current = 2; page.hasNext(); current++) {
            page = inventoryQuery.queryReservations(new InventoryReservationQuery(tenantId, null, null, null, null,
                    null, null, null, null, null, current, PAGE_SIZE));
            result.addAll(page.content());
        }
        return result;
    }

    private TraceFacts transactionFacts(InventoryTransaction value) {
        TraceNode node = new TraceNode(value.tenantId(), "inventory_transaction", value.id(),
                value.transactionNo(), value.transactionType(), "inv:transaction:view",
                FactsAdapterSupport.instant(value.occurredAt()), true);
        return new TraceFacts(List.of(node), List.of(), FactsAdapterSupport.instant(value.occurredAt()),
                "inventory transaction");
    }

    private TraceFacts reservationFacts(InventoryReservation value) {
        TraceNode node = new TraceNode(value.tenantId(), "inventory_reservation", value.id(),
                value.reservationNo(), value.status(), "inv:reservation:view",
                FactsAdapterSupport.instant(value.updatedAt()), true);
        return new TraceFacts(List.of(node), List.of(), FactsAdapterSupport.instant(value.updatedAt()),
                "inventory reservation");
    }

    private TraceFacts balanceFacts(InventoryBalance value) {
        TraceNode node = new TraceNode(value.tenantId(), "inventory_balance", value.id(),
                value.dimension().productId().toString(), "ACTIVE", "inv:balance:view",
                FactsAdapterSupport.instant(value.lastTransactionAt()), true);
        return new TraceFacts(List.of(node), List.of(), FactsAdapterSupport.instant(value.lastTransactionAt()),
                "inventory balance");
    }
}
