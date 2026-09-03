-- ==============================================================================
-- Flyway Migration Script: V2__seed_auth_demo_data.sql
-- 模块说明: Platform-Auth 认证与权限中心开发演示种子数据
-- 适用数据库: PostgreSQL 12.1及以上兼容
-- 包含数据:
--   1. 演示租户: tenant_demo_a (华东制造一号基地)
--   2. 6 类正式角色: 租户管理员、销售人员、采购人员、仓库人员、生产质检人员、IoT人员
--   3. 6 类演示账号: 密码统一为 123456 (BCrypt 加密)
--   4. 一期全部业务功能权限点 (auth, master, inventory, purchasing, sales, mes, iot, gis, ai)
--   5. 一期全部动态菜单树 (包含控制台主页、主数据、采购、销售、制造、IoT、地图、AI、系统管理等)
--   6. 角色与权限关联 (auth_role_permission)
--   7. 角色与菜单关联 (auth_role_menu)
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. 插入演示租户: tenant_demo_a (华东制造一号基地)
-- ------------------------------------------------------------------------------
INSERT INTO auth_tenant (id, tenant_code, tenant_name, status, created_by, updated_by, isdel)
VALUES (
    'a0000000-0000-0000-0000-000000000001'::uuid,
    'tenant_demo_a',
    '华东制造一号基地',
    'ACTIVE',
    'system',
    'system',
    0
) ON CONFLICT (id) DO NOTHING;


