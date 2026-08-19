# 二维 GIS 与综合看板模型

## 核心实体
- `SiteMap`
- `MapPoint`
- `DevicePoint`
- `WarehousePoint`
- `DashboardMetric`
- `AlarmMarker`

## 通用字段约束
- 所有地图与看板实体都必须带：`id`、`tenant_id`、`created_at`、`updated_at`
- 所有点位都必须带：`entity_type`、`entity_id`、`x`、`y`、`rotation`、`status`

## 页面字段冻结

### SiteMap
- 字段：`map_code`、`map_name`、`background_type`、`status`

### MapPoint
- 字段：`point_code`、`entity_type`、`entity_id`、`x`、`y`、`rotation`、`status_color`、`linked_page`

### DashboardMetric
- 指标字段：`metric_code`、`metric_name`、`metric_value`、`time_range`
- 首批指标：`available_inventory`、`work_order_in_progress`、`device_online_rate`、`open_alarm_count`

### AlarmMarker
- 字段：`alarm_id`、`point_id`、`alarm_level`、`marker_color`、`marker_status`

## 关键规则
- 点位必须支持绑定业务实体 ID
- 告警点位颜色和状态需统一规则
- 看板统计接口与地图状态接口需共享时间过滤条件
- 地图与看板状态源必须一致

## 状态约束
- 点位状态：`Normal`、`Warning`、`Alarm`、`Offline`
- 首页看板时间范围至少支持：`today`、`7d`、`30d`

## 二期模型边界

- 三维场景、设备三维模型及 `BlinkingAlarm`、`GreyOffline` 等三维表现状态放入二期
