# Luna Max 后端逻辑交接与实施计划（阶段 2–7）

> 执行角色：Luna Max，负责安全链路、数据库、后端领域逻辑、接口契约和后端测试。  
> 协作对象：Gemini 只负责界面，详见根目录 `gemini交接.md`。  
> 基线日期：2026-09-04；本次冻结以当前代码、配置和下文最终交接节为准。
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

GIS 表：`gis_site_map`、`gis_site_map_asset`、`gis_map_point`。底图保存外部资源 `storage_key` 及 MIME、大小、SHA-256 元数据，仅 PNG/JPEG/WebP、最大 5 MiB。点位坐标 0–100，实体限仓库、生产区域、设备。点位只存配置，状态优先级 `Alarm > Offline > Warning > Normal`。

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

## 17. 2026-09-05 最终后端冻结交接（权威覆盖前文目标性描述）

本节是本轮阶段 2–7 修复后的权威交接；前文仍保留为实施背景，但其中与当前 Controller、DTO、应用服务、迁移脚本或本节冲突的“目标接口”不再作为 Gemini 接入依据。未在本节或对应领域 `接口契约.md` 列出的路由，不对外承诺。

### 17.1 闸门状态

| 闸门 | 当前状态 | 证据/边界 |
| --- | --- | --- |
| `READY-S2` | 代码闸门与 5433 实库联调通过 | 调拨/盘点分页、详情、库存查询契约、库存余额和黄金路径已在 `127.0.0.1:5433/ai_learn` 验证；隔离数据库迁移未验证 |
| `READY-S3` | 代码闸门与 5433 实库联调通过 | 独立收货 ID、质量隔离、放行、上架和拒收边界已在当前练习库验证；多次拆分收货的独立事实已由回归覆盖 |
| `READY-S4` | 代码闸门与 5433 实库联调通过 | 直接拣货、库存预留、发货扣减、订单完成和允许动作已通过 Gateway 实链路；复杂多批次并发仍以自动化回归为主 |
| `READY-S5` | 代码闸门与 5433 实库联调通过 | BOM/工艺/工单/派工/执行/报工/质检/领退料/成品入库和工单完成已形成真实闭环；生产上下文正向关联未构造专用设备执行样本 |
| `READY-S6` | 代码闸门与 5433 实库模拟接入通过 | 设备、凭证、遥测、状态、告警、同载荷去重、冲突、确认/恢复和延迟消息已通过受控 simulate；真实 Broker/ACL/QoS1 仍未验证 |
| `READY-S7` | 代码闸门与 5433 实库联调通过 | BFS 真实适配器、反向追溯、GIS 创建/幂等/更新/软删除、七类看板和异常中心已通过；HMAC 负向上下文边界已验证，正向上下文仍未构造专用样本 |

`READY-S0` 的既有基线未重做；本轮没有修改登录、JWT、租户上下文和权限核心实现。阶段 2–7 已完成当前 5433 练习库的业务联调，但不能把未执行的 PostgreSQL 12.1 隔离迁移、真实 Mosquitto 或生产上下文正向样本写成已完成环境验收。

### 17.2 通用接口冻结

- HTTP 成功和业务错误统一返回 `{code,message,data,request_id,timestamp}`；成功 `code=200`，时间为 ISO-8601，`request_id` 与 `X-Request-Id` 对应。
- UUID 使用标准字符串；S2–S4 数量 DTO 使用字符串并按最多 6 位小数校验；制造摘要和看板指标中的 `BigDecimal` 为 JSON 数字。
- 分页 DTO 使用 `records,total,page,size,totalPages`（设备和告警现有分页 DTO 的实际返回为 `records,total,page,size`）；页码从 1 开始，服务端页大小上限 200。库存预留记录是 `{reservation,allocations}`，Java 内部 `content()` 不出现在 HTTP。
- 写接口带 `Authorization` 和 `Idempotency-Key`；客户端不得提交或覆盖 `tenantId/tenant_id`、操作人、会话和审计时间。相同租户、操作域、幂等键和载荷重放首次结果，不同载荷返回冲突。
- 所有下文字段名以当前 JSON DTO 为准：有 `@JsonProperty` 的字段使用其声明名，未声明的 Java DTO 使用默认 camelCase；已有 `@JsonAlias` 只表示反序列化兼容，不扩展正式 URL 或查询参数。

### 17.3 S2 最终 URL、参数和响应

正式路由：

