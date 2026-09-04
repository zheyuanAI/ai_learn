# 阶段 2–7 后端协同实施设计

> 日期：2026-09-03  
> 状态：已获用户批准，作为后续实施计划的设计基线  
> 适用范围：`backend/**`，以及后端运行所必需的 `deploy/**`
> Git 约束：只修改工作区，不执行 `git commit`、`git push`、建分支或合并

## 1. 请求与材料边界

本次用户请求决定执行方式：由主代理根据依赖关系拆分任务，合理并行派发子代理，统一整合、冲突消解、质量审查和最终交付。

根目录 `luna-max交接.md` 是本次实施的任务材料，提供阶段范围、角色边界、接口目标、闸门和技术约束。它不改变仓库事实优先级，也不把目标接口或原型行为变成已实现能力。

当前已确认的实现事实是：`platform-shared`、`platform-gateway`、`platform-auth` 存在阶段 0–1 基线但 S0 尚未收口；`platform-core` 和 `platform-iot` 目前只有应用入口、健康检查和 V1 基线迁移；前端和原型中的业务数据仍不能作为后端实现证据。

## 2. 目标与非目标

### 2.1 目标

- 收口最小身份 JWT、可信身份 Header、Redis Fail-Closed 和方法级权限链路。
- 建立租户隔离的主数据、库存余额/预留/流水、调拨和盘点内核。
- 以库存应用服务为唯一库存写入口，完成采购、销售、制造与 IoT 事实链路。
- 通过明确的应用服务查询端口连接追溯、GIS 和七类看板，不建立第二套业务事实。
- 为每个阶段提供测试证据、冻结契约、权限码、错误码、状态和 `allowedActions`。

### 2.2 非目标

- 不修改 `frontend/**`、`docs/prototype/**` 或参考工程目录。
- 不修改 Auth V1–V5 或其他历史 Flyway 迁移。
- 不实现 RabbitMQ、MinIO、pgvector、MRP、APS、WIP、线边仓、返工、三维数字孪生、Modbus TCP 或 OPC UA。
- 不把 AI、GIS、看板或 IoT 上下文补链做成新的业务事实来源。
- 不连接或污染 `127.0.0.1:5433/ai_learn` 开发数据库。

## 3. 依赖波次

采用“闸门串行、独立模块并行”的执行方式：

```text
S0 安全链路收口
  ↓
S2 主数据 + 库存内核 + 调拨 + 盘点
  ↓
S5-foundation：BOM/工艺/工单来源只读能力（内部准备，不发布 READY-S5）
  ↓
S3 采购/质量/上架  ║  S4 销售/预留/拣货/发货
                  ↓
S5 制造执行完整链路  ║  S6 IoT 自有事实链路
                  ↓              ↓
          S6 生产上下文补链与重试
                  ↓
S7 追溯 + GIS + 七类看板
  ↓
阶段 2–7 联合回归与最终审查
```

### 3.1 为什么保留 `S5-foundation`

采购明细允许保存 `sourceWorkOrderId`，而完整制造能力位于 S5；若严格先做完整 S3 再做 S5，会形成采购来源校验与制造工单实现的循环依赖。因此先建立最小的 BOM、Routing、WorkOrder 来源查询和同租户校验能力，采购使用应用服务软引用，不建立跨阶段硬外键。只有完整制造执行、生产上下文查询和测试通过后，才发布 `READY-S5`。

### 3.2 可并行边界

- S3 与 S4 只依赖已冻结的 S2 库存应用接口，分别使用 Core V3/V4 迁移和互斥领域目录，可以并行。
- S5 执行部分与 S6 IoT 自有设备/遥测/状态/告警部分可以并行；S6 的上下文补链必须等待 S5 的 `ProductionContextQuery`。
- S7 的真实聚合必须等待所有事实查询端口冻结；地图配置和投影测试可以在不连接真实源数据的情况下先行准备，但 `READY-S7` 只在真实端口联调后发布。

