# PostgreSQL 12.1 Auth Flyway 历史表接管

项目服务共用同一个 PostgreSQL 实例和 `public` schema；Auth、Core、IoT 通过表前缀和各自的 Flyway 历史表隔离演进。本目录不创建额外 schema。

## 现有共享历史表接管

当前开发库是 PostgreSQL 12.1（`127.0.0.1:5433`），不应直接作为迁移测试目标。切换 Auth 的 `spring.flyway.table` 前，按以下顺序处理生产或开发共享库：

1. 停止 Auth 写入并备份数据库，确认 `public.flyway_schema_history` 中 Auth 的 V1-V4 都是成功记录，且没有同名失败记录。
2. 在同一个数据库、同一个 `public` schema 内执行 `auth-flyway-history-handoff.sql`。
3. 检查脚本创建的 `public.auth_flyway_schema_history` 只有 Auth V1-V4 四条成功记录，且 `installed_rank`、`version`、`description`、`type`、`script`、`checksum`、`installed_by`、`installed_on`、`execution_time`、`success` 与旧共享历史表一致。脚本不会删除、重命名或清空旧的 `flyway_schema_history`，Core/IoT 等其他模块记录也不会复制到 Auth 历史表。
4. 发布使用 `auth_flyway_schema_history` 且 `baseline-on-migrate=false` 的 Auth 配置，再执行 Auth Flyway `migrate`；此时只应执行 V5。
5. 验证 Auth 历史表有 V1-V5、旧共享历史表完全不变，并记录备份与校验结果。

如果共享历史表中存在无法确认归属的版本、失败记录、重复版本、目标历史表冲突或校验和不一致，应先人工停机核对，不得直接复制、重新编号、删除旧表或重放 V1-V4。

`auth-flyway-history-handoff.sql` 自身包含显式顶层 `BEGIN/COMMIT` 事务；源表检查、目标表创建、复制和结果校验都在同一事务中执行。任一步失败都会整体回滚，不留下半成品接管结果。

## 独立 PostgreSQL 12.1 验证

`platform-auth` 的 `AuthPostgresMigrationTest` 只使用调用方预先启动的独立 PostgreSQL 12.1 验证实例，不启动内嵌 PostgreSQL，不回退到 H2，也不自动连接 `127.0.0.1:5433/ai_learn`。测试每次只创建随机 `auth_migration_*` 临时数据库并执行三类检查：

- 全新库：使用 `auth_flyway_schema_history` 执行 V1 -> V2 -> V3 -> V4 -> V5；
- 接管库：先在共享 `flyway_schema_history` 执行 V1 -> V4，插入模拟 Core/IoT 成功记录，运行本目录接管脚本，再切换到 `auth_flyway_schema_history`，确认只执行 V5；
- 回滚保护：构造失败的 Auth 历史记录，确认 handoff 事务失败后不会留下半成品 `auth_flyway_schema_history`。

Maven 默认通过 `backend/platform-auth/pom.xml` 使用预先启动的 `127.0.0.1:55432/postgres` 独立 PostgreSQL 12.1 验证实例；该实例不会由测试自动启动，且命令行参数可以覆盖默认值。也可以显式传入独立验证实例连接参数，例如：

```powershell
mvn -pl platform-auth -Dauth.test.pg12.jdbc-url=jdbc:postgresql://127.0.0.1:55432/postgres -Dauth.test.pg12.username=postgres -Dauth.test.pg12.password=postgres -Dtest=AuthPostgresMigrationTest test
```

直接绕过 Maven 默认配置时，缺少 `auth.test.pg12.jdbc-url`、`auth.test.pg12.username` 或 `auth.test.pg12.password` 会明确失败。启动时会用 `SHOW server_version_num` 和 JDBC 元数据断言主版本为 12，并解析 JDBC URL 断言目标库名不是 `ai_learn`。
