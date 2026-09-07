# 阶段 0–7 页面操作贯通修改计划

> **For agentic workers:** 使用 `executing-plans` 按批执行。每次只领取一个批次，先复现、再最小修改、最后验证；步骤使用复选框跟踪。用户明确将本计划交给其他 AI，本次只排查与编写计划，不执行业务实现，不提交 Git。

**Goal:** 让用户从真实角色菜单进入业务页面，通过页面完成采购、销售、制造和设备事实闭环；消除旧路由、运行配置、权限展示及业务操作入口之间的断点。

**Architecture:** 保留 Gateway/Auth/Core/IoT 四服务和现有 Vue 页面。先修入口及开发启动基线，再按实际断点补齐操作链；后端继续负责权限、租户、状态、事务和库存一致性，不重写已具备的领域内核。

**Tech Stack:** Java 21、Maven 3.9.1、Spring Boot 3.3.5、PostgreSQL 12.1、Redis、Mosquitto、Node 20、Vue 3、TypeScript、Vite 5。

**基准日期：** 2026-09-07。项目根目录：`D:\AI\ai_learn_wms_ai\ai_learn_developProject`。文中仓库相对路径均以此为根。

---

## 1. 先纠正本轮任务的判断方式

现状更准确的表述是：**阶段 0–7 已有大量代码和历史接口验证，但当前启动方式下的页面可达性及业务可操作性尚未验收通过。** 不应整体推倒重做，也不能沿用旧计划的勾选项直接宣布完成。

用户反馈账号为“张管理 / tenant.admin”，涉及控制台、主数据、采购入库、销售直接拣货与发货、生产派工与执行、厂区二维地图。本轮在 `http://localhost:5173` 的现有 `tenant_demo_a / 张管理 / tenant.admin` 浏览器会话中核对了菜单、列表和表单，没有创建/修改业务单据、调整角色权限或重启服务。

`docs/superpowers/plans/2026-09-04-stage-0-7-full-stack-integration.md` 与 `luna-max交接.md` 继续作为历史记录。它们记载了 9 月 5 日接口联调和构建结果，但不等于 9 月 7 日 IDEA 启动环境的浏览器验收。本计划补充当前复核结论；不覆盖既有未提交修改，不直接回滚旧修复。

### 方案选择

| 方案 | 结果及取舍 |
| --- | --- |
| 只修菜单跳转 | 最快解决大量“点了没反应”，但看板、权限、质检、手填 UUID 等断点仍在；作为第一批，不能作为结案 |
| **先修公共断点，再逐条业务链验收（采用）** | 保留后端已有能力，按可复现证据修改；每批可以单独交给 AI 和复核 |
| 重写阶段 0–7 | 改动及回归范围过大，容易破坏库存与租户边界；当前证据不支持采用 |

阶段 9 的“真实浏览器最小业务链”现在就作为修复闸门引入。阶段 8 AI 只读工具与审计暂不扩展；待基础业务事实可信后再实施，最后进行完整阶段 9 验收。

## 2. 本次已确认的断点

证据等级：**A=当前浏览器已复现；B=当前代码直接确认；C=待运行验证的根因假设/历史未验项**。C 不能写成已修复，也不能凭假设扩大改动。

