# AI 助手 API 契约

## 通用约束
- 所有接口要求 `Authorization`
- 业务工具接口只读，不允许变更业务数据
- 所有响应必须附带 `request_id`

## 会话与审计接口
- `POST /api/ai/chat`
- `GET /api/ai/tool-audit-logs`

### `POST /api/ai/chat`
- 必填：`message`
- 可选：`session_id`、`tool_whitelist`
- 返回：`answer`、`sources`、`time_range_summary`、`tool_calls`

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
- `AI_TOOL_001`：工具调用失败
- `AI_TOOL_002`：跨租户访问被拒绝

## 二期接口边界

- 知识库上传、文档列表、解析切分、向量索引和 RAG 检索接口放入二期
