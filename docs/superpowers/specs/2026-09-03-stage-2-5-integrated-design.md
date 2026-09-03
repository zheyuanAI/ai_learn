# 阶段 2–5 一体化设计方案

> 日期：2026-09-03  
> 状态：待用户评审  
> 适用范围：阶段 2 主数据与库存内核、阶段 3 采购入库、阶段 4 销售出库、阶段 5 制造执行，以及进入这些业务阶段前必须完成的权限链路收口  
> 实施方式：允许 Gemini 使用多个子代理协作，但必须遵守本文的依赖顺序、文件所有权和阶段门槛  
> Git 约束：所有 AI 改动保持未提交，由用户统一检查和处理

## 1. 目标

本轮将阶段 2、3、4、5 作为一个连续业务版本统一设计，形成从主数据、库存事实内核，到采购入库、销售出库和制造执行的稳定业务底座。

本轮必须同时完成阶段 1 遗留的权限链路收口：JWT 只携带身份；Gateway 只校验 Token、当前会话并透传可信身份；Auth 在登录成功时预热 Redis 权限上下文；Core 与 IoT 由 `platform-shared` 从 Redis 恢复 Spring Security Authority；所有业务方法统一使用 `hasAuthority(...)`；Redis 权限缓存未命中或异常时 Fail-Closed。

本轮完成后应具备以下能力：

1. 客户、供应商、计量单位、商品、仓库和库位可按租户维护。
2. 所有库存增加、扣减、移动、预留、释放和盘点调整经过统一库存应用接口。
3. 调拨、盘点、采购、销售和制造不能直接修改库存余额。
4. 库存余额、预留分配和库存流水可以相互核对。
5. 采购实际接收只增加一次库存，质量放行和上架只移动位置。
6. 销售直接拣货在同一事务内完成自动预留与移位，发货才扣减实物并释放预留。
7. MES 领料、退料和成品入库复用库存内核。
8. 每个业务写命令具备租户隔离、权限、状态校验、幂等和并发保护。

## 2. 当前仓库事实与设计约束

### 2.1 当前事实

- `platform-core` 当前只有健康检查、应用入口和 Core 的 V1 数据库基线，阶段 2–5 业务路由尚未实现。
- `platform-core` 与 `platform-iot` 已经只依赖 `platform-shared`，没有 Maven 层面的 Auth 依赖。
- Gateway 已停止主动写入 `X-Authorities`，但常量、共享过滤器、旧上下文拦截器和测试仍保留 `X-Authorities` 或 `X-Permissions` 逻辑。
- Auth 已存在 `auth:perms:{tenantId}:{userId}` 的读写能力，但登录成功后会清除权限缓存，没有完成权限预热。
- Auth 的权限缓存读取异常目前会被折叠为缓存未命中，并可能回源数据库，不符合本轮 Fail-Closed 要求。
- `SharedSecurityConfig` 当前对普通请求使用 `permitAll()`，真实业务方法尚未形成完整的 `hasAuthority(...)` 防线。
- Auth V2 已建立大部分阶段 2–5 权限点，但缺少完整的调拨、盘点、客户、供应商、计量单位和库位权限；部分测试与原型仍使用点号权限编码。
- `docs/prototype/pages/master-data.html` 是目标设计和假数据原型，不代表功能已经实现。

### 2.2 固定约束

- Java 21、Spring Boot 3.3.5、MyBatis-Plus、Flyway 10、PostgreSQL 12.1、Redis。
- 所有服务共用 PostgreSQL 的 `public` schema，通过 `auth_`、`md_`、`inv_`、`pur_`、`sales_`、`mes_` 表前缀和独立 Flyway 历史表隔离。
- Core 内保持物理单体、逻辑模块化；模块之间只能调用应用服务，不能跨模块直接修改表。
- 所有业务实体强制包含 `tenant_id`；客户端不能提交或覆盖当前租户。
- 业务核心表使用逻辑软删除；库存流水、库存命令事实和已经执行的业务事实禁止物理删除。
- 不修改已经执行过的 Auth V1–V5 和 Core V1；新增能力使用新迁移。
- PostgreSQL SQL 必须兼容 12.1，不能使用更高版本才支持的语法。
- 阶段 2–5 不引入 RabbitMQ、MinIO、pgvector、RAG、MRP、APS、WIP、线边仓或完整财务。

