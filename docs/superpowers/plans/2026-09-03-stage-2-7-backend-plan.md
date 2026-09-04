# 阶段 2–7 后端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不修改前端、不污染开发库、不提交 Git 的前提下，完成 S0–S7 后端安全链路、ERP/WMS、MES、IoT、追溯、GIS 和看板能力，并以自动化测试和阶段闸门证明黄金业务闭环。

**Architecture:** 维持 Gateway、Auth、Core、IoT 四服务和 `platform-shared` 共享安全组件。采用闸门串行、互斥写集并行：S0 → S2 → S5-foundation → S3/S4 并行 → S5 执行与 S6 IoT 自有事实并行 → S6 上下文补链 → S7 → 联合回归。

**Tech Stack:** Java 21、Spring Boot 3.3.5、Spring Security、Spring Cloud Gateway、MyBatis-Plus 3.5.7、Flyway 10.10.0、PostgreSQL 12.1、Redis、Mosquitto MQTT、JUnit 5、Spring Boot Test、H2、隔离 PostgreSQL 测试实例。

---

## 全局约束

- 只修改 `backend/**`、后端运行必需的 `deploy/**`、领域规格和本计划产生的协作文件；不修改 `frontend/**`、`docs/prototype/**`、参考工程或用户已有删除改动。
- 不执行 `git commit`、`git push`、建分支、合并或 `git clean`；每个任务以工作区差异、测试输出和阶段账本记录完成状态。
- 不修改 Auth V1–V5、Core V1、IoT V1 或其他历史 Flyway 文件；新增迁移只能使用指定版本号。
- 不连接 `127.0.0.1:5433/ai_learn`；PostgreSQL 迁移测试使用已配置的隔离实例或临时兼容容器。
- 所有新增方法使用中文注释说明用途、入参、出参和简略流程；修改已有方法在改动处用中文注释说明修改用途。
- 所有写接口从可信上下文取得租户、用户、会话和请求 ID；数量在数据库为 `NUMERIC(19,6)`，Java 内部为 `BigDecimal`，HTTP 数量使用字符串。
- 业务 DTO 对外使用 `camelCase`；`ApiResponse` 继续输出实际代码约定的 `request_id`；详情和命令响应必须包含后端计算的状态、累计数量、版本和 `allowedActions`。
- 核心写命令必须实现幂等、状态校验、租户隔离、权限校验、事务边界、并发保护和稳定错误码；库存变化只能调用 `InventoryCommandService`。
- 每个实现任务完成后必须先做任务级规格/质量审查；Critical/Important 问题进入最多五轮修复复审，未关闭的负载性问题不得进入下游任务。

## 协作账本与工作区

- 计划工作区：`.superpowers/sdd/2026-09-03-stage-2-7-backend/`。
- 账本：`.superpowers/sdd/2026-09-03-stage-2-7-backend/progress.md`，首行固定为 `# SDD ledger — plan: docs/superpowers/plans/2026-09-03-stage-2-7-backend-plan.md`。
- 因项目禁止提交 Git，账本使用“任务起始 HEAD、工作区差异摘要、测试命令/结果、审查结论”替代提交哈希；不得用提交操作满足流程。
- 开始任务前运行：

```powershell
git status --short --branch
git rev-parse HEAD
```

- 只把本任务写集加入审查；保留 `docs/superpowers/specs/2026-09-03-stage-2-5-integrated-design.md` 的用户删除状态以及现有前端改动。

## 阶段依赖总览

```text
Task 1–4  S0 安全闸门
    ↓
Task 5–8  S2 主数据/库存/调拨/盘点
    ↓
Task 9    S5-foundation 生产来源只读能力
    ↓
Task 10–11 S3 采购链       ║ Task 12–13 S4 销售链
                 ↘         ↙
Task 14–16 S5 制造执行      ║ Task 17–19 S6 IoT 自有事实
                 ↓                    ↓
Task 20    S6 上下文补链与 Core 适配
                 ↓
Task 21–23 S7 追溯/GIS/看板
                 ↓
Task 24    联合回归、契约审计和最终交付
```

---

### Task 1: S0 安全失败测试与基线

**依赖:** 无。  
**写入边界:** 仅以下测试文件：

- `backend/platform-shared/src/test/java/com/ailearn/platform/shared/security/DownstreamSecurityFilterTest.java`
- `backend/platform-shared/src/test/java/com/ailearn/platform/shared/security/MethodSecurityIntegrationTest.java`
- `backend/platform-gateway/src/test/java/com/ailearn/platform/gateway/filter/JwtAuthGlobalFilterTest.java`
- `backend/platform-auth/src/test/java/com/ailearn/platform/auth/AuthIntegrationTest.java`

- [ ] **Step 1: 记录基线状态**

```powershell
cd D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-shared,platform-gateway,platform-auth -am test
```

Expected: 记录实际通过数和失败测试；若存在基线失败，写入 SDD 账本并区分为既有失败，不修改生产代码消除它。

- [ ] **Step 2: 添加 S0 失败测试**

测试必须覆盖：登录成功写入 `auth:perms:{tenantId}:{userId}` 且缓存 TTL 不超过当前会话剩余 TTL；空权限集合序列化为 `[]`；权限缓存写失败时登录返回 503 且响应不含 Token；Gateway 清除伪造 `X-Authorities`、`X-Permissions`、`X-Roles`；下游 Redis 未命中、连接异常、超时、非法 JSON 均返回 503 且业务方法调用次数为 0；身份存在但权限缺失返回 403；无身份返回 401。

