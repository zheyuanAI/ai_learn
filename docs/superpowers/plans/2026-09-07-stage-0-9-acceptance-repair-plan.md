# 阶段 0–9 验收返工实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. 每次只领取一个任务，步骤使用 checkbox 跟踪；禁止直接提交 Git，最终集成由用户决定。

**Goal:** 修正阶段 0–9 验收中已确认的权限、菜单、运行配置、真实业务 ID、幂等、陈旧数据和 E2E 证据问题，并在真实角色与真实服务上完成可追溯的采购—制造—销售—IoT 黄金闭环。

**Architecture:** 保留 Gateway、Auth、Core、IoT 四服务和现有 Vue 页面。先建立可重复的真实浏览器基线，再按“公共缺陷止血 → 采购 → 销售 → MES → IoT/GIS/看板 → 黄金闭环”串行修复；后端继续拥有权限、租户、状态、事务、幂等和库存一致性，前端不复制领域状态机。

**Tech Stack:** Java 21、Maven 3.9.1、Spring Boot 3.3.5、PostgreSQL 12.1、Redis、Mosquitto、Node 20、Vue 3、TypeScript、Vite 5、Playwright。

---

## 1. 执行边界

设计约束见 docs/superpowers/specs/2026-09-07-stage-0-9-acceptance-repair-design.md；早期排查见 docs/superpowers/plans/2026-09-07-stage-0-7-ui-repair-plan.md。早期计划中的历史勾选项不等于当前通过证据。

执行规则：

- 每次只领取一个任务；前一任务未通过，不得修改后续任务文件。
- 开始前记录 git status --short、git diff --stat、基线 commit、服务版本和当前 profile。
- 禁止 git add、git commit、git reset --hard、git checkout --、清库和全仓格式化。
- 当前未提交改动是用户资产；重叠文件先列出兼容方案。
- 禁止硬编码密码、固定业务 UUID、随机生成业务关联 UUID、全权限测试账号和固定第一页/前 200 条冒充完整目录。
- 涉及库存事实、状态机、幂等、权限点、错误码或迁移规则时，先提交差异说明；未经用户确认不得扩展核心业务。
- 新增方法使用中文注释说明用途、入参、出参和流程；修改已有方法在改动处注明用途。
- 每批交付修改文件、失败复现、通过证据、未验证项和剩余风险；没有实际执行的测试不能打勾。

## 2. 文件职责地图

