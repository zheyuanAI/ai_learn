-- =============================================================================
-- Core 阶段 4 销售订单基础。
-- 约束：销售订单只保存生命周期和订单行事实；履约进度由应用层派生。
-- 直接拣货、预留、退回、释放和发货事实由 Task 13 在同一销售聚合上扩展。
-- =============================================================================

CREATE TABLE IF NOT EXISTS sales_order (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    so_no VARCHAR(64) NOT NULL,
    customer_id UUID NOT NULL,
    planned_ship_date DATE,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft'
        CHECK (status IN ('Draft', 'Submitted', 'Approved', 'Completed')),
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
    CONSTRAINT ck_sales_order_manual_completion_reason CHECK (
        completion_type <> 'Manual'
        OR (completion_reason IS NOT NULL AND length(btrim(completion_reason)) > 0
            AND completed_by IS NOT NULL AND completed_session_id IS NOT NULL AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_sales_order_completion_state CHECK (
        (status = 'Completed' AND completion_type IS NOT NULL)
        OR (status <> 'Completed' AND completion_type IS NULL
            AND completion_reason IS NULL AND completed_by IS NULL
            AND completed_session_id IS NULL AND completed_at IS NULL)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_order_tenant_no_active
    ON sales_order (tenant_id, so_no) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_sales_order_tenant_status_date
    ON sales_order (tenant_id, status, planned_ship_date) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS sales_order_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sales_order_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    product_id UUID NOT NULL,
    uom VARCHAR(64) NOT NULL,
    ordered_qty NUMERIC(19, 6) NOT NULL CHECK (ordered_qty > 0),
    reserved_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (reserved_qty >= 0),
    picked_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (picked_qty >= 0),
    shipped_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (shipped_qty >= 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_sales_order_line_quantity_chain CHECK (
        shipped_qty <= picked_qty AND picked_qty <= reserved_qty AND reserved_qty <= ordered_qty
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_order_line_tenant_order_no_active
    ON sales_order_line (tenant_id, sales_order_id, line_no) WHERE isdel = 0;
CREATE INDEX IF NOT EXISTS ix_sales_order_line_tenant_order_active
    ON sales_order_line (tenant_id, sales_order_id) WHERE isdel = 0;