- [ ] **Step 3: 运行失败测试并保存输出**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-shared,platform-gateway,platform-auth -am test
```

Expected: 新增的安全测试至少有一项失败，失败原因明确对应尚未实现的安全行为；将完整命令、失败测试名和输出摘要记录到账本。

- [ ] **Step 4: 检查写集**

```powershell
git status --short -- backend/platform-shared backend/platform-gateway backend/platform-auth
```

Expected: 只有四个测试文件出现本任务新增改动。

---

### Task 2: S0 最小 JWT、Gateway 可信 Header 与路由前置安全

**依赖:** Task 1 的失败测试。  
**写入边界:**

- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/constants/HeaderConstants.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/jwt/TokenPayload.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/jwt/JwtUtils.java`
- `backend/platform-gateway/src/main/java/com/ailearn/platform/gateway/filter/JwtAuthGlobalFilter.java`
- `backend/platform-gateway/src/main/resources/application.yml`
- `backend/platform-gateway/src/test/java/com/ailearn/platform/gateway/filter/JwtAuthGlobalFilterTest.java`

- [ ] **Step 1: 固化 TokenPayload 字段**

保留 `sub`、`jti`、`tenantId`、`username`、`issuedAt`、`expiresAt` 等最小身份字段；删除或拒绝角色、权限集合进入共享 Token 载荷。为新增或修改的方法补中文用途/参数/流程注释。

- [ ] **Step 2: 固化 Header 白名单和客户端清理**

允许下游身份 Header 仅为 `X-User-Id`、`X-Tenant-Id`、`X-Username`、`X-Session-Id`、`X-Request-Id`。Gateway 对所有请求先移除客户端传入的身份/权限 Header，再在 JWT 签名、有效期、当前 JTI 和租户字段通过后注入可信值；白名单请求仅保留/生成请求 ID。

- [ ] **Step 3: 补齐 IoT/GIS 目标路由**

在现有路由配置上只增加目标契约所需的路径映射：`/api/devices/**`、`/api/device-profiles/**`、`/api/device-alarm-rules/**`、`/api/device-alarms/**`、`/api/protocol-adapters/**`、`/api/site-maps/**`、`/api/site-map/**` 和 `/api/dashboard/**`/`/api/traceability/**` 的现有映射校验，不删除健康检查或已有路由。

- [ ] **Step 4: 验证 Gateway 测试**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-gateway -am test -Dtest=JwtAuthGlobalFilterTest
```

Expected: JWT 最小载荷和伪造 Header 清理测试通过；若路由断言失败，只调整当前 Gateway 配置对应项。

---

### Task 3: S0 共享下游权限恢复与 Fail-Closed

**依赖:** Task 2。  
**写入边界:**

- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/DownstreamSecurityFilter.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/SharedSecurityConfig.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/PermissionContextReader.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/RedisPermissionContextReader.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/config/SharedAutoConfiguration.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/security/UserAuthenticationToken.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/interceptor/HeaderContextInterceptor.java`
- `backend/platform-shared/src/main/java/com/ailearn/platform/shared/config/WebMvcConfig.java`
- `backend/platform-shared/src/test/java/com/ailearn/platform/shared/security/RedisPermissionContextReaderTest.java`
- 对应 `platform-shared/src/test/java` 安全测试文件

- [ ] **Step 1: 删除旧权限来源**

停止 `DownstreamSecurityFilter`、`HeaderContextInterceptor`、`RequestContext` 或 `UserContext` 从 `X-Authorities`、`X-Permissions`、`X-Roles` 恢复权限；保留身份上下文和请求追踪所需的可信 Header。不得新增兼容旧权限 Header 的旁路。

- [ ] **Step 2: 实现权限读取结果语义**

`PermissionContextReader` 返回三态结果：有效权限集合、已认证但无权限的空集合 `[]`、基础设施异常。Redis 未命中、连接失败、超时、非法 JSON、非数组 JSON 和反序列化错误均抛出可被统一处理的 503 异常，不回源数据库、不复用旧缓存。

- [ ] **Step 3: 建立 SecurityContext 生命周期**

过滤链先建立 `UserContext`、`TenantContextHolder`、MDC 和 `UserAuthenticationToken`，再执行方法安全；在 `finally` 清理所有 ThreadLocal、MDC 和 `SecurityContextHolder`。普通业务请求使用 `authenticated()`，健康、文档和签名内部端点按白名单放行。

- [ ] **Step 4: 验证 Fail-Closed**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-shared -am test -Dtest=DownstreamSecurityFilterTest,MethodSecurityIntegrationTest
```

Expected: 401/403/503 语义与 Task 1 测试一致，Redis 异常时业务方法调用次数为 0。

---

### Task 4: S0 Auth 登录预热、权限刷新、后台方法授权与 READY-S0

**依赖:** Task 3。  
**写入边界:**

- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/SessionCacheService.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/impl/RedisSessionCacheServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/impl/InMemorySessionCacheServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/impl/AuthServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/security/filter/JwtAuthenticationFilter.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/RoleAdminServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/UserAdminServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/MenuAdminServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/PermissionAdminServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/TenantAdminServiceImpl.java`
- `backend/platform-auth/src/main/java/com/ailearn/platform/auth/controller/admin/*.java`
- `backend/platform-auth/src/main/resources/db/migration/auth/V6__complete_stage_2_7_permissions.sql`
- `backend/platform-auth/src/test/java/com/ailearn/platform/auth/AuthIntegrationTest.java`
- `backend/platform-auth/src/test/java/com/ailearn/platform/auth/AdminManagementIntegrationTest.java`
- `backend/platform-auth/src/test/java/com/ailearn/platform/auth/AuthMigrationScriptTest.java`
- `backend/platform-auth/src/test/java/com/ailearn/platform/auth/AuthPostgresMigrationTest.java`
- `backend/platform-auth/src/test/java/com/ailearn/platform/auth/RedisSessionCacheServiceImplTest.java`
- `backend/platform-auth/src/test/resources/data-h2.sql`

