-- =============================================================================
-- Core S8：AI 只读助手会话、消息与受控工具调用审计。
-- 本迁移只保存 AI 交互与治理事实，不新增任何业务写入口。
-- =============================================================================

CREATE TABLE IF NOT EXISTS ai_chat_session (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    session_no VARCHAR(64) NOT NULL,
    user_id UUID NOT NULL,
    question_count INTEGER NOT NULL DEFAULT 0 CHECK (question_count >= 0),
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_ai_chat_session_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_chat_session_no
    ON ai_chat_session (tenant_id, session_no) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_ai_chat_session_user
    ON ai_chat_session (tenant_id, user_id, last_message_at DESC) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL CHECK (role IN ('User', 'Assistant')),
    content TEXT NOT NULL,
    source_summary TEXT NOT NULL DEFAULT '',
    time_range_summary VARCHAR(512) NOT NULL DEFAULT '',
    tool_call_summary TEXT NOT NULL DEFAULT '',
    navigation_action_summary TEXT NOT NULL DEFAULT '',
    model_id VARCHAR(128),
    status VARCHAR(16) NOT NULL CHECK (status IN ('Streaming', 'Completed', 'Interrupted', 'Failed')),
    input_tokens INTEGER CHECK (input_tokens IS NULL OR input_tokens >= 0),
    output_tokens INTEGER CHECK (output_tokens IS NULL OR output_tokens >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_ai_chat_message_session
        FOREIGN KEY (tenant_id, session_id) REFERENCES ai_chat_session (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_ai_chat_message_session
    ON ai_chat_message (tenant_id, session_id, created_at, id) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS ai_tool_audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    session_id UUID,
    request_id VARCHAR(128) NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    input_summary TEXT NOT NULL DEFAULT '',
    output_summary TEXT NOT NULL DEFAULT '',
    source_summary TEXT NOT NULL DEFAULT '',
    time_range_summary VARCHAR(512) NOT NULL DEFAULT '',
    tool_call_summary TEXT NOT NULL DEFAULT '',
    model_id VARCHAR(128),
    duration_ms BIGINT NOT NULL DEFAULT 0 CHECK (duration_ms >= 0),
    status VARCHAR(16) NOT NULL CHECK (status IN ('Success', 'Failed', 'Timeout', 'Denied')),
    error_code VARCHAR(64),
    error_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_ai_tool_audit_session
        FOREIGN KEY (tenant_id, session_id) REFERENCES ai_chat_session (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_ai_tool_audit_request
    ON ai_tool_audit_log (tenant_id, request_id, created_at DESC) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_ai_tool_audit_query
    ON ai_tool_audit_log (tenant_id, user_id, tool_name, status, created_at DESC) WHERE isdel = 0;
