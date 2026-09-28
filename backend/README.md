# Backend

## 模块
- `platform-gateway`：统一入口、JWT 验签与服务路由
- `platform-auth`：认证、租户、用户、角色、菜单
- `platform-core`：采购、销售、库存、制造、质量、追溯、统计与 AI 只读业务查询
- `platform-iot`：设备、MQTT 遥测、状态与告警事实
- `platform-shared`：公共类型、异常、基础配置

## 本地服务端口

端口基线以 `../docs/specs/00-project/架构设计.md` 为准，下表提供后端模块的便捷索引。

| 模块 | 端口 |
| --- | ---: |
| `platform-gateway` | 20001 |
| `platform-auth` | 10002 |
| `platform-core` | 10003 |
| `platform-iot` | 10004 |

Core / IoT 的 `dev` profile 与 S7 Facts 联调必须通过运行环境注入配对 HMAC，仓库不保存实际密钥；IDEA 共享运行配置统一加载被 Git 忽略的 `../deploy/local/runtime.env`，不再依赖 Open WebUI 配置。完整变量清单和手工启动命令见 `../runtime/README.md`。Gateway 的本地统一入口保持为 20001。

阶段 8 AI 已实现 Open WebUI Agent Provider、SSE 会话、唯一 YAML API 白名单、Gateway 裁剪 OpenAPI、用户会话委托和首批 36 个现有业务查询 operation，默认关闭。Windows Open WebUI 使用硅基流动 `deepseek-ai/DeepSeek-V4-Flash`，并绑定受控知识和 Gateway 发布的 WMS 查询目录；早期直连 Provider 只保留为诊断回退代码，同一次请求不得执行两套 Agent 循环。所有 Open WebUI、硅基流动密钥和服务签名只从运行环境读取，不得写入仓库。

## 当前状态
- **父工程与健康检查**：已创建多模块父工程与各服务启动类，各服务均提供 `/internal/ping` 探活接口。
- **platform-auth（认证与后台管理基础能力）**：
  - **数据库与 Flyway**：接入 PostgreSQL `public` schema，统一使用 `auth_` 表前缀与 `auth_flyway_schema_history` 独立版本表。Flyway V5 负责在同一 `public` schema 内完成菜单租户隔离、`visible`/`status` 字段和租户级编码唯一约束；V6 补齐阶段 2-7 使用的权限目录；V1-V4 已执行数据库须先按 `deploy/postgres/auth-flyway-history-handoff.sql` 安全接管历史表，当前开发数据库不得直接执行 V5/V6。
  - **基础认证与会话**：提供登录 (`POST /api/auth/login`)、登出 (`POST /api/auth/logout`)、个人画像 (`GET /api/me`)、个人菜单树 (`GET /api/me/menus`)，支持 JWT 签发与 Redis 单有效会话管理。
  - **后台管理核心接口（统一 `/api/auth/admin/**` 前缀）**：
    - **租户设置**：统一为 `GET/PUT /api/auth/admin/tenants/current`（查询与修改当前租户信息）
    - **用户管理**：`GET /api/auth/admin/users`（分页查询，入参统一为 `page`, `size`, `roleId`）、`POST /api/auth/admin/users`（新增用户）、`PUT /api/auth/admin/users/{id}`（修改用户/分配角色）、`DELETE /api/auth/admin/users/{id}`（删除用户，统一逻辑软删除 `isdel = 1`）、`POST /api/auth/admin/users/{id}/reset-password`（重置密码，入参字段统一为 `newPassword`）
    - **角色与授权管理**：`GET /api/auth/admin/roles`（角色列表）、`POST /api/auth/admin/roles`（新增角色）、`PUT /api/auth/admin/roles/{id}`（修改角色）、`PUT /api/auth/admin/roles/{id}/status`（启停角色）、`DELETE /api/auth/admin/roles/{id}`（逻辑软删除角色）、`GET /api/auth/admin/roles/{id}/permissions`（获取角色权限）、`PUT /api/auth/admin/roles/{id}/permissions`（分配权限点）、`GET /api/auth/admin/roles/{id}/menus`（获取角色菜单）、`PUT /api/auth/admin/roles/{id}/menus`（分配菜单）
    - **权限只读目录**：`GET /api/auth/admin/permissions`（获取系统预置权限列表/树，采用冒号分段规范如 `auth:user:view`，目录只读由角色负责授权）
    - **菜单管理**：`GET /api/auth/admin/menus`（完整菜单树）、`GET /api/auth/admin/menus/{id}`（详情）、`POST /api/auth/admin/menus`（创建菜单）、`PUT /api/auth/admin/menus/{id}`（更新 `menuCode`/名称/层级/`visible` 等）、`PUT /api/auth/admin/menus/{id}/status`（更新 `status=ACTIVE|DISABLED`）、`DELETE /api/auth/admin/menus/{id}`（逻辑软删除菜单）
  - **核心安全与数据约束**：
    - 一期无平台超级管理员，租户管理员仅管理当前租户数据（严格基于 `tenant_id`）；
    - **授权分配全量 ID 前置校验**：用户分配角色、角色分配权限点与菜单时，后端严格执行全量目标 ID 的存在性、当前租户、`isdel = 0` 与 `status = ACTIVE` 校验，遇非法/跨租户/已删除/停用 ID 在删除旧关联前整体拒绝；
    - **菜单字段语义**：`visible` 只控制导航展示，`status` 只控制菜单是否启用；二者分别持久化并可回读。
    - **统一逻辑软删除**：关联数据及核心实体统一采用逻辑软删除（`isdel = 1`），禁止物理 `DELETE`；
    - 实现防自删保护、最后管理员保护与 409 冲突拦截。
