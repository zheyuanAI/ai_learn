# Local Development Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 固化自研项目的 Node 20、PostgreSQL 5433、四服务端口，并在项目内部准备可手动启动的 Redis 3.0.504 与 Mosquitto 2.1.2。

**Architecture:** 采用“版本化配置 + Git 忽略的本机运行包”模式：`deploy/local/` 保存可审查配置，`runtime/` 保存二进制、数据和日志。现有 Docker Compose 保留为未来可选入口，本次不启动服务、不安装前后端依赖、不执行构建。

**Tech Stack:** Windows PowerShell 7、Java 21、Maven 3.9.1、Node.js 20、PostgreSQL 16、Redis for Windows 3.0.504、Eclipse Mosquitto 2.1.2 x64、Spring Boot 3.3.5。

## Global Constraints

- Java 固定为 21，由 IntelliJ IDEA 使用 `D:\ruanjian\IntelliJ IDEA 2025.1\jbr`。
- Maven 固定为 3.9.1，由 IntelliJ IDEA 使用 `D:\ruanjian\apache-maven-3.9.1`。
- 自研项目前端固定使用 Node.js 20；参考工程的 Node 22 配置不得删除或改写。
- PostgreSQL 固定为 `127.0.0.1:5433`，Redis 固定为 `127.0.0.1:6379`，Mosquitto 固定为 `127.0.0.1:1883`。
- Gateway、Auth、Core、IoT 分别固定为 10001、10002、10003、10004。
- Redis 与 Mosquitto 仅监听 `127.0.0.1`，不注册 Windows 服务，不自动终止已有进程。
- 不执行 `npm install`、`npm run build`、`mvn package` 或 `mvn compile`。
- 不启动 PostgreSQL、Redis、Mosquitto 或任何 Spring Boot 服务。
- 遵守仓库规则，不执行 Git commit；每个任务以 `git diff` 和只读验证作为审核检查点。

---

## File Map

- Modify: `.gitignore` — 排除 `runtime/` 下的二进制、数据和日志，同时保留说明文件。
- Create: `runtime/README.md` — 记录版本、来源、SHA256、可执行文件位置和手动启动命令。
- Create: `deploy/local/redis.conf` — Redis 本地回环监听及文件模式配置。
- Create: `deploy/local/mosquitto.conf` — Mosquitto 本地回环监听及匿名开发配置。
- Modify: `frontend/package.json` — 声明 Node 20 运行基线，不安装依赖。
- Modify: `backend/platform-gateway/src/main/resources/application.yml` — 声明端口 10001。
- Modify: `backend/platform-auth/src/main/resources/application.yml` — 声明端口 10002。
- Modify: `backend/platform-core/src/main/resources/application.yml` — 声明端口 10003。
- Modify: `backend/platform-iot/src/main/resources/application.yml` — 声明端口 10004。
- Modify: `specs/00-project/architecture.md` — 固化一期本地中间件与服务端口事实。
- Modify: `README.md`、`frontend/README.md`、`backend/README.md`、`docs/project-structure.md`、`AGENTS.md` — 同步开发入口和目录说明。
- Modify: `../AGENTS.md` — 将外层工作区对自研项目的 Node/PostgreSQL 基线改为已确认值，同时保留参考工程差异。
- Generated and ignored: `runtime/redis-3.0.504/**`、`runtime/mosquitto-2.1.2/**` — 下载包、可执行文件、数据和日志。

---

### Task 1: 固化工具链、目录和文档基线

**Files:**
- Modify: `.gitignore`
- Create: `runtime/README.md`
- Modify: `frontend/package.json`
- Modify: `README.md`
- Modify: `frontend/README.md`
- Modify: `specs/00-project/architecture.md`
- Modify: `docs/project-structure.md`
- Modify: `AGENTS.md`
- Modify: `../AGENTS.md`
- Inspect only: `openspec/README.md`

**Interfaces:**
- Consumes: 已批准的 `docs/superpowers/specs/2026-08-19-local-development-baseline-design.md`。
- Produces: 后续下载任务使用的 `runtime/` 约定，以及项目统一的 Node 20、PostgreSQL 5433 事实。

- [ ] **Step 1: 更新 Git 忽略规则**

在 `.gitignore` 末尾加入：

```gitignore
runtime/*
!runtime/README.md
```

- [ ] **Step 2: 创建运行目录说明**

创建 `runtime/README.md`，先写入不含版本占位值的目录约束：

