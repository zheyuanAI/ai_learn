# 阶段 0–7 全栈业务闭环实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以本机 PostgreSQL `127.0.0.1:5433/ai_learn` 为练手项目唯一运行基线，修复阶段 0–7 的后端接口、前端适配、状态流转和黄金业务闭环，使页面真实调用冻结契约而不是使用演示占位数据。

**Architecture:** 保留 Gateway、Auth、Core、IoT 四服务边界和现有 PostgreSQL/Flyway 结构。先修公共安全、路由和 DTO 约定，再按主数据→库存→采购/销售→MES→IoT→追溯/GIS/看板顺序修复；前端所有选择项从主数据/后端事实加载，API 层负责统一 snake_case/camelCase 适配，业务页面不伪造状态或动作。

**Tech Stack:** Java 21、Maven 3.9.1、Spring Boot 3.3.5、MyBatis-Plus、Flyway、PostgreSQL 12.1、Redis、Mosquitto、Vue 3、TypeScript、Vite 5、JUnit 5。

---

## 全局约束

- 直接使用 `127.0.0.1:5433/ai_learn`；不再为本次练手项目创建迁移专用数据库。任何迁移、清理或造数前先读取当前表和 Flyway 状态，禁止删除用户数据或执行不可恢复清库。
- 不执行 `git commit`、`git push`、`git reset --hard`、`git checkout --` 或 `git clean`；保留 Gemini 与用户已有改动，按文件最小修复。
- 以实际代码/配置为实现事实，以正式领域契约为接口约束；若实现与契约冲突，优先修代码和调用方，必要时同步文档。
- 所有新增方法使用中文注释说明用途、入参、出参和简略流程；修改已有方法在修改处增加中文用途注释。数量继续使用后端 `BigDecimal`/HTTP 字符串。
- 写操作必须保留租户、权限、事务、幂等、状态校验和并发保护；前端不得生成业务实体 ID、状态或 `allowedActions`。
- 本计划完成后必须重新执行后端测试、前端构建、`git diff --check`，并用 5433 数据库做 Flyway/健康检查/最小黄金流验证；没有证据的能力标记“未验证”。

## 文件责任划分

- 公共入口：`backend/platform-gateway/src/main/resources/application.yml`、`backend/platform-auth/**`、共享安全配置。
- 业务后端：`backend/platform-core/**`、`backend/platform-iot/**`，只修契约/业务缺口，不做无关重构。
- 前端适配：`frontend/src/api/**`、`frontend/src/types/**`、`frontend/src/views/**`、`frontend/src/router/index.ts`。
- 验证与事实：`docs/specs/**`、`runtime/README.md`、本计划对应的测试；不修改参考工程。

## 执行任务

### Task 1：公共安全、路由与本机数据库基线

**Files:** Gateway 路由与过滤器、Auth JWT/会话配置、对应测试、`runtime/README.md`。

- [x] 读取 5433 的 `SELECT version()`、`\dn`、各模块 Flyway history 和当前租户/用户/权限数量，保存只读快照；确认应用连接、Redis、Mosquitto 的实际端口。
- [x] 增加 `/api/exception-center` Gateway Core 路由，核对 `/api/traceability/**`、`/api/site-maps/**`、`/api/dashboard/**`、IoT 路由与 Controller 一致。
- [ ] 保留本地开发 RSA 默认值，但增加明确的 `spring.profiles.active=dev` 约束；非 dev profile 缺少密钥时启动失败，避免练手默认密钥误用于生产。
- [x] 验证客户端伪造身份/权限 Header 被清理、Redis 权限异常 fail-closed、租户和用户上下文在请求结束后清理；补中文注释和安全回归测试。

> 本任务仍保留一项未勾选的增强：非 `dev` profile 缺少 RSA 密钥时强制启动失败。本轮按练手项目边界保留现有稳定开发密钥与既有 S0 安全基线，不扩展生产配置改造。

### Task 2：阶段 1 主数据真实选择器与管理闭环

