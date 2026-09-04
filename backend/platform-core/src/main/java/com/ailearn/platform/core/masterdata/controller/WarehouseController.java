package com.ailearn.platform.core.masterdata.controller;

import com.ailearn.platform.core.masterdata.application.WarehouseApplicationService;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.core.masterdata.dto.WarehouseSaveRequest;
import com.ailearn.platform.core.masterdata.dto.WarehouseView;
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
 * 仓库主数据 REST API。
 */
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseApplicationService applicationService;

    public WarehouseController(WarehouseApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询当前租户仓库。
     *
     * @param query 分页查询参数
     * @return 统一分页响应
     */
    @GetMapping
    public ApiResponse<MasterDataPageResult<WarehouseView>> page(@ModelAttribute MasterDataPageQuery query) {
        return ApiResponse.success(applicationService.page(query));
    }

    /**
     * 查询仓库详情。
     *
     * @param id 仓库 ID
     * @return 统一详情响应
     */
    @GetMapping("/{id}")
    public ApiResponse<WarehouseView> detail(@PathVariable UUID id) {
        return ApiResponse.success(applicationService.detail(id));
    }

    /**
     * 创建仓库。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    @PostMapping
    public ApiResponse<WarehouseView> create(@RequestBody WarehouseSaveRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.create(request, idempotencyKey));
    }

    /**
     * 修改仓库。
     *
     * @param id 仓库 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    @PutMapping("/{id}")
    public ApiResponse<WarehouseView> update(@PathVariable UUID id, @RequestBody WarehouseSaveRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.update(id, request, idempotencyKey));
    }

    /**
     * 变更仓库状态。
     *
     * @param id 仓库 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<WarehouseView> changeStatus(@PathVariable UUID id, @RequestBody StatusChangeRequest request,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.changeStatus(id, request, idempotencyKey));
    }

    /**
     * 逻辑删除仓库。
     *
     * @param id 仓库 ID
     * @return 空数据成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        applicationService.delete(id, idempotencyKey);
        return ApiResponse.success();
    }
}
