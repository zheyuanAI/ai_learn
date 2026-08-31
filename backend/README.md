# Backend

## 模块
- `platform-gateway`：统一入口与后续网关能力
- `platform-auth`：认证、租户、用户、角色、菜单
- `platform-core`：采购、销售、库存、制造、质量、追溯、统计与 AI 只读业务查询
- `platform-iot`：设备、MQTT 遥测、状态与告警事实
- `platform-shared`：公共类型、异常、基础配置

## 本地服务端口

端口基线以 `../docs/specs/00-project/架构设计.md` 为准，下表提供后端模块的便捷索引。

| 模块 | 端口 |
| --- | ---: |
| `platform-gateway` | 10001 |
| `platform-auth` | 10002 |
| `platform-core` | 10003 |
| `platform-iot` | 10004 |

## 当前状态
- 已创建多模块父工程和服务启动类
- 已预留 `/internal/ping` 探活接口
- 还未接数据库、鉴权、MQTT 和业务领域代码；正式计划中的业务能力均尚未实现

## 规格入口
- 当前开发计划与一期范围：`../docs/specs/00-project/正式项目计划.md`
- 服务边界与横切约束：`../docs/specs/00-project/架构设计.md`
- 领域规则、接口与验收：对应领域目录的 `概述.md`、`领域模型.md`、`接口契约.md`、`验收标准.md`

## 一期边界
- 一期依赖与中间件范围以 `../docs/specs/00-project/架构设计.md` 为准；当前使用 PostgreSQL、Redis、Mosquitto，RabbitMQ、MinIO、pgvector 属于二期候选
- `platform-core` 物理上保持单服务，内部按采购、销售、库存、制造、质量、追溯、看板和 AI 逻辑模块组织
- `platform-auth`、`platform-core`、`platform-iot` 共用 PostgreSQL 实例时必须保持 schema 与数据访问边界
