-- ============================================================================
-- V14：补齐 IoT 模型/遥测导航，并允许生产质检角色读取工单来源销售行。
-- 只增加只读权限和已实现页面入口，不扩大设备写权限或销售写权限。
-- ============================================================================

INSERT INTO auth_menu
    (id, parent_id, menu_code, menu_name, route_path, component_path, icon, sort_order,
     permission_code, created_at, updated_at, isdel, tenant_id, visible, status)
VALUES
    ('e0000000-0000-0000-0000-000000000063'::uuid, 'e0000000-0000-0000-0000-000000000006'::uuid,
     'iot_profile', '设备模型与告警规则', '/iot/profiles', 'views/iot/DeviceProfileView.vue',
     'ControlOutlined', 1, 'iot:device:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000001'::uuid, TRUE, 'ACTIVE'),
    ('e2000000-0000-0000-0000-000000000063'::uuid, 'e2000000-0000-0000-0000-000000000006'::uuid,
     'iot_profile', '设备模型与告警规则', '/iot/profiles', 'views/iot/DeviceProfileView.vue',
     'ControlOutlined', 1, 'iot:device:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE'),
    ('e0000000-0000-0000-0000-000000000064'::uuid, 'e0000000-0000-0000-0000-000000000006'::uuid,
     'iot_telemetry', '设备遥测监控', '/iot/telemetry', 'views/iot/TelemetryView.vue',
     'LineChartOutlined', 3, 'iot:telemetry:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000001'::uuid, TRUE, 'ACTIVE'),
    ('e2000000-0000-0000-0000-000000000064'::uuid, 'e2000000-0000-0000-0000-000000000006'::uuid,
     'iot_telemetry', '设备遥测监控', '/iot/telemetry', 'views/iot/TelemetryView.vue',
     'LineChartOutlined', 3, 'iot:telemetry:view', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0,
     'a0000000-0000-0000-0000-000000000002'::uuid, TRUE, 'ACTIVE')
ON CONFLICT (id) DO UPDATE
   SET parent_id = EXCLUDED.parent_id, menu_name = EXCLUDED.menu_name, route_path = EXCLUDED.route_path,
       component_path = EXCLUDED.component_path, icon = EXCLUDED.icon, sort_order = EXCLUDED.sort_order,
       permission_code = EXCLUDED.permission_code, tenant_id = EXCLUDED.tenant_id,
       visible = EXCLUDED.visible, status = EXCLUDED.status, isdel = EXCLUDED.isdel,
       updated_at = CURRENT_TIMESTAMP;

INSERT INTO auth_role_menu (role_id, menu_id, isdel)
SELECT r.id, m.id, 0
FROM auth_role r
JOIN auth_menu m ON m.tenant_id = r.tenant_id
                AND m.menu_code IN ('iot_profile', 'iot_telemetry')
                AND m.isdel = 0
WHERE r.role_code IN ('tenant.admin', 'iot.engineer')
  AND r.status = 'ACTIVE'
  AND r.isdel = 0
ON CONFLICT DO NOTHING;

-- 生产工单创建页只读取已审核销售订单行作为来源，不授予任何销售命令权限。
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT r.id, p.id, 0
FROM auth_role r
JOIN auth_permission p ON p.permission_code = 'sales:order:view' AND p.isdel = 0
WHERE r.role_code = 'mes.inspector'
  AND r.status = 'ACTIVE'
  AND r.isdel = 0
ON CONFLICT DO NOTHING;
