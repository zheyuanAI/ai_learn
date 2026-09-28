# 项目检查与并发修复记录（2026-09-26～27）

本文保留第一阶段检查时的结果与未完成项。后续库存整笔锁序、IoT 设备锁、页面快照、完整数据库回归及六岗位流程的最新结果，见 [非 AI 并发修复与六岗位验收记录](2026-09-27-non-ai-concurrency-and-role-flow.md)，不要将下文的历史待办或旧测试数量当作最终状态。

本轮按用户要求检查现有功能与并发问题；必要检查限定在可信租户、幂等与数据一致性，不扩展安全架构。实际工程为 `ai_learn_developProject`，检查时已有的页面、路由、配置和文档改动均保留。未执行 Git 提交。

## 已修复的问题

| 问题 | 触发与影响 | 本轮修复 |
| --- | --- | --- |
| 内存幂等过期清理误删新占用 | A 读取过期记录，B 替换为新 claim，A 无条件删除同键，导致 B 丢失占用 | `InMemoryIdempotencyStorage.getRecord` 在原子计算中校验对象身份后清理；不能用按租户和键比较的 `equals` 判断新旧记录 |
| 库存幂等仍走旧兼容入口 | 旧请求回滚清理或提交完成可能影响同键的新请求；失去所有权后仍返回成功 | `InventoryApplicationService` 复用既有 `CoreIdempotencyExecutor`，使用 claim token 完成与清理；保留操作域、摘要、24 小时窗口、错误码和事务 |
| 采购、销售详情查询响应乱序 | 切到订单 B 或刷新同一订单后，A 的迟到成功或失败覆盖最新页面；销售旧库位响应也会覆盖当前库位 | 捕获订单 ID 与请求世代，只有当前有效请求能写入详情、库位、错误和加载状态 |
| 同键重试读取变化后的表单 | 首次请求结果不确定后，修改数量、原因或订单，再重试会提交不同载荷 | 两个详情页面在首次执行前快照订单和完整请求载荷；重试沿用原载荷与原键 |
| 销售拣货余额响应乱序 | 连续打开物料 A、B 的拣货窗，A 的迟到余额覆盖 B，旧 `finally` 提前结束新查询 | 独立余额请求世代，加上订单、选中行、弹窗可见性检查；关闭或切单后的响应失效 |
| IoT 采集事务同步等待 Core 补链 | 全局遥测锁与本地事务期间等待远程请求；嵌套补链失败可能使事务被标记回滚 | 新告警只调用本地 `enqueue`，告警和 `Pending` 任务同事务保存；现有调度器查询 Core。本地入队失败明确传播，保留回滚与消息重投 |

IoT 的立即补链、人工补链和指数退避实现保留；未新增 HTTP 路由、DTO、数据库表、迁移或中间件。自动补链会在既有后台调度后完成，默认调度间隔为 5000ms，前端可暂时看到 `Pending`。

修改入口：

- 库存：`backend/platform-core/src/main/java/com/ailearn/platform/core/inventory/application/InventoryApplicationService.java`。
- 共享幂等：`backend/platform-shared/src/main/java/com/ailearn/platform/shared/idempotency/InMemoryIdempotencyStorage.java`。
- IoT：`AlarmApplicationServiceImpl`、`AlarmContextLinkApplicationService` 与实现。
- 页面：`frontend/src/views/purchasing/PurchaseOrderDetailView.vue`、`frontend/src/views/sales/SalesOrderDetailView.vue`。
- 回归：`InventoryIdempotencyOwnershipTest`、`ContextHolderAndIdempotencyTest`、两个 IoT 应用服务测试、`frontend/src/views/__tests__/orderDetailRequests.test.ts`。

## 验证与证据边界

