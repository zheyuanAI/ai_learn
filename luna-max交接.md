# Luna Max 后端逻辑交接与实施计划（阶段 2–7）

> 执行角色：Luna Max，负责安全链路、数据库、后端领域逻辑、接口契约和后端测试。  
> 协作对象：Gemini 只负责界面，详见根目录 `gemini交接.md`。  
> 基线日期：2026-09-03。  
> Git 约束：只修改工作区，不执行 `git commit`、`git push`、建分支或合并，由用户统一审查。  
> 事实原则：代码与实际配置优先于原型和计划；未在仓库确认的能力不得写成“已实现”。

## 1. 可直接交给 Luna Max 的角色提示词

```text
你是本项目阶段 2–7 的后端逻辑负责人。只负责 backend/**、必要的 deploy/** 和后端领域规格，不负责 frontend/** 或 docs/prototype/**。

先完整阅读根 AGENTS.md、正式项目计划、词汇定义表、架构设计，以及对应领域的概述、领域模型、接口契约、业务规则和验收标准。先检查现有代码、调用链、配置和未提交改动，再按本文 S0→S2→S3→S4→S5→S6→S7 顺序实施。

每个写接口必须落实：租户隔离、hasAuthority(...)、状态校验、幂等、并发保护、事务边界和稳定错误码。采购、销售、制造、调拨和盘点不得直接修改库存余额，只能调用库存应用接口。Core/IoT 只能依赖 platform-shared，不得依赖 platform-auth。

采用测试先行：先写失败测试，运行确认失败，再写最小实现，运行确认通过。新增方法用中文注释说明用途、出入参和流程；修改已有方法在改动处用中文说明用途。只做当前任务所需改动，不批量格式化，不修改历史 Flyway 文件，不连接或污染开发库。

每完成一个 READY-Sn 闸门，向 Gemini 交付冻结接口清单、字段、枚举、权限、错误码、示例请求/响应和验证证据。没有达到 READY 状态，不允许让前端按猜测接入。

不提交 Git。交付时逐项列出修改文件、测试命令、测试结果、遗留风险和下一阶段可依赖的稳定契约。
```

## 2. 角色边界与文件所有权

### 2.1 Luna Max 独占

- `backend/platform-shared/**`
- `backend/platform-gateway/**`
- `backend/platform-auth/**`
- `backend/platform-core/**`
- `backend/platform-iot/**`
- `backend/pom.xml` 与后端各模块 `pom.xml`
- 后端运行所必需的 `deploy/**`、`runtime/README.md`
- `docs/specs/10-erp-wms/**`
- `docs/specs/20-mes/**`
- `docs/specs/30-iot-digital-twin/**`
- `docs/specs/40-gis-dashboard/**`

### 2.2 Luna Max 禁止修改

- `frontend/**`
- `docs/prototype/**`
- Gemini 已经开始修改的任何界面文件
- Auth V1–V5、Core V1、IoT V1 等已执行迁移
- `D:\AI\ai_learn_wms_ai\ai_learn_referenceProjects/**`

### 2.3 集成人独占的共享入口

以下文件 Luna Max 只提交“建议改动清单”，不得与 Gemini 同时落盘：

- `README.md`
- `AGENTS.md`
- `docs/词汇定义表.md`
- `docs/specs/00-project/正式项目计划.md`
- `docs/specs/00-project/架构设计.md`
- `docs/specs/00-project/阶段决策与续聊入口.md`

### 2.4 与 Gemini 的唯一协作协议

| 闸门 | Luna Max 必须交付 | Gemini 才能开始 |
| --- | --- | --- |
| `READY-S0` | 登录/权限响应、401/403/503 语义、菜单和权限码 | 权限态与错误态界面 |
| `READY-S2` | 主数据、库存、调拨、盘点 OpenAPI 与枚举 | 阶段 2 页面真实接入 |
| `READY-S3` | 采购/收货/质检/处置/上架契约 | 阶段 3 页面真实接入 |
| `READY-S4` | 销售/预留/拣货/发货契约 | 阶段 4 页面真实接入 |
| `READY-S5` | MES/BOM/工艺/工单/执行契约 | 阶段 5 页面真实接入 |
| `READY-S6` | 设备/遥测/状态/告警契约 | 阶段 6 页面真实接入 |
| `READY-S7` | 追溯/GIS/七类看板契约 | 阶段 7 页面真实接入 |

冻结后如必须破坏性修改，先发布变更说明，列出旧字段、新字段、影响页面和迁移方式；不得静默修改 DTO。

## 3. 必读事实源与环境

