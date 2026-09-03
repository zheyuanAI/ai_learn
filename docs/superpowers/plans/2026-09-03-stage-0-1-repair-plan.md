# 阶段 0-1 修复实施计划

> 状态：已废止，仅作为 2026-09-03 早期实施计划追溯记录；不代表当前方案，当前数据库基线与验收口径以 `docs/specs/00-project/架构设计.md`、`认证与后台管理验收标准.md` 为准。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. 本计划因用户要求直接在当前工作区执行，保持未提交 Git。

**Goal:** 在不改动 V1-V4 和当前开发数据库的前提下，修复 V5 菜单租户隔离、菜单管理 CRUD/状态、授权批量前置校验、Flyway 历史表接管、测试隔离和阶段 0 文档，并取得真实构建与 PostgreSQL 12.1 迁移证据。

**Architecture:** 继续使用单 PostgreSQL 实例的 `public` schema，以 `auth_` 表前缀和 `auth_flyway_schema_history` 独立历史表隔离 Auth 模块。V5 先拆除 V1 全局菜单索引，再回填真实默认租户 `a000...0001`、创建租户 B 菜单副本、补齐约束和租户级唯一索引；服务层在删除旧关联前通过批量 SQL 一次性验证全部目标对象。

**Tech Stack:** Java 21、Spring Boot 3.3.5、MyBatis-Plus、Flyway 10、PostgreSQL 12.1、H2（快速测试）、Vue 3、TypeScript、Vite 5、Node 20。

---

## 文件边界

- 修改 `backend/platform-auth/src/main/resources/db/migration/auth/V5__menu_tenant_isolation_and_visible_status.sql`：仅新增/修正 V5，不改 V1-V4。
- 修改 `backend/platform-auth/src/main/resources/application.yml`：Auth Flyway 历史表统一为 `auth_flyway_schema_history`。
- 新增 `docs/ops/auth-flyway-history-handoff.md` 和 `deploy/postgres/auth-flyway-history-handoff.sql`：说明并执行旧共享历史表的安全接管，不删除旧表。
- 修改 `backend/platform-auth/src/main/java/com/ailearn/platform/auth/mapper/MenuMapper.java`、`RoleMapper.java`、`UserMapper.java`：批量校验 SQL 与租户过滤。
- 修改 `backend/platform-auth/src/main/java/com/ailearn/platform/auth/service/admin/impl/MenuAdminServiceImpl.java`、`RoleAdminServiceImpl.java`、`UserAdminServiceImpl.java`：菜单状态/编码回读、授权全量前置校验和事务顺序。
- 修改 `backend/platform-auth/src/main/java/com/ailearn/platform/auth/domain/dto/admin/MenuUpdateRequest.java`、`MenuStatusUpdateRequest.java`、`MenuCreateRequest.java`、`Menu.java`、`MenuAdminNodeVo.java`、`MenuNodeVo.java`：明确 `menuCode`、`visible`、`status` 字段。
- 修改 `backend/platform-auth/src/main/java/com/ailearn/platform/auth/controller/admin/MenuAdminController.java`：让 `/status` 接收 `status` 语义并同步 OpenAPI 描述。
- 修改 `backend/platform-auth/src/main/resources/mapper/MenuMapper.xml`、相关 XML：返回和过滤 `status`，补批量查询。
- 修改 `frontend/src/api/admin.ts`、`frontend/src/api/auth.ts`、`frontend/src/views/system/MenuList.vue`：同步菜单编码、显隐、启停和 status 展示/编辑。
- 修改 `backend/platform-auth/src/test/resources/schema-h2.sql`、`data-h2.sql`、`application.yml`：真实默认租户 ID、约束、关系数据和内存会话缓存隔离。
- 修改/新增 `backend/platform-auth/src/test/java/com/ailearn/platform/auth/*`：真实 PostgreSQL SQL 迁移测试、菜单 CRUD/状态、跨租户/停用对象整体拒绝。
- 同步 `README.md`、`backend/README.md`、`frontend/README.md`、`docs/specs/00-project/架构设计.md`、`docs/specs/00-project/正式项目计划.md`、对应 Auth 领域 `接口契约.md`/`验收标准.md`，只修正文案漂移和本轮事实。

## Task 1: 修正 V5 与历史表接管入口

- [ ] 先保存当前工作区状态，确认只读 `git diff --name-only`，禁止运行任何指向 `127.0.0.1:5433/ai_learn` 的迁移命令。
- [ ] 重写 V5 顺序：第一条有效 DDL 为 `DROP INDEX IF EXISTS uq_auth_menu_code_active`；之后增加字段、回填 `a0000000-0000-0000-0000-000000000001`、补租户 B `a0000000-0000-0000-0000-000000000002`、插入 B 的菜单/角色/关联、设置 `NOT NULL`/默认值/合法值约束、创建 `(tenant_id, menu_code) WHERE isdel = 0` 索引。
- [ ] 使所有 V5 幂等插入使用 `ON CONFLICT (id)` 的明确更新或不改变已有正确记录，避免用 `10000000-...` 作为任何租户事实 ID。
- [ ] 将历史菜单、V4 新增系统管理菜单和现有 V2 角色菜单关系都绑定到 `a000...0001`；若历史数据存在旧的租户 B 记录，迁移只保留同一 B 租户内的角色/菜单/关联。
- [ ] 将 `spring.flyway.table` 改为 `auth_flyway_schema_history`，保留 `locations: classpath:db/migration/auth`。
- [ ] 编写接管 SQL，要求源表存在、Auth V1-V4 行全部成功且版本唯一，然后用 `CREATE TABLE ... (LIKE ... INCLUDING ALL)`/列名显式插入复制记录，最后比较版本集与行数；脚本不删除旧表、不运行迁移。
- [ ] 编写接管文档，明确旧共享表复制应在应用停机窗口/同一数据库事务完成，复制成功后再切换配置并只执行 V5；说明共享表含其他模块记录时的人工核对条件。