- [ ] **Step 1: 实现严格权限缓存 API**

固定键格式 `auth:perms:{tenantId}:{userId}`，值为 JSON 字符串数组；写入、读取、删除和 TTL 异常全部向上抛出。 `JwtAuthenticationFilter` 删除权限缓存回源数据库逻辑，权限缓存缺失直接交由共享安全链路返回 503。

- [ ] **Step 2: 修改登录顺序**

按“校验账号密码 → 查询数据库权限 → 生成 JTI/JWT → 保存数据库会话 → 严格写权限缓存 → 发布当前 JTI → 返回 Token”顺序实现；缓存写失败时事务/登录响应失败，不返回 Token。权限缓存 TTL 取当前有效会话剩余 TTL 的最小值。

- [ ] **Step 3: 修改授权刷新顺序**

角色、权限、用户角色变化前删除受影响权限键；数据库事务完成后按有效会话剩余 TTL 重建。重建失败保持缓存缺失并返回明确 503，不吞异常。为后台管理应用服务补充 `@PreAuthorize("hasAuthority('...')")`，禁止 `hasRole`、`hasPermission`、用户名特判和 Controller 手写权限判断。

- [ ] **Step 4: 添加 Auth V6 权限目录迁移**

只新增 S2–S7 冒号权限码，包括 `inv:*`、`pur:*`、`sales:*`、`mes:*`、`iot:*`、`trace:chain:view`、`gis:map:view`、`gis:map:manage`、`dashboard:view`；不修改 V1–V5。迁移测试必须检查重复权限码和既有租户数据兼容。

- [ ] **Step 5: 验证 S0**

```powershell
cd D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-shared,platform-gateway,platform-auth -am test
```

Expected: S0 安全测试全绿；另执行：

```powershell
rg -n "X-Authorities|X-Permissions|X-Roles|hasRole\(|hasPermission\(" platform-shared platform-gateway platform-auth/src/main
```

Expected: 生产代码不再以权限 Header 或旧式方法表达式作为授权来源。完成后写入 `READY-S0` 报告，包含登录、用户信息、401/403/503 示例、权限码、错误码和测试结果。

---

### Task 5: S2 Core V2 PostgreSQL 迁移与隔离迁移测试

**依赖:** READY-S0。  
**写入边界:**

- `backend/platform-core/src/main/resources/db/migration/core/V2__master_data_inventory_transfer_stocktake.sql`
- `backend/platform-core/src/test/java/com/ailearn/platform/core/migration/CorePostgresMigrationTest.java`
- `backend/platform-core/src/test/java/com/ailearn/platform/core/migration/CoreMigrationScriptTest.java`

- [ ] **Step 1: 编写迁移失败测试**

测试检查 V1 → V2 升级和全新 V2 结构存在：`md_uom`、`md_product`、`md_customer`、`md_supplier`、`md_warehouse`、`md_location`、`inv_inventory_balance`、`inv_inventory_reservation`、`inv_inventory_reservation_allocation`、`inv_inventory_transaction`、`inv_transfer_order`、`inv_transfer_order_line`、`inv_stocktake_order`、`inv_stocktake_order_line`、`core_idempotency_record`；所有核心表有 `tenant_id`；库存数量为 `NUMERIC(19,6)`；余额约束保证 `on_hand_qty >= 0`、`reserved_qty >= 0`、`reserved_qty <= on_hand_qty`。

- [ ] **Step 2: 运行隔离 PostgreSQL 测试确认失败**

```powershell
cd D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest=CorePostgresMigrationTest,CoreMigrationScriptTest
```

Expected: 测试在 V2 文件尚不存在时失败或明确报告缺少结构；不得连接 `127.0.0.1:5433/ai_learn`。

- [ ] **Step 3: 编写 V2 迁移**

使用 PostgreSQL 12.1 支持的 DDL；每个核心实体包含租户、状态、审计时间和逻辑删除字段；库存交易与已执行事实只追加；无批次使用空字符串并纳入唯一维度；使用租户范围内业务编码唯一约束和外键/检查约束表达可确认的同表关系。

- [ ] **Step 4: 运行迁移测试**

Expected: 全新数据库与 V1→V2 升级均通过；约束违反时事务回滚，不留半个表或部分索引。

---

### Task 6: S2 六类主数据应用服务与 API

**依赖:** Task 5。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/masterdata/`、`backend/platform-core/src/test/java/com/ailearn/platform/core/masterdata/`，以及对应 OpenAPI 文档注释；不得写 inventory 目录。

- [ ] **Step 1: 添加失败测试**

分别为 UOM、Product、Customer、Supplier、Warehouse、Location 测试分页、详情、创建、修改、启停、租户隔离、编码租户内唯一、逻辑删除过滤、被引用不可物理删除、QualityHold/ReceivingStaging/Storage/Picking/ShippingStaging/Adjustment 类型校验和停用库位实物/预留必须为零。

- [ ] **Step 2: 运行主数据测试确认失败**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*MasterData*"
```

Expected: 缺少服务和表映射时失败。

- [ ] **Step 3: 实现分层文件**

创建 `controller`、`application`、`domain`、`infrastructure`、`dto` 五层；每个写应用服务方法先取得可信租户/用户，再校验状态和引用，最后通过 Mapper 在事务内写入。所有 Controller 只负责入参绑定、调用应用服务和包装 `ApiResponse`。

- [ ] **Step 4: 实现查询与命令**

提供 `/api/uoms`、`/api/products`、`/api/customers`、`/api/suppliers`、`/api/warehouses`、`/api/locations` 的分页/详情/创建/修改/启停，使用字符串数量字段和 `allowedActions`。跨租户资源按不可见处理。

