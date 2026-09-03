package com.ailearn.platform.auth.controller;

import com.ailearn.platform.auth.domain.vo.MenuNodeVo;
import com.ailearn.platform.auth.domain.vo.UserProfileVo;
import com.ailearn.platform.auth.service.AuthService;
import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户信息与权限菜单控制器。
 */
@Tag(name = "当前用户接口", description = "获取当前登录用户的资料、角色、权限点与动态菜单树")
@RestController
@RequestMapping("/api/me")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 获取当前登录用户的基本信息、角色列表与功能权限点集合。
     *
     * @return 用户全量画像与权限点响应
     */
    @Operation(summary = "获取当前用户信息与权限", description = "根据受信任的请求上下文获取用户资料、分配角色及权限点清单")
    @GetMapping
    public ApiResponse<UserProfileVo> getCurrentUser() {
        UUID userId = UserContextHolder.requireUserId();
        UUID tenantId = TenantContextHolder.requireTenantId();
        UserProfileVo profile = authService.getCurrentUserProfile(userId, tenantId);
        return ApiResponse.success(profile);
    }

    /**
     * 获取当前登录用户角色对应的动态菜单树。
     *
     * @return 嵌套组织的动态菜单树结构列表
     */
    @Operation(summary = "获取当前用户动态菜单树", description = "获取当前用户所有角色授权的动态菜单树（含路由、组件、图标与层级关系）")
    @GetMapping("/menus")
    public ApiResponse<List<MenuNodeVo>> getCurrentUserMenus() {
        UUID userId = UserContextHolder.requireUserId();
        UUID tenantId = TenantContextHolder.requireTenantId();
        List<MenuNodeVo> menus = authService.getCurrentUserMenus(userId, tenantId);
        return ApiResponse.success(menus);
    }
}
