# AI 助手 API 契约

## 通用约束
- 所有接口要求 `Authorization`
- 业务工具接口只读，不允许变更业务数据
- 所有响应必须附带 `request_id`

## 会话与知识库接口
- `POST /api/ai/chat`
- `POST /api/ai/knowledge/upload`
- `GET /api/ai/knowledge/documents`
- `GET /api/ai/tool-audit-logs`

### `POST /api/ai/chat`
- 必填：`message`
- 可选：`session_id`、`tool_whitelist`
- 返回：`answer`、`sources`、`time_range_summary`、`tool_calls`

### `POST /api/ai/knowledge/upload`
- 必填：`document_name`、`document_type`、`category`、`file`

## 只读业务工具接口
- `POST /api/ai/tools/queryLowStock`
- `POST /api/ai/tools/queryInventoryByProductAndWarehouse`
- `POST /api/ai/tools/querySalesOrderStatus`
- `POST /api/ai/tools/queryPurchaseOrderStatus`
- `POST /api/ai/tools/queryWorkOrderProgress`
- `POST /api/ai/tools/queryQualityStatistics`
- `POST /api/ai/tools/queryDeviceAlarm`
- `POST /api/ai/tools/generateDailyOperationReport`

### 工具接口通用请求体
- 支持：`filters`
- 支持：`time_range`
- 可选：`limit`

## 业务错误码
- `AI_AUTH_001`：当前用户无工具调用权限
- `AI_DOC_001`：文档类型不支持
- `AI_DOC_002`：文档解析失败
- `AI_TOOL_001`：工具调用失败
- `AI_TOOL_002`：跨租户访问被拒绝