- `GET /api/transfers?page=1&size=20&keyword=&status=`、`GET /api/transfers/{id}`、`POST /api/transfers`、`POST /api/transfers/{id}/confirm`
- `GET /api/stocktakes?page=1&size=20&keyword=&status=`、`GET /api/stocktakes/{id}`、`POST /api/stocktakes`、`POST /api/stocktakes/{id}/start`、`POST /api/stocktakes/{id}/confirm`
- `GET /api/inventory/balances?product_id=&warehouse_id=&location_id=&lot_no=&page=1&size=50`
- `GET /api/inventory/reservations?reservation_id=&source_type=&source_id=&source_line_id=&status=&product_id=&warehouse_id=&location_id=&lot_no=&page=1&size=50`
- `GET /api/inventory/transactions?transaction_type=&source_type=&source_id=&source_line_id=&product_id=&warehouse_id=&location_id=&lot_no=&occurred_from=&occurred_to=&page=1&size=50`

调拨请求为 `transferNo,fromWarehouseId,fromLocationId,toWarehouseId,toLocationId,lines[{productId,lotNo,uom,quantity}]`；响应为 `TransferView`，含 `id,transferNo,fromWarehouseId,fromLocationId,toWarehouseId,toLocationId,status,version,confirmedBy,confirmedAt,lines,transactionIds,allowedActions`，状态 `Draft/Confirmed`，允许动作分别为 `confirm/[]`。盘点请求为 `stocktakeNo,warehouseId,locationId`；确认体为 `lines[{lineId,countedQty,varianceReason}]`，这是唯一记录入口；响应 `StocktakeView` 含系统快照版本、实盘/差异和调整流水，状态 `NotStarted/Counting/ConfirmedAdjusted`，允许动作分别为 `start/confirm/[]`。差异确认以余额版本锁校验，数量不能低于有效预留。

### 17.4 S3 最终 URL、参数和响应

- `POST /api/purchase-receipts/{receiptId}/confirm`：`{purchaseOrderId,receiptNo,receiptTime,qualityHoldLocationId,lines[{purchaseOrderLineId,productId,uom,arrivedQty,rejectedQty,receivedQty,lotNo,rejectionReason}]}`；路径 `receiptId` 是独立收货事实 ID，订单 ID 只取请求体 `purchaseOrderId`。
- `GET /api/purchase-orders`、`GET /api/purchase-orders/{id}`、`POST /api/purchase-orders`、`PUT /api/purchase-orders/{id}`、`POST /api/purchase-orders/{id}/submit`、`approve`、`complete`；质量为 `GET /api/purchase-receipts/quality-inspections`、`POST /api/purchase-receipts/{id}/quality/inspect`、`GET /api/purchase-quality-dispositions`、`POST /api/purchase-receipts/{id}/quality/{release|return|scrap}`、`POST /api/purchase-quality-dispositions/{id}/confirm`；上架为 `GET /api/putaway-tasks`、`POST /api/putaway-tasks/{id}/confirm`。
- 收货响应为 `PurchaseReceiptView`，含 `id,receiptNo,purchaseOrderId,receiptTime,qualityHoldLocationId,status,confirmedBy,confirmedSessionId,confirmedAt,version,lines,arrivalAcceptanceSummary,balanceDeltaSummary,inventoryTransactions,allowedActions`；收货状态 `Draft/Confirmed`。
- 约束：`arrivedQty=rejectedQty+receivedQty`；拒收不产生库存，实际接收只进入 `QualityHold`；质量决定不等于仓库执行，放行后才从 `QualityHold` 移到 `ReceivingStaging`，上架再从暂存位移动到 `Storage`。同一订单的拆分收货必须使用不同 `receiptId`。

### 17.5 S4 最终 URL、参数和响应

