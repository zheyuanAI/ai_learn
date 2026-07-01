# 架构与边界

## 建议架构
- 后端：Gateway、Auth、Core、IoT 四服务
- 前端：Vue 管理端，承载业务页面、地图、看板、AI 助手界面
- 中间件：PostgreSQL、Redis、RabbitMQ、MQTT、MinIO、pgvector

## 服务边界
- Auth：认证、用户、角色、菜单、租户上下文
- Core：ERP/WMS、MES、统计、AI 工具只读查询
- IoT：设备模型、遥测、协议适配、告警
- Gateway：统一入口、鉴权转发、异常包装

## 横切约束
- 单库多租户：核心业务表必须带 `tenant_id`
- 所有关键写操作需要幂等策略
- 领域事件通过 Outbox + MQ 传播
- AI 工具默认只读，按租户和权限过滤数据
