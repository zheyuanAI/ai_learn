-- ============================================================================
-- Flyway Migration Script: V10__grant_operational_masterdata_read_permissions.sql
-- 模块说明: 补齐业务角色在页面创建/执行表单中所需的主数据只读权限。
-- 适用数据库: PostgreSQL 12.1 及以上兼容
-- 约束: 只新增角色-权限关系，不修改历史迁移；重复执行保持幂等。
-- ============================================================================

-- 页面下拉框必须能够读取真实主数据，但不因此获得主数据维护权限。
-- 销售需要客户，采购需要供应商和来源工单，仓库/生产需要库位；计量单位作为产品表单和主数据页的公共只读目录。
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT r.id, p.id, 0
FROM auth_role r
JOIN auth_permission p
  ON (
      (r.role_code = 'sales.rep' AND p.permission_code IN (
          'inv:customer:view', 'inv:uom:view'
      ))
      OR (r.role_code = 'purchase.agent' AND p.permission_code IN (
          'inv:supplier:view', 'inv:location:view', 'inv:uom:view', 'mes:workorder:view'
      ))
      OR (r.role_code = 'warehouse.operator' AND p.permission_code IN (
          'inv:location:view', 'inv:uom:view'
      ))
      OR (r.role_code = 'mes.inspector' AND p.permission_code IN (
          'inv:location:view', 'inv:uom:view'
      ))
  )
WHERE r.status = 'ACTIVE'
  AND r.isdel = 0
  AND p.isdel = 0
ON CONFLICT DO NOTHING;