开始前按顺序阅读：`AGENTS.md`、`docs/specs/00-project/正式项目计划.md`、`docs/词汇定义表.md`、`docs/specs/00-project/架构设计.md`，以及各领域的 `概述.md`、`领域模型.md`、`接口契约.md`、`业务规则.md`、`验收标准.md`。

固定基线：Java 21、Maven 3.9.1、Spring Boot 3.3.5、PostgreSQL 12.1、Redis、Mosquitto；Gateway/Auth/Core/IoT 端口分别为 20001/10002/10003/10004。

当前确认事实：

- Core 和 IoT 只有应用入口、健康检查和 V1 基线，阶段 2–7 业务尚未实现。
- Core/IoT 已只依赖 `platform-shared`，没有 Auth Maven 依赖。
- Gateway 不再主动写 `X-Authorities`，但共享过滤器、常量、旧上下文路径和测试仍有残留。
- Auth 已有权限键读写能力，但登录未正确预热，异常语义也未满足 Fail-Closed。
- 前端和 HTML 原型均为目标设计，不是已实现证据。

## 4. 总执行顺序

```text
S0 权限链路收口
  ↓
S2 主数据 + 库存内核 + 调拨 + 盘点
  ↓
S3 采购 + 到货 + 质检处置 + 上架
  ↓
S4 销售 + 预留 + 拣货 + 发货
  ↓
S5 BOM + 工艺 + 工单 + 领退料 + 成品入库
  ↓
S6 设备 + MQTT + 遥测 + 状态 + 告警
  ↓
S7 追溯 + 二维 GIS + 综合看板
  ↓
阶段 2–7 联合回归
```

S2 未冻结库存命令接口前，S3–S5 只能准备测试和领域模型；S5 未冻结生产上下文查询前，S6 不绑定 Core；S2–S6 查询端口未冻结前，S7 不直接查询各模块表。

## 5. 通用后端规则

- 所有业务实体含 `tenant_id`，从可信请求上下文获取，客户端不能覆盖。
- 跨租户资源按不可见处理，不泄露是否存在。
- 数量统一 `NUMERIC(19,6)`/`BigDecimal`，不使用浮点数。
- 状态迁移写在应用服务中，Controller 不能直接拼状态。
- 核心表逻辑删除；库存流水和已执行事实只追加。
- 生产幂等唯一键为 `tenant_id + operation_code + idempotency_key`。
- 同键同载荷返回首次结果；同键不同载荷返回 409；事务失败不留伪成功。
- 库存余额用行锁或版本条件；双余额移动按稳定顺序加锁。
- 统一 `ApiResponse`；详情/命令响应返回后端计算的状态、累计数量、版本和 `allowedActions`。
- 页面不得自行推导可执行状态，所以 Luna Max 必须把 `allowedActions` 纳入冻结契约。

## 6. S0：权限链路收口

### Task S0.1：先锁定安全测试

**修改文件：**

- `backend/platform-shared/src/test/java/com/ailearn/platform/shared/security/DownstreamSecurityFilterTest.java`
- `backend/platform-shared/src/test/java/com/ailearn/platform/shared/security/MethodSecurityIntegrationTest.java`
- `backend/platform-gateway/src/test/java/com/ailearn/platform/gateway/filter/JwtAuthGlobalFilterTest.java`
- `backend/platform-auth/src/test/java/com/ailearn/platform/auth/AuthIntegrationTest.java`

先写并确认失败：登录后权限键存在且 TTL 对齐；空权限写 `[]`；缓存写失败登录 503 且不返回 Token；伪造内部 Header 被清除；权限 Header 不能形成 Authority；Redis 未命中/异常/非法 JSON 返回 503 且业务方法调用为 0；有身份无权限返回 403；无身份返回 401。

```powershell
cd backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-shared,platform-gateway,platform-auth -am test
```

### Task S0.2：最小身份 JWT 与可信 Header

**修改文件：**

- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/constants/HeaderConstants.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/jwt/TokenPayload.java`
- `backend/platform-gateway/src/main/java/com/ailearn/platform/gateway/filter/JwtAuthGlobalFilter.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/security/jwt/JwtTokenService.java`

JWT 只保留 `sub`、`jti`、`tenant_id`、`username`、签发和过期。下游仅允许 `X-User-Id`、`X-Tenant-Id`、`X-Username`、`X-Session-Id`、`X-Request-Id`。彻底移除 `X-Authorities`、`X-Permissions`。Gateway 对所有请求先剥离客户端内部身份 Header；白名单只保留/生成请求 ID。

### Task S0.3：登录预热和权限刷新

**修改文件：**

- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/SessionCacheService.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/impl/RedisSessionCacheServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/impl/AuthServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/RoleAdminServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/UserAdminServiceImpl.java`

