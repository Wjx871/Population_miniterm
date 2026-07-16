# Stop backend (pidfile based) and any stray java processes holding port 8080.
# Frontend is unaffected (managed separately).
$ErrorActionPreference = 'SilentlyContinue'
$BackendPidFile = Join-Path $PSScriptRoot '..\..\logs\backend.pid'

# 1) The pid we recorded
if (Test-Path $BackendPidFile) {
    $pid = (Get-Content $BackendPidFile -ErrorAction SilentlyContinue | Select-Object -First 1).Trim()
    if ($pid -match '^\d+$') {
        Write-Host "[stop-backend] recorded PID $pid"
        Stop-Process -Id ([int]$pid) -Force
    }
}

# 2) Any java that is our jar (catches orphaned forks)
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId=$($_.Id)").CommandLine
    if ($cmd -and $cmd -match 'population-miniterm') {
        Write-Host "[stop-backend] killing orphan java PID $($_.Id)"
        Stop-Process -Id $_.Id -Force
    }
}

# 3) Wait for port 8080 to free up
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 1
    $inUse = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue).Count -gt 0
    if (-not $inUse) {
        Write-Host "[stop-backend] port 8080 free"
        exit 0
    }
}
Write-Host "[stop-backend] WARNING: port 8080 still in use after 10s"
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  PID=$($_.OwningProcess)"
}
exit 1