| 编号 | 等级 / 优先级 | 事实与证据 | 实际影响 |
| --- | --- | --- | --- |
| F01 | A+B / P0 | 管理账号菜单的 9 个目标未注册为前端路由，见下表；点击“采购订单”仍停留 `/`。`frontend/src/router/index.ts` 将未知路径重定向 `/`。旧链接可追到 Auth V2/V5 菜单种子 | 主数据、采购、出库、生产和地图入口集体表现为“点不通” |
| F02 | A+B+C / P0 | `/dashboard` 可以进入，但七张卡均显示 `api/dashboard/...` 不存在；正确地图入口 `/gis/site-maps` 同样显示 `api/site-maps` 不存在。Dashboard/GIS/Traceability Controller 受 `core.facts.iot.enabled` 条件控制；基础配置默认 false，`application-dev.yml` 才开启，仓库 `.run/CoreApplication.run.xml`、`.run/IotApplication.run.xml` 没有显式 dev profile | 源码存在接口，但当前运行时不一定装配；不能仅修改前端 URL 或以 health=UP 验收 |
| F03 | A+B / P1 | 正确 `/mes/dispatch` 显示“没有操作权限”，同时“新建派工单”仍可打开；路由守卫只检查登录。项目明确 tenant.admin 不自动拥有全部业务权限 | 菜单可见、页面可进入、按钮可点与真正授权脱节；后端拒绝不等于后端故障 |
| F04 | B / P1 | 采购详情、销售详情、调拨详情组件根节点受 `visible` 控制且默认 false；Router 将其直接挂在 `/:id` 路径，却没提供对应 props 或页面宿主 | 从列表抽屉可看详情，不代表直达链接、刷新、追溯穿透可用 |
| F05 | A+B / P1 | `/master-data` 实际可读取现有商品，创建表单可打开；当前主数据视图只有商品、库位、客户、供应商分支，`createWarehouse/createUom` 没有视图调用。UOM 为自由文本，库位依赖已有仓库 | 不是主数据整体失效；入口修好后仍无法只靠界面补齐仓库/UOM 的从零基础数据 |
| F06 | A+B / P1 | `/sales/picks` 只有查询表格，无详情/拣货/发货入口；显示“拣货任务号”但实际 API 返回订单队列，当前任务号与更新时间显示 `-` | 正确路径也只是只读列表，用户无法沿该工作台继续履约 |
| F07 | A+B / P1 | 派工表单手填工单、工序、操作员、设备 UUID；工艺工作中心、领退料等也保留 UUID 输入。9 月 4 日计划“全部真实选择器”与现代码不一致 | 普通用户不知道 ID，无法建立合法关联，数据填错后只看到后端拒绝 |
| F08 | B / P1 | `frontend/src/api/manufacturing.ts` 已定义生产质检 create/submit/close，但在 `frontend/src/views` 未找到这些函数的调用入口；后端 `ProductionFactController` 已提供相应端点 | 报工之后缺少生产质检操作，无法通过界面达到合格成品可入库条件 |
| F09 | B / P1 | 多个 MES `isActionAllowed` 在 `allowedActions` 缺失/空或找不到动作时返回 true；请求封装只抛普通 Error，丢失状态码/业务码/request_id；每次新请求自动生成新幂等键 | 按钮误开放；结果不确定时重试可能形成新命令，定位失败也缺少链路信息 |
| F10 | A+B / P1 | 采购详情已能打开，但显示 QH-01/RS-01 默认库位，部分名称为空；代码确有默认库位文案，行上读取接口未必提供的聚合数量。采购建单选项通过 `Promise.all` 同时读取可选工单，失败只记 console | 用户可能把未提供数据当真实业务事实；一个可选来源请求失败会导致必填选项一起不赋值 |
| F11 | A+B / P2 | 七张看板卡请求失败时仍显示“实时”，因为 `SummaryCard.vue` 对 `stale` 的 else 分支直接显示实时 | 错误状态与新鲜度互相矛盾 |
| F12 | C / P1 | 历史交接明确真实 Mosquitto ACL/QoS1、IoT 正向生产上下文样本、生产区域 Facts 等未完成验证；当前 dev 配置也说明 HTTP simulate 不等于开启 MQTT | 阶段 6–7 不能只凭 HTTP simulate 和服务测试宣布端到端完成 |

F02 根因按顺序验证：①当前启动未启用 dev/Facts；②当前进程使用旧 class/resource 或运行入口不同；③条件 Bean 装配未满足。已核对 Core JVM 来自项目 Java 21，工作目录为 `backend`；JVM 系统属性未出现显式 active profile，但这**不能排除环境变量及其他 Spring 配置覆盖**。本轮没有读取完整 Spring Environment，也没有重启，最终运行根因保留待验证。

### 真实菜单与正式路由映射

| 当前菜单 URL（浏览器读取） | 计划采用的正式入口 | 需要保留的业务含义 |
| --- | --- | --- |
| `/master-data/products` | `/master-data?tab=products` | 商品页签 |
| `/master-data/warehouses` | `/master-data?tab=warehouses` | 补真实仓库页签，并可进入其库位 |
| `/master-data/inventory` | `/inventory/balances` | 库存台账 |
| `/purchase/orders` | `/purchasing/orders` | 采购订单 |
| `/purchase/inbound` | `/purchasing/receipts` | 收货入口保留，提供到质检/处置的下一步 |
| `/purchase/putaway` | `/purchasing/putaway` | 上架任务 |
| `/sales/outbound` | `/sales/picks` | 必须在第 6 批补齐履约操作，不能只跳到只读表 |
| `/mes/execution` | `/mes/dispatch` | 派工与执行之间有明确跳转，执行正式路由为 `/mes/executions` |
| `/gis/map` | `/gis/site-maps` | 多地图列表，选择后进入 `/:id` |

`tab` 查询参数及 warehouses 页签是本计划新增内容，**当前尚未实现**。修路由时先使旧 URL 兼容，主数据批次补齐页签语义。继续检查其余角色菜单、IoT、AI 与系统管理节点，不能只修这 9 条；阶段 8 节点明确未开放，不伪装成阶段 7 完成项。

### 已执行的失败判定

在项目根目录用 Node 执行下列只读检查，结果为 `checked=9, missing=9`，输出 `FAIL: observed menu targets are not registered routes`。该静态检查辅助定位；最终以真实 `/api/me/menus` 加浏览器跳转断言为准，不能只靠搜索文本结案。

```powershell
@'
const fs = require('fs');
const router = fs.readFileSync('frontend/src/router/index.ts', 'utf8');
const routes = new Set([...router.matchAll(/path:\s*"([^"]*)"/g)]
  .map(m => '/' + m[1].replace(/^\//, '')));
const menuPaths = ['/master-data/products', '/master-data/warehouses',
  '/master-data/inventory', '/purchase/orders', '/purchase/inbound',
  '/purchase/putaway', '/sales/outbound', '/mes/execution', '/gis/map'];
const missing = menuPaths.filter(p => !routes.has(p));
console.log(JSON.stringify({checked: menuPaths.length, missing}, null, 2));
if (missing.length) {
  console.error('FAIL: observed menu targets are not registered routes');
  process.exitCode = 1;
}
'@ | node
```