- [ ] **Step 5: 验证主数据**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*MasterData*"
```

Expected: 主数据测试通过，生产代码无物理删除核心实体。

---

### Task 7: S2 InventoryCommandService / InventoryQueryService 唯一库存写内核

**依赖:** Task 6。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/inventory/`、`backend/platform-core/src/test/java/com/ailearn/platform/core/inventory/`。

- [ ] **Step 1: 添加核心失败测试**

测试 `increase`、`decrease`、`move`、`reserve`、`release`、`moveReservationAllocation` 的租户、数量、库位类型、QualityHold 可分配量、幂等同载荷、幂等冲突、余额行锁、双余额稳定锁顺序、回滚和 `availableQty = onHandQty - reservedQty` 不变量。

- [ ] **Step 2: 运行测试确认失败**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Inventory*"
```

- [ ] **Step 3: 冻结接口类型**

在 `application` 包定义：

```java
InventoryMutationResult increase(InventoryIncreaseCommand command);
InventoryMutationResult decrease(InventoryDecreaseCommand command);
InventoryMutationResult move(InventoryMoveCommand command);
InventoryMutationResult reserve(InventoryReserveCommand command);
InventoryMutationResult release(InventoryReleaseCommand command);
InventoryMutationResult moveReservationAllocation(InventoryAllocationMoveCommand command);
InventoryBalancePage queryBalances(InventoryBalanceQuery query);
InventoryReservationPage queryReservations(InventoryReservationQuery query);
InventoryTransactionPage queryTransactions(InventoryTransactionQuery query);
```

所有命令携带来源单据/明细、库存维度、数量、交易类型、业务时间、租户、用户、JTI、请求 ID、幂等键和载荷摘要；方法注释说明入参、出参和事务流程。

- [ ] **Step 4: 实现持久化和事务**

用 `@Transactional` 包住余额、预留/分配和流水写入；对余额按稳定维度顺序加行锁，失败抛出领域错误；库存交易只追加；`InventoryMutationResult` 返回余额、分配、流水和版本。

- [ ] **Step 5: 验证并检查越权写入**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Inventory*"
rg -n "Inventory(Balance|Reservation)|inv_inventory_balance|InventoryMapper" platform-core/src/main/java/com/ailearn/platform/core --glob '*.java'
```

Expected: 只有 inventory 应用服务和其 infrastructure 层写库存表；采购、销售、制造目录尚未出现直接 Inventory Mapper 注入。

---

### Task 8: S2 调拨、盘点与 READY-S2

**依赖:** Task 7。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/{transfer,stocktake}/`、对应测试和领域接口契约增量。

- [ ] **Step 1: 添加失败测试**

测试调拨 `Draft -> Confirmed)、来源可用量、来源/目标不同、双边原子移动、跨租户拒绝、幂等；测试盘点 `NotStarted -> Counting -> ConfirmedAdjusted`、版本快照、版本冲突、预留不能低于实盘、差异原因、无差异不生成流水、重复确认幂等。

- [ ] **Step 2: 运行测试确认失败**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Transfer*,*Stocktake*"
```

- [ ] **Step 3: 实现调拨和盘点应用服务**

调拨确认只通过 `InventoryCommandService.move`；盘点确认根据 `countedQty - systemQty` 调用受控调整命令；所有命令返回后端状态、版本、流水标识和 `allowedActions`。

- [ ] **Step 4: 验证 S2**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test
git diff --check
```

Expected: S2 迁移、主数据、库存、调拨、盘点和不变量测试通过；写入 `READY-S2` 接口冻结报告，包含六类主数据、库存/调拨/盘点 OpenAPI、权限、错误码、示例请求/响应和测试证据。

---

### Task 9: S5-foundation BOM、Routing、WorkOrder 来源查询与 SalesFactsPort

**依赖:** READY-S2。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/manufacturing/foundation/`、对应 `dto`、`infrastructure`、`application` 测试；不得实现派工、执行、领退料和成品入库。

- [ ] **Step 1: 添加来源校验失败测试**

测试有效 BOM/Routing、WorkOrder 创建、一个工单最多一条销售明细来源、同一销售明细拆分多个工单、跨租户销售来源返回 `MES_WO_004`/`MES_TENANT_001`、无来源创建成功、来源产品不一致拒绝。

- [ ] **Step 2: 实现薄来源层**

建立 `BomFactsPort`、`RoutingFactsPort`、`SalesFactsPort` 和 `WorkOrderSourcePort`；采购只依赖 `WorkOrderSourcePort` 的同租户存在性/产品一致性查询，不建立跨阶段硬外键。BOM/Routing/WorkOrder 记录版本和逻辑删除，后续 S5 执行层复用同一模型。

- [ ] **Step 3: 验证 foundation**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*ManufacturingFoundation*"
```

Expected: 来源校验通过，尚未发布 `READY-S5`，采购可以通过明确的应用端口校验 `sourceWorkOrderId`。

---

### Task 10: S3 采购订单、到货验收与拒收

**依赖:** READY-S2、Task 9。  
**写入边界:**

- `backend/platform-core/src/main/resources/db/migration/core/V3__purchasing_receipt_quality_putaway.sql`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/purchasing/`
- `backend/platform-core/src/test/java/com/ailearn/platform/core/purchasing/`

- [ ] **Step 1: 添加失败测试**

覆盖采购单 `Draft -> Submitted -> Approved -> PartiallyReceived -> Completed`、来源工单校验、分批到货、部分/全部拒收、`arrivedQty = rejectedQty + receivedQty`、拒收原因、超待收、人工完成不伪造收货/库存事实。

- [ ] **Step 2: 运行测试确认失败**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Purchas*"
```

- [ ] **Step 3: 实现采购单和收货应用服务**

