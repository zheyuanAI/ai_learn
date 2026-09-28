# 非 AI 后续并发与隔离验收

日期：2026-09-28。范围为采购订单行累计事实、MQTT 消费边界、隔离库写入和持续读取观测。按用户要求未处理 AI 功能；开发库 `127.0.0.1:5433/ai_learn` 未用于本轮写入。

## 已落地的改动

- 采购详情按真实持久化关系追踪 `purchase_order_line_id`，聚合收货、拒收、质检合格、放行和上架数量；查询同时限制租户与 `isdel=0`，不按 SKU 猜测多行归属。新增 V11 部分索引，详情事务使用 `REPEATABLE_READ`。接口行视图新增 `arrivedQty`、`rejectedQty`、`qualifiedQty`、`releaseExecutedQty`、`putawayQty`，缺失值仍保持 `null`。
- MQTT 使用 Paho 手动确认。消息只有在事务消费成功返回后才 ACK；临时服务不可用会重投，坏消息单独确认避免毒丸阻塞；待处理队列有界（64），4 个工作线程按租户和设备保持 FIFO，不同设备允许并行。停止时唤醒背压回调，不确认未提交消息。
- 写入基线只接受 `127.0.0.1` 的非默认 Gateway、非 `5433` PostgreSQL 和独立数据库名；PG 观测连接强制只读。fixture 通过正式 DTO/API 建立，使用固定的现实化样本档案轮换客户、供应商、联系人、地址、数量和日期偏移，编号带本轮 `runId`，每条命令只发送一次幂等键。
- 角色流程脚本的临时 Mosquitto 以脱离 Node 父进程的方式启动，避免验收进程退出时误杀后续观测仍需使用的 Broker。

## 自动化验证

- 完整后端 `mvn package`：Shared 19、Gateway 20、Auth 43、Core 244、IoT 94，共 **420 项测试，零失败、零错误、零跳过**。日志：[non-ai-followup-package.log](../../output/non-ai-followup-package.log)。
- 采购累计专项 14 项通过；修正 Core 迁移断言后 Core 定向回归 15 项通过。MQTT listener/dispatcher/parser 定向回归 14 项通过。前端单测 **181 项通过**，`npm run build` 成功；Node 20 基线脚本语法检查和 3 项脚本测试通过。

## 隔离角色流程

本轮隔离环境为 PostgreSQL 12.1 `127.0.0.1:55432/wmscap_20260928_nkr1ifd2`、Redis DB 15，服务端口为 Auth 11002、Core 11003、IoT 11004、Gateway 21001。报告：[role-flow-result.json](../../output/role-flow-runtime-20260928-104843-21f5bdae/role-flow-result.json)，进程证据：[processes.json](../../output/role-flow-runtime-20260928-104843-21f5bdae/processes.json)。

六个岗位账号按管理员、销售、生产质检、采购、仓库、物联的顺序调用正式接口，流程状态 `PASSED`。采购详情累计值已从订单行关系核对；真实 MQTT QoS 1 发布、订阅、告警和恢复仍由本轮角色流程完成。角色流程结束后发现临时 Broker 子进程在 Windows 作业树中退出，IoT 自动重连；已恢复隔离 Broker，并将后续脚本改为 detached 启动。该现象没有造成业务写入或读取基线失败。

结束核对时发现开发库残留上一轮 `XS/CG/SC-260927-FAAA` 三条有效单据，先生成 [ai_learn-before-followup-clean-20260928.backup](../../output/ai_learn-before-followup-clean-20260928.backup)，再按 [role-flow-clean.sql](../../output/role-flow-clean.sql) 执行既有清理维护。带 `isdel` 的业务实体执行逻辑软删除，无 `isdel` 的追加事实按这份已授权维护脚本重置；没有对业务实体执行物理 `DELETE`。复核结果为销售、采购、工单和库存有效记录均为 `0`，认证有效账号保留 `6`。复核输出：[role-flow-clean-followup-20260928.verify.log](../../output/role-flow-clean-followup-20260928.verify.log)。

## 隔离写入结果

### 热点库存

报告：[bench-hotspot-report.json](../../output/bench-hotspot-report.json)。8 条不同工单同时确认同一原料、仓库和 Storage 库位：

| 指标 | 结果 |
| --- | ---: |
| 成功/请求 | 8/8 |
| 并发 | 8 |
| 成功吞吐 | 60.28 req/s |
| P95 / P99 | 126.6 / 126.6 ms |
| 期初/期末库存 | 34 / 24 |
| 余额不变量 | 通过 |
| 锁等待正样本 / 死锁增量 | 0 / 0 |

### 跨岗位混合写入

