package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.purchasing.application.PurchaseOrderApplicationService;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderLineView;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderPageQuery;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderView;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 查询采购订单生命周期及累计收货数量的 AI 只读工具。 */
@Component
public class QueryPurchaseOrderStatusAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("order_id", "order_no");
    private final PurchaseOrderApplicationService purchaseOrderService;

    /** 注入现有采购订单应用服务。 */
    public QueryPurchaseOrderStatusAiTool(PurchaseOrderApplicationService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }

    @Override public String name() { return "queryPurchaseOrderStatus"; }

    @Override public String description() {
        return "按采购订单 UUID 或单号查询订单状态、预计到货日和各明细订购/已收/待收数量";
    }

    @Override public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "order_id", Map.of("type", "string", "format", "uuid"),
                        "order_no", Map.of("type", "string", "maxLength", 128)),
                "anyOf", List.of(Map.of("required", List.of("order_id")),
                        Map.of("required", List.of("order_no"))));
    }

    @Override public Set<String> anyOfPermissions() { return Set.of("pur:order:view"); }

    /** 查询并裁剪采购订单事实，不返回允许写动作或原始完成会话。 */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safe = AiToolArguments.safe(arguments);
        AiToolArguments.rejectUnknown(safe, ALLOWED_ARGUMENTS, name());
        UUID orderId = AiToolArguments.optionalUuid(safe, "order_id");
        String orderNo = AiToolArguments.optionalText(safe, "order_no");
        if (orderId == null && orderNo == null) {
            throw new IllegalArgumentException("order_id 和 order_no 至少提供一个");
        }
        PurchaseOrderView order = orderId != null ? purchaseOrderService.detail(orderId) : findByNo(orderNo);
        List<PurchaseLineFact> lines = order.getLines().stream().map(QueryPurchaseOrderStatusAiTool::line).toList();
        PurchaseOrderStatusFact fact = new PurchaseOrderStatusFact(order.getId(), order.getPoNo(),
                order.getSupplierId(), order.getExpectedArrivalDate(), order.getStatus(), order.getCompletionType(),
                order.getCompletionReason(), order.getCompletedAt(), lines);
        String time = order.getCompletedAt() == null ? "" : "订单完成于 " + order.getCompletedAt();
        return new AiToolResult(name(), fact, "purchase order application: " + order.getPoNo(),
                time, List.of(), AiToolStatus.Success);
    }

    private PurchaseOrderView findByNo(String orderNo) {
        PurchaseOrderPageQuery query = new PurchaseOrderPageQuery();
        query.setKeyword(orderNo);
        query.setPage(1);
        query.setSize(20);
        List<PurchaseOrderView> exact = purchaseOrderService.page(query).records().stream()
                .filter(item -> orderNo.equalsIgnoreCase(item.getPoNo())).toList();
        if (exact.size() != 1) {
            throw new IllegalArgumentException("未找到唯一采购订单: " + orderNo);
        }
        return purchaseOrderService.detail(exact.getFirst().getId());
    }

    private static PurchaseLineFact line(PurchaseOrderLineView value) {
        return new PurchaseLineFact(value.lineNo(), value.productId(), value.uom(), value.orderedQty(),
                value.receivedQty(), value.pendingQty(), value.targetWarehouseId(), value.sourceWorkOrderId());
    }

    /** 面向模型的采购订单状态事实。 */
    public record PurchaseOrderStatusFact(UUID orderId, String orderNo, UUID supplierId,
                                          LocalDate expectedArrivalDate, String orderStatus,
                                          String completionType, String completionReason,
                                          OffsetDateTime completedAt, List<PurchaseLineFact> lines) { }

    /** 面向模型的采购明细数量事实。 */
    public record PurchaseLineFact(int lineNo, UUID productId, String uom, String orderedQty,
                                   String receivedQty, String pendingQty, UUID targetWarehouseId,
                                   UUID sourceWorkOrderId) { }
}
