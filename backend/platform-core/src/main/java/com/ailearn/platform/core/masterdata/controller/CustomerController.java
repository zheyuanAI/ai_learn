package com.ailearn.platform.core.masterdata.controller;

import com.ailearn.platform.core.masterdata.application.CustomerApplicationService;
import com.ailearn.platform.core.masterdata.dto.CustomerSaveRequest;
import com.ailearn.platform.core.masterdata.dto.CustomerView;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.shared.api.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户主数据 REST API。
 */
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerApplicationService applicationService;

    public CustomerController(CustomerApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询当前租户客户。
     *
     * @param query 分页查询参数
     * @return 统一分页响应
     */
    @GetMapping
    public ApiResponse<MasterDataPageResult<CustomerView>> page(@ModelAttribute MasterDataPageQuery query) {
        return ApiResponse.success(applicationService.page(query));
    }

    /**
     * 查询客户详情。
     *
     * @param id 客户 ID
     * @return 统一详情响应
     */
    @GetMapping("/{id}")
    public ApiResponse<CustomerView> detail(@PathVariable UUID id) {
        return ApiResponse.success(applicationService.detail(id));
    }

    /**
     * 创建客户。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    @PostMapping
    public ApiResponse<CustomerView> create(@RequestBody CustomerSaveRequest request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.create(request, idempotencyKey));
    }

    /**
     * 修改客户。
     *
     * @param id 客户 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    @PutMapping("/{id}")
    public ApiResponse<CustomerView> update(@PathVariable UUID id, @RequestBody CustomerSaveRequest request,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.update(id, request, idempotencyKey));
    }

    /**
     * 变更客户状态。
     *
     * @param id 客户 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<CustomerView> changeStatus(@PathVariable UUID id, @RequestBody StatusChangeRequest request,
                                                  @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.changeStatus(id, request, idempotencyKey));
    }

    /**
     * 逻辑删除客户。
     *
     * @param id 客户 ID
     * @return 空数据成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        applicationService.delete(id, idempotencyKey);
        return ApiResponse.success();
    }
}
