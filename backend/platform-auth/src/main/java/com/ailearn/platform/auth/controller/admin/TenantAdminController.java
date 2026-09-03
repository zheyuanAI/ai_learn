package com.ailearn.platform.auth.controller.admin;

import com.ailearn.platform.auth.domain.dto.admin.TenantUpdateRequest;
import com.ailearn.platform.auth.domain.vo.admin.TenantAdminVo;
import com.ailearn.platform.auth.service.admin.TenantAdminService;
import com.ailearn.platform.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户后台管理 REST 控制器。
 * <p>
 * 提供当前租户信息查看与基本属性维护端点。
 * </p>
 */
@Tag(name = "租户后台管理", description = "提供当前租户画像查询与属性修改端点")
@RestController
@RequestMapping("/api/auth/admin/tenants/current")
public class TenantAdminController {

    private final TenantAdminService tenantAdminService;

    public TenantAdminController(TenantAdminService tenantAdminService) {
        this.tenantAdminService = tenantAdminService;
    }

    /**
     * 获取当前租户详情信息。
     * <p>
     * 【用途】供管理后台展示当前登录租户的基本配置。
     * 主要入参：无（严格基于请求头或 JWT 上下文解析租户 ID）；
     * 返回结果：ApiResponse&lt;TenantAdminVo&gt; 租户画像；
     * 简要流程：调用 service.getCurrentTenantDetail() 读取租户数据并封装响应。
     * </p>
     *
     * @return 统一响应包装的租户视图对象
     */
    @Operation(summary = "获取当前租户详情", description = "读取当前安全上下文所属租户的基础信息与运行状态")
    @GetMapping
    public ApiResponse<TenantAdminVo> getTenantDetail() {
        TenantAdminVo vo = tenantAdminService.getCurrentTenantDetail();
        return ApiResponse.ok(vo);
    }

    /**
     * 修改当前租户基本信息。
     * <p>
     * 【用途】供管理员更新企业租户名称或状态。
     * 主要入参：request (租户更新请求体)；
     * 返回结果：ApiResponse&lt;TenantAdminVo&gt; 更新后的租户画像；
     * 简要流程：参数校验 -> 调用 service.updateCurrentTenant() 持久化修改。
     * </p>
     *
     * @param request 租户更新请求参数
     * @return 统一响应包装的更新后租户视图对象
     */
    @Operation(summary = "修改当前租户信息", description = "更新当前租户的名称与启用状态")
    @PutMapping
    public ApiResponse<TenantAdminVo> updateTenant(@Valid @RequestBody TenantUpdateRequest request) {
        TenantAdminVo vo = tenantAdminService.updateCurrentTenant(request);
        return ApiResponse.ok(vo);
    }
}
