# ERP/WMS 领域模型

## 核心实体
- `Product`
- `Warehouse`
- `Location`
- `Customer`
- `Supplier`
- `Inventory`
- `InventoryTransaction`
- `PurchaseOrder`
- `PurchaseReceipt`
- `PutawayTask`
- `SalesOrder`
- `PickTask`
- `SalesShipment`
- `TransferOrder`
- `StocktakeOrder`
- `AccountsReceivable`
- `AccountsPayable`

## 通用字段约束
- 所有主数据和业务单据都必须带：`id`、`tenant_id`、`status`、`created_by`、`created_at`、`updated_by`、`updated_at`
- 所有明细行都必须带：`line_no`、`product_id`、`uom`、`qty`
- 所有库存相关实体都必须明确：`warehouse_id`、`location_id`、`product_id`
- 启用批次管理时必须带 `lot_no`
- 启用序列号管理时必须带 `serial_no`
- 启用保质期管理时必须带 `production_date`、`expiry_date`

## 页面字段冻结

### Product
- 列表字段：`sku`、`name`、`spec`、`uom`、`batch_managed`、`serial_managed`、`shelf_life_managed`、`status`
- 详情字段：`category`、`barcode`、`default_supplier_id`、`default_warehouse_id`

### Warehouse
- 列表字段：`warehouse_code`、`warehouse_name`、`warehouse_type`、`manager_name`、`status`
- 详情字段：`address`、`enabled_for_inbound`、`enabled_for_outbound`

### Location
- 列表字段：`location_code`、`warehouse_id`、`location_type`、`capacity_limit`、`pickable`、`status`
- 详情字段：`zone`、`aisle`、`rack`、`level`

### Inventory
- 主字段：`on_hand_qty`、`frozen_qty`、`available_qty`、`lot_no`、`serial_no`、`last_transaction_at`
- 不变量：`available_qty = on_hand_qty - frozen_qty`

### PurchaseOrder
- 表头字段：`po_no`、`supplier_id`、`expected_arrival_date`、`status`、`remark`
- 明细字段：`product_id`、`ordered_qty`、`received_qty`、`pending_qty`、`target_warehouse_id`

### PurchaseReceipt
- 表头字段：`receipt_no`、`purchase_order_id`、`receipt_time`、`status`
- 明细字段：`product_id`、`received_qty`、`qualified_qty`、`lot_no`、`temp_location_id`

### PutawayTask
- 字段：`task_no`、`purchase_receipt_id`、`product_id`、`from_location_id`、`to_location_id`、`putaway_qty`、`status`

### SalesOrder
- 表头字段：`so_no`、`customer_id`、`planned_ship_date`、`status`、`remark`
- 明细字段：`product_id`、`ordered_qty`、`frozen_qty`、`picked_qty`、`shipped_qty`

### PickTask
- 字段：`task_no`、`sales_order_id`、`product_id`、`source_location_id`、`pick_qty`、`picked_qty`、`status`

### SalesShipment
- 字段：`shipment_no`、`sales_order_id`、`ship_time`、`carrier_name`、`status`

### TransferOrder
- 字段：`transfer_no`、`from_warehouse_id`、`from_location_id`、`to_warehouse_id`、`to_location_id`、`status`

### StocktakeOrder
- 字段：`stocktake_no`、`warehouse_id`、`scope_type`、`status`
- 明细字段：`product_id`、`system_qty`、`counted_qty`、`variance_qty`、`variance_reason`

## 核心规则
- `available_qty = on_hand_qty - frozen_qty`
- 冻结、释放、扣减、回补都必须记录库存流水
- 销售单冻结只允许一次成功扣减可用库存
- 入库确认后才能生成应付单
- 出库确认后才能生成应收单
- 所有库存变动都必须落 `InventoryTransaction`
- 业务单据跨租户不可见，也不可跨租户关联主数据

## 状态机冻结

### 采购单
- `Draft -> Submitted -> Approved -> PartiallyReceived -> Completed`
- `Draft` 允许编辑与删除
- `Submitted` 只允许审批或驳回回到 `Draft`
- `Approved` 后才能创建入库单
- 所有明细 `received_qty = ordered_qty` 时进入 `Completed`

### 入库单
- `Draft -> Confirmed`
- `Confirmed` 后不可再次确认

### 上架任务
- `Pending -> Processing -> Confirmed`
- `Confirmed` 后库存从暂存位转到目标库位

### 销售单
- `Draft -> Submitted -> Approved -> Frozen -> Picking -> Shipped -> Completed`
- `Approved` 后才能冻结库存
- `Frozen` 后可生成拣货任务
- 全部拣货完成进入 `Picking`
- 全部出库确认进入 `Shipped`
- 应收生成完成进入 `Completed`

### 盘点单
- `Draft -> Counting -> Confirmed`
- `Confirmed` 后差异必须写入库存流水

## 幂等与一致性约束
- 采购入库确认、上架确认、销售冻结、拣货确认、出库确认都必须支持幂等键
- 相同 `Idempotency-Key + tenant_id + endpoint` 重复调用必须返回第一次成功结果
- 库存冻结失败时不得写入冻结库存，但应记录失败审计日志
