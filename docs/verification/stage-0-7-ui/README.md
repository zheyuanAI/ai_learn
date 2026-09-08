# 阶段 0–7 页面操作贯通：基线与第 1 批验证

本目录保存阶段 0 的可重复环境摘要、F01–F12 问题台账和真实页面回归入口。基线章节记录第 0 批事实；后续批次只在有对应浏览器、服务端事实和数量证据时标记通过。

## 当前批次

- 计划：`docs/superpowers/plans/2026-09-07-stage-0-7-ui-repair-plan.md`
- 执行批次：第 0 批（基线）、第 1 批（路由与详情）、第 3 批（动作守卫与幂等）、第 4 批（主数据与选择器）、第 5–9 批（代码级修复与专项回归）已执行；第 10 批正在进行最终黄金流交接
- 基准日期：2026-09-08
- 工作树：`chore/local-development-baseline`，严格遵循未提交工作树基线，不提交 Git，不清库，不覆盖已有改动
- 问题台账：[issues.md](issues.md)

## 脱敏环境摘要

| 项目 | 基线事实 | 证据/说明 |
| --- | --- | --- |
| Frontend | `http://localhost:5173` | 当前 Vite `npm run dev` 监听；前端构建产物最近时间为 2026-09-05 09:33:43 |
| Gateway | `20001` | `platform-gateway` JVM 监听；根 `.run` 配置显示名仍写作 `GatewayApplication (10001)`，实际 `application.yml` 为 20001 |
| Auth | `10002` | `platform-auth` JVM 监听 |
| Core | `10003` | `platform-core` JVM 监听 |
| IoT | `10004` | `platform-iot` JVM 监听 |
| PostgreSQL | `127.0.0.1:5433/ai_learn`，12.1 | 只读连接确认 `PostgreSQL 12.1` |
| Redis | `6379` | 当前监听地址含 `0.0.0.0`/IPv6；按 `runtime/README.md` 不把它直接解释为可复用安全基线 |
| Mosquitto | `1883` | 当前监听为外部 Windows 服务，未加载仓库 ACL；本轮另用临时隔离 listener `18884` 完成真实 QoS1 验证，生产 1883 仍未验收 |
| Java | Temurin `21.0.12.1` | `runtime/jdk/bin/java.exe -version` |
| Node / npm | `v20.19.2` / `10.8.2` | 当前命令行版本 |
| 启动入口 | IDEA Spring Boot 启动四服务 + Vite `npm run dev` | 运行中的 JVM classpath 指向本项目 `backend/*/target/classes`；前端进程为 `npm run dev` |
| Active profiles | 运行 JVM 命令行未见 `spring.profiles.active`；Spring Environment 未通过当前暴露端点取证 | `platform-core`/`platform-iot` 的 `application-dev.yml` 已存在，但不能据此声称当前已激活；留给第 2 批核实 |
| 构建时间 | 后端 JAR 最近生成于 2026-09-05 09:41:52–09:41:57；前端 `dist` 最近生成于 2026-09-05 09:33:43 | 仅表示产物时间，不替代本批构建验证 |

### Flyway history（第 0 批基线）

| 模块 | 最新版本 | 说明 | success |
| --- | ---: | --- | --- |
| Auth | V7 | `stage 5 formal permissions` | `true` |
| Core | V8 | `mes material overage reason` | `true` |
| IoT | V2 | `device mqtt telemetry status alarm` | `true` |

第 0 批记录的数据库版本、三张 history 表和 success 状态均来自本地 PostgreSQL 12.1 的只读查询；当时未执行迁移、清库或重启。

## 浏览器回归入口

前端已增加真实 CLI 回归命令：

```powershell
Set-Location 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
$env:STAGE_UI_TENANT_CODE = 'tenant_demo_a'
$env:STAGE_UI_USERNAME = 'admin.zhang'
$env:STAGE_UI_PASSWORD = '<从本机安全环境注入，不写入命令历史/报告>'
npm run test:e2e
```

也可以使用受保护的 Playwright storage state，设置 `STAGE_UI_STATE_FILE` 后无需向脚本传入密码。脚本使用独立 `stage-0-7-ui` session，只导航和读取真实服务；输出目录为被 Git 忽略的 `output/playwright/stage-0-7-ui/`。测试中的三个红色断言为：