- `GET /api/pick-tasks?page=1&size=20&keyword=&status=&customerId=&fulfillmentStatus=` 返回 `SalesOrderPageResult`；当前没有独立持久化 `pick_task` 或 shipment header。
- `POST /api/pick-tasks/{id}/confirm` 请求 `salesOrderId,lines[{salesOrderLineId,pickedQty,sourceLocationId,shippingLocationId}]`；`POST /api/pick-tasks/{id}/return` 请求 `salesOrderId,lines[{salesOrderLineId,returnQty,toLocationId}]`。
- `POST /api/sales-orders/{id}/reservations/release` 请求 `releaseLines[{salesOrderLineId,releaseQty,reason}]`；`POST /api/sales-shipments/{id}/confirm` 请求 `salesOrderId,shipTime,shipmentLines[{salesOrderLineId,productId,shipQty}]`；`POST /api/sales-orders/{id}/complete` 请求 `completionReason`。
- 写响应为 `SalesFulfillmentResult{action,operationId,order,inventoryTransactionIds,reservationIds}`。路径 `{id}` 是履约操作标识，体内 `salesOrderId` 是订单标识；不提供无 ID 的旧通用动作路由。订单详情 `allowedActions` 只使用 `directPick,ship,returnPick,releaseReservation,manualComplete`，已完成订单为空。
- 直接拣货先自动补足预留并把实物/有效分配移到 `ShippingStaging`，不扣企业总实物；发货才释放预留并扣实物；退回只处理未发货暂存；人工完成只释放未拣预留并留存原因。

### 17.6 S5 最终 URL、参数和响应

正式读路由为 `GET /api/boms`、`/api/boms/{id}`、`/api/routings`、`/api/routings/{id}`、`/api/work-orders`、`/api/work-orders/{id}`、`/api/dispatch-orders`、`/api/operation-executions`、`/api/work-reports?work_order_id=`、`/api/work-reports/{workOrderId}`、`/api/quality-inspections?work_order_id=`、`/api/quality-inspections/{workOrderId}`、`/api/finished-goods-receipts?work_order_id=`、`/api/finished-goods-receipts/{workOrderId}`；正式写路由为 `POST /api/dispatch-orders`、`POST /api/operation-executions`、`POST /api/material-issues`、`POST /api/material-issues/{id}/confirm`、`POST /api/material-returns`、`POST /api/material-returns/{id}/confirm`、`POST /api/work-reports`、`POST /api/quality-inspections`、`POST /api/quality-inspections/{id}/submit`、`POST /api/quality-inspections/{id}/close`、`POST /api/finished-goods-receipts`、`POST /api/finished-goods-receipts/{id}/confirm`，以及工单 `POST/PUT` 生命周期路由。

核心字段：工单 `workOrderNo,productId,plannedQty,plannedStartTime,plannedFinishTime,bomId,routingId,sourceSalesOrderLineId`；派工正式字段 `work_order_id,operation_id,operator_id,dispatch_qty,device_id`；执行正式字段 `dispatch_order_id,work_order_id,operation_id,device_id`；领料 `issueNo,workOrderId,items[{productId,warehouseId,locationId,quantity}],overageReason`；报工 `reportNo,operationExecutionId,workOrderId,operationId,reportTime,qualifiedQty,defectQty,remark`；质检创建/提交/关闭分别为 `inspectionNo,workReportId,inspectionType,sampleQty`、`qualifiedQty,defectQty,result`、`disposition`；成品入库 `receiptNo,workOrderId,receiptQty,warehouseId,locationId`。列表为 `records,total,page,size,totalPages`，事实集合查询返回 JSON 数组，确认结果为 `ProductionFactSummary{operation,factId,fact,quantity,inventoryTransactionIds}`。

状态和规则：工单 `Draft/PendingApproval/Rejected/Released/InProgress/Completed`；派工 `Draft/Released/Processing/Completed`；执行 `NotStarted/Running/Paused/Completed`；质检 `Draft/Submitted/Passed/Failed/Closed`；成品入库 `Draft/Confirmed`。派工工序必须属于冻结 Routing，累计派工不超计划；后续工序必须等待前置执行 `Completed`；累计报工不超对应派工/工单计划；Failed 质检必须以 `ISOLATE/SCRAP/CLOSE` 关闭。超 BOM 原因必填，确认时还要 `mes:material:overage`，库存变化只能经 `InventoryCommandService`。

### 17.7 S6 最终 URL、参数和响应

