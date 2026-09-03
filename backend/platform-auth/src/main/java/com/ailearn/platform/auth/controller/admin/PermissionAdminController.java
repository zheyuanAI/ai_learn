package com.ailearn.platform.auth.controller.admin;

import com.ailearn.platform.auth.domain.dto.admin.PermissionQueryRequest;
import com.ailearn.platform.auth.domain.vo.admin.PermissionAdminVo;
import com.ailearn.platform.auth.service.admin.PermissionAdminService;
import com.ailearn.platform.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限点后台管理 REST 控制器。
 * <p>
 * 提供系统功能权限点字典目录的只读检索端点。
 * </p>
 */
@Tag(name = "权限后台管理", description = "提供系统功能权限点字典检索端点")
@RestController
@RequestMapping("/api/auth/admin/permissions")
public class PermissionAdminController {

    private final PermissionAdminService permissionAdminService;

    public PermissionAdminController(PermissionAdminService permissionAdminService) {
        this.permissionAdminService = permissionAdminService;
    }

    /**
     * 查询系统功能权限点字典列表。
     * <p>
     * 【用途】供管理后台权限清单页面或角色授权弹窗展示可选权限树/列表。
     * 主要入参：request (module, permissionCode, permissionName)；
     * 返回结果：ApiResponse&lt;List&lt;PermissionAdminVo&gt;&gt; 权限点列表；
     * 简要流程：调用 permissionAdminService.listPermissions(request)。
     * </p>
     *
     * @param request 权限点查询筛选参数
     * @return 统一响应包装的权限点列表
     */
    @Operation(summary = "查询权限点列表", description = "支持按所属业务模块、权限编码与权限名称检索系统预置的功能权限点")
    @GetMapping
    public ApiResponse<List<PermissionAdminVo>> listPermissions(@Valid PermissionQueryRequest request) {
        List<PermissionAdminVo> list = permissionAdminService.listPermissions(request);
        return ApiResponse.ok(list);
    }
}
