-- ==============================================================================
-- Flyway Migration Script: V5__menu_tenant_isolation_and_visible_status.sql
-- 模块说明: 菜单多租户物理隔离改造与显隐、启停状态字段补充
-- 适用数据库: PostgreSQL 12.1及以上兼容
-- 重要顺序: 必须先移除 V1 的全局菜单编码唯一索引，再写入第二租户菜单副本。
-- ============================================================================

-- 1. 先删除 V1 的全局唯一索引，允许后续第二租户使用相同 menu_code。
DROP INDEX IF EXISTS uq_auth_menu_code_active;

-- V2 已经创建真实默认租户；V5 只允许回填到该租户，禁止创建替代租户。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM auth_tenant
        WHERE id = 'a0000000-0000-0000-0000-000000000001'::uuid
          AND isdel = 0
          AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION 'V5 requires existing active default tenant a0000000-0000-0000-0000-000000000001';
    END IF;
END
$$;

-- 2. 建立或恢复第二演示租户，角色、菜单和关联记录均使用此租户 ID。
INSERT INTO auth_tenant (id, tenant_code, tenant_name, status, created_by, updated_by, isdel)
VALUES (
    'a0000000-0000-0000-0000-000000000002'::uuid,
    'TENANT_B',
    '演示租户B',
    'ACTIVE',
    'system',
    'system',
    0
)
ON CONFLICT (id) DO UPDATE
SET tenant_code = EXCLUDED.tenant_code,
    tenant_name = EXCLUDED.tenant_name,
    status = EXCLUDED.status,
    updated_by = EXCLUDED.updated_by,
    isdel = EXCLUDED.isdel;

-- 3. 先补充可空物理字段，待历史数据回填与清洗完成后再收紧约束。
ALTER TABLE auth_menu
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS visible BOOLEAN,
    ADD COLUMN IF NOT EXISTS status VARCHAR(32);

COMMENT ON COLUMN auth_menu.tenant_id IS '所属租户ID (外键关联 auth_tenant.id)';
COMMENT ON COLUMN auth_menu.visible IS '菜单在侧边栏是否可见 (TRUE: 可见, FALSE: 隐藏)';
COMMENT ON COLUMN auth_menu.status IS '菜单启用状态 (ACTIVE: 启用, DISABLED: 停用)';

-- 4. 历史菜单全部回填到 V2 已有默认租户，并修正租户设置组件路径。
UPDATE auth_menu
SET tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
WHERE tenant_id IS NULL;

UPDATE auth_menu
SET visible = COALESCE(visible, TRUE),
    status = CASE
        WHEN status IN ('ACTIVE', 'DISABLED') THEN status
        ELSE 'ACTIVE'
    END;

UPDATE auth_menu
SET component_path = 'views/system/TenantSetting.vue'
WHERE menu_code = 'sys_tenant';

-- 5. 补充显式租户外键，随后把租户字段设为必填。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'auth_menu'::regclass
          AND conname = 'fk_auth_menu_tenant_id'
    ) THEN
        ALTER TABLE auth_menu
            ADD CONSTRAINT fk_auth_menu_tenant_id
            FOREIGN KEY (tenant_id) REFERENCES auth_tenant(id) ON DELETE RESTRICT;
    END IF;
END
$$;

ALTER TABLE auth_menu
    ALTER COLUMN visible SET DEFAULT TRUE,
    ALTER COLUMN status SET DEFAULT 'ACTIVE',
    ALTER COLUMN tenant_id SET NOT NULL,
    ALTER COLUMN visible SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;