- 修复前：内存过期清理、库存所有权、详情响应乱序、拣货余额响应乱序及 IoT 同步补链均已复现失败；修复后相应用例通过。
- 前端：`npm run test:unit -- --run`，6 文件、45 项通过，含新增 24 项真实 SFC 脚本行为回归；`npm run build` 通过类型检查与生产打包。
- 后端：指定项目 JDK 21、Maven 3.9.1，聚合 `package` 成功，Shared/Gateway/Auth/Core/IoT 共 376 项测试通过，失败与错误均为 0。该命令明确排除了 `AuthPostgresMigrationTest`。
- `git diff --check` 对本轮修改通过。已执行跨代理只读复核。

默认未排除测试的 `mvn package` 首次失败：Auth 真实迁移测试连接隔离实例 `127.0.0.1:55432` 被拒绝；未修改迁移测试以隐藏失败，也未连接开发库执行迁移。Core 真实迁移测试未提供隔离实例参数，测试容器未执行用例。以上真实 PostgreSQL 迁移均不能算验收通过。

本轮没有启动业务服务或真实 Broker，没有执行浏览器端到端业务验收、真实数据库并发压测或容量测试。IoT 同事务持久化的结论由代码和单元回归支持，尚未在真实 PostgreSQL 上故障注入验证；45 项前端用例为脚本行为回归，不代表浏览器界面验收。仓库内未发现本次指定的目标 QPS、并发用户数或设备上报速率依据，不能宣称已支持某一并发规模。

复现构建命令（PowerShell，项目 `backend` 目录）：

```powershell
$env:JAVA_HOME = 'D:/AI/ai_learn_wms_ai/ai_learn_developProject/runtime/jdk'
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
& 'D:/ruanjian/apache-maven-3.9.1/bin/mvn.cmd' -o `
  '-Dmaven.repo.local=D:/project/MavenRepository391' `
  '-Dtest=*,!AuthPostgresMigrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' package
```

本机过程日志：`output/project-health-baseline-maven.log`、`output/project-health-maven-2026-09-27.log`。日志不作为需要提交的源码。

## 下一批并发工作

以下为已定位但本轮尚未修复或验证的项，不属于本轮通过结论：

1. **外层事务统一库存锁序**：直接拣货会先预留源余额，再移动源/目标；另一订单退回时可能先锁暂存位后等源余额。若两者顺序相反，会形成循环等待。多行操作也需要在整个事务内统一维度锁序。下一批先补确定性锁交错与隔离 PostgreSQL 回归，再通过库存应用端口预锁完整维度集合，避免只排序单次 `move`。这是静态风险，尚未以真实数据库复现。PostgreSQL 建议涉及同一组对象的事务使用一致锁序。[PostgreSQL 12 锁与死锁说明](https://www.postgresql.org/docs/12/explicit-locking.html#LOCKING-DEADLOCKS)
2. **IoT 设备粒度并行**：`TelemetryIngestionServiceImpl.ingest` 仍为整个实例的 `synchronized`。本轮已移出 Core 网络等待；下一批应在数据库去重、状态条件更新与告警唯一约束的回归支持下评估缩小锁粒度，并测量多设备吞吐。后台补链仍按现有批次处理，远程延迟会影响任务积压。
3. **其余页面重试快照**：采购列表、生产质检面板等调用 `useCommand` 的闭包仍有读取实时表单的情况。需逐入口冻结载荷并补行为回归；本轮只覆盖采购、销售详情的命令。
4. **容量与准入**：Core/IoT 当前连接池各为 10；AI 流式入口使用虚拟线程且没有已确认的并发准入上限。先在隔离环境逐级测试正常读请求、热点库存写、跨设备遥测和长连接，记录吞吐、p95/p99、错误率、锁等待、连接池等待与任务积压，再确定连接池、超时和准入值。

建议执行顺序为库存锁序 → 多设备遥测并行 → 剩余页面命令快照 → 容量压测与准入；复用一期 PostgreSQL/Redis/Mosquitto，不为这一批引入二期消息中间件。

内存测试实现另有既有边界：事务 `beforeCommit` 将内存幂等记录写为成功后，真正提交失败不能按 PENDING token 清理 SUCCESS。当前生产注入 PostgreSQL 幂等存储，与业务事实同事务回滚；本轮未改内存测试回退的事务模型。
