# ERP/WMS API 契约

## 通用约束
- 所有写接口都要求 `Authorization` 与 `Idempotency-Key`
- `tenant_id` 来自登录上下文，不允许前端自由指定
- 写接口统一返回：`code`、`message`、`data`、`request_id`
- 涉及库存冻结、释放、扣减的接口必须返回库存变化摘要

## 主数据接口
- `GET /api/products`
- `POST /api/products`
- `GET /api/warehouses`
- `POST /api/warehouses`
- `GET /api/locations`
- `POST /api/locations`

## 采购入库接口
- `POST /api/purchase-orders`
- `GET /api/purchase-orders`
- `POST /api/purchase-orders/{id}/submit`
- `POST /api/purchase-orders/{id}/approve`
- `POST /api/purchase-receipts/{id}/confirm`
- `POST /api/putaway-tasks/{id}/confirm`

### `POST /api/purchase-orders`
- 必填：`supplier_id`、`expected_arrival_date`、`lines`
- `lines[*]` 必填：`product_id`、`ordered_qty`、`uom`、`target_warehouse_id`

### `POST /api/purchase-receipts/{id}/confirm`
- 必填：`receipt_time`、`lines`
- `lines[*]` 必填：`product_id`、`received_qty`、`qualified_qty`、`temp_location_id`
- 启用批次管理时必填：`lot_no`
- 返回：`inventory_delta_summary`、`accounts_payable_draft`

### `POST /api/putaway-tasks/{id}/confirm`
- 必填：`to_location_id`、`putaway_qty`
- 返回：`location_transfer_summary`

## 销售出库接口
- `POST /api/sales-orders`
- `GET /api/sales-orders`
- `POST /api/sales-orders/{id}/submit`
- `POST /api/sales-orders/{id}/approve`
- `POST /api/sales-orders/{id}/freeze`
- `POST /api/pick-tasks/{id}/confirm`
- `POST /api/sales-shipments/{id}/confirm`

### `POST /api/sales-orders/{id}/freeze`
- 必填：`freeze_lines`
- `freeze_lines[*]` 必填：`product_id`、`warehouse_id`、`location_id`、`freeze_qty`
- 返回：`inventory_before`、`inventory_after`、`frozen_summary`

### `POST /api/pick-tasks/{id}/confirm`
- 必填：`picked_qty`、`source_location_id`
- 返回：`picked_summary`

### `POST /api/sales-shipments/{id}/confirm`
- 必填：`ship_time`、`shipment_lines`
- `shipment_lines[*]` 必填：`product_id`、`ship_qty`
- 返回：`inventory_delta_summary`、`accounts_receivable_draft`

## 二期接口边界

- 一期不提供收款、付款、核销、账龄和财务报表接口
- 一期不提供序列号、保质期及复杂批次组合接口

## 调拨与盘点接口
- `POST /api/transfers/{id}/confirm`
- `POST /api/stocktakes/{id}/confirm`
- `GET /api/inventory/transactions`

### `POST /api/stocktakes/{id}/confirm`
- 必填：`lines`
- `lines[*]` 必填：`product_id`、`counted_qty`、`variance_reason`
- 返回：`variance_summary`

## 查询过滤
- 采购、销售、库存流水查询至少支持：`keyword`、`status`、`date_from`、`date_to`
- 库存流水查询支持：`product_id`、`warehouse_id`、`location_id`、`transaction_type`
- 所有查询接口必须自动过滤 `tenant_id`

## 业务错误码
- `INV_001`：库存不足，冻结失败
- `INV_002`：重复冻结，幂等拦截
- `INV_003`：重复出库确认，幂等拦截
- `INV_004`：目标库位不可用
- `PO_001`：采购单状态不允许当前操作
- `SO_001`：销售单状态不允许当前操作
- `ST_001`：盘点单状态不允许确认