1. 从菜单点击“采购订单”后应到 `/purchasing/orders`，不能回 `/`；
2. `/dashboard` 的七个 `/api/dashboard/*` 请求不能返回 404；
3. `tenant.admin` 在 `/mes/dispatch` 收到明确无权限态时，不应看到“新建派工单”。

第 0 批基线预期为红灯：F01、F02、F03 尚未修复时，`npm run test:e2e` 应以退出码 1 结束；不能把该红灯忽略为成功。第 1 批后重复执行，F01 已转绿，F02/F03 仍按后续批次处理。

## 浏览器工具边界

- 已用 Codex Chrome 已登录会话复核了 F01、F02、F03、F06；只进行了页面导航和读取，没有创建/修改业务单据。
- `agent-browser` CLI 自检通过，但本机自动 Chrome 在快照阶段 DevTools 连接超时；该工具故障不作为应用断点结论。
- Playwright CLI 已能连接本地前端；真实菜单回归脚本使用项目内 `@playwright/cli`，不使用 mock 代替真实服务。
- 第 0 批不启动/停止 Redis、Mosquitto、PostgreSQL 或业务服务，不执行数据库迁移。

## 第 1 批实施结果（2026-09-07）

- 路由统一：新增旧菜单路径兼容映射、正式业务路由和可返回的 `NotFoundView`；旧 `/purchase/orders` 已跳转到 `/purchasing/orders`，旧 `/master-data/warehouses` 已跳转到 `/master-data?tab=warehouses` 并保持仓库页签。
- 菜单迁移：Auth 的 V8 已通过 Flyway 应用，内置菜单正式路径与当前前端页面一致；迁移后仅对验证账号退出并重新登录以刷新该账号菜单缓存，没有清空整库 Redis。
- 详情直达：采购、销售、调拨详情宿主均从 `route.params.id` 主动加载真实详情，复用原详情组件；列表关闭动作返回对应业务列表，调拨详情刷新后仍显示后端明细行数量和批次。
- 未知与错误反馈：不存在的 URL 显示可返回的 404；无权限采购详情显示明确的权限错误，不留下空白业务区。
- 实际验证：`npm run build` 通过；`AuthMigrationScriptTest` 5/5 通过；真实 `/api/me/menus` 枚举到的 11 个可见叶节点均未落入 404，`/ai/trace` 的正式重定向最终落到 `/traceability`；采购、销售、调拨真实详情请求均返回 200 并显示详情。
- 范围边界：完整 `npm run test:e2e` 中 F01 通过；F02 的 dashboard 七接口和 F03 的派工写权限按钮仍是后续第 2/3 批范围，当前红灯属于未完成批次，不能据此宣称整套计划完成。

第 1 批之后 Auth history 的当前事实为：Auth V8 `align builtin menu routes`，`success=true`；Core 仍为 V8，IoT 仍为 V2。Auth 服务曾按本项目 Maven 入口重启以应用 V8，其他基础依赖和业务服务未被清空或重启。

## 第 3 批实施结果（2026-09-07）

- **动作守卫与安全默认（F09）**：
  - 新增 `frontend/src/utils/actionGuard.ts`，严格遵循后端 `AllowedActionVo` 契约：仅在匹配且 `enabled=true` 时返回 true，动作不在集合中或集合为空时默认拒绝（false），彻底杜绝前端越权渲染。
  - 在 14 个核心业务视图（工单、BOM、工艺、派工、物料、报工、成品入库、设备、告警、主数据等）全面接入 `isActionAllowed` 与 `getActionDisabledReason`。
- **请求拦截与命令幂等性（F09）**：
  - 改造 `frontend/src/utils/request.ts`，响应拦截器统一保留 HTTP 状态码、业务码、脱敏 `request_id` 及错误详情；
  - 封装 `frontend/src/composables/useCommand.ts`，业务命令提交与网络重试复用同一逻辑 `Idempotency-Key`，防止网络抖动导致的重复记账。
