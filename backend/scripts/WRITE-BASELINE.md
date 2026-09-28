# 隔离环境写入基线（Node 20）

本工具在预置的真实业务 Draft 单据上执行一次性写命令，测量 HTTP 吞吐、P95/P99、失败分类及 PostgreSQL 锁等待采样。统一入口 `write-baseline.mjs` 的 `--prepare-isolated` 阶段只经正式 API 创建测试单据；`--execute-isolated` 阶段只经正式 API 确认/提交。两阶段都不清库、不执行 SQL 业务写入，也不自动重试不确定结果。

## 事实路径与环境

- 热点库存：`POST /api/material-issues` 创建 Draft（`mes:material:requisition`），`POST /api/material-issues/{id}/confirm` 由仓库角色执行（`mes:material:confirm`）；确认事务依次锁工单、库存余额，再减少原料库存。为测到共同余额争用，准备程序给每条领料建立**不同**已下达工单，且所有单据使用同一个原料、仓库、Storage 库位。接口及 DTO 见 `ProductionFactController`、`MaterialIssueCreateRequest`、`MaterialItemRequest`，数量字段为 `items[*].quantity`。
- 多岗位：销售 `POST /api/sales-orders/{id}/submit`、采购 `POST /api/purchase-orders/{id}/submit`、仓库领料确认交错执行。三种命令均无请求体，都要求各自的 `Idempotency-Key`。
- `GET /api/inventory/balances` 是只读查询，不能直接写库存。余额与来源单据按 `tenant_id`、`isdel=0` 过滤；余额必须保持 `on_hand_qty >= reserved_qty >= 0`。脚本只读 `pg_stat_activity.wait_event_type='Lock'` 与 `pg_stat_database.deadlocks`；采样没有观测到锁等待不代表没有锁等待。
- 当前项目开发库是 `127.0.0.1:5433/ai_learn`，默认 Gateway/Core 端口为 `20001/10003`。脚本拒绝这些端口与库；要求 `127.0.0.1` 上的独立 `bench_*` 或 `wmscap_*` 数据库及非默认 Gateway 端口。`PGOPTIONS` 强制脚本的 `psql` 连接只读。**脚本无法从外部直接读取 Core 的 JDBC 配置**；它通过隔离 PG 和 API 同时存在同一轮唯一业务编号的事实进行交叉校验，启动隔离服务时仍必须核对其进程环境和 PID。

## 取得隔离源事实

本轮主控已建立独立的 `127.0.0.1:55432/wmscap_20260928_nkr1ifd2`（示例值；下一轮必须换新库名）。先按现有脚本在这套隔离依赖上执行六岗位角色流程：

```powershell
& .\backend\scripts\start-role-flow.ps1 -DatabasePort 55432 -DatabaseName wmscap_20260928_5xnobauy -RedisDatabase 15 -ServicePortOffset 1000 -SkipFrontend
```

角色流程通过后，使用本轮 `output/role-flow-runtime-*/role-flow-result.json`，其中 `status` 须为 `PASSED`。`role-flow-result.json` 提供源销售、采购、工单与原料 ID；已提交/完成的旧单据不会作为写基线对象。准备程序从这些源事实读取客户、供应商、BOM、Routing、原料 Storage 余额，再创建本轮独立 Draft。角色流程后的原料 Storage 可用库存是有限的；准备数量必须小于该库位实际可用量。

准备程序使用以下环境变量，均只放进当前进程环境，不写入 manifest 或报告：

| 变量 | 用途 |
| --- | --- |
| `WRITE_BENCHMARK_MES_TOKEN` | 真实 MES 岗位令牌，创建/下达工单及领料 Draft |
| `WRITE_BENCHMARK_WAREHOUSE_TOKEN` | 真实仓库岗位令牌，查询余额与确认领料 |
| `WRITE_BENCHMARK_SALES_TOKEN` | 真实销售岗位令牌，创建/提交销售 Draft |
| `WRITE_BENCHMARK_PURCHASE_TOKEN` | 真实采购岗位令牌，创建/提交采购 Draft |
| `WRITE_BENCHMARK_ADMIN_TOKEN` | 多岗位 fixture 专用租户管理员令牌，创建各不相同的客户和供应商 |
| `WRITE_BENCHMARK_PG_USER`、`WRITE_BENCHMARK_PG_PASSWORD` | 隔离 PG 的只读查询连接；密码可空 |
| `WRITE_BENCHMARK_PSQL` | 可选，本机 `psql.exe` 绝对路径；省略时从 PATH 查找 |
| `WRITE_BENCHMARK_ISOLATED` | 执行开关，值必须与该批 `runId` 完全一致 |

登录端点为 `POST /api/auth/login`，请求体 `tenantCode,username,password`，响应 `data.token`。同一账号重新登录会顶替旧会话，准备及执行期间不要再次用同账号登录。示例在 PowerShell 进程内取令牌，不打印密码或令牌：

```powershell
$api = 'http://127.0.0.1:21001'
$tenant = 'tenant_demo_a'
$securePassword = Read-Host '隔离租户岗位密码' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try { $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
function Set-BenchRoleToken([string]$role, [string]$username) {
    $body = @{ tenantCode=$tenant; username=$username; password=$password } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod -Method Post -Uri "$api/api/auth/login" -ContentType 'application/json' -Body $body
    if ($response.code -ne 200 -or -not $response.data.token) { throw "岗位登录失败：$role" }
    [Environment]::SetEnvironmentVariable(('WRITE_BENCHMARK_' + $role.ToUpperInvariant() + '_TOKEN'), [string]$response.data.token, 'Process')
}
Set-BenchRoleToken 'mes' 'mes.inspector'
Set-BenchRoleToken 'warehouse' 'wh.operator'
Set-BenchRoleToken 'sales' 'sales.liu'
Set-BenchRoleToken 'purchase' 'buyer.chen'
Set-BenchRoleToken 'admin' 'admin.zhang'
$password = $null
$securePassword = $null
```