## 3. 总体实施策略

采用“一个总设计、五个顺序批次、逐批验收”的方式：

```text
S0 权限链路收口
  ↓
S2 主数据、库存内核、调拨与盘点
  ↓
S3 采购、到货、质检处置与上架
  ↓
S4 销售、自动预留、直接拣货与发货
  ↓
S5 BOM、工艺、工单、生产执行与库存衔接
  ↓
阶段 2–5 联合回归
```

S0 是后续业务接口开放的强制门槛。S2 必须先冻结库存应用接口，S3–S5 才能基于该接口开发。允许多个子代理并行准备 DTO、测试样板、页面和文档，但不能绕过依赖关系并行修改尚未冻结的核心接口。

## 4. 模块结构与接缝

`platform-core` 建议按以下包组织：

```text
com.ailearn.platform.core
├─ masterdata
│  ├─ controller
│  ├─ application
│  ├─ domain
│  ├─ infrastructure
│  └─ dto
├─ inventory
│  ├─ controller
│  ├─ application
│  ├─ domain
│  ├─ infrastructure
│  └─ dto
├─ purchasing
├─ sales
├─ manufacturing
├─ quality
└─ common
   └─ idempotency
```

核心接缝位于 `inventory.application`。采购、销售、制造、调拨和盘点调用库存应用接口，不引用库存 Mapper，不操作 `inv_inventory_balance`。

### 4.1 库存查询接口

`InventoryQueryService` 对调用方提供：

- 按租户、商品、仓库、库位、批次查询库存余额；
- 查询业务预留和预留分配；
- 查询来源明确的库存流水；
- 返回 `onHandQty`、`reservedQty`、`availableQty` 和业务可分配量 `allocatableQty`。

`availableQty` 永远等于 `onHandQty - reservedQty`。`QualityHold` 等受限库位即使算术可用量大于零，`allocatableQty` 仍返回零。这样既保留统一库存公式，也避免把质量隔离货物错误解释为可销售或可领料库存。

### 4.2 库存命令接口

`InventoryCommandService` 只暴露以下类型化命令：

- `increase`：收货、生产退料、成品入库和盘盈；
- `decrease`：发货、领料、质量退回、报废和盘亏；
- `move`：质量放行、上架、调拨、直接拣货和拣货退回；
- `reserve`：建立业务预留与库位分配；
- `release`：主动释放或随发货释放预留；
- `moveReservationAllocation`：拣货或退回时同步迁移有效预留分配。

每个命令必须携带：

- 来源类型、来源单据 ID、来源明细 ID；
- 商品、仓库、库位和标准化批次；
- 数量、交易类型和业务发生时间；
- 当前租户、操作用户、会话 JTI 和请求 ID；
- 幂等键与请求载荷摘要。

接口返回统一的 `InventoryMutationResult`，包含受影响余额、预留变化、位置变化和新生成的流水编号。调用方只能使用返回结果更新自己的履约累计，不能推测库存写入结果。

## 5. S0：权限链路收口

### 5.1 权限数据流

```text
登录
  ↓
Auth 从 PostgreSQL 查询当前有效权限集合
  ↓
严格写入 auth:perms:{tenantId}:{userId}
  ↓
写入 auth:session:{tenantId}:{userId} = jti
  ↓
签发并返回只含最小身份信息的 JWT

业务请求
  ↓
Gateway 清除客户端传入的内部身份 Header
  ↓
Gateway 验签 JWT、校验有效 jti
  ↓
Gateway 注入可信身份 Header
  ↓
Core/IoT 的 platform-shared 按 tenantId + userId 读取 Redis 权限
  ↓
构造 Spring Security Authentication
  ↓
业务方法执行 hasAuthority(...)
```

### 5.2 JWT 与 Header

JWT 只保留：

- `sub`：用户 ID；
- `jti`：会话 ID；
- `tenant_id`：租户 ID；
- `username`：展示和审计所需账号名；
- 签发时间和过期时间。

删除 JWT 中角色、权限和 authorities 的生成与兼容读取逻辑。Gateway 不读取权限，也不写权限 Header。

保留的下游身份 Header：

- `X-User-Id`
- `X-Tenant-Id`
- `X-Username`
- `X-Session-Id`
- `X-Request-Id`

移除：

- `X-Authorities`
- `X-Permissions`

