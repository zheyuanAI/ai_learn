# Frontend

## 当前状态
- **工程与基础设施**：基于 Vue 3 + TypeScript + Vite + Pinia 构建，已建立统一请求拦截器 (`src/api/request.ts` 与 `src/utils/request.ts`) 及全局认证状态管理 (`src/stores/auth.ts`)。
- **登录与会话**：已接入真实登录页 (`LoginView.vue`)，支持租户编码/用户名/密码登录、单有效会话管理、Token 存储与登出重定向。
- **后台管理系统（阶段1基础接口已接入；本轮修复中，阶段验收未完成）**：
  1. **租户设置**（`/system/tenant`）：端点统一为 `GET/PUT /api/auth/admin/tenants/current`，查询并更新当前企业租户名称与基础配置。
  2. **用户管理**（`/system/users`）：用户增删改查、分配角色、重置密码。分页查询入参统一为 `page`, `size`, `roleId`；重置密码端点统一为 `POST /api/auth/admin/users/{id}/reset-password`（入参字段 `newPassword`）；删除统一为逻辑软删除（`isdel = 1`）；并在前端提供防自删与最后管理员安全拦截提示。
  3. **角色与授权管理**（`/system/roles`）：角色增删改查，支持为角色分配权限点（Role-Permission）与绑定菜单（Role-Menu）。授权提交严格匹配后端全量 ID 前置校验机制（遇非法/跨租户/已删除/停用 ID 整体拒绝）。
  4. **权限目录查询**（`/system/permissions`）：只读展示系统内置权限树/列表，采用统一冒号分段规范（如 `auth:user:view`、`auth:role:edit` 等），权限目录不可修改，由角色负责授权。
  5. **菜单管理**（`/system/menus`）：前端动态菜单树的管理与维护，基于 Flyway V5 迁移实现的 `tenant_id` 多租户菜单物理隔离；普通更新维护 `menuCode`、`visible`，启停接口维护 `status`（`ACTIVE`/`DISABLED`）。
- **权限与租户边界**：
  - 前端接口统一对接后端 `/api/auth/admin/**` 路由；
  - 租户管理员只管理当前租户数据，一期无平台超级管理员；
  - 全量 ID 校验与逻辑软删除（`isdel = 1`，禁止物理 `DELETE`）前后端严格对齐；
  - 全局路由守卫自动校验 `requiresAuth` 与登录态，未登录自动重定向至登录页。

## 规格入口
- 当前开发计划与一期范围：`../docs/specs/00-project/正式项目计划.md`
- 页面范围与交互基线：`../docs/specs/00-project/原型与交互说明.md`、`../docs/prototype/README.md`
- 领域规则、接口与验收：对应领域目录的 `概述.md`、`领域模型.md`、`接口契约.md`、`验收标准.md`

## 本地开发环境基线
- 本前端工程的 Node 版本由 `package.json` 的 `engines` 约束为 20.x。
- Java、Maven、PostgreSQL、服务端口及参考工程隔离规则以 `../docs/specs/00-project/架构设计.md` 和根目录 `../README.md` 为准。

## 当前交接重点（Task 10）

第 5–8 批的代码级修复已分别记录在阶段验收报告中；本轮已完成一条清洁样本的真实黄金事实闭环，剩余结论按证据边界拆分：

1. 采购收货、质量处置、销售直接拣发和 MES 生产质量已由 `golden-facts.spec.ts` 在动态清洁样本中完成 PostgreSQL 数量对账；
2. IoT 已在隔离 Mosquitto `18884` 完成真实 ACL、credential、QoS1 重放/冲突和告警生命周期；外部 `1883`、`production_area` Facts 与 Core 上下文仍未确认；
3. 后续只需补生产 MQTT 与跨服务上下文边界，不得以隔离 Broker 或 HTTP simulate 扩大结论。

## 阶段 0–7 浏览器回归与黄金闭环

