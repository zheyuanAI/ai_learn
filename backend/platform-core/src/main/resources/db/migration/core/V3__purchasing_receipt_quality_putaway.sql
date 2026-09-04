-- =============================================================================
-- Core 阶段 3 采购订单、到货验收、质量处置与上架事实表
-- 约束：PostgreSQL 12.1；所有事实按 tenant_id 隔离；isdel=1 仅表示逻辑删除。
-- Task 10 仅启用采购订单和到货验收写入；质量/上架表为同一迁移中的后续 S3 结构。
-- =============================================================================

CREATE TABLE IF NOT EXISTS purchase_order (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    po_no VARCHAR(64) NOT NULL,
    supplier_id UUID NOT NULL,
    expected_arrival_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'Submitted', 'Approved', 'PartiallyReceived', 'Completed')),
    completion_type VARCHAR(32)
        CHECK (completion_type IS NULL OR completion_type IN ('Normal', 'Manual')),
    completion_reason VARCHAR(512),
    completed_by UUID,
    completed_session_id VARCHAR(128),
    completed_at TIMESTAMPTZ,
    remark VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_purchase_order_completion_audit CHECK (
        (completion_type IS NULL AND completion_reason IS NULL AND completed_by IS NULL
            AND completed_session_id IS NULL AND completed_at IS NULL)
        OR (completion_type IS NOT NULL AND completed_by IS NOT NULL AND completed_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_order_tenant_no_active
    ON purchase_order (tenant_id, po_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_order_tenant_id
    ON purchase_order (tenant_id, id);

CREATE TABLE IF NOT EXISTS purchase_order_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    purchase_order_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    product_id UUID NOT NULL,
    uom VARCHAR(64) NOT NULL,
    ordered_qty NUMERIC(19, 6) NOT NULL CHECK (ordered_qty > 0),
    received_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    target_warehouse_id UUID NOT NULL,
    source_work_order_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_purchase_order_line_received_not_over CHECK (received_qty <= ordered_qty),
    CONSTRAINT fk_purchase_order_line_order_tenant
        FOREIGN KEY (tenant_id, purchase_order_id) REFERENCES purchase_order (tenant_id, id),
    CONSTRAINT fk_purchase_order_line_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id),
    CONSTRAINT fk_purchase_order_line_warehouse_tenant
        FOREIGN KEY (tenant_id, target_warehouse_id) REFERENCES md_warehouse (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_order_line_order_no_active
    ON purchase_order_line (tenant_id, purchase_order_id, line_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_order_line_tenant_id
    ON purchase_order_line (tenant_id, id);

CREATE TABLE IF NOT EXISTS purchase_receipt (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    receipt_no VARCHAR(64) NOT NULL,
    purchase_order_id UUID NOT NULL,
    receipt_time TIMESTAMPTZ NOT NULL,
    quality_hold_location_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft' CHECK (status IN ('Draft', 'Confirmed')),
    confirmed_by UUID,
    confirmed_session_id VARCHAR(128),
    confirmed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_purchase_receipt_order_tenant
        FOREIGN KEY (tenant_id, purchase_order_id) REFERENCES purchase_order (tenant_id, id),
    CONSTRAINT fk_purchase_receipt_quality_location_tenant
        FOREIGN KEY (tenant_id, quality_hold_location_id) REFERENCES md_location (tenant_id, id),
    CONSTRAINT ck_purchase_receipt_confirmation_audit CHECK (
        (status = 'Draft' AND confirmed_by IS NULL AND confirmed_session_id IS NULL AND confirmed_at IS NULL)
        OR (status = 'Confirmed' AND confirmed_by IS NOT NULL
            AND NULLIF(BTRIM(confirmed_session_id), '') IS NOT NULL AND confirmed_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_receipt_tenant_no_active
    ON purchase_receipt (tenant_id, receipt_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_receipt_tenant_id
    ON purchase_receipt (tenant_id, id);

CREATE TABLE IF NOT EXISTS purchase_receipt_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    purchase_receipt_id UUID NOT NULL,
    purchase_order_line_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    product_id UUID NOT NULL,
    uom VARCHAR(64) NOT NULL,
    arrived_qty NUMERIC(19, 6) NOT NULL CHECK (arrived_qty > 0),
    rejected_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (rejected_qty >= 0),
    received_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (received_qty >= 0),
    lot_no VARCHAR(128) NOT NULL DEFAULT '',
    rejection_reason VARCHAR(512),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_purchase_receipt_line_quantity_relation
        CHECK (arrived_qty = rejected_qty + received_qty),
    CONSTRAINT ck_purchase_receipt_line_rejection_reason
        CHECK (rejected_qty = 0 OR NULLIF(BTRIM(rejection_reason), '') IS NOT NULL),
    CONSTRAINT fk_purchase_receipt_line_receipt_tenant
        FOREIGN KEY (tenant_id, purchase_receipt_id) REFERENCES purchase_receipt (tenant_id, id),
    CONSTRAINT fk_purchase_receipt_line_order_line_tenant
        FOREIGN KEY (tenant_id, purchase_order_line_id) REFERENCES purchase_order_line (tenant_id, id),
    CONSTRAINT fk_purchase_receipt_line_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_receipt_line_receipt_order_line_active
    ON purchase_receipt_line (tenant_id, purchase_receipt_id, purchase_order_line_id) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_receipt_line_tenant_id
    ON purchase_receipt_line (tenant_id, id);

-- 以下三张表由 Task 11 的质量/上架应用服务使用；本次不开放对应写接口。
CREATE TABLE IF NOT EXISTS purchase_quality_inspection (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    purchase_receipt_id UUID NOT NULL,
    purchase_receipt_line_id UUID NOT NULL,
    product_id UUID NOT NULL,
    inspected_qty NUMERIC(19, 6) NOT NULL CHECK (inspected_qty > 0),
    qualified_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (qualified_qty >= 0),
    unqualified_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (unqualified_qty >= 0),
    inspection_note VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'PendingDecision'
        CHECK (status IN ('PendingDecision', 'Completed')),
    inspected_by UUID NOT NULL,
    inspected_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_purchase_quality_inspection_quantity_relation
        CHECK (inspected_qty = qualified_qty + unqualified_qty),
    CONSTRAINT fk_purchase_quality_inspection_receipt_tenant
        FOREIGN KEY (tenant_id, purchase_receipt_id) REFERENCES purchase_receipt (tenant_id, id),
    CONSTRAINT fk_purchase_quality_inspection_receipt_line_tenant
        FOREIGN KEY (tenant_id, purchase_receipt_line_id) REFERENCES purchase_receipt_line (tenant_id, id),
    CONSTRAINT fk_purchase_quality_inspection_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS ix_purchase_quality_inspection_line
    ON purchase_quality_inspection (tenant_id, purchase_receipt_line_id) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_quality_inspection_tenant_id
    ON purchase_quality_inspection (tenant_id, id);

CREATE TABLE IF NOT EXISTS purchase_quality_disposition (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    purchase_quality_inspection_id UUID NOT NULL,
    disposition_type VARCHAR(32) NOT NULL CHECK (disposition_type IN ('Release', 'Return', 'Scrap')),
    disposition_qty NUMERIC(19, 6) NOT NULL CHECK (disposition_qty > 0),
    reason VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'PendingDecision'
        CHECK (status IN ('PendingDecision', 'PendingExecution', 'Completed')),
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    executed_by UUID,
    executed_at TIMESTAMPTZ,
    inventory_transaction_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_purchase_quality_disposition_inspection_tenant
        FOREIGN KEY (tenant_id, purchase_quality_inspection_id)
        REFERENCES purchase_quality_inspection (tenant_id, id)
);
CREATE INDEX IF NOT EXISTS ix_purchase_quality_disposition_inspection
    ON purchase_quality_disposition (tenant_id, purchase_quality_inspection_id) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS putaway_task (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    task_no VARCHAR(64) NOT NULL,
    purchase_receipt_id UUID NOT NULL,
    purchase_receipt_line_id UUID NOT NULL,
    product_id UUID NOT NULL,
    from_location_id UUID NOT NULL,
    to_location_id UUID NOT NULL,
    putaway_qty NUMERIC(19, 6) NOT NULL CHECK (putaway_qty > 0),
    status VARCHAR(32) NOT NULL DEFAULT 'Pending'
        CHECK (status IN ('Pending', 'Processing', 'Confirmed')),
    inventory_transaction_id UUID,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_putaway_different_location CHECK (from_location_id <> to_location_id),
    CONSTRAINT fk_putaway_receipt_tenant
        FOREIGN KEY (tenant_id, purchase_receipt_id) REFERENCES purchase_receipt (tenant_id, id),
    CONSTRAINT fk_putaway_receipt_line_tenant
        FOREIGN KEY (tenant_id, purchase_receipt_line_id) REFERENCES purchase_receipt_line (tenant_id, id),
    CONSTRAINT fk_putaway_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id),
    CONSTRAINT fk_putaway_from_location_tenant
        FOREIGN KEY (tenant_id, from_location_id) REFERENCES md_location (tenant_id, id),
    CONSTRAINT fk_putaway_to_location_tenant
        FOREIGN KEY (tenant_id, to_location_id) REFERENCES md_location (tenant_id, id)
);
-- 兼容同一未发布迁移的早期本地草稿结构；上架确认后回写库存流水标识。
ALTER TABLE putaway_task ADD COLUMN IF NOT EXISTS inventory_transaction_id UUID;
CREATE UNIQUE INDEX IF NOT EXISTS uq_putaway_task_tenant_no_active
    ON putaway_task (tenant_id, task_no) WHERE isdel = 0;