报告：[bench-multirole-report.json](../../output/bench-multirole-report.json)。仓库 4 条领料确认、销售 4 条提交、采购 4 条提交交错执行：

| 指标 | 结果 |
| --- | ---: |
| 成功/请求 | 12/12 |
| 并发 | 6 |
| 成功吞吐 | 110.05 req/s |
| P95 / P99 | 79.36 / 79.36 ms |
| 已提交事实 | 12 |
| 状态不一致 / 死锁增量 | 0 / 0 |

## 只读并发与持续观测

目标均为网关 `GET /api/inventory/balances?page=1&size=20`，使用仓库角色令牌，所有响应业务成功。

| 并发 | 请求数 | 成功率 | 吞吐 | P95 | P99 |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | 2,000 | 100% | 169.25 req/s | 8.72 ms | 10.90 ms |
| 5 | 2,000 | 100% | 404.65 req/s | 21.57 ms | 32.81 ms |
| 10 | 2,000 | 100% | 483.92 req/s | 39.31 ms | 57.44 ms |
| 20 | 2,000 | 100% | 625.18 req/s | 60.44 ms | 89.54 ms |
| 50 | 1,000 | 100% | 586.62 req/s | 118.00 ms | 129.02 ms |
| 100 | 1,000 | 100% | 970.71 req/s | 141.03 ms | 192.96 ms |
| 100（连续批次） | 30,000 | 100% | 1,265.43 req/s | 114.83 ms | 162.67 ms |

原始结果：[followup-isolated-read-load.json](../../output/followup-isolated-read-load.json)、[followup-isolated-read-load-high.json](../../output/followup-isolated-read-load-high.json)、[followup-isolated-read-load-steady.json](../../output/followup-isolated-read-load-steady.json)。

## 边界与后续

这些数据来自单机、单实例服务和有限业务样本，只说明本轮代码在隔离环境下的行为，不构成生产容量承诺；锁等待采样只有短批次有效样本，不能替代生产监控。若需要生产容量目标，应在与生产版本一致的多实例环境中继续做连接池、数据库、Broker 和 JVM 指标压测。

所有改动和验证证据均留在工作区，未执行 Git 提交。

## 封板前复核（2026-09-28）

- 修正采购详情的 `manualComplete` 动作名及 Element Plus 租户下拉框的 E2E 登录操作；前端 181 项单测与生产构建通过。完整 Chromium 在新隔离库执行 `e2e/golden-facts.spec.ts`，**1/1 通过**，日志为 `output/freeze-golden-e2e-headed.log`。本机 headless shell 因 ICU 文件句柄错误无法启动，首次失败发生在业务用例执行前，见 `output/freeze-golden-e2e.log`。
- 最后管理员保护的并发缺口已复现：两个管理员可同时撤销对方。修复后四条并发撤权路径仅一条成功，租户始终保留一名活动管理员；完整后端 `mvn package` 为 Shared 19、Gateway 20、Auth 47、Core 244、IoT 94，合计 **424 项，零失败、零错误、零跳过**，包含真实 PostgreSQL 12.1 迁移与并发测试，日志为 `output/freeze-final-package.log`。
- `start-role-flow.ps1` 不再依赖被 Git 忽略的保留事实文件，默认生成本轮工作中心软引用，并支持显式隔离 Redis 端口。首次空库启动发现 6379 属于需要认证的外部 Redis，Auth 健康检查未通过，未进入业务流程；改用项目运行包中的 Redis 3.0.504 `127.0.0.1:16379` 与全新空库 `127.0.0.1:55432/wmsfreeze_20260928_rerun1` 后，六岗位正式接口和真实 MQTT 流程 **PASSED**，报告为 `output/role-flow-runtime-20260928-181325-5ff12da0/role-flow-result.json`。开发库未用于本次写入；目标部署 Redis 7 仍须独立验证。
- 只读复核发现开发库 `127.0.0.1:5433/ai_learn` 在前次清零之后，于 16:30 又出现 1 条有效销售单、1 条采购单和 1 条工单（编号均为 `LIVE_205394` 后缀）；默认四服务仍在运行。本轮未对这些后续新增数据再次清理。隔离复演从空库开始，结束后该隔离库有两套流程数据，分别来自六岗位脚本与 Chromium 黄金用例。
- 正式计划要求 Auth、Core、IoT 分服务保存结构化业务操作审计；当前 Core 记录主要接入销售与采购，其余领域及 Auth、IoT 尚未按此口径全面接入。目标部署 MQTT 凭据/ACL、真实 Broker 容量与断连重投仍需按目标环境验收；连续两次 `mvn clean test`、人工现场验收和远端 CI 也未在本轮完成。以上不由本机单实例性能结果替代。
