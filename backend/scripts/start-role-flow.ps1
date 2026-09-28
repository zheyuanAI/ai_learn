# 用途：启动本轮已构建的本机服务并执行六岗位验收；不构建、不清库、不启动 Broker。
# 前置：若使用开发库须先备份并清理；隔离库可为空库。准备指定端口的 Redis，并完成最新 mvn package。
# 示例：pwsh -NoProfile -File backend/scripts/start-role-flow.ps1 -DatabasePort 55432 -DatabaseName wmscap_demo -RedisPort 16379 -RedisDatabase 15 -ServicePortOffset 1000 -SkipFrontend
[CmdletBinding()]
param(
    [string]$NodeExecutable = 'D:\ruanjian\nvm\v20.19.2\node.exe',
    [ValidateRange(10, 300)][int]$HealthTimeoutSeconds = 120,
    [ValidateRange(60, 3600)][int]$RoleFlowTimeoutSeconds = 900,
    [ValidateRange(1, 65535)][int]$DatabasePort = 5433,
    [ValidatePattern('^[a-z][a-z0-9_]*$')][string]$DatabaseName = 'ai_learn',
    [ValidateRange(0, 15)][int]$RedisDatabase = 0,
    [ValidateRange(1, 65535)][int]$RedisPort = 6379,
    [ValidateRange(0, 40000)][int]$ServicePortOffset = 0,
    [string]$WorkCenterId = '',
    [switch]$SkipFrontend
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# 修改用途：隔离基线必须同时换库和换服务端口，避免把压测或角色流程写入开发库。
if (($DatabasePort -eq 5433) -xor ($DatabaseName -eq 'ai_learn')) {
    throw '开发库必须同时使用 5433/ai_learn；隔离库须同时更换端口和库名。'
}
if ($DatabasePort -ne 5433 -and $ServicePortOffset -eq 0) {
    throw '隔离库运行须设置非零 ServicePortOffset。'
}
if ($DatabasePort -ne 5433 -and $RedisDatabase -eq 0) {
    throw '隔离库运行须设置非默认 RedisDatabase，避免复用开发会话。'
}
if ($ServicePortOffset -ne 0 -and -not $SkipFrontend) {
    throw '隔离服务须使用 SkipFrontend；当前 Vite 代理固定指向开发 Gateway 20001。'
}
$authPort = 10002 + $ServicePortOffset
$corePort = 10003 + $ServicePortOffset
$iotPort = 10004 + $ServicePortOffset
$gatewayPort = 20001 + $ServicePortOffset
$projectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$javaExecutable = Join-Path $projectRoot 'runtime/jdk/bin/java.exe'
$mqttBin = Join-Path $projectRoot 'runtime/mosquitto-2.1.2/app'
$viteEntry = Join-Path $projectRoot 'frontend/node_modules/vite/bin/vite.js'
$roleFlowEntry = Join-Path $PSScriptRoot 'role-flow.mjs'
$runId = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$runDirectory = Join-Path $projectRoot ('output/role-flow-runtime-' + $runId)
$pidEvidencePath = Join-Path $runDirectory 'processes.json'
$environmentBefore = @{}
$evidence = [ordered]@{
    startedAt = [DateTimeOffset]::Now.ToString('o'); status = 'PREFLIGHT'; projectRoot = $projectRoot
    runtimeDirectory = $runDirectory; profile = $(if ($DatabasePort -eq 5433) { 'dev' } else { 'isolated' }); aiEnabled = $false
    databasePort = $DatabasePort; databaseName = $DatabaseName; redisPort = $RedisPort; redisDatabase = $RedisDatabase
    brokerOwner = 'role-flow.mjs'; artifacts = @(); processes = @()
}

<# 用途：保存无凭据 PID、日志和制品证据；无入参、无返回值；整次运行使用新目录，禁止复用历史日志。 #>
function Save-ProcessEvidence {
    $serializedEvidence = $evidence | ConvertTo-Json -Depth 8
    # 修改原因：Windows 上监控读取会短暂占用证据文件；有界重试，避免健康服务因日志竞争而中断验收。
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        try {
            Set-Content -LiteralPath $pidEvidencePath -Value $serializedEvidence -Encoding UTF8
            return
        } catch [IO.IOException] {
            if ($attempt -eq 9) { throw }
            Start-Sleep -Milliseconds 100
        }
    }
}