```markdown
# Local Runtime Packages

本目录只保存本机开发使用的中间件包。除本说明外，二进制、安装包、数据和日志均不提交 Git。

运行包记录必须包含组件名称、准确版本、发布页、直接下载地址、SHA256、可执行文件绝对路径和手动启动命令。只有从本机下载文件计算出 SHA256 后才能写入对应记录，禁止预填或猜测哈希。

手动启动前先确认 6379 和 1883 未被其他进程占用。
```

- [ ] **Step 3: 声明 Node 20**

在 `frontend/package.json` 的 `type` 后加入：

```json
"engines": {
  "node": "20.x"
},
```

- [ ] **Step 4: 同步长期文档**

在列出的 README、架构、目录与 AGENTS 文档中统一写明：自研项目使用 Node 20、PostgreSQL 5433；参考工程仍可保留各自的 Node 22、PostgreSQL 5323 配置。不得改写功能范围、接口或业务规则。

- [ ] **Step 5: 验证工具链与文档事实**

Run:

```powershell
node -e "const p=require('./frontend/package.json'); if(p.engines.node!=='20.x') process.exit(1)"
rg -n "Node 20|5433|5323|Node 22" README.md frontend/README.md specs/00-project/architecture.md docs/project-structure.md AGENTS.md ..\AGENTS.md
git diff -- .gitignore frontend/package.json README.md frontend/README.md specs/00-project/architecture.md docs/project-structure.md AGENTS.md
```

Expected: Node 检查退出码为 0；自研项目只标注 5433/Node 20，5323/Node 22 仅出现在参考工程说明中；diff 不包含业务功能改动。

---

### Task 2: 下载并配置 Redis 3.0.504

**Files:**
- Create: `deploy/local/redis.conf`
- Modify: `runtime/README.md`
- Generated and ignored: `runtime/redis-3.0.504/**`

**Interfaces:**
- Consumes: `runtime/` 忽略规则和 `127.0.0.1:6379` 端口约定。
- Produces: `runtime/redis-3.0.504/redis-server.exe` 和可手动使用的 `deploy/local/redis.conf`。

- [ ] **Step 1: 下载 Microsoft 归档发布包**

Run from the repository root:

```powershell
$redisDir = 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\redis-3.0.504'
$redisZip = Join-Path $redisDir 'Redis-x64-3.0.504.zip'
New-Item -ItemType Directory -Force $redisDir | Out-Null
Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/microsoftarchive/redis/releases/download/win-3.0.504/Redis-x64-3.0.504.zip' -OutFile $redisZip
```

Expected: ZIP 来自 `microsoftarchive/redis` 的 `win-3.0.504` 发布页，文件大小大于 1 MB。

- [ ] **Step 2: 计算哈希并解压**

Run:

```powershell
$redisDir = 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\redis-3.0.504'
$redisZip = Join-Path $redisDir 'Redis-x64-3.0.504.zip'
Get-FileHash -Algorithm SHA256 $redisZip | Format-List
Expand-Archive -LiteralPath $redisZip -DestinationPath $redisDir -Force
New-Item -ItemType Directory -Force (Join-Path $redisDir 'data') | Out-Null
New-Item -ItemType Directory -Force (Join-Path $redisDir 'logs') | Out-Null
& (Join-Path $redisDir 'redis-server.exe') --version
```

Expected: 输出 `Redis server v=3.0.504`；把命令输出的 64 位 SHA256 原样写入 `runtime/README.md`。

- [ ] **Step 3: 创建只监听本机的 Redis 配置**

创建 `deploy/local/redis.conf`：

```conf
# 自研项目本地 Redis：仅允许本机访问，避免开发实例暴露到局域网。
bind 127.0.0.1
port 6379
timeout 0
tcp-keepalive 60
loglevel notice
logfile "./logs/redis.log"
databases 16
save 900 1
save 300 10
save 60 10000
stop-writes-on-bgsave-error yes
rdbcompression yes
rdbchecksum yes
dbfilename dump.rdb
dir "./data"
appendonly no
```

- [ ] **Step 4: 写入 Redis 手动启动命令**

将以下命令加入 `runtime/README.md`：

```powershell
Set-Location 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\redis-3.0.504'
.\redis-server.exe '..\..\deploy\local\redis.conf'
```

- [ ] **Step 5: 只读验证，不启动 Redis**

Run:

```powershell
$redisExe = 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\redis-3.0.504\redis-server.exe'
if (-not (Test-Path -LiteralPath $redisExe)) { throw 'redis-server.exe missing' }
& $redisExe --version
Select-String -Path 'deploy\local\redis.conf' -Pattern '^bind 127\.0\.0\.1$','^port 6379$'
git status --short -- runtime deploy/local/redis.conf
```

Expected: 可执行文件存在、版本为 3.0.504、配置命中两行；Git 只看到 README 和配置，不列出 ZIP、EXE、data 或 logs。

---

### Task 3: 下载并配置 Mosquitto 2.1.2

**Files:**
- Create: `deploy/local/mosquitto.conf`
- Modify: `runtime/README.md`
- Generated and ignored: `runtime/mosquitto-2.1.2/**`

**Interfaces:**
- Consumes: `runtime/` 忽略规则和 `127.0.0.1:1883` 端口约定。
- Produces: `runtime/mosquitto-2.1.2/app/mosquitto.exe` 和可手动使用的 `deploy/local/mosquitto.conf`。

- [ ] **Step 1: 记录现有服务状态并下载官方安装包**

Run:

```powershell
Get-Service -Name 'mosquitto' -ErrorAction SilentlyContinue | Select-Object Status,Name,DisplayName
$mosqDir = 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\mosquitto-2.1.2'
$mosqInstaller = Join-Path $mosqDir 'mosquitto-2.1.2-install-windows-x64.exe'
New-Item -ItemType Directory -Force $mosqDir | Out-Null
Invoke-WebRequest -UseBasicParsing -Uri 'https://mosquitto.org/files/binary/win64/mosquitto-2.1.2-install-windows-x64.exe' -OutFile $mosqInstaller
Get-FileHash -Algorithm SHA256 $mosqInstaller | Format-List
```

Expected: 安装包来自 Eclipse Mosquitto 官方 win64 目录，文件大小大于 20 MB；把实际 SHA256 原样写入 `runtime/README.md`。

- [ ] **Step 2: 静默安装到项目内部但不注册服务**

Run:

```powershell
$mosqDir = 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\mosquitto-2.1.2'
$mosqInstaller = Join-Path $mosqDir 'mosquitto-2.1.2-install-windows-x64.exe'
$mosqApp = Join-Path $mosqDir 'app'
$process = Start-Process -FilePath $mosqInstaller -ArgumentList @('/S', "/D=$mosqApp") -Wait -PassThru
if ($process.ExitCode -ne 0) { throw "Mosquitto installer failed: $($process.ExitCode)" }
```

Do not run `mosquitto.exe install`. The official Windows service registration is an explicit, separate command and is outside this plan.

- [ ] **Step 3: 创建只监听本机的 Mosquitto 配置**

创建 `deploy/local/mosquitto.conf`：

```conf
# 自研项目本地 MQTT Broker：仅允许本机匿名开发连接。
listener 1883 127.0.0.1
allow_anonymous true
persistence false
log_dest stdout
connection_messages true
log_type all
```

- [ ] **Step 4: 写入 Mosquitto 手动启动命令**

将以下命令加入 `runtime/README.md`：

```powershell
& 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\mosquitto-2.1.2\app\mosquitto.exe' -c 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\deploy\local\mosquitto.conf' -v
```

- [ ] **Step 5: 只读验证，不启动 Broker**

Run:

```powershell
$mosqExe = 'D:\AI\ai_learn_wms_ai\ai_learn_developProject\runtime\mosquitto-2.1.2\app\mosquitto.exe'
if (-not (Test-Path -LiteralPath $mosqExe)) { throw 'mosquitto.exe missing' }
& $mosqExe -h
Select-String -Path 'deploy\local\mosquitto.conf' -Pattern '^listener 1883 127\.0\.0\.1$','^allow_anonymous true$'
Get-Service -Name 'mosquitto' -ErrorAction SilentlyContinue | Select-Object Status,Name,DisplayName
git status --short -- runtime deploy/local/mosquitto.conf
```

Expected: 帮助输出显示 2.1.2；配置命中两行；服务状态与 Step 1 相同；Git 不列出安装包和 EXE。

---

### Task 4: 固化四个 Spring Boot 服务端口

**Files:**
- Modify: `backend/platform-gateway/src/main/resources/application.yml`
- Modify: `backend/platform-auth/src/main/resources/application.yml`
- Modify: `backend/platform-core/src/main/resources/application.yml`
- Modify: `backend/platform-iot/src/main/resources/application.yml`
- Modify: `backend/README.md`