## 3. 执行边界与交付规则

- 开始每批前读 `AGENTS.md`、正式项目计划、词汇表以及对应领域的概述/模型/契约/验收标准；页面同步读对应原型和交互说明。普通任务，不启动 OpenSpec 流程。
- 当前工作树已有大量前后端、规格及迁移修改。先记录文件状态和相关 diff，按当前工作树继续；禁止直接提交、清库、覆盖用户修改或重写历史迁移。
- 业务使用本机 `127.0.0.1:5433/ai_learn`，但破坏性迁移测试只在隔离 PostgreSQL 12.1 环境运行，不能把历史练习库联调授权解释成可以清理用户库。
- 修新增菜单数据使用新的 Flyway 版本；当前源码 Auth 已有 V7，候选为 V8，执行前检查版本是否被占用。只定向修正系统种子菜单已知旧值，保留用户自定义路径、角色授权、状态、排序与软删除记录。禁止修改已执行 V2/V5。
- 账号授权以真实角色为准，不给 tenant.admin 默认授全业务权限，不借增加管理权限解决普通业务选择器问题。需要业务操作时使用相应正式角色；给同一账号增配角色须由用户另行决定。
- 保留数量精度及现有契约：不要全仓强行转换大小写；`uom` 按当前 DTO 为编码/字符串，并非 UUID。业务实体 ID、命令关联 ID、幂等键明确区分。
- 新增方法以中文说明用途、入参、出参及流程；修改已有方法注明用途。核心业务规则变动补自动化回归，接口/权限/验收变化同步领域文档。
- 每批交付“实际文件、失败复现、最小修复、通过证据、仍未验证项”；尚未运行的测试不得打勾，不能用构建通过替代浏览器业务成功。
- 下列文件名单中“新增”表示建议新建，执行前检查重名；“检查/必要时修改”只在本批有证据时改动，不是批量重写授权。

## 4. 分批修改任务

### 第 0 批：保存可重复的运行基线与问题台账

**依赖：** 无。**目标：** 防止执行 AI 用另一套启动命令、另一组权限或临时数据证明“已修好”。

**文件：** 新增 `docs/verification/stage-0-7-ui/README.md`、`docs/verification/stage-0-7-ui/issues.md`；本轮只读 `.run/`、各服务 `application*.yml`、`runtime/README.md` 和现有迁移历史。

- [ ] 保存脱敏环境摘要：前后端端口、Java/Node 版本、启动入口、active profiles、构建时间、三个 Flyway history 最新版本及 success 状态。凭据、JWT、HMAC 不写日志和报告。
- [ ] 按 F01–F12 建问题行：账号角色→菜单→URL→按钮→HTTP 状态/业务码→实际结果→预期结果→证据等级。必须标明从菜单点击还是手输 URL。
- [ ] 建立菜单遍历的浏览器回归入口。前端当前只有 dev/build/preview，没有已配置的 test/e2e 脚本；若引入测试运行器，在 `frontend/package.json` 增加真实命令和依赖，不能直接运行一个不存在的 `npm run test` 后忽略失败。
- [ ] 以“采购菜单点击不应回 `/`”“看板七接口应已注册”“无派工权限不显示创建入口”作为首组三个红色断言。测试使用真实服务；mock 只用于独立错误态组件测试。

**通过条件：** 同一启动方式、同一角色可重复得到相同断点；执行 AI 无需猜账号、入口或当前版本。此批不能标记业务完成。

### 第 1 批：修菜单、路由、详情直达与未知页面反馈（P0）

**依赖：** 第 0 批。**对应：** F01/F04。

**文件：**
- 修改 `frontend/src/router/index.ts`、`frontend/src/components/Layout/AppLayout.vue`。
- 修改 `frontend/src/views/purchasing/PurchaseOrderDetailView.vue`、`frontend/src/views/sales/SalesOrderDetailView.vue`、`frontend/src/views/inventory/TransferDetailView.vue`，或新增对应 `PurchaseOrderDetailPage.vue`、`SalesOrderDetailPage.vue`、`TransferDetailPage.vue` 页面宿主；优先宿主复用原抽屉。
- 新增 `frontend/src/views/NotFoundView.vue`；必要时新增 Auth `V8__align_builtin_menu_routes.sql`。
- 测试建议新增 `frontend/e2e/menu-routes.spec.ts`、`frontend/e2e/detail-navigation.spec.ts`；数据库回归复用 `backend/platform-auth/src/test/java/com/ailearn/platform/auth/AuthMigrationScriptTest.java` / `AdminManagementIntegrationTest.java`。

- [x] 用真实 `/api/me/menus` 枚举所有有 URL 的可见叶节点，检查实际 Router 匹配结果。父菜单用于展开时不当作业务页；未知路径不能悄悄回首页。
- [x] 按映射表新增明确的旧路由 redirect，并修种子菜单正式路径。redirect 保留业务 ID、查询筛选和返回位置；不建立第二套页面。兼容示例：

