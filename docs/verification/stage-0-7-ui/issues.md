# 阶段 0–7 页面操作贯通问题台账

基线日期：2026-09-07。角色与账号只记录非敏感的租户/角色标识；不记录密码、JWT、HMAC 或完整凭据。表格保留第 0 批问题快照；第 1 批结果见文末，未列出的后续状态不能视为已修复。

| 编号 | 优先级 | 账号角色 | 来源方式 | 菜单 / URL / 按钮 | HTTP 状态 / 业务码 | 实际结果 | 预期结果 | 证据等级 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | P0 | `tenant_demo_a` / `tenant.admin` | 菜单点击 | “采购订单” → `/purchase/orders` | 无业务请求；Router 最终 `/` | 点击后停留首页；同类旧路径共 9 条未注册 | 迁移到 `/purchasing/orders`，保留业务语义与查询上下文 | A+B，浏览器复核 |
| F02 | P0 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/dashboard`；另测 `/gis/site-maps` | 七个 dashboard 请求均 HTTP 404；页面日志为“请求的静态资源或接口不存在” | 看板七卡错误；地图/追溯事实源在当前运行方式下不一定装配 | 先取证 active profile 和条件装配，再让已注册端点按权限返回真实数据或明确拒绝 | A+B+C，浏览器复核 |
| F03 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/mes/dispatch`；“新建派工单” | 列表请求 HTTP 403，业务提示“没有操作权限” | 无权限态仍展示可打开的创建入口 | 读/写权限分别反映在页面，缺写权限不显示或禁用写按钮 | A+B，浏览器复核 |
| F04 | P1 | `tenant_demo_a` / `tenant.admin` | 手输详情 URL / 列表抽屉 | 采购、销售、调拨 `/:id` 详情 | 本批未执行写入；代码直接确认根组件 `visible=false` | 抽屉可看不等于详情直达、刷新和追溯穿透可用 | 由页面宿主读取 `route.params.id`，主动加载详情并传入 `visible=true` | B，代码确认 |
| F05 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/master-data`；商品、库位、客户、供应商分支 | 商品读取及创建表单打开；仓库/UOM入口未调用 | 不能只靠当前视图从零建立仓库和 UOM；UOM 是自由文本 | 主数据页签覆盖仓库/UOM，真实目录可建、改、停用并供业务选择 | A+B，浏览器与代码复核 |
| F06 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/sales/picks`；表格行 | 列表可读；本批未执行写入 | 仅有订单队列；任务号和更新时间为 `-`，无详情/拣货/发货可操作入口 | 订单驱动履约队列展示真实数量并可进入既有直接拣货/发货操作 | A+B，浏览器复核 |
| F07 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL / 表单打开 | `/mes/dispatch`；工单、工序、操作员、设备 UUID 输入 | 列表 HTTP 403；创建表单仍可打开 | 普通用户需要猜 UUID，非法关联最终只能看到后端拒绝 | 工单冻结工艺、受控操作员目录、真实设备选择器；允许无设备人工工序 | A+B，浏览器与代码复核 |
| F08 | P1 | 生产质检角色待运行 | 手输工单详情 URL | 工单详情后的生产质检入口 | 本批未执行；API 已定义 create/submit/close，视图无调用 | 报工后没有生产质检页面入口，不能形成成品入库前质量阻塞 | 选择真实 `workReportId`，创建/提交/关闭质检并复读工单事实 | B，代码确认 |
| F09 | P1 | 正式角色待运行 | 手输/页面动作待运行 | MES 动作按钮与写请求 | 本批未执行；代码中部分 `isActionAllowed` 缺动作时返回 true，普通 Error 丢状态/业务码/request_id | 按钮可能误开放；超时重试会自动换幂等键，无法区分结果不确定 | 契约承诺的动作缺失/未知即拒绝；保留错误元数据；同一用户命令复用幂等键 | B，代码确认 |
| F10 | P1 | `buyer.chen` / `wh.operator` 待运行 | 菜单点击待运行 | 采购详情、建单选项、质量隔离/收货暂存库位 | 本批未执行；代码含 QH-01/RS-01 兜底及 `Promise.all` 可选来源耦合 | 可能把默认库位/未返回的数量当作事实；可选工单失败会拖垮必填选项 | 使用真实投影、逐项加载态与合法库位选择，未返回数量不伪造为已发生 0 | A+B，代码确认 |
| F11 | P1 | `tenant_demo_a` / `tenant.admin` | 手输 URL | `/dashboard`；SummaryCard 新鲜度标签 | 七个源请求 HTTP 404；卡片仍显示“实时” | 错误状态与实时新鲜度矛盾 | 只有成功且明确 fresh 才显示“实时”；错误/旧缓存显示不可用或 stale | A+B，浏览器与代码复核 |
| F12 | P1 | `iot.engineer` | 隔离 Broker + Gateway API | IoT 遥测、告警、GIS/追溯上下文 | 隔离 Mosquitto `18884` 真实 QoS1 已执行；外部 `1883` 未加载仓库 ACL，生产区域 Facts/跨服务上下文未取证 | 不能以 HTTP simulate 或服务 health=UP 宣布生产实机闭环 | 隔离 Broker 已证明同键去重、异载荷冲突、告警触发/恢复/确认；生产 Broker 与上下文事实仍需独立验收 | B，隔离实机；生产环境待验证 |

