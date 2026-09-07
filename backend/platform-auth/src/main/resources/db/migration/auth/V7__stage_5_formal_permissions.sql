-- ============================================================================
-- 阶段 5 正式查询、工单修改和超 BOM 领料权限增量。
-- 只新增缺失权限，不修改 V1-V6 历史脚本；重复执行保持幂等。
-- ============================================================================

INSERT INTO auth_permission (id, permission_code, permission_name, module, description, isdel)
VALUES
    ('d0000000-0000-0000-0000-000000000616'::uuid, 'mes:bom:view', 'BOM 查询', 'mes', '查询制造 BOM 事实', 0),
    ('d0000000-0000-0000-0000-000000000617'::uuid, 'mes:routing:view', '工艺路线查询', 'mes', '查询制造工艺路线事实', 0),
    ('d0000000-0000-0000-0000-000000000618'::uuid, 'mes:workorder:update', '生产工单修改', 'mes', '修改允许修改状态的生产工单', 0),
    ('d0000000-0000-0000-0000-000000000619'::uuid, 'mes:material:overage', '超 BOM 领料授权', 'mes', '授权提交或确认超出 BOM 预计用量的领料', 0),
    ('d0000000-0000-0000-0000-000000000820'::uuid, 'dashboard:exception:view', '异常中心查询', 'gis', '查询聚合后的库存、生产和设备异常', 0)
ON CONFLICT (permission_code) WHERE isdel = 0 DO NOTHING;

-- 演示角色只补授与其职责相关的权限；租户管理员获得全部新增权限。
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT r.id, p.id, 0
FROM auth_role r
JOIN auth_permission p ON p.permission_code IN (
    'mes:bom:view', 'mes:routing:view', 'mes:workorder:update',
    'mes:material:overage', 'dashboard:exception:view'
)
WHERE r.status = 'ACTIVE'
  AND r.isdel = 0
  AND p.isdel = 0
  AND (
      r.role_code = 'tenant.admin'
      OR (r.role_code = 'mes.inspector' AND p.permission_code IN (
          'mes:bom:view', 'mes:routing:view', 'mes:workorder:update', 'mes:material:overage'
      ))
      OR (r.role_code = 'warehouse.operator' AND p.permission_code IN ('mes:material:overage'))
      OR (r.role_code IN ('iot.engineer', 'sales.rep', 'purchase.agent')
          AND p.permission_code = 'dashboard:exception:view')
  )
ON CONFLICT DO NOTHING;