- **无权状态与按钮裁剪（F03）**：
  - 新增 `frontend/src/views/ForbiddenView.vue` 与 `usePermission.ts`；
  - `tenant.admin` 访问 `/mes/dispatch` 时呈现明确的 403 提示，并彻底隐藏“新建派工单”按钮。

## 第 4 批实施结果（2026-09-07）

- **主数据仓库与 UOM 维护入口闭环（F05）**：
  - `MasterDataView.vue` 新增 `warehouses`（仓库）与 `uoms`（计量单位）专属页签及完整表格列；
  - `MasterDataEditor.vue` 支持仓库（编码、名称、类型、负责人、地址等）与计量单位（编码、名称、符号、小数位等）维护；商品创建中的计量单位改造为从 `getUoms` 动态加载的下拉选择，禁止自由文本输入与 UUID 混淆；
  - 移除别名重定向，实现 URL 参数 `?tab=` 与界面当前页签的双向同步与刷新持久化。
- **派工单级联下拉选择器（F07）**：
  - `DispatchView.vue` 全面移除工单、工序、操作员、设备的纯文本手输框；
  - 改造为级联选择器：工单从 `getWorkOrders` 动态过滤 `Released` 状态工单；工序根据所选工单的冻结工艺快照（Routing）级联拉取；操作员提供受控用户列表；设备从 `getDevices` 动态拉取，支持人工工位留空。
- **采购建单容错解耦与真实库位（F10）**：
  - `PurchaseOrderListView.vue` 建单选项加载改用 `Promise.allSettled`，工单异常绝不拖垮供应商、仓库、物料等必填选项；
  - `PurchaseOrderDetailView.vue` 彻底移除 `'QH-01'` 与 `'RS-01'` 硬编码兜底，未指定库位展示为真实“待分配”占位。

## 第 9 批规格记录（2026-09-07，历史执行记录）

- **最小黄金业务闭环 E2E 规格落地**：
  - 新增 `frontend/e2e/golden-flow.spec.ts` 与 `frontend/playwright.config.ts`，严格按照计划第 5 节《统一验收数据与数量对账》定义 11 个业务阶段回归套件：
    1. 基础数据准备（仓库、库位、UOM、原料 R、成品 F）；
    2. 销售需求创建并审核（2 件 F，断言行 ID 且库存未变）；
    3. 制造工单创建与下达（关联销售行，冻结 BOM/工艺快照）；
    4. 原料采购订单创建（3 件 R，关联工单，无自动 MRP）；
    5. 外观接收（到货 3、拒收 1、实收 2，R 增加 2 位于 QualityHold，待收 1）；
    6. 质检放行与上架（质检 2 合格决定放行，仓库上架至 Storage，总实物 R=2 不变）；
    7. 领料与工序执行（申请并确认领料 2 R，实物扣减，派工并执行操作）；
    8. IoT 遥测告警去重（同键消息重复投递去重，关联工单上下文）；
    9. 报工、工单质检与成品入库（报工合格 2，质检 Passed，入库 2 F）；
    10. 销售履约直接拣货与分批出库（直接拣货 2 F，先发 1 再发 1，严格核对 onHand/reserved/available）；
    11. 采购人工终止与全域追溯对账（填写原因终止未收 1，全链路正逆向可追，看板只计真实发生数量）。
  - 包含 403 权限隔离、跨租户物理隔离与幂等防重负向测试用例。
- **跨批依赖诊断与交接门禁（历史记录，严禁虚构通过）**：
  - 当时黄金流只承担第 5–8 批依赖探测；后续专项修复已分别记录代码级证据，但真实清洁样本数量对账和 MQTT 环境证据仍由 Task 10 负责；
  - 旧测试曾输出 `[BLOCKED_BY_BATCH_5/6/7/8]`。该命名只保留为历史输出，当前测试改用 `GOLDEN_FLOW_BLOCKED_*` 指向缺失的事实 ID、数量或运行环境。
- **验证执行与结果**：
  - `npm run build`：执行 `vue-tsc --noEmit && vite build`，编译与类型检查 0 错误，打包耗时 6.14s，产物生成完整，退出码 0；
  - `npx playwright test e2e/golden-flow.spec.ts --list`：成功解析并列出全部 12 个端到端闭环与负向测试用例，退出码 0；
