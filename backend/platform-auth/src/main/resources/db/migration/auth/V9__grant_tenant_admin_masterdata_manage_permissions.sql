-- ============================================================================
-- Flyway Migration Script: V9__grant_tenant_admin_masterdata_manage_permissions.sql
-- 模块说明: 修复租户管理员缺少商品与仓库主数据维护权限的问题
-- 适用数据库: PostgreSQL 12.1 及以上兼容
-- 约束: 只补齐既有权限关系，不修改历史脚本；重复执行保持幂等。
-- ============================================================================

-- V2 已定义商品与仓库维护权限，但初始 tenant.admin 只被授予了查询权限。
-- 按角色编码和权限码关联，避免依赖单一租户或固定角色 UUID，兼容既有租户管理员。
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT r.id, p.id, 0
FROM auth_role r
JOIN auth_permission p
  ON p.permission_code IN ('inv:product:manage', 'inv:warehouse:manage')
WHERE r.role_code = 'tenant.admin'
  AND r.status = 'ACTIVE'
  AND r.isdel = 0
  AND p.isdel = 0
ON CONFLICT DO NOTHING;