**Interfaces:**
- Consumes: 已确认的四模块端口映射。
- Produces: IDEA 可分别启动的四个无端口冲突服务；不产生 Gateway 路由规则。

- [ ] **Step 1: 为 Gateway 添加端口**

在 Gateway `application.yml` 顶部加入：

```yaml
server:
  port: 10001
```

- [ ] **Step 2: 为 Auth 添加端口**

在 Auth `application.yml` 顶部加入：

```yaml
server:
  port: 10002
```

- [ ] **Step 3: 为 Core 添加端口**

在 Core `application.yml` 顶部加入：

```yaml
server:
  port: 10003
```

- [ ] **Step 4: 为 IoT 添加端口**

在 IoT `application.yml` 顶部加入：

```yaml
server:
  port: 10004
```

- [ ] **Step 5: 同步后端 README 并验证映射**

在 `backend/README.md` 增加四模块端口表，并运行：

```powershell
$expected = @{
  'platform-gateway' = 10001
  'platform-auth' = 10002
  'platform-core' = 10003
  'platform-iot' = 10004
}
foreach ($module in $expected.Keys) {
  $path = "backend/$module/src/main/resources/application.yml"
  $content = Get-Content -Raw $path
  if ($content -notmatch "(?ms)^server:\s*\r?\n\s+port:\s+$($expected[$module])\s*$") {
    throw "Unexpected port in $path"
  }
}
git diff -- backend
```

Expected: 四个文件全部通过映射验证；diff 仅增加 `server.port` 和端口说明。

---

### Task 5: 完成一致性与交付自检

**Files:**
- Inspect: 本计划 File Map 中的全部文件
- Modify if needed: 仅修正本任务发现的环境基线矛盾

**Interfaces:**
- Consumes: Tasks 1–4 的全部产物。
- Produces: 可供用户手动启动的路径、命令和无歧义环境文档。

- [ ] **Step 1: 扫描占位内容和旧基线**

Run:

```powershell
rg -n "下载后[记]录|T[B]D|T[O]DO" runtime/README.md deploy/local docs/superpowers/specs/2026-08-19-local-development-baseline-design.md
rg -n "5323|Node 22|10001|10002|10003|10004|5433|Node 20" README.md frontend/README.md backend/README.md specs/00-project/architecture.md docs/project-structure.md AGENTS.md ..\AGENTS.md
```

Expected: 第一个命令无结果；5323/Node 22 只用于明确标识参考工程差异，其他值与设计一致。

- [ ] **Step 2: 验证二进制、配置和 Git 忽略**

Run:

```powershell
$required = @(
  'runtime\redis-3.0.504\redis-server.exe',
  'runtime\mosquitto-2.1.2\app\mosquitto.exe',
  'deploy\local\redis.conf',
  'deploy\local\mosquitto.conf'
)
foreach ($path in $required) {
  if (-not (Test-Path -LiteralPath $path)) { throw "Missing: $path" }
}
git check-ignore runtime/redis-3.0.504/redis-server.exe runtime/mosquitto-2.1.2/app/mosquitto.exe
git status --short
```

Expected: 四个路径存在；两个 EXE 均被忽略；Git 状态不包含任何运行二进制、数据或日志。

- [ ] **Step 3: 检查端口占用但不处理进程**

Run:

```powershell
netstat -ano | Select-String -Pattern ':5433\s',':6379\s',':1883\s',':10001\s',':10002\s',':10003\s',':10004\s'
```

Expected: 只记录当前占用情况；即使 6379 或 1883 被占用，也不得停止进程或启动新服务。

- [ ] **Step 4: 最终 diff 审核**

Run:

```powershell
git diff --check
git diff --stat
git diff -- .gitignore frontend/package.json backend README.md frontend/README.md backend/README.md specs/00-project/architecture.md docs/project-structure.md AGENTS.md deploy/local runtime/README.md
```

Expected: `git diff --check` 退出码为 0；没有业务实现、依赖安装、Gateway 路由、数据库 Schema、Redis Key 或 MQTT Topic 设计。

- [ ] **Step 5: 交付手动启动信息**

向用户报告 Redis、Mosquitto 的绝对路径、实际 SHA256、配置路径、手动启动命令和当前端口冲突。明确声明未启动服务、未执行构建、未安装前后端依赖、未执行 Git commit。