- `git diff --check`：无异常空白符或编码格式问题，退出码 0。

## Task 0 当前执行记录（2026-09-07 20:04，Asia/Shanghai）

本次按阶段 0–9 返工计划重新建立基线；此前工作树中的改动均视为用户资产，未执行 `reset`、`checkout`、清库、暂存或提交。

| 项目 | 当前事实 | 证据/边界 |
| --- | --- | --- |
| 工作树 | `chore/local-development-baseline`，已有未提交前端、文档和 E2E 改动 | `git status --short`、`git diff --stat`；不把历史改动归因于本次执行 |
| Java / Maven | 仓库 `runtime/jdk` 为 Java 21.0.12.1；Maven 3.9.1 绑定 shell 默认 Java 8，显式设置 `JAVA_HOME=runtime/jdk` 后才符合项目基线 | 直接 `mvn -version` 与绑定 Java 21 的 `mvn -version` 对照 |
| Node / npm | Node `v20.19.2`、npm `10.8.2` | `node --version`、`npm --version` |
| 服务监听 | Auth `10002`、Core `10003`、IoT `10004`、Gateway `20001`、Frontend `5173` | 现有 Auth/Core/IoT 未重启；本次仅为取得基线临时启动 Gateway 与 Vite 前端 |
| 依赖监听 | PostgreSQL `127.0.0.1:5433`、Redis `6379`、Mosquitto `1883` | Redis 仍绑定全地址，不能仅凭监听认定为安全复用 |
| Active profile | Auth/Gateway 未显式设置；Core/IoT 进程命令行实际为 `dev` | 进程命令行与 `.run/*.run.xml` 交叉取证 |
| PostgreSQL / Flyway | PostgreSQL `12.1`；Auth/Core/IoT 最新成功版本分别为 V8/V8/V2 | 只读 JDBC 兼容查询；未执行迁移 |
| 前端构建 | `npm run build` 通过，退出码 0 | 仅证明类型检查和打包，不代表业务通过；未产生需提交的 `dist` 变更 |
| 浏览器认证 | 未设置 `STAGE_UI_PASSWORD`，也未提供 `STAGE_UI_STATE_FILE` | `npm run test:e2e` 在认证入口处停止，未猜测密码或生成登录态 |

### F01/F02/F03/F11 当前台账

本次不能在缺少受保护认证上下文时声称四项业务结果。下表记录的是可复现阻塞，不是通过结论。

| 编号 | 当前执行结果 | 证据 | 下一步 |
| --- | --- | --- | --- |
| F01 | 未进入菜单断言 | `npm run test:e2e` 在认证前停止 | 注入受保护密码或 storage state 后重新运行 |
| F02 | 未进入看板接口断言 | 同上 | 登录后记录七个 `/api/dashboard/*` 状态 |
| F03 | 未进入派工权限断言 | 同上 | 登录后记录 `mes/dispatch` 403 与创建按钮状态 |
| F11 | 未进入卡片新鲜度断言 | 同上 | 登录后记录 error/stale/live 三态 |

Task 0 在认证上下文补齐前保持未完成；不得将构建通过、服务健康或未登录页面当作 F01/F02/F03/F11 业务证据。

### 认证上下文补齐后的补充执行（2026-09-07 20:14）

本次使用本地演示账号 `tenant_demo_a / admin.zhang` 完成真实页面回归；密码只注入当前测试进程，没有写入源码或报告。Gateway `20001` 与 Vite `5173` 为本次临时启动，测试结束后已停止。

| 编号 | 实际结果 | 证据 |
| --- | --- | --- |
| F01 | **FAIL**：点击采购菜单后的路径仍为 `/`，预期 `/purchasing/orders` | `npm run test:e2e`；Playwright 菜单回归输出 |
| F02 | **FAIL**：进入 `/dashboard` 后七个请求均未观测到，缺少 `inventory, fulfillment, manufacturing, quality, device, alarms, traceability` | 同上；当前不是“七接口均 200” |
| F03 | **PASS**：`tenant.admin` 得到明确拒绝态，未显示“新建派工单” | 同上，`denied=True/createButtonVisible=False` |
| F11 | **PASS（仅当前断言）**：未发现错误卡片与“实时”并存 | 同上，`errorCards=0/liveBadges=7`；仍需 Task 4 用故障/缓存样本验证三态 |

