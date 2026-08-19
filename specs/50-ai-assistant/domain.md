# AI 助手模型

## 核心实体
- `ChatSession`
- `ChatMessage`
- `ToolAuditLog`

## 通用字段约束
- 所有实体必须带：`id`、`tenant_id`、`created_at`、`updated_at`
- 所有工具审计都必须带：`user_id`、`tool_name`、`input_summary`、`output_summary`、`duration_ms`、`status`

## 页面字段冻结

### ChatSession
- 字段：`session_no`、`user_id`、`question_count`、`last_message_at`

### ChatMessage
- 字段：`role`、`content`、`source_summary`、`time_range_summary`

### ToolAuditLog
- 字段：`tenant_id`、`user_id`、`tool_name`、`input_summary`、`output_summary`、`duration_ms`、`status`、`error_reason`

## 关键规则
- AI 工具只读，不得写业务库
- 所有工具调用必须记录租户、用户、输入、输出摘要、耗时
- 生成类功能必须标注数据时间范围和来源
- 非授权用户不能调用业务工具

## 状态约束
- 工具调用状态：`Success`、`Failed`、`Timeout`、`Denied`

## 二期模型边界

- `KnowledgeDocument`、`KnowledgeChunk`、`PromptTemplate` 和文档索引状态机放入二期
