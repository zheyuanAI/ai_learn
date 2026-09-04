package com.ailearn.platform.core.purchasing.controller;

import com.ailearn.platform.core.purchasing.application.PurchaseOrderApplicationService;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderCompleteRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderPageQuery;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderPageResult;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderSaveRequest;
import com.ailearn.platform.core.purchasing.dto.PurchaseOrderView;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采购订单 REST API。
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderApplicationService applicationService;

    /**
     * 注入采购订单应用服务。
     */
    public PurchaseOrderController(PurchaseOrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前租户采购订单。
     */
    @GetMapping
    public ApiResponse<PurchaseOrderPageResult> page(@ModelAttribute PurchaseOrderPageQuery query) {
        return ApiResponse.success(applicationService.page(query));
    }

    /**
     * 查询采购订单详情。
     */
    @GetMapping("/{id}")
    public ApiResponse<PurchaseOrderView> detail(@PathVariable UUID id) {
        return ApiResponse.success(applicationService.detail(id));
    }

    /**
     * 创建 Draft 采购订单。
     */
    @PostMapping
    public ApiResponse<PurchaseOrderView> create(@RequestBody PurchaseOrderSaveRequest request,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.create(request, idempotencyKey));
    }

    /**
     * 修改 Draft 采购订单。
     */
    @PutMapping("/{id}")
    public ApiResponse<PurchaseOrderView> update(@PathVariable UUID id,
                                                 @RequestBody PurchaseOrderSaveRequest request,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.update(id, request, idempotencyKey));
    }

    /**
     * 提交采购订单。
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<PurchaseOrderView> submit(@PathVariable UUID id,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.submit(id, idempotencyKey));
    }

    /**
     * 审核采购订单。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<PurchaseOrderView> approve(@PathVariable UUID id,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.approve(id, idempotencyKey));
    }

    /**
     * 人工完成采购订单。
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<PurchaseOrderView> manuallyComplete(@PathVariable UUID id,
                                                            @RequestBody PurchaseOrderCompleteRequest request,
                                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.manuallyComplete(id, request, idempotencyKey));
    }
}