## 4. 阶段所有权与交付物

| 阶段 | 允许写入范围 | 主要交付 | 发布闸门 |
| --- | --- | --- | --- |
| S0 | `platform-shared`、`platform-gateway`、`platform-auth` 及其直接测试，Auth V6 权限迁移 | JWT、可信 Header、Redis 权限恢复、401/403/503、后台权限 | `READY-S0` |
| S2 | `platform-core` V2 迁移、masterdata/inventory/transfer/stocktake 及测试 | 六类主数据、库存命令/查询、调拨、盘点、库存不变量 | `READY-S2` |
| S5-foundation | `platform-core` BOM/Routing/WorkOrder 来源查询及测试 | 采购可依赖的同租户生产来源校验 | 内部依赖，不单独对前端冻结 |
| S3 | `platform-core` V3 迁移、purchasing/quality/putaway 及测试 | 采购、到货拒收、QualityHold、质检、处置、上架 | `READY-S3` |
| S4 | `platform-core` V4 迁移、sales 及测试 | 销售订单、自动预留、直接拣货、退回、释放、发货 | `READY-S4` |
| S5 | `platform-core` V5 迁移、manufacturing 及测试 | BOM/工艺、工单、派工、执行、领退料、报工、质检、成品入库 | `READY-S5` |
| S6 | `platform-iot` V2 迁移、IoT 领域、MQTT/模拟摄取、Mosquitto 配置及测试 | 设备、凭证、遥测、状态、告警、上下文补链 | `READY-S6` |
| S7 | `platform-core` V6 迁移、traceability/gis/dashboard 及测试 | 追溯、二维地图、七类看板、权限裁剪、陈旧缓存 | `READY-S7` |

主代理只在阶段子代理完成后整合结果；每个实现子代理必须有互斥写集、测试证据和中文报告。每个任务完成后另派审查子代理检查规格符合性与代码质量。

## 5. 冻结的接口与数据口径

### 5.1 通用约束

- `tenantId` 只从可信认证上下文获得，客户端字段不能覆盖；跨租户资源按不可见或统一错误处理。
- 数量在数据库使用 `NUMERIC(19,6)`，Java 内部使用 `BigDecimal`，禁止 `double`/`float`。
- HTTP 数量字段在业务 DTO 中按字符串传输和接收，以匹配现有前端精度约定；应用层在边界转换为 `BigDecimal`。不修改全局 Jackson 的既有时间和 BigDecimal 配置。
- 业务 DTO 对外使用现有前端约定的 `camelCase`，包括 `allowedActions`、`sourceWorkOrderId` 等；统一响应包装沿用实际代码中的 `request_id`。
- 每个命令返回后端计算的状态、累计数量、版本、事实标识和 `allowedActions`，页面不能自行推导可执行状态。
- 写接口需要 `Authorization`；形成业务事实的命令还需要 `Idempotency-Key`。同租户、同接口、同幂等键同载荷返回首次结果，不同载荷返回 409。
- 统一响应结构为 `code`、`message`、`data`、`request_id`、`timestamp`；错误码和 HTTP 状态必须与领域契约一致。

### 5.2 S0 安全口径

- JWT 只包含 `sub`、`jti`、`tenant_id`、`username`、签发/过期等最小身份字段，不携带角色或权限。
- Gateway 先清理客户端提供的内部身份和权限 Header，再在 JWT、当前会话和请求 ID 校验通过后注入可信身份 Header。
- 下游不再从 `X-Authorities`、`X-Permissions`、`X-Roles` 等 Header 构造权限；权限只从 Redis 权限上下文恢复。
- Redis 未命中、连接失败、超时、非法 JSON 和反序列化失败均为 503；`[]` 表示已认证但无业务权限，业务方法返回 403；禁止权限缓存回源数据库。
- 身份上下文、租户上下文、MDC 和 `SecurityContext` 在请求完成的 `finally` 中清理。

### 5.3 S2–S5 库存和业务事实口径