Task 0 基线现已完成；F01/F02 的红灯分别交由 Task 1/Task 4 修复，F03/F11 的当前通过不替代后续回归。

### 阶段 0–9 返工 Task 1 实际执行结果（2026-09-07）

本次在真实本地服务、真实演示角色和 Chromium 上执行；密码仅注入当前 PowerShell 进程，没有写入源码、报告或仓库。

| 命令 | 结果 | 事实证据 |
| --- | --- | --- |
| `npm install` | PASS | 依赖安装完成，退出码 0 |
| `npx playwright install chromium` | PASS | Chromium 与 headless shell 已安装，退出码 0 |
| `npm run test:e2e:list` | PASS | 解析 12 个黄金闭环/负向用例，退出码 0 |
| `npm run test:e2e` | PASS | 动态菜单叶节点 23 个；无静默回首页；F01/F02/F03/F11 当前断言均 PASS |
| `npm run test:e2e:golden` | **RED（真实阻塞）** | 7 passed、5 failed；步骤 5 未发现真实 `button.act-receive`，步骤 6/8/9/10 输出 `BLOCKED_BY_BATCH_5/8/7/6` |
| `npm run build` | PASS | `vue-tsc --noEmit && vite build` 退出码 0 |
| `git diff --check` | PASS | 未发现空白符错误 |

动态菜单回归的 23 个叶节点均记录了 `menuCode`、请求路径、最终路径和结果；其中 MES 派工节点为明确 `FORBIDDEN`，不是静默回首页。黄金测试的 `afterEach` 弹窗清理已修复，不再出现 45 秒清理超时。步骤 5 的失败属于当前收货动作/权限事实缺口，不能写成采购收货业务已通过。

## Task 9–10 当前交接（2026-09-08）

### Task 9：专项代码级回归已完成，真实 MQTT 保持环境阻塞

- IoT 全量 Java 测试 `70/70`、Core S7 目标测试 `12/12`、`frontend/e2e/insights.spec.ts` Chromium `4/4`、前端构建均通过。
- 告警、设备、地图和追溯页面缺失事实不再用固定指标、区域编码、库位编码或数量 0 兜底；HTTP simulate 明确标注为开发模拟。
- 当前 1883 由外部 Mosquitto Windows 服务占用，未加载仓库 `deploy/local/mosquitto.conf`；IoT 进程未注入项目 MQTT 订阅账号，匿名发布不能证明 ACL/QoS1。因此 `F12` 的真实重复/异载荷冲突仍未通过。

### Task 10：黄金流当前是前置探测，不是业务闭环通过

`frontend/e2e/golden-flow.spec.ts` 已把旧的 `BLOCKED_BY_BATCH_5/6/7/8` 改为事实/环境门禁：

- 步骤 1–4、7、11 的绿色结果只表示页面、菜单或表单入口可访问；尚未在测试中提交并复读完整实体 ID；
- 步骤 5–6 因没有服务端 `Approved purchaseOrderId/receiptId/receiptLineId` 失败；
- 步骤 8 因真实 MQTT ACL、订阅账号、设备 credential 未注入失败；
- 步骤 9 因没有 `operationExecutionId/workReportId/inspectionId` 失败；
- 步骤 10 因没有 FGR 库存事实失败。

本轮命令结果（密码只注入当前进程，不写入文件）：

| 命令 | 结果 | 解释 |
| --- | --- | --- |
| `npm run test:e2e:golden` | **RED：7 条前置探测通过、5 条事实/环境门禁失败** | 不能作为黄金流业务通过证据 |
| `npm run test:e2e` | PASS | 仅动态菜单和已有 F01/F02/F03/F11 断言 |
| `npm run build` | PASS | 类型检查与打包通过，不代表业务闭环 |
| `git diff --check` | PASS | 未发现空白符错误 |

