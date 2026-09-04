package com.ailearn.platform.auth;

import com.ailearn.platform.auth.domain.dto.LoginRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.TenantUpdateRequest;
import com.ailearn.platform.auth.domain.entity.Menu;
import com.ailearn.platform.auth.domain.entity.Tenant;
import com.ailearn.platform.auth.domain.vo.LoginResponse;
import com.ailearn.platform.auth.domain.vo.MenuNodeVo;
import com.ailearn.platform.auth.domain.vo.UserProfileVo;
import com.ailearn.platform.auth.security.jwt.JwtTokenService;
import com.ailearn.platform.shared.api.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * platform-auth 认证与权限集成测试套件。
 * <p>
 * 覆盖：
 * 1. 登录成功与 JWT 最小不可变 Claims 校验；
 * 2. 账号密码错误拦截；
 * 3. 租户不存在拦截；
 * 4. 单账号单有效会话控制（后登顶前，旧 JTI 返回 401）；
 * 5. GET /api/me 获取当前用户基本信息、角色与权限点列表；
 * 6. GET /api/me/menus 获取动态菜单树；
 * 7. GET /api/auth/jwks 暴露 RSA 公钥；
 * 8. POST /api/auth/logout 主动注销与会话销毁。
 * </p>
 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.ailearn.platform.auth.mapper.UserMapper userMapper;

    @Autowired
    private com.ailearn.platform.auth.mapper.MenuMapper menuMapper;

    @Autowired
    private com.ailearn.platform.auth.mapper.TenantMapper tenantMapper;

    @Autowired
    private com.ailearn.platform.auth.service.SessionCacheService sessionCacheService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        String encoded = passwordEncoder.encode("123456");
        for (com.ailearn.platform.auth.domain.entity.User user : userMapper.selectList(null)) {
            user.setPasswordHash(encoded);
            userMapper.updateById(user);
        }
    }

    /**
     * 辅助登录方法。
     */
    private LoginResponse doLogin(String tenantCode, String username, String password) throws Exception {
        LoginRequest req = new LoginRequest(tenantCode, username, password);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<LoginResponse> response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<LoginResponse>>() {}
        );
        return response.getData();
    }

    @Test
    @DisplayName("测试1：租户管理员正常登录并核验证书载荷最小化原则")
    void testLoginSuccessAndVerifyMinimalJwtClaims() throws Exception {
        LoginRequest req = new LoginRequest("DEFAULT", "admin.zhang", "123456");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.jti").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("admin.zhang"))
                .andReturn();

        ApiResponse<LoginResponse> resp = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<LoginResponse>>() {}
        );
        LoginResponse data = resp.getData();
        assertNotNull(data.getToken());

        // 验签并核对 JWT Claims：必须仅包含 sub, jti, tenant_id, username, iss, aud, iat, exp；严禁包含可变权限列表！
        JWTClaimsSet claims = jwtTokenService.parseAndVerify(data.getToken());
        assertNotNull(claims.getSubject());
        assertEquals(data.getJti(), claims.getJWTID());
        assertEquals("DEFAULT", data.getUser().getTenantCode());
        assertEquals("admin.zhang", claims.getStringClaim("username"));
        assertNotNull(claims.getStringClaim("tenant_id"));
        assertNotNull(claims.getIssueTime());
        assertNotNull(claims.getExpirationTime());
        assertNull(claims.getClaim("permissions"), "JWT 中严禁存放可变权限列表");
        assertNull(claims.getClaim("roles"), "JWT 中严禁存放可变角色列表");
    }

    @Test
    @DisplayName("测试登录成功必须预热当前会话权限缓存")
    void testLoginWarmsPermissionCacheBeforeReturningToken() throws Exception {
        LoginResponse loginResponse = doLogin("DEFAULT", "buyer.chen", "123456");

        // 登录完成后，权限缓存应已写入，后续下游请求不能依赖权限 Header 或数据库回源。
        Set<String> cachedPermissions = sessionCacheService.getCachedPermissions(
                loginResponse.getUser().getTenantId(), loginResponse.getUser().getUserId());
        assertNotNull(cachedPermissions, "登录成功返回 Token 前必须完成权限缓存预热");
        assertTrue(cachedPermissions.contains("pur:order:create"));
    }

    /**
     * 验证权限快照缺失时 JWT 过滤器返回 503，且不会回源数据库继续放行请求。
     */
    @Test
    @DisplayName("权限缓存未命中必须返回 503")
    void testPermissionCacheMissFailsClosed() throws Exception {
        LoginResponse loginResponse = doLogin("DEFAULT", "buyer.chen", "123456");
        sessionCacheService.evictUserAuthCache(
                loginResponse.getUser().getTenantId(), loginResponse.getUser().getUserId());

        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.getToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
    }

    /**
     * 验证租户停用会立即撤销该租户现有会话，防止旧 Token 在原 TTL 内继续访问。
     */
    @Test
    @DisplayName("租户停用必须立即撤销现有会话")
    void testTenantDisableRevokesExistingSession() throws Exception {
        LoginResponse loginResponse = doLogin("DEFAULT", "admin.zhang", "123456");
        Tenant tenant = tenantMapper.findByTenantCode("DEFAULT");
        String originalStatus = tenant.getStatus();
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                            "/api/auth/admin/tenants/current")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.getToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new TenantUpdateRequest(tenant.getTenantName(), "DISABLED"))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResponse.getToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(401));
        } finally {
            tenant.setStatus(originalStatus);
            tenantMapper.updateById(tenant);
            sessionCacheService.clearAll();
        }
    }

    /**
     * 验证菜单状态变更会清除所有租户用户的菜单快照，而不依赖固定的菜单缓存 TTL。
     */
    @Test
    @DisplayName("菜单状态变更必须失效租户菜单缓存")
    void testMenuStatusChangeEvictsTenantMenuCache() throws Exception {
        LoginResponse warehouseLogin = doLogin("DEFAULT", "wh.operator", "123456");
        mockMvc.perform(get("/api/me/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + warehouseLogin.getToken()))
                .andExpect(status().isOk());
        assertNotNull(sessionCacheService.getCachedMenus(
                warehouseLogin.getUser().getTenantId(), warehouseLogin.getUser().getUserId()));

        LoginResponse adminLogin = doLogin("DEFAULT", "admin.zhang", "123456");
        java.util.UUID menuId = java.util.UUID.fromString("60000000-0000-0000-0000-000000000001");
        Menu menu = menuMapper.selectById(menuId);
        String originalStatus = menu.getStatus();
        try {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                            "/api/auth/admin/menus/" + menuId + "/status")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminLogin.getToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new MenuStatusUpdateRequest("DISABLED"))))
                    .andExpect(status().isOk());

            assertNull(sessionCacheService.getCachedMenus(
                    warehouseLogin.getUser().getTenantId(), warehouseLogin.getUser().getUserId()));
        } finally {
            menu.setStatus(originalStatus);
            menuMapper.updateById(menu);
            sessionCacheService.clearAll();
        }
    }

    @Test
    @DisplayName("测试2：密码错误应拒绝登录并返回友好提示")
    void testLoginWithWrongPassword() throws Exception {
        LoginRequest req = new LoginRequest("DEFAULT", "admin.zhang", "wrong_pass_999");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("登录账号或密码错误"));
    }

    @Test
    @DisplayName("测试3：不存在的租户编码应返回 404")
    void testLoginWithInvalidTenant() throws Exception {
        LoginRequest req = new LoginRequest("UNKNOWN_TENANT", "admin.zhang", "123456");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("测试4：单账号单有效会话控制（后登踢前，旧 Token 请求返回 401）")
    void testSingleSessionDisplacement() throws Exception {
        // 1. 第一次登录 sales.liu
        LoginResponse session1 = doLogin("DEFAULT", "sales.liu", "123456");
        String token1 = session1.getToken();
        assertNotNull(token1);

        // 2. 使用 token1 访问受保护接口 /api/me，应正常返回 200
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("sales.liu"));

        // 3. 第二次在另一终端登录同一账号 sales.liu（产生新 JTI）
        LoginResponse session2 = doLogin("DEFAULT", "sales.liu", "123456");
        String token2 = session2.getToken();
        assertNotNull(token2);
        assertFalse(session1.getJti().equals(session2.getJti()), "两次登录的 JTI 必须独立生成");

        // 4. 再次使用旧 token1 访问 /api/me，必须被拦截并返回 401（已在其他终端登录）
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 5. 使用新 token2 访问 /api/me，正常返回 200
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("sales.liu"));
    }

    @Test
    @DisplayName("测试5：GET /api/me 获取当前用户的基本信息、角色列表与功能权限点集合")
    void testGetCurrentUserProfileAndPermissions() throws Exception {
        LoginResponse loginResp = doLogin("DEFAULT", "buyer.chen", "123456");

        MvcResult result = mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResp.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("buyer.chen"))
                .andExpect(jsonPath("$.data.realName").value("陈采购"))
                .andReturn();

        ApiResponse<UserProfileVo> resp = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<UserProfileVo>>() {}
        );
        UserProfileVo profile = resp.getData();
        assertNotNull(profile);
        assertTrue(profile.getRoles().contains("PURCHASING"));
        assertTrue(profile.getPerms().contains("pur:order:create"));
        assertTrue(profile.getPerms().contains("pur:order:submit"));
        assertTrue(profile.getPerms().contains("pur:order:approve"));
        assertTrue(profile.getPerms().contains("pur:quality:return"));
    }

    @Test
    @DisplayName("测试6：GET /api/me/menus 获取当前用户角色的动态菜单树")
    void testGetCurrentUserMenus() throws Exception {
        LoginResponse loginResp = doLogin("DEFAULT", "wh.operator", "123456");

        MvcResult result = mockMvc.perform(get("/api/me/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginResp.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        ApiResponse<List<MenuNodeVo>> resp = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<ApiResponse<List<MenuNodeVo>>>() {}
        );
        List<MenuNodeVo> menus = resp.getData();
        assertNotNull(menus);
        assertFalse(menus.isEmpty());

        // 仓库人员分配了 dashboard, master-data, purchase-inbound, sales-outbound
        List<String> menuCodes = menus.stream().map(MenuNodeVo::getMenuCode).toList();
        assertTrue(menuCodes.contains("dashboard"));
        assertTrue(menuCodes.contains("purchase-inbound"));
        assertTrue(menuCodes.contains("sales-outbound"));
    }

    @Test
    @DisplayName("测试7：GET /api/auth/jwks 开放端点成功返回 RSA 公钥 JWKS")
    void testGetJwksPublicKey() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/jwks"))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> jwks = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                new TypeReference<Map<String, Object>>() {}
        );
        assertNotNull(jwks.get("keys"));
        List<?> keys = (List<?>) jwks.get("keys");
        assertFalse(keys.isEmpty());

        Map<?, ?> firstKey = (Map<?, ?>) keys.get(0);
        assertEquals("RSA", firstKey.get("kty"));
        assertEquals("RS256", firstKey.get("alg"));
        assertEquals("sig", firstKey.get("use"));
        assertNotNull(firstKey.get("n"));
        assertNotNull(firstKey.get("e"));
    }

    @Test
    @DisplayName("测试8：POST /api/auth/logout 注销后会话立即失效")
    void testLogoutRevokesSession() throws Exception {
        LoginResponse loginResp = doLogin("DEFAULT", "mes.inspector", "123456");
        String token = loginResp.getToken();

        // 1. 验证登录态有效
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 2. 执行注销操作
        mockMvc.perform(post("/api/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 3. 注销后再次访问，应返回 401
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("测试9：跨租户查询 /api/me 严格隔离（非当前租户用户访问返回 404）")
    void testCrossTenantIsolationReturns404() throws Exception {
        LoginResponse loginResp = doLogin("DEFAULT", "sales.liu", "123456");
        java.util.UUID foreignTenantId = java.util.UUID.randomUUID();
        java.util.UUID actualUserId = loginResp.getUser().getUserId();
        String fakeJti = java.util.UUID.randomUUID().toString();

        // 在 Redis 中写入跨租户会话以通过网关/过滤器会话校验
        sessionCacheService.saveActiveSession(foreignTenantId, actualUserId, fakeJti, java.time.Duration.ofHours(1));
        // 新的认证链路对权限缓存缺失 Fail-Closed；写入空快照后继续验证控制器层的跨租户 404。
        sessionCacheService.cachePermissions(foreignTenantId, actualUserId, Set.of(), java.time.Duration.ofHours(1));

        String crossTenantToken = jwtTokenService.generateToken(actualUserId, foreignTenantId, "sales.liu", fakeJti);

        // 由于 UserMapper.findByUserIdAndTenantId 找不到 (actualUserId, foreignTenantId)，因此 /api/me 必须返回 404
        mockMvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crossTenantToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        // 跨租户访问 /api/me/menus 同样必须返回 404
        mockMvc.perform(get("/api/me/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + crossTenantToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }
}
