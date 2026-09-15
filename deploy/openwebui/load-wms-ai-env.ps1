param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot 'windows.env')
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $ConfigPath)) {
    throw "Local config not found: $ConfigPath"
}

$allowed = @(
    'WMS_AI_PROVIDER',
    'WMS_AI_ENABLED',
    'WMS_OPENWEBUI_BASE_URL',
    'WMS_OPENWEBUI_API_KEY',
    'WMS_OPENWEBUI_MODEL',
    'WMS_OPENWEBUI_TOOL_SERVER_ID',
    'WMS_OPENWEBUI_TOOL_SERVER_IDS',
    'WMS_AI_API_WHITELIST_PATH',
    'WMS_AI_GATEWAY_BASE_URL',
    'WMS_AI_TOOL_SERVICE_SECRET',
    'WMS_AI_TOOL_TIMEOUT',
    'CORE_FACTS_IOT_ENABLED',
    'CORE_FACTS_IOT_HMAC_SECRET',
    'IOT_INTERNAL_S7_HMAC_SECRET'
)
foreach ($line in Get-Content -LiteralPath $ConfigPath -Encoding UTF8) {
    if ($line -match '^\s*#' -or $line -notmatch '=') {
        continue
    }
    $name, $value = $line -split '=', 2
    $name = $name.Trim()
    if ($allowed -contains $name) {
        [Environment]::SetEnvironmentVariable($name, $value.Trim(), 'Process')
    }
}
Write-Output 'Loaded WMS AI environment variables for the current PowerShell process without printing secrets.'
