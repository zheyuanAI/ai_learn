# Backend

## 模块
- `platform-gateway`：统一入口与后续网关能力
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

## 当前状态
- **父工程与骨架**：已创建多模块父工程与各服务启动类，各服务均提供 `/internal/ping` 探活接口。
- **platform-auth（阶段1基础能力与本轮修复中，阶段验收未完成）**：
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
- **platform-core / platform-iot / platform-gateway**：当前保持基础服务骨架，业务领域代码将在后续阶段推进。

## 规格入口
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
- 租户隔离规则：所有核心业务表与认证表均包含 `tenant_id`，查询与写入严格按当前租户隔离。