Gateway 对所有请求，包括白名单请求，必须先删除客户端提交的上述内部身份 Header。受保护请求只有在 JWT 和 Redis 当前会话校验通过后才能重新注入身份。白名单请求只保留或生成 `X-Request-Id`，不注入用户身份。

部署和本地编排只对外暴露 Gateway 端口 `20001`；Core、Auth、IoT 端口不得作为外部入口。可信 Header 的信任成立于“外部流量只能经过 Gateway”这一网络约束。

### 5.3 Redis 权限上下文

键：

```text
auth:perms:{tenantId}:{userId}
```

值使用 JSON 字符串数组。空权限集合必须写为 `[]`，并被识别为有效命中；Redis 返回 `null` 才表示未命中。

权限缓存 TTL 与当前会话剩余 TTL 一致，不能固定为短于 Token 生命周期的 30 分钟，否则合法会话会在权限缓存过期后被锁死。权限刷新时读取 `auth:session:*` 的剩余 TTL，并用相同 TTL 重建权限键；不存在有效会话时不创建权限缓存。

登录顺序固定为：

1. 校验租户、账号、密码和状态；
2. 查询 PostgreSQL 权限事实；
3. 生成新 JTI 和最小 JWT；
4. 写入数据库会话事实；
5. 严格写入权限缓存；
6. 最后发布 Redis 当前有效 JTI；
7. 全部成功后返回 Token。

权限缓存写入、读取、删除异常均不得吞掉。登录期间失败返回 `503`，不向客户端返回新 Token。

### 5.4 权限变更一致性

角色权限、用户角色、角色状态或用户状态发生变化时：

1. 在数据库变更前解析受影响用户集合；
2. 严格删除这些用户的权限缓存；删除失败则拒绝本次授权变更；
3. 执行数据库事务；
4. 事务成功后为仍有有效会话的用户重建权限缓存；
5. 重建失败时保持缓存缺失，使业务请求 Fail-Closed；用户可在 Redis 恢复后重新登录完成自恢复。

该顺序优先保证撤权立即安全，不允许旧权限在数据库撤销后继续生效。授权增加期间短暂缓存缺失只会拒绝请求，不会扩大权限。

### 5.5 `platform-shared` 安全模块

共享安全模块的对外接口保持尽量小：Core 和 IoT 只需导入共享安全配置并声明 `@PreAuthorize`，不感知 Redis 序列化、键格式和异常映射。

共享模块内部包含：

- Redis 权限上下文读取实现；
- 下游身份和权限恢复过滤器；
- Spring Security `Authentication` 构造；
- 统一 401、403、503 响应；
- 测试使用的内存权限读取适配器。

删除重复的 Header 权限解析路径，避免 `DownstreamSecurityFilter` 与 `HeaderContextInterceptor` 分别构造不同权限上下文。身份、租户、权限、MDC 和 SecurityContext 必须由同一条过滤链建立并在 `finally` 中清理。

`SharedSecurityConfig` 调整为：

- 健康检查、明确的内部端点和 OpenAPI 文档按配置放行；
- 其他请求使用 `authenticated()`；
- 方法级授权开启；
- 不存在有效身份时返回 401；
- 权限缓存不可用时在进入业务方法前返回 503。

### 5.6 Fail-Closed 语义

| 场景 | HTTP | 结果 |
| --- | ---: | --- |
| Token 缺失、无效、过期 | 401 | Gateway 拒绝 |
| 当前 Redis JTI 缺失或不匹配 | 401 | Gateway 判定会话失效 |
| 下游身份 Header 缺失或格式非法 | 401 | `platform-shared` 拒绝 |
| 权限 Redis 未命中 | 503 | 不构造认证权限，不进入业务方法 |
| 权限 Redis 连接、超时或 JSON 解析异常 | 503 | 不回源数据库、不使用本地旧缓存 |
| 权限键有效命中但不含所需权限 | 403 | `hasAuthority(...)` 拒绝 |
| 权限键有效命中且权限集合为空 | 403 | 正常认证但无业务权限 |

### 5.7 业务方法授权规范

所有 Auth 管理方法以及 Core、IoT 业务应用方法统一使用：

```java
@PreAuthorize("hasAuthority('inv:transfer:confirm')")
```

禁止新增：

