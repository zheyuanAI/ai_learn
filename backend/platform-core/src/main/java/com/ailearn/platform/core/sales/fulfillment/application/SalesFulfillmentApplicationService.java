package com.ailearn.platform.core.sales.fulfillment.application;

import com.ailearn.platform.core.sales.dto.PickTaskConfirmRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnRequest;
import com.ailearn.platform.core.sales.dto.ReservationReleaseRequest;
import com.ailearn.platform.core.sales.dto.SalesFulfillmentResult;
import com.ailearn.platform.core.sales.dto.SalesOrderCompleteRequest;
import com.ailearn.platform.core.sales.dto.ShipmentConfirmRequest;
import java.util.UUID;

/**
 * 销售履约应用端口；所有库存变化必须经由 InventoryCommandService。
 */
public interface SalesFulfillmentApplicationService {

    /** 确认直接拣货。 */
    SalesFulfillmentResult confirmPick(UUID pickTaskId, PickTaskConfirmRequest request, String idempotencyKey);

    /**
     * 服务端分配直接拣货操作 ID并确认拣货；客户端只提交销售订单事实和幂等键。
     */
    SalesFulfillmentResult confirmPick(PickTaskConfirmRequest request, String idempotencyKey);

    /** 退回尚未发货的拣货。 */
    SalesFulfillmentResult returnPick(UUID pickTaskId, PickTaskReturnRequest request, String idempotencyKey);

    /**
     * 服务端分配拣货退回操作 ID并确认退回；客户端只提交销售订单事实和幂等键。
     */
    SalesFulfillmentResult returnPick(PickTaskReturnRequest request, String idempotencyKey);

    /** 释放尚未拣货的业务预留。 */
    SalesFulfillmentResult releaseReservations(UUID salesOrderId, ReservationReleaseRequest request,
                                               String idempotencyKey);

    /** 确认发货。 */
    SalesFulfillmentResult confirmShipment(UUID shipmentId, ShipmentConfirmRequest request, String idempotencyKey);

    /**
     * 服务端分配发货操作 ID并确认发货；客户端只提交销售订单事实和幂等键。
     */
    SalesFulfillmentResult confirmShipment(ShipmentConfirmRequest request, String idempotencyKey);

    /** 释放剩余未拣预留后人工完成销售订单。 */
    SalesFulfillmentResult manuallyComplete(UUID salesOrderId, SalesOrderCompleteRequest request,
                                            String idempotencyKey);
}
