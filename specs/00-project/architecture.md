# 架构与边界

## 建议架构
- 后端：Gateway、Auth、Core、IoT 四服务
- 前端：Vue 管理端，承载业务页面、地图、看板、AI 助手界面
- 一期中间件：PostgreSQL、Redis、Mosquitto（MQTT）
- 二期候选中间件：RabbitMQ、MinIO、pgvector

## 服务边界
- Auth：认证、用户、角色、菜单、租户上下文
- Core：ERP/WMS、MES、统计、AI 工具只读查询
- IoT：设备模型、遥测、协议适配、告警
- Gateway：统一入口、鉴权转发、异常包装

## 横切约束
- 单库多租户：核心业务表必须带 `tenant_id`
- 所有关键写操作需要幂等策略
- 一期优先使用清晰的同步接口完成主业务闭环，只在 MQTT 设备接入边界使用消息协议
- 通用 Outbox + RabbitMQ 可靠事件传播放入二期
- AI 工具默认只读，按租户和权限过滤数据

## 本地开发环境基线
- 自研项目使用 Node 20 与 PostgreSQL 16（`127.0.0.1:5433`）。
- 参考工程可继续保留各自的 Node 22 与 PostgreSQL 16（`127.0.0.1:5323`）配置。
