[CmdletBinding()]
param(
    [string]$BackendBaseUrl = 'http://127.0.0.1:8080',
    [ValidateRange(1, 60)]
    [int]$TimeoutSeconds = 5,
    [switch]$Quiet
)

$healthUrl = "$($BackendBaseUrl.TrimEnd('/'))/api/health"

try {
    $response = Invoke-WebRequest -Uri $healthUrl -Method Get -TimeoutSec $TimeoutSeconds -UseBasicParsing -ErrorAction Stop
} catch {
    if ($_.Exception.Response) {
        if (-not $Quiet) { Write-Error "[FAIL] 健康接口 HTTP 异常：$healthUrl" }
        exit 3
    }
    if (-not $Quiet) { Write-Error "[FAIL] 无法连接健康接口：$healthUrl" }
    exit 2
}

try {
    $payload = $response.Content | ConvertFrom-Json -ErrorAction Stop
} catch {
    if (-not $Quiet) { Write-Error "[FAIL] 健康接口响应不是有效 JSON：$healthUrl" }
    exit 3
}

if ($response.StatusCode -ne 200 -or $payload.code -ne 200) {
    if (-not $Quiet) { Write-Error "[FAIL] 健康接口契约异常：HTTP=$($response.StatusCode)，code=$($payload.code)" }
    exit 3
}
if ($null -eq $payload.data -or [string]::IsNullOrWhiteSpace([string]$payload.data.database)) {
    if (-not $Quiet) { Write-Error '[FAIL] 健康接口缺少 data.database。' }
    exit 4
}
if ([string]$payload.data.database -ne 'UP') {
    if (-not $Quiet) { Write-Error "[FAIL] 数据库未就绪：database=$($payload.data.database)" }
    exit 5
}

if (-not $Quiet) {
    Write-Host "[PASS] 后端健康检查：database=UP；redisStatus=$($payload.data.redisStatus)"
}
exit 0
