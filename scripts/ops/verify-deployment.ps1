[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://127.0.0.1:8088',
    [string]$AdminToken = $env:OPS_ADMIN_TOKEN
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')

function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    Write-Host "[PASS] $Message"
}

$health = Invoke-WebRequest -Uri "$base/api/health" -UseBasicParsing -TimeoutSec 15
$data = $health.Content | ConvertFrom-Json
Assert-Check ($data.code -eq 200 -and $data.data.database -eq 'UP') '数据库健康'
Assert-Check ($health.Headers['X-Trace-Id'] -match '^[A-Za-z0-9_-]{1,64}$') 'API 返回有效 traceId'
Assert-Check (-not [string]::IsNullOrWhiteSpace($health.Headers['X-Instance-Id'])) 'API 返回 instanceId'
Assert-Check ($health.Headers['Cache-Control'] -match 'no-store') 'API 禁止浏览器缓存'
Write-Host "[INFO] Redis: $($data.data.redisStatus); cacheMode: $($data.data.cacheMode)"

$index = Invoke-WebRequest -Uri "$base/index.html" -UseBasicParsing -TimeoutSec 15
Assert-Check ($index.Headers['Cache-Control'] -match 'no-cache') 'HTML 入口要求重新验证'
$deep = Invoke-WebRequest -Uri "$base/persons" -UseBasicParsing -TimeoutSec 15
Assert-Check ($deep.Content -eq $index.Content) 'History 路由回退到 HTML 入口'

$assetMatch = [regex]::Match($index.Content, '(?:src|href)="(/assets/[^\"]+\.(?:js|css))"')
Assert-Check ($assetMatch.Success) '构建产物存在 JS/CSS 资源引用'
$asset = Invoke-WebRequest -Uri ($base + $assetMatch.Groups[1].Value) -UseBasicParsing -TimeoutSec 15
Assert-Check ($asset.Headers['Cache-Control'] -match 'immutable') '带哈希静态资源使用长期缓存'

if ($AdminToken) {
    $ops = Invoke-RestMethod -Uri "$base/api/ops/cache" -Headers @{ Authorization = "Bearer $AdminToken" } -TimeoutSec 15
    Assert-Check ($ops.code -eq 200 -and $ops.data.scope -eq 'process_since_start') '授权运维接口可访问'
    Write-Host "[INFO] cache counters: $($ops.data.counters | ConvertTo-Json -Compress)"
    $runtime = Invoke-RestMethod -Uri "$base/api/ops/runtime" -Headers @{ Authorization = "Bearer $AdminToken" } -TimeoutSec 15
    Assert-Check ($runtime.code -eq 200 -and $runtime.data.heapUsedBytes -gt 0) 'JVM 与连接池运行指标可读取'
    $routing = Invoke-RestMethod -Uri "$base/api/ops/database-routing" -Headers @{ Authorization = "Bearer $AdminToken" } -TimeoutSec 15
    Assert-Check ($routing.code -eq 200) '主从路由指标可读取'
    $broker = Invoke-RestMethod -Uri "$base/api/ops/rabbitmq" -Headers @{ Authorization = "Bearer $AdminToken" } -TimeoutSec 15
    Assert-Check ($broker.code -eq 200) '队列监控接口可读取'
    Write-Host "[INFO] RabbitMQ: $($broker.data.status)"
    if ($broker.data.status -eq 'DOWN') { Write-Warning '队列不可用，请核对私有网络、管理账号和 RabbitMQ 健康状态。' }
} else {
    Write-Host '[SKIP] 未设置 OPS_ADMIN_TOKEN，未检查授权运维接口。'
}

Write-Host '[DONE] 只读部署检查完成；故障、写业务和持久化仍需按执行清单验收。'
