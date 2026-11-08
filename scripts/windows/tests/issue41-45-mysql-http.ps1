[CmdletBinding()]
param([int]$Port = 34145)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($Port -eq 3306 -or $Port -lt 1024 -or $Port -gt 65535) { throw 'Require an unused non-default local port.' }
if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) { throw 'Port already in use; refusing to touch that instance.' }
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$stamp = [guid]::NewGuid().ToString('N')
$privateRoot = Join-Path $env:USERPROFILE ('.codex/tmp/issue41-45-mysql-' + $stamp)
$database = 'population_miniterm_mysql_gate_i4145_' + $stamp.Substring(0, 10)
$appPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$adminPassword = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
$bin = 'C:/Program Files/MySQL/MySQL Server 8.4/bin'
$mysql = Join-Path $bin 'mysql.exe'
$mysqld = Join-Path $bin 'mysqld.exe'
New-Item -ItemType Directory -Path $privateRoot | Out-Null
$oldEncoding = [Console]::OutputEncoding
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$OutputEncoding = [Text.UTF8Encoding]::new($false)
$server = $null
$environmentNames = @('MYSQL_PWD','SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','SPRING_DATASOURCE_DRIVER_CLASS_NAME','SPRING_SQL_INIT_MODE','MYSQL84_GATE_DB_URL','MYSQL84_GATE_DB_USERNAME','MYSQL84_GATE_DB_PASSWORD','MYSQL84_GATE_ADMIN_USERNAME','MYSQL84_GATE_ADMIN_PASSWORD','JAVA_TOOL_OPTIONS')
$priorEnvironment = @{}
foreach ($name in $environmentNames) { $priorEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
function Invoke-Sql([string]$Sql) {
    $Sql | & $mysql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --default-character-set=utf8mb4 --batch
    if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL SQL execution failed.' }
}
try {
    & $mysqld --no-defaults --initialize-insecure "--basedir=$bin/.." "--datadir=$privateRoot/data" "--log-error=$privateRoot/initialize.log"
    if ($LASTEXITCODE -ne 0) { throw 'New data-directory initialization failed.' }
    $server = Start-Process -FilePath $mysqld -ArgumentList @('--no-defaults', ('--basedir="' + $bin + '/.."'), ('--datadir="' + $privateRoot + '/data"'), '--bind-address=127.0.0.1', "--port=$Port", '--mysqlx=0', ('--log-error="' + $privateRoot + '/server.log"')) -PassThru -WindowStyle Hidden
    $env:MYSQL_PWD = ''
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        & $mysql --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --execute='SELECT 1' *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 250
    }
    if (!$ready) { throw 'The newly created MySQL instance did not start.' }
    Invoke-Sql "ALTER USER 'root'@'localhost' IDENTIFIED BY '$adminPassword';"
    $env:MYSQL_PWD = $adminPassword
    Invoke-Sql 'SELECT @@version,@@port,@@bind_address,@@transaction_isolation;'
    foreach ($source in @('doc/database/population_miniterm.sql','doc/database/demo_data.sql','doc/database/demo_data_household_migration.sql','doc/database/migrations/V4_011_application_return.sql','doc/database/migrations/V4_012_dashboard_viewer_scope.sql','doc/database/migrations/V4_013_person_idcard_image.sql','doc/database/migrations/V4_015_ai_precheck_async.sql','doc/database/migrations/V4_016_s4a_entity_resolution.sql','doc/database/migrations/V4_017_s4a_resolution_retention_indexes.sql','doc/database/migrations/V4_018_s4b_confirm_draft_binding.sql')) {
        $sql = [IO.File]::ReadAllText((Join-Path $repo $source), [Text.Encoding]::UTF8)
        $sql = [regex]::Replace($sql, '\bpopulation_miniterm\b', $database)
        if ($source -ne 'doc/database/population_miniterm.sql') { $sql = "USE $database;`n$sql" }
        Invoke-Sql $sql
        Write-Output "INITIALIZED $source in $database"
    }
    Invoke-Sql "CREATE USER 'i4145_app'@'127.0.0.1' IDENTIFIED BY '$appPassword'; GRANT SELECT,INSERT,UPDATE,DELETE ON $database.* TO 'i4145_app'@'127.0.0.1';"
    # Private runtime configuration is never emitted or committed. No existing DB/data directory is reused.
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:$Port/$database`?createDatabaseIfNotExist=false&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
    $env:SPRING_DATASOURCE_USERNAME = 'i4145_app'
    $env:SPRING_DATASOURCE_PASSWORD = $appPassword
    $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
    $env:SPRING_SQL_INIT_MODE = 'never'
    $env:MYSQL84_GATE_DB_URL = $env:SPRING_DATASOURCE_URL
    $env:MYSQL84_GATE_DB_USERNAME = 'i4145_app'
    $env:MYSQL84_GATE_DB_PASSWORD = $appPassword
    $env:MYSQL84_GATE_ADMIN_USERNAME = 'root'
    $env:MYSQL84_GATE_ADMIN_PASSWORD = $adminPassword
    $env:JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8'
    Write-Output "ISOLATION database=$database port=$Port dataDirectory=$privateRoot app=i4145_app schema-scoped-DML=true"
    Push-Location $repo
    try {
        & ./mvnw.cmd '-Dtest=Issue4145KeyPopulationScopeHttpIntegrationTest,MySql84KeyPopulationC2RepeatableReadIT' test
        $testExit = $LASTEXITCODE
    } finally { Pop-Location }
    Invoke-Sql "USE $database; SHOW INDEX FROM residence; SHOW INDEX FROM key_population_history; SHOW INDEX FROM key_population_application;"
    if ($testExit -ne 0) { throw "MySQL HTTP suite failed (Maven exit $testExit)." }
} finally {
    if ($null -ne $server -and !$server.HasExited) {
        $env:MYSQL_PWD = $adminPassword
        & (Join-Path $bin 'mysqladmin.exe') --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root shutdown
        if ($LASTEXITCODE -ne 0) { Stop-Process -Id $server.Id }
        Write-Output 'Stopped only the instance created by this script; data directory retained.'
    }
    [Console]::OutputEncoding = $oldEncoding
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $priorEnvironment[$name], 'Process') }
}
