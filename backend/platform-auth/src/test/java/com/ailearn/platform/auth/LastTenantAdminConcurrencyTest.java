package com.ailearn.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.auth.domain.dto.admin.UserRoleAssignRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserStatusUpdateRequest;
import com.ailearn.platform.auth.domain.dto.admin.UserUpdateRequest;
import com.ailearn.platform.auth.mapper.UserMapper;
import com.ailearn.platform.auth.service.admin.UserAdminService;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContext;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** 验证两个管理员并发撤销对方账号时，租户至少保留一个可用管理员。 */
@SpringBootTest(classes = AuthApplication.class)
class LastTenantAdminConcurrencyTest {

    private enum Mutation { DELETE, DISABLE, ASSIGN_ROLES, UPDATE_ROLES }

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserMapper userMapper;
    @Autowired private UserAdminService userAdminService;

    /**
     * 使用独立测试租户与真实事务制造交错：先锁住两个用户行，使两个撤销请求有机会同时完成管理员计数。
     * 入参：无；出参：无；核心流程：创建两个管理员、并发互删、释放用户行锁，再核对仅一个请求成功。
     */
    @ParameterizedTest
    @EnumSource(Mutation.class)
    void concurrentCrossMutationKeepsOneActiveTenantAdmin(Mutation mutation) throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_tenant (id, tenant_code, tenant_name, status, isdel) VALUES (?, ?, '并发验收租户', 'ACTIVE', 0)",
                tenantId, "LOCK-" + tenantId);
        jdbc.update("INSERT INTO auth_role (id, tenant_id, role_code, role_name, status, isdel) VALUES (?, ?, 'TENANT_ADMIN', '租户管理员', 'ACTIVE', 0)",
                roleId, tenantId);
        insertAdmin(tenantId, roleId, first, "first");
        insertAdmin(tenantId, roleId, second, "second");

        try (Connection blocker = dataSource.getConnection(); ExecutorService workers = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            String lockedTable = mutation == Mutation.ASSIGN_ROLES || mutation == Mutation.UPDATE_ROLES
                    ? "auth_user_role" : "auth_user";
            try (PreparedStatement statement = blocker.prepareStatement(
                    "SELECT id FROM " + lockedTable + " WHERE tenant_id = ? AND "
                            + (lockedTable.equals("auth_user") ? "id" : "user_id")
                            + " IN (?, ?) ORDER BY id FOR UPDATE")) {
                statement.setObject(1, tenantId);
                statement.setObject(2, first);
                statement.setObject(3, second);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertTrue(rows.next());
                }
            }
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> firstMutation = workers.submit(() -> mutateAs(first, second, tenantId, mutation, ready, start));
            Future<Boolean> secondMutation = workers.submit(() -> mutateAs(second, first, tenantId, mutation, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            // 修改原因：用户行锁把两个请求停在写入前，稳定暴露“同时读到两个管理员”的竞态。
            Thread.sleep(Duration.ofMillis(700));
            blocker.commit();
            int successes = (firstMutation.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (secondMutation.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes, "两个交叉减少管理员的请求只能有一个成功: " + mutation);
            assertEquals(1, userMapper.countActiveAdmins(tenantId), "租户必须保留一个活动管理员");
        } finally {
            jdbc.update("UPDATE auth_user_role SET isdel = 1 WHERE tenant_id = ? AND isdel = 0", tenantId);
            jdbc.update("UPDATE auth_user SET isdel = 1 WHERE tenant_id = ? AND isdel = 0", tenantId);
            jdbc.update("UPDATE auth_role SET isdel = 1 WHERE tenant_id = ? AND isdel = 0", tenantId);
            jdbc.update("UPDATE auth_tenant SET isdel = 1 WHERE id = ? AND isdel = 0", tenantId);
        }
    }

    /** 插入具备管理员角色的最小测试账号；角色与用户均只属于本测试租户。 */
    private void insertAdmin(UUID tenantId, UUID roleId, UUID userId, String suffix) {
        jdbc.update("INSERT INTO auth_user (id, tenant_id, user_no, username, password_hash, real_name, status, isdel) "
                        + "VALUES (?, ?, ?, ?, 'unused-test-hash', '并发管理员', 'ACTIVE', 0)",
                userId, tenantId, "LOCK-" + suffix + userId, "lock." + suffix + userId);
        jdbc.update("INSERT INTO auth_user_role (id, tenant_id, user_id, role_id, isdel) VALUES (?, ?, ?, ?, 0)",
                UUID.randomUUID(), tenantId, userId, roleId);
    }

    /** 在各自请求线程设置可信身份，执行正式事务化用户服务，返回该请求是否成功。 */
    private boolean mutateAs(UUID actorId, UUID targetId, UUID tenantId, Mutation mutation,
                             CountDownLatch ready, CountDownLatch start) throws Exception {
        UserContext user = new UserContext();
        user.setUserId(actorId.toString());
        user.setTenantId(tenantId.toString());
        user.setUsername("并发管理员");
        UserContextHolder.set(user);
        TenantContextHolder.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actorId, null, List.of(new SimpleGrantedAuthority("auth:user:manage"))));
        try {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            try {
                switch (mutation) {
                    case DELETE -> userAdminService.deleteUser(targetId);
                    case DISABLE -> userAdminService.updateUserStatus(targetId,
                            new UserStatusUpdateRequest("DISABLED"));
                    case ASSIGN_ROLES -> userAdminService.assignRoles(targetId,
                            new UserRoleAssignRequest(List.of()));
                    case UPDATE_ROLES -> {
                        UserUpdateRequest update = new UserUpdateRequest();
                        update.setRealName("并发角色撤销");
                        update.setRoleIds(List.of());
                        userAdminService.updateUser(targetId, update);
                    }
                }
                return true;
            } catch (RuntimeException rejected) {
                return false;
            }
        } finally {
            SecurityContextHolder.clearContext();
            UserContextHolder.clear();
            RequestContextHolder.clear();
        }
    }
}