```ts
{ path: 'purchase/orders', redirect: '/purchasing/orders' },
{ path: 'purchase/putaway', redirect: '/purchasing/putaway' },
{ path: 'sales/outbound', redirect: '/sales/picks' },
{ path: 'mes/execution', redirect: '/mes/dispatch' },
{ path: 'gis/map', redirect: '/gis/site-maps' },
```

- [x] 用页面宿主读取 `route.params.id`，加载真实详情，并将 `visible=true` 和正确对象/ID 传给原抽屉；关闭返回业务列表。直接路由加载要主动请求，不能只依赖非 immediate 的 props watch。
- [x] 给真实不存在的 URL 展示可返回的 404 页面；资源不存在与无权限使用正确反馈，均不得显示空白业务区。
- [x] 迁移后重新获取菜单；验证刷新、浏览器后退、旧收藏 URL、详情 URL 和登录后的 redirect。不要清空整库 Redis。

**通过条件：** 所有已开放菜单都进入对应业务页或清晰的无权限页；采购/销售/调拨详情从列表、直链、刷新三种方式均正常。第 4 批完成后追加主数据 tab 深链断言。

### 第 2 批：修实际启动配置与 S7 接口可用性（P0）

**依赖：** 第 0 批，可在第 1 批之后执行。**对应：** F02/F11。

**文件：** `.run/CoreApplication.run.xml`、`.run/IotApplication.run.xml`、必要时 `.run/GatewayApplication.run.xml`、`.run/ALL_Services.run.xml`、`.run/BACKEND_Services.run.xml`；Core/IoT 的 `application-dev.yml`；`backend/platform-core/src/main/java/com/ailearn/platform/core/traceability/config/S7ApiConfiguration.java` 及 `dashboard/controller/DashboardController.java`；`frontend/src/views/insights/components/SummaryCard.vue`、`frontend/src/api/insights.ts`。同步 `runtime/README.md`、`backend/README.md`，检查根 README/架构文档。

- [ ] 先读取当前 active profiles、启动日志和条件装配结果，验证 F02 三个假设。记录是配置问题、旧产物还是 Bean 条件问题；只有对应证据成立才改该处。
- [ ] 使项目提供的 IDEA Core/IoT 启动配置显式启用本地 dev，并验证资源被当前构建输出包含。现有两端服务地址及 HMAC 配对一致；不要把本地默认密钥推广到其他环境。
- [ ] 核对组合启动引用。Gateway 配置显示名仍为 10001，但实际配置是 20001；如修显示名，同时更新两个组合启动引用，不要错误修改实际端口。
- [ ] 经 Gateway 验证七个 dashboard 端点、site-maps、traceability、exception-center 已注册；合法且有权限的查询成功，无权限请求明确拒绝，非法查询不得被当成“接口不存在”。
- [ ] 若 Facts Bean 条件本身有问题，补 `S7ApiConfigurationTest` 的实际装配用例后再最小修复。不要把缺失事实源替换为零数据成功，不直接取消鉴权。
- [ ] `SummaryCard.vue` 把 error、未知新鲜度、stale、明确 fresh 分开：只有成功且明确新鲜才显示实时；没有成功缓存则显示不可用，有旧结果则保留其原始时间与 stale 标记。
- [ ] 按用户日常 IDEA 组合启动停止/重启一次，再走浏览器查询，验证不依赖 AI 临时 shell 中残留的环境变量。

**通过条件：** 使用文档化启动入口即可复现可用的 S7；七卡和地图无“接口不存在”；一次源故障显示真实错误或旧数据标记。关闭/开启配置各自行为有记录；健康检查不能代替本闸门。

### 第 3 批：对齐角色、动作权限、错误提示与命令重试（P1）

**依赖：** 第 1–2 批。**对应：** F03/F09。

**文件：** `frontend/src/stores/auth.ts`、`frontend/src/router/index.ts`、`frontend/src/utils/request.ts`、`frontend/src/components/Layout/AppLayout.vue`；各领域现有 `isActionAllowed` 调用位置；必要时 Auth 菜单过滤及角色配置代码，Core 相关动作投影。测试新增 `frontend/e2e/role-access.spec.ts`、`frontend/e2e/command-retry.spec.ts`。

- [ ] 输出六类正式角色的“菜单、页面读取、创建、状态动作、选择器读取”矩阵，并与服务端实际权限比对。租户管理员的管理职责与业务操作职责分开，不能以“管理员应有所有权限”作为修复前提。
- [ ] 缺少读权限时展示独立无权限态并停止无意义重试；缺少写权限时不显示/禁用写按钮。登录态、权限、后端状态限制同时满足才允许操作。
- [ ] 对 `allowedActions` 按端点确认契约：契约承诺该字段时，缺失视为异常、未知动作拒绝；尚不返回动作表的事实端点不能机械改成全禁用，先补必要的服务端动作能力或按正式权限+已冻结状态生成前端展示限制。后端仍最终校验。
- [ ] 错误封装保留 `httpStatus`、业务 `code`、`request_id`、message，页面显示可理解原因并提供可复制请求号。403、404、409、422、503 与网络超时分开，不能只留下 console。
- [ ] 一个用户命令创建一次幂等键，在执行中与结果不确定的重试中复用；明确开始新的业务操作才换键。超时后先复读事实，禁止自动换键重复出库/收货。前端禁双击，后端幂等与事务仍保留。
- [ ] 用 tenant.admin 验证管理功能与明确的业务拒绝；用 sales/buyer/warehouse/mes/iot 正式角色验证允许动作；用另一租户验证隔离。测试不得借临时全权限角色过关。