-- 6. 为第二演示租户写入与默认租户同编码的完整菜单副本。
INSERT INTO auth_menu (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order, permission_code, tenant_id, visible, status, isdel)
VALUES
    ('e2000000-0000-0000-0000-000000000001'::uuid, NULL, 'dashboard', '控制台看板', '/dashboard', 'views/Dashboard.vue', 'DashboardOutlined', 1, 'dashboard:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000002'::uuid, NULL, 'master_data', '主数据管理', '/master-data', 'views/MasterData.vue', 'DatabaseOutlined', 2, NULL, 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000003'::uuid, NULL, 'purchase', '采购入库', '/purchase', 'views/Purchase.vue', 'ShoppingOutlined', 3, NULL, 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000004'::uuid, NULL, 'sales', '销售出库', '/sales', 'views/Sales.vue', 'ShopOutlined', 4, NULL, 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000005'::uuid, NULL, 'mes', '制造执行', '/mes', 'views/Mes.vue', 'ToolOutlined', 5, NULL, 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000006'::uuid, NULL, 'iot', 'IoT设备与告警', '/iot', 'views/Iot.vue', 'ApiOutlined', 6, NULL, 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000007'::uuid, NULL, 'gis', '厂区二维地图', '/gis/map', 'views/gis/SiteMap.vue', 'CompassOutlined', 7, 'gis:map:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000008'::uuid, NULL, 'ai', 'AI助手与追溯', '/ai', 'views/AiAssistant.vue', 'RobotOutlined', 8, NULL, 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000009'::uuid, NULL, 'system', '系统管理', '/system', 'views/System.vue', 'SettingOutlined', 9, NULL, 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000021'::uuid, 'e2000000-0000-0000-0000-000000000002'::uuid, 'master_product', '商品主数据', '/master-data/products', 'views/master/ProductList.vue', 'AppstoreOutlined', 1, 'inv:product:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000022'::uuid, 'e2000000-0000-0000-0000-000000000002'::uuid, 'master_warehouse', '仓库与库位', '/master-data/warehouses', 'views/master/WarehouseList.vue', 'HomeOutlined', 2, 'inv:warehouse:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000023'::uuid, 'e2000000-0000-0000-0000-000000000002'::uuid, 'master_inventory', '实物库存台账', '/master-data/inventory', 'views/master/InventoryBalance.vue', 'TableOutlined', 3, 'inv:balance:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000031'::uuid, 'e2000000-0000-0000-0000-000000000003'::uuid, 'purchase_order', '采购订单', '/purchase/orders', 'views/purchase/PurchaseOrderList.vue', 'FileTextOutlined', 1, 'pur:order:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000032'::uuid, 'e2000000-0000-0000-0000-000000000003'::uuid, 'purchase_inbound', '到货收货与质检', '/purchase/inbound', 'views/purchase/PurchaseInbound.vue', 'InboxOutlined', 2, 'pur:receipt:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000033'::uuid, 'e2000000-0000-0000-0000-000000000003'::uuid, 'purchase_putaway', '上架任务', '/purchase/putaway', 'views/purchase/PutawayTaskList.vue', 'VerticalAlignTopOutlined', 3, 'pur:putaway:confirm', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000041'::uuid, 'e2000000-0000-0000-0000-000000000004'::uuid, 'sales_order', '销售订单', '/sales/orders', 'views/sales/SalesOrderList.vue', 'FileProtectOutlined', 1, 'sales:order:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000042'::uuid, 'e2000000-0000-0000-0000-000000000004'::uuid, 'sales_outbound', '直接拣货与发货', '/sales/outbound', 'views/sales/SalesOutbound.vue', 'ExportOutlined', 2, 'sales:pick:confirm', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000051'::uuid, 'e2000000-0000-0000-0000-000000000005'::uuid, 'mes_work_order', '生产工单', '/mes/work-orders', 'views/mes/WorkOrderList.vue', 'ScheduleOutlined', 1, 'mes:workorder:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000052'::uuid, 'e2000000-0000-0000-0000-000000000005'::uuid, 'mes_execution', '派工与工序执行', '/mes/execution', 'views/mes/OperationExecution.vue', 'PlayCircleOutlined', 2, 'mes:execution:manage', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000053'::uuid, 'e2000000-0000-0000-0000-000000000005'::uuid, 'mes_material', '生产领料与成品入库', '/mes/materials', 'views/mes/MaterialHandling.vue', 'SwapOutlined', 3, 'mes:material:requisition', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000061'::uuid, 'e2000000-0000-0000-0000-000000000006'::uuid, 'iot_device', '设备列表与详情', '/iot/devices', 'views/iot/DeviceList.vue', 'HddOutlined', 1, 'iot:device:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000062'::uuid, 'e2000000-0000-0000-0000-000000000006'::uuid, 'iot_alarm', '设备告警中心', '/iot/alarms', 'views/iot/DeviceAlarm.vue', 'AlertOutlined', 2, 'iot:alarm:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000081'::uuid, 'e2000000-0000-0000-0000-000000000008'::uuid, 'ai_chat', 'AI对话问答', '/ai/chat', 'views/ai/AiChat.vue', 'CommentOutlined', 1, 'ai:chat:query', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000082'::uuid, 'e2000000-0000-0000-0000-000000000008'::uuid, 'ai_trace', '跨域追溯查询', '/ai/trace', 'views/ai/TraceabilityQuery.vue', 'NodeIndexOutlined', 2, 'ai:trace:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000083'::uuid, 'e2000000-0000-0000-0000-000000000008'::uuid, 'ai_audit', 'AI工具调用审计', '/ai/audit', 'views/ai/ToolAudit.vue', 'AuditOutlined', 3, 'ai:audit:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000090'::uuid, 'e2000000-0000-0000-0000-000000000009'::uuid, 'sys_tenant', '租户信息', '/system/tenant', 'views/system/TenantSetting.vue', 'ApartmentOutlined', 1, 'auth:tenant:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000091'::uuid, 'e2000000-0000-0000-0000-000000000009'::uuid, 'sys_user', '用户管理', '/system/users', 'views/system/UserList.vue', 'UserOutlined', 2, 'auth:user:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000092'::uuid, 'e2000000-0000-0000-0000-000000000009'::uuid, 'sys_role', '角色管理', '/system/roles', 'views/system/RoleList.vue', 'SafetyCertificateOutlined', 3, 'auth:role:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000094'::uuid, 'e2000000-0000-0000-0000-000000000009'::uuid, 'sys_permission', '权限清单', '/system/permissions', 'views/system/PermissionList.vue', 'KeyOutlined', 4, 'auth:role:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0),
    ('e2000000-0000-0000-0000-000000000093'::uuid, 'e2000000-0000-0000-0000-000000000009'::uuid, 'sys_menu', '菜单管理', '/system/menus', 'views/system/MenuList.vue', 'MenuOutlined', 5, 'auth:menu:view', 'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE', 0)
ON CONFLICT (id) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    menu_code = EXCLUDED.menu_code,
    menu_name = EXCLUDED.menu_name,
    route_path = EXCLUDED.route_path,
    component_path = EXCLUDED.component_path,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    permission_code = EXCLUDED.permission_code,
    tenant_id = EXCLUDED.tenant_id,
    visible = EXCLUDED.visible,
    status = EXCLUDED.status,
    isdel = EXCLUDED.isdel,
    updated_at = CURRENT_TIMESTAMP;

-- 7. 确保第二租户管理员角色及其菜单关联属于同一个第二租户。
INSERT INTO auth_role (id, tenant_id, role_code, role_name, description, status, created_by, updated_by, isdel)
VALUES (
    'b2000000-0000-0000-0000-000000000001'::uuid,
    'a0000000-0000-0000-0000-000000000002'::uuid,
    'tenant.admin',
    '租户管理员',
    '演示租户B管理员角色',
    'ACTIVE',
    'system',
    'system',
    0
)
ON CONFLICT (id) DO UPDATE
SET tenant_id = EXCLUDED.tenant_id,
    role_code = EXCLUDED.role_code,
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    status = EXCLUDED.status,
    updated_by = EXCLUDED.updated_by,
    isdel = EXCLUDED.isdel;

INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT r.id, m.id, 0
FROM auth_role r
CROSS JOIN auth_menu m
WHERE r.id = 'b2000000-0000-0000-0000-000000000001'::uuid
  AND r.tenant_id = 'a0000000-0000-0000-0000-000000000002'::uuid
  AND r.isdel = 0
  AND m.tenant_id = 'a0000000-0000-0000-0000-000000000002'::uuid
  AND m.isdel = 0
  AND NOT EXISTS (
      SELECT 1
      FROM auth_role_menu rm
      WHERE rm.role_id = r.id
        AND rm.menu_id = m.id
        AND rm.isdel = 0
  );

-- 8. 收紧 visible/status 合法值，并重建租户级菜单编码唯一约束。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'auth_menu'::regclass
          AND conname = 'ck_auth_menu_visible_allowed'
    ) THEN
        ALTER TABLE auth_menu
            ADD CONSTRAINT ck_auth_menu_visible_allowed CHECK (visible IN (TRUE, FALSE));
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'auth_menu'::regclass
          AND conname = 'ck_auth_menu_status_allowed'
    ) THEN
        ALTER TABLE auth_menu
            ADD CONSTRAINT ck_auth_menu_status_allowed CHECK (status IN ('ACTIVE', 'DISABLED'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_auth_menu_tenant_id ON auth_menu (tenant_id);
CREATE INDEX IF NOT EXISTS idx_auth_menu_tenant_parent ON auth_menu (tenant_id, parent_id);
DROP INDEX IF EXISTS uq_auth_menu_tenant_code_active;
CREATE UNIQUE INDEX uq_auth_menu_tenant_code_active
    ON auth_menu (tenant_id, menu_code)
    WHERE isdel = 0;