创建/修改/提交/审核/人工完成通过采购应用服务完成；收货确认校验完整批次后，只对 `receivedQty` 调用一次 `InventoryCommandService.increase` 到 QualityHold；拒收只保存采购事实，不写库存余额或流水。所有方法补中文注释。

- [ ] **Step 4: 实现采购 API**

提供 `/api/purchase-orders` 查询/创建/更新、`/{id}/submit`、`/{id}/approve`、`/{id}/complete`、`/api/purchase-receipts/{id}/confirm`；所有写接口校验 `pur:*` 权限和 `Idempotency-Key`。

- [ ] **Step 5: 验证 S3 采购基础**

Expected: 收货、拒收、人工完成和库存一次性增加测试通过；未实现质量处置和上架前不发布 `READY-S3`。

---

### Task 11: S3 质检、质量处置、退回/报废与上架

**依赖:** Task 10。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/{quality,purchasing/putaway}/`、对应测试和同一 V3 迁移文件；不得创建第二个库存写入口。

- [ ] **Step 1: 添加失败测试**

覆盖 `inspectedQty = qualifiedQty + unqualifiedQty`、累计检验上限、放行/退回/报废角色、处置决定不改库存、仓库执行确认后才移动/扣减、报废原因、上架前置和采购人工完成后继续处置。

- [ ] **Step 2: 实现质量事实与处置**

检验只写质量事实；放行决定、退回决定、报废决定保存为 `PendingExecution`；仓库确认放行调用 `move(QualityHold -> ReceivingStaging)`，退回/报废调用 `decrease`，记录库存流水标识。

- [ ] **Step 3: 实现上架**

上架只调用 `move(ReceivingStaging -> 合法目标库位)`，不得增加总库存；累计上架不超过已放行未上架数量；人工完成采购单不阻断已收货货物后续处置。

- [ ] **Step 4: 验证 S3**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Purchas*,*Quality*,*Putaway*"
```

Expected: 全拒收、部分拒收、放行、退供、报废、上架、人工完成和幂等测试通过；输出 `READY-S3` 固定格式契约报告。

---

### Task 12: S4 销售订单、双轴状态与订单行数量

**依赖:** READY-S2。与 Task 10–11 互不写同一文件，可并行。  
**写入边界:**

- `backend/platform-core/src/main/resources/db/migration/core/V4__sales_reservation_pick_shipment.sql`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/sales/`
- `backend/platform-core/src/test/java/com/ailearn/platform/core/sales/`

- [ ] **Step 1: 添加失败测试**

测试订单 `Draft -> Submitted -> Approved -> Completed`、订单行 `0 <= shippedQty <= pickedQty <= reservedQty <= orderedQty`、履约状态派生、只在 Draft 修改、人工完成原因和完成审计、跨租户客户/产品拒绝。

- [ ] **Step 2: 实现销售订单聚合**

保存 `orderedQty`、`reservedQty`、`pickedQty`、`shippedQty`，查询计算 `unreservedQty`、`unpickedQty`、`shippingStagedQty`、`activeReservedQty`、`unshippedQty`、`fulfillmentStatus` 和 `allowedActions`；不把 `fulfillmentStatus` 持久化。

- [ ] **Step 3: 实现销售 API**

提供 `/api/sales-orders` 查询/创建/更新、`/{id}/submit`、`/{id}/approve`、`/{id}/complete` 和人工完成入口；销售命令使用 `sales:*` 权限和幂等记录。

- [ ] **Step 4: 验证订单状态**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*SalesOrder*"
```

Expected: 双轴状态、累计数量、人工完成审计和租户测试通过。

---

### Task 13: S4 自动预留、直接拣货、退回、释放与发货

**依赖:** Task 7、Task 12；与 Task 10–11 可并行完成。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/sales/fulfillment/`、对应测试；只能调用 `InventoryCommandService`。

- [ ] **Step 1: 添加失败测试**

覆盖并发直接拣货不超卖、已有未拣预留优先使用、自动预留与移位同事务、拣货不扣总库存、退回不改变有效预留合计、释放不超过 `unpickedQty`、发货不超过暂存和有效预留、发货释放库存预留、人工完成前先退回暂存。

- [ ] **Step 2: 实现直接拣货事务**

按“锁订单行与余额 → 使用已有预留 → 不足自动 `reserve` → `move` 到 ShippingStaging → `moveReservationAllocation` → 更新拣货事实”顺序实现；任一步失败整体回滚；返回自动预留、位置移动和分配移动摘要。

- [ ] **Step 3: 实现异常和发货**

退回调用反向 `move` 和分配移动；释放调用 `release`；发货调用 `decrease` 和 `release`，更新订单行累计数量；全部发货同事务进入 `Completed + Normal`。

- [ ] **Step 4: 验证 S4**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Sales*,*Pick*,*Shipment*"
```

Expected: 并发、分批拣/发、退回、释放、人工完成、幂等和租户测试通过；发布 `READY-S4` 契约。

---

### Task 14: S5 MES V5 迁移与 BOM/Routing/WorkOrder 完整状态

**依赖:** Task 9、READY-S3、READY-S4。  
**写入边界:**

- `backend/platform-core/src/main/resources/db/migration/core/V5__manufacturing_execution_inventory_links.sql`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/manufacturing/`
- `backend/platform-core/src/test/java/com/ailearn/platform/core/manufacturing/`

- [ ] **Step 1: 添加失败测试**

覆盖 BOM/Routing 版本、工单 `Draft -> PendingApproval -> Released -> InProgress -> Completed`、审核拒绝重提、审核通过冻结版本、销售来源和采购来源软关联、人工完成不补造事实。

- [ ] **Step 2: 扩展 foundation 模型**

复用 Task 9 的 BOM/Routing/WorkOrder 表和端口；V5 迁移只增加派工、执行、报工、质检、领退料、成品入库所需表和来源关联字段，不重复创建 BOM/工单表。

- [ ] **Step 3: 实现工单应用服务**

提交、审核、拒绝、正常完成和人工完成均在应用服务校验；审核通过快照化 BOM/Routing 版本；工单状态不承担工序执行细节；生产工单完成必须满足必需工序、报工、质检和入库约束。

- [ ] **Step 4: 验证工单**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*WorkOrder*,*Bom*,*Routing*"
```

