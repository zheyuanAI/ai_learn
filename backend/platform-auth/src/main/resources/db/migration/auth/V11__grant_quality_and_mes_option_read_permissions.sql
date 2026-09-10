-- V11：补齐质检/收货与 MES 派工页面所需的只读目录权限。
-- 仓库与生产角色需要读取采购订单目录，质检页才能把真实采购单作为可选来源。
INSERT INTO auth_role_permission (role_id, permission_id, isdel)
SELECT r.id, p.id, 0
FROM auth_role r
JOIN auth_permission p
  ON (
      (r.role_code IN ('warehouse.operator', 'mes.inspector')
          AND p.permission_code = 'pur:order:view')
      OR (r.role_code = 'mes.inspector'
          AND p.permission_code = 'iot:device:view')
  )
WHERE r.status = 'ACTIVE'
  AND r.isdel = 0
  AND p.isdel = 0
ON CONFLICT DO NOTHING;
