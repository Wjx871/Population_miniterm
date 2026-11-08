[CmdletBinding()]
param([string]$TestSelector = 'Issue8183MySqlIT', [int]$Port = 13391)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Port -eq 3306 -or $Port -lt 1024 -or $Port -gt 65535) { throw 'Use a dedicated non-default test port.' }
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw 'Selected port is already in use; no process will be touched.' }
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$bin = 'C:/Program Files/MySQL/MySQL Server 8.4/bin'
$tag = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$taskDir = Join-Path $env:TEMP ('population_mysql84_issue8183_' + $tag)
$null = New-Item -ItemType Directory -Path $taskDir
$null = & icacls $taskDir /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' 'Administrators:(OI)(CI)F'
if ($LASTEXITCODE -ne 0) { throw 'Unable to restrict the new test directory.' }
$dataDir = Join-Path $taskDir 'data'
$ini = Join-Path $taskDir 'my.ini'
$config = @"
[mysqld]
basedir=$($bin.Replace('\','/').Replace('/bin',''))
datadir=$($dataDir.Replace('\','/'))
port=$Port
bind-address=127.0.0.1
mysqlx=OFF
transaction-isolation=REPEATABLE-READ
innodb-buffer-pool-size=128M
log-error=$($taskDir.Replace('\','/'))/mysql-error.log
"@
[IO.File]::WriteAllText($ini, $config, [Text.UTF8Encoding]::new($false))
& "$bin/mysqld.exe" "--defaults-file=$ini" --initialize-insecure *> (Join-Path $taskDir 'initialize.log')
if ($LASTEXITCODE -ne 0) { throw "Initialization failed; evidence preserved at $taskDir" }
$proc = Start-Process -FilePath "$bin/mysqld.exe" -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
$appPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$adminPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:MYSQL84_GATE_DB_URL = "jdbc:mysql://127.0.0.1:$Port/population_miniterm_mysql_gate_issue8183_${tag}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=false"
$env:MYSQL84_GATE_DB_USERNAME = "i8183_$tag"
$env:MYSQL84_GATE_DB_PASSWORD = $appPassword
$env:MYSQL84_GATE_ADMIN_USERNAME = 'root'
$env:MYSQL84_GATE_ADMIN_PASSWORD = $adminPassword
$credentials = @{url=$env:MYSQL84_GATE_DB_URL; appUser=$env:MYSQL84_GATE_DB_USERNAME; appPassword=$appPassword; adminPassword=$adminPassword; pid=$proc.Id; port=$Port}
($credentials | ConvertTo-Json -Compress | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString) |
    Set-Content -LiteralPath (Join-Path $taskDir 'credentials.dpapi')
$previousPwd = $env:MYSQL_PWD
try {
    $env:MYSQL_PWD = ''
    $ready = $false
    for ($i=0; $i -lt 100; $i++) {
        $null = 'SELECT 1;' | & "$bin/mysql.exe" --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch --skip-column-names 2>$null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        if ($proc.HasExited) { throw "New test instance exited; see $taskDir" }
        Start-Sleep -Milliseconds 100
    }
    if (!$ready) { throw "New test instance not ready; see $taskDir" }
    $null = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$adminPassword';" | & "$bin/mysql.exe" --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch
    if ($LASTEXITCODE -ne 0) { throw 'Failed to set new private instance administrator credentials.' }
    $env:MYSQL_PWD = $adminPassword
    & (Join-Path $PSScriptRoot 'mysql84-prepare-schema.ps1') -Mode Fresh -MySqlClient "$bin/mysql.exe" *> (Join-Path $taskDir 'schema.log')
    if ($LASTEXITCODE -ne 0) { throw "Schema preparation failed; see $taskDir" }
    Push-Location $repo
    try {
        & ./mvnw.cmd "-Dtest=$TestSelector" test *> (Join-Path $taskDir 'maven.log')
        $result = $LASTEXITCODE
        Get-Content -LiteralPath (Join-Path $taskDir 'maven.log') -Tail 24
        Write-Output "ISSUE8183_GATE_EVIDENCE=$taskDir"
        Write-Output "ISSUE8183_GATE_EXIT=$result"
    } finally { Pop-Location }
} finally {
    # Only the just-created instance; all files and schemas are preserved for review.
    $env:MYSQL_PWD = $adminPassword
    if (!$proc.HasExited) {
        & "$bin/mysqladmin.exe" --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root shutdown 2>$null
        $null = $proc.WaitForExit(10000)
    }
    $env:MYSQL_PWD = $previousPwd
}
exit $result
