# 二维 GIS 与综合看板 API 契约

## 通用约束
- 写接口要求 `Authorization` 与 `Idempotency-Key`
- 查询接口必须自动过滤 `tenant_id`
- 地图与看板查询支持统一的 `time_range`

## 地图接口
- `GET /api/site-map`
- `POST /api/site-map/points`

### `POST /api/site-map/points`
- 必填：`entity_type`、`entity_id`、`x`、`y`
- 可选：`rotation`、`linked_page`

## 看板接口
- `GET /api/dashboard/inventory`
- `GET /api/dashboard/production`
- `GET /api/dashboard/quality`
- `GET /api/dashboard/device`
- `GET /api/dashboard/alarms`

### 看板查询参数
- 支持：`time_range`
- 可选：`warehouse_id`、`line_id`

## 业务错误码
- `MAP_001`：点位绑定实体不存在
- `MAP_002`：点位坐标非法
- `DB_001`：统计时间范围不支持

## 二期接口边界

- Cesium 三维场景、模型资源和三维状态同步接口放入二期