### 1. 基础菜单与核心断言回归
- 执行入口：`npm run test:e2e`（调用 `e2e/menu-regression.ps1` 与 `@playwright/cli`）；
- 核心断言：采购菜单正式路由跳转（F01）、看板接口与实时标签新鲜度（F02/F11）、无权派工单入口裁剪（F03）；
- 凭据配置：通过环境变量 `STAGE_UI_PASSWORD` 或 `STAGE_UI_STATE_FILE` 注入，杜绝硬编码密码。

### 2. 黄金闭环前置探测（Task 10，尚未替代业务验收）
- 规格文件：`e2e/golden-flow.spec.ts`（配置文件：`playwright.config.ts`）；
- 覆盖入口：探测 11 个业务阶段（主数据 ➔ 销售需求 ➔ 生产工单 ➔ 原料采购 ➔ 外观验收 ➔ 质检放行上架 ➔ 领料与工序执行 ➔ IoT 告警去重 ➔ 报工质检入库 ➔ 销售履约直接拣发 ➔ 采购人工终止与全域追溯）及权限边界负向用例；当前步骤 1–4、7、11 的通过只表示页面/表单入口可访问，不表示写入、状态流转或数量对账通过。
- 执行命令：
  ```powershell
  # 列出规格所有测试用例
  npm run test:e2e:list
  # 运行黄金闭环端到端测试（单 worker 串行执行，避免会话被顶替）
  npm run test:e2e:golden
  ```
- 依赖门禁：收货、IoT、制造和成品库存事实缺失时，测试会输出 `GOLDEN_FLOW_BLOCKED_*` 并失败，不再引用已经过时的 `[BLOCKED_BY_BATCH_X]`。详见 `../docs/verification/stage-0-7-ui/README.md`。

### 3. 阶段 0–9返工 Task 0 认证门禁
- 2026-09-07 基线构建已通过；真实菜单/看板/派工/新鲜度断言尚未执行，因为当前 shell 没有 `STAGE_UI_PASSWORD` 或 `STAGE_UI_STATE_FILE`。
- 运行前必须在受保护环境注入认证上下文：
  ```powershell
  $env:STAGE_UI_TENANT_CODE = 'tenant_demo_a'
  $env:STAGE_UI_USERNAME = 'admin.zhang'
  $env:STAGE_UI_PASSWORD = '<只从本机安全环境注入>'
  npm run test:e2e
  ```
- 也可以只设置受保护的 `STAGE_UI_STATE_FILE`；不得把明文密码、JWT、storage state 或完整凭据写入仓库、命令历史或报告。没有认证上下文时，Task 0 保持未完成，不得把构建或健康检查写成业务验收通过。

2026-09-07 补充：本机演示账号已注入当前回归进程，Task 0 实际结果为 F01/F02 红灯、F03/F11 当前断言通过。F01/F02 的红灯是真实应用问题，不能用登录成功或构建成功覆盖；下一步按计划分别进入测试入口与 Dashboard/运行配置修复。

### 阶段 0–9 返工 Task 1 / Task 10 执行结果

- `npm run test:e2e:list` 解析 12 个测试用例并通过；`npm run test:e2e` 动态枚举 23 个菜单叶节点，F01/F02/F03/F11 当前断言通过。
- `npm run test:e2e:golden` 在真实 Chromium 上执行，7 条前置探测通过、5 条以明确事实/环境门禁失败；步骤 5/6 缺少服务端采购收货 ID，步骤 8 缺少项目 MQTT ACL/credential，步骤 9 缺少工序与质检事实 ID，步骤 10 缺少 FGR 库存事实，不能将该结果写成全链路通过。
- 黄金测试密码继续只从 `STAGE_UI_PASSWORD` 注入当前进程；源码不包含演示密码，测试清理覆盖业务弹窗遮罩，避免退出登录被遮挡而超时。

### 真实黄金事实验收（2026-09-08 继续执行）

