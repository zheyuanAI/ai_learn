# Project Content Alignment Implementation Plan

> **历史实施计划（2026-08-20）：** 本文保留当时的实施背景与步骤记录；当前正式事实源路径已迁移至 `docs/specs/`，下列路径均已同步为现行中文文件名。

**Goal:** 以已批准的制造与仓储协同执行平台项目计划为准，清理自研项目中与项目定位、一期范围、领域语义、原型和运行基线不一致的内容。

**Architecture:** 先更新最高层项目事实源，再按 ERP/WMS、MES、IoT、GIS、AI 的领域调用链同步规格，随后更新原型和前端壳文案，最后收敛部署编排与协作规则。历史计划保留但显式标记为已被新计划取代，参考工程不修改。

**Tech Stack:** Markdown、HTML、Vue 3、TypeScript、Docker Compose、PowerShell、Git

## 执行结果（2026-08-20）

- 后续决策：当前开发周期已于 2026-08-20 调整为一个月；正式交付计划以 `docs/specs/00-project/正式项目计划.md` 为准。
- Tasks 1—8 的内容更新已完成；下方复选框保留原执行步骤定义，完成状态以本节为准。
- 前端 `npm run build`、后端 Java 21/Maven 3.9.1 `mvn package`、HTML 标签平衡、静态 JavaScript 语法、关键词审计和 `git diff --check` 均通过。
- 当前终端没有 Docker CLI，因此未执行 `docker compose config`；已用结构检查确认活动服务只有 `postgres`、`redis`、`mosquitto`，仍需用户在安装 Docker 的环境中复验。
- OpenSpec 工件按仓库路由规则保持不变；当前入口为 `docs/openspec/**`，旧项目名称若仍出现在历史工件中，不属于本次普通任务的生效事实源。
- 本次没有创建 Git commit。

## Global Constraints

- 正式事实源：`docs/specs/00-project/正式项目计划.md` 与项目根目录 `CONTEXT.md`。
- 一期中间件仅包含 PostgreSQL 16（5433）、Redis（6379）和 Mosquitto（1883）。
- 后端保持 Gateway 10001、Auth 10002、Core 10003、IoT 10004 四服务。
- 一期人工建立销售、生产、采购供需关联，不实现 MRP、APS 或自动排产。
- 库存统一使用 `reserved_qty`，禁止与 `frozen_qty` 并存。
- 收货确认增加收货暂存位库存；上架只移动位置；预留不减少实物库存；拣货只移动位置；发货确认才减少实物库存。
- 生产工单、派工、工序执行、报工和质检分离；一期包含生产领料、退料和成品入库。
- IoT 区分 Telemetry、DeviceStatus、Alarm，QoS 1 消息按设备与消息标识去重，Core 不可用时已收到的设备事实仍需保存。
- GIS、Dashboard、AI 只表达或查询事实；AI 只读并按租户、权限审计。
- 不修改参考工程、编译产物、历史 OpenSpec 工件和二期原型资产本身。
- 遵守仓库规则，本计划执行过程中不创建 Git commit。

---

