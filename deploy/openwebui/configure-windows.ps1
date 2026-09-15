param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot 'windows.env'),
    [string]$GatewayBaseUrl = 'http://127.0.0.1:20001'
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
    throw "Local config not found: $ConfigPath"
}

. (Join-Path $PSScriptRoot 'load-wms-ai-env.ps1') -ConfigPath $ConfigPath
& (Join-Path $PSScriptRoot 'validate-knowledge.ps1') | Out-Host

foreach ($name in @('WMS_OPENWEBUI_BASE_URL', 'WMS_OPENWEBUI_API_KEY', 'WMS_AI_TOOL_SERVICE_SECRET')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "$name is missing from the local config"
    }
}

$openWebUiBaseUrl = $env:WMS_OPENWEBUI_BASE_URL.TrimEnd('/')
$gatewayBaseUrl = $GatewayBaseUrl.TrimEnd('/')
$jsonHeaders = @{
    Authorization = "Bearer $env:WMS_OPENWEBUI_API_KEY"
    'Content-Type' = 'application/json'
}
$authHeaders = @{ Authorization = "Bearer $env:WMS_OPENWEBUI_API_KEY" }

Write-Host 'Checking the WMS business API catalogs generated from the single whitelist...'
$toolCatalogIndex = Invoke-RestMethod -Uri "$gatewayBaseUrl/v3/api-docs/ai-tools"
$catalogServices = @($toolCatalogIndex.services)
if ($catalogServices.Count -eq 0 -or [int]$toolCatalogIndex.operation_count -le 0) {
    throw 'The WMS AI catalog index contains no services or operations'
}