- `hasRole(...)`；
- `hasPermission(...)`；
- 在 Controller 中手写权限集合判断；
- 根据用户名或固定账号特判；
- 从请求 Header 直接读取权限；
- 把角色编码当成业务操作权限。

角色继续用于权限聚合、菜单展示和用户资料，不作为业务方法的直接授权表达式。

## 6. S2：主数据、库存内核、调拨与盘点

### 6.1 主数据范围

阶段 2 一次完成以下主数据：

| 实体 | 核心字段 | 关键约束 |
| --- | --- | --- |
| `Uom` | code、name、precision、status | 租户内有效编码唯一 |
| `Product` | sku、name、spec、category、uom_id、batch_managed、status | 租户内有效 SKU 唯一 |
| `Customer` | customer_code、name、contact、status | 租户内有效编码唯一 |
| `Supplier` | supplier_code、name、contact、status | 租户内有效编码唯一 |
| `Warehouse` | warehouse_code、name、warehouse_type、status | 租户内有效编码唯一 |
| `Location` | warehouse_id、location_code、name、location_type、status | 租户和仓库内有效编码唯一 |

库位类型固定为：`ReceivingStaging`、`Storage`、`Picking`、`ShippingStaging`、`QualityHold`、`Adjustment`。

被业务事实引用的主数据不得物理删除。停用商品、仓库或库位不影响历史查询，但不得被新业务单据或库存命令选用。仓库停用前必须确认不存在启用库位；库位停用前必须确认实物和预留均为零。

### 6.2 主数据接口

沿用现有规格的简洁路由：

- `/api/uoms`
- `/api/products`
- `/api/customers`
- `/api/suppliers`
- `/api/warehouses`
- `/api/locations`

每类主数据提供分页查询、详情、创建、修改和状态变更。阶段 2 不提供物理删除接口；如保留删除语义，只能执行受约束的逻辑删除。

### 6.3 库存表

Core V2 至少创建：

- `inv_inventory_balance`
- `inv_inventory_reservation`
- `inv_inventory_reservation_allocation`
- `inv_inventory_transaction`
- `inv_transfer_order`
- `inv_transfer_order_line`
- `inv_stocktake_order`
- `inv_stocktake_order_line`
- `core_idempotency_record`

数量统一使用 `NUMERIC(19,6)`，禁止使用浮点类型。

库存维度固定为：

```text
tenant_id + product_id + warehouse_id + location_id + lot_no
```

持久层将“无批次”标准化为空字符串，`lot_no` 使用 `NOT NULL DEFAULT ''`，从而保证唯一约束和并发创建余额行的确定性；接口层仍可把空字符串映射为 `null`。

`inv_inventory_balance` 必须满足：

- `on_hand_qty >= 0`；
- `reserved_qty >= 0`；
- `reserved_qty <= on_hand_qty`；
- `available_qty = on_hand_qty - reserved_qty`；
- 同一库存维度只能有一行有效余额；
- `version` 每次库存或预留变化递增；
- `last_transaction_at` 指向最近一次已完成库存事实的发生时间。

`available_qty` 建议使用 PostgreSQL 12.1 支持的 stored generated column，消除应用层重复计算导致的漂移；若实际迁移验证表明与当前 ORM 映射不兼容，则改为只读查询表达式，但不允许由普通业务代码独立写入。

### 6.4 库存流水

流水只追加、不修改。每条流水包含：

- `transaction_no` 和 `transaction_type`；
- 来源类型、来源 ID、来源明细 ID；
- 商品、批次、来源仓库/库位、目标仓库/库位；
- 业务数量和对企业实物总量的有符号影响；
- 操作用户、会话 JTI、请求 ID、幂等键；
- 业务发生时间和记录时间。

位置移动使用一条含来源和目标的流水表达；核对单个库位时，来源视为负向、目标视为正向。纠错只能生成反向或调整流水，不能更新原流水。

### 6.5 并发与幂等

余额更新使用数据库行锁或带版本条件的更新。涉及两个余额维度的移动命令按稳定排序锁定来源和目标，避免交叉调拨死锁。不存在的目标余额行通过唯一约束和受控重试创建。

幂等唯一键：

```text
tenant_id + operation_code + idempotency_key
```

首次请求保存请求载荷哈希和最终响应摘要：

- 相同键、相同载荷：返回首次成功结果；
- 相同键、不同载荷：返回 409；
- 前次处理中：返回明确的处理中冲突；
- 业务事务失败：不保留伪成功记录。

