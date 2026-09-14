package com.ailearn.platform.core.ai.tool;

import com.ailearn.platform.core.ai.application.AiReadTool;
import com.ailearn.platform.core.ai.application.AiRequestContext;
import com.ailearn.platform.core.ai.application.AiToolResult;
import com.ailearn.platform.core.ai.domain.AiToolStatus;
import com.ailearn.platform.core.sales.application.SalesOrderApplicationService;
import com.ailearn.platform.core.sales.dto.SalesOrderLineView;
import com.ailearn.platform.core.sales.dto.SalesOrderPageQuery;
import com.ailearn.platform.core.sales.dto.SalesOrderView;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 查询销售订单生命周期与履约数量的 AI 只读工具。 */
@Component
public class QuerySalesOrderStatusAiTool implements AiReadTool {
    private static final Set<String> ALLOWED_ARGUMENTS = Set.of("order_id", "order_no");
    private final SalesOrderApplicationService salesOrderService;

    /** 注入现有销售订单应用服务。 */
    public QuerySalesOrderStatusAiTool(SalesOrderApplicationService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @Override public String name() { return "querySalesOrderStatus"; }

    @Override public String description() {
        return "按销售订单 UUID 或单号查询订单状态、履约状态和各明细订购/预留/拣货/发货数量";
    }

    @Override public Map<String, Object> parametersSchema() {
        return Map.of("type", "object", "additionalProperties", false,
                "properties", Map.of(
                        "order_id", Map.of("type", "string", "format", "uuid"),
                        "order_no", Map.of("type", "string", "maxLength", 128)),
                "anyOf", List.of(Map.of("required", List.of("order_id")),
                        Map.of("required", List.of("order_no"))));
    }

    @Override public Set<String> anyOfPermissions() { return Set.of("sales:order:view"); }

    /** 查询并裁剪销售订单事实，不返回允许写动作或原始完成会话。 */
    @Override
    public AiToolResult execute(AiRequestContext context, Map<String, Object> arguments) {
        Map<String, Object> safe = AiToolArguments.safe(arguments);
        AiToolArguments.rejectUnknown(safe, ALLOWED_ARGUMENTS, name());
        UUID orderId = AiToolArguments.optionalUuid(safe, "order_id");
        String orderNo = AiToolArguments.optionalText(safe, "order_no");
        if (orderId == null && orderNo == null) {
            throw new IllegalArgumentException("order_id 和 order_no 至少提供一个");
        }
        SalesOrderView order = orderId != null ? salesOrderService.detail(orderId) : findByNo(orderNo);
        List<SalesLineFact> lines = order.getLines().stream().map(QuerySalesOrderStatusAiTool::line).toList();
        SalesOrderStatusFact fact = new SalesOrderStatusFact(order.getId(), order.getSoNo(),
                order.getCustomerCode(), order.getCustomerName(), order.getPlannedShipDate(),
                order.getStatus(), order.getFulfillmentStatus(), order.getCompletionType(),
                order.getCompletionReason(), order.getCompletedAt(), lines);
        String time = order.getCompletedAt() == null ? "" : "订单完成于 " + order.getCompletedAt();
        return new AiToolResult(name(), fact, "sales order application: " + order.getSoNo(),
                time, List.of(), AiToolStatus.Success);
    }

    private SalesOrderView findByNo(String orderNo) {
        SalesOrderPageQuery query = new SalesOrderPageQuery();
        query.setKeyword(orderNo);
        query.setPage(1);
        query.setSize(20);
        List<SalesOrderView> exact = salesOrderService.page(query).records().stream()
                .filter(item -> orderNo.equalsIgnoreCase(item.getSoNo())).toList();
        if (exact.size() != 1) {
            throw new IllegalArgumentException("未找到唯一销售订单: " + orderNo);
        }
        return salesOrderService.detail(exact.getFirst().getId());
    }

    private static SalesLineFact line(SalesOrderLineView value) {
        return new SalesLineFact(value.lineNo(), value.productId(), value.sku(), value.productName(), value.uom(),
                value.orderedQty(), value.reservedQty(), value.pickedQty(), value.shippedQty(),
                value.unreservedQty(), value.unpickedQty(), value.unshippedQty());
    }

    /** 面向模型的销售订单状态事实，不含写能力和原始会话信息。 */
    public record SalesOrderStatusFact(UUID orderId, String orderNo, String customerCode, String customerName,
                                       LocalDate plannedShipDate, String orderStatus, String fulfillmentStatus,
                                       String completionType, String completionReason, OffsetDateTime completedAt,
                                       List<SalesLineFact> lines) { }

    /** 面向模型的销售明细数量口径。 */
    public record SalesLineFact(int lineNo, UUID productId, String sku, String productName, String uom,
                                String orderedQty, String reservedQty, String pickedQty, String shippedQty,
                                String unreservedQty, String unpickedQty, String unshippedQty) { }
}