下一步必须提供可重复的清洁租户样本运行条件，并让每个写接口响应的服务端 ID/状态/数量进入同一串行测试；在此之前不得勾选 Task 10 Step 1–4 或宣称 F01–F12 全部验收通过。

## Task 10 真实事实执行记录（2026-09-08 继续执行）

本轮已把实际黄金链路固化到 `frontend/e2e/golden-facts.spec.ts`，不复用历史业务单据、不删除旧数据。测试每次生成新的 `GOLDEN-<运行号>` 前缀，逐次重新登录目标角色，所有写请求携带幂等键，并只接受服务端返回的 `id`、数量和状态。

| 链路段 | 真实结果 | 证据 |
| --- | --- | --- |
| 主数据与版本 | UOM、仓库、4 类库位、客户、供应商、原料、成品、BOM、Routing 均由 API 创建；工作中心从真实 Routing 目录读取 | `golden-facts.spec.ts` 步骤 1–2 |
| 销售与制造来源 | 销售订单 2 件审核通过；工单关联真实销售行并进入 `Released` | 步骤 3–4 |
| 采购与到货 | 到货 `3`、拒收 `1`、实收 `2`；独立 `receiptId` 由服务端分配；质量隔离库存实物为 `2` | 步骤 5，接口响应数量断言 |
| 质检、放行、上架 | 采购质检 `2` 合格；放行移动到 `ReceivingStaging`，上架移动到 `Storage`，总实物仍为 `2` | 步骤 6 |
| 领料与执行 | 真实派工先进入 `Processing`，再补建 `NotStarted` 执行实例；确认领料 `2` 后原料实物为 `0` | 步骤 7–8；对应 Task15 回归 |
| 报工与成品 | 报工合格 `2`、缺陷 `0`；生产质检 `Passed`；FGR `2`；工单 `Completed` | 步骤 9 |
| 销售履约 | 两次各发 `1`；销售行 `2/2/2`，订单 `Completed`；成品最终 `onHand/reserved/available = 0/0/0` | 步骤 10 |
| 负向与页面复读 | tenant.admin 创建派工收到明确 403；销售页面详情能复读本次服务端订单号 | 步骤 11–12 |

执行命令及结果：

```powershell
Set-Location 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
$env:STAGE_UI_PASSWORD = '<仅注入当前进程>'
$env:STAGE_API_BASE_URL = 'http://127.0.0.1:20001'
npm run test:e2e:golden              # Chromium 1 passed
```

本轮仍未把真实 MQTT 写成通过：1883 外部 Mosquitto 未确认加载仓库 ACL，项目订阅账号、设备 credential、QoS1 重放和冲突证据仍缺失；F12 保持环境阻塞。前置 `golden-flow.spec.ts` 的旧事实门禁已明确跳过，避免把“未创建事实”误报为失败；黄金业务结论以本节动态事实套件为准。

## 最终矩阵补充（2026-09-08 16:00 后）

| 范围 | 结果 | 证据与边界 |
| --- | --- | --- |
| 前端单元 | **13/13 PASS** | `npm run test:unit -- --run`，3 个测试文件 |
| 前端构建 | **PASS** | `npm run build`，`vue-tsc` 与 Vite 均通过，305 modules |
| F01/F02/F03/F11 | **PASS** | `npm run test:e2e`；23 个菜单叶节点，F02 七接口 200，F11 无错误卡片/实时矛盾，F03 明确 403 且创建按钮隐藏 |
| 真实黄金闭环 | **1/1 PASS** | `npm run test:e2e:golden`；到货 3/拒收 1/实收 2，领料后原料 0，FGR 2，两次发货后销售行 2/2/2 与成品 0/0/0；同键重放/异载荷冲突、未关闭 Failed 质检阻断 FGR、库存不足阻断超量拣货均实际断言 |
| Core / IoT / Gateway | **155/155、70/70、11/11 PASS** | Maven 全量报告均 Failures 0、Errors 0、Skipped 0 |
| Auth | **40/40 PASS** | `platform-auth` 完整测试 40/40；其中 `AuthMigrationScriptTest` 6/6、临时 PostgreSQL 12.1 夹具 `127.0.0.1:55432` 上的 `AuthPostgresMigrationTest` 4/4，测试结束后已停止夹具；未连接开发库 5433 |
| F12 真实 MQTT | **部分通过（隔离 Broker）** | 临时 Mosquitto 2.1.2 listener `18884` 使用外部 password/ACL 文件；真实 QoS1 同键重放、异载荷冲突、告警触发/恢复/确认均通过。当前外部 1883 的仓库 ACL、生产区域 Facts 和跨服务上下文仍未验收；HTTP simulate 和匿名发布不计为证据 |

