package com.ailearn.platform.core.masterdata.controller;

import com.ailearn.platform.core.masterdata.application.UomApplicationService;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageQuery;
import com.ailearn.platform.core.masterdata.dto.MasterDataPageResult;
import com.ailearn.platform.core.masterdata.dto.StatusChangeRequest;
import com.ailearn.platform.core.masterdata.dto.UomSaveRequest;
import com.ailearn.platform.core.masterdata.dto.UomView;
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
 * 计量单位主数据 REST API。
 */
@RestController
@RequestMapping("/api/uoms")
public class UomController {

    private final UomApplicationService applicationService;

    public UomController(UomApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询当前租户计量单位。
     *
     * @param query 分页查询参数
     * @return 统一分页响应
     */
    @GetMapping
    public ApiResponse<MasterDataPageResult<UomView>> page(@ModelAttribute MasterDataPageQuery query) {
        return ApiResponse.success(applicationService.page(query));
    }

    /**
     * 查询计量单位详情。
     *
     * @param id 计量单位 ID
     * @return 统一详情响应
     */
    @GetMapping("/{id}")
    public ApiResponse<UomView> detail(@PathVariable UUID id) {
        return ApiResponse.success(applicationService.detail(id));
    }

    /**
     * 创建计量单位。
     *
     * @param request 创建请求
     * @return 创建后的详情
     */
    @PostMapping
    public ApiResponse<UomView> create(@RequestBody UomSaveRequest request,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.create(request, idempotencyKey));
    }

    /**
     * 修改计量单位。
     *
     * @param id 计量单位 ID
     * @param request 修改请求
     * @return 修改后的详情
     */
    @PutMapping("/{id}")
    public ApiResponse<UomView> update(@PathVariable UUID id, @RequestBody UomSaveRequest request,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.update(id, request, idempotencyKey));
    }

    /**
     * 变更计量单位状态。
     *
     * @param id 计量单位 ID
     * @param request 目标状态
     * @return 状态变更后的详情
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<UomView> changeStatus(@PathVariable UUID id, @RequestBody StatusChangeRequest request,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.success(applicationService.changeStatus(id, request, idempotencyKey));
    }

    /**
     * 逻辑删除计量单位。
     *
     * @param id 计量单位 ID
     * @return 空数据成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String idempotencyKey) {
        applicationService.delete(id, idempotencyKey);
        return ApiResponse.success();
    }
}
