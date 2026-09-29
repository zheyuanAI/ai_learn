package com.ailearn.platform.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailearn.platform.auth.domain.dto.admin.MenuUpdateRequest;
import com.ailearn.platform.auth.service.admin.MenuAdminService;
import com.ailearn.platform.shared.context.RequestContextHolder;
import com.ailearn.platform.shared.context.TenantContextHolder;
import com.ailearn.platform.shared.context.UserContextHolder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** 使用隔离 H2 租户的真实事务验证并发交叉改父级不能产生菜单环。 */
@SpringBootTest(classes = AuthApplication.class)
class MenuHierarchyConcurrencyTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MenuAdminService menuAdminService;

    /**
     * 验证两个根节点同时互为父级只能有一个成功。
     * 无入参；流程：创建隔离租户和节点，锁住两条菜单行以暴露旧实现的读写间隙，
     * 并发执行正式菜单服务，再检查数据库不存在双向父子环。
     */
    @Test
    void concurrentOppositeReparentDoesNotCreateCycle() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_tenant (id, tenant_code, tenant_name, status, isdel) VALUES (?, ?, '菜单并发租户', 'ACTIVE', 0)",
                tenantId, "MENU-" + tenantId);
        jdbc.update("INSERT INTO auth_menu (id, tenant_id, menu_code, menu_name, status, isdel) VALUES (?, ?, ?, ?, 'ACTIVE', 0)",
                first, tenantId, "first", "第一个菜单");
        jdbc.update("INSERT INTO auth_menu (id, tenant_id, menu_code, menu_name, status, isdel) VALUES (?, ?, ?, ?, 'ACTIVE', 0)",
                second, tenantId, "second", "第二个菜单");
        try (Connection blocker = dataSource.getConnection(); ExecutorService workers = Executors.newFixedThreadPool(2)) {
            blocker.setAutoCommit(false);
            try (PreparedStatement statement = blocker.prepareStatement(
                    "SELECT id FROM auth_menu WHERE tenant_id = ? ORDER BY id FOR UPDATE")) {
                statement.setObject(1, tenantId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertTrue(rows.next());
                }
            }
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> left = workers.submit(() -> reparent(tenantId, first, second, "first", ready, start));
            Future<Boolean> right = workers.submit(() -> reparent(tenantId, second, first, "second", ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            // 修改原因：拦住数据库写入，使无租户锁时两个线程都能读到旧树，再释放验证串行结果。
            Thread.sleep(300);
            blocker.commit();
            int successes = (left.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (right.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes, "交叉改父级只能有一个请求通过防环校验");
            UUID firstParent = jdbc.queryForObject("SELECT parent_id FROM auth_menu WHERE id = ?", UUID.class, first);
            UUID secondParent = jdbc.queryForObject("SELECT parent_id FROM auth_menu WHERE id = ?", UUID.class, second);
            assertTrue(firstParent == null || secondParent == null, "不能形成 A↔B 菜单环");
        } finally {
            jdbc.update("UPDATE auth_menu SET isdel = 1 WHERE tenant_id = ? AND isdel = 0", tenantId);
            jdbc.update("UPDATE auth_tenant SET isdel = 1 WHERE id = ? AND isdel = 0", tenantId);
        }
    }

    /** 在线程内设置可信租户及权限上下文；入参为待修改节点与目标父节点，返回该请求是否被接受。 */
    private boolean reparent(UUID tenantId, UUID menuId, UUID parentId, String code,
                             CountDownLatch ready, CountDownLatch start) throws Exception {
        TenantContextHolder.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test", null, List.of(new SimpleGrantedAuthority("auth:menu:manage"))));
        try {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            MenuUpdateRequest request = new MenuUpdateRequest();
            request.setMenuCode(code);
            request.setMenuName(code);
            request.setParentId(parentId);
            try {
                menuAdminService.updateMenu(menuId, request);
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