另在当前进程注入 `WRITE_BENCHMARK_PG_USER`、`WRITE_BENCHMARK_PG_PASSWORD`，并确认 `psql` 可运行。不要将这些值写入 shell 历史、报告或 manifest。

## 准备可复用 fixture

以下以热点 8 条领料为例。先省略执行开关，仅做源事实及库存容量预检；输出 `PRECHECKED` 时尚未新建单据。然后设置本轮确认标记并显式执行：

```powershell
$node = 'D:\ruanjian\nvm\v20.19.2\node.exe'
$source = 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\output\role-flow-runtime-<本轮目录>\role-flow-result.json'
$runId = 'BENCH-20260928A'
$prepare = @('backend/scripts/write-baseline.mjs', '--source-report', $source, '--mode', 'hotspot', '--count', '8', '--run-id', $runId, '--output', 'output/bench-hotspot-manifest.json', '--base-url', 'http://127.0.0.1:21001/', '--db-host', '127.0.0.1', '--db-port', '55432', '--db-name', 'wmscap_20260928_5xnobauy')
& $node @prepare
$env:WRITE_BENCHMARK_ISOLATED = $runId
& $node @prepare --prepare-isolated
```

多岗位批次换一个**新的** `runId`、输出文件，命令中的 `--mode multiRole --count 4` 将新建 4 条领料 Draft、4 条销售 Draft、4 条采购 Draft；准备阶段还将经租户管理员 API 新建 4 个不同客户、4 个不同供应商，并由生产岗位新建并下达 4 个独立工单。准备程序使用固定的现实化样本档案轮换客户、供应商、联系人、地址、数量和日期偏移，避免整齐递增的占位数据；每个编号均带该批 `runId`。准备失败时输出文件保留 `PREPARING` 和已创建 ID，不能直接交给压测脚本；不自动删除或重复创建同编号单据。

生成的热点 manifest 结构如下，多岗位时省略 `hotspot`，并加入 `salesSubmit`、`purchaseSubmit` job：

```json
{
  "runId": "BENCH-20260928A",
  "mode": "hotspot",
  "jobs": [
    { "kind": "materialIssueConfirm", "id": "<正式领料UUID>", "workOrderId": "<正式工单UUID>" }
  ],
  "hotspot": {
    "productId": "<正式原料UUID>",
    "warehouseId": "<正式仓库UUID>",
    "locationId": "<正式Storage库位UUID>"
  }
}
```

准备阶段只调用已存在的业务 API：每条热点 job 依次创建工单、提交、审核、创建领料 Draft；多岗位还创建各异的客户/供应商主数据和销售/采购 Draft。固定 DTO 分别为工单 `workOrderNo,productId,plannedQty,plannedStartTime,plannedFinishTime,bomId,routingId`，领料 `issueNo,workOrderId,items[*]{productId,warehouseId,locationId,quantity}`，销售 `soNo,customerId,plannedShipDate,lines[*]`，采购 `poNo,supplierId,expectedArrivalDate,lines[*]`。全部业务行由服务端产生；不插 SQL 行。

## 写入基线及报告

先省略执行开关，确认 `PRECHECKED`：脚本只读隔离 PG 与各角色 API，要求每条单据为本轮 `runId` 前缀的 `Draft`、租户和编号一致、热点样本属于不同工单和同一库存维度，待领总量不超过可用余额。随后在同一隔离服务上执行一次：

```powershell
$write = @('backend/scripts/write-baseline.mjs', '--manifest', 'output/bench-hotspot-manifest.json', '--base-url', 'http://127.0.0.1:21001/', '--db-host', '127.0.0.1', '--db-port', '55432', '--db-name', 'wmscap_20260928_5xnobauy', '--concurrency', '8', '--sample-ms', '500', '--report', 'output/bench-hotspot-report.json')
& $node @write
& $node @write --execute-isolated
```

每条命令在进程内生成一个不同的新幂等键，只发送一次；HTTP 超时、网络中断、409 或 5xx 均记为失败，不会擅自同键/换键重放。中途退出留下 `RUNNING` 报告，可按报告的单据 ID 到数据库和 API 核对，不可直接对同一个 manifest 再运行。完整报告含 QPS、成功 QPS、P95/P99、失败分类、各岗位分组、实际已提交数、响应失败但事实已提交数、锁等待采样和数据库死锁计数差量；热点再复核期末余额是否等于期初余额减去已确认数量。报告中的锁等待为**隔离数据库范围**采样值，非单条命令精确等待时间。没有足够的锁等待样本时，报告保留有效样本数，不把零采样当作性能证明。

要重复实验，请在同一隔离库为下一轮使用新的 `runId` 重新准备 Draft，同时确认剩余库存足够；或由主控创建新的隔离库并重新走六岗位流程。不要清理开发库，也不要对 `5433/ai_learn`、默认服务端口或旧 manifest 重试。当前批次结束后保留隔离库、日志和失败单据供复盘。

## 离线验证

```powershell
& 'D:\ruanjian\nvm\v20.19.2\node.exe' --check backend/scripts/prepare-write-fixture.mjs
& 'D:\ruanjian\nvm\v20.19.2\node.exe' --check backend/scripts/write-baseline.mjs
& 'D:\ruanjian\nvm\v20.19.2\node.exe' --test backend/scripts/write-baseline.test.mjs
```

以上命令不连接服务或数据库。真实写基线须由主控在隔离服务启动、预置事实完成、岗位令牌和 PG 只读连接均验证后执行。