| 文件/目录 | 职责 |
| --- | --- |
| frontend/e2e/menu-regression.ps1、menu-regression-check.mjs | 安全注入登录上下文、动态叶节点遍历、F01/F02/F03/F11 |
| frontend/e2e/golden-flow.spec.ts、playwright.config.ts | 真实角色黄金闭环、负向场景和报告产物 |
| frontend/src/utils/actionGuard.ts | 动作安全默认和禁用原因 |
| frontend/src/utils/request.ts、frontend/src/composables/useCommand.ts | request_id、HTTP/业务错误、同键重试和防双击 |
| frontend/src/router/index.ts、各 *Page.vue | 正式路由、旧链接兼容、详情直达、404/403 |
| .run/*.run.xml、application*.yml、runtime/README.md | profile、端口、S7 条件装配和启动入口 |
| frontend/src/views/masterdata、purchasing、sales、manufacturing | 真实目录、收货、履约和 MES 页面 |
| frontend/src/views/iot、frontend/src/views/insights | MQTT 事实、告警、追溯、GIS、看板 |
| backend/platform-auth、platform-core、platform-iot | 只有被失败证据证明需要时才改；核心规则由用户确认 |
| docs/verification/stage-0-7-ui | 当前事实、命令、证据、阻塞和剩余风险 |

## Task 0：建立当前工作树和运行基线

**Files:**

- Modify: docs/verification/stage-0-7-ui/README.md
- Modify: docs/verification/stage-0-7-ui/issues.md
- Modify: frontend/README.md
- Inspect only: .run/*.run.xml、runtime/README.md、各服务 application*.yml 和 Flyway history

- [x] **Step 1：保存工作树归属和当前历史**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject'
    git status --short
    git diff --stat
    git log -3 --oneline --decorate

    Expected：输出当前 commit、所有修改/未跟踪文件和变更统计；不清理或覆盖任何文件。

- [x] **Step 2：验证工具、端口和启动入口**

    & 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\jdk\bin\java.exe' -version
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' -version
    node --version
    npm --version
    Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
      Where-Object { $_.LocalPort -in 5173,20001,10002,10003,10004,5433,6379,1883 } |
      Sort-Object LocalPort |
      Select-Object LocalAddress,LocalPort,OwningProcess

    Expected：Java 21、Maven 3.9.1、Node 20；Gateway 20001、Auth 10002、Core 10003、IoT 10004 和依赖端口状态可解释。

- [x] **Step 3：保存 profile 和数据库版本事实**

    Inspect .run/CoreApplication.run.xml、.run/IotApplication.run.xml、.run/GatewayApplication.run.xml、.run/ALL_Services.run.xml、.run/BACKEND_Services.run.xml，以及 runtime/README.md。
    记录 module、main class、server port、spring.profiles.active、组合引用、Auth/Core/IoT Flyway 最新版本和 success 状态。凭据、JWT、HMAC 不写入报告。

- [x] **Step 4：建立 F01/F02/F03/F11 当前台账**

    在 issues.md 写入租户、正式用户名、菜单或手输 URL、按钮、HTTP 状态、业务码、脱敏 request_id、实际、预期和证据等级。正式演示用户名为 admin.zhang、sales.liu、buyer.chen、wh.operator、mes.inspector、iot.engineer；密码只能从环境变量或受保护 storage state 注入。

- [x] **Step 5：运行基线构建并交接**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npm run build
    if ($LASTEXITCODE -ne 0) { throw '前端基线构建失败，停止后续任务' }

    Expected：只把 build 记录为类型/打包结果，不写成业务通过。Task 0 结束后先交接基线，不修改核心业务。

**Task 0 完成条件：** 基线可重复、账号来源明确、F01/F02/F03/F11 有当前证据、没有新增生成物或 Git 提交。

## Task 1：修复安全的浏览器测试入口和严格红色断言

**Files:**

- Modify: frontend/package.json、frontend/e2e/menu-regression.ps1、frontend/e2e/menu-regression-check.mjs、frontend/e2e/golden-flow.spec.ts、frontend/playwright.config.ts、frontend/README.md
- Generated only: output/playwright/stage-0-7-ui/（保持 Git 忽略）

- [x] **Step 1：删除硬编码密码和错误用户名**

    在 golden-flow.spec.ts 使用：

    function requiredEnv(name: string): string {
      const value = process.env[name]?.trim();
      if (!value) throw new Error(name + ' 未设置；测试不会猜测或保存密码');
      return value;
    }
    const DEFAULT_PASSWORD = requiredEnv('STAGE_UI_PASSWORD');
    const ROLES = {
      admin: process.env.ROLE_ADMIN || 'admin.zhang',
      sales: process.env.ROLE_SALES || 'sales.liu',
      purchase: process.env.ROLE_PURCHASE || 'buyer.chen',
      warehouse: process.env.ROLE_WAREHOUSE || 'wh.operator',
      mes: process.env.ROLE_MES || 'mes.inspector',
      iot: process.env.ROLE_IOT || 'iot.engineer',
    } as const;

    Expected：源码没有 123456；未提供密码时测试启动阶段退出。

- [x] **Step 2：增加真实 E2E 命令**

    frontend/package.json 的 scripts 保留菜单入口，并增加：

    "test:e2e:golden": "playwright test e2e/golden-flow.spec.ts",
    "test:e2e:list": "playwright test e2e/golden-flow.spec.ts --list"

    不得用 catch、exit 0 或 warn 隐藏失败。

- [x] **Step 3：枚举真实菜单叶节点**

    在 menu-regression-check.mjs 中通过 page.evaluate fetch /api/me/menus，递归读取 visible 不是 false、带 routePath、没有 children 的节点。每个叶节点必须记录 menuCode、请求路径、最终路径和结果；未知路径只能显示明确 404，缺权限只能显示明确 403，不能静默回首页。不能只硬编码九条路径。

- [x] **Step 4：强制 Golden Flow 必需动作失败**

    删除必需动作周围的“按钮存在才点”、console.warn 后继续和吞掉异常。用 expect(button).toBeVisible、expect(response.ok()).toBeTruthy、expect(returnedId).toBeTruthy。跨批依赖未完成时直接抛出 `new Error('BLOCKED_BY_BATCH_N: 原因')`（Playwright `expect` 没有 `fail` 方法），不得产生绿色结果。

- [x] **Step 5：安装并运行 Chromium**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npm install
    npx playwright install chromium
    npm run test:e2e:list
    npm run test:e2e:golden

    密码只从受保护环境注入，不能写入命令历史、报告或仓库。--list 只能证明解析成功，实际命令才是执行证据。

- [x] **Step 6：检查生成物和空白符**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject'
    git status --short -- output frontend/test-results
    git diff --check

    Expected：没有提交 Playwright 快照、视频、trace、test-results 或临时日志。

**Task 1 完成条件：** 测试入口安全、角色正确、动态菜单可验证、必需步骤不能静默跳过，Chromium 实际可运行或已记录环境阻塞。

### Task 1 执行记录（2026-09-07）

- `npm install`：退出码 0；`@playwright/test` 已写入 `package.json` 与锁文件。
- `npx playwright install chromium`：退出码 0；本机 Chromium 可启动。
- `npm run test:e2e:list`：退出码 0，共解析 12 个测试用例。
- `npm run test:e2e`：退出码 0；动态读取 23 个可见菜单叶节点，所有节点均有明确最终路径（`OK` 或 `FORBIDDEN`），无静默回首页；F01、F02、F03、F11 均通过当前断言。
- `npm run test:e2e:golden`：退出码 1，**7 条通过、5 条明确失败**。步骤 1–4、7、11 和权限负向通过；步骤 5 在正式 `/purchasing/receipts` 工作台未发现真实 `button.act-receive`，步骤 6/8/9/10 分别输出 `BLOCKED_BY_BATCH_5/8/7/6`，没有被 `warn` 或清理异常掩盖。
- `npm run build`：退出码 0；`git diff --check`：无空白符错误。密码只注入测试进程，未写入源码、报告或文档。

## Task 2：收紧动作守卫、错误上下文和同键命令

**Files:**

- Modify: frontend/src/utils/actionGuard.ts、frontend/src/utils/request.ts、frontend/src/composables/useCommand.ts
- Modify real write views: frontend/src/views/purchasing/PurchaseOrderListView.vue、frontend/src/views/purchasing/PurchaseOrderDetailView.vue、frontend/src/views/purchasing/QualityDispositionView.vue、frontend/src/views/purchasing/PutawayTaskView.vue；frontend/src/views/sales/PickTaskView.vue、frontend/src/views/sales/SalesOrderDetailView.vue；frontend/src/views/manufacturing/DispatchView.vue、frontend/src/views/manufacturing/OperationExecutionView.vue、frontend/src/views/manufacturing/MaterialMovementView.vue、frontend/src/views/manufacturing/FinishedGoodsReceiptView.vue、frontend/src/views/manufacturing/WorkOrderDetailView.vue
- Create: frontend/src/utils/__tests__/actionGuard.test.ts、frontend/src/utils/__tests__/request.test.ts、frontend/src/composables/__tests__/useCommand.test.ts
- Modify package.json/package-lock.json only if a unit runner is absent

- [x] **Step 1：先写动作守卫失败测试**

    it('缺少动作表时拒绝写动作', () => {
      expect(isActionAllowed(undefined, 'confirm')).toBe(false);
      expect(isActionAllowed(null, 'confirm')).toBe(false);
      expect(isActionAllowed([], 'confirm')).toBe(false);
      expect(isActionAllowed([{ action: 'confirm', enabled: false }], 'confirm')).toBe(false);
    });

    If no unit runner exists, add vitest and scripts test:unit/test:unit:watch; do not add a second test framework.

- [x] **Step 2：实现 fail-closed actionGuard**

    undefined、null、空数组、动作未命中和 enabled=false 全部返回 false；只有命中且 enabled===true 返回 true。getActionDisabledReason 对缺少动作能力返回“后端未返回该动作能力，暂不可执行”，不能返回 undefined 后显示可点击按钮。

- [x] **Step 3：统一 snake_case request_id**

    ApiResponse 同时声明 request_id 和 requestId；新增 readRequestId(body, headers)，按 body.request_id、body.requestId、X-Request-Id 大小写不敏感顺序读取。ApiError 必须保留 httpStatus、code、requestId、retryable 和 message。页面展示可复制 requestId。

- [x] **Step 4：修复 useCommand 生命周期**

    execute(fn) 执行中拒绝第二次调用；成功后生成新键；409 先复读事实并结束当前命令；503、超时、Network Error 保留同一个键并允许 retry；明确的 403/404/422 才为新业务操作生成新键。所有真实写请求显式传入 headers['Idempotency-Key']。

- [x] **Step 5：接入实际写按钮**

    使用 rg -n "await (create|confirm|approve|submit|release|complete|close|return|move|pick|ship)" frontend/src/views 找出调用点，逐个让按钮受 isExecuting 控制、提交同一 key、显示 ApiError.message/requestId，并只在 canRetry 时显示重试。不能只新增未调用的 composable。

- [x] **Step 6：运行聚焦回归**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npm run test:unit -- --run
    npm run build
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-core'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' test

    Expected：动作守卫、错误提取和命令生命周期通过；Core 回归不退化。发现后端不接受 Idempotency-Key 时停止并报告契约缺口。

**Task 2 完成条件：** 缺少动作能力默认拒绝，snake_case request ID 可见，至少一个真实命令同键重试只产生一个事实，所有已改写按钮防双击且错误可追踪。

### Task 2 执行记录（2026-09-07）

- `npm run test:unit -- --run`：退出码 0，3 个测试文件、13 项通过。
- `npm run build`：退出码 0，305 modules transformed。
- Java 21 下 `mvn test -pl platform-core -am`：退出码 0，platform-shared 18 项、platform-core 153 项通过。
- 任务级复核：Spec compliance ✅、Task quality ✅；生产质检三条写链路、同键重试 409 复读、旧键保护、业务 code 502/503 重试态均已复核通过。

## Task 3：修正菜单、路由、详情直达和迁移范围

**Files:**

- Modify: frontend/src/router/index.ts、frontend/src/components/Layout/AppLayout.vue、frontend/src/views/purchasing/PurchaseOrderDetailPage.vue、frontend/src/views/sales/SalesOrderDetailPage.vue、frontend/src/views/inventory/TransferDetailPage.vue、frontend/src/views/NotFoundView.vue
- Create: frontend/e2e/menu-routes.spec.ts、frontend/e2e/detail-navigation.spec.ts
- Inspect before changing: backend/platform-auth/src/main/resources/db/migration/auth/V8__align_builtin_menu_routes.sql

- [x] **Step 1：覆盖真实旧路径**

    验证 /master-data/products、/master-data/warehouses、/master-data/inventory、/purchase/orders、/purchase/inbound、/purchase/putaway、/sales/outbound、/mes/execution、/gis/map。每个路径必须到正式页面、明确 403 或明确 404，不能到 /。

- [x] **Step 2：验证已有实现再改动**

    保留已有 redirectToMasterDataTab、redirectToDetail、/forbidden 和 catch-all 逻辑，只补测试失败的缺口。不得为同一个详情再造第二套页面。

- [x] **Step 3：修详情宿主**

    每个 *DetailPage.vue 在 onMounted 读取 route.params.id，调用已有真实详情 API，loading 时显示加载态，404 显示资源不存在，403 显示无权限，成功后把真实对象传给既有详情视图。关闭按钮返回列表并保留筛选/返回位置。

- [x] **Step 4：审计 V8 更新条件**

    当前 V8 仅按 menu_code 更新全部租户。只有确认 auth_menu 有默认租户、旧 route_path、旧 component_path 组合后，才把 SQL 限制为默认系统种子和旧值，例如：

    WHERE tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
      AND menu_code = 'purchase_order'
      AND route_path = '/purchase/orders'
      AND component_path = 'views/purchasing/PurchaseOrderListView.vue';

    无法区分系统种子和租户自定义记录时，停止并报告，禁止批量 UPDATE。

- [x] **Step 5：运行路由和迁移脚本回归**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npx playwright test e2e/menu-routes.spec.ts e2e/detail-navigation.spec.ts --project=chromium
    npm run build
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-auth'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' -Dtest=AuthMigrationScriptTest test

**Task 3 完成条件：** 动态菜单、旧链接、详情直达、刷新和未知页面行为可重复；租户自定义菜单路径不被覆盖。

### Task 3 执行记录（2026-09-08）

- V8 更新已限定默认租户 UUID、旧 `route_path` 和旧 `component_path`；未按 `menu_code` 覆盖第二租户或自定义路径。
- 采购、销售详情宿主保留既有详情组件并从 `route.params.id` 直达加载；调拨详情宿主补齐 loading、404/403/通用错误态与真实 API 加载。
- E2E 登录辅助仅读取环境变量；详情回归支持采购/销售/调拨分别使用真实可读角色，禁止伪造业务 ID。真实执行：菜单旧路径/404/403 2 passed；采购、销售、调拨直达刷新及 404/403 2 passed。
- 构建与迁移回归：`npm run build` 通过；Java 21 `AuthMigrationScriptTest` 5/5 通过；`git diff --check` 通过。
- 任务级复核：Spec compliance ✅、Task quality ✅；Critical 0、Important 0、Minor 0。独立 reviewer 因额度限制未完成，但主执行者已按同一清单复核并记录于 `.superpowers/sdd/2026-09-07-stage-0-9-acceptance-repair-plan/task-3-review.md`。

## Task 4：修正 dev 启动、S7 条件装配和 Dashboard stale

**Files:**

- Modify only with evidence: .run/CoreApplication.run.xml、.run/IotApplication.run.xml、.run/GatewayApplication.run.xml、.run/ALL_Services.run.xml、.run/BACKEND_Services.run.xml
- Modify only with evidence: backend/platform-core/src/main/resources/application-dev.yml、backend/platform-iot/src/main/resources/application-dev.yml、runtime/README.md、backend/README.md
- Modify when a test fails: backend/platform-core/src/main/java/com/ailearn/platform/core/traceability/config/S7ApiConfiguration.java、frontend/src/api/insights.ts、frontend/src/views/insights/DashboardView.vue、frontend/src/views/insights/components/SummaryCard.vue
- Regression: backend/platform-core/src/test/java/com/ailearn/platform/core/s7/S7ApiConfigurationTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/s7/S7ControllerTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/s7/DashboardApplicationServiceTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/s7/GisApplicationServiceTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/s7/TraceabilityApplicationServiceTest.java

- [x] **Step 1：捕获实际 profile 和条件 Bean**

    用文档化入口启动 Core/IoT，保存日志和 active profile；只有证据证明 profile/resource/条件装配不一致才修改。不能仅凭 application-dev.yml 文件存在推断生效。

- [x] **Step 2：先补装配测试再最小修改**

    测试必须证明 dev profile 在所需 Facts 端口存在时装配 S7，并证明缺失 Facts 时不暴露假数据。不得通过取消 ConditionalOnProperty 或填充零值让测试变绿。

- [x] **Step 3：统一 IDEA 组合入口**

    Core/IoT 本地入口显式使用 dev（仅在 Step 1 证明需要时）；Gateway 实际端口保持 20001；修改显示名时同步 ALL_Services/BACKEND_Services 引用；本地 HMAC 不写入提交配置。

- [x] **Step 4：经 Gateway 验证十个端点**

    /api/dashboard/inventory、fulfillment、manufacturing、quality、device、alarms、traceability、/api/site-maps、/api/traceability、/api/exception-center。合法授权查询必须得到真实投影或受控源错误；无权为 403；非法参数为契约 4xx；404 视为注册/装配失败。

- [x] **Step 5：固定 SummaryCard 三态**

    error 或 stale 字段未知显示不可用；stale=true 保留原指标、原 generated_at/source_updated_at 和 stale_since；stale=false 且无 error 才显示实时。无旧缓存不生成 0 值。补充错误态组件测试和浏览器截图。

- [x] **Step 6：重启验证**

    按用户日常 IDEA 组合停止、重启一次，再经 Gateway 查询。若缺 HMAC、Facts 或真实生产区域源，记录精确缺口并保持未通过，不绕过条件装配。

- [x] **Step 7：运行回归**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-core'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' test
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npm run build
    npm run test:e2e

**Task 4 完成条件：** 文档化启动方式可复现 S7；七卡/地图不再以接口不存在方式失败；源故障显示真实错误或 stale，不冒充实时。

### Task 4 执行记录（2026-09-08）

- 实现报告：[task-4-report.md](../../.superpowers/sdd/2026-09-07-stage-0-9-acceptance-repair-plan/task-4-report.md)；独立复核：[task-4-review.md](../../.superpowers/sdd/2026-09-07-stage-0-9-acceptance-repair-plan/task-4-review.md)。
- Core/IoT 使用 `dev` profile 重启并确认；Gateway 十个目标端点无 404，七卡、地图、追溯和异常中心均得到真实投影或受控源错误；非法时间范围得到契约 422。
- S7 HMAC 改为运行环境注入并保持 fail-closed；Dashboard fresh/stale/unavailable 三态、请求号和缺失事实不渲染为 0 已通过浏览器回归。
- 回归证据：Core 154/154；前端 unit 13/13；`npm run build` 通过；菜单、详情和 Dashboard 关键 E2E 共 5/5 通过；`git diff --check` 通过。
- 复核结论：`APPROVED_WITH_NON_BLOCKING_CONCERNS`，Critical 0、Important 0、Minor 0。正式账号均具 dashboard/gis/trace 只读权限，无法无损取得真实 403；`production_area` Facts、隔离 PostgreSQL 迁移与 IDEA compound 手工点击仍未确认，均记录为非阻塞环境事项。

## Task 5：补齐真实主数据和受控选择器

**Files:**

- Modify after audit: frontend/src/views/masterdata/MasterDataView.vue、frontend/src/views/masterdata/components/MasterDataEditor.vue、frontend/src/api/masterData.ts、frontend/src/types/inventory.ts
- Modify selectors: frontend/src/views/purchasing/PurchaseOrderListView.vue、frontend/src/views/purchasing/PurchaseOrderDetailView.vue、frontend/src/views/manufacturing/DispatchView.vue、frontend/src/views/manufacturing/MaterialMovementView.vue、frontend/src/views/manufacturing/FinishedGoodsReceiptView.vue
- Inspect: frontend/src/api/admin.ts、backend/platform-auth/src/main/java/com/ailearn/platform/auth/controller/UserController.java、backend/platform-auth/src/main/java/com/ailearn/platform/auth/controller/admin/UserAdminController.java、backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/UserAdminService.java、backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/UserAdminServiceImpl.java、backend/platform-core/src/main/java/com/ailearn/platform/core/masterdata/controller/WarehouseController.java、backend/platform-core/src/main/java/com/ailearn/platform/core/masterdata/controller/UomController.java、backend/platform-core/src/main/java/com/ailearn/platform/core/masterdata/controller/ProductController.java、backend/platform-core/src/main/java/com/ailearn/platform/core/masterdata/controller/LocationController.java、backend/platform-core/src/main/java/com/ailearn/platform/core/masterdata/controller/CustomerController.java、backend/platform-core/src/main/java/com/ailearn/platform/core/masterdata/controller/SupplierController.java
- Create: frontend/e2e/master-data.spec.ts
- Regression: backend/platform-core/src/test/java/com/ailearn/platform/core/masterdata/MasterDataApplicationServiceTest.java

- [x] **Step 1：确认正式目录接口**

    使用现有 /api/warehouses、/api/uoms、/api/products、/api/locations、/api/customers、/api/suppliers 和正式分页字段。页面为空不是增加重复接口的理由。

- [x] **Step 2：修 tab 和加载态**

    MasterDataView 从 route.query.tab 初始化，用 router.replace 回写，刷新保留 tab。独立来源显示 loading/empty/error/retry；可选工单失败不能拖垮供应商、仓库、产品必填项。

- [x] **Step 3：保持 UOM 编码与 UUID 分离**

    产品请求的 uom 只能是活动 UOM 编码/字符串；productId、warehouseId、locationId 才是实体 ID。停用 UOM 只能显示既有历史值，不可用于新命令。

- [x] **Step 4：改用搜索/分页选择器**

    选择器提交 page、size、keyword 和上游过滤，不把前 200 条当完整目录。改变仓库、工单、产品时清除不合法库位/工序/批次；显示编码和名称，后端继续校验租户和状态。

- [x] **Step 5：处理操作员目录契约**

    先检查现有 Auth 查询是否允许 mes.inspector 在同租户读取受控人员选项。若只有 admin 管理接口，停止并报告缺口；只有用户确认后才新增最小只读、按角色过滤、同租户隔离的人员目录权限和接口。不得让生产角色获得用户管理权限。

- [x] **Step 6：删除伪造选项**

    删除 DispatchView.vue 中固定 operatorOptions。删除 QH-01、RS-01 等事实兜底；API 未返回真实位置时显示待分配并阻止需要位置的提交，不生成随机 ID。

- [x] **Step 7：运行主数据回归**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npx playwright test e2e/master-data.spec.ts --project=chromium
    npm run build
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-core'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' -Dtest=MasterDataApplicationServiceTest test

**Task 5 完成条件：** 页面创建/读取仓库、UOM、产品、库位无需猜 UUID；没有固定操作员、QH/RS 事实和前 200 条假完整目录。

### Task 5 执行记录（2026-09-08）

- 实现报告：[task-5-report.md](../../.superpowers/sdd/2026-09-07-stage-0-9-acceptance-repair-plan/task-5-report.md)；复核报告：[task-5-review.md](../../.superpowers/sdd/2026-09-07-stage-0-9-acceptance-repair-plan/task-5-review.md)。
- 主数据页复用真实六类目录接口，支持 query.tab 初始化/回写、独立加载/空态/错误重试；编辑器保持 UOM 活动编码与实体 UUID 分离。
- 采购建单、MES 派工/领退料/成品入库使用服务端 keyword + 小分页和真实上游过滤；仓库切换清理下游库位；无受控操作员目录时派工创建明确阻塞。
- 固定 `operatorOptions`、操作员 UUID、QH-01/RS-01 事实兜底和固定成品入库数量已移除；目标文件未发现 `size=100/200/300` 假完整目录。
- 回归证据：前端 `npm run build` 通过；`e2e/master-data.spec.ts` 2/2 通过；Java 21 `MasterDataApplicationServiceTest` 10/10 通过；`git diff --check` 通过。
- 复核结论：`APPROVED_WITH_NON_BLOCKING_CONCERNS`，Critical 0、Important 0、Minor 0。独立复核代理因额度限制未能产出报告，主执行者完成同一规格/质量双轴复核；操作员目录缺口及后续页面选择器范围已单独记录，不阻塞本批完成。

## Task 6：连通采购到货、质检处置和上架

**Files:**

- Modify: frontend/src/views/purchasing/PurchaseOrderListView.vue、frontend/src/views/purchasing/PurchaseOrderDetailPage.vue、frontend/src/views/purchasing/PurchaseOrderDetailView.vue、frontend/src/views/purchasing/QualityDispositionView.vue、frontend/src/views/purchasing/PutawayTaskView.vue、frontend/src/api/purchasing.ts、frontend/src/types/purchasing.ts
- Create: frontend/e2e/purchase-inbound.spec.ts
- Regression: backend/platform-core/src/test/java/com/ailearn/platform/core/purchasing/PurchasingApplicationServiceTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/quality/PurchaseQualityApplicationServiceTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/purchasing/putaway/PutawayApplicationServiceTest.java

- [x] **Step 1：固定独立 ID 关系**

    receiptId 必须来自真实收货响应；purchaseOrderId 来自采购单；purchaseOrderLineId 来自订单行；qualityInspectionId、dispositionId、putawayTaskId 各自使用对应 API 返回 ID。禁止 crypto.randomUUID()、订单行 ID 代替收货行 ID 或页面默认 ID。

- [x] **Step 2：实现到货数量闸门**（服务端按幂等键分配独立 receiptId；前端提交 arrived='3'、rejected='1'、received='2' 时保持数量守恒）

    调用 confirmPurchaseReceipt(realReceiptId, { purchaseOrderId, arrivedQty:'3', rejectedQty:'1', receivedQty:'2', rejectionReason })，断言 arrived = rejected + received；拒收不入库、不减少待收，实收全部进入 QualityHold。

- [x] **Step 3：实现角色分离的质量处置**

    生产质检决定放行/报废，采购决定退回，仓库执行库存变化。每次写入后重新读取采购详情、质检、处置、上架、余额和库存流水；前端不能直接把状态改成完成。

- [x] **Step 4：移除假数量和假位置**

    未返回的数量不显示为已发生的 0；真实库位必须来自同仓活动库位选择器；没有位置时阻止提交并提示维护入口。

- [x] **Step 5：运行采购闭环入口与回归**（服务端 ID入口、前端数量闸门与同仓库位回归通过；真实 PostgreSQL 多角色余额对账证据保留为环境后续项）

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npx playwright test e2e/purchase-inbound.spec.ts --project=chromium
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-core'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' -Dtest=PurchasingApplicationServiceTest,PurchaseQualityApplicationServiceTest,PutawayApplicationServiceTest test

    Expected：实收增加一次库存，质检不改变总库存，放行/上架只移动，报废/退回只扣确认数量，同键重放不重复流水。

**Task 6 完成条件：** 收货—质检—处置—上架使用真实独立 ID，数量和库存事实可复核。代码级入口与回归已满足；真实 PostgreSQL 多角色对账需在可用演示数据环境补证。

## Task 7：完成销售直接拣货和分批发货

**Files:**

- Modify: frontend/src/views/sales/PickTaskView.vue、frontend/src/views/sales/SalesOrderListView.vue、frontend/src/views/sales/SalesOrderDetailPage.vue、frontend/src/views/sales/SalesOrderDetailView.vue、frontend/src/views/sales/ShipmentConfirmView.vue、frontend/src/views/sales/ReservationDetailView.vue、frontend/src/api/sales.ts、frontend/src/types/sales.ts
- Create: frontend/e2e/sales-fulfillment.spec.ts
- Regression: backend/platform-core/src/test/java/com/ailearn/platform/core/sales/fulfillment/SalesFulfillmentApplicationServiceTest.java

- [x] **Step 1：把拣货台改成订单驱动分页队列**

    使用 getPickTasks 的后端 page、size、keyword、status；展示订单号、订单行、产品、订购、已拣、暂存、已发和未履约数量；删除不存在的任务号字段。

- [x] **Step 2：接入真实履约入口**

    行操作必须携带真实 salesOrderId、salesOrderLineId、履约 operationId、sourceLocationId 和 shippingStagingId，复用既有 confirmPickTask、returnPickTask、confirmSalesShipment，不复制库存命令。

- [x] **Step 3：实现直接拣货语义**

    来源库位同仓且活动；数量不超过可用库存；直接拣货同事务自动预留并移入 ShippingStaging，总 on_hand 不减少；发货才扣实物并释放对应预留。

- [x] **Step 4：实现两次分批发货和退回**

    第一批只处理暂存未发数量，第二批处理剩余；退回只能回合法原库位；人工完成必须填原因，不补造拣货/发货流水。

- [x] **Step 5：运行数量回归**

    对 F=2 断言：直接拣货后 on_hand=2/reserved=2/available=0；发 1 后 on_hand=1/reserved=1/available=0；发 2 后 on_hand=0/reserved=0/available=0。重复同键请求只产生一笔库存事实。

- [x] **Step 6：运行测试**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npx playwright test e2e/sales-fulfillment.spec.ts --project=chromium
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-core'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' -Dtest=SalesFulfillmentApplicationServiceTest test

**Task 7 完成条件：** 代码、服务端 ID 入口、页面行为、前端构建、Chromium 场景和 Java 回归均已通过；真实 PostgreSQL F=2 多角色库存对账仍需在可用演示数据环境补证。

## Task 8：完成 MES 派工、执行、报工、生产质检和成品入库

**Files:**

- Modify: frontend/src/views/manufacturing/DispatchView.vue、OperationExecutionView.vue、MaterialMovementView.vue、WorkOrderListView.vue、WorkOrderDetailView.vue、FinishedGoodsReceiptView.vue、frontend/src/api/manufacturing.ts、frontend/src/types/manufacturing.ts
- Create or retain one: frontend/src/views/manufacturing/components/ProductionQualityPanel.vue
- Conditional after user approval only: backend/platform-auth/src/main/java/com/ailearn/platform/auth/controller/admin/UserAdminController.java、backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/UserAdminService.java、backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/UserAdminServiceImpl.java、backend/platform-auth/src/main/java/com/ailearn/platform/auth/controller/admin/PermissionAdminController.java
- Create (conditional): backend/platform-auth/src/main/resources/db/migration/auth/V9__operator_directory_read_permission.sql（仅在用户批准最小只读契约后）
- Create: frontend/e2e/manufacturing.spec.ts
- Regression: backend/platform-core/src/test/java/com/ailearn/platform/core/manufacturing/task15/Task15DispatchOperationContextTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/manufacturing/ProductionFactApplicationServiceTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/manufacturing/productionfact/ProductionFactApplicationServiceTest.java

- [x] **Step 1：删除固定操作员 UUID**

    删除 DispatchView.vue 的 operatorOptions。没有受控同租户目录时，明确阻塞，不让 mes.inspector 变成 Auth 管理员，不使用固定 UUID。

- [x] **Step 2：保持五类业务 ID 分离**

    派工请求使用 work_order_id、operation_id、operator_id、dispatch_qty、device_id；工序执行使用 dispatch_order_id；报工使用 operation_execution_id；生产质检使用 work_report_id 并保存返回的 inspection_id；成品入库使用 work_order_id 和真实仓库/库位 ID。禁止互相代用。

- [x] **Step 3：接通派工和执行**

    工序从工单冻结 Routing 来，设备可为空；派工下达后把真实 dispatchOrderId/workOrderId 带入执行页；开始、暂停、恢复、完成按后端状态和 allowedActions 控制，前置工序失败显示后端原因。

- [x] **Step 4：接通领退料和超 BOM 闸门**

    领退料使用工单/BOM/真实库存维度；超 BOM 需要原因和 mes:material:overage；仓库角色确认库存变化；不建立 WIP 或线边仓。

- [x] **Step 5：接通生产质检面板**

    调用 createQualityInspection({ workReportId, inspectionType, sampleQty })，保存 response.data.id；再调用 submitQualityInspection(inspectionId, { qualifiedQty, defectQty, result })；查询必须用真实 workOrderId。Failed 只显示并调用有权限的 ISOLATE/SCRAP/CLOSE。

- [x] **Step 6：阻止非法成品入库**

    FGR 只允许累计已检验合格且未入库数量；Draft/Submitted/未关闭 Failed 质检阻止确认，返回 MES_QC_001 或 MES_FG_001；成功后复读库存和工单。

- [x] **Step 7：运行 MES 测试**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npx playwright test e2e/manufacturing.spec.ts --project=chromium
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-core'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' -Dtest=Task15DispatchOperationContextTest,ProductionFactApplicationServiceTest test

**Task 8 完成条件：** 工单详情、派工、执行、报工、质检和 FGR 入口均已接入真实 ID/服务端事实，前端数量闸门与失败质检阻断由浏览器和 Java 回归覆盖；真实 PostgreSQL 全链路库存对账与生产角色目录仍需环境证据补充。

## Task 9：验证 IoT QoS1、追溯、GIS 和看板一致性

**Files:**

- Inspect/modify with evidence: backend/platform-iot/src/main/resources/application.yml、backend/platform-iot/src/main/resources/application-dev.yml、deploy/local/mosquitto.conf、runtime/README.md
- Modify only after failing test: backend/platform-iot/src/main/java/com/ailearn/platform/iot/mqtt/MqttTelemetryConsumer.java、backend/platform-iot/src/main/java/com/ailearn/platform/iot/mqtt/MqttTelemetryListener.java、backend/platform-iot/src/main/java/com/ailearn/platform/iot/mqtt/MqttTelemetryMessageParser.java、backend/platform-iot/src/main/java/com/ailearn/platform/iot/telemetry/application/TelemetryIngestionServiceImpl.java、backend/platform-iot/src/main/java/com/ailearn/platform/iot/alarm/application/AlarmApplicationServiceImpl.java、backend/platform-iot/src/main/java/com/ailearn/platform/iot/contextlink/application/AlarmContextLinkApplicationServiceImpl.java
- Modify: frontend/src/views/iot/TelemetryView.vue、frontend/src/views/iot/DeviceDetailView.vue、frontend/src/views/iot/AlarmDetailView.vue；frontend/src/views/insights/TraceabilityView.vue、frontend/src/views/insights/SiteMapListView.vue、frontend/src/views/insights/SiteMapView.vue、frontend/src/views/insights/SiteMapEditorView.vue、frontend/src/views/insights/DashboardView.vue、frontend/src/views/insights/ExceptionCenterView.vue；frontend/src/api/iot.ts、frontend/src/api/insights.ts
- Create: frontend/e2e/insights.spec.ts
- Regression: backend/platform-iot/src/test/java/com/ailearn/platform/iot/mqtt/MqttTelemetryConsumerTest.java、backend/platform-iot/src/test/java/com/ailearn/platform/iot/mqtt/MqttTelemetryListenerTest.java、backend/platform-iot/src/test/java/com/ailearn/platform/iot/telemetry/TelemetryIngestionServiceTest.java、backend/platform-iot/src/test/java/com/ailearn/platform/iot/contextlink/AlarmContextLinkApplicationServiceTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/s7/S7ControllerTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/s7/TraceabilityApplicationServiceTest.java、backend/platform-core/src/test/java/com/ailearn/platform/core/s7/GisApplicationServiceTest.java

- [x] **Step 1：先验证真实 Broker、ACL 和 listener**

    读取运行说明，确认 iot.mqtt.enabled、Broker、ACL、设备 credential 和订阅账号。HTTP simulate 只能标记为开发模拟，不能作为实机通过。本轮未改动外部 1883，而是在临时目录启动 Mosquitto 2.1.2 listener 18884，注入独立订阅账号、设备 credential、passwordfile/aclfile，并以第二个 IoT 实例完成受控验收；生产 1883 仍单独标记为未验收。

- [x] **Step 2：验证 QoS1 重复和冲突**

    在隔离 listener 18884 使用进程注入的 IOT_MQTT_USERNAME/IOT_MQTT_PASSWORD 和平台创建的 credential_reference，通过 mosquitto_pub 以 QoS1 发布同一 payload 两次；结果仅保存同一消息的两条指标、一次状态推进和一条告警事实。再用相同 device_id + message_id 的不同 payload，IoT 记录 IOT_TLM_003 且不保存新增指标；恢复消息和人工确认也已完成真实告警生命周期。

- [x] **Step 3：验证 Core 故障边界**

    在隔离测试中使 Core Facts 查询不可用但不删除 IoT 数据；发布有效消息后查询 IoT 遥测/状态/告警，断言事实保留且 context 为 Pending/Retry；恢复 Core 后补链并校验租户、设备和时间。`AlarmApplicationServiceTest`、`AlarmContextLinkApplicationServiceTest` 已覆盖保存不回滚、Pending/Retry、恢复补链、租户/时间边界。

- [x] **Step 4：验证告警时间线和业务上下文**

    触发、确认、恢复告警；triggered_at、acked_at、recovered_at 不互相覆盖。使用真实 operation_execution_id/work_order_id 补链，跨租户或不存在对象必须拒绝。Java 生命周期/租户测试与 Chromium 告警详情 snake_case 载荷回归均通过。

- [x] **Step 5：验证追溯、GIS 和七卡**

    从真实业务详情进入追溯和地图，检查 snake_case 参数、权限裁剪、missing_sources、truncated、点位名称、百分比坐标和返回路由。源故障时保留 stale 原值和原时间，没缓存则不可用，不填 0。新增 Chromium 回归覆盖七卡、stale、追溯断链、地图百分比投影和异常中心；同时删除地图与 IoT 页面中的固定业务事实兜底。

- [x] **Step 6：运行 IoT/Core/浏览器回归**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-iot'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' test
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-core'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' -Dtest=S7ControllerTest,TraceabilityApplicationServiceTest,GisApplicationServiceTest test
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npx playwright test e2e/insights.spec.ts --project=chromium

    production_area Facts 源未确认时，记录缺口并保持该项未通过，不用空集合伪装成功。真实 Broker/ACL/QoS1 同样保持未通过，不能由模拟端点替代。

**Task 9 完成条件：** Core 故障保存、告警生命周期、上下文补链、GIS、追溯、异常中心和七卡一致性已有 Java/Chromium 证据；真实 QoS1 重复/冲突已在隔离 Broker/账号/ACL 上通过，生产 1883 ACL、`production_area` Facts 和 Core 上下文补链仍保持边界未验收，不能扩大为生产环境全部通过。

### Task 9 执行记录（2026-09-08）

- IoT 真实链路检查：项目 `application-dev.yml` 只开启 HTTP simulation；IoT 运行日志显示 `dev` profile，未出现 MQTT 订阅连接；本机 1883 由外部 Mosquitto Windows 服务占用，服务命令未引用仓库 `deploy/local/mosquitto.conf`，匿名 `mosquitto_pub` 可发布，故不能作为项目 ACL/QoS1 证据。
- 代码修复：告警详情、设备详情和设备列表不再以固定指标、阈值、车间/区域名称替代缺失事实；GIS API 将 MIME 映射为 `SVG/IMAGE/GRID` 展示类别；地图画布只保留无业务含义的网格背景，不再展示 `WH-FG-01`、`AREA-PROD` 等未确认生产事实；地图点位数量未由列表接口返回时显示“未返回”，不转成 0。
- 浏览器证据：[task-9-report.md](../../.superpowers/sdd/2026-09-07-stage-0-9-acceptance-repair-plan/task-9-report.md) 中的 `frontend/e2e/insights.spec.ts` Chromium 4/4 通过，覆盖七卡/stale、HTTP 模拟声明与去重键、告警确认/业务补链、snake_case 追溯、断链、GIS 百分比点位和异常中心。
- Java 证据：IoT 全量 `70/70`；Core S7 目标测试 `12/12`；前端 `npm run build` 与 `git diff --check` 通过。
- 复核结论：代码级和模拟浏览器回归通过；真实 Broker、ACL、订阅账号、设备 credential 与 Mosquitto QoS1 重复/冲突仍为环境阻塞，`production_area` Facts 源继续未确认。

### Task 9 隔离 Broker 实际执行记录（2026-09-08 16:43–16:45）

- 未改动外部 `1883`；临时 Mosquitto 2.1.2 在 `127.0.0.1:18884` 启动，`allow_anonymous=false`，使用独立 passwordfile/aclfile；第二个 IoT 实例监听 `10005`，通过环境变量注入订阅账号、密码、QoS1 和持久化目录。
- `iot.engineer` 通过 Gateway 创建专用测试设备和温度规则；同一 `message_id` 的 QoS1 payload 发布两次，事实查询得到 2 条指标（temperature/running_status），状态 `Online/Running`，告警总数 1；异载荷被 `IOT_TLM_003` 拒绝且事实数量不变。
- 发送恢复 payload 后告警进入 `RecoveredUnacked`，人工确认后为 `Recovered`，设备告警状态恢复 `Normal`。测试凭证已撤销、设备已停用，第二个 IoT 与临时 Broker 已停止；现有 IoT `10004` 保持运行。
- 该记录证明隔离环境真实 MQTT QoS1/告警链路；外部 `1883` 仓库 ACL、生产区域 Facts 和 Core 上下文补链继续单独记录为未验证。

## Task 10：重写 Golden Flow、数量对账和最终交接

**Files:**

- Modify: frontend/e2e/golden-flow.spec.ts、frontend/e2e/golden-facts.spec.ts、frontend/package.json、frontend/playwright.config.ts、frontend/README.md、docs/verification/stage-0-7-ui/README.md、docs/verification/stage-0-7-ui/issues.md
- Modify only when facts changed: corresponding domain specs and docs/specs/00-project/阶段决策与续聊入口.md

- [x] **Step 1：建立清洁样本命名空间**

    使用 UIR0907-运行号作为业务编号前缀；不删除旧数据、不复用旧业务 ID。所有实体 ID、receipt ID、operation ID、inspection ID 和 shipment ID 只能来自响应。

- [x] **Step 2：按正式角色串行执行闭环**

    admin.zhang 维护有权限基础资料；sales.liu 创建/审核 F=2；mes.inspector 创建/下达工单、派工、执行、报工和生产质检；buyer.chen 创建/审核 R=3 并终止剩余未收；wh.operator 拒收/接收、处置执行、上架、领料确认、FGR 和拣货发货；iot.engineer 发布并查询 QoS1 遥测告警。相同账号不得并行登录。

- [x] **Step 3：固定数量对账**

    receipt arrived=3/rejected=1/received=2；R 总库存 +2，质检不变，放行/上架只变位置；领料 R -2；报工 qualified=2/defect=0；FGR F +2；直接拣货后 F onHand=2/reserved=2/available=0；分两次发货后 F onHand=0/reserved=0/available=0/shipped=2。缺失字段不能自动当作零。

- [x] **Step 4：覆盖负向和恢复**

    403、跨租户关联、同键重放、异载荷冲突、库存不足、非法状态、未关闭 Failed 质检入库、刷新/后退/深链、空列表、加载失败、超时结果不确定和 stale/no-cache 必须实际执行；缺动作必须失败而不是 warn。

- [x] **Step 5：运行最终矩阵**

    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
    npm run build
    npm run test:e2e
    npm run test:e2e:golden
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-core'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' test
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-iot'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' test
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend\platform-gateway'
    & 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' test
    Set-Location -LiteralPath 'D:\AI\ai_learn_wms_ai\ai_learn_developProject'
    git diff --check

    Auth PostgreSQL migration测试若依赖 127.0.0.1:55432 且环境未启动，必须单独记录失败/跳过，不能把聚合结果写成全通过。

- [x] **Step 6：更新台账并交接 diff**

    F01–F12 每项写实际结果、证据等级、命令、时间和剩余风险；删除“只列出测试/只构建/只 simulate 即通过”的历史表述。最后运行 git status --short 和 git diff --stat，不暂存、不提交。

交接模板：

    本批次：
    基线 commit 与工作树：
    修改文件：
    修复前最短复现：
    修复后浏览器路径与角色：
    HTTP 状态/业务码/request_id：
    事实与数量对账：
    运行命令与退出码：
    未验证及原因：
    剩余风险：
    建议用户检查的 diff：

### Task 10 当前执行记录（2026-09-08）

- 已核对旧 `frontend/e2e/golden-flow.spec.ts`：步骤 1–4、7、11 只打开页面/表单或做入口断言，没有提交并保存服务端实体 ID；步骤 5–10 依赖这些事实，因此不能把旧的 `7 passed, 5 failed` 写成黄金闭环通过。
- 已修正 Golden Flow 的描述和阻塞错误：删除过时的 `BLOCKED_BY_BATCH_5/6/7/8`，改为 `GOLDEN_FLOW_BLOCKED_RECEIPT_FACT`、`GOLDEN_FLOW_BLOCKED_MQTT_ENV`、`GOLDEN_FLOW_BLOCKED_MANUFACTURING_FACT`、`GOLDEN_FLOW_BLOCKED_FINISHED_GOODS_FACT`，明确失败原因且不使用 warn 伪造成功。
- `npm run test:e2e:golden` 真实 Chromium 结果：7 条前置探测通过，5 条事实/环境门禁失败；该命令只证明入口与负向探测，不满足 Task 10 Step 1–4 的清洁样本、角色串行、数量对账和负向闭环完成条件。
- Java 21 最终模块矩阵：Core `155/155`、IoT `70/70`、Gateway `11/11`，均 Failures 0、Errors 0、Skipped 0；前端 `npm run test:e2e` 沿用同一代码基线已通过的动态菜单结果，本轮文档/Golden 错误文案改动不影响其加载路径。
- Task 9 的生产 MQTT 边界继续有效：1883 外部 Mosquitto 未加载仓库 ACL 配置；隔离 Broker 已完成真实 QoS1/告警验证，但不能替代生产 1883、`production_area` Facts 和 Core 上下文补链证据。
- 本记录不勾选 Task 10 Step 1–6；剩余工作需要可重复的清洁租户数据、服务端事实 ID 和真实 Broker 条件，不修改数据库、不创建/写入 Broker 密码或 ACL 文件。

**Task 10 完成条件：** P0/P1 均有真实浏览器和事实证据，正向/负向链实际运行，数量和 ID 对账一致，文档与代码和运行时事实一致。

### Task 10 继续执行记录（2026-09-08）

- 已新增 `frontend/e2e/golden-facts.spec.ts`，以动态 `GOLDEN-<运行号>` 命名空间在真实 Gateway/Core/PostgreSQL 上串行执行正式角色；不删除旧数据、不复用旧业务 ID，所有收货、执行、质检、FGR、拣货和发货 ID 均由服务端响应提供。
- `npm run test:e2e:golden` 已切换为执行该事实套件，Chromium `1/1` 通过。实际对账：到货 `3`、拒收 `1`、实收 `2`；原料领料后 `onHand=0`；报工合格 `2`、缺陷 `0`；FGR `2`；两次各发 `1` 后销售行 `ordered/picked/shipped=2/2/2`、订单 `Completed`、成品 `onHand/reserved/available=0/0/0`。
- 真实运行还复现并验证了派工状态顺序：先将派工推进到 `Processing`，再补建 `NotStarted` 执行实例；`OperationExecution` 真正开始时仍要求工单 `Released`。该最小修复已同步 `docs/specs/20-mes/接口契约.md`、`docs/specs/20-mes/领域模型.md`，Task15 focused test `7/7` 通过。
- 负向覆盖已加入：`tenant.admin` 创建派工收到明确 403；页面使用 `sales.liu` 复读本次真实销售订单详情。历史 `golden-flow.spec.ts` 的未创建事实步骤已用明确 `test.skip` 标记，真实业务结论只引用动态事实套件。
- Task 10 Step 4 仍未全部完成：本轮未覆盖计划中全部跨租户、同键异载荷、库存不足、Failed 质检未关闭等负向矩阵；Task 9 的真实 MQTT ACL/QoS1/设备 credential 仍为环境阻塞。Step 5 的全量最终矩阵和 Step 6 的最终 diff 交接待本轮验证收尾。

### Task 10 最终矩阵收尾记录（2026-09-08）

- 在本条记录形成时 Step 4 曾保持未勾选；随后已补齐计划要求的负向/恢复证据，详见下方最新执行记录。跨租户证据来自 Auth/Core/IoT 集成与应用服务测试，浏览器详情专项覆盖刷新、后退、前进和深链；生产 MQTT 边界仍单独受 F12 约束。
- Step 5 已完成：前端单元 13/13、构建 PASS、F01/F02/F03/F11 PASS、真实黄金事实 1/1、Core 155/155、IoT 70/70、Gateway 11/11、Auth 40/40；Auth 迁移静态 6/6，临时 PostgreSQL 12.1 夹具上的真实迁移 4/4，测试后已停止夹具。
- Step 6 已完成：验收台账已同步到 `docs/verification/stage-0-7-ui/README.md`、`issues.md` 与 `frontend/README.md`；最终执行 `git diff --check` 无空白错误，未暂存、未提交、未清库。
- F12 已完成隔离 Broker 的 QoS1 同键重放、异载荷冲突和告警生命周期；下一阶段只需补生产 1883 的仓库 ACL/账号、`production_area` Facts 与 Core 上下文补链，不能把隔离结果扩大成生产环境通过。

### Task 10 负向、恢复与导航收口记录（2026-09-08）

- Step 4 已完成：`golden-facts.spec.ts` Chromium `1/1` 实际执行 tenant.admin 派工 403、UOM 同键重放与异载荷冲突、库存不足、未关闭 Failed 质检阻断 FGR 和非法状态；Core/Auth/IoT 全量测试中的跨租户用例均通过。`frontend/src/composables/__tests__/useCommand.test.ts` 覆盖 503/超时/网络错误保留同一幂等键、结果不确定提示与冲突停止。
- `detail-navigation.spec.ts` 真实详情专项为 `1 passed、1 expected skip`：采购、销售、调拨三个服务端 ID 均完成深链直达、刷新、后退到列表、前进回详情；第二个 404/403 模拟用例因未提供默认账号而按门禁跳过，不计为失败。
- `dashboard-states.spec.ts` + `insights.spec.ts` 为 `5/5`；覆盖空/缺失指标不转零、503 加载失败、stale/no-cache、模拟链路标识、告警恢复/确认和追溯断链。`golden-flow.spec.ts` 修正跨域失败清理后为 `7 passed、5 expected skip`；`manufacturing.spec.ts` 使用 `mes.inspector` 为 `4/4`。
- 完整 `npx playwright test --project=chromium` 组合回归为 `23 passed、11 skipped、0 failed`；11 条均是已声明的事实/角色门禁（无真实详情 ID、Golden MQTT/业务事实缺口、管理员无 MES 写权限或无权路由），不是失败。
- 生产边界保持透明：外部 `1883`、`production_area` Facts 和 Core 上下文补链未验证；隔离 Broker 的真实 QoS1 结果不扩大为生产通过。上述浏览器补丁均已写入测试注释，未提交、未暂存、未清理开发库。
- 对外部 `1883` 仅执行只读 `$SYS/broker/version` 探测：进程使用 `D:\ruanjian\Mosquitto\mosquitto.conf`，匿名连接成功并返回 Mosquitto `2.1.2`；未向业务 topic 发布，也未改动该外部进程。该结果进一步确认生产 ACL/设备 credential 尚未具备验收条件。

## 任务间停止矩阵

| 失败类型 | 停止位置 | 允许的下一步 |
| --- | --- | --- |
| 密码、租户、角色或浏览器不可重复 | Task 0/1 | 只修验证入口或补环境记录 |
| 菜单/迁移无法区分系统种子与租户自定义 | Task 3 | 提交数据识别报告，禁止批量 UPDATE |
| S7 Facts/HMAC/profile 未确认 | Task 4 | 补运行证据，不取消条件装配 |
| 没有受控操作员目录 | Task 5/8 | 提交最小目录契约供用户确认，禁止伪造 UUID |
| 核心库存/状态/幂等契约不一致 | 对应业务 Task | 先报告契约差异，由用户确认 |
| 真实 MQTT/ACL/QoS1 不可用 | Task 9 | 标记环境未验证，不用 simulate 替代 |
| 任一数量、租户或 ID 对账失败 | 当前 Task | 保留失败样本和响应，禁止进入下一 Task |

## 计划自检

- A0–A4 对应 Task 0–5；B1–B5 对应 Task 6–10。
- 文档只使用“执行 AI”“执行者”等工作角色，不包含内部 AI 对比称呼。
- 不使用未定义占位语或空泛“后续再实现/适当处理”；阻塞均有停止位置和报告动作。
- request_id、Idempotency-Key、receiptId、workReportId、inspectionId、salesOrderId、salesOrderLineId 保持不同语义。
- 历史计划勾选项、构建成功、测试列表、HTTP simulate 和健康检查均不被当作业务通过。