Expected: 工单状态、版本冻结、来源关联和人工完成测试通过。

---

### Task 15: S5 派工、工序执行与生产上下文查询

**依赖:** Task 14。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/manufacturing/execution/`、对应测试、内部查询端口。

- [ ] **Step 1: 添加失败测试**

覆盖派工 `Draft -> Released -> Processing -> Completed`、未下达工单不能派工/开始、工序执行 `NotStarted -> Running -> Paused -> Running -> Completed`、暂停原因、设备可选、非法重复状态、同设备/告警时间查询唯一活动执行上下文。

- [ ] **Step 2: 实现派工和执行**

派工只表达安排；工序执行独立保存开始/暂停/恢复/完成时间和操作人；设备引用为可选软引用，不能复制 IoT 遥测。

- [ ] **Step 3: 冻结 ProductionContextQuery**

新增内部 `ProductionContextQuery`，按 `tenantId + deviceId + alarmTime` 返回唯一活动 `OperationExecution`/`WorkOrder` 摘要；接口只返回生产标识、执行标识、工序和时间，不暴露用户 Header，不写 Core 表外数据。

- [ ] **Step 4: 验证执行上下文**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Dispatch*,*OperationExecution*,*ProductionContext*"
```

Expected: 执行状态和内部生产上下文查询通过；该端口冻结后才允许 S6 上下文补链。

---

### Task 16: S5 领料、退料、报工、质检、成品入库与 READY-S5

**依赖:** Task 15、READY-S2。与 Task 17–19 的 IoT 自有事实实现可并行。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/manufacturing/{material,report,quality,finishedgoods}/`、对应测试。

- [ ] **Step 1: 添加失败测试**

覆盖领料只对 BOM 产品、库存不足整单失败、退料不超过可退范围、报工 `qualifiedQty + defectQty` 和累计上限、质检失败阻止入库、成品入库不超过合格未入库数量、重复确认不重复库存事实、人工完成不补造事实。

- [ ] **Step 2: 实现库存桥接**

MaterialIssue 确认只调用 `InventoryCommandService.decrease`；MaterialReturn 只调用 `increase`；FinishedGoodsReceipt 只调用 `increase`；制造包不得注入 Inventory Mapper。每张单据保存返回的库存业务标识/流水标识。

- [ ] **Step 3: 实现报工和质量**

报工事实与质检事实分离；失败质检数量不得直接成品入库；不创建 WIP、线边仓、返工单或重复质检流程。

- [ ] **Step 4: 验证 S5**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Manufacturing*,*Material*,*WorkReport*,*FinishedGoods*"
```

Expected: S5 核心测试通过；发布 `READY-S5`，包含 MES API、状态、权限、错误码、`allowedActions`、Inventory 调用证据和 `ProductionContextQuery` 内部契约。

---

### Task 17: S6 IoT V2 迁移、DeviceProfile、Device、Credential

**依赖:** READY-S0、READY-S2；可与 Task 14–16 并行，但不得修改 `platform-core`。  
**写入边界:**

- `backend/platform-iot/src/main/resources/db/migration/iot/V2__device_mqtt_telemetry_status_alarm.sql`
- `backend/platform-iot/src/main/java/com/ailearn/platform/iot/{device,profile,credential}/`
- `backend/platform-iot/src/test/java/com/ailearn/platform/iot/{device,profile,credential}/`
- `backend/platform-iot/pom.xml`（仅新增 MQTT 客户端依赖）

- [ ] **Step 1: 添加失败测试**

覆盖设备模型指标白名单、MQTT-only、设备生命周期 `Active <-> Disabled`、有历史设备只能停用、凭证一次性明文、撤销立即失效、跨租户和跨设备凭证拒绝。

- [ ] **Step 2: 编写 IoT V2 迁移**

创建 profile、metric、device、credential、dedup/message、telemetry、status、alarm、alarm_rule 和上下文任务表；遥测与告警事实只追加，状态为当前快照；所有表包含 `tenant_id` 和必要唯一键。

- [ ] **Step 3: 实现设备管理 API**

提供 `/api/device-profiles`、`/api/device-alarm-rules`、`/api/devices`、`/api/devices/{id}/credentials` 和凭证撤销 API；只返回凭证业务标识与状态，明文只出现在创建成功响应，不写日志和数据库。

- [ ] **Step 4: 验证设备基础**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-iot -am test -Dtest="*Device*,*Credential*,*Profile*"
```

Expected: 设备、模型和凭证测试通过。

---

### Task 18: S6 TelemetryIngestionService、消息去重与 DeviceStatus

**依赖:** Task 17。  
**写入边界:** `backend/platform-iot/src/main/java/com/ailearn/platform/iot/{telemetry,mqtt}/`、对应测试。

- [ ] **Step 1: 添加摄取失败测试**

覆盖 `message_id` 优先、否则 `sequence`、去重键包含 `device_id`、同键同哈希幂等成功、同键不同哈希返回 `IOT_TLM_003`、缺少标识/非法指标整条拒绝、延迟消息保存但不倒退状态。

- [ ] **Step 2: 冻结共用摄取接口**

```java
TelemetryIngestionResult ingest(TelemetryIngestionCommand command);
```

MQTT 消费和 `/api/protocol-adapters/mqtt/simulate` 都只能调用该接口；命令包含设备身份、凭证上下文、设备时间、消息标识、序号、指标集合和原始载荷摘要。

- [ ] **Step 3: 实现保存顺序**

按“完整校验 → 消息去重记录 → 全部遥测 → 较新消息更新状态 → 告警触发/维持/恢复 → IoT 事务提交”实现；任何指标非法时不保存合法部分；状态只被较新的有效消息推进。

- [ ] **Step 4: 验证摄取**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-iot -am test -Dtest="*Telemetry*,*Ingestion*,*Mqtt*"
```

