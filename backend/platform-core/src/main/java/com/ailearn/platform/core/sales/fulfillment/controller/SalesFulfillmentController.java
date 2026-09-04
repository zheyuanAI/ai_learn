package com.ailearn.platform.core.sales.fulfillment.controller;

import com.ailearn.platform.core.sales.dto.PickTaskConfirmRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnRequest;
import com.ailearn.platform.core.sales.dto.ReservationReleaseRequest;
import com.ailearn.platform.core.sales.dto.SalesFulfillmentResult;
import com.ailearn.platform.core.sales.dto.ShipmentConfirmRequest;
import com.ailearn.platform.core.sales.fulfillment.application.SalesFulfillmentApplicationService;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 销售履约 REST API；控制器只转发请求，租户、权限和状态校验由应用服务完成。
 */
@RestController
@RequestMapping("/api")
public class SalesFulfillmentController {

    private final SalesFulfillmentApplicationService applicationService;

    /** 注入销售履约应用端口。 */
    public SalesFulfillmentController(SalesFulfillmentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 确认直接拣货。 */
    @PostMapping("/pick-tasks/{id}/confirm")
    public ApiResponse<SalesFulfillmentResult> confirmPick(
            @PathVariable UUID id, @RequestBody PickTaskConfirmRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirmPick(id, request, idempotencyKey));
    }

    /** 退回未发货拣货。 */
    @PostMapping("/pick-tasks/{id}/return")
    public ApiResponse<SalesFulfillmentResult> returnPick(
            @PathVariable UUID id, @RequestBody PickTaskReturnRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.returnPick(id, request, idempotencyKey));
    }

    /** 释放订单行尚未拣货的预留。 */
    @PostMapping("/sales-orders/{id}/reservations/release")
    public ApiResponse<SalesFulfillmentResult> releaseReservations(
            @PathVariable UUID id, @RequestBody ReservationReleaseRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.releaseReservations(id, request, idempotencyKey));
    }

    /** 确认销售发货。 */
    @PostMapping("/sales-shipments/{id}/confirm")
    public ApiResponse<SalesFulfillmentResult> confirmShipment(
            @PathVariable UUID id, @RequestBody ShipmentConfirmRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirmShipment(id, request, idempotencyKey));
    }
}