## 本批红灯结果

使用同一 `tenant_demo_a / tenant.admin` 真实页面复核：

- F01：点击“采购订单”后最终 URL 仍为 `http://localhost:5173/`；
- F02：`/dashboard` 的 `inventory`、`fulfillment`、`manufacturing`、`quality`、`device`、`alarms`、`traceability` 七个请求均收到 HTTP 404；
- F03：`/mes/dispatch` 列表收到 HTTP 403，页面仍显示“新建派工单”；
- F06：`/sales/picks` 只显示订单队列，按钮列表无详情、直接拣货或发货动作；
- F11：F02 的 404 错误卡片仍标记“实时”。

前端错误日志中可见脱敏后的 HTTP 状态与消息；页面没有展示可复制 `request_id`，因此本批不虚构业务码或请求号。后续第 1–3 批修复后，应重新填写对应行的浏览器步骤、HTTP 状态/业务码和证据位置。

## 第 1 批结果（2026-09-07）

- F01 已修复：旧 `/purchase/orders` 和真实菜单点击均最终进入 `/purchasing/orders`，采购列表成功请求真实 `/api/purchase-orders`。
- F04 已修复：采购、销售、调拨详情均支持列表复用、详情直链和刷新；直链使用真实详情 ID，请求分别返回 200，关闭后返回对应业务列表。
- 旧主数据路由已覆盖：`/master-data/warehouses` 最终进入 `/master-data?tab=warehouses`，页面激活仓库与库位页签；当前验证角色无该 API 权限时显示明确的 403 提示。

## 第 3 批结果（2026-09-07）

- F03 已修复：`DispatchView.vue` 与各核心业务页面接入 `actionGuard.ts` 与权限裁剪；`tenant.admin` 等无派工写权限账号访问 `/mes/dispatch` 时收到 403 拒绝态，页面不渲染“新建派工单”按钮，彻底消除违规操作诱导。
- F09 已修复：
  1. 动作判定安全闭环：新增 `frontend/src/utils/actionGuard.ts`，严格遵循后端 `AllowedActionVo` 契约，当动作在 allowedActions 中不存在或 enabled=false 时坚决返回 false，消除原部分视图缺失动作默认返回 true 的漏洞；
  2. 错误元数据保留：`frontend/src/utils/request.ts` 改造响应拦截器，统一捕获并保留 HTTP 状态码、业务 code、脱敏 request_id 及详细 message，不再抹平成模糊 Error；
  3. 幂等性防重：建立 `frontend/src/composables/useCommand.ts`，为业务命令生成并在失败重试时复用同一逻辑 `Idempotency-Key`，网络超时重试不会因更换密钥造成重复记账。

## 第 4 批结果（2026-09-07）

- F05 已修复：
  1. `MasterDataView.vue` 补齐 `warehouses`（仓库）与 `uoms`（计量单位）独立维护页签与表格列，支持查看编码、名称、状态、负责人及备注；
  2. `MasterDataEditor.vue` 扩充仓库与 UOM 增改表单，且商品创建中的计量单位改造为从 `getUoms` 动态加载的下拉选择框，禁止自由输入乱码或手填 UUID；
  3. 彻底修复 `?tab=warehouses` 被强行别名重定向到库位的缺陷，实现 URL `?tab=` 参数与当前激活页签双向同步。
- F07 已修复：
  1. `DispatchView.vue` 新建派工弹窗中全面移除盲猜手填工单、工序、操作员、设备 UUID 的输入框；
  2. 改造为结构化级联选择器：工单从 `getWorkOrders` 动态过滤已下达（Released）工单；工序联动选中工单的冻结工艺快照（Routing）；操作员提供受控人员目录；设备从 `getDevices` 动态拉取，并支持人工工序留空。
