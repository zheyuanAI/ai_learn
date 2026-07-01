# MES API 契约

## 通用约束
- 所有写接口都要求 `Authorization` 与 `Idempotency-Key`
- `tenant_id` 来自登录上下文
- 业务写接口统一返回：`code`、`message`、`data`、`request_id`

## 主数据接口
- `POST /api/boms`
- `GET /api/boms`
- `POST /api/routings`
- `GET /api/routings`

### `POST /api/boms`
- 必填：`bom_code`、`product_id`、`version`、`components`
- `components[*]` 必填：`component_product_id`、`component_qty`、`uom`

### `POST /api/routings`
- 必填：`routing_code`、`product_id`、`version`、`operations`
- `operations[*]` 必填：`operation_no`、`operation_name`、`work_center`

## 工单执行接口
- `POST /api/work-orders`
- `GET /api/work-orders`
- `POST /api/work-orders/{id}/release`
- `POST /api/work-orders/{id}/pause`
- `POST /api/work-orders/{id}/resume`
- `POST /api/work-orders/{id}/complete`
- `POST /api/dispatch-orders`
- `POST /api/work-reports`
- `POST /api/quality-inspections`
- `POST /api/finished-goods-receipts`

### `POST /api/work-orders`
- 必填：`product_id`、`planned_qty`、`planned_start_time`、`planned_finish_time`、`bom_id`、`routing_id`

### `POST /api/dispatch-orders`
- 必填：`work_order_id`、`operation_id`、`machine_id`、`operator_id`、`dispatch_qty`

### `POST /api/work-reports`
- 必填：`work_order_id`、`operation_id`、`report_time`、`qualified_qty`、`defect_qty`
- 返回：`reported_summary`、`work_order_progress`

### `POST /api/quality-inspections`
- 必填：`work_order_id`、`inspection_type`、`sample_qty`、`qualified_qty`、`defect_qty`
- 返回：`inspection_result`、`rework_required`

### `POST /api/finished-goods-receipts`
- 必填：`work_order_id`、`receipt_qty`、`warehouse_id`、`location_id`
- 返回：`finished_goods_summary`

## 查询过滤
- 工单查询至少支持：`keyword`、`status`、`product_id`、`planned_date_from`、`planned_date_to`
- 报工查询至少支持：`work_order_id`、`operation_id`、`date_from`、`date_to`
- 质检查询至少支持：`work_order_id`、`result`

## 业务错误码
- `MES_WO_001`：工单状态不允许当前操作
- `MES_WO_002`：工单未下达，禁止报工
- `MES_WO_003`：报工累计数量超出工单上限
- `MES_QC_001`：质检失败，禁止直接成品入库
- `MES_FG_001`：成品入库数量超出可入库上限
