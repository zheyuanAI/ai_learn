-- 领料超出冻结 BOM 预计用量时保留业务原因；权限由 auth:mes:material:overage 控制。
ALTER TABLE mes_material_issue
    ADD COLUMN IF NOT EXISTS overage_reason VARCHAR(512);
