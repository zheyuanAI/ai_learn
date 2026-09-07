package com.ailearn.platform.core.sales.fulfillment.controller;

import com.ailearn.platform.core.sales.dto.PickTaskConfirmRequest;
import com.ailearn.platform.core.sales.dto.PickTaskReturnRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderPageQuery;
import com.ailearn.platform.core.sales.dto.SalesOrderPageResult;
import com.ailearn.platform.core.sales.application.SalesOrderApplicationService;
import com.ailearn.platform.core.sales.dto.ReservationReleaseRequest;
import com.ailearn.platform.core.sales.dto.SalesFulfillmentResult;
import com.ailearn.platform.core.sales.dto.ShipmentConfirmRequest;
import com.ailearn.platform.core.sales.fulfillment.application.SalesFulfillmentApplicationService;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final SalesOrderApplicationService salesOrderApplicationService;

    /** 注入销售履约和订单只读应用端口。 */
    public SalesFulfillmentController(SalesFulfillmentApplicationService applicationService,
                                      SalesOrderApplicationService salesOrderApplicationService) {
        this.applicationService = applicationService;
        this.salesOrderApplicationService = salesOrderApplicationService;
    }

    /**
     * 查询订单驱动的拣货任务队列。
     * 当前版本没有单独的 pick_task 事实表；列表返回销售订单分页，写操作路径 ID 仅作为履约操作事实标识，
     * 请求体中的 salesOrderId 才是被履约的销售订单标识。
     */
    @GetMapping("/pick-tasks")
    public ApiResponse<SalesOrderPageResult> pagePickTasks(@ModelAttribute SalesOrderPageQuery query) {
        return ApiResponse.success(salesOrderApplicationService.page(query));
    }

    /** 确认直接拣货。 */
    @PostMapping("/pick-tasks/{id}/confirm")
    public ApiResponse<SalesFulfillmentResult> confirmPick(
            @PathVariable("id") UUID id, @RequestBody PickTaskConfirmRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirmPick(id, request, idempotencyKey));
    }

    /** 退回未发货拣货。 */
    @PostMapping("/pick-tasks/{id}/return")
    public ApiResponse<SalesFulfillmentResult> returnPick(
            @PathVariable("id") UUID id, @RequestBody PickTaskReturnRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.returnPick(id, request, idempotencyKey));
    }

    /** 释放订单行尚未拣货的预留。 */
    @PostMapping("/sales-orders/{id}/reservations/release")
    public ApiResponse<SalesFulfillmentResult> releaseReservations(
            @PathVariable("id") UUID id, @RequestBody ReservationReleaseRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.releaseReservations(id, request, idempotencyKey));
    }

    /** 确认销售发货。 */
    @PostMapping("/sales-shipments/{id}/confirm")
    public ApiResponse<SalesFulfillmentResult> confirmShipment(
            @PathVariable("id") UUID id, @RequestBody ShipmentConfirmRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.confirmShipment(id, request, idempotencyKey));
    }

}
