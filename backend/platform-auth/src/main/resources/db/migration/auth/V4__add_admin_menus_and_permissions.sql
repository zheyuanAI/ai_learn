-- ==============================================================================
-- Flyway Migration Script: V4__add_admin_menus_and_permissions.sql
-- 模块说明: 为系统管理补充完整的二级管理菜单及权限绑定
-- 适用数据库: PostgreSQL 12.1及以上兼容
-- 包含内容:
--   1. 新增/更新系统管理下的 5 个二级菜单（/system/tenant, /system/users, /system/roles, /system/permissions, /system/menus）
--   2. 为租户管理员 (tenant.admin) 角色配置新菜单与权限授权记录
-- ==============================================================================

-- 1. 插入或更新 5 大系统管理二级菜单
-- 1.1 租户信息菜单
INSERT INTO auth_menu (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order, permission_code, isdel)
VALUES (
    'e0000000-0000-0000-0000-000000000090'::uuid,
    'e0000000-0000-0000-0000-000000000009'::uuid,
    'sys_tenant',
    '租户信息',
    '/system/tenant',
    'views/system/TenantManage.vue',
    'ApartmentOutlined',
    1,
    'auth:tenant:view',
    0
)
ON CONFLICT (id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    route_path = EXCLUDED.route_path,
    component_path = EXCLUDED.component_path,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    permission_code = EXCLUDED.permission_code,
    updated_at = CURRENT_TIMESTAMP;

-- 1.2 用户管理菜单
INSERT INTO auth_menu (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order, permission_code, isdel)
VALUES (
    'e0000000-0000-0000-0000-000000000091'::uuid,
    'e0000000-0000-0000-0000-000000000009'::uuid,
    'sys_user',
    '用户管理',
    '/system/users',
    'views/system/UserList.vue',
    'UserOutlined',
    2,
    'auth:user:view',
    0
)
ON CONFLICT (id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    route_path = EXCLUDED.route_path,
    component_path = EXCLUDED.component_path,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    permission_code = EXCLUDED.permission_code,
    updated_at = CURRENT_TIMESTAMP;

-- 1.3 角色管理菜单
INSERT INTO auth_menu (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order, permission_code, isdel)
VALUES (
    'e0000000-0000-0000-0000-000000000092'::uuid,
    'e0000000-0000-0000-0000-000000000009'::uuid,
    'sys_role',
    '角色管理',
    '/system/roles',
    'views/system/RoleList.vue',
    'SafetyCertificateOutlined',
    3,
    'auth:role:view',
    0
)
ON CONFLICT (id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    route_path = EXCLUDED.route_path,
    component_path = EXCLUDED.component_path,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    permission_code = EXCLUDED.permission_code,
    updated_at = CURRENT_TIMESTAMP;

-- 1.4 权限清单菜单
INSERT INTO auth_menu (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order, permission_code, isdel)
VALUES (
    'e0000000-0000-0000-0000-000000000094'::uuid,
    'e0000000-0000-0000-0000-000000000009'::uuid,
    'sys_permission',
    '权限清单',
    '/system/permissions',
    'views/system/PermissionList.vue',
    'KeyOutlined',
    4,
    'auth:role:view',
    0
)
ON CONFLICT (id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    route_path = EXCLUDED.route_path,
    component_path = EXCLUDED.component_path,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    permission_code = EXCLUDED.permission_code,
    updated_at = CURRENT_TIMESTAMP;

-- 1.5 菜单管理菜单
INSERT INTO auth_menu (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order, permission_code, isdel)
VALUES (
    'e0000000-0000-0000-0000-000000000093'::uuid,
    'e0000000-0000-0000-0000-000000000009'::uuid,
    'sys_menu',
    '菜单管理',
    '/system/menus',
    'views/system/MenuList.vue',
    'MenuOutlined',
    5,
    'auth:menu:view',
    0
)
ON CONFLICT (id) DO UPDATE SET
    menu_name = EXCLUDED.menu_name,
    route_path = EXCLUDED.route_path,
    component_path = EXCLUDED.component_path,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    permission_code = EXCLUDED.permission_code,
    updated_at = CURRENT_TIMESTAMP;


-- 2. 为租户管理员 (tenant.admin) 角色配置新增菜单授权记录
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000001'::uuid,
    m.id,
    0
FROM auth_menu m
WHERE m.menu_code IN ('sys_tenant', 'sys_permission', 'sys_user', 'sys_role', 'sys_menu')
  AND NOT EXISTS (
      SELECT 1 FROM auth_role_menu rm
      WHERE rm.role_id = 'b0000000-0000-0000-0000-000000000001'::uuid
        AND rm.menu_id = m.id
        AND rm.isdel = 0
  );

-- 3. 确保租户管理员拥有所有管理类权限点
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000001'::uuid,
    p.id,
    0
FROM auth_permission p
WHERE p.permission_code IN (
    'auth:tenant:view', 'auth:tenant:manage',
    'auth:user:view', 'auth:user:manage',
    'auth:role:view', 'auth:role:manage',
    'auth:menu:view', 'auth:menu:manage',
    'auth:session:view', 'auth:session:revoke'
)
AND NOT EXISTS (
    SELECT 1 FROM auth_role_permission rp
    WHERE rp.role_id = 'b0000000-0000-0000-0000-000000000001'::uuid
      AND rp.permission_id = p.id
      AND rp.isdel = 0
);