- `availableQty = onHandQty - reservedQty >= 0`；QualityHold 的可分配量为零。
- 收货前拒收不入库；实际接收一次性进入 QualityHold；质检只写质量事实；放行、上架和调拨只移动位置；发货、报废、领料才扣减实物。
- 制造、采购、销售只能调用 `InventoryCommandService`，禁止直接注入或操作 Inventory Mapper。
- 销售履约数量始终满足 `0 <= shippedQty <= pickedQty <= reservedQty <= orderedQty`。
- 盘点确认使用开始时保存的版本快照；版本变化即拒绝，差异通过库存命令生成调整流水。

### 5.4 S6–S7 跨服务口径

- IoT 先保存自己的遥测、状态和告警事实；Core 故障不能回滚 IoT 事实。
- IoT 与 Core 的生产上下文使用内部服务身份/HMAC，不复用用户身份 Header；上下文失败只保留 `Pending` 并可重试或人工补链。
- GIS 的 `MapPointConfiguration` 是点位坐标唯一写入方；设备状态、告警级别和源更新时间仍以 IoT 为事实源。
- S7 只通过 `InventoryFactsQuery`、`PurchasingFactsQuery`、`SalesFactsQuery`、`ManufacturingFactsQuery`、`QualityFactsQuery` 和 `IotFactsPort` 查询，不跨模块 Mapper 或跨服务直查表。
- 看板时间范围只支持 `today`、`7d`、`30d`；新鲜缓存 60 秒，最多返回带原生成时间的 10 分钟陈旧结果；失败时不得伪造零值。

## 6. 测试与阶段闸门

每个实现任务遵循测试先行：先添加会失败的测试并运行确认失败，再写最小实现，随后运行针对性测试和阶段聚合测试。每个阶段必须包含租户、权限、状态、幂等、并发、回滚和错误码覆盖，适用时增加 PostgreSQL 12.1 隔离迁移测试与 Redis Fail-Closed 测试。

基础验证命令固定为：

```powershell
cd backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd test
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd package
git diff --check
```

真实 PostgreSQL 迁移测试必须使用隔离实例或兼容容器，快速单元回归可以使用 H2；两者不得连接开发库。S6 需要额外验证 Mosquitto 匿名访问关闭、设备 ACL、QoS 1 重复消息和设备凭证撤销。S7 需要额外验证权限裁剪、跨租户过滤、七类摘要、状态优先级和陈旧缓存。

每个 `READY-Sn` 交付固定包含：闸门、接口清单、请求字段与校验、响应字段与可空性、状态/枚举、权限码、错误码与 HTTP 状态、`allowedActions`、分页/筛选/排序、示例请求/响应、OpenAPI 地址或导出文件、测试命令与结果、已知限制。

## 7. 风险与处理

- 现有工作区包含用户删除的 `docs/superpowers/specs/2026-09-03-stage-2-5-integrated-design.md`、未跟踪的交接文件及前端改动；执行前后均只比较本任务写集，不恢复、不覆盖、不提交这些改动。
- Gateway 当前 IoT/GIS 路由与目标路径不完全一致；在对应阶段只增加必要路由，不顺手重排无关配置。
- `sourceWorkOrderId`、生产上下文 HMAC 和 GIS 点位所有权是跨阶段契约；在实现相关阶段先更新领域契约与测试，再开放下游接入。
- 平台共享安全代码属于高风险边界；S0 每次修改都必须先有失败测试、再有最小修复和独立审查。
- 子代理不得同时修改同一迁移、同一公共 DTO 或同一配置文件；发现冲突由主代理暂停下游合并并统一裁决。

## 8. 完成定义

只有 `READY-S0`、`READY-S2` 至 `READY-S7` 全部具备测试证据，隔离 PostgreSQL 12.1 迁移通过，库存和安全不变量通过自动化回归，规格与实际接口一致，且确认未改前端、未修改历史迁移、未污染开发库、未提交 Git，才报告阶段 2–7 后端完成。
