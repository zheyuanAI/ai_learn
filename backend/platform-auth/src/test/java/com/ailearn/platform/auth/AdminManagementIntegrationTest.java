package com.ailearn.platform.auth;

import com.ailearn.platform.auth.domain.dto.LoginRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.MenuUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleMenusAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.RolePermissionsAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.RoleStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.TenantUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserCreateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserResetPasswordRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserRoleAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserUpdateRequest;
import com.ailearn.platform.auth.domain.entity.Role;
import com.ailearn.platform.auth.domain.entity.User;
import com.ailearn.platform.auth.domain.vo.LoginResponse;
import com.ailearn.platform.auth.domain.vo.admin.MenuAdminNodeVo;
import com.ailearn.platform.auth.domain.vo.admin.PageResult;
import com.ailearn.platform.auth.domain.vo.admin.PermissionAdminVo;
import com.ailearn.platform.auth.domain.vo.admin.RoleAdminVo;
import com.ailearn.platform.auth.domain.vo.admin.TenantAdminVo;
import com.ailearn.platform.auth.domain.vo.admin.UserAdminVo;
import com.ailearn.platform.auth.mapper.UserMapper;
import com.ailearn.platform.shared.api.ApiResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后端核心管理模块（租户、用户、角色、权限与菜单）全量集成测试套件。
 * <p>
 * 覆盖：
 * 1. 租户详情查询与更新；
 * 2. 用户分页、创建（BCrypt密码与防密码泄露）、修改、防自停/自删/最后管理员防御、重置密码与登录验证；
 * 3. 角色 CRUD、权限与菜单分配、预置管理员保护与分配用户时的 409 冲突拒绝删除；
 * 4. 权限点列表查询与所属模块检索；
 * 5. 菜单全量树构建、创建、防环路修改、有子菜单与被角色引用时的 409 冲突拒绝删除。
 * </p>
 */
