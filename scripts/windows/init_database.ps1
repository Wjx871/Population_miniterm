[CmdletBinding()]
param(
    [ValidateSet('Fresh', 'Verify')]
    [string]$Mode = 'Verify',
    [string]$MySqlClient = 'mysql',
    [string]$HostName,
    [int]$Port,
    [string]$Username,
    [string]$Database,
    [string]$Password,
    [switch]$DemoData,
    [switch]$AllowExistingEmptyDatabase,
    [switch]$AllowCourseDatabase
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$courseDatabase = 'population_miniterm'
$defaultDatabase = 'population_miniterm_integration_verify'
$localEnvPath = Join-Path $root 'start.local.env'
$sourceSql = Join-Path $root 'doc\database\population_miniterm.sql'
$checkSql = Join-Path $root 'doc\database\check_backend_v1.sql'
$demoSqlFiles = @(
    (Join-Path $root 'doc\database\demo_data.sql'),
    (Join-Path $root 'doc\database\demo_data_household_migration.sql')
)

function Read-LocalEnv {
    param([string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $values }
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
        $parts = $line.Split('=', 2)
        $values[$parts[0].Trim()] = $parts[1]
    }
    return $values
}

$localEnv = Read-LocalEnv $localEnvPath
$providedParameters = $PSBoundParameters
function Resolve-Setting {
    param([string]$Name, [string]$ExplicitValue, [string]$DefaultValue)
    if ($providedParameters.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($ExplicitValue)) { return $ExplicitValue }
    $fromProcess = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($fromProcess)) { return $fromProcess }
    if ($localEnv.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($localEnv[$Name])) { return $localEnv[$Name] }
    return $DefaultValue
}

$HostName = Resolve-Setting -Name 'HostName' -ExplicitValue $HostName -DefaultValue '127.0.0.1'
$portText = Resolve-Setting -Name 'Port' -ExplicitValue ([string]$Port) -DefaultValue '3306'
$Port = [int]$portText
$Username = Resolve-Setting -Name 'Username' -ExplicitValue $Username -DefaultValue 'root'
$Database = Resolve-Setting -Name 'Database' -ExplicitValue $Database -DefaultValue $defaultDatabase
$Password = Resolve-Setting -Name 'Password' -ExplicitValue $Password -DefaultValue ''
if ([string]::IsNullOrEmpty($Password)) {
    $processPassword = [Environment]::GetEnvironmentVariable('DB_PASSWORD')
    $Password = if (-not [string]::IsNullOrWhiteSpace($processPassword)) { $processPassword } elseif ($localEnv.ContainsKey('DB_PASSWORD')) { $localEnv['DB_PASSWORD'] } else { '' }
}

if ($Database -notmatch '^[A-Za-z0-9_]+$') { throw '目标数据库名只能包含字母、数字和下划线。' }
if (-not (Test-Path -LiteralPath $sourceSql) -or -not (Test-Path -LiteralPath $checkSql)) { throw '未找到数据库初始化或校验 SQL。' }
if (-not (Get-Command $MySqlClient -ErrorAction SilentlyContinue)) { throw "未找到 MySQL 客户端：$MySqlClient" }

function Invoke-MySql {
    param([string]$Sql, [string]$TargetDatabase, [switch]$Batch)
    $previousPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        if (-not [string]::IsNullOrEmpty($Password)) { [Environment]::SetEnvironmentVariable('MYSQL_PWD', $Password, 'Process') }
        $args = @('--protocol=TCP', "--host=$HostName", "--port=$Port", "--user=$Username", '--default-character-set=utf8mb4')
        if ($Batch) { $args += @('--batch', '--skip-column-names') }
        if ($TargetDatabase) { $args += $TargetDatabase }
        $output = $Sql | & $MySqlClient @args 2>&1
        if ($LASTEXITCODE -ne 0) { throw "MySQL 命令失败：$($output | Select-Object -Last 1)" }
        return @($output)
    } finally {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousPassword, 'Process')
    }
}

function Invoke-MySqlFile {
    param([string]$Path, [string]$TargetDatabase)
    $previousPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        if (-not [string]::IsNullOrEmpty($Password)) { [Environment]::SetEnvironmentVariable('MYSQL_PWD', $Password, 'Process') }
        $args = @('--protocol=TCP', "--host=$HostName", "--port=$Port", "--user=$Username", '--default-character-set=utf8mb4')
        if ($TargetDatabase) { $args += $TargetDatabase }
        Get-Content -LiteralPath $Path -Raw | & $MySqlClient @args
        if ($LASTEXITCODE -ne 0) { throw "执行 SQL 失败：$([IO.Path]::GetFileName($Path))" }
    } finally {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousPassword, 'Process')
    }
}

function Test-DatabaseExists {
    $result = Invoke-MySql -Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$Database';" -Batch
    return ([int]$result[-1] -gt 0)
}

function Get-TableCount {
    $result = Invoke-MySql -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database';" -Batch
    return [int]$result[-1]
}

function Assert-MySqlZeroResult {
    param([string]$Sql, [string]$Description)
    $rows = Invoke-MySql -Sql $Sql -TargetDatabase $Database -Batch
    $failed = $rows | Where-Object { $_ -match '\t' -and ($_ -split '\t')[-1] -ne '0' }
    if ($failed) { throw "$Description 失败：$($failed -join '; ')" }
}