S7 Dashboard 的 404 回归已定位为 Core/IoT 未按 fail-closed 设计注入成对运行变量，而非前端伪造数据；重启到正确运行条件后七个聚合接口均为 200。所有测试密码只在当前进程注入，未写入源码、测试文件或本报告。

## F12 真实 MQTT 隔离验收补充（2026-09-08 16:43–16:45）

- 未改动现有外部 `1883` 服务；在 `%TEMP%` 临时目录启动 Mosquitto 2.1.2 listener `127.0.0.1:18884`，使用独立订阅账号、设备 credential、passwordfile 和 aclfile，并将账号通过进程环境注入第二个 IoT 实例 `10005`。
- 通过 Gateway 创建测试设备 `MQTT-F12-20260908163856` 和温度告警规则；设备 credential 仅在创建响应中取得，测试结束后已撤销 credential、停用测试设备，IoT `10005` 与 Broker `18884` 均已停止，现有 IoT `10004` 保持运行。
- 使用 `mosquitto_pub -q 1` 发布同一 `message_id` 两次：查询到同一消息键下仅 `temperature=72` 与 `running_status=Running` 两条指标，设备状态为 `Online/Running`，告警总数为 1；相同消息键改为 `temperature=73` 的异载荷被 IoT 以 `IOT_TLM_003` 拒绝，未新增遥测事实。
- 新消息 `temperature=30` 触发真实告警恢复为 `RecoveredUnacked`，随后人工确认变为 `Recovered`，设备告警状态回到 `Normal`。该记录只证明隔离 Broker 的真实 QoS1 与告警链路，不扩大为生产 `1883`、Core 上下文补链或 `production_area` Facts 已通过。

## 负向、恢复与导航专项补充（2026-09-08）

- `frontend/e2e/golden-flow.spec.ts`：`7 passed、5 expected skip`；修正跨域/错误文档下的退出清理，不再把事实门禁跳过误报为失败。
- `frontend/e2e/manufacturing.spec.ts`：以真实 `mes.inspector` 执行 `4/4`；管理员无 MES 写权限时明确跳过，避免把权限事实伪装成按钮缺陷。
- `frontend/e2e/detail-navigation.spec.ts`：`1 passed、1 expected skip`；采购、销售、调拨真实详情均完成深链直达、刷新、后退到列表、前进回详情。`404/403` 明确错误态用例未提供默认账号时按认证门禁跳过。
- `frontend/e2e/dashboard-states.spec.ts` 与 `frontend/e2e/insights.spec.ts`：`5/5`；覆盖空/缺失指标不转零、503 加载失败、stale/no-cache、模拟链路标识、告警确认和追溯断链。
- 完整 `npx playwright test --project=chromium` 组合回归：`23 passed、11 skipped、0 failed`；11 条跳过均有明确事实/角色门禁说明，未隐藏失败。
- 负向矩阵的跨租户部分由 Auth/Core/IoT 全量 Java 测试实际执行并通过；超时/网络结果不确定由 `useCommand` 单元测试验证同键重试与冲突停止。F12 生产 `1883`、`production_area` Facts、Core 上下文补链仍是明确未验证项。
- 对外部 `1883` 做了只读 `$SYS/broker/version` 订阅：`D:\ruanjian\Mosquitto\mosquitto.conf` 使用默认配置，匿名 `CONNECT/CONNACK/SUBACK` 成功并返回 Mosquitto `2.1.2`；这证明 Broker 可达，但恰好不能作为项目 ACL/设备凭证验收证据，未向业务 topic 发布消息。
