param(
    [string]$ManifestPath = (Join-Path $PSScriptRoot "..\..\docs\ai-knowledge\knowledge-manifest.yaml")
)

$ErrorActionPreference = "Stop"
$manifest = Get-Item -LiteralPath $ManifestPath
$knowledgeRoot = [System.IO.Path]::GetFullPath($manifest.DirectoryName)
$rootPrefix = $knowledgeRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
$manifestLines = Get-Content -LiteralPath $manifest.FullName -Encoding UTF8
$relativePaths = @(
    $manifestLines |
        ForEach-Object {
            if ($_ -match '^\s*-\s+path:\s+(.+?)\s*$') { $Matches[1].Trim('"', "'") }
        } |
        Where-Object { $_ }
)

if ($relativePaths.Count -eq 0) {
    throw "knowledge-manifest.yaml does not declare any document"
}

$validated = @()
foreach ($relativePath in $relativePaths) {
    if ([System.IO.Path]::IsPathRooted($relativePath)) {
        throw "Knowledge manifest must not use an absolute path: $relativePath"
    }
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $knowledgeRoot $relativePath))
    if (-not $candidate.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Knowledge path escapes docs/ai-knowledge: $relativePath"
    }
    if ([System.IO.Path]::GetExtension($candidate) -ne ".md") {
        throw "Knowledge manifest only allows Markdown files: $relativePath"
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "Knowledge file does not exist: $relativePath"
    }
    $content = Get-Content -LiteralPath $candidate -Raw -Encoding UTF8
    if ($content -match '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|\bsk-[A-Za-z0-9_-]{16,}') {
        throw "Knowledge file may contain a private key or API key: $relativePath"
    }
    $validated += [PSCustomObject]@{
        Path = $relativePath
        Sha256 = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}

$validated | Format-Table -AutoSize
Write-Host "Knowledge manifest validation passed: $($validated.Count) files under $knowledgeRoot."