function Invoke-Verify {
    if (-not (Test-DatabaseExists)) { throw "目标数据库不存在：$Database" }
    $requiredTables = @('person','household','residence','business_application','sys_approval_request','cancellation_record','household_archive','floating_registration_application','residence_permit','data_export_request','data_export_log','admin_region','data_dictionary','key_population','key_population_application','key_population_history','operation_log','login_log')
    $requiredRoles = @('QUERY_VIEWER','POPULATION_MANAGER','HOUSEHOLD_MANAGER','APPROVER','SYSTEM_ADMIN')
    $requiredPermissions = @('application:view','migration:view','cancellation:view','floating:view','residence-permit:view','data:export:normal','data:export:log:view','region:view','dictionary:view','certificate:view','key-population:view','log:view')
    $requiredIndexes = @('idx_key_population_query','idx_household_member_person_status','idx_household_member_household_status','idx_person_query','idx_operation_log_query','idx_migration_in_query','idx_migration_out_query')

    foreach ($table in $requiredTables) {
        $count = Invoke-MySql -Sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='$table';" -Batch
        if ([int]$count[-1] -ne 1) { throw "缺少必需表：$table" }
    }
    foreach ($role in $requiredRoles) {
        $count = Invoke-MySql -Sql "SELECT COUNT(*) FROM sys_role WHERE role_code='$role' AND status='ENABLED';" -TargetDatabase $Database -Batch
        if ([int]$count[-1] -ne 1) { throw "缺少启用角色：$role" }
    }
    foreach ($permission in $requiredPermissions) {
        $count = Invoke-MySql -Sql "SELECT COUNT(*) FROM sys_permission WHERE permission_code='$permission' AND status='ENABLED';" -TargetDatabase $Database -Batch
        if ([int]$count[-1] -ne 1) { throw "缺少启用权限：$permission" }
    }
    foreach ($index in $requiredIndexes) {
        $count = Invoke-MySql -Sql "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$Database' AND index_name='$index';" -Batch
        if ([int]$count[-1] -lt 1) { throw "缺少必需索引：$index" }
    }
    Assert-MySqlZeroResult -Sql (Get-Content -LiteralPath $checkSql -Raw) -Description '数据库一致性检查'
    Write-Host "[PASS] 数据库 Verify：$Database（结构、角色、权限、V4_010 相关索引和一致性检查均通过）"
}

if ($Mode -eq 'Verify') { Invoke-Verify; exit 0 }

# Fresh 只能用于新建或明确允许的空验证库，绝不承担旧库升级职责。
if ($Database -eq $courseDatabase) {
    if (-not $AllowCourseDatabase) { throw "Fresh 默认拒绝课程库 $courseDatabase。仅在确认可丢弃时传入 -AllowCourseDatabase。" }
    $confirmation = Read-Host "将初始化课程库 $courseDatabase。输入库名以再次确认"
    if ($confirmation -ne $courseDatabase) { throw '课程库二次确认未通过，已停止。' }
}

$databaseExists = Test-DatabaseExists
if ($databaseExists) {
    $tableCount = Get-TableCount
    if ($tableCount -gt 0) { throw "Fresh 拒绝非空数据库 $Database（发现 $tableCount 张表）；不得将 Fresh 用于旧库升级。" }
    if (-not $AllowExistingEmptyDatabase) { throw "Fresh 拒绝已存在的空数据库 $Database；如确认可使用，请显式传入 -AllowExistingEmptyDatabase。" }
}

$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) ("population-init-" + [guid]::NewGuid().ToString('N'))
$temporarySql = Join-Path $temporaryDirectory 'population_miniterm.generated.sql'
try {
    New-Item -ItemType Directory -Path $temporaryDirectory -Force | Out-Null
    $source = Get-Content -LiteralPath $sourceSql -Raw
    $replacement = "CREATE DATABASE IF NOT EXISTS $Database`r`n    DEFAULT CHARACTER SET utf8mb4`r`n    DEFAULT COLLATE utf8mb4_unicode_ci;`r`n`r`nUSE $Database;"
    $generated = [regex]::Replace($source, '(?ms)\ACREATE DATABASE IF NOT EXISTS population_miniterm\s+DEFAULT CHARACTER SET utf8mb4\s+DEFAULT COLLATE utf8mb4_unicode_ci;\s+USE population_miniterm;', $replacement, 1)
    if ($generated -eq $source -or $generated -notmatch "USE $Database;") { throw '无法安全重写源 SQL 的数据库头部，已停止。' }
    Set-Content -LiteralPath $temporarySql -Value $generated -Encoding utf8NoBOM
    Invoke-MySqlFile -Path $temporarySql
    if ($DemoData) {
        foreach ($demoSql in $demoSqlFiles) {
            if (-not (Test-Path -LiteralPath $demoSql)) { throw "缺少演示数据脚本：$demoSql" }
            Invoke-MySqlFile -Path $demoSql -TargetDatabase $Database
        }
    }
    Invoke-Verify
    Write-Host "[PASS] Fresh 完成：$Database"
} finally {
    # 仅删除本次运行创建的临时目录；绝不修改源 SQL。
    if (Test-Path -LiteralPath $temporaryDirectory) { Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force }
}
