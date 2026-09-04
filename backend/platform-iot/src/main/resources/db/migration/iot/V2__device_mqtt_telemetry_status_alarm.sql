-- =============================================================================
-- IoT 阶段 2 设备基础与事实表
-- 只新增 IoT 自有表；不修改 V1，也不建立跨服务外键。
-- PostgreSQL 12.1 兼容：数量/阈值使用 NUMERIC(19,6)，时间使用 TIMESTAMPTZ。
-- =============================================================================

CREATE TABLE IF NOT EXISTS iot_idempotency_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    operation VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    claim_token UUID,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    response_body TEXT,
    error_message VARCHAR(1024),
    expires_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_iot_idempotency_tenant_operation_key_active
    ON iot_idempotency_record (tenant_id, operation, idempotency_key) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS iot_device_profile (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    profile_code VARCHAR(64) NOT NULL,
    profile_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    offline_timeout_seconds INTEGER NOT NULL DEFAULT 60 CHECK (offline_timeout_seconds BETWEEN 1 AND 86400),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_iot_profile_code UNIQUE (tenant_id, profile_code),
    CONSTRAINT uq_iot_profile_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_iot_profile_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE IF NOT EXISTS iot_device_profile_metric (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    profile_id UUID NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    metric_name VARCHAR(128) NOT NULL,
    value_type VARCHAR(16) NOT NULL CHECK (value_type IN ('NUMBER', 'BOOLEAN', 'TEXT')),
    unit VARCHAR(32),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_iot_profile_metric_code UNIQUE (tenant_id, profile_id, metric_code),
    CONSTRAINT fk_iot_metric_profile FOREIGN KEY (tenant_id, profile_id)
        REFERENCES iot_device_profile(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS iot_device (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    device_code VARCHAR(64) NOT NULL,
    device_name VARCHAR(128) NOT NULL,
    device_profile_id UUID NOT NULL,
    protocol_type VARCHAR(16) NOT NULL CHECK (protocol_type = 'MQTT'),
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'Active' CHECK (lifecycle_status IN ('Active', 'Disabled')),
    work_center_id UUID,
    area_id UUID,
    map_point_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_iot_device_code UNIQUE (tenant_id, device_code),
    CONSTRAINT uq_iot_device_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_iot_device_profile FOREIGN KEY (tenant_id, device_profile_id)
        REFERENCES iot_device_profile(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS iot_device_credential (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    device_id UUID NOT NULL,
    credential_reference VARCHAR(96) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    secret_salt VARCHAR(96) NOT NULL,
    credential_status VARCHAR(24) NOT NULL DEFAULT 'Active'
        CHECK (credential_status IN ('PendingProvision', 'Active', 'ProvisionFailed', 'Revoked')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_by UUID,
    revoked_at TIMESTAMPTZ,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_iot_credential_reference UNIQUE (tenant_id, credential_reference),
    CONSTRAINT fk_iot_credential_device FOREIGN KEY (tenant_id, device_id)
        REFERENCES iot_device(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS iot_message_dedup (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    device_id UUID NOT NULL,
    -- UUID + message_id(128) + 类型前缀最长超过 160，必须留足消息键空间。
    message_key VARCHAR(256) NOT NULL,
    message_id VARCHAR(128),
    sequence_no BIGINT,
    payload_hash CHAR(64) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_iot_message_dedup UNIQUE (tenant_id, device_id, message_key),
    CONSTRAINT fk_iot_dedup_device FOREIGN KEY (tenant_id, device_id)
        REFERENCES iot_device(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS iot_device_telemetry (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    device_id UUID NOT NULL,
    message_key VARCHAR(256) NOT NULL,
    message_id VARCHAR(128),
    sequence_no BIGINT,
    ts TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    metric_value TEXT NOT NULL,
    metric_unit VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iot_telemetry_device FOREIGN KEY (tenant_id, device_id)
        REFERENCES iot_device(tenant_id, id),
    CONSTRAINT uq_iot_telemetry_metric UNIQUE (tenant_id, device_id, message_key, metric_code)
);

CREATE TABLE IF NOT EXISTS iot_device_status (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    device_id UUID NOT NULL,
    online_status VARCHAR(16) NOT NULL DEFAULT 'Offline',
    running_status VARCHAR(16) NOT NULL DEFAULT 'Idle',
    alarm_status VARCHAR(16) NOT NULL DEFAULT 'Normal',
    last_seen_at TIMESTAMPTZ,
    last_message_key VARCHAR(256),
    -- last_seen_at 是平台接收时间；单独保存设备采集时间，才能拒绝迟到消息回写状态。
    last_source_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_iot_device_status UNIQUE (tenant_id, device_id),
    CONSTRAINT fk_iot_status_device FOREIGN KEY (tenant_id, device_id)
        REFERENCES iot_device(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS iot_device_alarm_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    device_profile_id UUID NOT NULL,
    device_id UUID,
    metric_code VARCHAR(64) NOT NULL,
    operator VARCHAR(8) NOT NULL CHECK (operator IN ('GT', 'GTE', 'LT', 'LTE', 'EQ')),
    trigger_threshold NUMERIC(19,6) NOT NULL,
    recovery_threshold NUMERIC(19,6) NOT NULL,
    alarm_level VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_iot_alarm_rule_code UNIQUE (tenant_id, rule_code),
    CONSTRAINT uq_iot_alarm_rule_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_iot_alarm_rule_profile FOREIGN KEY (tenant_id, device_profile_id)
        REFERENCES iot_device_profile(tenant_id, id),
    CONSTRAINT fk_iot_alarm_rule_device FOREIGN KEY (tenant_id, device_id)
        REFERENCES iot_device(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS iot_device_alarm (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    alarm_no VARCHAR(64) NOT NULL,
    device_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    alarm_type VARCHAR(64) NOT NULL,
    alarm_level VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('Triggered', 'Acked', 'RecoveredUnacked', 'Recovered')),
    triggered_at TIMESTAMPTZ NOT NULL,
    acked_at TIMESTAMPTZ,
    ack_user_id UUID,
    ack_comment VARCHAR(512),
    recovered_at TIMESTAMPTZ,
    operation_execution_id UUID,
    work_order_id UUID,
    context_source VARCHAR(16),
    context_status VARCHAR(16) NOT NULL DEFAULT 'Pending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT uq_iot_alarm_no UNIQUE (tenant_id, alarm_no),
    CONSTRAINT uq_iot_alarm_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_iot_alarm_device FOREIGN KEY (tenant_id, device_id)
        REFERENCES iot_device(tenant_id, id),
    CONSTRAINT fk_iot_alarm_rule FOREIGN KEY (tenant_id, rule_id)
        REFERENCES iot_device_alarm_rule(tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_iot_alarm_active_rule
    ON iot_device_alarm (tenant_id, device_id, rule_id)
    WHERE status IN ('Triggered', 'Acked', 'RecoveredUnacked');

CREATE TABLE IF NOT EXISTS iot_alarm_context_task (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    alarm_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'Pending',
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_iot_context_alarm FOREIGN KEY (tenant_id, alarm_id)
        REFERENCES iot_device_alarm(tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_iot_context_task_open_alarm
    ON iot_alarm_context_task (tenant_id, alarm_id)
    WHERE status <> 'Completed';

CREATE INDEX IF NOT EXISTS idx_iot_profile_tenant ON iot_device_profile (tenant_id, isdel, profile_code);
CREATE INDEX IF NOT EXISTS idx_iot_device_tenant ON iot_device (tenant_id, isdel, device_code);
CREATE INDEX IF NOT EXISTS idx_iot_telemetry_device_time ON iot_device_telemetry (tenant_id, device_id, ts DESC);
CREATE INDEX IF NOT EXISTS idx_iot_alarm_device_time ON iot_device_alarm (tenant_id, device_id, triggered_at DESC);

-- 设备是稳定业务身份；历史事实设备只能走生命周期停用，禁止任何物理 DELETE。
CREATE OR REPLACE FUNCTION iot_prevent_device_physical_delete()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM iot_device_telemetry
               WHERE tenant_id = OLD.tenant_id AND device_id = OLD.id)
       OR EXISTS (SELECT 1 FROM iot_device_alarm
                  WHERE tenant_id = OLD.tenant_id AND device_id = OLD.id) THEN
        RAISE EXCEPTION 'IoT historical device facts cannot be physically deleted';
    END IF;
    RAISE EXCEPTION 'IoT devices are lifecycle-managed and cannot be physically deleted';
END;
$$;

DROP TRIGGER IF EXISTS trg_iot_device_no_physical_delete ON iot_device;
CREATE TRIGGER trg_iot_device_no_physical_delete
    BEFORE DELETE ON iot_device
    FOR EACH ROW
    EXECUTE FUNCTION iot_prevent_device_physical_delete();
