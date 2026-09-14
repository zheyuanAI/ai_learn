-- =============================================================================
-- Core AI-01B：Core 服务结构化业务操作审计。
-- 业务事实仍以各领域表为准；本表只保存操作者、请求、动作、结果和状态变化摘要。
-- =============================================================================

CREATE TABLE IF NOT EXISTS core_operation_audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM', 'DEVICE')),
    actor_id UUID,
    actor_account VARCHAR(128),
    session_id VARCHAR(128),
    request_id VARCHAR(128) NOT NULL,
    action_code VARCHAR(128) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id UUID NOT NULL,
    entity_no VARCHAR(128),
    before_status VARCHAR(64),
    after_status VARCHAR(64),
    operation_summary TEXT NOT NULL DEFAULT '',
    changed_fields_summary TEXT NOT NULL DEFAULT '',
    result VARCHAR(16) NOT NULL CHECK (result IN ('SUCCESS', 'FAILED', 'DENIED')),
    error_code VARCHAR(64),
    error_reason VARCHAR(1000),
    idempotency_key_hash VARCHAR(64),
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

CREATE INDEX IF NOT EXISTS ix_core_operation_audit_entity
    ON core_operation_audit_log (tenant_id, entity_type, entity_id, occurred_at DESC)
    WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_core_operation_audit_actor
    ON core_operation_audit_log (tenant_id, actor_id, occurred_at DESC)
    WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_core_operation_audit_request
    ON core_operation_audit_log (tenant_id, request_id, occurred_at DESC)
    WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_core_operation_audit_idempotency
    ON core_operation_audit_log (tenant_id, action_code, idempotency_key_hash)
    WHERE isdel = 0 AND idempotency_key_hash IS NOT NULL;
