param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot 'windows.env')
)

$ErrorActionPreference = 'Stop'

function New-SecureHex {
    param([int]$ByteLength = 32)
    $bytes = New-Object byte[] $ByteLength
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $generator.GetBytes($bytes)
    } finally {
        $generator.Dispose()
    }
    return ([BitConverter]::ToString($bytes) -replace '-', '').ToLowerInvariant()
}

function Add-MissingConfigLines {
    param(
        [string]$Path,
        [string[]]$Lines
    )
    $existingNames = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z0-9_]+)\s*=') {
            $existingNames[$matches[1]] = $true
        }
    }
    $missing = @($Lines | Where-Object {
        $name = ($_ -split '=', 2)[0]
        -not $existingNames.ContainsKey($name)
    })
    if ($missing.Count -gt 0) {
        $suffix = [Environment]::NewLine + ($missing -join [Environment]::NewLine) + [Environment]::NewLine
        [System.IO.File]::AppendAllText($Path, $suffix, [System.Text.UTF8Encoding]::new($false))
    }
    return $missing.Count
}

function Get-ConfigValue {
    param(
        [string]$Path,
        [string]$Name
    )
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match ('^\s*' + [Regex]::Escape($Name) + '\s*=(.*)$')) {
            return $matches[1].Trim()
        }
    }
    return ''
}

if (Test-Path -LiteralPath $ConfigPath) {
    # 修改用途：旧版 windows.env 原地补齐 Core/IoT 追溯所需的成对 HMAC，不覆盖已有模型或人工密钥。
    $coreFactsSecret = Get-ConfigValue -Path $ConfigPath -Name 'CORE_FACTS_IOT_HMAC_SECRET'
    $iotFactsSecret = Get-ConfigValue -Path $ConfigPath -Name 'IOT_INTERNAL_S7_HMAC_SECRET'
    if ($coreFactsSecret -and $iotFactsSecret -and $coreFactsSecret -ne $iotFactsSecret) {
        throw 'Local Core/IoT Facts HMAC settings do not match; resolve the existing values before initialization.'
    }
    $factsSecret = if ($coreFactsSecret) {
        $coreFactsSecret
    } elseif ($iotFactsSecret) {
        $iotFactsSecret
    } else {
        New-SecureHex
    }
    $added = Add-MissingConfigLines -Path $ConfigPath -Lines @(
        'CORE_FACTS_IOT_ENABLED=true'
        "CORE_FACTS_IOT_HMAC_SECRET=$factsSecret"
        "IOT_INTERNAL_S7_HMAC_SECRET=$factsSecret"
        'WMS_AI_API_WHITELIST_PATH=../deploy/openwebui/wms-ai-api-whitelist.yml'
        'WMS_AI_GATEWAY_BASE_URL=http://127.0.0.1:20001'
        'WMS_OPENWEBUI_TOOL_SERVER_IDS=server:wms_core,server:wms_iot'
    )
    Write-Output "Local Open WebUI config already exists; added $added missing traceability setting(s): $ConfigPath"
    exit 0
}

$configDirectory = Split-Path -Parent $ConfigPath
New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
$factsSecret = New-SecureHex
$content = @(
    '# Local secrets for Open WebUI and WMS AI. Never commit this file.'
    'SILICONFLOW_BASE_URL=https://api.siliconflow.cn/v1'
    'SILICONFLOW_MODEL=deepseek-ai/DeepSeek-V4-Flash'
    'SILICONFLOW_API_KEY='
    "OPENWEBUI_SECRET_KEY=$(New-SecureHex)"
    'WMS_OPENWEBUI_API_KEY='
    "WMS_AI_TOOL_SERVICE_SECRET=$(New-SecureHex)"
    'CORE_FACTS_IOT_ENABLED=true'
    "CORE_FACTS_IOT_HMAC_SECRET=$factsSecret"
    "IOT_INTERNAL_S7_HMAC_SECRET=$factsSecret"
    'WMS_AI_PROVIDER=open-webui'
    'WMS_AI_ENABLED=true'
    'WMS_OPENWEBUI_BASE_URL=http://127.0.0.1:3000'
    'WMS_OPENWEBUI_MODEL=wms-assistant'
    'WMS_OPENWEBUI_TOOL_SERVER_ID=server:wms'
    'WMS_OPENWEBUI_TOOL_SERVER_IDS=server:wms_core,server:wms_iot'
    'WMS_AI_API_WHITELIST_PATH=../deploy/openwebui/wms-ai-api-whitelist.yml'
    'WMS_AI_GATEWAY_BASE_URL=http://127.0.0.1:20001'
    'WMS_AI_TOOL_TIMEOUT=10s'
)
[System.IO.File]::WriteAllLines($ConfigPath, $content, [System.Text.UTF8Encoding]::new($false))
Write-Output "Created local Open WebUI config without printing secrets: $ConfigPath"
