package com.ailearn.platform.core.sales.controller;

import com.ailearn.platform.core.sales.application.SalesOrderApplicationService;
import com.ailearn.platform.core.sales.fulfillment.application.SalesFulfillmentApplicationService;
import com.ailearn.platform.core.sales.dto.SalesOrderCompleteRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderPageQuery;
import com.ailearn.platform.core.sales.dto.SalesOrderPageResult;
import com.ailearn.platform.core.sales.dto.SalesOrderSaveRequest;
import com.ailearn.platform.core.sales.dto.SalesOrderView;
import com.ailearn.platform.core.sales.dto.SalesFulfillmentResult;
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
 * 销售订单基础 REST API。
 */
@RestController
@RequestMapping("/api/sales-orders")
public class SalesOrderController {

    private final SalesOrderApplicationService applicationService;
    private final SalesFulfillmentApplicationService fulfillmentService;

    /**
     * 注入销售订单应用服务。
     *
     * @param applicationService 销售订单应用服务
     */
    public SalesOrderController(SalesOrderApplicationService applicationService,
                                SalesFulfillmentApplicationService fulfillmentService) {
        this.applicationService = applicationService;
        this.fulfillmentService = fulfillmentService;
    }

    /**
     * 查询当前租户销售订单。
     *
     * @param query 查询条件
     * @return 分页订单
     */
    @GetMapping
    public ApiResponse<SalesOrderPageResult> page(@ModelAttribute SalesOrderPageQuery query) {
        return ApiResponse.success(applicationService.page(query));
    }

    /**
     * 查询销售订单详情。
     *
     * @param id 订单 ID
     * @return 订单详情
     */
    @GetMapping("/{id}")
    public ApiResponse<SalesOrderView> detail(@PathVariable UUID id) {
        return ApiResponse.success(applicationService.detail(id));
    }

    /**
     * 创建 Draft 订单。
     *
     * @param request 创建请求
     * @param idempotencyKey HTTP 幂等键
     * @return Draft 订单
     */
    @PostMapping
    public ApiResponse<SalesOrderView> create(@RequestBody SalesOrderSaveRequest request,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.create(request, idempotencyKey));
    }

    /**
     * 修改 Draft 订单。
     *
     * @param id 订单 ID
     * @param request 修改请求
     * @param idempotencyKey HTTP 幂等键
     * @return 修改后的订单
     */
    @PutMapping("/{id}")
    public ApiResponse<SalesOrderView> update(@PathVariable UUID id, @RequestBody SalesOrderSaveRequest request,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.update(id, request, idempotencyKey));
    }

    /**
     * 提交订单。
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<SalesOrderView> submit(@PathVariable UUID id,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.submit(id, idempotencyKey));
    }

    /**
     * 审核订单；审核不自动预留库存。
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<SalesOrderView> approve(@PathVariable UUID id,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.approve(id, idempotencyKey));
    }

    /**
     * 人工完成订单；原因由请求提交，账号、会话和时间由可信上下文生成。
     */
    @PostMapping("/{id}/complete")
    public ApiResponse<SalesFulfillmentResult> manuallyComplete(@PathVariable UUID id,
                                                                @RequestBody SalesOrderCompleteRequest request,
                                                                @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(fulfillmentService.manuallyComplete(id, request, idempotencyKey));
    }

}
