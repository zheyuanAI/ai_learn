package com.ailearn.platform.auth.controller;

import com.ailearn.platform.auth.domain.dto.LoginRequest;
import com.ailearn.platform.auth.domain.vo.LoginResponse;
import com.ailearn.platform.auth.security.jwt.RsaKeyProvider;
import com.ailearn.platform.auth.service.AuthService;
import com.ailearn.platform.shared.api.ApiResponse;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与授权基础端点控制器。
 */
@Tag(name = "认证服务接口", description = "提供用户登录、注销及 RSA JWKS 公钥端点")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RsaKeyProvider rsaKeyProvider;

    public AuthController(AuthService authService, RsaKeyProvider rsaKeyProvider) {
        this.authService = authService;
        this.rsaKeyProvider = rsaKeyProvider;
    }

    /**
     * 用户统一登录接口。
     * <p>
     * 校验所属租户与账密，废弃旧会话并建立新有效会话，签发 RSA JWT。
     * </p>
     *
     * @param request 登录参数（tenantCode, username, password）
     * @param httpRequest HTTP 请求对象
     * @return 包含 Token、JTI 及用户信息的响应
     */
    @Operation(summary = "用户登录认证", description = "根据租户编码和用户名密码完成登录，签发 JWT 并记录单账号单会话状态")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.login(request, ipAddress, userAgent);
        return ApiResponse.success("登录成功", response);
    }

    /**
     * 用户注销接口。
     * <p>
     * 撤销当前用户的活跃会话记录并清除 Redis 缓存。
     * </p>
     *
     * @return 成功响应
     */
    @Operation(summary = "用户注销登出", description = "使当前登录账号的会话立即失效，并清除服务端权限缓存")
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        UUID userId = UserContextHolder.requireUserId();
        UUID tenantId = TenantContextHolder.requireTenantId();
        authService.logout(userId, tenantId);
        return ApiResponse.success("注销成功", null);
    }

    /**
     * 暴露 RSA 公钥集合（JWKS 端点）。
     * <p>
     * 遵循 RFC 7517 标准，供 API 网关或其他微服务拉取公钥进行离线 JWT 验签。
     * </p>
     *
     * @return 符合标准格式的 JWKS JSON 对象
     */
    @Operation(summary = "获取 RSA 公钥集合 (JWKS)", description = "供网关或下游服务拉取 RSA 公钥进行 JWT 验签")
    @GetMapping("/jwks")
    public Map<String, Object> getJwks() {
        return rsaKeyProvider.getJwksJson();
    }
}