Expected: 消息幂等、冲突、延迟和状态测试通过。

---

### Task 19: S6 告警生命周期、MQTT Broker ACL 与 IoT READY-S6 基础

**依赖:** Task 18。  
**写入边界:**

- `backend/platform-iot/src/main/java/com/ailearn/platform/iot/alarm/`
- `backend/platform-iot/src/main/java/com/ailearn/platform/iot/controller/`
- `backend/platform-iot/src/test/java/com/ailearn/platform/iot/alarm/`
- `deploy/docker/mosquitto.conf`
- `deploy/local/mosquitto.conf`
- 经 `runtime/README.md` 确认的 Mosquitto 配置文件

- [ ] **Step 1: 添加告警测试**

覆盖单指标阈值、回差恢复、同设备同规则一个活动告警、`Triggered -> Acked -> Recovered`、`Triggered -> RecoveredUnacked -> Recovered`、非法重复确认、Core 不可用不影响 IoT 事实。

- [ ] **Step 2: 实现告警 API**

提供告警查询、详情、确认和设备遥测/状态查询；确认只记录用户、时间、意见，不伪造恢复；业务上下文字段保持 `Pending` 直到内部补链或人工补链。

- [ ] **Step 3: 加固 Mosquitto**

关闭匿名访问；每设备账号只允许发布 `devices/{device_code}/telemetry`；IoT 只读订阅账号不能发布；凭证撤销后禁止认证；QoS 1 由应用去重而非假设 Broker exactly-once。

- [ ] **Step 4: 验证 S6 基础**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-iot -am test
rg -n "allow_anonymous|devices/.*/telemetry|acl_file|listener" deploy runtime --glob '*.conf' --glob '*.md'
```

Expected: MQTT ACL/匿名配置和 IoT 业务测试通过；在上下文补链完成后发布完整 `READY-S6`。

---

### Task 20: S6 生产上下文 HMAC、重试与 READY-S6

**依赖:** READY-S5、Task 19。  
**写入边界:** `backend/platform-iot/src/main/java/com/ailearn/platform/iot/contextlink/`、`backend/platform-iot/src/main/java/com/ailearn/platform/iot/alarm/` 增量、对应测试和必要配置。

- [ ] **Step 1: 添加上下文测试**

覆盖唯一活动执行自动关联、无结果/多结果保持 Pending、Core 超时/5xx 后 IoT 遥测和告警仍可查询、重试成功、跨租户/不一致上下文拒绝、人工补链设置 `contextSource = Manual`、不改告警生命周期时间。

- [ ] **Step 2: 实现内部服务调用**

使用服务身份 HMAC 请求 `ProductionContextQuery`；不传递用户 `X-Authorities` 或业务用户 Header；上下文补链在 IoT 事实事务提交后执行，失败只记录待重试任务。

- [ ] **Step 3: 验证完整 S6**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-iot -am test
```

Expected: 凭证 ACL、撤销、QoS1 重复、载荷冲突、延迟、离线、告警回差、Core 不可用和上下文重试全部通过；发布 `READY-S6` 固定格式契约。

---

### Task 21: S7 Core V6 追溯/GIS/看板迁移与查询端口

**依赖:** READY-S5、READY-S6；不得在 S7 直连其他模块表。  
**写入边界:**

- `backend/platform-core/src/main/resources/db/migration/core/V6__traceability_gis_dashboard.sql`
- `backend/platform-core/src/main/java/com/ailearn/platform/core/{traceability,gis,dashboard}/ports/`
- 对应端口测试和迁移测试

- [ ] **Step 1: 添加端口隔离失败测试**

测试只允许 `InventoryFactsQuery`、`PurchasingFactsQuery`、`SalesFactsQuery`、`ManufacturingFactsQuery`、`QualityFactsQuery` 和远程 `IotFactsPort`；扫描 S7 包不得出现跨模块 Mapper 或直接表名查询。

- [ ] **Step 2: 编写 V6 迁移**

创建 `gis_site_map`、`gis_site_map_asset`、`gis_map_point` 和必要的展示配置/缓存元数据表；底图约束 PNG/JPEG/WebP、最大 5 MiB、保存 MIME/大小/SHA-256；点位坐标约束 0–100；所有配置带租户和逻辑删除字段。

- [ ] **Step 3: 冻结事实查询端口**

端口按当前用户租户和权限指纹查询；返回业务标识、状态、源更新时间和完整性标识；不返回跨租户对象存在性。

- [ ] **Step 4: 验证迁移和边界**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Traceability*,*Gis*,*Dashboard*"
```

Expected: V6 在 V1–V5 之后隔离升级通过，端口测试确认无跨模块直接查询。

---

### Task 22: S7 追溯链与 GIS 地图/点位配置

**依赖:** Task 21。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/{traceability,gis}/`、对应测试。

- [ ] **Step 1: 添加失败测试**

追溯测试从销售单、工单、库存流水和设备告警进入并沿真实来源双向构造；`trace:chain:view` 与节点领域权限双重裁剪；无权节点只返回计数；缺失来源标记完整性缺口。GIS 测试多地图、底图校验、点位租户/实体类型/坐标、幂等和源实体失效。

- [ ] **Step 2: 实现追溯应用服务**