-- ------------------------------------------------------------------------------
-- 2. 插入 6 类正式业务角色
-- ------------------------------------------------------------------------------
INSERT INTO auth_role (id, tenant_id, role_code, role_name, description, status, created_by, updated_by, isdel)
VALUES
    -- 1. 租户管理员
    ('b0000000-0000-0000-0000-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'tenant.admin', '租户管理员', '负责当前租户内的账号、角色、权限、菜单与系统基础配置管理', 'ACTIVE', 'system', 'system', 0),
    -- 2. 销售人员
    ('b0000000-0000-0000-0000-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'sales.rep', '销售人员', '负责客户和销售订单的创建、提交、审核、履约进度跟踪及人工完成', 'ACTIVE', 'system', 'system', 0),
    -- 3. 采购人员
    ('b0000000-0000-0000-0000-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'purchase.agent', '采购人员', '负责供应商和采购订单创建、生产来源关联、提交、审核、供应方退回协调及人工完成', 'ACTIVE', 'system', 'system', 0),
    -- 4. 仓库人员
    ('b0000000-0000-0000-0000-000000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'warehouse.operator', '仓库人员', '负责到货外观验收、收货前拒收/实际接收、质量处置实物执行、上架、直接拣货、发货、异常退回/释放及领退料/入库实物确认', 'ACTIVE', 'system', 'system', 0),
    -- 5. 生产质检人员
    ('b0000000-0000-0000-0000-000000000005'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'mes.inspector', '生产质检人员', '负责BOM、工艺路线、工单、派工、工序执行、报工、采购到货质检、放行/报废决定及生产侧领退料/成品入库单创建', 'ACTIVE', 'system', 'system', 0),
    -- 6. IoT人员
    ('b0000000-0000-0000-0000-000000000006'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'iot.engineer', 'IoT人员', '负责IoT设备、凭证、遥测、状态、告警确认及工序业务上下文补充', 'ACTIVE', 'system', 'system', 0)
ON CONFLICT (id) DO NOTHING;


-- ------------------------------------------------------------------------------
-- 3. 插入 6 类演示账号 (密码均为 123456 -> BCrypt: $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2)
-- ------------------------------------------------------------------------------
INSERT INTO auth_user (id, tenant_id, user_no, username, password_hash, real_name, email, phone, status, created_by, updated_by, isdel)
VALUES
    -- 1. 租户管理员账号: admin.zhang
    ('c0000000-0000-0000-0000-000000000001'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'U1001', 'admin.zhang', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张管理', 'admin.zhang@ailearn.com', '13800000001', 'ACTIVE', 'system', 'system', 0),
    -- 2. 销售人员账号: sales.liu
    ('c0000000-0000-0000-0000-000000000002'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'U1002', 'sales.liu', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '刘销售', 'sales.liu@ailearn.com', '13800000002', 'ACTIVE', 'system', 'system', 0),
    -- 3. 采购人员账号: buyer.chen
    ('c0000000-0000-0000-0000-000000000003'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'U1003', 'buyer.chen', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '陈采购', 'buyer.chen@ailearn.com', '13800000003', 'ACTIVE', 'system', 'system', 0),
    -- 4. 仓库人员账号: wh.operator
    ('c0000000-0000-0000-0000-000000000004'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'U1004', 'wh.operator', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '王库管', 'wh.operator@ailearn.com', '13800000004', 'ACTIVE', 'system', 'system', 0),
    -- 5. 生产质检人员账号: mes.inspector
    ('c0000000-0000-0000-0000-000000000005'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'U1005', 'mes.inspector', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李质检', 'mes.inspector@ailearn.com', '13800000005', 'ACTIVE', 'system', 'system', 0),
    -- 6. IoT人员账号: iot.engineer
    ('c0000000-0000-0000-0000-000000000006'::uuid, 'a0000000-0000-0000-0000-000000000001'::uuid, 'U1006', 'iot.engineer', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '赵物联', 'iot.engineer@ailearn.com', '13800000006', 'ACTIVE', 'system', 'system', 0)
ON CONFLICT (id) DO NOTHING;


-- ------------------------------------------------------------------------------
-- 4. 建立用户与角色对应关系
-- ------------------------------------------------------------------------------
INSERT INTO auth_user_role (tenant_id, user_id, role_id, isdel)
VALUES
    -- admin.zhang -> tenant.admin
    ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000001'::uuid, 'b0000000-0000-0000-0000-000000000001'::uuid, 0),
    -- sales.liu -> sales.rep
    ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000002'::uuid, 'b0000000-0000-0000-0000-000000000002'::uuid, 0),
    -- buyer.chen -> purchase.agent
    ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000003'::uuid, 'b0000000-0000-0000-0000-000000000003'::uuid, 0),
    -- wh.operator -> warehouse.operator
    ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000004'::uuid, 'b0000000-0000-0000-0000-000000000004'::uuid, 0),
    -- mes.inspector -> mes.inspector
    ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000005'::uuid, 'b0000000-0000-0000-0000-000000000005'::uuid, 0),
    -- iot.engineer -> iot.engineer
    ('a0000000-0000-0000-0000-000000000001'::uuid, 'c0000000-0000-0000-0000-000000000006'::uuid, 'b0000000-0000-0000-0000-000000000006'::uuid, 0)
ON CONFLICT DO NOTHING;


-- ------------------------------------------------------------------------------
-- 5. 插入一期全部功能权限点 (auth_permission)
-- ------------------------------------------------------------------------------
INSERT INTO auth_permission (id, permission_code, permission_name, module, description, isdel)
VALUES
    -- [auth 模块]
    ('d0000000-0000-0000-0000-000000000101'::uuid, 'auth:tenant:view', '租户查询', 'auth', '查询租户信息与列表', 0),
    ('d0000000-0000-0000-0000-000000000102'::uuid, 'auth:tenant:manage', '租户管理', 'auth', '创建与维护租户信息', 0),
    ('d0000000-0000-0000-0000-000000000103'::uuid, 'auth:user:view', '用户查询', 'auth', '查询用户列表与账号详情', 0),
    ('d0000000-0000-0000-0000-000000000104'::uuid, 'auth:user:manage', '用户管理', 'auth', '创建、修改、禁用用户账号', 0),
    ('d0000000-0000-0000-0000-000000000105'::uuid, 'auth:role:view', '角色查询', 'auth', '查询角色列表与权限分配情况', 0),
    ('d0000000-0000-0000-0000-000000000106'::uuid, 'auth:role:manage', '角色管理', 'auth', '创建、维护角色及权限分配', 0),
    ('d0000000-0000-0000-0000-000000000107'::uuid, 'auth:menu:view', '菜单查询', 'auth', '查询动态菜单树与配置', 0),
    ('d0000000-0000-0000-0000-000000000108'::uuid, 'auth:menu:manage', '菜单管理', 'auth', '创建、修改系统菜单树结构', 0),
    ('d0000000-0000-0000-0000-000000000109'::uuid, 'auth:session:view', '会话查询', 'auth', '查询在线用户会话与Token状态', 0),
    ('d0000000-0000-0000-0000-000000000110'::uuid, 'auth:session:revoke', '会话注销', 'auth', '强制踢除或注销在线会话', 0),

    -- [master 主数据模块]
    ('d0000000-0000-0000-0000-000000000201'::uuid, 'inv:product:view', '商品主数据查询', 'master', '查询物料与商品主数据', 0),
    ('d0000000-0000-0000-0000-000000000202'::uuid, 'inv:product:manage', '商品主数据管理', 'master', '维护商品物料主数据', 0),
    ('d0000000-0000-0000-0000-000000000203'::uuid, 'inv:warehouse:view', '仓库库位查询', 'master', '查询仓库与库位信息', 0),
    ('d0000000-0000-0000-0000-000000000204'::uuid, 'inv:warehouse:manage', '仓库库位管理', 'master', '维护仓库与库位主数据', 0),

    -- [inventory 库存模块]
    ('d0000000-0000-0000-0000-000000000301'::uuid, 'inv:balance:view', '实物库存查询', 'inventory', '查询库位实物库存与可用量', 0),
    ('d0000000-0000-0000-0000-000000000302'::uuid, 'inv:reservation:view', '库存预留查询', 'inventory', '查询订单预留明细与分配', 0),
    ('d0000000-0000-0000-0000-000000000303'::uuid, 'inv:transaction:view', '库存流水查询', 'inventory', '查询不可篡改的库存交易流水', 0),

    -- [purchasing 采购模块]
    ('d0000000-0000-0000-0000-000000000401'::uuid, 'pur:order:view', '采购订单查询', 'purchasing', '查询采购订单列表与详情', 0),
    ('d0000000-0000-0000-0000-000000000402'::uuid, 'pur:order:create', '采购订单创建', 'purchasing', '创建草稿态采购订单及明细', 0),
    ('d0000000-0000-0000-0000-000000000403'::uuid, 'pur:order:submit', '采购订单提交', 'purchasing', '提交草稿态采购订单进入审核', 0),
    ('d0000000-0000-0000-0000-000000000404'::uuid, 'pur:order:approve', '采购订单审核', 'purchasing', '审核通过采购订单', 0),
    ('d0000000-0000-0000-0000-000000000405'::uuid, 'pur:order:complete', '采购订单人工完成', 'purchasing', '人工终止剩余未收货履约', 0),
    ('d0000000-0000-0000-0000-000000000406'::uuid, 'pur:receipt:view', '采购收货记录查询', 'purchasing', '查询到货验收与收货记录', 0),
    ('d0000000-0000-0000-0000-000000000407'::uuid, 'pur:receipt:confirm', '仓库到货验收与收货', 'purchasing', '仓库到货外观验收、拒收与实际接收确认(进入质量隔离位)', 0),
    ('d0000000-0000-0000-0000-000000000408'::uuid, 'pur:quality:inspect', '采购到货质检', 'purchasing', '生产质检人员记录到货质检合格与不合格数量', 0),
    ('d0000000-0000-0000-0000-000000000409'::uuid, 'pur:quality:release', '采购质量放行决定', 'purchasing', '生产质检人员对合格品做出放行决定', 0),
    ('d0000000-0000-0000-0000-000000000410'::uuid, 'pur:quality:return', '采购退回供应方决定', 'purchasing', '采购人员对不合格品做出退回供应方决定', 0),
    ('d0000000-0000-0000-0000-000000000411'::uuid, 'pur:quality:scrap', '采购质量报废决定', 'purchasing', '生产质检人员对不合格品做出报废决定', 0),
    ('d0000000-0000-0000-0000-000000000412'::uuid, 'pur:disposition:confirm', '质量处置执行确认', 'purchasing', '仓库人员确认实物移位、退回出库或报废扣减', 0),
    ('d0000000-0000-0000-0000-000000000413'::uuid, 'pur:putaway:confirm', '仓库上架确认', 'purchasing', '仓库人员将放行货物从收货暂存位上架到存储库位', 0),

    -- [sales 销售模块]
    ('d0000000-0000-0000-0000-000000000501'::uuid, 'sales:order:view', '销售订单查询', 'sales', '查询销售订单列表与动态履约进度', 0),
    ('d0000000-0000-0000-0000-000000000502'::uuid, 'sales:order:create', '销售订单创建', 'sales', '创建销售订单及需求明细', 0),
    ('d0000000-0000-0000-0000-000000000503'::uuid, 'sales:order:submit', '销售订单提交', 'sales', '提交销售订单进入审核', 0),
    ('d0000000-0000-0000-0000-000000000504'::uuid, 'sales:order:approve', '销售订单审核', 'sales', '审核通过销售订单', 0),
    ('d0000000-0000-0000-0000-000000000505'::uuid, 'sales:order:complete', '销售订单人工完成', 'sales', '人工终止剩余未发货履约', 0),
    ('d0000000-0000-0000-0000-000000000506'::uuid, 'sales:reservation:release', '异常释放未拣预留', 'sales', '释放销售行未拣预留库存', 0),
    ('d0000000-0000-0000-0000-000000000507'::uuid, 'sales:pick:confirm', '仓库直接拣货确认', 'sales', '仓库执行直接拣货(内部自动预留并移位至发货暂存位)', 0),
    ('d0000000-0000-0000-0000-000000000508'::uuid, 'sales:pick:return', '仓库退回未发货拣货', 'sales', '将发货暂存货物移回来源库位', 0),
    ('d0000000-0000-0000-0000-000000000509'::uuid, 'sales:shipment:confirm', '仓库发货出库确认', 'sales', '仓库发货扣减企业实物总库存', 0),

    -- [mes 制造执行模块]
    ('d0000000-0000-0000-0000-000000000601'::uuid, 'mes:workorder:view', '生产工单查询', 'mes', '查询生产工单与工序明细', 0),
    ('d0000000-0000-0000-0000-000000000602'::uuid, 'mes:workorder:create', '生产工单创建', 'mes', '创建生产工单并人工关联来源销售行', 0),
    ('d0000000-0000-0000-0000-000000000603'::uuid, 'mes:workorder:submit', '生产工单提交', 'mes', '提交生产工单', 0),
    ('d0000000-0000-0000-0000-000000000604'::uuid, 'mes:workorder:approve', '生产工单审核与下达', 'mes', '生产质检人员审核并下达工单', 0),
    ('d0000000-0000-0000-0000-000000000605'::uuid, 'mes:workorder:complete', '生产工单人工完成', 'mes', '人工终止剩余未生产履约', 0),
    ('d0000000-0000-0000-0000-000000000606'::uuid, 'mes:dispatch:manage', '工单派工管理', 'mes', '为工序执行指派人员或设备', 0),
    ('d0000000-0000-0000-0000-000000000607'::uuid, 'mes:execution:manage', '工序执行管理', 'mes', '开始、暂停、恢复与完成工序执行', 0),
    ('d0000000-0000-0000-0000-000000000608'::uuid, 'mes:report:manage', '工序报工管理', 'mes', '记录生产产出与工时消耗', 0),
    ('d0000000-0000-0000-0000-000000000609'::uuid, 'mes:quality:inspect', '生产工序质检', 'mes', '工序过程检验与完工质量判定', 0),
    ('d0000000-0000-0000-0000-000000000610'::uuid, 'mes:material:requisition', '生产领退料单创建', 'mes', '生产侧发起原料领料或退料单据', 0),
    ('d0000000-0000-0000-0000-000000000611'::uuid, 'mes:material:confirm', '仓库确认领退料发料', 'mes', '仓库人员确认发料出库或退料入库', 0),
    ('d0000000-0000-0000-0000-000000000612'::uuid, 'mes:finished:receipt', '生产成品入库单创建', 'mes', '生产侧发起合格成品入库单据', 0),
    ('d0000000-0000-0000-0000-000000000613'::uuid, 'mes:finished:confirm', '仓库成品入库确认', 'mes', '仓库人员确认合格成品入库上架', 0),

    -- [iot 物联网模块]
    ('d0000000-0000-0000-0000-000000000701'::uuid, 'iot:device:view', 'IoT设备查询', 'iot', '查询IoT设备台账与在线状态', 0),
    ('d0000000-0000-0000-0000-000000000702'::uuid, 'iot:device:manage', 'IoT设备与凭证管理', 'iot', '创建设备、配置MQTT认证凭证', 0),
    ('d0000000-0000-0000-0000-000000000703'::uuid, 'iot:telemetry:view', '设备遥测与状态查询', 'iot', '查看实时与历史遥测时序数据', 0),
    ('d0000000-0000-0000-0000-000000000704'::uuid, 'iot:alarm:view', '设备告警查询', 'iot', '查看设备告警列表与详情', 0),
    ('d0000000-0000-0000-0000-000000000705'::uuid, 'iot:alarm:ack', '设备告警确认', 'iot', '确认已发生设备告警', 0),
    ('d0000000-0000-0000-0000-000000000706'::uuid, 'iot:alarm:context', '设备告警补充上下文', 'iot', '为告警补充生产工序与工单上下文', 0),

    -- [gis & dashboard 模块]
    ('d0000000-0000-0000-0000-000000000801'::uuid, 'gis:map:view', '厂区二维地图查看', 'gis', '查看厂区二维平面点位与状态', 0),
    ('d0000000-0000-0000-0000-000000000802'::uuid, 'dashboard:view', '综合看板查看', 'gis', '查看跨模块业务指标与实时看板', 0),

    -- [ai 模块]
    ('d0000000-0000-0000-0000-000000000901'::uuid, 'ai:chat:query', 'AI助手问答对话', 'ai', '通过只读工具进行智能业务问答', 0),
    ('d0000000-0000-0000-0000-000000000902'::uuid, 'ai:trace:view', '跨域追溯查询', 'ai', '查询从销售到生产、采购、IoT的全链路追溯', 0),
    ('d0000000-0000-0000-0000-000000000903'::uuid, 'ai:audit:view', 'AI工具调用审计查看', 'ai', '查看AI工具只读调用日志与审计信息', 0)
ON CONFLICT (id) DO NOTHING;


-- ------------------------------------------------------------------------------
-- 6. 插入一期全部动态菜单树 (auth_menu)
-- ------------------------------------------------------------------------------
-- 6.1 顶级菜单
INSERT INTO auth_menu (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order, permission_code, isdel)
VALUES
    ('e0000000-0000-0000-0000-000000000001'::uuid, NULL, 'dashboard', '控制台看板', '/dashboard', 'views/Dashboard.vue', 'DashboardOutlined', 1, 'dashboard:view', 0),
    ('e0000000-0000-0000-0000-000000000002'::uuid, NULL, 'master_data', '主数据管理', '/master-data', 'views/MasterData.vue', 'DatabaseOutlined', 2, NULL, 0),
    ('e0000000-0000-0000-0000-000000000003'::uuid, NULL, 'purchase', '采购入库', '/purchase', 'views/Purchase.vue', 'ShoppingOutlined', 3, NULL, 0),
    ('e0000000-0000-0000-0000-000000000004'::uuid, NULL, 'sales', '销售出库', '/sales', 'views/Sales.vue', 'ShopOutlined', 4, NULL, 0),
    ('e0000000-0000-0000-0000-000000000005'::uuid, NULL, 'mes', '制造执行', '/mes', 'views/Mes.vue', 'ToolOutlined', 5, NULL, 0),
    ('e0000000-0000-0000-0000-000000000006'::uuid, NULL, 'iot', 'IoT设备与告警', '/iot', 'views/Iot.vue', 'ApiOutlined', 6, NULL, 0),
    ('e0000000-0000-0000-0000-000000000007'::uuid, NULL, 'gis', '厂区二维地图', '/gis/map', 'views/gis/SiteMap.vue', 'CompassOutlined', 7, 'gis:map:view', 0),
    ('e0000000-0000-0000-0000-000000000008'::uuid, NULL, 'ai', 'AI助手与追溯', '/ai', 'views/AiAssistant.vue', 'RobotOutlined', 8, NULL, 0),
    ('e0000000-0000-0000-0000-000000000009'::uuid, NULL, 'system', '系统管理', '/system', 'views/System.vue', 'SettingOutlined', 9, NULL, 0)
ON CONFLICT (id) DO NOTHING;

-- 6.2 二级子菜单
INSERT INTO auth_menu (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order, permission_code, isdel)
VALUES
    -- [主数据管理 子菜单]
    ('e0000000-0000-0000-0000-000000000021'::uuid, 'e0000000-0000-0000-0000-000000000002'::uuid, 'master_product', '商品主数据', '/master-data/products', 'views/master/ProductList.vue', 'AppstoreOutlined', 1, 'inv:product:view', 0),
    ('e0000000-0000-0000-0000-000000000022'::uuid, 'e0000000-0000-0000-0000-000000000002'::uuid, 'master_warehouse', '仓库与库位', '/master-data/warehouses', 'views/master/WarehouseList.vue', 'HomeOutlined', 2, 'inv:warehouse:view', 0),
    ('e0000000-0000-0000-0000-000000000023'::uuid, 'e0000000-0000-0000-0000-000000000002'::uuid, 'master_inventory', '实物库存台账', '/master-data/inventory', 'views/master/InventoryBalance.vue', 'TableOutlined', 3, 'inv:balance:view', 0),

    -- [采购入库 子菜单]
    ('e0000000-0000-0000-0000-000000000031'::uuid, 'e0000000-0000-0000-0000-000000000003'::uuid, 'purchase_order', '采购订单', '/purchase/orders', 'views/purchase/PurchaseOrderList.vue', 'FileTextOutlined', 1, 'pur:order:view', 0),
    ('e0000000-0000-0000-0000-000000000032'::uuid, 'e0000000-0000-0000-0000-000000000003'::uuid, 'purchase_inbound', '到货收货与质检', '/purchase/inbound', 'views/purchase/PurchaseInbound.vue', 'InboxOutlined', 2, 'pur:receipt:view', 0),
    ('e0000000-0000-0000-0000-000000000033'::uuid, 'e0000000-0000-0000-0000-000000000003'::uuid, 'purchase_putaway', '上架任务', '/purchase/putaway', 'views/purchase/PutawayTaskList.vue', 'VerticalAlignTopOutlined', 3, 'pur:putaway:confirm', 0),

    -- [销售出库 子菜单]
    ('e0000000-0000-0000-0000-000000000041'::uuid, 'e0000000-0000-0000-0000-000000000004'::uuid, 'sales_order', '销售订单', '/sales/orders', 'views/sales/SalesOrderList.vue', 'FileProtectOutlined', 1, 'sales:order:view', 0),
    ('e0000000-0000-0000-0000-000000000042'::uuid, 'e0000000-0000-0000-0000-000000000004'::uuid, 'sales_outbound', '直接拣货与发货', '/sales/outbound', 'views/sales/SalesOutbound.vue', 'ExportOutlined', 2, 'sales:pick:confirm', 0),

    -- [制造执行 子菜单]
    ('e0000000-0000-0000-0000-000000000051'::uuid, 'e0000000-0000-0000-0000-000000000005'::uuid, 'mes_work_order', '生产工单', '/mes/work-orders', 'views/mes/WorkOrderList.vue', 'ScheduleOutlined', 1, 'mes:workorder:view', 0),
    ('e0000000-0000-0000-0000-000000000052'::uuid, 'e0000000-0000-0000-0000-000000000005'::uuid, 'mes_execution', '派工与工序执行', '/mes/execution', 'views/mes/OperationExecution.vue', 'PlayCircleOutlined', 2, 'mes:execution:manage', 0),
    ('e0000000-0000-0000-0000-000000000053'::uuid, 'e0000000-0000-0000-0000-000000000005'::uuid, 'mes_material', '生产领料与成品入库', '/mes/materials', 'views/mes/MaterialHandling.vue', 'SwapOutlined', 3, 'mes:material:requisition', 0),

    -- [IoT设备与告警 子菜单]
    ('e0000000-0000-0000-0000-000000000061'::uuid, 'e0000000-0000-0000-0000-000000000006'::uuid, 'iot_device', '设备列表与详情', '/iot/devices', 'views/iot/DeviceList.vue', 'HddOutlined', 1, 'iot:device:view', 0),
    ('e0000000-0000-0000-0000-000000000062'::uuid, 'e0000000-0000-0000-0000-000000000006'::uuid, 'iot_alarm', '设备告警中心', '/iot/alarms', 'views/iot/DeviceAlarm.vue', 'AlertOutlined', 2, 'iot:alarm:view', 0),

    -- [AI助手与追溯 子菜单]
    ('e0000000-0000-0000-0000-000000000081'::uuid, 'e0000000-0000-0000-0000-000000000008'::uuid, 'ai_chat', 'AI对话问答', '/ai/chat', 'views/ai/AiChat.vue', 'CommentOutlined', 1, 'ai:chat:query', 0),
    ('e0000000-0000-0000-0000-000000000082'::uuid, 'e0000000-0000-0000-0000-000000000008'::uuid, 'ai_trace', '跨域追溯查询', '/ai/trace', 'views/ai/TraceabilityQuery.vue', 'NodeIndexOutlined', 2, 'ai:trace:view', 0),
    ('e0000000-0000-0000-0000-000000000083'::uuid, 'e0000000-0000-0000-0000-000000000008'::uuid, 'ai_audit', 'AI工具调用审计', '/ai/audit', 'views/ai/ToolAudit.vue', 'AuditOutlined', 3, 'ai:audit:view', 0),

    -- [系统管理 子菜单]
    ('e0000000-0000-0000-0000-000000000091'::uuid, 'e0000000-0000-0000-0000-000000000009'::uuid, 'sys_user', '用户管理', '/system/users', 'views/system/UserList.vue', 'UserOutlined', 1, 'auth:user:view', 0),
    ('e0000000-0000-0000-0000-000000000092'::uuid, 'e0000000-0000-0000-0000-000000000009'::uuid, 'sys_role', '角色与权限', '/system/roles', 'views/system/RoleList.vue', 'SafetyCertificateOutlined', 2, 'auth:role:view', 0),
    ('e0000000-0000-0000-0000-000000000093'::uuid, 'e0000000-0000-0000-0000-000000000009'::uuid, 'sys_menu', '菜单管理', '/system/menus', 'views/system/MenuList.vue', 'MenuOutlined', 3, 'auth:menu:view', 0)
ON CONFLICT (id) DO NOTHING;


-- ------------------------------------------------------------------------------
-- 7. 角色与权限关系映射 (auth_role_permission)
-- ------------------------------------------------------------------------------

-- 7.1 租户管理员 (tenant.admin): 拥有系统管理与全部模块的查看/审计权限
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
    'auth:session:view', 'auth:session:revoke',
    'dashboard:view', 'gis:map:view',
    'inv:product:view', 'inv:warehouse:view', 'inv:balance:view', 'inv:reservation:view', 'inv:transaction:view',
    'pur:order:view', 'pur:receipt:view',
    'sales:order:view',
    'mes:workorder:view',
    'iot:device:view', 'iot:telemetry:view', 'iot:alarm:view',
    'ai:chat:query', 'ai:trace:view', 'ai:audit:view'
)
ON CONFLICT DO NOTHING;

-- 7.2 销售人员 (sales.rep): 销售订单全生命周期操作 + 主数据/库存查看 + 地图与AI追溯
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000002'::uuid,
    p.id,
    0
FROM auth_permission p
WHERE p.permission_code IN (
    'dashboard:view', 'gis:map:view',
    'inv:product:view', 'inv:warehouse:view', 'inv:balance:view', 'inv:reservation:view',
    'sales:order:view', 'sales:order:create', 'sales:order:submit', 'sales:order:approve', 'sales:order:complete',
    'ai:chat:query', 'ai:trace:view'
)
ON CONFLICT DO NOTHING;

-- 7.3 采购人员 (purchase.agent): 采购订单全生命周期 + 质量退回决定 + 主数据/库存查看 + 地图与AI追溯
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000003'::uuid,
    p.id,
    0
FROM auth_permission p
WHERE p.permission_code IN (
    'dashboard:view', 'gis:map:view',
    'inv:product:view', 'inv:warehouse:view', 'inv:balance:view',
    'pur:order:view', 'pur:order:create', 'pur:order:submit', 'pur:order:approve', 'pur:order:complete',
    'pur:receipt:view', 'pur:quality:return',
    'ai:chat:query', 'ai:trace:view'
)
ON CONFLICT DO NOTHING;

-- 7.4 仓库人员 (warehouse.operator): 到货验收/拒收/接收、质量处置执行、上架、直接拣货、发货、领退料/成品实物确认
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000004'::uuid,
    p.id,
    0
FROM auth_permission p
WHERE p.permission_code IN (
    'dashboard:view', 'gis:map:view',
    'inv:product:view', 'inv:warehouse:view', 'inv:location:view', 'inv:balance:view', 'inv:reservation:view', 'inv:transaction:view',
    'pur:receipt:view', 'pur:receipt:confirm', 'pur:disposition:confirm', 'pur:putaway:confirm',
    'sales:order:view', 'sales:pick:confirm', 'sales:pick:return', 'sales:reservation:release', 'sales:shipment:confirm',
    'mes:workorder:view', 'mes:material:confirm', 'mes:finished:confirm',
    'ai:chat:query', 'ai:trace:view'
)
ON CONFLICT DO NOTHING;

-- 7.5 生产质检人员 (mes.inspector): 工单、派工、执行、报工、工序质检、到货质检、放行/报废决定、领料/成品单据创建
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000005'::uuid,
    p.id,
    0
FROM auth_permission p
WHERE p.permission_code IN (
    'dashboard:view', 'gis:map:view',
    'inv:product:view', 'inv:warehouse:view', 'inv:balance:view',
    'pur:receipt:view', 'pur:quality:inspect', 'pur:quality:release', 'pur:quality:scrap',
    'mes:workorder:view', 'mes:workorder:create', 'mes:workorder:submit', 'mes:workorder:approve', 'mes:workorder:complete',
    'mes:dispatch:manage', 'mes:execution:manage', 'mes:report:manage', 'mes:quality:inspect',
    'mes:material:requisition', 'mes:finished:receipt',
    'ai:chat:query', 'ai:trace:view'
)
ON CONFLICT DO NOTHING;

-- 7.6 IoT人员 (iot.engineer): 设备与凭证管理、遥测与状态查询、告警确认及工序上下文补充
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000006'::uuid,
    p.id,
    0
FROM auth_permission p
WHERE p.permission_code IN (
    'dashboard:view', 'gis:map:view',
    'iot:device:view', 'iot:device:manage', 'iot:telemetry:view', 'iot:alarm:view', 'iot:alarm:ack', 'iot:alarm:context',
    'ai:chat:query', 'ai:trace:view'
)
ON CONFLICT DO NOTHING;


-- ------------------------------------------------------------------------------
-- 8. 角色与菜单关系映射 (auth_role_menu)
-- ------------------------------------------------------------------------------

-- 8.1 租户管理员 (tenant.admin): 拥有全部菜单访问权限
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000001'::uuid,
    m.id,
    0
FROM auth_menu m
ON CONFLICT DO NOTHING;

-- 8.2 销售人员 (sales.rep): 看板、主数据(商品/库存)、销售订单、地图、AI助手
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000002'::uuid,
    m.id,
    0
FROM auth_menu m
WHERE m.menu_code IN (
    'dashboard',
    'master_data', 'master_product', 'master_inventory',
    'sales', 'sales_order',
    'gis',
    'ai', 'ai_chat', 'ai_trace'
)
ON CONFLICT DO NOTHING;

-- 8.3 采购人员 (purchase.agent): 看板、主数据(商品/库存)、采购订单与到货质检跟踪、地图、AI助手
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000003'::uuid,
    m.id,
    0
FROM auth_menu m
WHERE m.menu_code IN (
    'dashboard',
    'master_data', 'master_product', 'master_inventory',
    'purchase', 'purchase_order', 'purchase_inbound',
    'gis',
    'ai', 'ai_chat', 'ai_trace'
)
ON CONFLICT DO NOTHING;

-- 8.4 仓库人员 (warehouse.operator): 看板、主数据全集、采购收货/上架、销售直接拣货与发货、生产领料/入库实物确认、地图、AI助手
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000004'::uuid,
    m.id,
    0
FROM auth_menu m
WHERE m.menu_code IN (
    'dashboard',
    'master_data', 'master_product', 'master_warehouse', 'master_inventory',
    'purchase', 'purchase_inbound', 'purchase_putaway',
    'sales', 'sales_outbound',
    'mes', 'mes_material',
    'gis',
    'ai', 'ai_chat', 'ai_trace'
)
ON CONFLICT DO NOTHING;

-- 8.5 生产质检人员 (mes.inspector): 看板、主数据(商品/库存)、采购到货质检、生产工单/执行/物料单据、地图、AI助手
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000005'::uuid,
    m.id,
    0
FROM auth_menu m
WHERE m.menu_code IN (
    'dashboard',
    'master_data', 'master_product', 'master_inventory',
    'purchase', 'purchase_inbound',
    'mes', 'mes_work_order', 'mes_execution', 'mes_material',
    'gis',
    'ai', 'ai_chat', 'ai_trace'
)
ON CONFLICT DO NOTHING;

-- 8.6 IoT人员 (iot.engineer): 看板、IoT设备与告警中心、地图、AI助手
INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT
    'b0000000-0000-0000-0000-000000000006'::uuid,
    m.id,
    0
FROM auth_menu m
WHERE m.menu_code IN (
    'dashboard',
    'iot', 'iot_device', 'iot_alarm',
    'gis',
    'ai', 'ai_chat', 'ai_trace'
)
ON CONFLICT DO NOTHING;