**通过条件：** 正常业务角色能操作、无权角色明确受限；用户不再看到“能点但必失败”的假按钮；同键重放只产生一次事实，409 提示复读，503/超时保留结果不确定状态。

### 第 4 批：补齐主数据与可用选择器（P1）

**依赖：** 第 1/3 批。**对应：** F05/F07/F10。

**文件：** `frontend/src/views/masterdata/MasterDataView.vue`、`frontend/src/views/masterdata/components/MasterDataEditor.vue`、`frontend/src/api/masterData.ts`、`frontend/src/api/admin.ts`、`frontend/src/api/manufacturing.ts`、相关 types；后端复用 `masterdata/controller` 的 Warehouse/Uom/Product/Location 入口。测试建议 `frontend/e2e/master-data.spec.ts`，后端复用 `masterdata/MasterDataApplicationServiceTest.java`。

- [ ] 增加仓库和 UOM 的最小列表/创建/编辑/状态入口，保留现有商品、库位、客户、供应商。实现 `?tab=` 初始化和页签切换回写 URL；刷新保留所在页签。
- [ ] UOM 选择从真实目录取编码作为值；产品 UUID 与 UOM 编码不得混淆。库位选择先仓库后库位，按正式库位类型过滤，停用对象不可被新业务引用。
- [ ] 必填选项逐项显示加载、空、失败、重试；可选来源工单失败不拖垮供应商/产品/仓库选项。不能在 `Promise.all` 全拒绝后仅记 console 并留下空表单。
- [ ] 业务选择器支持搜索或翻页；不能只取前 200 条就把后面的记录当不存在。改变上游选项时清除不再合法的下游值。
- [ ] 选择器显示编码+名称，提交真实 ID，后端继续校验有效性、租户及关联；缺少基础资料时明确指向有权限的维护入口。
- [ ] 工作中心与生产区域完整维护入口本轮未在仓库中确认；先检查事实源及规格。若确缺闭环必需的工作中心选择来源，补最小目录契约及维护/初始化入口，禁止为通过派工伪造 UUID。操作员目录同样先找现有 Auth 查询能力；若普通生产角色无权使用管理接口，设计受控的最小人员选项读取，不授予用户管理权限。

**通过条件：** 在没有预先手工写入数据的测试样本中，用户可通过页面建仓库/UOM/产品/库位和客商；采购/销售/MES 表单无需猜 ID。工作中心及操作员选项缺口若未解决，本批不得宣称完整通过。

### 第 5 批：采购到货、质检处置和上架逐步连通（P1）

**依赖：** 第 1/3/4 批。**对应：** F04/F10。

**文件：** `frontend/src/views/purchasing/PurchaseOrderListView.vue`、`PurchaseOrderDetailView.vue`、`ReceiptConfirmView.vue`、`QualityDispositionView.vue`、`PutawayTaskView.vue`、`frontend/src/api/purchasing.ts`、`frontend/src/types/purchasing.ts`；必要时 Core purchasing/quality/putaway 的 DTO 和只读投影。测试新增 `frontend/e2e/purchase-inbound.spec.ts`，服务回归复用 `purchasing/PurchasingApplicationServiceTest.java`。

- [ ] 在采购入口完成创建→提交→审核，并能由仓库角色找到可收货订单；`/purchasing/receipts` 即使复用订单页，也必须表达收货用途、提供可收货筛选与下一步指引。
- [ ] 用独立 receipt ID 与订单行 ID 完成部分拒收/实收，保留 `arrived = rejected + received`；全拒收不加库存、不减少待收，实际接收全部进入 QualityHold。
- [ ] 收货后可导航到该收货事实的质检；生产质检角色录入合格/不合格，再分别由有权角色下达放行/报废/退回决定，仓库确认执行。成功后复读详情和库存。
- [ ] 从放行事实导航到上架任务，选择同仓合法目标库位并确认；不得要求用户在无过滤的全库 UUID 中查找。
- [ ] 删除订单详情 QH-01/RS-01 硬编码事实兜底。仓库、供应商、产品等名称用真实投影或既有受控查询补齐；未返回数量不展示为已发生的 0，逐项核对收货/检验/处置/上架事实来源。
- [ ] 验证完成订单的存量收货仍可继续质检处置及上架；人工完成必须填写原因，不补造库存/履约事实。

**通过条件：** 一笔采购从菜单进入即可走完整链；实收增加一次库存，质检不变库存，放行/上架只移动，报废/退回只扣减确认数量，刷新与角色交接不丢来源。