- **platform-core / platform-iot / platform-gateway**：Core 已提供采购、销售、库存、制造、质量、追溯与看板业务接口；IoT 已提供设备、凭证、MQTT 遥测、状态、告警及上下文补链；Gateway 已接入统一路由、JWT 与会话校验。各域验收范围以对应 `docs/specs/` 和最新验证记录为准。
- **platform-core / platform-gateway 阶段 8 AI 纵向切片**：Core 提供会话、消息、SSE 和短期用户委托上下文；Gateway 读取 `deploy/openwebui/wms-ai-api-whitelist.yml`，从 Core/IoT OpenAPI 生成裁剪工具目录并恢复原用户可信身份；下游现有 `@PreAuthorize`、租户和数据范围继续负责最终授权。低库存和操作时间线已补为普通业务查询入口，不再要求新增 AI 专用 DTO。
- **Open WebUI 演进状态**：旧 `wms` Tool Server 和十个 AI 专用接口暂作迁移兜底；Gateway、Core、IoT 重启后运行 `configure-windows.ps1` 会按唯一清单创建 `wms_core`、`wms_iot` 并绑定 `wms-assistant`。新链路白名单解析、越界拒绝、失效会话拒绝和身份恢复测试已通过，多角色真实 SSE 回归与旧接口删除仍待收口。

## 规格入口
非 AI 并发、真实 PostgreSQL 回归和清库后的六岗位流程见 [2026-09-27 验收记录](../docs/verification/2026-09-27-non-ai-concurrency-and-role-flow.md)；采购行累计、MQTT 提交后确认、隔离并发写入与持续读取的后续结果见 [2026-09-28 验收记录](../docs/verification/2026-09-28-non-ai-followup.md)。`scripts/start-role-flow.ps1` 启动最新构建并调用真实角色流程，脚本本身不清库；`scripts/read-load.mjs` 仅执行 GET，通过 `BENCHMARK_TOKEN` 注入角色令牌。

在已启动的独立 PostgreSQL 空库、Redis 和未占用的本机服务端口上复演岗位流程，可从项目根目录运行以下命令（先完成 `mvn package`，并准备脚本要求的本机 JDK、Node 20 与 Mosquitto 运行包）：

