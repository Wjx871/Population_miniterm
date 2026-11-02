[CmdletBinding()]
param(
    [ValidateSet('Fresh', 'Verify')]
    [string]$Mode = 'Fresh',
    [string]$MySqlClient = 'mysql',
    [switch]$AllowExistingEmptyDatabase
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$databaseUrl = $env:MYSQL84_GATE_DB_URL
$appUser = $env:MYSQL84_GATE_DB_USERNAME
$appPassword = $env:MYSQL84_GATE_DB_PASSWORD
$adminUser = $env:MYSQL84_GATE_ADMIN_USERNAME
$adminPassword = $env:MYSQL84_GATE_ADMIN_PASSWORD
foreach ($name in @('MYSQL84_GATE_DB_URL','MYSQL84_GATE_DB_USERNAME','MYSQL84_GATE_DB_PASSWORD',
        'MYSQL84_GATE_ADMIN_USERNAME','MYSQL84_GATE_ADMIN_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Set $name in the current process environment."
    }
}

$url = [regex]::Match($databaseUrl,
    '^jdbc:mysql://(?<host>[^/:?]+):(?<port>\d+)/(?<database>[A-Za-z0-9_]+)\?(?<options>.*)$',
    [Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $url.Success -or $url.Groups['host'].Value -ne '127.0.0.1') {
    throw 'MYSQL84_GATE_DB_URL must explicitly use 127.0.0.1, a port, and a schema.'
}
$port = [int]$url.Groups['port'].Value
$database = $url.Groups['database'].Value
if ($port -eq 3306) { throw 'The MySQL gate refuses the existing default/course MySQL port 3306.' }
if ($database -notmatch '^population_miniterm_mysql_gate_[A-Za-z0-9_]+$' -or $database -eq 'population_miniterm') {
    throw 'Use an explicitly named population_miniterm_mysql_gate_* schema.'
}
if ($url.Groups['options'].Value -notmatch '(?i)(?:^|&)createDatabaseIfNotExist=false(?:&|$)') {
    throw 'MYSQL84_GATE_DB_URL must explicitly set createDatabaseIfNotExist=false.'
}
if ($appUser -eq 'root' -or $appUser -notmatch '^[A-Za-z0-9_]+$') {
    throw 'The application test account must be non-root and use only letters, digits, or underscore.'
}
if ($adminUser -eq $appUser) { throw 'Use a separate instance administrator account.' }
if ($appPassword -notmatch '^[A-Fa-f0-9]{32,128}$') {
    throw 'Use a private random hexadecimal application-test password (32-128 characters).'
}

$mysql = (Get-Command $MySqlClient -ErrorAction Stop).Source
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$oldMysqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
$oldOutputEncoding = [Console]::OutputEncoding
try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $adminPassword, 'Process')
    $identitySql = 'SELECT @@version,@@port,@@bind_address,@@transaction_isolation,CURRENT_USER();'
    $identityLines = @($identitySql | & $mysql --protocol=TCP --host=127.0.0.1 --port=$port `
        "--user=$adminUser" --batch --skip-column-names)
    if ($LASTEXITCODE -ne 0 -or $identityLines.Count -ne 1) { throw 'Could not verify the selected isolated MySQL instance.' }
    $identity = ([string]$identityLines[0]).Split([char]9)
    if ($identity.Count -ne 5 -or $identity[0] -notmatch '^8\.4\.' -or [int]$identity[1] -ne $port -or
        $identity[2] -ne '127.0.0.1' -or $identity[4] -notmatch ('^' + [regex]::Escape($adminUser) + '@')) {
        throw 'Connected instance identity does not match the requested loopback MySQL 8.4 endpoint.'
    }

    $init = Join-Path $repo 'scripts/windows/init_database.ps1'
    if ($Mode -eq 'Fresh') {
        $freshArgs = @{
            Mode = 'Fresh'; HostName = '127.0.0.1'; Port = $port; Username = $adminUser
            Database = $database; Password = $adminPassword; MySqlClient = $MySqlClient; DemoData = $true
        }
        if ($AllowExistingEmptyDatabase) { $freshArgs.AllowExistingEmptyDatabase = $true }
        & $init @freshArgs
        if ($LASTEXITCODE -ne 0) { throw 'Fresh schema initialization and its built-in Verify failed.' }

        [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
        foreach ($name in @('V4_011_application_return.sql','V4_012_dashboard_viewer_scope.sql','V4_013_person_idcard_image.sql')) {
            $path = Join-Path $repo ('doc/database/migrations/' + $name)
            $source = [IO.File]::ReadAllText($path,[Text.Encoding]::UTF8)
            $usePattern = '(?im)^\s*USE\s+population_miniterm\s*;'
            if ([regex]::Matches($source,$usePattern).Count -ne 1) { throw "Unexpected database target statement in $name." }
            $targetSql = [regex]::Replace($source,$usePattern,('USE ' + $database + ';'),1)
            $output = $targetSql | & $mysql --protocol=TCP --host=127.0.0.1 --port=$port `
                "--user=$adminUser" "--database=$database" --default-character-set=utf8mb4 2>&1
            if ($LASTEXITCODE -ne 0) { throw "Applying $name failed: $($output | Select-Object -Last 1)" }
            Write-Output "PASS applied $name to the named isolated schema"
        }
    }

    & $init -Mode Verify -HostName 127.0.0.1 -Port $port -Username $adminUser -Database $database -Password $adminPassword -MySqlClient $MySqlClient
    if ($LASTEXITCODE -ne 0) { throw 'Final MySQL Verify failed.' }

    $identityCheck = "SELECT (SELECT data_scope FROM sys_role r JOIN sys_user u ON u.role_id=r.role_id WHERE u.username='viewer'),(SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$database' AND table_name='person_idcard_image');"
    $identityResult = $identityCheck | & $mysql --protocol=TCP --host=127.0.0.1 --port=$port `
        "--user=$adminUser" "--database=$database" --default-character-set=utf8mb4 --batch --skip-column-names
    if ($LASTEXITCODE -ne 0) { throw 'Current-schema verification query failed.' }
    $columns = ([string]$identityResult).Trim().Split([char]9)
    if ($columns.Count -ne 2 -or $columns[0] -ne 'ALL' -or [int]$columns[1] -ne 1) {
        throw 'V4_012/V4_013 contract check failed: expected QUERY_VIEWER=ALL and person_idcard_image present.'
    }

    if ($Mode -eq 'Fresh') {
        $appSql = "CREATE USER IF NOT EXISTS '$appUser'@'127.0.0.1' IDENTIFIED BY '$appPassword'; ALTER USER '$appUser'@'127.0.0.1' IDENTIFIED BY '$appPassword'; GRANT SELECT,INSERT,UPDATE,DELETE ON $database.* TO '$appUser'@'127.0.0.1';"
        $appResult = $appSql | & $mysql --protocol=TCP --host=127.0.0.1 --port=$port `
            "--user=$adminUser" --default-character-set=utf8mb4 --batch 2>&1
        if ($LASTEXITCODE -ne 0) { throw 'Creating the isolated non-root application account failed; the server output is suppressed to avoid exposing credentials.' }
    } else {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD',$appPassword,'Process')
        $appIdentitySql = 'SELECT DATABASE(),CURRENT_USER();'
        $appIdentity = @($appIdentitySql | & $mysql --protocol=TCP --host=127.0.0.1 --port=$port `
            "--user=$appUser" "--database=$database" --batch --skip-column-names)
        if ($LASTEXITCODE -ne 0 -or $appIdentity.Count -ne 1) { throw 'The non-root application account cannot verify the named schema.' }
        $appColumns = ([string]$appIdentity[0]).Split([char]9)
        if ($appColumns.Count -ne 2 -or $appColumns[0] -ne $database -or
            $appColumns[1] -notmatch ('^' + [regex]::Escape($appUser) + '@')) {
            throw 'The application account identity does not match the named test schema.'
        }
        [Environment]::SetEnvironmentVariable('MYSQL_PWD',$adminPassword,'Process')
    }
    Write-Output "PASS prepared schema: MySQL $($identity[0]), $database, 127.0.0.1:$port, transaction isolation $($identity[3])"
    Write-Output "PASS application account: $appUser (non-root, schema-scoped DML privileges)"
} finally {
    [Console]::OutputEncoding = $oldOutputEncoding
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldMysqlPassword, 'Process')
}