- F10 已修复：
  1. `PurchaseOrderListView.vue` 建单弹窗的选项加载改造为 `Promise.allSettled`，即使当前用户无工单权限导致工单接口报 403，也绝不拖垮供应商、仓库、物料等必填选项；
  2. `PurchaseOrderDetailView.vue` 彻底移除 `'QH-01'` 与 `'RS-01'` 硬编码兜底，未指定库位时展示真实“待分配”态，杜绝伪造库位业务事实。

## 第 9 批规格记录（2026-09-07，历史执行记录）

- 端到端黄金闭环规格落地（仅规格与前置探测，不等于真实业务闭环通过）：
  1. 新增 `frontend/e2e/golden-flow.spec.ts` 与 `frontend/playwright.config.ts`，基于计划第 5 节规范完整编写了包含主数据、销售需求、制造工单、原料采购、外观接收、质检放行上架、领料执行、IoT 遥测告警去重、报工质检入库、销售直接拣货发货、采购人工终止与全域追溯对账的 11 个业务阶段，以及 403 权限隔离、跨租户物理隔离与幂等防重的端到端自动化回归套件；
  2. 规格严格面向正式 6 类角色独立运行，动态生成独立运行号 `UIR0907-<runId>`，杜绝污染正式数据。
- 跨批依赖阻塞台账与交接（严禁虚构“全通过”）：
  - **F02 / F11（看板七卡与新鲜度）**：归入第 2 批；依赖后端聚合接口返回真实业务事实。当前测试已包含路径与卡片状态核验。
  - **F06（销售直接拣货与发货队列）**：第 6 批代码级修复已有专项证据；Task 10 仍需用同一清洁样本完成 FGR 库存、预留和两次发货对账，不能沿用旧的 `[BLOCKED_BY_BATCH_6]` 表述。
  - **F08（工单生产质检入口）**：第 7 批代码级修复已有专项证据；Task 10 仍需取得真实 `workReportId/inspectionId` 并复读状态，不能沿用旧的 `[BLOCKED_BY_BATCH_7]` 表述。
  - **F12（IoT MQTT 实机链路与去重）**：第 9 批代码级模拟和专项回归已有证据；真实 Mosquitto QoS1 消息、ACL、设备 credential 仍是环境阻塞，不能沿用旧的 `[BLOCKED_BY_BATCH_8]` 表述。
- 自动化构建与验证：
  - `npm run build`（vue-tsc 严格类型检查 + Vite 生产构建）执行通过，退出码 0；
  - `npx playwright test e2e/golden-flow.spec.ts --list` 成功解析 12 个测试用例；
- `git diff --check` 检查通过，退出码 0。

## 阶段 0–9 返工 Task 0 当前阻塞（2026-09-07 20:04）

- 已重新记录当前工作树、Java/Maven/Node 工具链、监听端口、实际 active profile 和 PostgreSQL/Flyway 只读事实；没有覆盖已有未提交改动。
- `npm run build` 已通过，退出码 0；该结果仅是前端构建证据。
- 为取得浏览器基线，本次仅启动 Gateway `20001` 与 Vite `5173`；Auth/Core/IoT 现有进程未重启，数据库未迁移、未清库。
- `npm run test:e2e` 因没有 `STAGE_UI_PASSWORD` 或受保护的 `STAGE_UI_STATE_FILE` 在认证入口停止；脚本没有猜测或保存密码。因此 F01、F02、F03、F11 均保持“待认证后执行”，不能沿用历史结果作为当前通过证据。
- 继续执行前需要提供受保护的认证上下文（环境变量或 storage state 文件路径），不得在聊天、命令历史、报告或仓库中发送明文密码。

### 认证上下文补齐后的 Task 0 结果（2026-09-07 20:14）

- 使用本地演示账号 `tenant_demo_a / admin.zhang` 运行 `npm run test:e2e`；密码仅作为当前进程输入，未写入源文件。
- F01 **FAIL**：采购菜单点击后路径仍为 `/`，没有进入 `/purchasing/orders`。
- F02 **FAIL**：`/dashboard` 未观测到七个 `/api/dashboard/*` 请求，不能写成七接口通过。
- F03 **PASS**：`tenant.admin` 为明确 403/无权限态，创建派工按钮隐藏。
- F11 **PASS（当前断言范围）**：没有错误卡片同时显示“实时”；结果仍需 Task 4 的故障、stale 和 no-cache 样本补强。
- Task 0 的基线、工具链、端口、profile、Flyway 和 F01/F02/F03/F11 台账已完成；后续按计划进入 Task 1。

