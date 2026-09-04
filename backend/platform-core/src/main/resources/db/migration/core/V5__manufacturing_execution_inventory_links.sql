-- =============================================================================
-- Core 阶段 5 MES foundation 与执行链路增量结构。
--
-- foundation 的 BOM、Routing、WorkOrder 基础事实在本阶段正式落入 PostgreSQL；执行层只保存其生命周期
-- 与现场事实。跨领域销售来源和工序工作中心保持软引用，库存变化必须由 InventoryCommandService 产生，
-- MES 只保存返回的库存业务标识。
-- 兼容 PostgreSQL 12.1；数量统一使用 NUMERIC(19, 6)，isdel=1 表示逻辑删除。
-- =============================================================================

CREATE TABLE IF NOT EXISTS mes_bom (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    bom_code VARCHAR(64) NOT NULL,
    product_id UUID NOT NULL,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_mes_bom_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_mes_bom_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_bom_tenant_code_version_active
    ON mes_bom (tenant_id, bom_code, version) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_mes_bom_product_status
    ON mes_bom (tenant_id, product_id, status) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_bom_component (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    bom_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    component_product_id UUID NOT NULL,
    component_qty NUMERIC(19, 6) NOT NULL CHECK (component_qty > 0),
    uom VARCHAR(64) NOT NULL,
    scrap_rate NUMERIC(19, 6) CHECK (scrap_rate IS NULL OR scrap_rate >= 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_mes_bom_component_bom_tenant
        FOREIGN KEY (tenant_id, bom_id) REFERENCES mes_bom (tenant_id, id),
    CONSTRAINT fk_mes_bom_component_product_tenant
        FOREIGN KEY (tenant_id, component_product_id) REFERENCES md_product (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_bom_component_line_active
    ON mes_bom_component (tenant_id, bom_id, line_no) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_routing (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    routing_code VARCHAR(64) NOT NULL,
    product_id UUID NOT NULL,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'DISABLED')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_mes_routing_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_mes_routing_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_routing_tenant_code_version_active
    ON mes_routing (tenant_id, routing_code, version) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_mes_routing_product_status
    ON mes_routing (tenant_id, product_id, status) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_routing_operation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    routing_id UUID NOT NULL,
    operation_no INTEGER NOT NULL CHECK (operation_no > 0),
    operation_name VARCHAR(128) NOT NULL,
    work_center_id UUID NOT NULL,
    standard_time_minutes NUMERIC(19, 6)
        CHECK (standard_time_minutes IS NULL OR standard_time_minutes >= 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_mes_routing_operation_routing_tenant
        FOREIGN KEY (tenant_id, routing_id) REFERENCES mes_routing (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_routing_operation_no_active
    ON mes_routing_operation (tenant_id, routing_id, operation_no) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_work_order (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    work_order_no VARCHAR(64) NOT NULL,
    product_id UUID NOT NULL,
    planned_qty NUMERIC(19, 6) NOT NULL CHECK (planned_qty > 0),
    planned_start_time TIMESTAMPTZ NOT NULL,
    planned_finish_time TIMESTAMPTZ NOT NULL,
    bom_id UUID NOT NULL,
    bom_version VARCHAR(64) NOT NULL,
    routing_id UUID NOT NULL,
    routing_version VARCHAR(64) NOT NULL,
    source_sales_order_line_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'PendingApproval', 'Released', 'InProgress', 'Completed', 'Rejected')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_mes_work_order_planned_time
        CHECK (planned_finish_time > planned_start_time),
    CONSTRAINT uq_mes_work_order_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_mes_work_order_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id),
    CONSTRAINT fk_mes_work_order_bom_tenant
        FOREIGN KEY (tenant_id, bom_id) REFERENCES mes_bom (tenant_id, id),
    CONSTRAINT fk_mes_work_order_routing_tenant
        FOREIGN KEY (tenant_id, routing_id) REFERENCES mes_routing (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_work_order_tenant_no_active
    ON mes_work_order (tenant_id, work_order_no) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_mes_work_order_status_date
    ON mes_work_order (tenant_id, status, planned_finish_time) WHERE isdel = 0;

-- 工单执行生命周期快照与基础工单事实分表，避免用工单状态替代工序执行事实。
CREATE TABLE IF NOT EXISTS mes_work_order_lifecycle (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL
        CHECK (status IN ('Draft', 'PendingApproval', 'Released', 'InProgress', 'Completed', 'Rejected')),
    required_operation_ids JSONB NOT NULL,
    completed_operation_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    reported_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (reported_qty >= 0),
    qualified_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (qualified_qty >= 0),
    defect_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (defect_qty >= 0),
    received_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    quality_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    pending_inventory_commands BOOLEAN NOT NULL DEFAULT FALSE,
    locked_bom_version VARCHAR(64),
    locked_routing_version VARCHAR(64),
    submitted_by UUID,
    submitted_at TIMESTAMPTZ,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    rejection_reason VARCHAR(512),
    completion_type VARCHAR(16),
    completion_reason VARCHAR(512),
    completed_by UUID,
    completed_session_id VARCHAR(128),
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_mes_work_order_lifecycle_tenant_id UNIQUE (tenant_id, work_order_id),
    CONSTRAINT fk_mes_work_order_lifecycle_order_tenant
        FOREIGN KEY (tenant_id, work_order_id) REFERENCES mes_work_order (tenant_id, id),
    CONSTRAINT ck_mes_work_order_lifecycle_reported_qty
        CHECK (reported_qty = qualified_qty + defect_qty),
    CONSTRAINT ck_mes_work_order_lifecycle_completion_type
        CHECK (completion_type IS NULL OR completion_type IN ('Normal', 'Manual'))
);
CREATE INDEX IF NOT EXISTS ix_mes_work_order_lifecycle_status
    ON mes_work_order_lifecycle (tenant_id, status, updated_at DESC) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_dispatch_order (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    dispatch_no VARCHAR(64) NOT NULL,
    work_order_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    operator_id UUID NOT NULL,
    dispatch_qty NUMERIC(19, 6) NOT NULL CHECK (dispatch_qty > 0),
    device_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'Released', 'Processing', 'Completed')),
    released_by UUID,
    released_at TIMESTAMPTZ,
    processing_by UUID,
    processing_at TIMESTAMPTZ,
    completed_by UUID,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_dispatch_tenant_no_active
    ON mes_dispatch_order (tenant_id, dispatch_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_dispatch_tenant_id
    ON mes_dispatch_order (tenant_id, id);
CREATE INDEX IF NOT EXISTS ix_mes_dispatch_work_order_active
    ON mes_dispatch_order (tenant_id, work_order_id, status) WHERE isdel = 0;

-- 兼容同一未发布迁移的早期本地草稿结构；生产首次执行时该列已由建表定义，重复执行安全无副作用。
ALTER TABLE mes_dispatch_order ADD COLUMN IF NOT EXISTS completed_by UUID;

CREATE TABLE IF NOT EXISTS mes_operation_execution (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    execution_no VARCHAR(64) NOT NULL,
    dispatch_order_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    operator_id UUID NOT NULL,
    device_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'NotStarted'
        CHECK (status IN ('NotStarted', 'Running', 'Paused', 'Completed')),
    started_at TIMESTAMPTZ,
    paused_at TIMESTAMPTZ,
    resumed_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    pause_reason VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT uq_mes_operation_execution_context
        UNIQUE (tenant_id, id, work_order_id, operation_id),
    CONSTRAINT fk_mes_execution_dispatch_tenant
        FOREIGN KEY (tenant_id, dispatch_order_id) REFERENCES mes_dispatch_order (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_execution_tenant_no_active
    ON mes_operation_execution (tenant_id, execution_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_execution_tenant_id
    ON mes_operation_execution (tenant_id, id);
CREATE INDEX IF NOT EXISTS ix_mes_execution_work_order_active
    ON mes_operation_execution (tenant_id, work_order_id, status) WHERE isdel = 0;

-- 工序执行事件单独保存，避免用单行的最近时间字段丢失多次暂停/恢复的完整审计时间线。
CREATE TABLE IF NOT EXISTS mes_operation_execution_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    operation_execution_id UUID NOT NULL,
    event_seq INTEGER NOT NULL CHECK (event_seq > 0),
    event_type VARCHAR(16) NOT NULL
        CHECK (event_type IN ('STARTED', 'PAUSED', 'RESUMED', 'COMPLETED')),
    occurred_at TIMESTAMPTZ NOT NULL,
    operator_id UUID NOT NULL,
    reason VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mes_operation_event_seq UNIQUE (tenant_id, operation_execution_id, event_seq),
    CONSTRAINT fk_mes_operation_event_execution_tenant
        FOREIGN KEY (tenant_id, operation_execution_id) REFERENCES mes_operation_execution (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS ix_mes_operation_event_execution_time
    ON mes_operation_execution_event (tenant_id, operation_execution_id, occurred_at, event_seq);

CREATE TABLE IF NOT EXISTS mes_work_report (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    report_no VARCHAR(64) NOT NULL,
    operation_execution_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    report_time TIMESTAMPTZ NOT NULL,
    qualified_qty NUMERIC(19, 6) NOT NULL CHECK (qualified_qty >= 0),
    defect_qty NUMERIC(19, 6) NOT NULL CHECK (defect_qty >= 0),
    report_qty NUMERIC(19, 6) NOT NULL CHECK (report_qty > 0),
    remark VARCHAR(512),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_mes_work_report_quantity
        CHECK (report_qty = qualified_qty + defect_qty),
    CONSTRAINT fk_mes_work_report_execution_tenant
        FOREIGN KEY (tenant_id, operation_execution_id) REFERENCES mes_operation_execution (tenant_id, id),
    CONSTRAINT fk_mes_work_report_execution_context_tenant
        FOREIGN KEY (tenant_id, operation_execution_id, work_order_id, operation_id)
            REFERENCES mes_operation_execution (tenant_id, id, work_order_id, operation_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_work_report_tenant_no_active
    ON mes_work_report (tenant_id, report_no) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_mes_work_report_work_order_time
    ON mes_work_report (tenant_id, work_order_id, report_time DESC) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_quality_inspection (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    inspection_no VARCHAR(64) NOT NULL,
    work_report_id UUID NOT NULL,
    work_order_id UUID NOT NULL,
    operation_id UUID NOT NULL,
    inspection_type VARCHAR(64) NOT NULL,
    sample_qty NUMERIC(19, 6) NOT NULL CHECK (sample_qty > 0),
    qualified_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (qualified_qty >= 0),
    defect_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (defect_qty >= 0),
    result VARCHAR(32),
    status VARCHAR(32) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'Submitted', 'Passed', 'Failed')),
    submitted_by UUID,
    submitted_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_mes_quality_inspection_quantity
        CHECK (qualified_qty + defect_qty <= sample_qty),
    CONSTRAINT fk_mes_quality_inspection_report_tenant
        FOREIGN KEY (tenant_id, work_report_id) REFERENCES mes_work_report (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_quality_tenant_no_active
    ON mes_quality_inspection (tenant_id, inspection_no) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_mes_quality_work_order_active
    ON mes_quality_inspection (tenant_id, work_order_id, result) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_material_issue (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    issue_no VARCHAR(64) NOT NULL,
    work_order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'Confirmed')),
    idempotency_key VARCHAR(128),
    inventory_operation_id UUID,
    confirmed_by UUID,
    confirmed_session_id VARCHAR(128),
    confirmed_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_material_issue_tenant_no_active
    ON mes_material_issue (tenant_id, issue_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_material_issue_idempotency
    ON mes_material_issue (tenant_id, idempotency_key) WHERE isdel = 0 AND idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_material_issue_tenant_id
    ON mes_material_issue (tenant_id, id);

CREATE TABLE IF NOT EXISTS mes_material_issue_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    material_issue_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    product_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    issue_qty NUMERIC(19, 6) NOT NULL CHECK (issue_qty > 0),
    inventory_transaction_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_mes_material_issue_line_header_tenant
        FOREIGN KEY (tenant_id, material_issue_id) REFERENCES mes_material_issue (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_material_issue_line_no_active
    ON mes_material_issue_line (tenant_id, material_issue_id, line_no) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_material_return (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    return_no VARCHAR(64) NOT NULL,
    work_order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'Confirmed')),
    idempotency_key VARCHAR(128),
    inventory_operation_id UUID,
    confirmed_by UUID,
    confirmed_session_id VARCHAR(128),
    confirmed_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_material_return_tenant_no_active
    ON mes_material_return (tenant_id, return_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_material_return_idempotency
    ON mes_material_return (tenant_id, idempotency_key) WHERE isdel = 0 AND idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_material_return_tenant_id
    ON mes_material_return (tenant_id, id);

CREATE TABLE IF NOT EXISTS mes_material_return_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    material_return_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    product_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    return_qty NUMERIC(19, 6) NOT NULL CHECK (return_qty > 0),
    inventory_transaction_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_mes_material_return_line_header_tenant
        FOREIGN KEY (tenant_id, material_return_id) REFERENCES mes_material_return (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_material_return_line_no_active
    ON mes_material_return_line (tenant_id, material_return_id, line_no) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS mes_finished_goods_receipt (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    receipt_no VARCHAR(64) NOT NULL,
    work_order_id UUID NOT NULL,
    receipt_qty NUMERIC(19, 6) NOT NULL CHECK (receipt_qty > 0),
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'Confirmed')),
    idempotency_key VARCHAR(128),
    inventory_operation_id UUID,
    inventory_transaction_id UUID,
    confirmed_by UUID,
    confirmed_session_id VARCHAR(128),
    confirmed_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_finished_receipt_tenant_no_active
    ON mes_finished_goods_receipt (tenant_id, receipt_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_finished_receipt_idempotency
    ON mes_finished_goods_receipt (tenant_id, idempotency_key) WHERE isdel = 0 AND idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mes_finished_receipt_tenant_id
    ON mes_finished_goods_receipt (tenant_id, id);