现有 `platform-shared` 内存幂等存储只能用于单元测试，不能作为阶段 2–5 的生产幂等实现。

### 6.6 调拨

调拨状态固定为：

```text
Draft -> Confirmed
```

创建调拨单只表达意图，不改变库存。确认调拨时：

1. 校验调拨单为 `Draft`；
2. 校验来源和目标属于当前租户且均启用；
3. 校验来源与目标不同、商品和批次一致；
4. 校验来源 `available_qty` 足够；
5. 同一事务减少来源实物、增加目标实物；
6. 写入调拨流水；
7. 调拨单进入 `Confirmed`。

普通调拨只移动未预留库存，不迁移预留分配。`QualityHold` 和 `Adjustment` 不允许通过普通调拨绕过质量处置或受控调整规则。

### 6.7 盘点

盘点状态固定为：

```text
NotStarted -> Counting -> ConfirmedAdjusted
```

创建盘点单时确定仓库、库位或商品范围。开始盘点时保存每条盘点明细的系统数量、批次和余额版本快照，但不长时间锁表。

确认时：

1. 校验状态为 `Counting`；
2. 校验每行已填写非负实盘数量；
3. 有差异时要求填写差异原因；
4. 校验当前余额版本仍等于开始盘点时的快照版本；
5. 若版本变化，返回盘点快照失效错误，要求重新开始盘点；
6. 校验实盘数量不能低于当前有效预留数量；
7. 按 `counted_qty - system_qty` 生成盘盈或盘亏调整流水；
8. 无差异时只保存确认事实，不生成数量流水；
9. 全部成功后进入 `ConfirmedAdjusted`。

阶段 2 的期初演示库存通过受控盘点调整或测试数据建立，不提供直接修改余额接口。

## 7. S3：采购、到货、质检处置与上架

### 7.1 业务实体

- `PurchaseOrder`、`PurchaseOrderLine`
- `PurchaseReceipt`、`PurchaseReceiptLine`
- `PurchaseQualityInspection`
- `PurchaseQualityDisposition`
- `PutawayTask`

### 7.2 状态与关键规则

采购单：

```text
Draft -> Submitted -> Approved -> PartiallyReceived -> Completed
```

`Completed` 可由全部实际收货或人工完成进入。人工完成只终止剩余未收货数量，不补造库存事实，也不阻止已收货货物继续质检、处置和上架。

收货确认：

```text
arrived_qty = rejected_qty + received_qty
```

- `rejected_qty` 不进入库存、不增加累计已收货；
- `received_qty` 通过库存 `increase` 一次性进入 `QualityHold`；
- 允许全部拒收，此时不产生库存流水；
- 质检只记录质量事实，不改变库存。

处置采用“业务决定 + 仓库执行确认”：

- 生产质检人员决定放行或报废；
- 采购人员决定退回供应方；
- 仓库人员确认实际执行；
- 放行调用库存 `move`，从 `QualityHold` 到 `ReceivingStaging`；
- 退回和报废调用库存 `decrease`；
- 上架调用库存 `move`，从 `ReceivingStaging` 到合法存储库位。

每次收货、检验、决定、执行和上架均使用独立幂等键。

## 8. S4：销售、自动预留、直接拣货与发货

### 8.1 业务实体

- `SalesOrder`、`SalesOrderLine`
- `PickTask`
- `SalesShipment`、`SalesShipmentLine`

### 8.2 状态与数量规则

销售生命周期：

```text
Draft -> Submitted -> Approved -> Completed
```

履约进度由数量动态派生，不持久化。订单行始终满足：

```text
0 <= shipped_qty <= picked_qty <= reserved_qty <= ordered_qty
```

直接拣货必须在同一数据库事务内：

1. 锁定销售订单行和来源库存余额；
2. 使用已有未拣预留；
3. 不足部分调用库存 `reserve` 自动补足；
4. 调用库存 `move` 把实物移至 `ShippingStaging`；
5. 调用 `moveReservationAllocation` 同步迁移预留分配；
6. 更新订单行累计数量和拣货任务；
7. 任一步失败全部回滚。

发货确认调用库存 `decrease` 和 `release`，减少发货暂存位实物并释放对应有效预留。一个销售订单允许多次拣货和多次发货。

