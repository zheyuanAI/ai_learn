param(
    [string]$InstallRoot = (Join-Path $env:LOCALAPPDATA 'AiLearnWms\openwebui'),
    [string]$PythonVersion = '3.12',
    [string]$OpenWebUiVersion = '0.11.3'
)

$ErrorActionPreference = 'Stop'
$venvPath = Join-Path $InstallRoot 'venv'
$pythonPath = Join-Path $venvPath 'Scripts\python.exe'

New-Item -ItemType Directory -Path $InstallRoot -Force | Out-Null
if (-not (Test-Path -LiteralPath $pythonPath)) {
    & py "-$PythonVersion" -m venv $venvPath
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to create Python $PythonVersion virtual environment."
    }
}

& $pythonPath -m pip install --disable-pip-version-check --index-url https://pypi.org/simple --upgrade pip
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to upgrade pip from official PyPI.'
}
& $pythonPath -m pip install --disable-pip-version-check --index-url https://pypi.org/simple "open-webui==$OpenWebUiVersion"
if ($LASTEXITCODE -ne 0) {
    throw "Unable to install Open WebUI $OpenWebUiVersion from official PyPI."
}

& (Join-Path $PSScriptRoot 'initialize-windows-config.ps1')
Write-Output "Open WebUI $OpenWebUiVersion installed under: $InstallRoot"
