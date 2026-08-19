# IoT 设备接入领域模型

## 核心实体
- `Device`
- `DeviceTelemetry`
- `DeviceStatus`
- `DeviceAlarm`
- `ProtocolAdapter`
- `NotificationRecord`

## 通用字段约束
- 所有实体都必须带：`id`、`tenant_id`、`created_at`、`updated_at`
- 所有设备都必须带：`device_code`、`device_name`、`device_type`、`protocol_type`、`status`
- 所有遥测都必须带：`device_id`、`ts`、`metric_code`、`metric_value`
- 所有告警都必须带：`device_id`、`alarm_code`、`alarm_level`、`triggered_at`、`status`

## 页面字段冻结

### Device
- 列表字段：`device_code`、`device_name`、`device_type`、`protocol_type`、`status`、`last_heartbeat_at`
- 详情字段：`line_id`、`area_id`、`work_order_id`、`map_point_id`

### DeviceTelemetry
- 字段：`metric_code`、`metric_name`、`metric_value`、`metric_unit`、`ts`
- 首批演示指标：`temperature`、`running_state`、`output_qty`、`heartbeat`

### DeviceStatus
- 字段：`online_status`、`running_status`、`alarm_status`、`last_seen_at`

### DeviceAlarm
- 字段：`alarm_no`、`device_id`、`alarm_type`、`alarm_level`、`triggered_at`、`acked_at`、`recovered_at`、`ack_user_id`

### ProtocolAdapter
- 字段：`adapter_type`、`simulator_enabled`、`topic_or_endpoint`、`status`
- 一期范围：`MQTT`
- 二期范围：`Modbus TCP`、`OPC UA`、Ditto 数字孪生映射

## 关键规则
- 每条遥测数据都必须绑定设备和时间戳
- 离线判定基于心跳超时
- 告警支持触发、确认、恢复三阶段
- 设备可选关联工单、产线、区域点位
- 同一设备同一告警类型在去重窗口内重复触发时不得无限新增告警

## 状态机冻结

### 设备在线状态
- `Online <-> Offline`
- 心跳超时进入 `Offline`
- 新心跳进入 `Online`

### 设备运行状态
- `Idle -> Running -> Stopped`
- 允许 `Stopped -> Running`

### 告警
- `Triggered -> Acked -> Recovered`
- `Triggered` 时必须记录触发时间
- `Acked` 时必须记录确认人和确认时间
- `Recovered` 时必须记录恢复时间

## 一致性约束
- 告警状态变化后必须能同步到地图页和看板页
- 遥测入库失败不得更新设备最新状态
- 不同租户设备、遥测、告警绝对隔离