人工完成前必须保证 `picked_qty = shipped_qty`。存在未发货暂存数量时先执行拣货退回；人工完成只释放剩余未拣预留，不补造拣货或发货事实。

## 9. S5：BOM、工艺、工单和制造执行

### 9.1 主数据与业务实体

- `Bom`、`BomLine`
- `Routing`、`RoutingOperation`
- `Operation`
- `WorkCenter`
- `WorkOrder`
- `DispatchOrder`
- `OperationExecution`
- `WorkReport`
- `QualityInspection`
- `MaterialIssue`、`MaterialIssueLine`
- `MaterialReturn`、`MaterialReturnLine`
- `FinishedGoodsReceipt`、`FinishedGoodsReceiptLine`

### 9.2 工单和执行边界

- 工单表达生产意图，可选关联一个来源销售订单行；同一销售行可以拆分为多个工单。
- 工单、派工、工序执行、报工和质检分别保存事实，不能合并成一个万能状态。
- 工单审核通过时锁定 BOM 和工艺版本快照，后续主数据修改不回写已下达工单。
- 派工表达人员、设备、工序和数量安排；工序执行记录实际开始、暂停、恢复和完成事实。
- 阶段 5 不实现自动排产、返工、WIP 和线边仓。

### 9.3 库存衔接

- 生产质检人员创建领料、退料和成品入库业务单据；
- 仓库人员确认实物变化；
- 领料调用库存 `decrease`；
- 退料调用库存 `increase`；
- 合格成品入库调用库存 `increase`；
- 每次库存变化关联工单、单据和明细并生成不可变流水；
- 制造模块不得注入 Inventory Mapper 或直接更新库存表。

## 10. 权限目录

权限编码统一使用冒号分段。阶段 2 新增或补齐：

```text
inv:uom:view
inv:uom:manage
inv:product:view
inv:product:manage
inv:customer:view
inv:customer:manage
inv:supplier:view
inv:supplier:manage
inv:warehouse:view
inv:warehouse:manage
inv:location:view
inv:location:manage
inv:balance:view
inv:reservation:view
inv:transaction:view
inv:transfer:view
inv:transfer:create
inv:transfer:confirm
inv:stocktake:view
inv:stocktake:create
inv:stocktake:start
inv:stocktake:confirm
```

阶段 3–5 优先复用 Auth V2 已存在的 `pur:*`、`sales:*`、`mes:*` 权限编码；只通过 Auth V6 补缺或修正文案，不改历史迁移。原型、测试和 Java 注释中出现的 `inventory.transfer.confirm`、`inventory.stocktake.adjust` 等点号编码统一改为正式冒号编码。

租户管理员默认只拥有查看权限，不自动成为业务超级用户。仓库人员负责调拨确认、盘点确认和各种实物执行；主数据管理权限按既有角色职责分配，不通过用户名特判。

## 11. 数据库迁移

### 11.1 Auth

- `V6__complete_stage_2_5_permissions.sql`
  - 补齐阶段 2 权限点；
  - 补齐 `inv:location:view` 等已有角色关联但权限字典缺失的项目；
  - 为既有角色分配阶段 2 所需权限；
  - 保持权限编码全局唯一和软删除规则；
  - 不修改 V1–V5。

### 11.2 Core

- `V2__master_data_inventory_transfer_stocktake.sql`
- `V3__purchasing_receipt_quality_putaway.sql`
- `V4__sales_reservation_pick_shipment.sql`
- `V5__manufacturing_execution_inventory_links.sql`

每个迁移只创建本阶段拥有的表、索引、约束和必要演示数据，不跨阶段提前创建空泛表。所有外键都必须验证租户一致性，应用层继续做当前租户校验；不能仅依赖 UUID 全局唯一假设替代租户隔离。

迁移验证必须同时覆盖：

- 全新数据库从 Core V1 顺序执行到 V5；
- 已有 Core V1 数据库逐版本升级；
- PostgreSQL 12.1 真实执行；
- 连续启动时迁移不重复执行；
- 失败迁移不连接或污染当前开发数据库 `127.0.0.1:5433/ai_learn`。

## 12. 错误处理

沿用统一 `ApiResponse`，并补充稳定业务错误码：

