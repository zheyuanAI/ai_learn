# 阶段 0–7 页面操作贯通：基线与第 1 批验证

本目录保存阶段 0 的可重复环境摘要、F01–F12 问题台账和真实页面回归入口。基线章节记录第 0 批事实；第 1 批结果记录在本文后半部分，不替代后续第 2–9 批的浏览器与业务事实验收。

## 当前批次

- 计划：`docs/superpowers/plans/2026-09-07-stage-0-7-ui-repair-plan.md`
- 执行批次：第 0 批基线已完成；当前为第 1 批（菜单、路由、详情直达与未知页面反馈）
- 基准日期：2026-09-07
- 工作树：`chore/local-development-baseline`，开始执行前已存在大量未提交前后端、规格和迁移改动；第 0 批只新增验证入口和台账，第 1 批继续保留并兼容这些改动。
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
| Mosquitto | `1883` | 当前监听在回环地址；真实 MQTT 验收仍未执行 |
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
