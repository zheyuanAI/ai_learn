-- =============================================================================
-- Core 阶段 4.1 销售履约只追加事实。
-- 订单行累计数量仍由 sales_order_line 保存；库存数量和库存流水只能由 InventoryCommandService 维护。
-- =============================================================================

CREATE TABLE IF NOT EXISTS sales_fulfillment_fact (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    sales_order_id UUID NOT NULL,
    sales_order_line_id UUID NOT NULL,
    action_type VARCHAR(32) NOT NULL CHECK (action_type IN ('PICK', 'PICK_RETURN', 'RESERVATION_RELEASE', 'SHIP')),
    operation_id UUID NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL CHECK (quantity > 0),
    from_location_id UUID,
    to_location_id UUID,
    reservation_id UUID,
    allocation_id UUID,
    idempotency_key VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_sales_fulfillment_fact_key
        UNIQUE (tenant_id, action_type, operation_id, sales_order_line_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS ix_sales_fulfillment_fact_order_time
    ON sales_fulfillment_fact (tenant_id, sales_order_id, occurred_at DESC);