| 范围 | 示例错误 |
| --- | --- |
| Security | 权限上下文缺失、Redis 不可用、无 Authority |
| Master Data | 编码重复、主数据停用、被业务引用不能删除 |
| Inventory | 库存不足、预留不足、库存维度非法、版本冲突 |
| Transfer | 状态不允许、来源目标相同、受限库位非法调拨 |
| Stocktake | 状态不允许、快照版本失效、差异原因缺失、实盘低于预留 |
| Purchasing | 到货数量关系错误、处置超量、库位类型非法 |
| Sales | 履约数量超限、暂存货物未退回、并发预留失败 |
| Manufacturing | 工单状态错误、BOM/工艺未冻结、确认数量超限 |

所有命令在业务校验、余额更新、流水追加或单据状态更新任一步失败时整体回滚，不返回部分成功。

## 13. 测试策略与阶段门槛

### 13.1 S0 门槛

- 登录成功后权限键已存在，TTL 与会话一致；空权限集合保存为 `[]`。
- 权限缓存写失败时登录返回 503，不返回 Token。
- Core/IoT 不接收 `X-Authorities` 或 `X-Permissions`。
- Gateway 会清除客户端伪造的内部身份 Header。
- 权限缓存未命中、Redis 异常、非法 JSON 均不进入业务方法。
- `hasAuthority` 成功返回业务结果，缺少 Authority 返回 403。
- 全仓业务代码不存在新增的 `hasRole`、`hasPermission` 或权限 Header 判断。

### 13.2 S2 门槛

- 六类主数据按租户隔离，编码唯一和停用规则生效。
- `available_qty` 永远等于 `on_hand_qty - reserved_qty` 且不小于零。
- 调拨不改变企业总库存，失败不会只更新单边余额。
- 盘点差异只在确认时入账，同一幂等键不重复调整。
- 盘点期间余额版本变化会阻止使用过期快照覆盖库存。
- 库存流水可以核对余额变化，普通接口不能直接修改余额。

### 13.3 S3 门槛

- 拒收数量不进入库存；实际接收数量只增加一次库存。
- 质检和处置决定不改变库存；执行确认才移位或扣减。
- 上架只移动位置，不重复增加企业总库存。
- 采购人工完成不伪造库存事实，也不阻断已收货货物后续处理。

### 13.4 S4 门槛

- 并发直接拣货不能使可用库存为负。
- 自动预留、实物移动、预留分配移动和订单累计在一个事务内完成。
- 发货才扣减实物，并同步释放对应预留。
- 分批拣货、分批发货和人工完成满足所有订单行数量不变量。

### 13.5 S5 门槛

- 工单意图、派工、执行、报工和质检事实可区分。
- 工单下达后使用冻结的 BOM 和工艺版本。
- 领料、退料和成品入库只能通过库存应用接口产生余额和流水。
- 制造模块测试能够证明其未直接修改库存 Mapper 或库存表。

### 13.6 联合回归

- 后端模块单元测试和接口测试。
- 隔离 PostgreSQL 12.1 上的 Flyway V1–V5 迁移测试。
- Redis 权限异常和权限撤销回归。
- 前端构建。
- 从采购收货到生产领料、成品入库，再到销售拣货发货的阶段 2–5 联合场景。
- `git diff --check`、连续两次后端测试和最终改动范围检查。

## 14. Gemini 多子代理协作方案

### 14.1 角色划分

| 子代理 | 独占范围 | 依赖 |
| --- | --- | --- |
| A：安全链路 | `platform-shared` 安全代码、Gateway 过滤器、Auth 权限缓存、Auth V6、安全测试 | 无，最先完成 |
| B：主数据 | `core/masterdata/**`、主数据 DTO/Mapper/接口及测试 | A 的鉴权契约 |
| C：库存内核 | `core/inventory` 的余额、预留、流水、命令接口、并发和幂等测试 | A；必须先于 D/E/F/G 冻结接口 |
| D：调拨盘点 | 调拨、盘点领域代码、接口和测试 | C 已冻结的库存接口 |
| E：采购 | `core/purchasing/**`、采购质量处置及测试 | B、C |
| F：销售 | `core/sales/**`、预留/拣货/发货及并发测试 | B、C |
| G：MES | `core/manufacturing/**`、生产质量、领退料/成品入库及测试 | B、C；销售来源接口冻结 |
| H：前端与文档 | 阶段 2–5 API 接入、页面、原型和正式文档同步 | 各后端接口冻结后分批进入 |
| I：集成审核 | POM、应用配置、迁移顺序、跨模块回归和最终缺口检查 | 所有批次 |