只从冻结 FactsQuery 端口读取，节点关系来自真实来源字段，不建立万能关系表；响应含缺失来源标记、权限裁剪结果和源更新时间。

- [ ] **Step 3: 实现 GIS 配置和投影**

GIS 作为 `MapPointConfiguration` 唯一写入方；点位实体只允许仓库、生产区域、设备；设备状态/告警事实从 IoT 端口读取；显示状态按 `Alarm > Offline > Warning > Normal` 计算；点位写入不修改源业务状态。

- [ ] **Step 4: 验证追溯/GIS**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Traceability*,*Map*,*Point*"
```

Expected: 追溯权限裁剪、完整性缺口、GIS 跨租户、底图校验、状态优先级和点位幂等测试通过。

---

### Task 23: S7 七类看板、陈旧缓存与 READY-S7

**依赖:** Task 21–22。  
**写入边界:** `backend/platform-core/src/main/java/com/ailearn/platform/core/dashboard/`、对应测试、必要的 Core 配置和领域契约。

- [ ] **Step 1: 添加失败测试**

测试 `inventory`、`fulfillment`、`manufacturing`、`quality`、`device`、`alarm`、`traceability` 七类摘要；仅支持 `today/7d/30d`；租户时区边界；权限指纹隔离；新鲜 60 秒；源失败带时间返回最近成功 `stale=true` 且不超过 10 分钟；没有旧结果返回 `GIS_QUERY_002`，不返回零值。

- [ ] **Step 2: 实现看板查询**

每个摘要通过对应 FactsQuery 端口聚合；结果包含 `summaryType`、`metrics`、实际 `timeRange`、`sourceSummary`、`generatedAt`、`stale`、可选 `staleSince`；业务权限过滤在应用服务完成。

- [ ] **Step 3: 实现缓存**

缓存键包含租户、权限指纹、摘要类型、筛选条件和时间范围；成功结果 60 秒内新鲜；源失败只允许读取最近成功的 10 分钟结果并标记陈旧；缓存不能将查询失败转换为 0。

- [ ] **Step 4: 验证 S7**

```powershell
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd -pl platform-core -am test -Dtest="*Dashboard*,*Traceability*,*Gis*"
```

Expected: 七类看板、部分失败、陈旧缓存、权限和时间范围测试通过；发布 `READY-S7` 固定格式契约。

---

### Task 24: 阶段 2–7 联合回归、规格同步与最终交付

**依赖:** READY-S0、READY-S2、READY-S3、READY-S4、READY-S5、READY-S6、READY-S7。  
**写入边界:** 后端测试、领域规格和本任务报告；不得顺手修改前端或项目级共享入口。

- [ ] **Step 1: 做全仓安全/边界扫描**

```powershell
cd D:\AI\ai_learn_wms_ai\ai_learn_developProject
rg -n "X-Authorities|X-Permissions|X-Roles|hasRole\(|hasPermission\(" backend --glob '*.java'
rg -n "platform-auth|auth" backend/platform-core/pom.xml backend/platform-iot/pom.xml
rg -n "V1__|V2__|V3__|V4__|V5__|V6__" backend/platform-auth/src/main/resources/db/migration backend/platform-core/src/main/resources/db/migration backend/platform-iot/src/main/resources/db/migration
```

Expected: Core/IoT 不依赖 `platform-auth`；生产授权不使用旧 Header/旧表达式；历史迁移未被改写。

- [ ] **Step 2: 做后端全量测试和打包**

```powershell
cd D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd test
D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd package
```

Expected: 全部后端测试和聚合打包通过；记录 Java/Maven 实际版本及测试摘要。

- [ ] **Step 3: 做差异与文件所有权审查**

```powershell
cd D:\AI\ai_learn_wms_ai\ai_learn_developProject
git status --short --branch
git diff --check
git diff --stat -- backend deploy docs/specs
```

Expected: 无前端/原型写入，无历史迁移修改，无开发库配置污染，无 Git 提交；用户原有删除和未跟踪交接文件仍保持原状态。

- [ ] **Step 4: 同步领域契约**

只在实际接口与原领域契约存在差异时更新 `docs/specs/10-erp-wms/`、`docs/specs/20-mes/`、`docs/specs/30-iot-digital-twin/`、`docs/specs/40-gis-dashboard/` 的接口字段、权限、错误码和实现状态；每次更新同步检查同目录领域模型、业务规则、验收标准和对应测试。

- [ ] **Step 5: 形成最终交付报告**

最终报告逐项列出：修改文件、每个 READY 闸门、接口清单、字段/枚举/权限/错误码、`allowedActions`、测试命令与实际结果、隔离 PostgreSQL 12.1 证据、Redis/MQTT 证据、遗留风险、未实现二期边界和未提交 Git 证据。只有全部完成定义满足时才报告后端阶段 2–7 完成。

---

## 计划自检

- 覆盖范围：S0、S2、S3、S4、S5、S6、S7 均有独立任务和 READY 闸门；S5-foundation 解决采购来源循环依赖；Task 24 覆盖联合回归与契约一致性。
- 并行边界：Task 10–11 与 Task 12–13 使用互斥 Core V3/V4 写集；Task 14–16 与 Task 17–19 分别写 Core 和 IoT；Task 20 等待 S5 查询端口；S7 等待所有事实端口。
- 占位扫描：执行步骤没有未填写的占位内容；每个代码任务给出目标文件、接口名、测试行为和命令。
- 类型一致性：全计划统一使用 `camelCase` DTO、`allowedActions`、`request_id`、`BigDecimal`、`NUMERIC(19,6)`、`InventoryCommandService`、`ProductionContextQuery` 和 `IotFactsPort`。
- 项目约束：未安排 `git commit`、`git push`、建分支、修改前端或连接开发库；未恢复用户删除的历史设计文件。