```powershell
pwsh -NoProfile -File backend/scripts/start-role-flow.ps1 -DatabasePort 55432 -DatabaseName wmscap_demo -RedisPort 16379 -RedisDatabase 15 -ServicePortOffset 1000 -SkipFrontend
```

脚本默认生成一个本轮 UUID，供新建 Routing 工序与设备共同引用，不依赖被忽略的 `output/role-flow-retained-facts.json`，也不宣称存在独立工作中心主表。若复演已有业务环境且已核实工作中心 ID，可显式追加 `-WorkCenterId <已核实的 UUID>`。`-RedisPort` 应指向预先启动、仅供本次复演的 Redis；本机可将 `deploy/local/redis.conf` 复制到被忽略的 `output/`，仅在副本中改为 `port 16379`、把 `dir` 指向已存在的隔离目录后启动 `runtime/redis-3.0.504/redis-server.exe`，保持 `bind 127.0.0.1`，不改现有 6379 服务。脚本只检查端口可达，不能代替 Redis 认证健康检查。岗位账号来自 Auth 预置演示租户，Broker 使用外部密码/ACL；进程、生成的工作中心软引用及结果写入本轮 `output/role-flow-runtime-*`。脚本不会自动停止本轮服务，复演前须确保其端口空闲。

- 当前开发计划与一期范围：`../docs/specs/00-project/正式项目计划.md`
- 服务边界与横切约束：`../docs/specs/00-project/架构设计.md`
- 领域规则、接口与验收：对应领域目录的 `概述.md`、`领域模型.md`、`接口契约.md`、`验收标准.md`

## 一期边界与数据库规范
- 一期依赖与中间件范围以 `../docs/specs/00-project/架构设计.md` 为准；当前开发实例使用 PostgreSQL 12.1（`127.0.0.1:5433/ai_learn`，SQL/Flyway 兼容性以下限 12.1 为准）、Redis（端口 6379）、Mosquitto（端口 1883）；RabbitMQ、MinIO、pgvector 属于二期候选。
- **数据库模块划分机制**：所有服务共用同一个 PostgreSQL 实例及 `public` schema，通过表前缀（`auth_` 认证权限、`md_` 主数据、`inv_` 仓储库存、`iot_` 物联网设备）和独立 Flyway 历史表（如 `auth_flyway_schema_history`、`core_flyway_schema_history`、`iot_flyway_schema_history`）划分模块与独立演进。
- **Flyway V5/V6 与权限数据维护**：V5 使 `auth_menu` 表包含 `tenant_id`、`visible`、`status`，各租户菜单物理隔离维护；V6 以幂等方式补齐阶段 2-7 使用的权限目录。
- **Flyway 历史表接管**：旧共享 `public.flyway_schema_history` 只作为 V1-V4 的迁移历史来源，接管脚本复制 Auth 记录到 `public.auth_flyway_schema_history`，不删除旧表、不创建新 schema、不在接管脚本中执行 V5/V6。
- **统一逻辑软删除**：全库实体统一采用 `isdel = 1` 逻辑删除标记，禁止物理 `DELETE`。
- `platform-core` 物理上保持单服务，内部按采购、销售、库存、制造、质量、追溯、看板和 AI 逻辑模块组织，跨模块调用必须通过应用服务，严禁直接读写其他模块底层表。
- 综合看板七个查询入口始终注册并按摘要隔离事实依赖；IoT Facts 未启用时只降级设备、告警和依赖完整追溯的摘要，不连带关闭库存、履约、制造和质量接口。
- 租户隔离规则：所有核心业务表与认证表均包含 `tenant_id`，查询与写入严格按当前租户隔离。

## 并发修复与验证（2026-09-27—28）

库存写入已复用带 claim token 的幂等执行器并统一业务锁序；IoT 新告警只在本地事务内登记补链任务，由现有后台调度器调用 Core，MQTT 消费改为有界并行且提交后确认。完整构建、隔离写入和读取结果见 [后续验收记录](../docs/verification/2026-09-28-non-ai-followup.md)；目标部署容量与 Broker 行为仍须按实际环境验收。