## 阶段 0–9 返工 Task 1 实际执行结果（2026-09-07）

- `npm run test:e2e`：退出码 0；管理员动态菜单读取到 23 个可见叶节点，逐项记录请求路径、最终路径和 `OK/FORBIDDEN` 结果；F01、F02、F03、F11 当前断言通过。MES 派工节点明确为 `FORBIDDEN`，没有静默回首页。
- `npm run test:e2e:golden`：退出码 1，7 条通过、5 条明确失败。步骤 1–4、7、11 与权限负向通过；步骤 5 在 `/purchasing/receipts` 未发现 `button.act-receive`，步骤 6、8、9、10 分别输出 `BLOCKED_BY_BATCH_5/8/7/6`。
- 步骤 5 的失败是当前收货工作台动作/权限或可收货数据缺口，不再是原先误匹配“部分收货”筛选按钮；不能把它解释成收货业务已实现。
- 黄金测试清理逻辑已覆盖 `.modal-mask`、`.modal-overlay` 与 `.editor-mask`，本次没有再出现弹窗遮罩拦截退出导致的 45 秒 `afterEach` 超时。
- `npm run build`、`npm run test:e2e:list` 和 `git diff --check` 均通过；Chromium 安装与真实浏览器运行完成。测试密码仅作为当前进程环境变量存在，未记录在台账中。

## Task 9–10 当前状态（2026-09-08）

### F01–F11：代码级与专项浏览器证据

- F01/F03/F04/F05/F07/F09/F10：对应路由、详情、权限守卫、主数据目录、级联选择器、错误元数据/幂等和采购选项容错已在前置批次记录专项证据；这些证据不替代跨领域黄金流数量对账。
- F02/F11：`frontend/e2e/insights.spec.ts` 覆盖七卡请求、`time_range=today`、stale/no-cache 状态，Chromium `4/4`；缺失服务端事实显示不可用或“未返回”，不转成 0 或“实时”。
- F06：销售直接拣货/分批发货的 Core 与页面专项回归已有证据；最终 F=2 的 `onHand/reserved/available/shipped` 仍需在 Task 10 清洁样本复读。
- F08：生产质检、FGR 和数量闸门已有 Core/页面专项回归；最终工单链必须由真实 `operationExecutionId`、`workReportId`、`inspectionId` 串接。

### F12：真实 IoT 隔离链路与生产环境边界

- IoT 全量 Java `70/70`、Core S7 目标 `12/12`、洞察 Chromium `4/4` 通过；另外已在临时 Mosquitto 2.1.2 `18884` 以账号/ACL 方式启动第二个 IoT 实例，完成真实 QoS1 消息链路。
- 同一 `message_id` 的两次 QoS1 发布只保存两条指标事实（一个消息、两个指标）；同键异载荷由 IoT 记录 `IOT_TLM_003` 并拒绝，未新增事实；温度越阈触发告警，恢复消息与人工确认完成 `Triggered → RecoveredUnacked → Recovered`。
- 现有 `1883` 仍由外部 Windows Mosquitto 服务占用，命令行未加载仓库 `deploy/local/mosquitto.conf`；生产 `1883` ACL、`production_area` Facts 和 Core 上下文补链仍不能由隔离 fixture 代替。

### Task 10 Golden Flow 历史前置探测结果

`frontend/e2e/golden-flow.spec.ts` 已去掉过时的 `BLOCKED_BY_BATCH_5/6/7/8`，改用事实/环境门禁：

- 步骤 1–4、7、11 的 7 条绿色结果仅表示页面或表单入口可访问；测试当前没有提交并保存一套完整的服务端实体 ID，因此不计入黄金业务通过；
- 步骤 5–6：缺少真实 `Approved purchaseOrderId/receiptId/receiptLineId`；
- 步骤 8：缺少项目 Broker ACL、订阅账号和设备 credential；
- 步骤 9：缺少真实 `operationExecutionId/workReportId/inspectionId`；
- 步骤 10：缺少 FGR 成品库存事实，不能核对预留与两次发货。

执行结果为 `7 条前置探测通过、5 条事实/环境门禁失败`。这不是 Task 10 完成，也不能把 F01–F12 写成全部通过。下一步需在受控清洁租户中串行创建数据，并从每个写接口响应复读 ID、数量、状态和租户；如无法提供真实 Broker 条件，F12 继续保持环境阻塞。

