param(
    [string]$InstallRoot = (Join-Path $env:LOCALAPPDATA 'AiLearnWms\openwebui'),
    [string]$ConfigPath = (Join-Path $PSScriptRoot 'windows.env'),
    [int]$Port = 3000
)

$ErrorActionPreference = 'Stop'

function Import-LocalEnv {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Local config not found: $Path"
    }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match '^\s*#' -or $line -notmatch '=') {
            continue
        }
        $name, $value = $line -split '=', 2
        [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), 'Process')
    }
}

Import-LocalEnv $ConfigPath
$openWebUi = Join-Path $InstallRoot 'venv\Scripts\open-webui.exe'
if (-not (Test-Path -LiteralPath $openWebUi)) {
    throw 'Open WebUI is not installed. Run install-windows.ps1 first.'
}
if ([string]::IsNullOrWhiteSpace($env:OPENWEBUI_SECRET_KEY)) {
    throw 'OPENWEBUI_SECRET_KEY is missing from the local config.'
}

$dataDirectory = Join-Path $InstallRoot 'data'
New-Item -ItemType Directory -Path $dataDirectory -Force | Out-Null
$env:DATA_DIR = $dataDirectory
$env:WEBUI_SECRET_KEY = $env:OPENWEBUI_SECRET_KEY
$env:OPENAI_API_BASE_URL = $env:SILICONFLOW_BASE_URL
$env:OPENAI_API_KEY = $env:SILICONFLOW_API_KEY
$env:ENABLE_OPENAI_API_PASSTHROUGH = 'false'
$env:ENABLE_FORWARD_USER_INFO_HEADERS = 'false'
$env:ENABLE_API_KEYS = 'true'
$env:ENABLE_SIGNUP = 'false'
$env:CORS_ALLOW_ORIGIN = 'http://127.0.0.1:3000;http://localhost:3000'

if ([string]::IsNullOrWhiteSpace($env:SILICONFLOW_API_KEY)) {
    Write-Warning 'SILICONFLOW_API_KEY is empty. Open WebUI can start, but SiliconFlow DeepSeek V4 Flash will be unavailable until it is configured.'
}
Write-Output "Starting Open WebUI on http://127.0.0.1:$Port"
& $openWebUi serve --host 127.0.0.1 --port $Port
