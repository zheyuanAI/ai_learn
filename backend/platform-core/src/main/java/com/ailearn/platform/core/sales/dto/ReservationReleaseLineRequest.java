package com.ailearn.platform.core.sales.dto;

import java.util.UUID;

/** 未拣预留释放明细。 */
public class ReservationReleaseLineRequest {
    private UUID salesOrderLineId;
    private String releaseQty;
    private String reason;

    public UUID getSalesOrderLineId() { return salesOrderLineId; }
    public String getReleaseQty() { return releaseQty; }
    public String getReason() { return reason; }
    public void setSalesOrderLineId(UUID value) { this.salesOrderLineId = value; }
    public void setReleaseQty(String value) { this.releaseQty = value; }
    public void setReason(String value) { this.reason = value; }
}