$toolServers = @()
$toolIds = @()
$verifiedOperationCount = 0
foreach ($service in $catalogServices) {
    $serviceId = [string]$service.id
    $catalogPath = [string]$service.catalog_path
    $expectedOperationIds = @($service.operation_ids | ForEach-Object { [string]$_ } | Sort-Object -Unique)
    $filteredCatalog = Invoke-RestMethod -Uri "$gatewayBaseUrl$catalogPath"
    $actualOperationIds = @()
    foreach ($pathProperty in $filteredCatalog.paths.PSObject.Properties) {
        foreach ($methodProperty in $pathProperty.Value.PSObject.Properties) {
            if ($methodProperty.Name -in @('get', 'post', 'put', 'patch', 'delete')) {
                $actualOperationIds += [string]$methodProperty.Value.operationId
            }
        }
    }
    $actualOperationIds = @($actualOperationIds | Sort-Object -Unique)
    if (($actualOperationIds -join ',') -ne ($expectedOperationIds -join ',')) {
        throw "Filtered OpenAPI operations do not match the whitelist for service $serviceId"
    }
    $verifiedOperationCount += $actualOperationIds.Count
    $toolId = "server:wms_$serviceId"
    $toolIds += $toolId
    $toolServers += @{
        url = $gatewayBaseUrl
        path = $catalogPath
        type = 'openapi'
        auth_type = 'none'
        headers = @{
            'X-WMS-AI-Service-Key' = $env:WMS_AI_TOOL_SERVICE_SECRET
            'X-WMS-OpenWebUI-Chat-Id' = '{{CHAT_ID}}'
            'X-WMS-OpenWebUI-Message-Id' = '{{MESSAGE_ID}}'
        }
        key = ''
        config = @{ enable = $true }
        info = @{
            id = "wms_$serviceId"
            name = [string]$service.name
            description = '由 WMS 唯一业务 API 白名单生成；最终权限由当前登录用户的 Spring Security 决定'
        }
    }
}
if ($verifiedOperationCount -ne [int]$toolCatalogIndex.operation_count) {
    throw 'The total filtered OpenAPI operation count does not match the whitelist index'
}
foreach ($toolServer in $toolServers) {
    $null = Invoke-RestMethod -Method Post -Uri "$openWebUiBaseUrl/api/v1/configs/tool_servers/verify" `
        -Headers $jsonHeaders -Body ($toolServer | ConvertTo-Json -Depth 10)
}
$currentToolServers = Invoke-RestMethod -Uri "$openWebUiBaseUrl/api/v1/configs/tool_servers" -Headers $authHeaders
$connections = @($currentToolServers.TOOL_SERVER_CONNECTIONS | Where-Object {
    -not ([string]$_.info.id).StartsWith('wms')
})
$connections += $toolServers
$null = Invoke-RestMethod -Method Post -Uri "$openWebUiBaseUrl/api/v1/configs/tool_servers" `
    -Headers $jsonHeaders -Body (@{ TOOL_SERVER_CONNECTIONS = $connections } | ConvertTo-Json -Depth 12)

$manifestPath = Join-Path $PSScriptRoot '..\..\docs\ai-knowledge\knowledge-manifest.yaml'
$knowledgeRoot = Split-Path -Parent (Resolve-Path -LiteralPath $manifestPath)
$enabledDocuments = @()
$pendingPath = $null
foreach ($line in Get-Content -LiteralPath $manifestPath -Encoding UTF8) {
    if ($line -match '^\s*-\s+path:\s+(.+?)\s*$') {
        $pendingPath = $Matches[1].Trim('"', "'")
        continue
    }
    if ($pendingPath -and $line -match '^\s+enabled:\s+(true|false)\s*$') {
        if ($Matches[1] -eq 'true') {
            $enabledDocuments += $pendingPath
        }
        $pendingPath = $null
    }
}
if ($enabledDocuments.Count -eq 0) {
    throw 'No enabled document was found in knowledge-manifest.yaml'
}

$knowledgeName = 'WMS 受控业务知识'
$knowledgeList = Invoke-RestMethod -Uri "$openWebUiBaseUrl/api/v1/knowledge/?page=1" -Headers $authHeaders
$matchingKnowledge = @($knowledgeList.items | Where-Object { $_.name -eq $knowledgeName })
if ($matchingKnowledge.Count -gt 1) {
    throw "Multiple Open WebUI knowledge bases are named $knowledgeName"
}
if ($matchingKnowledge.Count -eq 0) {
    $knowledge = Invoke-RestMethod -Method Post -Uri "$openWebUiBaseUrl/api/v1/knowledge/create" `
        -Headers $jsonHeaders -Body (@{
            name = $knowledgeName
            description = '制造与仓储协同执行平台的受控静态业务规则、术语、角色职责和 AI 工具目录'
            access_grants = @()
        } | ConvertTo-Json -Depth 5)
} else {
    $knowledge = $matchingKnowledge[0]
}

$knowledgeFiles = Invoke-RestMethod -Uri "$openWebUiBaseUrl/api/v1/knowledge/$($knowledge.id)/files?page=1" `
    -Headers $authHeaders
if ($knowledgeFiles.total -eq 0) {
    Write-Host "Uploading $($enabledDocuments.Count) controlled knowledge documents..."
    for ($index = 0; $index -lt $enabledDocuments.Count; $index++) {
        $sourcePath = (Resolve-Path -LiteralPath (Join-Path $knowledgeRoot $enabledDocuments[$index])).Path
        $safeUploadName = 'wms-knowledge-{0:D2}.md' -f $index
        $rawUpload = & curl.exe -sS --fail-with-body -X POST `
            -H "Authorization: Bearer $env:WMS_OPENWEBUI_API_KEY" `
            -F "file=@$sourcePath;filename=$safeUploadName;type=text/markdown" `
            "$openWebUiBaseUrl/api/v1/files/?process=true&process_in_background=false"
        if ($LASTEXITCODE -ne 0) {
            throw "Knowledge upload failed at document index $index"
        }
        $uploadedFile = $rawUpload | ConvertFrom-Json
        $null = Invoke-RestMethod -Method Post `
            -Uri "$openWebUiBaseUrl/api/v1/knowledge/$($knowledge.id)/file/add" `
            -Headers $jsonHeaders -Body (@{ file_id = $uploadedFile.id } | ConvertTo-Json)
    }
} elseif ($knowledgeFiles.total -ne $enabledDocuments.Count) {
    throw "Knowledge file count is $($knowledgeFiles.total), expected $($enabledDocuments.Count); review it before replacing data"
}

$systemPrompt = @'
你是制造与仓储协同执行平台的只读业务助手。业务状态、原因、责任角色和建议必须优先依据 WMS 实时查询工具与已绑定的受控知识库；不得用通用 WMS 常识替代本项目事实。需要实时数据时应自主选择已授权工具，不要求用户使用固定关键词。不得声称已经执行任何新增、修改、删除、状态流转、SQL、Shell、文件系统或任意 HTTP 操作；当前只允许查询、分析和建议。工具拒绝、超时、无数据或事实源未启用时，应明确说明限制并提示用户补充必要标识，不得猜测。一个问题可能涉及多个角色，不生成页面跳转或代办动作。
'@.Trim()
$modelForm = @{
    id = 'wms-assistant'
    base_model_id = 'deepseek-ai/DeepSeek-V4-Flash'
    name = 'WMS AI 助手'
    params = @{
        system = $systemPrompt
        temperature = 0.2
        function_calling = 'native'
    }
    meta = @{
        description = '基于受控知识和当前 WMS 用户实时权限的只读查询分析助手'
        capabilities = @{
            vision = $false
            file_upload = $false
            web_search = $false
            image_generation = $false
            code_interpreter = $false
            citations = $true
        }
        builtinTools = @{ knowledge = $true }
        knowledge = @(@{ id = $knowledge.id; name = $knowledgeName; type = 'collection' })
        toolIds = $toolIds
    }
    access_grants = @()
    is_active = $true
}
$existingModels = Invoke-RestMethod -Uri "$openWebUiBaseUrl/api/v1/models/export" -Headers $authHeaders
$modelEndpoint = if (@($existingModels | Where-Object { $_.id -eq 'wms-assistant' }).Count -eq 0) {
    '/api/v1/models/create'
} else {
    '/api/v1/models/model/update'
}
$null = Invoke-RestMethod -Method Post -Uri "$openWebUiBaseUrl$modelEndpoint" `
    -Headers $jsonHeaders -Body ($modelForm | ConvertTo-Json -Depth 12)
$visibleModels = Invoke-RestMethod -Uri "$openWebUiBaseUrl/api/models" -Headers $authHeaders
if (@($visibleModels.data.id) -notcontains 'wms-assistant') {
    throw 'The wms-assistant preset was saved but is not visible through the Open WebUI model API'
}

Write-Host "Open WebUI WMS setup passed: $verifiedOperationCount business API tools, $($enabledDocuments.Count) knowledge files, model wms-assistant."