## Task 10 真实事实复核补充（2026-09-08）

- F06/F08 及采购—制造—销售主链已由 `frontend/e2e/golden-facts.spec.ts` 使用动态清洁样本实际串行执行；不再依赖旧测试中的 `[BLOCKED_BY_BATCH_*]` 文案。
- 数量证据：采购到货 `3/1/2`（到货/拒收/实收），质检放行与上架不重复增加实物，领料扣减原料 `2`，报工合格 `2`，FGR 成品 `2`，两次各 `1` 发货后销售行累计 `2/2/2`、订单 `Completed`，成品库存最终 `0/0/0`（onHand/reserved/available）。
- ID 证据：收货、采购质检、质量处置、上架任务、派工、工序执行、报工、生产质检、FGR、拣货和发货均使用接口响应返回的 ID；测试源码不保存固定业务 UUID。
- 权限负向：`tenant.admin` 对派工写接口收到 HTTP/业务 403；页面复读使用销售角色和本次真实订单详情。
- F12 已有隔离 Broker 的真实 QoS1 与告警证据；外部 `1883` 的仓库 ACL、生产区域 Facts 和跨服务上下文仍是未验证边界，HTTP simulate 不替代真实证据。

## 最终验收矩阵补充（2026-09-08）

- 前端单元 `13/13`、构建 `PASS`；菜单与 Dashboard 回归 `F01/F02/F03/F11` 全部 `PASS`。F02 七个聚合接口均返回 200，F11 `errorCards=0/liveBadges=7/unavailable=0`。
- `golden-facts.spec.ts` Chromium `1/1` 通过：真实清洁样本对账为 `3 = 1 + 2`、原料领料后 `0`、FGR `2`、销售行 `2/2/2`、成品 `0/0/0`；UOM 同键重放与异载荷冲突、未关闭 Failed 质检阻断 FGR、库存不足阻断超量拣货均已实际执行。
- Java 全量矩阵为 Core `155/155`、IoT `70/70`、Gateway `11/11`、Auth `40/40`，均无失败/错误/跳过；Auth 静态迁移脚本 `6/6`，临时 PostgreSQL 12.1 夹具上的真实迁移 `4/4`，测试后夹具已停止。
- F12 为“隔离 Broker 部分通过”：QoS1 同键重放、异载荷冲突、告警触发/恢复/确认已实际执行；生产 `1883` ACL、`production_area` Facts 和 Core 上下文补链仍未验证，不得扩大结论。
- S7 Dashboard 的原始 404 已由运行条件修复：Core/IoT 必须在进程环境中注入成对开关、Base URL 和 HMAC；敏感值未写入仓库。

## Task 10 负向、恢复与导航专项收口（2026-09-08）

- Step 4 已完成：真实黄金事实套件执行 tenant.admin 派工 403、跨租户应用服务/集成用例、同键重放、异载荷冲突、库存不足、非法状态和未关闭 Failed 质检阻断 FGR；超时/网络不确定结果保持同一幂等键并在冲突时停止。
- `golden-flow.spec.ts` 修正错误/跨域文档下的 logout 清理后为 `7 passed、5 expected skip`；`manufacturing.spec.ts` 使用 `mes.inspector` 为 `4/4`。
- `detail-navigation.spec.ts` 为 `1 passed、1 expected skip`：采购、销售、调拨真实详情均验证深链、刷新、后退、前进；另一个 404/403 模拟用例因无默认账号按认证门禁跳过。
- `dashboard-states.spec.ts` + `insights.spec.ts` 为 `5/5`，覆盖空/缺失指标、503 加载失败、stale/no-cache、HTTP simulate 标识、告警确认与追溯断链。
- 完整 Chromium 组合回归为 `23 passed、11 skipped、0 failed`；跳过项均为无真实详情 ID、Golden 事实/MQTT 门禁、管理员无 MES 写权限或无权路由，均已在测试中显式声明。
- F12 仍严格分层：隔离 Broker 的真实 QoS1/告警生命周期已通过；外部 `1883` 仓库 ACL、`production_area` Facts 和 Core 上下文补链仍未验证，不能写成生产通过。
- 外部 `1883` 只做了只读 `$SYS/broker/version` 探测：`D:\ruanjian\Mosquitto\mosquitto.conf` 未启用仓库 ACL/密码项，匿名连接成功并返回 Mosquitto `2.1.2`。因此 Broker 可达不等于设备凭证/ACL 通过，未向业务 topic 发布消息。