## Task 2: 菜单 CRUD、状态和回读

- [ ] 增加 `MenuUpdateRequest.menuCode`，校验非空/长度；`MenuStatusUpdateRequest` 改为 `status`，只接受 `ACTIVE`/`DISABLED`。
- [ ] 在 `MenuAdminServiceImpl.updateMenu` 中按当前租户检查编码唯一性并 `setMenuCode`，保留 `visible` 在普通更新中的独立更新；状态接口只写 `status`。
- [ ] 在 `MenuAdminNodeVo`、`MenuNodeVo` 和转换/查询 SQL 中返回 `status`，管理树、详情和用户菜单均可回读。
- [ ] 逻辑删除继续写 `isdel = 1`，详情/树/角色菜单查询继续过滤 `isdel = 0`，不引入物理删除。
- [ ] 前端 API、`MenuItem`、菜单管理表格和表单分别展示 status 与 visible，并让 `/status` 请求体发送 `{status}`；普通更新发送 `{menuCode, visible, ...}`。

## Task 3: 授权全量前置校验

- [ ] 在 `RoleMapper` 增加按租户、ID 集合、`isdel = 0`、`status = ACTIVE` 查询角色的方法；在 `MenuMapper` 增加按租户、ID 集合、`isdel = 0`、`status = ACTIVE` 查询菜单的方法。
- [ ] 在 `UserAdminServiceImpl` 中对用户分配角色先去重并批量读取全部角色，比较集合数量；任一 null/跨租户/停用/逻辑删除/不存在都抛出 `ValidationException`，之后才删除旧 `auth_user_role` 并插入新关系。
- [ ] 在 `RoleAdminServiceImpl` 中对角色分配菜单做同样的批量查询和集合完整性比较；创建/更新角色的初始菜单关系也复用该校验。
- [ ] 保证校验失败不会触发旧关系删除；用真实租户 B 的角色/菜单 UUID 覆盖跨租户失败，并加入停用角色、停用菜单失败用例。
- [ ] 对分配成功后的用户/角色缓存继续执行失效；不扩展本轮安全加固或平台管理员能力。

## Task 4: 测试环境与真实迁移测试

- [ ] H2 测试数据统一使用 `a0000000-0000-0000-0000-000000000001` 与 `a0000000-0000-0000-0000-000000000002`，保证每个租户的角色、菜单、用户和关联记录租户一致；为 H2 schema 加上菜单 `tenant_id NOT NULL`、visible/status 默认和合法值约束。
- [ ] 测试 profile 显式选择 `InMemorySessionCacheServiceImpl` 或排除 Redis 自动配置，验证测试 JVM 不读取开发 Redis database 0 的 JTI。
- [ ] 保留 V2/V3 静态校验作为辅助，但新增真实 SQL 测试：快速回归继续使用 H2；真实迁移测试在隔离 PostgreSQL 12.1 库中安装 V1-V5，检查历史菜单默认租户、租户 B 同编码、索引/约束和 Flyway 历史表；在只安装 V1-V4 且历史表为旧表的隔离库中执行接管 SQL 后启动新 Flyway，仅执行 V5。
- [ ] 扩展管理集成测试覆盖：菜单编码修改后 GET 回读、visible/status 独立变化、跨租户 ID 与停用对象整体拒绝且旧关联不变、管理员升级后仍能查询菜单。

## Task 5: 文档同步

- [ ] 修正 `frontend/README.md` 中后台路径为实际 `/system/*`，并把 API `/api/auth/admin/**` 与菜单 status 语义写清。
- [ ] 在 Auth 接口契约、验收标准、架构文档、根 README 和 backend README 中统一：`a000...0001` 默认租户、`auth_flyway_schema_history`、同 `public` schema、菜单 `visible/status` 分离、V5 两条迁移路径、授权全量前置校验。
- [ ] 不把 PostgreSQL 共用 `public` schema 描述为问题，不宣称阶段 0-1 验收已完成。

## Task 6: 验证与交付

- [ ] 找到并显式使用 Java 21、Maven 3.9.1、Node 20；先运行 `mvn clean test`，保存完整摘要。
- [ ] 运行 `mvn clean package` 和 `npm run build`，失败则按系统调试流程定位并重新验证。
- [ ] 用 PostgreSQL 12.1 隔离环境执行并记录全新库 V1→V5、V1-V4→V5 两条结果；禁止连接当前开发 PostgreSQL `127.0.0.1:5433/ai_learn`。
- [ ] 连续两次运行后端测试并记录两次成功结果；运行 `git diff --check`。
- [ ] 最终检查 `git status --short` 和 `git diff --name-only`：确认没有提交 Git、V1-V4 文件内容未改、当前开发数据库未被操作；任意缺证据都报告“未完成”。
