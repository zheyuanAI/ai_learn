package com.ailearn.platform.core.transfer.dto;

import com.ailearn.platform.core.transfer.domain.TransferLine;
import com.ailearn.platform.core.transfer.domain.TransferOrder;
import com.ailearn.platform.core.masterdata.dto.AllowedActionVo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 调拨单响应，数量以字符串输出并携带后端允许动作和库存流水标识。
 */
public class TransferView {
    private UUID id;
    private String transferNo;
    private UUID fromWarehouseId;
    private UUID fromLocationId;
    private UUID toWarehouseId;
    private UUID toLocationId;
    private String status;
    private long version;
    private UUID confirmedBy;
    private OffsetDateTime confirmedAt;
    private List<TransferLineView> lines;
    private List<UUID> transactionIds;
    private List<AllowedActionVo> allowedActions;

    public TransferView() {
    }

    /**
     * 从调拨聚合构造响应视图。
     *
     * @param order 调拨聚合
     * @param transactionIds 本次确认产生的库存流水 ID
     * @param allowedActions 后端计算的允许动作
     */
    public TransferView(TransferOrder order, List<UUID> transactionIds, List<AllowedActionVo> allowedActions) {
        this.id = order.id();
        this.transferNo = order.transferNo();
        this.fromWarehouseId = order.fromWarehouseId();
        this.fromLocationId = order.fromLocationId();
        this.toWarehouseId = order.toWarehouseId();
        this.toLocationId = order.toLocationId();
        this.status = order.status().name();
        this.version = order.version();
        this.confirmedBy = order.confirmedBy();
        this.confirmedAt = order.confirmedAt();
        this.lines = order.lines().stream().map(TransferLineView::from).toList();
        this.transactionIds = transactionIds == null ? List.of() : List.copyOf(transactionIds);
        this.allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public UUID getId() { return id; }
    public String getTransferNo() { return transferNo; }
    public UUID getFromWarehouseId() { return fromWarehouseId; }
    public UUID getFromLocationId() { return fromLocationId; }
    public UUID getToWarehouseId() { return toWarehouseId; }
    public UUID getToLocationId() { return toLocationId; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
    public UUID getConfirmedBy() { return confirmedBy; }
    public OffsetDateTime getConfirmedAt() { return confirmedAt; }
    public List<TransferLineView> getLines() { return lines; }
    public List<UUID> getTransactionIds() { return transactionIds; }
    public List<AllowedActionVo> getAllowedActions() { return allowedActions; }

    /**
     * 调拨明细响应。
     */
    public record TransferLineView(UUID id, int lineNo, UUID productId, String lotNo,
                                   String uom, String quantity) {
        private static TransferLineView from(TransferLine line) {
            return new TransferLineView(line.id(), line.lineNo(), line.productId(), line.lotNo(),
                    line.uom(), line.quantity().toPlainString());
        }
    }
}
