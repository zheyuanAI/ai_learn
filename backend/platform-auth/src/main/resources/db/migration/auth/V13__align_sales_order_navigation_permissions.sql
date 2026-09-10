-- ============================================================================
-- V13：补齐仓库人员的销售订单查询入口。
-- 说明：warehouse.operator 已拥有 sales:order:view，但旧角色菜单只挂了直接拣货页，
--       导致“有查询权限但导航不可见”。本迁移只补角色-菜单绑定，不扩大写权限。
-- ============================================================================

INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT r.id, m.id, 0
FROM auth_role r
JOIN auth_menu m ON m.tenant_id = r.tenant_id
                 AND m.menu_code = 'sales_order'
                 AND m.isdel = 0
                 AND m.visible = TRUE
                 AND m.status = 'ACTIVE'
WHERE r.role_code = 'warehouse.operator'
  AND r.status = 'ACTIVE'
  AND r.isdel = 0
ON CONFLICT DO NOTHING;