### 第 6 批：销售工作台真正支持直接拣货与发货（P1）

**依赖：** 第 1/3/4 批，有可用库存样本。**对应：** F06/F09。

**文件：** `frontend/src/views/sales/PickTaskView.vue`、`SalesOrderListView.vue`、`SalesOrderDetailView.vue`、`ShipmentConfirmView.vue`、`ReservationDetailView.vue`、`frontend/src/api/sales.ts`、`frontend/src/types/sales.ts`；必要时 Core sales/fulfillment 的现有查询 DTO。测试新增 `frontend/e2e/sales-fulfillment.spec.ts`，复用 `sales/fulfillment/SalesFulfillmentApplicationServiceTest.java`。

- [ ] 将 `/sales/picks` 明确实现为订单驱动履约队列，展示真实订单、待拣/暂存/已发数量和下一步动作；移除不存在的任务号字段，使用后端分页，不再固定第一页和客户端局部过滤。
- [ ] 从队列打开现有销售详情，复用直接拣货与发货操作；提供可点击入口，并保留筛选与返回位置。不复制一套库存命令逻辑。
- [ ] 正常流程为 Approved→直接拣货→发货；不增加强制人工预留步骤。来源库位按商品/仓库/批次/可用量选择，目标为真实 ShippingStaging。
- [ ] 直接拣货同事务自动预留并移位；发货只处理已暂存且未发数量。履约操作 ID、salesOrderId、salesOrderLineId 不混用，重试维持同一逻辑命令。
- [ ] 支持部分拣货/部分发货以及未发货拣货退回；在人工完成前处理暂存实物，保留完成原因和实际数量。

**通过条件：** 从用户原“直接拣货与发货”菜单可完成订单履约，不需要手输 URL；直接拣货不扣企业实物、发货才扣减并释放对应预留。库存不足、重复点击、并发冲突不会多扣库存。

### 第 7 批：MES 派工、执行、报工、质检与成品入库（P1）

**依赖：** 第 1/3/4 批，采购原料样本可用。**对应：** F07/F08。

**文件：** `frontend/src/views/manufacturing/DispatchView.vue`、`OperationExecutionView.vue`、`MaterialMovementView.vue`、`RoutingListView.vue`、`WorkOrderListView.vue`、`WorkOrderDetailView.vue`、`FinishedGoodsReceiptView.vue`、`frontend/src/api/manufacturing.ts`、`frontend/src/types/manufacturing.ts`；新增 `frontend/src/views/manufacturing/components/ProductionQualityPanel.vue` 并嵌入现有工单详情。复用 Core `manufacturing/productionfact/controller/ProductionFactController.java`。测试新增 `frontend/e2e/manufacturing.spec.ts`，复用 `manufacturing/task15/Task15DispatchOperationContextTest.java` 和 `manufacturing/productionfact/ProductionFactApplicationServiceTest.java`。

- [ ] 从已下达工单进入派工时带入工单 ID；工序来自该工单冻结工艺快照，操作员来自受控同租户目录，可选设备来自真实设备查询。保留“无设备”的合法人工工序。
- [ ] 派工下达后提供“创建/进入执行”入口，携带派工及关联工单/工序信息；开始、暂停、恢复、完成按服务端状态限制执行，前置工序不完成时说明原因。
- [ ] 领退料表单用工单/BOM 及真实库存维度选择，金额/数量格式沿用契约；超 BOM 必须原因及对应权限，确认实物变化由仓库角色执行。
- [ ] 工序完成后可报工并读取真实报工列表，显示合格、不良、合计及剩余可报量；禁止把报工合格直接视为最终质检合格。
- [ ] 在工单详情新增生产质检面板：选择真实 workReportId→调用 `createQualityInspection`→输入合格/不良和结果→调用 `submitQualityInspection`；Failed 显示有权限的 ISOLATE/SCRAP/CLOSE 操作并调用 `closeQualityInspection`。使用返回 inspection ID，不把 workOrder ID 误作 inspection ID。
- [ ] 质检每次写入后复读 `getQualityInspections(workOrderId)` 及工单摘要；Draft/Submitted/未关闭 Failed 保持真实质量阻塞，不能用前端改状态解锁。
- [ ] 由可入库合格数量引导创建 FGR，仓库确认后复读库存和工单；工单正常完成/人工完成各自沿现有规则，不补造报工和入库。

**通过条件：** 生产与仓库角色可从页面完成“工单→领料→派工→执行→报工→生产质检→成品入库”；全程无需复制 UUID。质检失败场景确实阻止非法入库，累计派工/报工/入库上限及前置工序约束有回归。

### 第 8 批：IoT 实机链路及追溯/GIS/看板收尾（P1/P2）

**依赖：** 第 2/3/7 批。**对应：** F12，以及前面修复后的跨域结果。