- `PATCH /api/devices/{id}/lifecycle` 请求 `{lifecycle_status}`，返回 `DeviceView`；`GET /api/devices/{id}/telemetry?metric_code=&date_from=&date_to=&limit=100` 返回 `TelemetryFact[]`；`GET /api/devices/{id}/status` 返回 `DeviceStatus`。
- `POST /api/protocol-adapters/mqtt/simulate` 请求 `{device_code,ts,message_id,sequence,metrics[{metric_code,metric_value,metric_unit}]}`，返回 `TelemetryIngestionResult{accepted,duplicate,messageKey,telemetryIds,status}`；模拟能力默认关闭。
- `GET /api/device-alarms?device_id=&status=&alarm_level=&date_from=&date_to=&context_status=&page=1&size=20`、`GET /api/device-alarms/{id}`、`POST /api/device-alarms/{id}/ack` 请求 `{ack_comment}`、`PUT /api/device-alarms/{id}/business-context` 请求 `{operation_execution_id,work_order_id}`（至少一项）。
- 告警返回字段使用 `AlarmView` 的 `alarm_no,device_id,rule_id,alarm_type,alarm_level,status,triggered_at,acked_at,ack_user_id,recovered_at,operation_execution_id,work_order_id,context_source,context_status,ack_comment`。状态为 `Triggered/Acked/RecoveredUnacked/Recovered`；同键同载荷重复为幂等成功，同键不同载荷为 `IOT_TLM_003`。
- 告警先本地保存，Core 不可用时上下文保持 `Pending` 并进入 `Retry`；调度器固定延迟默认 5 秒、每轮最多 16 个租户、每租户 50 条到期任务，退避上限 1 小时。真实 MQTT Topic 是 `devices/{credential_reference}/telemetry`，Broker 匿名关闭，凭证文件/ACL/密码只由运行环境注入。

### 17.8 S7 最终 URL、参数和响应

- 追溯：`GET /api/traceability?entity_type=&entity_id=`，仅接受 snake_case；返回 `TraceabilityProjection{nodes,links,hidden_node_count,missing_sources,generated_at,source_updated_at,request_id,truncated}`，BFS 上限为查询 256、节点 256、关系 512。
- 地图：`GET/POST /api/site-maps`、`GET /api/site-map?site_map_id=&entity_type=&status=`、`GET /api/site-maps/{siteMapId}/projection`、`GET /api/site-map/points/{pointId}`、`POST/PUT/DELETE /api/site-map/points[/{pointId}]`。`site_map_id` 查询不再接受 `siteMapId` 别名；点位命令为 `siteMapId,entityType,entityId,xPercent,yPercent,rotation,linkedPage`，删除是 GIS 配置软删除。
- 看板只有七个：`GET /api/dashboard/inventory`、`/fulfillment`、`/manufacturing`、`/quality`、`/device`、`/alarms`、`/traceability`；查询为 `time_range=today|7d|30d,warehouse_id,production_area_id,device_id`，不提供 `/api/dashboard/overview`，不再使用 `timeRange/warehouseId/areaId/deviceId` 别名。返回 `DashboardSummaryProjection{summary_type,metrics,time_range,source_summary,generated_at,source_updated_at,stale,stale_since,request_id}`。
- 异常中心：`GET /api/exception-center?time_range=&source=&severity=&page=1&size=20`，返回 `ExceptionCenterPage{records,total,page,size,totalPages,generated_at,source_updated_at}`，记录为 `source,exception_type,severity,value,message,occurredAt`，只从库存、制造和 IoT 告警三类 Facts 派生。
- GIS/追溯/看板/异常中心均按租户和权限裁剪；`production_area` 事实源在当前仓库中未确认，不能由原型补齐。

### 17.9 本轮变更文件与迁移

- `backend/platform-core/**`：S2 调拨/盘点读接口和分页、库存余额版本锁；S3 收货契约；S4 履约路径与动作；S5 foundation/派工/执行/生产事实/质量闭环/超 BOM；S7 BFS、真实 Facts 适配器、GIS 更新删除、七类看板和异常中心；对应测试位于 `backend/platform-core/src/test/**`。
- `backend/platform-iot/**`：S6 设备/遥测/告警 HTTP 契约、自动补链、重试调度、MQTT 配置和对应测试；`backend/platform-auth/**` 增加正式 MES/S7 权限迁移。
- 新增迁移仅有 `platform-auth` `auth/V7__stage_5_formal_permissions.sql`、`platform-core` `core/V7__mes_quality_inspection_closure.sql` 和 `core/V8__mes_material_overage_reason.sql`；当前工作树同时在既有 Core `V5__manufacturing_execution_inventory_links.sql` 中补齐 PostgreSQL 12.1 所需的 `(tenant_id,id)` 唯一约束，5433 实库已存在该约束并成功加载后续迁移，未执行清库或迁移重放。
- `deploy/local/mosquitto.conf`、`deploy/docker/mosquitto.conf` 和 `runtime/README.md` 只补运行入口、匿名关闭、外部凭证文件与 ACL 占位说明，不含真实密钥。规格同步文件为四个领域的 `接口契约.md`、`领域模型.md`、`验收标准.md` 及必要业务规则文件。