**Files:** `frontend/src/api/masterData.ts`、`frontend/src/views/masterdata/**`、必要的 Core masterdata DTO/Controller。

- [x] 产品、客户、供应商、UOM、仓库、库位全部使用真实 GET 数据填充选择器；创建/编辑请求严格发送 UUID，不再发送 `1`、`2`、`3` 或业务编码占位值。
- [x] 补齐客户、供应商创建/修改保存分支；仓库和库位表单按后端字段发送 `warehouseId`、`locationType` 等正式字段。
- [x] 保存后重新读取详情/列表，错误统一展示 `ApiResponse` 消息；停用对象不能继续作为业务单据选择项。
- [x] 增加前端类型检查和至少一组 API 适配测试/静态契约断言。

### Task 3：阶段 2 库存、调拨、预留、盘点

**Files:** Core inventory/transfer/stocktake 服务与测试，`frontend/src/api/inventory.ts`、库存和盘点页面。

- [x] 修正盘点创建请求为 `stocktakeNo`、UUID `warehouseId`、单个可选 `locationId`；创建只保存表头，页面必须调用 `POST /api/stocktakes/{id}/start` 后才允许录入。
- [x] 修正后端分页映射，返回真实 `lines`、`totalPages`、状态、版本和 `allowedActions`；详情页在列表进入时读取 `/api/stocktakes/{id}`。
- [x] 库存余额、预留、流水、调拨全部通过 `InventoryCommandService`，核对锁、幂等、租户和数量字符串；前端分页使用服务端 `total`/`totalPages`。
- [x] 使用主数据选择器替代所有假库位/假仓库 ID，补转移确认、释放、盘点确认的成功后重读。

### Task 4：阶段 3 采购、收货、质检、上架

**Files:** Core purchasing/quality/putaway、`frontend/src/api/purchasing.ts`、采购相关页面和类型。

- [x] 采购订单创建严格发送 UUID `supplierId`、行 `productId`、`targetWarehouseId`、可选 `sourceWorkOrderId`，删除不存在的顶层字段和硬编码 ID。
- [x] 收货确认使用独立 `receiptId` 路径，发送 `receiptNo`、`purchaseOrderId`、`receiptTime`、`qualityHoldLocationId` 和 `purchaseOrderLineId` 明细；禁止 payload-only helper 猜测路径。
- [x] 质检、处置、上架按正式 UUID 路径和字段调用；上架页面传递 `status` 查询并正确读取 `taskNo`、`purchaseOrderNo`、数量、状态和分页字段。
- [x] 后端 putaway page 补齐 `totalPages` 或统一前端按契约计算，补收货/质检/上架集成回归。

### Task 5：阶段 4 销售、预留、直接拣货、发运

**Files:** Core sales/fulfillment/reservation、`frontend/src/api/sales.ts`、销售订单和拣货页面。

- [x] 销售订单创建使用主数据 UUID；客户、仓库、产品、来源库位不允许业务编码或数字占位值。
- [x] 拣货页直接使用后端订单/拣货事实，不拼装任务 ID、数量、状态和 `allowedActions`；确认请求使用真实操作事实 UUID、`salesOrderLineId`、来源库位和发运暂存库位。
- [x] 预留/释放/归还/发运请求统一携带正式实体 ID、幂等键和版本，成功后刷新详情；错误状态不继续显示可操作按钮。
- [x] 增加销售创建→预留→直接拣货→发运的 API/服务回归。

### Task 6：阶段 5 BOM、工艺、工单、派工、报工、物料和完工

**Files:** Core manufacturing 全链路、`frontend/src/api/manufacturing.ts`、MES views/types。