**文件：** `frontend/src/views/iot/TelemetryView.vue`、`DeviceDetailView.vue`、`AlarmDetailView.vue`；`frontend/src/views/insights/TraceabilityView.vue`、`SiteMapListView.vue`、`SiteMapEditorView.vue`、`SiteMapView.vue`、`DashboardView.vue`、`ExceptionCenterView.vue`；对应 `api/iot.ts`、`api/insights.ts`；IoT MQTT、contextlink 和 Core Facts 相关配置/适配器只在复现后最小修改。检查 `deploy/local/mosquitto.conf`、`runtime/README.md`。测试新增 `frontend/e2e/insights.spec.ts`，复用 Core `s7` 与 IoT `contextlink` 测试。

- [ ] 核对 MQTT listener 开关、真实 Broker 配置、订阅权限和设备主题。通过真实 Mosquitto QoS1 发布消息验证遥测→状态→告警；HTTP simulate 单独标注为开发模拟，不以其按钮文案宣布 MQTT 验收通过。
- [ ] 同一有效消息重复投递仅产生一次事实；异载荷同键按契约拒绝。验证超阈值触发、确认、恢复，并用当前活动工序补上正向生产上下文。
- [ ] 验证 Core 暂时不可用时 IoT 事实保留，恢复后补链；只在可控测试运行中注入故障，避免随意停止用户正在使用的业务服务。
- [ ] GIS 从真实地图列表进入，点位按实体类型查询仓库/设备/生产区域，显示名称而非要求手填 UUID；生产区域事实源缺失须显式列缺口，不用空集合伪装完成。
- [ ] 保存百分比坐标后刷新保持位置，调整视窗不漂移；点位穿透使用第 1 批的可刷新详情路由，目标仍执行权限校验。
- [ ] 追溯可从真实业务详情带入 entity_type/entity_id，保留缺失来源、权限裁剪与 truncated 提示；地图和看板数据与同一批库存、订单、工单、设备事实一致。
- [ ] 删除正式业务流程中的模拟角色授权效果；真实权限由会话和后端裁剪决定。开发演示开关若保留需明确隔离，不能混入验收截图。

**通过条件：** 一次真实设备异常能在 IoT、工单上下文、追溯和地图中对应到同一事实；七卡范围切换与 30 秒刷新可用，源故障不伪造新鲜数据。生产区域等必需源未确认时，阶段 7 仍标为部分未验收。

### 第 9 批：浏览器最小黄金闭环、回归和文档交接

**依赖：** 第 1–8 批。**文件：** 新增 `frontend/e2e/golden-flow.spec.ts`、更新 `docs/verification/stage-0-7-ui/README.md` 与 issues 台账；同步 `frontend/README.md`、相关领域验收标准及项目阶段决策入口的当前状态。

- [ ] 按下节样本，业务写入通过真实角色页面完成；API/只读数据库查询用于断言事实，不能先用 API 写完全程再拍空页面作为端到端证据。
- [ ] 测试登录不同角色时使用各自账号/上下文；同账号单会话限制仍生效，不并行重复登录同一账号造成随机 401。
- [ ] 覆盖刷新、后退、直接深链、空列表、加载失败、403、409、超时结果不确定、同键重试；租户 B 拒绝关联租户 A 对象。
- [ ] 执行后端受影响测试、前端构建、真实浏览器回归和 `git diff --check`；记录命令、时间和退出码。迁移测试与普通回归独立报告，不隐去 skip。
- [ ] 核对 README、领域 API/模型/验收、原型说明与运行入口；新事实写入长期文档，历史交接只追加复核链接，不继续保留互相冲突的“全通过”说法。

**通过条件：** 全部 P0/P1 已关闭且有浏览器与事实核对证据；剩余项目逐一标“未在仓库中确认”或“未验证及原因”，不能以阶段编号完成度代替可操作性。

## 5. 统一验收数据与数量对账

建议在获准使用的练习/测试数据范围内创建独立前缀样本，例如 `UIR0907-<运行号>`；不复用或修改用户现有正式单据，不删除旧样本。以下为**计划创建的验收样本，不是当前库事实**。

| 步骤 | 页面行为 | 必须核对的事实 |
| --- | --- | --- |
| 基础数据 | 建客户、供应商、UOM、原料 R、成品 F、仓库及 QualityHold/ReceivingStaging/Storage/ShippingStaging 库位 | 所有选择来自真实目录；BOM 每件 F 消耗 1 件 R，工艺及工作中心有效 |
| 销售需求 | 销售角色创建并审核 2 件 F 订单 | 返回真实订单行 ID，尚未改变库存 |
| 制造来源 | 生产角色创建 2 件 F 工单，人工关联该销售行 | 冻结 BOM/工艺，提交审核后下达 |
| 采购来源 | 采购角色创建原料 3 件订单，人工关联工单 | 有明确来源，无自动 MRP |
| 外观接收 | 仓库录入到货 3、拒收 1、实收 2 | 3=1+2；R 实物只增加 2，均在 QualityHold；采购仍待收 1 |
| 质检/放行/上架 | 生产质检检验 2 合格，决定放行；仓库执行并上架 | 三步均不再增加企业实物，总 R=2；位置逐步变化 |
| 领料/执行 | 生产创建领料，仓库确认 2 R；派工并执行 | R 减为 0，存在领料流水；执行开始/暂停/恢复/完成可审计 |
| IoT | 同一设备上报告警及恢复消息，重复投递一次 | 事实不重复，关联本次执行及工单 |
| 报工/生产质检/入库 | 报工合格 2、不良 0，生产质检 Passed；仓库确认入库 2 F | F 实物增加 2，未检验或超额入库被拒绝 |
| 部分履约 | 仓库直接拣货 2 F，先发 1，再发 1 | 拣货后 F onHand=2/reserved=2/available=0；首次发货=1/1/0；全部发货=0/0/0 |
| 结束与追溯 | 采购人员填写原因终止剩余未收 1；读取订单/工单/流水/告警/地图/看板 | 不伪造收货；销售履约完成，全部来源可追，统计只计真实发生数量 |

