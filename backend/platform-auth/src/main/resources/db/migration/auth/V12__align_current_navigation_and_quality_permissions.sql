-- ============================================================================
-- V12：对齐当前前端路由、制造入口和采购质检权限。
-- 说明：历史菜单保留兼容关系；本迁移只补齐当前可访问页面，不删除角色权限基础数据。
-- ============================================================================

-- 历史菜单名称和路由曾停留在旧版原型，统一改为当前实际页面术语。
UPDATE auth_menu
   SET menu_name = '采购到货验收', route_path = '/purchasing/receipts', permission_code = 'pur:receipt:view',
       updated_at = CURRENT_TIMESTAMP
 WHERE menu_code = 'purchase_inbound' AND isdel = 0;

UPDATE auth_menu
   SET menu_name = '车间派工看板', route_path = '/mes/dispatch', permission_code = 'mes:dispatch:manage',
       updated_at = CURRENT_TIMESTAMP
 WHERE menu_code = 'mes_execution' AND isdel = 0;

-- 当前前端已有独立的质检、BOM、路线、工序执行和成品入库路由；菜单必须真实挂到租户菜单树。
INSERT INTO auth_menu
    (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order,
     permission_code, created_at, updated_at, isdel, tenant_id, visible, status)
VALUES
    ('e0000000-0000-0000-0000-000000000095'::uuid, 'e0000000-0000-0000-0000-000000000003'::uuid,
     'purchase_quality', '采购质检与处置', '/purchasing/quality', 'views/purchasing/QualityDispositionView.vue',
     'SafetyCertificateOutlined', 4, 'pur:receipt:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000001'::uuid, TRUE, 'ACTIVE'),
    ('e2000000-0000-0000-0000-000000000095'::uuid, 'e2000000-0000-0000-0000-000000000003'::uuid,
     'purchase_quality', '采购质检与处置', '/purchasing/quality', 'views/purchasing/QualityDispositionView.vue',
     'SafetyCertificateOutlined', 4, 'pur:receipt:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000054'::uuid, 'e0000000-0000-0000-0000-000000000005'::uuid,
     'mes_bom', 'BOM 物料清单', '/mes/boms', 'views/manufacturing/BomListView.vue',
     'ApartmentOutlined', 4, 'mes:bom:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000001'::uuid, TRUE, 'ACTIVE'),
    ('e2000000-0000-0000-0000-000000000054'::uuid, 'e2000000-0000-0000-0000-000000000005'::uuid,
     'mes_bom', 'BOM 物料清单', '/mes/boms', 'views/manufacturing/BomListView.vue',
     'ApartmentOutlined', 4, 'mes:bom:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000055'::uuid, 'e0000000-0000-0000-0000-000000000005'::uuid,
     'mes_routing', '工艺路线管理', '/mes/routings', 'views/manufacturing/RoutingListView.vue',
     'BranchesOutlined', 5, 'mes:routing:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000001'::uuid, TRUE, 'ACTIVE'),
    ('e2000000-0000-0000-0000-000000000055'::uuid, 'e2000000-0000-0000-0000-000000000005'::uuid,
     'mes_routing', '工艺路线管理', '/mes/routings', 'views/manufacturing/RoutingListView.vue',
     'BranchesOutlined', 5, 'mes:routing:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000056'::uuid, 'e0000000-0000-0000-0000-000000000005'::uuid,
     'mes_executions', '工序报工与质检', '/mes/executions', 'views/manufacturing/OperationExecutionView.vue',
     'PlayCircleOutlined', 6, 'mes:execution:manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000001'::uuid, TRUE, 'ACTIVE'),
    ('e2000000-0000-0000-0000-000000000056'::uuid, 'e2000000-0000-0000-0000-000000000005'::uuid,
     'mes_executions', '工序报工与质检', '/mes/executions', 'views/manufacturing/OperationExecutionView.vue',
     'PlayCircleOutlined', 6, 'mes:execution:manage', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000057'::uuid, 'e0000000-0000-0000-0000-000000000005'::uuid,
     'mes_receipts', '成品完工入库', '/mes/receipts', 'views/manufacturing/FinishedGoodsReceiptView.vue',
     'InboxOutlined', 7, 'mes:finished:confirm', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000001'::uuid, TRUE, 'ACTIVE'),
    ('e2000000-0000-0000-0000-000000000057'::uuid, 'e2000000-0000-0000-0000-000000000005'::uuid,
     'mes_receipts', '成品完工入库', '/mes/receipts', 'views/manufacturing/FinishedGoodsReceiptView.vue',
     'InboxOutlined', 7, 'mes:finished:confirm', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE')
ON CONFLICT (id) DO UPDATE
   SET parent_id = EXCLUDED.parent_id, menu_name = EXCLUDED.menu_name, route_path = EXCLUDED.route_path,
       component_path = EXCLUDED.component_path, icon = EXCLUDED.icon, sort_order = EXCLUDED.sort_order,
       permission_code = EXCLUDED.permission_code, tenant_id = EXCLUDED.tenant_id, visible = EXCLUDED.visible,
       status = EXCLUDED.status, isdel = EXCLUDED.isdel, updated_at = CURRENT_TIMESTAMP;

-- 质检角色必须能从导航直接进入采购质检页；仓库角色需要进入该页执行实物处置。
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT r.id, m.id, 0
FROM auth_role r
JOIN auth_menu m ON m.tenant_id = r.tenant_id AND m.menu_code = 'purchase_quality' AND m.isdel = 0
WHERE r.status = 'ACTIVE' AND r.isdel = 0
  AND r.role_code IN ('tenant.admin', 'mes.inspector', 'warehouse.operator', 'purchase.agent')
ON CONFLICT DO NOTHING;

-- MES 质检人员需要从当前导航进入 BOM、路线、工序、成品入库页面；管理员保留全租户配置入口。
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT r.id, m.id, 0
FROM auth_role r
JOIN auth_menu m ON m.tenant_id = r.tenant_id AND m.menu_code IN
    ('mes_bom', 'mes_routing', 'mes_executions', 'mes_receipts') AND m.isdel = 0
WHERE r.status = 'ACTIVE' AND r.isdel = 0
  AND r.role_code IN ('tenant.admin', 'mes.inspector')
ON CONFLICT DO NOTHING;

-- 仓库人员可从导航打开成品入库页，页面按钮仍由 mes:finished:confirm 控制。
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT r.id, m.id, 0
FROM auth_role r
JOIN auth_menu m ON m.tenant_id = r.tenant_id AND m.menu_code = 'mes_receipts' AND m.isdel = 0
WHERE r.status = 'ACTIVE' AND r.isdel = 0 AND r.role_code = 'warehouse.operator'
ON CONFLICT DO NOTHING;