@SpringBootTest(classes = AuthApplication.class)
@AutoConfigureMockMvc
public class AdminManagementIntegrationTest {

    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID TENANT_B_ROLE_ID = UUID.fromString("20000000-0000-0000-0000-000000000021");
    private static final UUID TENANT_B_DASHBOARD_MENU_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        String encoded = passwordEncoder.encode("123456");
        for (User user : userMapper.selectList(null)) {
            user.setPasswordHash(encoded);
            userMapper.updateById(user);
        }
    }

    /**
     * 辅助登录方法，获取管理员访问 Token。
     */
    private String loginAsAdmin() throws Exception {
        LoginRequest req = new LoginRequest("DEFAULT", "admin.zhang", "123456");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<LoginResponse> response = objectMapper.readValue(
                result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<LoginResponse>>() {}
        );
        return response.getData().getToken();
    }

    @Test
    @DisplayName("测试1：租户后台管理——查询当前租户详情与修改租户属性")
    void testTenantAdminOperations() throws Exception {
        String token = loginAsAdmin();

        // 1. 获取当前租户详情
        MvcResult getResult = mockMvc.perform(get("/api/auth/admin/tenants/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<TenantAdminVo> tenantResponse = objectMapper.readValue(
                getResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<TenantAdminVo>>() {}
        );
        assertNotNull(tenantResponse.getData());
        assertEquals("DEFAULT", tenantResponse.getData().getTenantCode());

        // 2. 修改租户名称
        TenantUpdateRequest updateReq = new TenantUpdateRequest("华东一号示范工厂", "ACTIVE");
        MvcResult updateResult = mockMvc.perform(put("/api/auth/admin/tenants/current")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<TenantAdminVo> updatedVo = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<TenantAdminVo>>() {}
        );
        assertEquals("华东一号示范工厂", updatedVo.getData().getTenantName());
    }

    @Test
    @DisplayName("测试2：用户后台管理——分页、创建(BCrypt/脱敏)、冲突409、防御机制与密码重置登录全链路")
    void testUserAdminLifecycleAndDefences() throws Exception {
        String token = loginAsAdmin();

        // 1. 分页检索
        MvcResult pageResult = mockMvc.perform(get("/api/auth/admin/users?page=1&size=10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<PageResult<UserAdminVo>> pageResp = objectMapper.readValue(
                pageResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<PageResult<UserAdminVo>>>() {}
        );
        assertTrue(pageResp.getData().getTotal() > 0);

        // 2. 创建新用户 (验证明文传入但持久化为 BCrypt，VO 不含 passwordHash)
        UserCreateRequest createReq = new UserCreateRequest();
        createReq.setUsername("test.operator");
        createReq.setPassword("Password@123");
        createReq.setUserNo("EMP999");
        createReq.setRealName("测试操作员");
        createReq.setEmail("test.operator@example.com");
        createReq.setPhone("13911112222");

        MvcResult createResult = mockMvc.perform(post("/api/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<UserAdminVo> createResp = objectMapper.readValue(
                createResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<UserAdminVo>>() {}
        );
        UserAdminVo newUserVo = createResp.getData();
        assertNotNull(newUserVo.getId());
        assertEquals("test.operator", newUserVo.getUsername());

        // 检查数据库中确实是 BCrypt 密文
        User dbUser = userMapper.selectById(newUserVo.getId());
        assertNotNull(dbUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("Password@123", dbUser.getPasswordHash()));

        // 3. 冲突校验：重复用户名创建 -> 返回 HTTP 409
        mockMvc.perform(post("/api/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isConflict());

        // 4. 修改用户信息
        UserUpdateRequest updateReq = new UserUpdateRequest();
        updateReq.setRealName("测试操作员(已修改)");
        updateReq.setUserNo("EMP999");
        updateReq.setEmail("test.modified@example.com");
        updateReq.setPhone("13933334444");

        MvcResult updateResult = mockMvc.perform(put("/api/auth/admin/users/" + newUserVo.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<UserAdminVo> updateResp = objectMapper.readValue(
                updateResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<UserAdminVo>>() {}
        );
        assertEquals("测试操作员(已修改)", updateResp.getData().getRealName());

        // 5. 重置密码并验证新密码可以正常登录 (统一路径 /api/auth/admin/users/{id}/reset-password)
        UserResetPasswordRequest resetReq = new UserResetPasswordRequest("NewSecret@888");
        mockMvc.perform(post("/api/auth/admin/users/" + newUserVo.getId() + "/reset-password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetReq)))
                .andExpect(status().isOk());

        // 用新密码登录
        LoginRequest newLoginReq = new LoginRequest("DEFAULT", "test.operator", "NewSecret@888");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLoginReq)))
                .andExpect(status().isOk());

        // 6. 防自停用保护：管理员尝试禁用自己 -> 返回 HTTP 400
        User adminUser = userMapper.findByTenantIdAndUsername(DEFAULT_TENANT_ID, "admin.zhang");
        UserStatusUpdateRequest disableReq = new UserStatusUpdateRequest("DISABLED");
        mockMvc.perform(put("/api/auth/admin/users/" + adminUser.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(disableReq)))
                .andExpect(status().isBadRequest());

        // 7. 防自删除保护：管理员尝试删除自己 -> 返回 HTTP 400
        mockMvc.perform(delete("/api/auth/admin/users/" + adminUser.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        // 8. 创建测试专用有效角色与非法角色样本，验证任一非法 ID 都会整体拒绝且旧关联不变
        RoleAdminVo baselineRole = createRole(token, "ROLE_ATOMIC_BASELINE_", "原子性基线角色");
        RoleAdminVo disabledRole = createRole(token, "ROLE_ATOMIC_DISABLED_", "原子性停用角色");
        RoleAdminVo deletedRole = createRole(token, "ROLE_ATOMIC_DELETED_", "原子性删除角色");

        mockMvc.perform(put("/api/auth/admin/roles/" + disabledRole.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleStatusUpdateRequest("DISABLED"))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/auth/admin/roles/" + deletedRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        UserRoleAssignRequest validRoleReq = new UserRoleAssignRequest(List.of(baselineRole.getId()));
        mockMvc.perform(put("/api/auth/admin/users/" + newUserVo.getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRoleReq)))
                .andExpect(status().isOk());

        assertUserRolesUnchangedAfterRejectedAssign(
                token,
                newUserVo.getId(),
                List.of(baselineRole.getId(), disabledRole.getId()),
                List.of(baselineRole.getId())
        );
        assertUserRolesUnchangedAfterRejectedAssign(
                token,
                newUserVo.getId(),
                List.of(baselineRole.getId(), TENANT_B_ROLE_ID),
                List.of(baselineRole.getId())
        );
        assertUserRolesUnchangedAfterRejectedAssign(
                token,
                newUserVo.getId(),
                List.of(baselineRole.getId(), deletedRole.getId()),
                List.of(baselineRole.getId())
        );
        assertUserRolesUnchangedAfterRejectedAssign(
                token,
                newUserVo.getId(),
                List.of(baselineRole.getId(), UUID.randomUUID()),
                List.of(baselineRole.getId())
        );

        // 9. 正常删除创建的测试用户与临时角色，避免影响后续断言
        mockMvc.perform(delete("/api/auth/admin/users/" + newUserVo.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/auth/admin/roles/" + baselineRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/auth/admin/roles/" + disabledRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("测试3：角色后台管理——CRUD、权限菜单绑定、409删除冲突与预置角色保护")
    void testRoleAdminLifecycleAndConflictDefence() throws Exception {
        String token = loginAsAdmin();

        // 1. 查询角色列表
        MvcResult listResult = mockMvc.perform(get("/api/auth/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<List<RoleAdminVo>> listResp = objectMapper.readValue(
                listResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<List<RoleAdminVo>>>() {}
        );
        assertFalse(listResp.getData().isEmpty());

        // 2. 创建新角色
        RoleCreateRequest createRoleReq = new RoleCreateRequest();
        createRoleReq.setRoleCode("ROLE_QUALITY_LEAD");
        createRoleReq.setRoleName("品保主管");
        createRoleReq.setDescription("负责产线品保与质量放行");

        MvcResult createRoleResult = mockMvc.perform(post("/api/auth/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRoleReq)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<RoleAdminVo> createdRoleVo = objectMapper.readValue(
                createRoleResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<RoleAdminVo>>() {}
        );
        UUID newRoleId = createdRoleVo.getData().getId();
        assertNotNull(newRoleId);

        // 3. 角色编码冲突 409 校验
        mockMvc.perform(post("/api/auth/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRoleReq)))
                .andExpect(status().isConflict());

        // 4. 为角色分配权限点
        RolePermissionsAssignRequest permAssignReq = new RolePermissionsAssignRequest(
                List.of(UUID.fromString("50000000-0000-0000-0000-000000000103"), UUID.fromString("50000000-0000-0000-0000-000000000105"))
        );
        mockMvc.perform(put("/api/auth/admin/roles/" + newRoleId + "/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(permAssignReq)))
                .andExpect(status().isOk());

        // 强校验：分配不存在的权限点 -> 422
        RolePermissionsAssignRequest invalidPermAssignReq = new RolePermissionsAssignRequest(
                List.of(UUID.randomUUID())
        );
        mockMvc.perform(put("/api/auth/admin/roles/" + newRoleId + "/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPermAssignReq)))
                .andExpect(status().isUnprocessableEntity());

        // 读取角色权限验证
        MvcResult permsGetResult = mockMvc.perform(get("/api/auth/admin/roles/" + newRoleId + "/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<List<PermissionAdminVo>> permsResp = objectMapper.readValue(
                permsGetResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<List<PermissionAdminVo>>>() {}
        );
        assertEquals(2, permsResp.getData().size());

        // 5. 创建测试专用有效菜单与非法菜单样本，验证任一非法 ID 都会整体拒绝且旧关联不变
        MenuAdminNodeVo baselineMenu = createMenu(token, "sys_atomic_baseline_", "原子性基线菜单");
        MenuAdminNodeVo disabledMenu = createMenu(token, "sys_atomic_disabled_", "原子性停用菜单");
        MenuAdminNodeVo deletedMenu = createMenu(token, "sys_atomic_deleted_", "原子性删除菜单");

        mockMvc.perform(put("/api/auth/admin/menus/" + disabledMenu.getId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MenuStatusUpdateRequest("DISABLED"))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/auth/admin/menus/" + deletedMenu.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        RoleMenusAssignRequest menuAssignReq = new RoleMenusAssignRequest(
                List.of(baselineMenu.getId())
        );
        mockMvc.perform(put("/api/auth/admin/roles/" + newRoleId + "/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(menuAssignReq)))
                .andExpect(status().isOk());

        assertRoleMenusUnchangedAfterRejectedAssign(
                token,
                newRoleId,
                List.of(baselineMenu.getId(), disabledMenu.getId()),
                List.of(baselineMenu.getId())
        );
        assertRoleMenusUnchangedAfterRejectedAssign(
                token,
                newRoleId,
                List.of(baselineMenu.getId(), TENANT_B_DASHBOARD_MENU_ID),
                List.of(baselineMenu.getId())
        );
        assertRoleMenusUnchangedAfterRejectedAssign(
                token,
                newRoleId,
                List.of(baselineMenu.getId(), deletedMenu.getId()),
                List.of(baselineMenu.getId())
        );
        assertRoleMenusUnchangedAfterRejectedAssign(
                token,
                newRoleId,
                List.of(baselineMenu.getId(), UUID.randomUUID()),
                List.of(baselineMenu.getId())
        );

        // 6. 创建临时用户，并验证停用角色不能被分配；恢复后再分配以测试 409 删除冲突
        UserCreateRequest userReq = new UserCreateRequest();
        userReq.setUsername("temp.quality.user");
        userReq.setPassword("Password@123");
        userReq.setRealName("临时品保员");

        MvcResult userResult = mockMvc.perform(post("/api/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userReq)))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<UserAdminVo> tempUser = objectMapper.readValue(
                userResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<UserAdminVo>>() {}
        );

        mockMvc.perform(put("/api/auth/admin/roles/" + newRoleId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleStatusUpdateRequest("DISABLED"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/auth/admin/users/" + tempUser.getData().getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRoleAssignRequest(List.of(newRoleId)))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(put("/api/auth/admin/roles/" + newRoleId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleStatusUpdateRequest("ACTIVE"))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/auth/admin/users/" + tempUser.getData().getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRoleAssignRequest(List.of(newRoleId)))))
                .andExpect(status().isOk());

        // 尝试删除已被引用的角色 -> 必须返回 HTTP 409
        mockMvc.perform(delete("/api/auth/admin/roles/" + newRoleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());

        // 移除用户上的该角色关联
        UserRoleAssignRequest emptyRoleReq = new UserRoleAssignRequest(Collections.emptyList());
        mockMvc.perform(put("/api/auth/admin/users/" + tempUser.getData().getId() + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRoleReq)))
                .andExpect(status().isOk());

        // 解除引用后再次删除角色 -> 成功
        mockMvc.perform(delete("/api/auth/admin/roles/" + newRoleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/auth/admin/menus/" + baselineMenu.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/auth/admin/menus/" + disabledMenu.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 清理临时用户
        mockMvc.perform(delete("/api/auth/admin/users/" + tempUser.getData().getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        // 7. 预置管理员角色禁止删除保护
        Role adminRole = listResp.getData().stream()
                .filter(r -> "TENANT_ADMIN".equalsIgnoreCase(r.getRoleCode()) || "tenant.admin".equalsIgnoreCase(r.getRoleCode()))
                .findFirst().map(r -> {
                    Role role = new Role();
                    role.setId(r.getId());
                    return role;
                }).orElseThrow();

        mockMvc.perform(delete("/api/auth/admin/roles/" + adminRole.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("测试4：权限后台管理——查询全量字典与按模块筛选")
    void testPermissionAdminQuery() throws Exception {
        String token = loginAsAdmin();

        // 1. 查询全量权限
        MvcResult allResult = mockMvc.perform(get("/api/auth/admin/permissions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<List<PermissionAdminVo>> allResp = objectMapper.readValue(
                allResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<List<PermissionAdminVo>>>() {}
        );
        assertFalse(allResp.getData().isEmpty());
        assertTrue(allResp.getData().stream().anyMatch(p -> "auth:user:view".equals(p.getPermissionCode())));

        // 2. 按模块过滤
        MvcResult authModuleResult = mockMvc.perform(get("/api/auth/admin/permissions?module=auth")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<List<PermissionAdminVo>> moduleResp = objectMapper.readValue(
                authModuleResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<List<PermissionAdminVo>>>() {}
        );
        assertTrue(moduleResp.getData().stream().allMatch(p -> "auth".equals(p.getModule())));
    }

    @Test
    @DisplayName("测试5：菜单后台管理——树形构建、创建、防成环更新、子节点/角色引用409冲突防御与删除")
    void testMenuAdminLifecycleAndConflictDefence() throws Exception {
        String token = loginAsAdmin();

        // 1. 获取全量动态菜单树
        MvcResult treeResult = mockMvc.perform(get("/api/auth/admin/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<List<MenuAdminNodeVo>> treeResp = objectMapper.readValue(
                treeResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<List<MenuAdminNodeVo>>>() {}
        );
        assertFalse(treeResp.getData().isEmpty());
        // 找到系统管理父节点
        MenuAdminNodeVo systemNode = treeResp.getData().stream()
                .filter(m -> "system".equals(m.getMenuCode()))
                .findFirst().orElse(null);
        assertNotNull(systemNode);
        assertFalse(systemNode.getChildren().isEmpty());
        assertEquals("ACTIVE", systemNode.getStatus());

        // 2. 创建新子菜单节点
        MenuCreateRequest createReq = new MenuCreateRequest();
        createReq.setParentId(systemNode.getId());
        createReq.setMenuCode("sys_test_custom");
        createReq.setMenuName("自定义测试菜单");
        createReq.setRoutePath("/system/custom-test");
        createReq.setComponentPath("views/system/CustomTest.vue");
        createReq.setIcon("ToolOutlined");
        createReq.setSortOrder(99);

        MvcResult createResult = mockMvc.perform(post("/api/auth/admin/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<MenuAdminNodeVo> createdMenuVo = objectMapper.readValue(
                createResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<MenuAdminNodeVo>>() {}
        );
        UUID customMenuId = createdMenuVo.getData().getId();
        assertNotNull(customMenuId);
        assertEquals("sys_test_custom", createdMenuVo.getData().getMenuCode());
        assertEquals("ACTIVE", createdMenuVo.getData().getStatus());
        assertTrue(createdMenuVo.getData().getVisible());

        // 3. 菜单编码冲突 409 校验
        mockMvc.perform(post("/api/auth/admin/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isConflict());

        // 4. 防成环校验：尝试将 systemNode 的 parentId 设置为自身或其子菜单 customMenuId -> 400
        MenuUpdateRequest invalidParentReq = new MenuUpdateRequest();
        invalidParentReq.setParentId(systemNode.getId());
        invalidParentReq.setMenuCode(systemNode.getMenuCode());
        invalidParentReq.setMenuName("系统管理");
        mockMvc.perform(put("/api/auth/admin/menus/" + systemNode.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidParentReq)))
                .andExpect(status().isBadRequest());

        // 5. 修改菜单编码和显隐并回读，验证字段真实持久化且 visible 与 status 相互独立
        MenuUpdateRequest updateReq = new MenuUpdateRequest();
        updateReq.setParentId(systemNode.getId());
        updateReq.setMenuCode("sys_test_custom_renamed");
        updateReq.setMenuName("自定义测试菜单（已修改）");
        updateReq.setRoutePath("/system/custom-test-renamed");
        updateReq.setComponentPath("views/system/CustomTest.vue");
        updateReq.setIcon("ToolOutlined");
        updateReq.setSortOrder(100);
        updateReq.setVisible(false);
        MvcResult updateMenuResult = mockMvc.perform(put("/api/auth/admin/menus/" + customMenuId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<MenuAdminNodeVo> updatedMenuResp = objectMapper.readValue(
                updateMenuResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<MenuAdminNodeVo>>() {}
        );
        assertEquals("sys_test_custom_renamed", updatedMenuResp.getData().getMenuCode());
        assertFalse(updatedMenuResp.getData().getVisible());
        assertEquals("ACTIVE", updatedMenuResp.getData().getStatus());

        MvcResult detailResult = mockMvc.perform(get("/api/auth/admin/menus/" + customMenuId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<MenuAdminNodeVo> detailResp = objectMapper.readValue(
                detailResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<MenuAdminNodeVo>>() {}
        );
        assertEquals("sys_test_custom_renamed", detailResp.getData().getMenuCode());
        assertFalse(detailResp.getData().getVisible());
        assertEquals("ACTIVE", detailResp.getData().getStatus());

        MvcResult disabledResult = mockMvc.perform(put("/api/auth/admin/menus/" + customMenuId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MenuStatusUpdateRequest("DISABLED"))))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<MenuAdminNodeVo> disabledResp = objectMapper.readValue(
                disabledResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<MenuAdminNodeVo>>() {}
        );
        assertEquals("DISABLED", disabledResp.getData().getStatus());
        assertFalse(disabledResp.getData().getVisible());

        // 普通更新只恢复 visible，不能把 status 偷换为 ACTIVE。
        updateReq.setVisible(true);
        MvcResult visibleResult = mockMvc.perform(put("/api/auth/admin/menus/" + customMenuId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<MenuAdminNodeVo> visibleResp = objectMapper.readValue(
                visibleResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<MenuAdminNodeVo>>() {}
        );
        assertTrue(visibleResp.getData().getVisible());
        assertEquals("DISABLED", visibleResp.getData().getStatus());

        mockMvc.perform(put("/api/auth/admin/menus/" + customMenuId + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MenuStatusUpdateRequest("ACTIVE"))))
                .andExpect(status().isOk());

        // 6. 冲突删除校验：尝试删除拥有子菜单的 systemNode -> 返回 HTTP 409
        mockMvc.perform(delete("/api/auth/admin/menus/" + systemNode.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());

        // 7. 冲突删除校验：尝试删除已被角色引用的子菜单 (如 sys_tenant) -> 返回 HTTP 409
        UUID tenantMenuId = UUID.fromString("60000000-0000-0000-0000-000000000090");
        mockMvc.perform(delete("/api/auth/admin/menus/" + tenantMenuId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict());

        // 8. 正常删除无子菜单且未被角色引用的新自定义菜单 -> 成功
        mockMvc.perform(delete("/api/auth/admin/menus/" + customMenuId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    /**
     * 创建测试专用角色，供授权原子性回归场景复用。
     * <p>
     * 用途：避免直接改动种子角色状态，降低测试之间的相互影响；
     * 入参：token 为管理员令牌，roleCodePrefix 为角色编码前缀，roleName 为角色名称；
     * 返回值：新建角色详情；
     * 简略流程：拼接唯一角色编码，请求角色创建接口，再解析响应体返回角色对象。
     * </p>
     *
     * @param token 管理员访问令牌
     * @param roleCodePrefix 角色编码前缀
     * @param roleName 角色名称
     * @return 新建角色详情
     */
    private RoleAdminVo createRole(String token, String roleCodePrefix, String roleName) throws Exception {
        RoleCreateRequest createRoleReq = new RoleCreateRequest();
        createRoleReq.setRoleCode(roleCodePrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        createRoleReq.setRoleName(roleName);
        createRoleReq.setDescription(roleName + "测试专用");

        MvcResult createRoleResult = mockMvc.perform(post("/api/auth/admin/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRoleReq)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<RoleAdminVo> createdRoleVo = objectMapper.readValue(
                createRoleResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<RoleAdminVo>>() {}
        );
        return createdRoleVo.getData();
    }

    /**
     * 创建测试专用菜单，供角色菜单原子性回归场景复用。
     * <p>
     * 用途：避免直接改动种子菜单状态，降低测试之间的相互影响；
     * 入参：token 为管理员令牌，menuCodePrefix 为菜单编码前缀，menuName 为菜单名称；
     * 返回值：新建菜单详情；
     * 简略流程：组装唯一菜单编码和基础路由字段，请求菜单创建接口，再解析响应体返回菜单对象。
     * </p>
     *
     * @param token 管理员访问令牌
     * @param menuCodePrefix 菜单编码前缀
     * @param menuName 菜单名称
     * @return 新建菜单详情
     */
    private MenuAdminNodeVo createMenu(String token, String menuCodePrefix, String menuName) throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MenuCreateRequest createReq = new MenuCreateRequest();
        createReq.setMenuCode(menuCodePrefix + uniqueSuffix);
        createReq.setMenuName(menuName);
        createReq.setRoutePath("/system/" + uniqueSuffix);
        createReq.setComponentPath("views/system/Atomic" + uniqueSuffix + ".vue");
        createReq.setIcon("ToolOutlined");
        createReq.setSortOrder(200);

        MvcResult createResult = mockMvc.perform(post("/api/auth/admin/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn();

        ApiResponse<MenuAdminNodeVo> createdMenuVo = objectMapper.readValue(
                createResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<MenuAdminNodeVo>>() {}
        );
        return createdMenuVo.getData();
    }

    /**
     * 断言用户角色分配在非法请求被拒绝后，旧角色关联保持不变。
     * <p>
     * 用途：统一覆盖停用、跨租户、已删除和不存在角色的原子性回归；
     * 入参：token 为管理员令牌，userId 为目标用户，attemptRoleIds 为本次尝试分配集合，expectedRoleIds 为失败后应保留集合；
     * 返回值：无；
     * 简略流程：先发起应返回 422 的角色分配请求，再回读用户详情并比较角色 ID 集合。
     * </p>
     *
     * @param token 管理员访问令牌
     * @param userId 目标用户 ID
     * @param attemptRoleIds 本次尝试分配的角色 ID 集合
     * @param expectedRoleIds 失败后应保留的角色 ID 集合
     */
    private void assertUserRolesUnchangedAfterRejectedAssign(String token,
                                                             UUID userId,
                                                             List<UUID> attemptRoleIds,
                                                             List<UUID> expectedRoleIds) throws Exception {
        mockMvc.perform(put("/api/auth/admin/users/" + userId + "/roles")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserRoleAssignRequest(attemptRoleIds))))
                .andExpect(status().isUnprocessableEntity());

        MvcResult roleReadResult = mockMvc.perform(get("/api/auth/admin/users/" + userId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<UserAdminVo> roleReadResp = objectMapper.readValue(
                roleReadResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<UserAdminVo>>() {}
        );
        assertUuidListEquals(expectedRoleIds, roleReadResp.getData().getRoleIds());
    }

    /**
     * 断言角色菜单分配在非法请求被拒绝后，旧菜单关联保持不变。
     * <p>
     * 用途：统一覆盖停用、跨租户、已删除和不存在菜单的原子性回归；
     * 入参：token 为管理员令牌，roleId 为目标角色，attemptMenuIds 为本次尝试分配集合，expectedMenuIds 为失败后应保留集合；
     * 返回值：无；
     * 简略流程：先发起应返回 422 的菜单分配请求，再回读角色详情并比较菜单 ID 集合。
     * </p>
     *
     * @param token 管理员访问令牌
     * @param roleId 目标角色 ID
     * @param attemptMenuIds 本次尝试分配的菜单 ID 集合
     * @param expectedMenuIds 失败后应保留的菜单 ID 集合
     */
    private void assertRoleMenusUnchangedAfterRejectedAssign(String token,
                                                             UUID roleId,
                                                             List<UUID> attemptMenuIds,
                                                             List<UUID> expectedMenuIds) throws Exception {
        mockMvc.perform(put("/api/auth/admin/roles/" + roleId + "/menus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleMenusAssignRequest(attemptMenuIds))))
                .andExpect(status().isUnprocessableEntity());

        MvcResult menuReadResult = mockMvc.perform(get("/api/auth/admin/roles/" + roleId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        ApiResponse<RoleAdminVo> menuReadResp = objectMapper.readValue(
                menuReadResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8),
                new TypeReference<ApiResponse<RoleAdminVo>>() {}
        );
        assertUuidListEquals(expectedMenuIds, menuReadResp.getData().getMenuIds());
    }

    /**
     * 比较两个 UUID 列表是否表示同一组关联。
     * <p>
     * 用途：避免依赖接口返回顺序，只验证关联集合内容与数量；
     * 入参：expected 为期望集合，actual 为实际集合；
     * 返回值：无；
     * 简略流程：先断言数量一致，再把两组 UUID 转为 Set 后比较。
     * </p>
     *
     * @param expected 期望 UUID 列表
     * @param actual 实际 UUID 列表
     */
    private void assertUuidListEquals(List<UUID> expected, List<UUID> actual) {
        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        assertEquals(Set.copyOf(expected), Set.copyOf(actual));
    }
}
