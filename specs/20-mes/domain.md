# MES 领域模型

## 核心实体
- `Bom`
- `Routing`
- `Operation`
- `WorkOrder`
- `DispatchOrder`
- `WorkReport`
- `QualityInspection`
- `FinishedGoodsReceipt`

## 通用字段约束
- 所有实体必须带：`id`、`tenant_id`、`status`、`created_by`、`created_at`、`updated_by`、`updated_at`
- 所有工艺相关实体都必须明确 `product_id`
- 所有执行类单据都必须能追溯：`work_order_id`、`operation_id`、`operator_id`

## 页面字段冻结

### Bom
- 字段：`bom_code`、`product_id`、`version`、`status`
- 明细字段：`component_product_id`、`component_qty`、`uom`、`scrap_rate`

### Routing
- 字段：`routing_code`、`product_id`、`version`、`status`
- 工序字段：`operation_no`、`operation_name`、`work_center`、`standard_time_minutes`

### WorkOrder
- 表头字段：`work_order_no`、`product_id`、`planned_qty`、`planned_start_time`、`planned_finish_time`、`status`
- 详情字段：`bom_id`、`routing_id`、`current_operation_id`、`reported_qty`、`qualified_qty`、`defect_qty`

### DispatchOrder
- 字段：`dispatch_no`、`work_order_id`、`operation_id`、`machine_id`、`operator_id`、`dispatch_qty`、`status`

### WorkReport
- 字段：`report_no`、`work_order_id`、`operation_id`、`report_time`、`qualified_qty`、`defect_qty`、`remark`
- 约束：`report_qty = qualified_qty + defect_qty`

### QualityInspection
- 字段：`inspection_no`、`work_order_id`、`inspection_type`、`sample_qty`、`qualified_qty`、`defect_qty`、`result`

### FinishedGoodsReceipt
- 字段：`receipt_no`、`work_order_id`、`receipt_qty`、`warehouse_id`、`location_id`、`status`

## 关键规则
- 工单必须绑定产品、计划数量、计划完工时间
- 工单必须引用有效的 `Bom` 与 `Routing`
- 报工数量 = 合格品 + 不良品
- 工单未下达前不能派工、不能报工
- 质检不通过时记录不良数量并阻止成品入库；返工单属于二期范围
- 成品入库只允许基于已完工且已确认数量生成
- 成品入库数量不得超过工单累计合格数量减已入库数量

## 状态机冻结

### 工单
- `Draft -> Released -> InProgress -> Completed`
- 质检阻塞时保留 `InProgress`，并增加 `quality_blocked = true`
- `Released` 后才允许派工
- 首次有效报工后进入 `InProgress`
- 累计合格数量满足完工条件后才允许 `Completed`

### 派工单
- `Draft -> Released -> Processing -> Completed`

### 质检单
- `Draft -> Submitted -> Passed`
- `Draft -> Submitted -> Failed`

## 一致性约束
- 报工、质检、成品入库都必须按工单与工序维度落审计记录
- 工单、派工、报工、质检都必须按租户隔离

## 二期模型边界

- `CapacityCalendar`、自动排程规则和 APS 模型放入二期
- `ReworkRecord`、返工状态机和重复质检链路放入二期
