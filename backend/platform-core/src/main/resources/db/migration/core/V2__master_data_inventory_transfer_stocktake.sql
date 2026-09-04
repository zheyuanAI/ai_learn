-- =============================================================================
-- Core 阶段 2 主数据、库存、调拨与盘点表
-- 约束：PostgreSQL 12.1；所有业务事实按租户隔离；isdel=1 仅表示逻辑删除。
-- =============================================================================

CREATE TABLE IF NOT EXISTS md_uom (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    symbol VARCHAR(32),
    decimal_scale SMALLINT NOT NULL DEFAULT 0 CHECK (decimal_scale BETWEEN 0 AND 6),
    remark VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_uom_tenant_code_active
    ON md_uom (tenant_id, code) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS md_product (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sku VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    spec VARCHAR(256),
    uom VARCHAR(64) NOT NULL,
    category VARCHAR(128),
    batch_managed BOOLEAN NOT NULL DEFAULT FALSE,
    unit_price NUMERIC(19, 6),
    min_stock NUMERIC(19, 6),
    max_stock NUMERIC(19, 6),
    safety_stock NUMERIC(19, 6),
    remark VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_md_product_stock_nonnegative CHECK (
        (unit_price IS NULL OR unit_price >= 0)
        AND (min_stock IS NULL OR min_stock >= 0)
        AND (max_stock IS NULL OR max_stock >= 0)
        AND (safety_stock IS NULL OR safety_stock >= 0)
    )
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_product_tenant_sku_active
    ON md_product (tenant_id, sku) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_product_tenant_id
    ON md_product (tenant_id, id);

CREATE TABLE IF NOT EXISTS md_customer (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_code VARCHAR(64) NOT NULL,
    customer_name VARCHAR(128) NOT NULL,
    contact_person VARCHAR(64),
    contact_phone VARCHAR(32),
    shipping_address VARCHAR(512),
    remark VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_customer_tenant_code_active
    ON md_customer (tenant_id, customer_code) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS md_supplier (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    supplier_code VARCHAR(64) NOT NULL,
    supplier_name VARCHAR(128) NOT NULL,
    contact_person VARCHAR(64),
    contact_phone VARCHAR(32),
    address VARCHAR(512),
    remark VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_supplier_tenant_code_active
    ON md_supplier (tenant_id, supplier_code) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS md_warehouse (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(64),
    manager VARCHAR(64),
    contact VARCHAR(32),
    address VARCHAR(512),
    remark VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_warehouse_tenant_code_active
    ON md_warehouse (tenant_id, code) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_warehouse_tenant_id
    ON md_warehouse (tenant_id, id);

CREATE TABLE IF NOT EXISTS md_location (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (
        type IN ('QualityHold', 'ReceivingStaging', 'Storage', 'Picking', 'ShippingStaging', 'Adjustment')
    ),
    capacity NUMERIC(19, 6),
    description VARCHAR(512),
    remark VARCHAR(512),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_md_location_capacity_nonnegative CHECK (capacity IS NULL OR capacity >= 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_location_tenant_warehouse_code_active
    ON md_location (tenant_id, warehouse_id, code) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_md_location_tenant_id
    ON md_location (tenant_id, id);
ALTER TABLE md_location
    ADD CONSTRAINT fk_md_location_warehouse_tenant
    FOREIGN KEY (tenant_id, warehouse_id) REFERENCES md_warehouse (tenant_id, id);

-- 库存余额是可变快照；企业事实总量由 on_hand_qty 表达，预留不得超过实物。
CREATE TABLE IF NOT EXISTS inv_inventory_balance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    lot_no VARCHAR(128) NOT NULL DEFAULT '',
    on_hand_qty NUMERIC(19, 6) NOT NULL DEFAULT 0,
    reserved_qty NUMERIC(19, 6) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    last_transaction_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_inv_balance_nonnegative CHECK (
        on_hand_qty >= 0 AND reserved_qty >= 0 AND reserved_qty <= on_hand_qty
    ),
    CONSTRAINT fk_inv_balance_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id),
    CONSTRAINT fk_inv_balance_warehouse_tenant
        FOREIGN KEY (tenant_id, warehouse_id) REFERENCES md_warehouse (tenant_id, id),
    CONSTRAINT fk_inv_balance_location_tenant
        FOREIGN KEY (tenant_id, location_id) REFERENCES md_location (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_balance_tenant_dimension_active
    ON inv_inventory_balance (tenant_id, product_id, warehouse_id, location_id, lot_no) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS inv_inventory_reservation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    reservation_no VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id UUID NOT NULL,
    source_line_id UUID,
    reserved_qty NUMERIC(19, 6) NOT NULL CHECK (reserved_qty > 0),
    released_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (released_qty >= 0),
    status VARCHAR(32) NOT NULL DEFAULT 'Active',
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_inv_reservation_released_not_over CHECK (released_qty <= reserved_qty)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_reservation_tenant_no_active
    ON inv_inventory_reservation (tenant_id, reservation_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_reservation_tenant_id
    ON inv_inventory_reservation (tenant_id, id);

CREATE TABLE IF NOT EXISTS inv_inventory_reservation_allocation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    reservation_id UUID NOT NULL,
    product_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    lot_no VARCHAR(128) NOT NULL DEFAULT '',
    allocated_qty NUMERIC(19, 6) NOT NULL CHECK (allocated_qty > 0),
    released_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (released_qty >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_inv_allocation_released_not_over CHECK (released_qty <= allocated_qty)
    ,CONSTRAINT fk_inv_allocation_reservation_tenant
        FOREIGN KEY (tenant_id, reservation_id) REFERENCES inv_inventory_reservation (tenant_id, id),
    CONSTRAINT fk_inv_allocation_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id),
    CONSTRAINT fk_inv_allocation_warehouse_tenant
        FOREIGN KEY (tenant_id, warehouse_id) REFERENCES md_warehouse (tenant_id, id),
    CONSTRAINT fk_inv_allocation_location_tenant
        FOREIGN KEY (tenant_id, location_id) REFERENCES md_location (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_allocation_tenant_dimension_active
    ON inv_inventory_reservation_allocation
        (tenant_id, reservation_id, product_id, warehouse_id, location_id, lot_no)
        WHERE isdel = 0 AND allocated_qty > released_qty;

-- 库存流水只追加，位置移动同时保存来源与目标维度。
CREATE TABLE IF NOT EXISTS inv_inventory_transaction (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transaction_no VARCHAR(64) NOT NULL,
    transaction_type VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id UUID NOT NULL,
    source_line_id UUID,
    from_product_id UUID,
    from_warehouse_id UUID,
    from_location_id UUID,
    from_lot_no VARCHAR(128) NOT NULL DEFAULT '',
    to_product_id UUID,
    to_warehouse_id UUID,
    to_location_id UUID,
    to_lot_no VARCHAR(128) NOT NULL DEFAULT '',
    quantity NUMERIC(19, 6) NOT NULL CHECK (quantity > 0),
    occurred_at TIMESTAMPTZ NOT NULL,
    operator_id UUID NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(128) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_transaction_tenant_no_active
    ON inv_inventory_transaction (tenant_id, transaction_no) WHERE isdel = 0;
-- 幂等窗口由 core_idempotency_record 负责；流水保留历史，不能用永久唯一键阻止 TTL 到期后的合法新命令。

CREATE TABLE IF NOT EXISTS inv_transfer_order (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transfer_no VARCHAR(64) NOT NULL,
    from_warehouse_id UUID NOT NULL,
    from_location_id UUID NOT NULL,
    to_warehouse_id UUID NOT NULL,
    to_location_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'Draft',
    version BIGINT NOT NULL DEFAULT 0,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_inv_transfer_different_location CHECK (from_location_id <> to_location_id)
    ,CONSTRAINT fk_inv_transfer_from_warehouse_tenant
        FOREIGN KEY (tenant_id, from_warehouse_id) REFERENCES md_warehouse (tenant_id, id)
    ,CONSTRAINT fk_inv_transfer_to_warehouse_tenant
        FOREIGN KEY (tenant_id, to_warehouse_id) REFERENCES md_warehouse (tenant_id, id)
    ,CONSTRAINT fk_inv_transfer_from_location_tenant
        FOREIGN KEY (tenant_id, from_location_id) REFERENCES md_location (tenant_id, id)
    ,CONSTRAINT fk_inv_transfer_to_location_tenant
        FOREIGN KEY (tenant_id, to_location_id) REFERENCES md_location (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_transfer_tenant_no_active
    ON inv_transfer_order (tenant_id, transfer_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_transfer_tenant_id
    ON inv_transfer_order (tenant_id, id);

CREATE TABLE IF NOT EXISTS inv_transfer_order_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    transfer_order_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    product_id UUID NOT NULL,
    lot_no VARCHAR(128) NOT NULL DEFAULT '',
    uom VARCHAR(64) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL CHECK (quantity > 0),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_inv_transfer_line_order_tenant
        FOREIGN KEY (tenant_id, transfer_order_id) REFERENCES inv_transfer_order (tenant_id, id),
    CONSTRAINT fk_inv_transfer_line_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_transfer_line_order_no_active
    ON inv_transfer_order_line (tenant_id, transfer_order_id, line_no) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS inv_stocktake_order (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    stocktake_no VARCHAR(64) NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID,
    status VARCHAR(32) NOT NULL DEFAULT 'NotStarted',
    version BIGINT NOT NULL DEFAULT 0,
    started_by UUID,
    started_at TIMESTAMPTZ,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT fk_inv_stocktake_warehouse_tenant
        FOREIGN KEY (tenant_id, warehouse_id) REFERENCES md_warehouse (tenant_id, id),
    CONSTRAINT fk_inv_stocktake_location_tenant
        FOREIGN KEY (tenant_id, location_id) REFERENCES md_location (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_stocktake_tenant_no_active
    ON inv_stocktake_order (tenant_id, stocktake_no) WHERE isdel = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_stocktake_tenant_id
    ON inv_stocktake_order (tenant_id, id);

CREATE TABLE IF NOT EXISTS inv_stocktake_order_line (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    stocktake_order_id UUID NOT NULL,
    line_no INTEGER NOT NULL CHECK (line_no > 0),
    product_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    location_id UUID NOT NULL,
    lot_no VARCHAR(128) NOT NULL DEFAULT '',
    system_qty NUMERIC(19, 6) NOT NULL DEFAULT 0 CHECK (system_qty >= 0),
    system_balance_version BIGINT NOT NULL DEFAULT 0,
    counted_qty NUMERIC(19, 6),
    variance_reason VARCHAR(512),
    adjustment_transaction_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1)),
    CONSTRAINT ck_inv_stocktake_counted_nonnegative CHECK (counted_qty IS NULL OR counted_qty >= 0),
    CONSTRAINT fk_inv_stocktake_line_order_tenant
        FOREIGN KEY (tenant_id, stocktake_order_id) REFERENCES inv_stocktake_order (tenant_id, id),
    CONSTRAINT fk_inv_stocktake_line_product_tenant
        FOREIGN KEY (tenant_id, product_id) REFERENCES md_product (tenant_id, id),
    CONSTRAINT fk_inv_stocktake_line_warehouse_tenant
        FOREIGN KEY (tenant_id, warehouse_id) REFERENCES md_warehouse (tenant_id, id),
    CONSTRAINT fk_inv_stocktake_line_location_tenant
        FOREIGN KEY (tenant_id, location_id) REFERENCES md_location (tenant_id, id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_inv_stocktake_line_order_no_active
    ON inv_stocktake_order_line (tenant_id, stocktake_order_id, line_no) WHERE isdel = 0;

CREATE TABLE IF NOT EXISTS core_idempotency_record (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    operation VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    claim_token UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_body TEXT,
    error_message VARCHAR(1024),
    expires_at TIMESTAMPTZ NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    isdel SMALLINT NOT NULL DEFAULT 0 CHECK (isdel IN (0, 1))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_core_idempotency_tenant_key_active
    ON core_idempotency_record (tenant_id, idempotency_key) WHERE isdel = 0;