### 14.2 并行规则

第一波可并行：

- A 完成权限链路；
- B 完成主数据领域设计和独立文件；
- C 完成库存接口与数据模型；
- H 依据已确认契约准备前端类型和测试样板，但不抢先固化未冻结接口。

第二波在 C 冻结后并行：

- D 实现调拨盘点；
- E 实现采购；
- F 实现销售；
- G 实现 MES 的非销售来源部分。

第三波顺序集成：

- F 冻结销售订单来源查询接口；
- G 完成工单来源销售行关联；
- H 分阶段接入真实接口；
- I 执行迁移、构建和跨域回归。

### 14.3 文件所有权

- 同一时刻一个文件只能由一个子代理修改。
- A 独占 `HeaderConstants`、共享安全配置、Gateway 认证过滤器、Auth 会话缓存实现和 Auth V6。
- C 独占库存应用接口、库存余额 Mapper、库存事务与 Core 幂等实现；其他代理只能调用，不能扩展方法或修改实现。
- Core V2–V5 迁移分别由对应阶段代理编写，但最终迁移顺序、外键和命名由 I 统一审核；不得多人同时修改同一迁移文件。
- POM、`application.yml`、根 README、项目级架构文档和最终权限矩阵由 I 合并，其他代理只提交明确的变更建议。
- H 独占前端路由、公共 API 类型和原型同步，避免多个业务代理同时改前端入口。

### 14.4 子代理交付格式

每个子代理交付时必须说明：

1. 修改文件清单；
2. 实现的接口和不变量；
3. 未修改的模块边界；
4. 已执行测试及结果；
5. 尚未验证的事项；
6. 对下游代理的稳定接口；
7. 是否发现与其他代理改动冲突。

任何子代理不得宣称整个阶段完成，只能报告自己负责的切片。阶段完成结论由集成审核统一给出。

## 15. 文档与原型同步

实现期间至少同步检查：

- `README.md`
- `backend/README.md`
- `frontend/README.md`
- `docs/词汇定义表.md`
- `docs/specs/00-project/正式项目计划.md`
- `docs/specs/00-project/架构设计.md`
- `docs/specs/00-project/阶段决策与续聊入口.md`
- `docs/specs/10-erp-wms/概述.md`
- `docs/specs/10-erp-wms/领域模型.md`
- `docs/specs/10-erp-wms/接口契约.md`
- `docs/specs/10-erp-wms/库存业务规则.md`
- `docs/specs/10-erp-wms/验收标准.md`
- `docs/specs/20-mes/*`
- `docs/prototype/README.md`
- `docs/prototype/pages/master-data.html`
- 采购、销售、工单相关原型页

原型中的点号权限编码、盘点状态 `InProgress/Adjusted` 和正式规格中的 `Counting/ConfirmedAdjusted` 必须统一。实际代码完成前继续保留“目标设计/接口未实现”标记；完成并取得验证证据后才能改为已实现。

## 16. 明确不做

- 不把阶段 6 IoT 设备、MQTT、遥测、状态和告警提前到本轮。
- 不实现阶段 7 追溯、GIS 和综合看板的完整能力，只保留阶段 2–5 来源字段。
- 不实现阶段 8 AI 工具。
- 不宣称完成阶段 9 端到端验收；本轮只执行阶段 2–5 联合回归。
- 不建立万能业务关系表、万能库存调整接口或通用工作流引擎。
- 不允许前端直接提交 `tenant_id`、操作账号、会话 ID 或库存余额。
- 不允许任何业务模块绕过库存应用接口。

## 17. 完成定义

本方案只有在以下证据全部取得后，才能报告阶段 2–5 实现完成：

1. S0、S2、S3、S4、S5 各自阶段门槛全部通过；
2. PostgreSQL 12.1 隔离环境迁移通过；
3. Redis 未命中和异常的 Fail-Closed 用例通过；
4. 后端聚合测试和打包通过；
5. 前端构建通过；
6. 阶段 2–5 联合业务场景通过；
7. 正式规格、原型、权限目录和实际接口一致；
8. 未直接提交 Git，未修改 Auth V1–V5 或 Core V1，未操作当前开发数据库。