权限键固定为 `auth:perms:{tenantId}:{userId}`，值为 JSON 字符串数组。登录顺序：校验 → 查询 DB 权限 → 生成 JTI/JWT → 保存数据库会话 → 严格写权限键 → 发布当前 JTI → 返回 Token。任何 Redis 异常不得吞掉。

撤权/授权变更前严格删除受影响权限键；数据库事务完成后仅按有效会话剩余 TTL 重建。重建失败保持缺失，让业务 Fail-Closed。

### Task S0.4：`platform-shared` 统一恢复 Authority

**修改/新增文件：**

- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/DownstreamSecurityFilter.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/SharedSecurityConfig.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/interceptor/HeaderContextInterceptor.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/config/SharedAutoConfiguration.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/PermissionContextReader.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/RedisPermissionContextReader.java`

`null` 是未命中；`[]` 是认证成功但无权限。未命中、连接失败、超时、反序列化失败统一 503，不查 DB、不用旧值。身份、租户、MDC、SecurityContext 由一条过滤链建立并在 `finally` 清理。除健康/文档/内部签名端点外使用 `authenticated()`，同时开启方法安全。

### Task S0.5：统一业务授权

业务应用方法统一：

```java
@PreAuthorize("hasAuthority('inv:transfer:confirm')")
```

禁止 `hasRole`、`hasPermission`、Controller 手写判断、用户名特判和 Header 权限判断。

新增 `backend/platform-auth/src/main/resources/db/migration/auth/V6__complete_stage_2_7_permissions.sql`，补齐 S2–S7 冒号权限码，不修改 V1–V5。

**READY-S0：**安全测试全绿；全仓无权限 Header 残留和新增旧式权限表达式；交付登录/用户信息、401/403/503 示例。

## 7. S2：主数据、库存内核、调拨与盘点

### Task S2.1：Core V2

新增 `backend/platform-core/src/main/resources/db/migration/core/V2__master_data_inventory_transfer_stocktake.sql`。

创建 UOM、商品、客户、供应商、仓库、库位及：`inv_inventory_balance`、`inv_inventory_reservation`、`inv_inventory_reservation_allocation`、`inv_inventory_transaction`、调拨单/明细、盘点单/明细、`core_idempotency_record`。

库存维度：`tenant_id + product_id + warehouse_id + location_id + lot_no`；无批次持久化为 `''`。数据库保证 `on_hand_qty >= 0`、`reserved_qty >= 0`、`reserved_qty <= on_hand_qty`。先写 PostgreSQL 迁移测试，验证 12.1 的全新与 V1→V2 升级。

### Task S2.2：六类主数据

新增 `backend/platform-core/src/main/java/com/ailearn/platform/core/masterdata/{controller,application,domain,infrastructure,dto}/**` 及 `backend/platform-core/src/test/java/com/ailearn/platform/core/masterdata/**`。

实现分页、详情、创建、修改和启停。编码租户内唯一；被引用不可物理删除；库位停用前实物和预留为零。库位类型固定：`ReceivingStaging`、`Storage`、`Picking`、`ShippingStaging`、`QualityHold`、`Adjustment`。

### Task S2.3：库存唯一写内核

新增 `backend/platform-core/src/main/java/com/ailearn/platform/core/inventory/**` 及对应测试。

冻结 `InventoryCommandService`：`increase`、`decrease`、`move`、`reserve`、`release`、`moveReservationAllocation`；冻结 `InventoryQueryService`：余额、预留、分配、流水。

命令携带来源单据/明细、库存维度、数量、交易类型、业务时间、租户、用户、JTI、请求 ID、幂等键和载荷摘要。返回 `InventoryMutationResult`。

不变量：`availableQty = onHandQty - reservedQty`；`QualityHold.allocatableQty = 0`；流水只追加；位置移动不改变企业总库存。

### Task S2.4：调拨和盘点

调拨状态 `Draft -> Confirmed`；创建不动库存，确认同事务完成双边移动和流水；普通调拨不迁移预留，不允许通过受限库位绕过质量规则。

盘点状态 `NotStarted -> Counting -> ConfirmedAdjusted`；开始保存数量和版本快照，确认时版本变化即拒绝；实盘不能低于有效预留；差异必须有原因；只用库存命令生成盘盈/盘亏。

**READY-S2：**租户、余额公式、双边原子性、盘点快照、幂等和并发测试通过；交付 `/api/uoms`、`/api/products`、`/api/customers`、`/api/suppliers`、`/api/warehouses`、`/api/locations` 及库存/调拨/盘点 OpenAPI。

## 8. S3：采购、到货、质检处置与上架

新增：

- `backend/platform-core/src/main/resources/db/migration/core/V3__purchasing_receipt_quality_putaway.sql`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/purchasing/**`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/quality/**`
- 对应测试包

实体：采购单/明细、收货/明细、采购质检、处置、上架任务。采购状态 `Draft -> Submitted -> Approved -> PartiallyReceived -> Completed`。

规则：`arrivedQty = rejectedQty + receivedQty`；拒收不入库；实收只 `increase` 一次进入 `QualityHold`；质检/处置决定不动库存；仓库执行放行 `move` 到 `ReceivingStaging`；退供/报废 `decrease`；上架只 `move`。人工完成不补造库存，也不阻断已收货货物后续处理。

**READY-S3：**全拒收、部分/分批收货、放行、退供、报废、上架、人工完成测试通过；交付状态、累计数量、权限和 `allowedActions`。

## 9. S4：销售、预留、拣货与发货

新增：

- `backend/platform-core/src/main/resources/db/migration/core/V4__sales_reservation_pick_shipment.sql`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/sales/**`
- 对应测试包

实体：销售单/明细、拣货任务、发货单/明细。销售状态 `Draft -> Submitted -> Approved -> Completed`；履约进度由数量派生。

```text
0 <= shippedQty <= pickedQty <= reservedQty <= orderedQty
```

直接拣货一个事务内：锁订单行和余额 → 使用已有预留 → 不足自动 `reserve` → `move` 至 `ShippingStaging` → 迁移预留分配 → 更新累计和任务。发货才 `decrease` 实物并 `release` 预留。人工完成前必须 `pickedQty = shippedQty`，暂存未发先退回。

**READY-S4：**并发不超卖，分批拣/发、退回、人工完成和幂等测试通过；交付列表/详情/命令及库存分配字段。

## 10. S5：MES、BOM、工艺、工单与库存衔接

新增：

- `backend/platform-core/src/main/resources/db/migration/core/V5__manufacturing_execution_inventory_links.sql`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/manufacturing/**`
- 对应测试包

实体：BOM/明细、工艺/工序、工序、工作中心、工单、派工、工序执行、报工、生产质检、领料/退料、成品入库。

- 工单可选关联一个销售行，同一销售行可拆多个工单。
- 审核通过时冻结 BOM 和工艺版本快照。
- 工单、派工、执行、报工、质检分别保存事实。
- 执行保存开始、暂停、恢复、完成；不合并万能状态。
- 领料只 `decrease`，退料和合格成品入库只 `increase`；制造包禁止注入 Inventory Mapper。
- 阶段 5 不实现自动排产、返工、WIP、线边仓。

冻结供 IoT 使用的内部只读生产上下文接口：按租户、设备和告警时间查询唯一活动 `OperationExecution`/`WorkOrder` 摘要；使用服务身份 HMAC，不复用用户 Header。

**READY-S5：**冻结版本、工单状态、领退料、成品入库、销售来源软关联和生产上下文查询测试通过。

## 11. S6：IoT 设备、MQTT、遥测、状态与告警

新增：

- `backend/platform-iot/src/main/resources/db/migration/iot/V2__device_mqtt_telemetry_status_alarm.sql`
- `backend/platform-iot/src/main/java/com/ailearn/platform/iot/{device,mqtt,telemetry,alarm,contextlink}/**`
- 对应测试包

V2 至少含设备模型/指标、设备、凭证、消息去重、遥测、当前状态、规则、告警、上下文任务。只支持 MQTT。设备生命周期 `Active <-> Disabled`；在线和运行状态独立。凭证 `PendingProvision -> Active/ProvisionFailed -> Revoked`。

修改 `deploy/docker-compose.yml` 和经 `runtime/README.md` 确认的 Mosquitto 配置：关闭匿名；每设备只可发布 `devices/{device_code}/telemetry`；IoT 使用独立只读订阅账号；明文密码仅创建成功响应显示一次，不入库、不写日志。

MQTT 和开发/测试 simulate 共用 `TelemetryIngestionService.ingest(...)`。消息含 `ts`、非空 `metrics`、`message_id` 或 `sequence`。优先 `device_id + message_id` 去重，否则 `device_id + sequence`；同键同哈希幂等，同键不同哈希返回 `IOT_TLM_003` 并审计。

保存顺序：完整校验 → 消息事实 → 全部遥测 → 较新消息更新状态 → 告警触发/维持/恢复 → 提交 IoT 事务 → 提交后补上下文。Core 故障不能回滚 IoT 事实。

告警：`Triggered -> Acked -> Recovered` 或 `Triggered -> RecoveredUnacked -> Recovered`；恢复只由设备事实驱动；同设备同规则最多一个活动告警。

权限：`iot:device:view/manage`、`iot:telemetry:view`、`iot:alarm:view/ack/context`、`iot:device:simulate`。MQTT 设备认证不得伪装用户 Authority。

**READY-S6：**凭证 ACL、撤销、QoS1 重复、载荷冲突、延迟消息、离线、告警回差、Core 不可用和上下文重试测试通过。

## 12. S7：追溯、二维 GIS 与综合看板

新增：

- `backend/platform-core/src/main/resources/db/migration/core/V6__traceability_gis_dashboard.sql`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/{traceability,gis,dashboard}/**`
- 对应测试包

只通过 `InventoryFactsQuery`、`PurchasingFactsQuery`、`SalesFactsQuery`、`ManufacturingFactsQuery`、`QualityFactsQuery` 和远程 `IotFactsPort` 读取；禁止跨模块 Mapper 和跨服务直查表。

追溯从销售单、工单、库存流水或设备告警进入，沿真实来源字段双向构造节点。采用 `trace:chain:view` 加节点领域权限双重过滤；无权节点只返回计数；缺失来源明确标记，不建万能关系表。

GIS 表：`gis_site_map`、`gis_site_map_asset`、`gis_map_point`。底图存 PostgreSQL `BYTEA`，仅 PNG/JPEG/WebP，最大 5 MiB，保存 MIME、大小、SHA-256。点位坐标 0–100，实体限仓库、生产区域、设备。点位只存配置，状态优先级 `Alarm > Offline > Warning > Normal`。

看板固定库存、履约、制造、质量、设备、告警、追溯七类摘要；范围仅 `today`、`7d`、`30d`。Redis 按租户、权限指纹、摘要、筛选、时间范围隔离：新鲜 60 秒，最近成功最多陈旧 10 分钟；来源失败可返回带时间的 `stale=true`，无旧结果报错，绝不伪造零值。

**READY-S7：**追溯权限裁剪、完整性缺口、GIS 跨租户、状态优先级、七类聚合、部分失败和陈旧缓存测试通过。

## 13. 权限目录

S2 至少补齐：

```text
inv:uom:view / inv:uom:manage
inv:product:view / inv:product:manage
inv:customer:view / inv:customer:manage
inv:supplier:view / inv:supplier:manage
inv:warehouse:view / inv:warehouse:manage
inv:location:view / inv:location:manage
inv:balance:view
inv:reservation:view
inv:transaction:view
inv:transfer:view / inv:transfer:create / inv:transfer:confirm
inv:stocktake:view / inv:stocktake:create / inv:stocktake:start / inv:stocktake:confirm
```

S3–S6 优先复用已有 `pur:*`、`sales:*`、`mes:*`、`iot:*`，缺项才在 Auth V6 增加。S7：`trace:chain:view`、`gis:map:view`、`gis:map:manage`、`dashboard:view`。

## 14. 后端验证矩阵

```powershell
cd backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd test
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd package
```

还须取得：PostgreSQL 12.1 隔离迁移；Redis Fail-Closed；Core/IoT POM 无 Auth；全仓无权限 Header、旧点号权限和业务 `hasRole`；Mosquitto ACL/QoS1；S2–S7 租户/权限/幂等/并发/回滚；`git diff --check`。不得碰用户已有 `.run` 改动。

## 15. 交付给 Gemini 的固定格式

```text
闸门：READY-Sn
接口清单：
请求字段与校验：
响应字段与可空性：
状态/枚举：
权限码：
错误码与 HTTP 状态：
allowedActions：
分页/筛选/排序：
示例请求/响应：
OpenAPI 地址或导出文件：
测试命令与结果：
已知限制：
```

Gemini 可提出契约问题，但不能直接改后端 DTO；Luna Max 评估、修改并重新发布闸门。

## 16. 完成定义

只有 `READY-S0`、`READY-S2` 至 `READY-S7` 全部发布且有测试证据，迁移在 PostgreSQL 12.1 隔离环境通过，关键不变量通过自动化回归，规格与真实接口一致，并确认未提交 Git、未改历史迁移、未污染开发库，才可报告后端完成。

阶段 8 AI、阶段 9 完整黄金闭环验收、RabbitMQ、MinIO、MRP、APS、WIP、线边仓、Modbus/OPC UA、三维数字孪生均不在本计划范围。