### 17.10 旧 Gemini 假设删除/纠正清单

- 删除追溯 `entityType/entityId`、GIS `siteMapId` 查询别名；看板只认 snake_case 查询参数。
- 删除 `/api/dashboard/overview` 假设；只能调用七个固定摘要入口。
- 删除无 ID 的通用拣货/退回/发货假设；只能使用带路径 ID 的正式履约路由。`GET /api/pick-tasks` 是销售订单分页，不代表有独立任务表。
- 删除 `/api/stocktakes/{id}/record` 假设；盘点实盘记录嵌在 `/confirm` 请求中。
- 纠正采购收货 `{id}` 语义：路径是独立收货 ID，不是采购订单 ID；拆分到货必须产生不同收货事实 ID。
- 删除“收货直接进入正常可用库存”“质检决定等同仓储执行”“Failed 可直接结束”的假设；实际接收进 `QualityHold`，质量决定与仓储执行分离，Failed 必须闭环。
- 删除“报工只受工单计划量限制”的简化假设；现在同时受对应派工累计量和工单计划量限制，后续工序还受前置完成门控。

### 17.11 验证证据、未验证项和 Git 状态

已执行（均使用 Java 21、Maven 3.9.1 和仓库 `D:\project\MavenRepository391`；业务联调直接使用 `127.0.0.1:5433/ai_learn`）：

```powershell
cd backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd test '-Dtest=!AuthPostgresMigrationTest' '-Dcheckstyle.skip=true' '-Dmaven.repo.local=D:\project\MavenRepository391'
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd package '-DskipTests' '-Dcheckstyle.skip=true' '-Dmaven.repo.local=D:\project\MavenRepository391'
cd ..\frontend
npm run build
cd ..
git diff --check
git status --short
```

后端聚合测试最终为 `platform-shared 18 + platform-gateway 11 + platform-auth 34 + platform-core 153 + platform-iot 70 = 286` 个通过测试；`AuthPostgresMigrationTest` 未纳入本次聚合运行，因为它是可选的外部隔离迁移测试，默认目标 `127.0.0.1:55432` 未提供服务；该地址不是本项目数据库，也不是项目运行依赖。聚合 `package -DskipTests` 和前端 `npm run build` 均成功；前端 Vite 构建通过 `vue-tsc --noEmit`，共转换 280 个模块。测试日志中的 Core unavailable 告警和 MQTT listener 错误日志是专门验证故障路径的预期输出，不是测试失败。`git diff --check` 通过（仅有工作区 LF/CRLF 转换提示），`git status --short` 确认改动仍在工作区。

5433 只读核对结果为 PostgreSQL `12.1`，`auth_flyway_schema_history` 最新 `V7`、`core_flyway_schema_history` 最新 `V8`、`iot_flyway_schema_history` 最新 `V2` 且均为 `success=true`；Auth/Core/IoT/Gateway 健康端点均返回 HTTP 200。黄金流已通过 Gateway 写入并复读当前练习库：库存成品余额合计 `onHand=7,reserved=0,available=7`（Storage 5、Picking 2），工单为 `Completed` 且 `reportedQty=2,qualifiedQty=2,receivedQty=2`；IoT simulate 验证了遥测、在线/运行/告警状态、同载荷去重、同键异载荷冲突、告警确认/恢复及延迟消息不倒退；S7 追溯返回 `9 nodes/8 links/truncated=false`，七类看板均成功且 `stale=false`，异常中心成功，GIS 地图创建/幂等重放/点位创建更新/软删除均成功。开发库已保留本轮阶段 2–7 的练习数据，未清库；联调临时角色已清理。

未验证或不纳入当前练习库完成判定：认证模块代码中预留的外部隔离迁移测试（默认目标地址为 `127.0.0.1:55432`）未执行；该地址不是本项目数据库，也不是项目启动依赖。真实 PostgreSQL 隔离迁移、Mosquitto ACL/QoS1 实机链路、IoT 与生产执行事实的正向上下文关联专用样本、生产区域 Facts 源未验证；IoT 本次使用开发 profile 的 HTTP `simulate` 入口，基础配置仍默认关闭。复杂多批次并发主要由自动化回归覆盖。未执行 `git commit`、`git push`、`git reset`、`git checkout`、`git clean` 或建分支；所有改动仍留在工作区，等待用户审查。
