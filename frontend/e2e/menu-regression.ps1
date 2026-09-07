[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

# 阶段 0 只允许通过环境变量或外部 state 文件提供认证上下文，避免把凭据写入仓库。
$baseUrl = if ([string]::IsNullOrWhiteSpace($env:STAGE_UI_BASE_URL)) { "http://localhost:5173" } else { $env:STAGE_UI_BASE_URL.TrimEnd('/') }
$tenantCode = if ([string]::IsNullOrWhiteSpace($env:STAGE_UI_TENANT_CODE)) { "tenant_demo_a" } else { $env:STAGE_UI_TENANT_CODE }
$username = if ([string]::IsNullOrWhiteSpace($env:STAGE_UI_USERNAME)) { "admin.zhang" } else { $env:STAGE_UI_USERNAME }
$password = $env:STAGE_UI_PASSWORD
$stateFile = $env:STAGE_UI_STATE_FILE
$sessionName = if ([string]::IsNullOrWhiteSpace($env:STAGE_UI_SESSION)) { "stage-0-7-ui" } else { $env:STAGE_UI_SESSION }

if ([string]::IsNullOrWhiteSpace($stateFile) -and [string]::IsNullOrWhiteSpace($password)) {
  Write-Error "请设置 STAGE_UI_PASSWORD，或设置已脱敏且受保护的 STAGE_UI_STATE_FILE；脚本不会猜测或保存密码。"
  exit 2
}

$cli = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\node_modules\.bin\playwright-cli.cmd"))
if (-not (Test-Path -LiteralPath $cli)) {
  Write-Error "未找到项目内 playwright-cli，请先在 frontend 执行 npm i。"
  exit 2
}

$repoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$artifactDir = Join-Path $repoRoot "output\playwright\stage-0-7-ui"
$checkFile = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "menu-regression-check.mjs"))
$resolvedStateFile = $null
if (-not [string]::IsNullOrWhiteSpace($stateFile)) {
  # 在切换到产物目录前解析相对路径，避免 npm 工作目录变化导致 state 文件找不到。
  $resolvedStateFile = [IO.Path]::GetFullPath($stateFile)
}
New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

function Invoke-QuietPlaywrightCli {
  <#
    用途：执行不应把页面内容或敏感输入写入控制台的 Playwright CLI 操作。
    入参：CLI 参数数组；出参：无。失败时抛出异常并保留退出码语义。
    流程：使用阶段专用 session 调用 CLI，丢弃正常输出，错误由调用方处理。
  #>
  param(
    [Parameter(Mandatory = $true)]
    [string[]]$Arguments
  )

  $allArguments = @("-s=$sessionName") + $Arguments
  & $cli @allArguments *> $null
  if ($LASTEXITCODE -ne 0) {
    throw "playwright-cli 操作失败，退出码 $LASTEXITCODE。"
  }
}

$previousLocation = Get-Location
Push-Location -LiteralPath $artifactDir
try {
  # 仅清理本脚本自己命名的浏览器 session，不触碰 Redis、数据库或用户浏览器配置。
  & $cli "-s=$sessionName" "close" *> $null
  & $cli "-s=$sessionName" "delete-data" *> $null

  Invoke-QuietPlaywrightCli @("open", "$baseUrl/login")
  Invoke-QuietPlaywrightCli @("snapshot")

  if (-not [string]::IsNullOrWhiteSpace($stateFile)) {
    if (-not (Test-Path -LiteralPath $resolvedStateFile)) {
      throw "STAGE_UI_STATE_FILE 不存在：$resolvedStateFile"
    }
    Invoke-QuietPlaywrightCli @("state-load", $resolvedStateFile)
    Invoke-QuietPlaywrightCli @("goto", "$baseUrl/")
  } else {
    Invoke-QuietPlaywrightCli @("select", "#loginTenant", $tenantCode)
    Invoke-QuietPlaywrightCli @("fill", "#loginUsername", $username)
    # CLI 输出被抑制；密码只在当前子进程参数和页面内存中短暂存在，不写入报告或仓库。
    Invoke-QuietPlaywrightCli @("fill", "#loginPassword", $password)
    Invoke-QuietPlaywrightCli @("click", "button[type=submit]")
  }

  $checkArguments = @("-s=$sessionName", "--raw", "run-code", "--filename", $checkFile)
  $rawResult = (& $cli @checkArguments 2>&1 | Out-String).Trim()
  $checkExitCode = $LASTEXITCODE
  if ($checkExitCode -ne 0) {
    throw "菜单回归函数执行失败，退出码 $checkExitCode：$rawResult"
  }

  try {
    $result = $rawResult | ConvertFrom-Json
  } catch {
    throw "无法解析菜单回归结果：$rawResult"
  }

  Write-Output "阶段 0 菜单回归结果（session=$sessionName，角色=$username）："
  foreach ($item in $result.results) {
    $state = if ($item.passed) { "PASS" } else { "FAIL" }
    Write-Output ("[{0}] {1}: {2}" -f $state, $item.id, $item.detail)
  }
  Write-Output "最终 URL：$($result.finalUrl)"

  if (-not $result.passed) {
    exit 1
  }
} finally {
  & $cli "-s=$sessionName" "close" *> $null
  Set-Location -LiteralPath $previousLocation
}
