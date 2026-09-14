package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.inventory.application.LowStockItem;
import com.ailearn.platform.core.inventory.application.LowStockPage;
import com.ailearn.platform.core.inventory.application.LowStockQueryService;
import com.ailearn.platform.core.masterdata.application.WarehouseApplicationService;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 按安全库存阈值查询低库存产品的 AI 只读工具。 */
@Component
public class QueryLowStockAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("warehouse_id", "limit");
    private static final Set<String> REQUIRED_PERMISSIONS = Set.of(
            "inv:product:view", "inv:warehouse:view", "inv:balance:view");
    private final LowStockQueryService lowStockQueryService;
    private final WarehouseApplicationService warehouseApplicationService;

    /** 注入低库存聚合端口和现有仓库详情服务。 */
    public QueryLowStockAiTool(LowStockQueryService lowStockQueryService,
                               WarehouseApplicationService warehouseApplicationService) {
        this.lowStockQueryService = lowStockQueryService;
        this.warehouseApplicationService = warehouseApplicationService;
    }

    @Override
    public String name() {
        return "queryLowStock";
    }

    @Override
    public String description() {
        return "按可用库存小于商品安全库存的固定口径查询低库存产品，可限定仓库；只提示缺口，不创建采购或补货任务";
    }

    @Override
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "warehouse_id", Map.of("type", "string", "format", "uuid"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 100,
                                "default", 50)));
    }

    @Override
    public Set<String> anyOfPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    /** 低库存跨商品和库存事实，因此必须同时拥有商品、仓库和余额查看权限。 */
    @Override
    public boolean isAllowed(AiRequestContext context) {
        return REQUIRED_PERMISSIONS.stream().allMatch(context::hasPermission);
    }

    /** 校验仓库归属后调用受控聚合读模型，并把数量转成字符串避免模型侧浮点精度损失。 */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safe = AiToolArguments.safe(arguments);
        AiToolArguments.rejectUnknown(safe, ALLOWED_ARGUMENTS, name());
        UUID warehouseId = AiToolArguments.optionalUuid(safe, "warehouse_id");
        int limit = AiToolArguments.limit(safe, 50, 100);
        if (warehouseId != null) {
            warehouseApplicationService.detail(warehouseId);
        }
        LowStockPage page = lowStockQueryService.query(context.tenantId(), warehouseId, limit);
        List<LowStockFact> records = page.records().stream().map(QueryLowStockAiTool::toFact).toList();
        OffsetDateTime latest = page.records().stream().map(LowStockItem::lastTransactionAt)
                .filter(java.util.Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        String time = latest == null ? "当前库存余额尚无更新时间" : "库存事实更新至 " + latest;
        return new AiToolResult(name(), Map.of("rule", "available_qty < safety_stock",
                "records", records, "truncated", page.truncated()),
                "product safety stock and inventory balance aggregate: " + records.size() + " records",
                time, List.of(), AiToolStatus.Success);
    }

    private static LowStockFact toFact(LowStockItem item) {
        return new LowStockFact(item.productId(), item.sku(), item.productName(), item.uom(),
                item.safetyStock().toPlainString(), item.availableQty().toPlainString(),
                item.shortfallQty().toPlainString(), item.lastTransactionAt());
    }

    /** 面向模型的低库存事实，tenant_id 和内部余额行均不对外返回。 */
    public record LowStockFact(UUID productId, String sku, String productName, String uom,
                               String safetyStock, String availableQty, String shortfallQty,
                               OffsetDateTime lastTransactionAt) { }
}
