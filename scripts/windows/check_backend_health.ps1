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
    $payload = $response.Content | ConvertFrom-Json -ErrorAction Stop
    $database = [string]$payload.data.database

    if ($response.StatusCode -ne 200 -or $payload.code -ne 200 -or $database -ne 'UP') {
        throw "健康契约不满足（HTTP=$($response.StatusCode), code=$($payload.code), database=$database）"
    }

    if (-not $Quiet) {
        Write-Host "[PASS] 后端健康检查：database=UP；redisStatus=$($payload.data.redisStatus)"
    }
    exit 0
} catch {
    # 不回显完整响应，避免意外输出连接串、密钥或其它敏感诊断信息。
    if (-not $Quiet) {
        Write-Error "[FAIL] 后端健康检查失败：$healthUrl；$($_.Exception.Message)"
    }
    exit 1
}