### Task 1（已完成）: 更新项目身份、协作规则与文档入口

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`
- Modify: `docs/README.md`
- Modify: `docs/项目目录结构.md`
- Modify: `backend/README.md`
- Modify: `frontend/README.md`

**Interfaces:**
- Consumes: `docs/specs/00-project/正式项目计划.md`、`CONTEXT.md`
- Produces: 全仓统一的项目名称、事实源优先级、一期技术栈和阅读入口

- [ ] **Step 1:** 将项目名称统一为“制造与仓储协同执行平台”，明确它是学习驱动的轻量化 MES/WMS 协同执行平台。
- [ ] **Step 2:** 将项目计划和 `CONTEXT.md` 加入必读事实源，并把实际代码与配置调整为最高优先级。
- [ ] **Step 3:** 将 RabbitMQ、MinIO、pgvector、三维数字孪生和 RAG 标记为二期，不再描述为当前技术栈。
- [ ] **Step 4:** 更新 Core、IoT、前端与部署目录说明，使其与模块化 Core、IoT 独立事实和一期中间件一致。
- [ ] **Step 5:** 运行 `rg -n "设备数字孪生与 AI 助手平台|PostgreSQL.*RabbitMQ.*MinIO" README.md AGENTS.md CLAUDE.md docs backend frontend`，预期无未标记的旧定位。

### Task 2（已完成）: 更新项目级规格与当时的十周学习路线

**Files:**
- Modify: `docs/specs/00-project/正式项目计划.md`
- Modify: `docs/specs/00-project/项目概述.md`
- Modify: `docs/specs/00-project/架构设计.md`
- Modify: `docs/specs/00-project/原型与交互说明.md`
- Modify: `docs/superpowers/plans/2026-06-30-ai-learn-project.md`

**Interfaces:**
- Consumes: 已批准项目定位与评审吸收结论
- Produces: 生效的一期范围、黄金闭环、模块边界、交付顺序和历史计划替代关系

- [ ] **Step 1:** 将项目计划状态改为已批准生效，并删除“规格尚未同步”的临时说明。
- [ ] **Step 2:** 在总览中以黄金闭环、人工供需关联、库存事实内核、制造执行和 IoT 事实作为一期主线。
- [ ] **Step 3:** 在架构中冻结四服务、Core 逻辑模块、schema 边界、IoT 独立保存与应用服务访问纪律。
- [ ] **Step 4:** 更新原型范围和当时的十周学习路线，顺序固定为 IAM/主数据、Inventory Kernel、Inbound、Outbound/供需关联、Manufacturing、IoT、Traceability、GIS/Dashboard、AI、E2E。
- [ ] **Step 5:** 在旧的 2026-06-30 计划顶部标记“历史计划，已由正式项目计划取代”，不重写历史内容。
- [ ] **Step 6:** 运行 `rg -n "待项目负责人确认|本计划确认后|冻结库存|第 4 周.*WMS" docs/specs/00-project`，只允许出现解释历史差异的文字。

### Task 3（已完成）: 同步 ERP/WMS 规格

**Files:**
- Modify: `docs/specs/10-erp-wms/概述.md`
- Modify: `docs/specs/10-erp-wms/领域模型.md`
- Modify: `docs/specs/10-erp-wms/接口契约.md`
- Modify: `docs/specs/10-erp-wms/验收标准.md`

**Interfaces:**
- Consumes: 人工供需关联、库存关键时点、`InventoryBalance`、`InventoryReservation`、`InventoryTransaction`
- Produces: 采购、收货、上架、预留、拣货、发货、领退料共用的库存契约

- [ ] **Step 1:** 将 `Inventory` 拆清为余额、预留和流水，字段统一为 `on_hand_qty`、`reserved_qty`、`available_qty`、`version`。
- [ ] **Step 2:** 固定收货、上架、预留、释放、拣货、发货、领料、退料、成品入库的数量与位置语义。
- [ ] **Step 3:** 将销售操作命名从 freeze/frozen 调整为 reserve/reserved，并同步状态、DTO、返回摘要和错误码语义。
- [ ] **Step 4:** 增加采购来源生产工单的可选人工关联；应收应付草稿降为非核心附属结果。
- [ ] **Step 5:** 增加事务、幂等、并发控制、版本或行锁原则以及余额一致性校验要求。
- [ ] **Step 6:** 更新场景与自动化验收，明确上架不重复增加库存、拣货不减少总库存、发货才扣减、并发预留不超卖。
- [ ] **Step 7:** 运行 `rg -n "frozen_qty|freeze_lines|/freeze|Frozen|冻结库存" docs/specs/10-erp-wms`，预期无旧字段、旧接口或旧状态残留。

### Task 4（已完成）: 同步 MES 与仓储协同规格

**Files:**
- Modify: `docs/specs/20-mes/概述.md`
- Modify: `docs/specs/20-mes/领域模型.md`
- Modify: `docs/specs/20-mes/接口契约.md`
- Modify: `docs/specs/20-mes/验收标准.md`

**Interfaces:**
- Consumes: `WorkOrder`、`DispatchOrder`、`OperationExecution`、`MaterialIssue`、`MaterialReturn`、库存应用服务
- Produces: 从销售来源到领料、工序执行、报工、质检和成品入库的制造契约

- [ ] **Step 1:** 为工单增加可选来源销售订单行，明确该关联由人工建立且不自动计算需求。
- [ ] **Step 2:** 新增工序执行、生产领料和生产退料模型，区分工单意图、派工安排和实际执行。
- [ ] **Step 3:** 补充工序执行开始、暂停、恢复、完成接口，以及领料、退料接口。
- [ ] **Step 4:** 规定 MES 只能通过库存应用服务改变原料和成品库存，禁止直接修改余额。
- [ ] **Step 5:** 更新页面字段、状态机、错误码和验收场景，覆盖领料到成品入库的完整闭环。
- [ ] **Step 6:** 运行 `rg -n "OperationExecution|MaterialIssue|MaterialReturn|source_sales_order_line_id" docs/specs/20-mes`，四个规格文件均应覆盖相应概念。

### Task 5（已完成）: 同步 IoT 事实与 IT/OT 边界

**Files:**
- Modify: `docs/specs/30-iot-digital-twin/概述.md`
- Modify: `docs/specs/30-iot-digital-twin/领域模型.md`
- Modify: `docs/specs/30-iot-digital-twin/接口契约.md`
- Modify: `docs/specs/30-iot-digital-twin/验收标准.md`

**Interfaces:**
- Consumes: `DeviceProfile`、`DeviceTelemetry`、`DeviceStatus`、`DeviceAlarm`、工序执行上下文
- Produces: 独立于 Core 的 MQTT 采集、状态、告警和业务上下文补充契约

- [ ] **Step 1:** 新增设备模型、凭证或身份、消息标识字段，明确 Telemetry 不等于 DeviceStatus。
- [ ] **Step 2:** 使用 `device_id + message_id/sequence` 去重 QoS 1 重复消息。
- [ ] **Step 3:** 将设备静态关联调整为工作中心、生产区域和地图点位；告警上下文关联工序执行，允许人工补充。
- [ ] **Step 4:** 固定“先保存遥测、再更新状态、再生成告警、最后补业务上下文”的失败隔离顺序。
- [ ] **Step 5:** 更新 API、错误码和测试，覆盖重复消息、Core 不可用、遥测保存失败和跨租户隔离。
- [ ] **Step 6:** 运行 `rg -n "message_id|sequence|DeviceProfile|OperationExecution|Core" docs/specs/30-iot-digital-twin`，四个规格文件应完整覆盖新边界。

### Task 6（已完成）: 同步 GIS、看板与 AI 查询规格

**Files:**
- Modify: `docs/specs/40-gis-dashboard/概述.md`
- Modify: `docs/specs/40-gis-dashboard/领域模型.md`
- Modify: `docs/specs/40-gis-dashboard/接口契约.md`
- Modify: `docs/specs/40-gis-dashboard/验收标准.md`
- Modify: `docs/specs/50-ai-assistant/概述.md`
- Modify: `docs/specs/50-ai-assistant/领域模型.md`
- Modify: `docs/specs/50-ai-assistant/接口契约.md`
- Modify: `docs/specs/50-ai-assistant/验收标准.md`

**Interfaces:**
- Consumes: Core 与 IoT 提供的只读查询模型
- Produces: 不建立第二事实源的地图、看板、追溯和 AI 工具契约

- [ ] **Step 1:** 明确地图和看板只消费库存、生产、设备、告警与追溯查询模型。
- [ ] **Step 2:** 增加订单履约、库存异常和受影响工序的展示摘要，不增加通用异常明细表。
- [ ] **Step 3:** 为 AI 增加只读 `queryTrace` 或等价追溯工具，并规定所有工具经过应用服务授权。
- [ ] **Step 4:** 保留三维和 RAG 为二期资产，但从一期页面入口和当前能力文案中移除。
- [ ] **Step 5:** 运行 `rg -n "第二套事实|追溯|只读|RAG|Cesium" docs/specs/40-gis-dashboard docs/specs/50-ai-assistant`，确认一期与二期边界清楚。

### Task 7（已完成）: 同步原型与前端壳文案

**Files:**
- Modify: `prototype/README.md`
- Modify: `prototype/index.html`
- Modify: `prototype/assets/app.js`
- Modify: `prototype/pages/master-data.html`
- Modify: `prototype/pages/purchase-inbound.html`
- Modify: `prototype/pages/sales-outbound.html`
- Modify: `prototype/pages/work-order.html`
- Modify: `prototype/pages/device-alarm.html`
- Modify: `prototype/pages/dashboard.html`
- Modify: `prototype/pages/site-map.html`
- Modify: `prototype/pages/ai-assistant.html`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/views/PrototypeHome.vue`

