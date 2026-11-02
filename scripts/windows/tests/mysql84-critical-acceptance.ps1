[CmdletBinding()]
param(
    [string]$MySqlClient = 'mysql'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$url = $env:MYSQL84_GATE_DB_URL
$appUser = $env:MYSQL84_GATE_DB_USERNAME
$appPassword = $env:MYSQL84_GATE_DB_PASSWORD
$adminUser = $env:MYSQL84_GATE_ADMIN_USERNAME
$adminPassword = $env:MYSQL84_GATE_ADMIN_PASSWORD
$apiTestPassword = $env:MYSQL84_GATE_API_TEST_PASSWORD
if ([string]::IsNullOrWhiteSpace($url) -or [string]::IsNullOrWhiteSpace($appUser) -or
    [string]::IsNullOrWhiteSpace($appPassword) -or [string]::IsNullOrWhiteSpace($adminUser) -or
    [string]::IsNullOrWhiteSpace($adminPassword) -or [string]::IsNullOrWhiteSpace($apiTestPassword)) {
    throw 'Set MYSQL84_GATE_DB_URL, MYSQL84_GATE_DB_USERNAME/PASSWORD, MYSQL84_GATE_ADMIN_USERNAME/PASSWORD, and MYSQL84_GATE_API_TEST_PASSWORD in the current process environment.'
}

$match = [regex]::Match($url, '^jdbc:mysql://(?<host>[^/:?]+):(?<port>\d+)/(?<database>[A-Za-z0-9_]+)\?(?<options>.*)$', [Text.RegularExpressions.RegexOptions]::IgnoreCase)
if (-not $match.Success -or $match.Groups['host'].Value -ne '127.0.0.1') {
    throw 'MYSQL84_GATE_DB_URL must explicitly use 127.0.0.1, a port, and a database.'
}
$port = [int]$match.Groups['port'].Value
$database = $match.Groups['database'].Value
if ($port -eq 3306) { throw 'MySQL gate refuses the default/course MySQL port 3306.' }
if ($database -notmatch '^population_miniterm_mysql_gate_[a-zA-Z0-9_]+$' -or $database -eq 'population_miniterm') {
    throw 'MySQL gate requires an explicitly named population_miniterm_mysql_gate_* test database.'
}
if ($match.Groups['options'].Value -notmatch '(?i)(?:^|&)createDatabaseIfNotExist=false(?:&|$)') {
    throw 'MYSQL84_GATE_DB_URL must explicitly set createDatabaseIfNotExist=false.'
}
if ($appUser -eq 'root') { throw 'The application test user must not be root.' }
if ($adminUser -eq $appUser) { throw 'Use a separate instance administration account for test-only trigger setup.' }

$client = (Get-Command $MySqlClient -ErrorAction Stop).Source
$oldMysqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $appPassword, 'Process')
    $identitySql = 'SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER();'
    $identityLines = @($identitySql | & $client --protocol=TCP --host=127.0.0.1 --port=$port `
        "--user=$appUser" "--database=$database" --batch --skip-column-names)
    if ($LASTEXITCODE -ne 0 -or $identityLines.Count -ne 1) { throw 'Could not verify the selected isolated MySQL instance identity.' }
    $identity = ([string]$identityLines[0]).Split([char]9)
    if ($identity.Count -ne 6 -or $identity[0] -notmatch '^8\.4\.' -or [int]$identity[1] -ne $port -or
        $identity[2] -ne '127.0.0.1' -or $identity[4] -ne $database -or $identity[5] -notmatch ('^' + [regex]::Escape($appUser) + '@')) {
        throw 'Connected MySQL identity does not match the requested loopback 8.4 test instance/schema/user.'
    }

    $repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
    Push-Location $repo
    try {
        & .\mvnw.cmd '-Dtest=MySql84CriticalAcceptanceIT' test
        if ($LASTEXITCODE -ne 0) { throw 'MySQL 8.4 critical acceptance test class failed.' }
    } finally {
        Pop-Location
    }
} finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldMysqlPassword, 'Process')
}
