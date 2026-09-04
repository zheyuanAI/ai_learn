package com.ailearn.platform.core.stocktake.dto;

import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import com.ailearn.platform.core.stocktake.domain.StocktakeLine;
import com.ailearn.platform.core.stocktake.domain.StocktakeOrder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 盘点单响应；数量以字符串输出，并携带后端计算的允许动作和调整流水。
 */
public class StocktakeView {

    private UUID id;
    private String stocktakeNo;
    private UUID warehouseId;
    private UUID locationId;
    private String status;
    private long version;
    private UUID startedBy;
    private OffsetDateTime startedAt;
    private UUID confirmedBy;
    private OffsetDateTime confirmedAt;
    private List<StocktakeLineView> lines;
    private List<UUID> transactionIds;
    private List<AllowedActionVo> allowedActions;

    public StocktakeView() {
    }

    /**
     * 从盘点聚合构造响应。
     *
     * @param order 盘点聚合
     * @param transactionIds 本次确认产生的调整流水
     * @param allowedActions 当前状态允许动作
     */
    public StocktakeView(StocktakeOrder order, List<UUID> transactionIds,
                         List<AllowedActionVo> allowedActions) {
        this.id = order.id();
        this.stocktakeNo = order.stocktakeNo();
        this.warehouseId = order.warehouseId();
        this.locationId = order.locationId();
        this.status = order.status().name();
        this.version = order.version();
        this.startedBy = order.startedBy();
        this.startedAt = order.startedAt();
        this.confirmedBy = order.confirmedBy();
        this.confirmedAt = order.confirmedAt();
        this.lines = order.lines().stream().map(StocktakeLineView::from).toList();
        this.transactionIds = transactionIds == null ? List.of() : List.copyOf(transactionIds);
        this.allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public UUID getId() { return id; }
    public String getStocktakeNo() { return stocktakeNo; }
    public UUID getWarehouseId() { return warehouseId; }
    public UUID getLocationId() { return locationId; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
    public UUID getStartedBy() { return startedBy; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public List<StocktakeLineView> getLines() { return lines; }
    public List<UUID> getTransactionIds() { return transactionIds; }
    public List<AllowedActionVo> getAllowedActions() { return allowedActions; }

    /**
     * 盘点明细响应。
     */
    public record StocktakeLineView(UUID id, int lineNo, UUID productId, UUID warehouseId,
                                    UUID locationId, String lotNo, String systemQty,
                                    long systemBalanceVersion, String countedQty,
                                    String varianceQty, String varianceReason,
                                    UUID adjustmentTransactionId) {

        private static StocktakeLineView from(StocktakeLine line) {
            BigDecimal variance = line.variance();
            return new StocktakeLineView(line.id(), line.lineNo(), line.productId(), line.warehouseId(),
                    line.locationId(), line.lotNo(), line.systemQty().toPlainString(),
                    line.systemBalanceVersion(), line.countedQty() == null ? null : line.countedQty().toPlainString(),
                    variance == null ? null : variance.toPlainString(), line.varianceReason(),
                    line.adjustmentTransactionId());
        }
    }
}