独立失败样本必须覆盖：全拒收；不合格质量处置；库存不足拣货；部分拣货退回；超 BOM 无原因/无权；前置工序未完成；未检验成品入库；旧 version 冲突；同键同载荷重放/异载荷冲突；跨租户关联；缺事实源。失败后余额、流水、关联记录均不能部分成功。

## 6. 验证命令与证据格式

后端快速回归可沿用已记载的环境路径；以下是**执行阶段命令，本次编写计划未重新运行这些构建/测试**：

```powershell
$env:JAVA_HOME='D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\jdk'
Set-Location 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\backend'
& 'D:\ruanjian\apache-maven-3.9.1\bin\mvn.cmd' test '-Dtest=!AuthPostgresMigrationTest' '-Dcheckstyle.skip=true' '-Dmaven.repo.local=D:\project\MavenRepository391'
if ($LASTEXITCODE -ne 0) { throw '后端回归失败，停止本批交付' }
```

这里显式排除需要隔离 PG 的 AuthPostgresMigrationTest，并沿用历史 checkstyle skip，**不表示迁移或 Checkstyle 验证通过**。修改迁移后必须额外在隔离 PostgreSQL 12.1 验证新库与升级库；按涉及模块的更严格验收标准补足所需检查。

```powershell
Set-Location 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\frontend'
npm run build
if ($LASTEXITCODE -ne 0) { throw '前端构建失败，停止本批交付' }
# 第 0 批建立真实 test:e2e 脚本后运行；此前不能声称该命令存在。
npm run test:e2e
if ($LASTEXITCODE -ne 0) { throw '浏览器回归失败，停止本批交付' }
Set-Location 'D:\AI\ai_learn_wms_ai\ai_learn_developProject'
git diff --check
```

每批报告格式：

```text
批次及 F 编号：
基准启动入口 / profile / 租户 / 角色：
修改文件及原因：
修复前最短复现与实际失败：
修复后浏览器步骤、最终 URL 与页面结果：
对应 HTTP 状态、业务码、脱敏 request_id：
库存/业务事实前后对账：
执行的测试命令、退出码及证据位置：
未执行项与原因：
对下一批的依赖及交接：
确认未提交 Git、未覆盖其他改动：
```

## 7. 给执行 AI 的分批提示词

每次只替换“第 N 批”，不要把“修复全部阶段”作为无限范围任务：

> 读取 AGENTS.md 和 `docs/superpowers/plans/2026-09-07-stage-0-7-ui-repair-plan.md`，执行第 N 批。以当前未提交工作树为基线，先复核该批 F 编号问题及依赖闸门，再做最小修复。使用正式角色、真实 Gateway 与浏览器页面验证，不用临时全权限角色、假数据投影或仅构建通过代替验收。不得提交 Git、清库、覆盖已有改动、修改已执行历史 Flyway。没有证据的情况写“未在仓库中确认”或“未验证及原因”。本批遇到跨批依赖时先完成独立项并记录具体接口/文件阻塞，不自行扩展到整块核心业务重写。交付按文档第 6 节格式，最后追加喵~。

建议领取顺序：**0→1→2→3→4→5→6→7→8→9**。先拿到第 1–2 批结果复核，通常能先解除用户当前最明显的一组入口故障；之后按业务链收口。当前没有可靠的逐按钮统计，不给出虚构的“完成百分比”或保证工期。

## 8. 本次排查的验证边界

- 已执行：当前浏览器管理账号菜单读取、采购旧入口点击、控制台七卡错误、正确主数据查询及新建表单打开、正确采购查询及详情抽屉、派工无权限态及新建表单、拣货只读队列、正确地图入口错误；9 条菜单路由静态失败检查；项目服务监听与 Core JVM 基线核对。
- 代码确认：旧种子菜单、路由兜底、抽屉宿主缺口、S7 条件配置、主数据维护入口、UUID 输入、生产质检无视图调用、默认允许动作、错误封装及看板实时标识。
- 未执行：业务写入、六角色全量操作、数据库迁移、实际 Spring Environment 完整取证、服务重启、MQTT 真消息、本轮后端测试及前端构建。因此本文件是有证据的修改计划，**不声称业务已经修复**。
- 本轮仅新增本计划；后续实现和验收由用户指定的其他 AI 按批执行。喵~
