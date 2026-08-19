# IoT 设备接入 API 契约

## 通用约束
- 写接口要求 `Authorization` 与 `Idempotency-Key`
- 设备模拟接口只用于演示与测试环境
- 查询接口必须自动过滤 `tenant_id`

## 设备接口
- `POST /api/devices`
- `GET /api/devices`
- `GET /api/devices/{id}/telemetry`
- `GET /api/devices/{id}/status`
- `GET /api/device-alarms`
- `POST /api/device-alarms/{id}/ack`

### `POST /api/devices`
- 必填：`device_code`、`device_name`、`device_type`、`protocol_type`
- 可选：`line_id`、`area_id`、`work_order_id`、`map_point_id`

### `GET /api/devices/{id}/telemetry`
- 查询参数：`metric_code`、`date_from`、`date_to`、`limit`

### `POST /api/device-alarms/{id}/ack`
- 必填：`ack_comment`
- 返回：`alarm_status`、`ack_user`

## 协议模拟接口
- `POST /api/protocol-adapters/mqtt/simulate`

### 模拟接口请求体
- 必填：`device_code`、`ts`、`metrics`
- `metrics[*]` 必填：`metric_code`、`metric_value`

## 业务错误码
- `IOT_DEV_001`：设备不存在
- `IOT_DEV_002`：设备协议类型不支持
- `IOT_TLM_001`：遥测格式校验失败
- `IOT_ALM_001`：告警状态不允许确认
- `IOT_ALM_002`：重复触发命中去重窗口

## 二期接口边界

- Modbus TCP、OPC UA 协议模拟和真实设备适配接口放入二期
- Ditto Thing/Feature 映射和数字孪生同步接口放入二期
