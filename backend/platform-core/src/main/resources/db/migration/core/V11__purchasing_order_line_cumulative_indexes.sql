-- 采购详情按订单回溯已确认收货，并按收货明细汇总已确认上架。
-- 仅索引有效最终事实；既有收货明细、质检和处置关联索引继续复用。
CREATE INDEX IF NOT EXISTS ix_purchase_receipt_order_confirmed
    ON purchase_receipt (tenant_id, purchase_order_id, id)
    WHERE isdel = 0 AND status = 'Confirmed';

CREATE INDEX IF NOT EXISTS ix_putaway_task_receipt_line_confirmed
    ON putaway_task (tenant_id, purchase_receipt_line_id, purchase_receipt_id)
    WHERE isdel = 0 AND status = 'Confirmed';