<# 用途：临时注入子进程环境；入参为变量名和值，无返回值；先保存旧值，最终恢复，不打印变量内容。 #>
function Set-RunEnvironment([string]$Name, [string]$Value) {
    if (-not $environmentBefore.ContainsKey($Name)) {
        $environmentBefore[$Name] = [Environment]::GetEnvironmentVariable($Name, 'Process')
    }
    [Environment]::SetEnvironmentVariable($Name, $Value, 'Process')
}

<# 用途：生成只在本轮进程内使用的随机密钥；无入参，返回 32 字节 Base64；使用系统加密随机源。 #>
function New-RunSecret {
    $bytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $generator.GetBytes($bytes); return [Convert]::ToBase64String($bytes) }
    finally { $generator.Dispose() }
}

<# 用途：启动本轮拥有的隐藏进程；入参为名称、程序、参数和工作目录；返回进程对象，同时记录独立 stdout/stderr/PID。 #>
function Start-RunProcess([string]$Name, [string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory) {
    $stdout = Join-Path $runDirectory ($Name + '.stdout.log')
    $stderr = Join-Path $runDirectory ($Name + '.stderr.log')
    $started = Start-Process -FilePath $Executable -ArgumentList $Arguments -WorkingDirectory $WorkingDirectory `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    $evidence.processes += [ordered]@{ name = $Name; pid = $started.Id; stdout = $stdout; stderr = $stderr }
    Save-ProcessEvidence
    return $started
}

<# 用途：有界等待真实新进程的健康端点；入参为进程、端口和前端标记，无返回值；拒绝其他 PID 顶替端口或进程提前退出。 #>
function Wait-RunHealth([Diagnostics.Process]$StartedProcess, [int]$Port, [bool]$Frontend = $false) {
    $deadline = [DateTime]::UtcNow.AddSeconds($HealthTimeoutSeconds)
    $url = if ($Frontend) { "http://127.0.0.1:$Port/" } else { "http://127.0.0.1:$Port/actuator/health" }
    while ([DateTime]::UtcNow -lt $deadline) {
        $StartedProcess.Refresh()
        if ($StartedProcess.HasExited) { throw "PID $($StartedProcess.Id) 在健康检查前退出；查看本轮日志。" }
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
        if ($listeners | Where-Object { $_.OwningProcess -ne $StartedProcess.Id }) {
            throw "端口 $Port 被其他 PID 占用；不接管既有进程。"
        }
        try {
            if ($Frontend) {
                $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 2
                if ($response.StatusCode -eq 200) { return }
            } else {
                $health = Invoke-RestMethod -Uri $url -TimeoutSec 2
                if ($health.status -eq 'UP') { return }
            }
        } catch { # 启动期间连接失败或 DOWN 继续等待；不输出可能含配置的异常正文。
        }
        Start-Sleep -Milliseconds 500
    }
    throw "端口 $Port 健康等待超时（$HealthTimeoutSeconds 秒）；查看本轮日志。"
}

# 修改用途：先检查所有前置条件，再准备密钥或启动任何进程；端口冲突只报 PID，不停止旧服务。
$requiredPaths = @($javaExecutable, $NodeExecutable, $roleFlowEntry,
    (Join-Path $mqttBin 'mosquitto.exe'), (Join-Path $mqttBin 'mosquitto_passwd.exe'), (Join-Path $mqttBin 'mosquitto_pub.exe'))
if (-not $SkipFrontend) { $requiredPaths += $viteEntry }
foreach ($path in $requiredPaths) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "缺少前置文件：$path" }
}
$nodeVersion = & $NodeExecutable --version
if ($LASTEXITCODE -ne 0 -or $nodeVersion -notmatch '^v20\.') { throw '必须使用 Node 20 可执行文件。' }
foreach ($port in @($authPort, $corePort, $iotPort, $gatewayPort, 1883) + $(if ($SkipFrontend) { @() } else { @(5173) })) {
    $conflicts = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)
    if ($conflicts.Count -gt 0) { throw "端口 $port 已占用，PID：$(($conflicts.OwningProcess | Sort-Object -Unique) -join ',')；先由主控处理。" }
}
foreach ($port in @($DatabasePort, $RedisPort)) {
    if (-not (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)) {
        throw "缺少已准备的本机依赖端口 $port；本脚本不启动或修改数据库/Redis。"
    }
}
# 修改原因：新克隆和空库没有被 Git 忽略的保留事实文件；本轮为 Routing 工序与设备生成同一软引用，
# 若调用方已核实某个工作中心 ID，可用 WorkCenterId 显式覆盖。两者都不假定存在独立工作中心主表。
$validatedWorkCenter = [guid]::Empty
if ([string]::IsNullOrWhiteSpace($WorkCenterId)) {
    $validatedWorkCenter = [guid]::NewGuid()
    $evidence.workCenterSource = 'generated-for-run'
} else {
    if (-not [guid]::TryParse($WorkCenterId, [ref]$validatedWorkCenter) -or $validatedWorkCenter -eq [guid]::Empty) {
        throw 'WorkCenterId 必须为已核实的非空 UUID。'
    }
    $evidence.workCenterSource = 'explicit'
}
$evidence.workCenterId = $validatedWorkCenter.ToString()

Add-Type -AssemblyName System.IO.Compression.FileSystem
$applications = @(
    @{ name = 'auth'; module = 'platform-auth'; port = $authPort; mainClass = 'com.ailearn.platform.auth.AuthApplication' },
    @{ name = 'core'; module = 'platform-core'; port = $corePort; mainClass = 'com.ailearn.platform.core.CoreApplication' },
    @{ name = 'iot'; module = 'platform-iot'; port = $iotPort; mainClass = 'com.ailearn.platform.iot.IotApplication' },
    @{ name = 'gateway'; module = 'platform-gateway'; port = $gatewayPort; mainClass = 'com.ailearn.platform.gateway.GatewayApplication' }
)
foreach ($application in $applications) {
    $moduleRoot = Join-Path $projectRoot ('backend/' + $application.module)
    $jar = Get-ChildItem -LiteralPath (Join-Path $moduleRoot 'target') -Filter '*.jar' -File |
        Where-Object { $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (-not $jar) { throw "缺少 $($application.module) 的可执行 JAR；由主控先 package。" }
    $sourceDirectories = @((Join-Path $moduleRoot 'src/main'), (Join-Path $moduleRoot 'target/classes'),
        (Join-Path $projectRoot 'backend/platform-shared/src/main'), (Join-Path $projectRoot 'backend/platform-shared/target/classes'))
    $newestInput = Get-ChildItem -LiteralPath $sourceDirectories -File -Recurse |
        Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if ($newestInput -and $newestInput.LastWriteTimeUtc -gt $jar.LastWriteTimeUtc) {
        throw "$($application.module) JAR 早于最新主源码或编译结果；由主控重新 package，禁止启动旧包。"
    }
    $archive = [IO.Compression.ZipFile]::OpenRead($jar.FullName)
    try {
        $entry = $archive.GetEntry('META-INF/MANIFEST.MF')
        if (-not $entry) { throw "$($application.module) 缺少 JAR manifest。" }
        $reader = New-Object IO.StreamReader($entry.Open())
        try { $manifest = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($manifest -notmatch ('(?m)^Start-Class: ' + [regex]::Escape($application.mainClass) + '\r?$')) {
            throw "$($application.module) 不是对应启动类的 Boot 可执行包。"
        }
    } finally { $archive.Dispose() }
    $application.jar = $jar.FullName
    $evidence.artifacts += [ordered]@{ module = $application.module; jar = $jar.FullName; mainClass = $application.mainClass
        builtAt = $jar.LastWriteTimeUtc.ToString('o'); sha256 = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash }
}

New-Item -ItemType Directory -Path $runDirectory | Out-Null
try {
    $contextSecret = New-RunSecret
    $factsSecret = New-RunSecret
    $subscriberSecret = New-RunSecret
    $subscriberName = 'iot-role-flow-' + $runId
    $passwordFile = Join-Path $runDirectory 'mosquitto.password'
    $aclFile = Join-Path $runDirectory 'mosquitto.acl'
    $brokerConfig = Join-Path $runDirectory 'mosquitto.conf'
    $utf8 = New-Object Text.UTF8Encoding($false)
    # 新 password_file 仅创建一次；-U 就地哈希，订阅密码不放在子进程参数或输出日志中。
    [IO.File]::WriteAllText($passwordFile, ($subscriberName + ':' + $subscriberSecret + "`n"), $utf8)
    $passwordUtility = Start-RunProcess 'mqtt-password-prepare' (Join-Path $mqttBin 'mosquitto_passwd.exe') @('-U', ('"{0}"' -f $passwordFile)) $runDirectory
    if (-not $passwordUtility.WaitForExit(10000)) {
        Stop-Process -Id $passwordUtility.Id -ErrorAction SilentlyContinue
        [IO.File]::WriteAllText($passwordFile, '', $utf8)
        throw 'Broker 密码哈希准备超时。'
    }
    # 修改用途：WaitForExit 后刷新退出状态，再判断真实子进程退出码。
    $passwordUtility.Refresh()
    if ($passwordUtility.ExitCode -ne 0) {
        [IO.File]::WriteAllText($passwordFile, '', $utf8)
        throw 'Broker 密码哈希准备失败；不输出凭据。'
    }
    [IO.File]::WriteAllText($aclFile, "user $subscriberName`ntopic read devices/#`n", $utf8)
    $brokerLog = (Join-Path $runDirectory 'mosquitto.log').Replace('\', '/')
    $config = @('listener 1883 127.0.0.1', 'allow_anonymous false',
        ('password_file ' + $passwordFile.Replace('\', '/')), ('acl_file ' + $aclFile.Replace('\', '/')),
        'persistence false', ('log_dest file ' + $brokerLog), 'connection_messages true', 'log_type all') -join "`n"
    [IO.File]::WriteAllText($brokerConfig, ($config + "`n"), $utf8)

    $runEnvironment = @{
        JAVA_HOME = (Join-Path $projectRoot 'runtime/jdk'); SPRING_PROFILES_ACTIVE = 'dev'; WMS_AI_ENABLED = 'false'
        POSTGRES_HOST = '127.0.0.1'; POSTGRES_PORT = [string]$DatabasePort; POSTGRES_DB = $DatabaseName
        # 修改原因：隔离复演不得强制复用其他项目占用的 6379；端口由本轮显式参数传入。
        REDIS_HOST = '127.0.0.1'; REDIS_PORT = [string]$RedisPort; SPRING_DATA_REDIS_DATABASE = [string]$RedisDatabase
        AUTH_SERVICE_URI = "http://127.0.0.1:$authPort"; CORE_SERVICE_URI = "http://127.0.0.1:$corePort"; IOT_SERVICE_URI = "http://127.0.0.1:$iotPort"
        AUTH_JWKS_URL = "http://127.0.0.1:$authPort/api/auth/jwks"
        CORE_CONTEXT_IOT_HMAC_SECRET = $contextSecret; IOT_CONTEXT_CORE_HMAC_SECRET = $contextSecret
        IOT_CONTEXT_CORE_ENABLED = 'true'; IOT_CONTEXT_CORE_QUERY_URL = "http://127.0.0.1:$corePort/internal/production-context"
        CORE_FACTS_IOT_ENABLED = 'true'; CORE_FACTS_IOT_BASE_URL = "http://127.0.0.1:$iotPort"
        CORE_FACTS_IOT_HMAC_SECRET = $factsSecret; IOT_INTERNAL_S7_HMAC_SECRET = $factsSecret
        IOT_MQTT_ENABLED = 'true'; IOT_MQTT_SERVER_URI = 'tcp://127.0.0.1:1883'; IOT_MQTT_QOS = '1'
        IOT_MQTT_CLIENT_ID = ('role-flow-consumer-' + $runId); IOT_MQTT_USERNAME = $subscriberName; IOT_MQTT_PASSWORD = $subscriberSecret
        IOT_MQTT_TOPIC_FILTER = 'devices/+/telemetry'; IOT_MQTT_CLEAN_SESSION = 'false'; IOT_MQTT_RECONNECT_DELAY_SECONDS = '1'
        IOT_MQTT_PASSWORD_FILE = $passwordFile; IOT_MQTT_ACL_FILE = $aclFile
        IOT_MQTT_PERSISTENCE_DIRECTORY = (Join-Path $runDirectory 'mqtt-client-state')
        ROLE_FLOW_API_BASE = "http://127.0.0.1:$gatewayPort"; ROLE_FLOW_PASSWORD = '123456'
        ROLE_FLOW_WORK_CENTER_ID = $validatedWorkCenter.ToString(); ROLE_FLOW_OUTPUT = (Join-Path $runDirectory 'role-flow-result.json')
        ROLE_FLOW_MQTT_BIN = $mqttBin; ROLE_FLOW_MQTT_CONFIG = $brokerConfig
        ROLE_FLOW_MQTT_PASSWORD_FILE = $passwordFile; ROLE_FLOW_MQTT_ACL_FILE = $aclFile
        ROLE_FLOW_IOT_LOG = (Join-Path $runDirectory 'iot.stdout.log')
    }
    if ($env:REDIS_PASSWORD) { $runEnvironment.SPRING_DATA_REDIS_PASSWORD = $env:REDIS_PASSWORD }
    foreach ($item in $runEnvironment.GetEnumerator()) { Set-RunEnvironment $item.Key $item.Value }
    $evidence.status = 'STARTING'
    Save-ProcessEvidence
    foreach ($application in $applications) {
        $arguments = @('-Dfile.encoding=UTF-8', '-jar', ('"{0}"' -f $application.jar),
            '--spring.profiles.active=dev', '--server.address=127.0.0.1', ('--server.port=' + $application.port))
        if ($application.name -eq 'core') { $arguments += '--platform.ai.enabled=false' }
        if ($application.name -eq 'gateway') { $arguments += '--gateway.ai.enabled=false' }
        $startedService = Start-RunProcess $application.name $javaExecutable $arguments (Join-Path $projectRoot 'backend')
        Wait-RunHealth $startedService $application.port
    }
    if (-not $SkipFrontend) {
        $frontend = Start-RunProcess 'frontend' $NodeExecutable @(('"{0}"' -f $viteEntry), '--host', '127.0.0.1', '--port', '5173', '--strictPort') (Join-Path $projectRoot 'frontend')
        Wait-RunHealth $frontend 5173 $true
    }
    $evidence.status = 'ROLE_FLOW_RUNNING'
    Save-ProcessEvidence
    $roleFlow = Start-RunProcess 'role-flow' $NodeExecutable @(('"{0}"' -f $roleFlowEntry)) $projectRoot
    if (-not $roleFlow.WaitForExit($RoleFlowTimeoutSeconds * 1000)) {
        Stop-Process -Id $roleFlow.Id -ErrorAction SilentlyContinue
        throw '岗位验收超时；服务和已创建事实保留供主控检查。'
    }
    # 修改用途：刷新 Node 验收进程状态，避免沿用启动时缓存的退出信息。
    $roleFlow.Refresh()
    $evidence.roleFlowExitCode = $roleFlow.ExitCode
    if ($roleFlow.ExitCode -ne 0) { throw '岗位验收失败；查看本轮 role-flow stdout/stderr/result，服务保留供复核。' }
    $evidence.status = 'PASSED'
    $evidence.completedAt = [DateTimeOffset]::Now.ToString('o')
    Save-ProcessEvidence
    Write-Output "本轮验收通过，日志、结果与 PID：$runDirectory"
} catch {
    $evidence.status = 'FAILED'
    $evidence.failure = $_.Exception.Message
    Save-ProcessEvidence
    throw
} finally {
    foreach ($name in $environmentBefore.Keys) {
        [Environment]::SetEnvironmentVariable($name, $environmentBefore[$name], 'Process')
    }
    $contextSecret = $null; $factsSecret = $null; $subscriberSecret = $null; $runEnvironment = $null
}