- [x] 所有 BOM、Routing、WorkOrder、Dispatch、OperationExecution 表单的产品、BOM、Routing、工作中心、操作员、设备、库位使用真实 UUID 选择器。
- [x] 删除前端对不存在的 `GET /api/material-issues`、`GET /api/material-returns` 调用；列表改为来源于工单/事实详情，写入仍走正式 POST。
- [x] FGR 查询固定要求 `work_order_id`，读取数组响应而不是 `records`；报工请求补齐 `workOrderId`、`operationId`、`operationExecutionId` 和数量字符串。
- [x] 保持 BOM 超额原因、质量隔离/报废/关闭、工单版本 CAS、派工前置关系和累计报工规则，补端到端服务测试。

### Task 7：阶段 6 IoT 设备、遥测、告警和上下文补链

**Files:** `backend/platform-iot/**`、`frontend/src/api/iot.ts`、IoT views/types、MQTT 配置。

- [x] 将设备告警规则列表类型改为真实数组，将业务上下文链接响应改为 `ContextLinkResult`；上下文默认值不得使用假 ID。
- [x] 设备详情、凭证、协议、遥测、状态、告警页面全部从接口获得真实 ID 和状态，确认/补链成功后重读。
- [ ] 保持 MQTT QoS1 应用去重、告警回差和上下文重试；使用本机 Mosquitto/Redis 做至少一条真实消息链验证，失败则明确记录原因。

> 本任务的自动去重、回差、重试和故障路径已由测试及 HTTP `simulate` 验证；真实 Mosquitto ACL/QoS1 实机链路按 17.11 标记为未验证。

### Task 8：阶段 7 追溯、GIS、看板、异常中心

**Files:** Core traceability/gis/dashboard/exceptioncenter、Gateway、`frontend/src/api/insights.ts`、S7 views/router。

- [x] 异常中心过滤值与后端统一为真实 `source`/`severity`，网关可达；列表分页和错误状态真实展示。
- [x] 追溯入口使用 UUID `entity_id`，把后端 snake_case `TraceNode` 映射为前端模型；覆盖率、缺失来源和裁剪状态使用服务端结果。
- [x] GIS 地图创建发送后端要求的 `asset`，点位保存使用 UUID `entityId` 和正式字段；统一 snake_case 投影映射，地图列表/详情/点位不再读取不存在的 camelCase 字段。
- [x] 看板穿透链接与实际 Router 路径一致；失败卡片保留真实 `generatedAt`/`stale`，不伪造当前时间。
- [x] GIS 写接口落实 `Idempotency-Key`，补异常中心/GIS/追溯/看板回归。

### Task 9：本机 PostgreSQL 黄金流、前端构建与最终审查

**Files:** 测试和必要的运行说明/契约文档；不新建无关数据表，不提交 Git。

- [x] 先用只读查询确认 Flyway 状态；按当前迁移顺序在 5433 启动 Auth/Core/IoT/Gateway，检查健康端点和日志中的失败请求。
- [x] 用现有或新增最小练习数据执行：登录→主数据→采购订单→收货→质检→上架→销售订单→预留→拣货→发运→工单→派工→报工→完工入库→IoT 告警→追溯/GIS/看板。
- [x] 执行后端聚合测试和打包：

```powershell
$env:JAVA_HOME='D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\jdk'
cd D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend
& D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd test '-Dcheckstyle.skip=true' '-Dmaven.repo.local=D:\project\MavenRepository391'
& D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd package '-DskipTests' '-Dcheckstyle.skip=true' '-Dmaven.repo.local=D:\project\MavenRepository391'
```

- [x] 执行前端：

```powershell
cd D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend
npm run build
```

- [x] 执行 `git diff --check`、关键路径 `rg` 静态契约扫描和工作树审查；最终报告区分“已修复并验证”“代码完成但环境未验证”“仍有明确缺口”。

## 完成判定

- 阶段 0–7 的核心写接口与页面调用不再使用假业务 ID、错误字段或不存在的 GET 接口。
- 后端测试、前端构建和本机 PostgreSQL 最小黄金流均有实际命令输出；任何失败不得用“编译通过”替代。
- 不执行 Git 提交；所有变更保留在当前工作树，并在最终报告中列出未解决风险和未验证依赖。