**Interfaces:**
- Consumes: 更新后的项目和领域规格
- Produces: 与黄金闭环、一期范围和新术语一致的静态交互基线

- [ ] **Step 1:** 将原型总览从六条并列主线改为一条黄金闭环及若干支持能力。
- [ ] **Step 2:** 更新采购、销售、制造和设备页面的字段、动作、接口、状态和说明。
- [ ] **Step 3:** 将“冻结库存”改为“库存预留”，将收货和上架、拣货和发货的语义分别展示。
- [ ] **Step 4:** 在工单页增加来源销售行、领退料和工序执行；在设备页增加消息标识、三类设备事实和工序上下文。
- [ ] **Step 5:** 将三维和知识库保留为二期原型资产，但从 Vue 一期导航摘要中移除。
- [ ] **Step 6:** 运行 `npm run build`，预期 Vue/TypeScript/Vite 构建成功。

### Task 8（内容完成，待 Docker CLI 复验）: 收敛一期部署编排

**Files:**
- Modify: `deploy/docker-compose.yml`
- Cross-check: `docs/specs/00-project/架构设计.md`
- Cross-check: `AGENTS.md`
- Cross-check: `docs/项目目录结构.md`
- Cross-check: `README.md`

**Interfaces:**
- Consumes: 一期中间件边界
- Produces: 仅包含 PostgreSQL、Redis、Mosquitto 的活动 Compose 编排