- 新增 `e2e/golden-facts.spec.ts`：使用动态 `GOLDEN-<运行号>` 前缀，在真实 PostgreSQL/Gateway/Core 上按 `admin.zhang`、`sales.liu`、`buyer.chen`、`mes.inspector`、`wh.operator` 串行创建并复读主数据、销售、工单、采购、收货、质检、放行、上架、领料、工序、报工、生产质检、FGR、拣货和发货事实；所有实体 ID 只取接口响应，不写固定业务 UUID。
- `npm run test:e2e:golden` 当前只执行该真实事实套件；Chromium **1/1 通过**。数量对账为：到货 `3 = 拒收 1 + 实收 2`，原料领料后实物 `0`，成品入库 `2`，两次分批发货后销售行 `ordered/picked/shipped = 2/2/2`、订单 `Completed`、成品 `onHand/reserved/available = 0/0/0`。
- `e2e/golden-flow.spec.ts` 保留页面入口和历史环境探测；其中原先依赖未创建事实的步骤已明确标记为跳过，真实业务证据以 `golden-facts.spec.ts` 为准。真实 MQTT ACL/QoS1 仍不在该套件内伪造通过。
- 运行前仍需只在本机进程注入 `STAGE_UI_PASSWORD`；可选用 `STAGE_API_BASE_URL` 指向 Gateway（默认 `http://127.0.0.1:20001`）和 `STAGE_WORK_CENTER_ID` 仅在 Routing 目录没有可发现工作中心时提供。

### 最终矩阵补充（2026-09-08）

- `npm run test:unit -- --run`：3 个 Vitest 文件、13/13 通过；`npm run build`：305 modules 转换成功，退出码 0。
- `npm run test:e2e`：23 个动态菜单叶节点通过；F01/F02/F03/F11 均通过，其中 F02 七个 Dashboard 聚合接口 `responseCount=7`，F11 `errorCards=0/liveBadges=7/unavailable=0`。
- `npm run test:e2e:golden`：真实 Chromium `1/1` 通过；除主链数量对账外，还验证 UOM 同载荷幂等重放返回同一 ID、异载荷复用同一幂等键被拒绝、未关闭 Failed 质检阻止 FGR、库存不足阻止超量拣货。
- Dashboard 联调要求 Core/IoT 进程同时注入匹配的 S7 开关、Base URL 和 HMAC；这些值只存在于当前运行进程，未写入源码或文档。真实 MQTT 已在临时隔离 Broker 上验证账号/ACL、QoS1 重放、异载荷冲突和告警生命周期；外部 `1883` 生产 ACL、`production_area` Facts 与 Core 上下文补链仍未验证。
- 外部 `1883` 只读 `$SYS/broker/version` 探测返回 Mosquitto `2.1.2`，且匿名连接成功；其配置为 `D:\ruanjian\Mosquitto\mosquitto.conf` 默认配置，不能作为项目 ACL/设备 credential 通过证据，未向业务 topic 发布消息。

### 负向、恢复与详情导航专项（2026-09-08）

- `e2e/golden-flow.spec.ts`：`7 passed、5 expected skip`；修正错误/跨域文档下的退出清理，跳过门禁不再转成失败。
- `e2e/manufacturing.spec.ts`：真实 `mes.inspector` `4/4`；管理员无 MES 写权限时明确跳过。
- `e2e/detail-navigation.spec.ts`：`1 passed、1 expected skip`；采购、销售、调拨详情完成深链、刷新、后退和前进验证。
- `e2e/dashboard-states.spec.ts` + `e2e/insights.spec.ts`：`5/5`；覆盖空/缺失指标、503、stale/no-cache、模拟链路标识、告警确认与追溯断链。超时/网络结果不确定由 `src/composables/__tests__/useCommand.test.ts` 覆盖。
- 完整 `npx playwright test --project=chromium`：`23 passed、11 skipped、0 failed`；11 条均为已声明的事实/角色门禁，不是隐藏失败。
