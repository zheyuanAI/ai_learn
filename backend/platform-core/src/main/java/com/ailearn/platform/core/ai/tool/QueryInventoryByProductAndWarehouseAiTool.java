package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.inventory.application.InventoryBalancePage;
import com.ailearn.platform.core.inventory.application.InventoryBalanceQuery;
import com.ailearn.platform.core.inventory.application.InventoryQueryService;
import com.ailearn.platform.core.inventory.domain.InventoryBalance;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 按产品、仓库、库位和批次查询库存余额的 AI 只读工具。 */
@Component
public class QueryInventoryByProductAndWarehouseAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of(
            "product_id", "warehouse_id", "location_id", "lot_no", "limit");
    private final InventoryQueryService inventoryQueryService;

    /** 注入现有库存事实查询端口。 */
    public QueryInventoryByProductAndWarehouseAiTool(InventoryQueryService inventoryQueryService) {
        this.inventoryQueryService = inventoryQueryService;
    }

    @Override public String name() { return "queryInventoryByProductAndWarehouse"; }

    @Override public String description() {
        return "查询指定产品在可选仓库、库位和批次下的实物量、预留量、可用量与最近库存事实时间";
    }

    @Override public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "product_id", Map.of("type", "string", "format", "uuid"),
                        "warehouse_id", Map.of("type", "string", "format", "uuid"),
                        "location_id", Map.of("type", "string", "format", "uuid"),
                        "lot_no", Map.of("type", "string", "maxLength", 128),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100)),
                "required", List.of("product_id"));
    }

    @Override public Set<String> anyOfPermissions() { return Set.of("inv:balance:view"); }

    /** 查询当前租户库存并裁剪租户字段和内部余额行 ID。 */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safe = AiToolArguments.safe(arguments);
        AiToolArguments.rejectUnknown(safe, ALLOWED_ARGUMENTS, name());
        UUID productId = AiToolArguments.requiredUuid(safe, "product_id");
        UUID warehouseId = AiToolArguments.optionalUuid(safe, "warehouse_id");
        UUID locationId = AiToolArguments.optionalUuid(safe, "location_id");
        String lotNo = AiToolArguments.optionalText(safe, "lot_no");
        int limit = AiToolArguments.limit(safe, 50, 100);
        InventoryBalancePage page = inventoryQueryService.queryBalances(new InventoryBalanceQuery(
                context.tenantId(), productId, warehouseId, locationId, lotNo, 1, limit));
        List<InventoryBalanceFact> balances = page.records().stream()
                .map(QueryInventoryByProductAndWarehouseAiTool::fact).toList();
        OffsetDateTime latest = page.records().stream().map(InventoryBalance::lastTransactionAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        String time = latest == null ? "" : "库存事实更新至 " + latest;
        return new AiToolResult(name(), Map.of("records", balances, "total", page.total(),
                "truncated", page.total() > balances.size()), "inventory balance application: "
                + balances.size() + " records", time, List.of(), AiToolStatus.Success);
    }

    private static InventoryBalanceFact fact(InventoryBalance value) {
        return new InventoryBalanceFact(value.dimension().productId(), value.dimension().warehouseId(),
                value.dimension().locationId(), value.dimension().lotNo(), value.onHandQty().toPlainString(),
                value.reservedQty().toPlainString(), value.availableQty().toPlainString(),
                value.lastTransactionAt());
    }

    /** 面向模型的库存数量事实。 */
    public record InventoryBalanceFact(UUID productId, UUID warehouseId, UUID locationId, String lotNo,
                                       String onHandQty, String reservedQty, String availableQty,
                                       OffsetDateTime lastTransactionAt) { }
}