- [ ] **Step 1:** 保留 PostgreSQL 5433、Redis 6379 和 Mosquitto 1883 的现有有效配置。
- [ ] **Step 2:** 从活动 Compose 中移除 RabbitMQ 和 MinIO 服务；二期选择只保留在文档中。
- [ ] **Step 3:** 运行 `docker compose config`，预期配置解析成功且服务列表只包含 `postgres`、`redis`、`mosquitto`。

### Task 9（除 Docker CLI 外已完成）: 全仓一致性与构建验证

**Files:**
- Verify: 全部本次修改文件

**Interfaces:**
- Consumes: Tasks 1–8 产物
- Produces: 可供用户审查和提交的统一工作区

- [ ] **Step 1:** 搜索旧定位、旧库存字段和一期越界能力，逐条确认只存在于历史或二期说明中。
- [ ] **Step 2:** 运行 Markdown/文本占位符、行尾空白、UTF-8 替换字符检查。
- [ ] **Step 3:** 运行 `npm run build` 验证前端文案改动。
- [ ] **Step 4:** 运行 `mvn package` 验证后端工程未因联动修改受损。
- [ ] **Step 5:** 运行 `docker compose config --services` 验证一期依赖列表。
- [ ] **Step 6:** 运行 `git diff --check` 和 `git status --short`，确认没有提交、没有编译产物被纳入修改。
