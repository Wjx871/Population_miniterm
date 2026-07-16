# Start the population-miniterm Spring Boot backend in the background.
# Usage:
#   pwsh -File scripts/windows/start_backend.ps1 -Database population_miniterm
# Defaults match application.properties (DB_URL=jdbc:mysql://.../population_miniterm).
[CmdletBinding()]
param(
    [string]$Database = 'population_miniterm',
    [string]$DbHost   = '127.0.0.1',
    [int]   $DbPort   = 3306,
    [string]$DbUser   = 'root',
    [string]$DbPassword = $env:DB_PASSWORD,
    [string]$AppUploadDir = $env:APP_UPLOAD_DIR,
    [string]$JwtSecret   = $env:JWT_SECRET,
    [switch]$DisableRedis
)

$ErrorActionPreference = 'Stop'
$Root = Resolve-Path (Join-Path $PSScriptRoot '..\..')
Set-Location $Root

if ([string]::IsNullOrWhiteSpace($AppUploadDir)) {
    $AppUploadDir = Join-Path $Root 'data\uploads'
}
if ([string]::IsNullOrWhiteSpace($JwtSecret)) {
    throw 'JWT_SECRET or -JwtSecret is required'
}

# Free port first
$stop = Join-Path $PSScriptRoot 'stop_backend.ps1'
$psExe = (Get-Command pwsh -ErrorAction SilentlyContinue).Source
if (-not $psExe) { $psExe = (Get-Command powershell -ErrorAction SilentlyContinue).Source }
if ($psExe) {
    & $psExe -NoProfile -ExecutionPolicy Bypass -File $stop
} else {
    & $stop
}
if ($LASTEXITCODE -ne 0) {
    throw 'Port 8080 is occupied and stop_backend.ps1 could not free it; aborting start'
}

if (-not (Test-Path $AppUploadDir)) {
    New-Item -ItemType Directory -Path $AppUploadDir -Force | Out-Null
}

$env:DB_URL    = "jdbc:mysql://${DbHost}:${DbPort}/${Database}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = $DbUser
$env:DB_PASSWORD = $DbPassword
$env:JWT_SECRET  = $JwtSecret
$env:APP_UPLOAD_DIR = $AppUploadDir
if ($DisableRedis) { $env:REDIS_ENABLED = 'false' }

$logDir = Join-Path $Root 'logs'
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$log = Join-Path $logDir 'backend.log'
$err = "$log.err"
if (Test-Path $log) { Remove-Item $log }
if (Test-Path $err) { Remove-Item $err }

Write-Host "[start-backend] DB_URL=$($env:DB_URL)"
Write-Host "[start-backend] UPLOAD_DIR=$AppUploadDir"

$proc = Start-Process -FilePath 'java' `
    -ArgumentList @('-jar','target\population-miniterm-1.0.0.jar') `
    -WorkingDirectory $Root `
    -RedirectStandardOutput $log `
    -RedirectStandardError $err `
    -PassThru -NoNewWindow

Write-Host "[start-backend] PID $($proc.Id)"
Set-Content -Path (Join-Path $logDir 'backend.pid') -Value $proc.Id -Encoding ascii

$timeout = 60
$elapsed = 0
while ($elapsed -lt $timeout) {
    Start-Sleep -Seconds 2
    $elapsed += 2
    if ((Test-Path $log) -and (Select-String -Path $log -Pattern 'Started PopulationMinitermApplication' -Quiet)) {
        Write-Host "[start-backend] ready after ${elapsed}s"
        exit 0
    }
    if ((Test-Path $err) -and (Select-String -Path $err -Pattern 'FAILED TO START' -Quiet -ErrorAction SilentlyContinue)) {
        Write-Host "[start-backend] FAILED"
        Get-Content $log -Tail 20
        exit 2
    }
}
Write-Host "[start-backend] TIMEOUT after ${timeout}s"
Get-Content $log -Tail 10
exit 3
