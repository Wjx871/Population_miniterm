param(
    [ValidateSet('Initialize', 'Migrate', 'Repeat', 'All')]
    [string]$Mode = 'All',
    [string]$MySqlClient = 'mysql',
    [string]$HistoricalSql,
    [switch]$ResetTestDatabase
)

$ErrorActionPreference = 'Stop'
$allowedDatabases = @('population_miniterm_fresh', 'population_miniterm_upgrade', 'population_miniterm_repeat')

if (-not $env:DB_URL -or -not $env:DB_USERNAME) {
    throw 'DB_URL and DB_USERNAME must be set. DB_PASSWORD may be empty for an isolated test instance.'
}
if ($env:DB_URL -notmatch '^jdbc:mysql://([^/:?]+)(?::(\d+))?/([^?]+)') {
    throw 'DB_URL must use jdbc:mysql://host[:port]/database format.'
}
$hostName = $Matches[1]
$port = if ($Matches[2]) { $Matches[2] } else { '3306' }
$database = $Matches[3]
if ($database -notin $allowedDatabases) {
    throw "Refusing to operate on database '$database'. Allowed test databases: $($allowedDatabases -join ', ')."
}
$client = (Get-Command $MySqlClient -ErrorAction Stop).Source
$env:MYSQL_PWD = $env:DB_PASSWORD
$common = @("--host=$hostName", "--port=$port", "--user=$($env:DB_USERNAME)", '--default-character-set=utf8mb4')
$temporaryFiles = [System.Collections.Generic.List[string]]::new()

function Invoke-MySql([string]$Sql) {
    & $client @common --execute=$Sql
    if ($LASTEXITCODE -ne 0) { throw "mysql exited with code $LASTEXITCODE" }
}

function New-DatabaseCopy([string]$Source) {
    $path = Join-Path $env:TEMP ("population-mysql-verify-{0}.sql" -f [guid]::NewGuid())
    $content = [IO.File]::ReadAllText((Resolve-Path $Source), [Text.Encoding]::UTF8)
    $content = $content.Replace('population_miniterm', $database)
    [IO.File]::WriteAllText($path, $content, [Text.UTF8Encoding]::new($false))
    $temporaryFiles.Add($path)
    return $path.Replace('\', '/')
}

try {
    Write-Host "[mysql] client: $(& $client --version)"
    Write-Host "[mysql] target: $hostName`:$port/$database"
    if ($ResetTestDatabase) {
        Write-Host '[mysql] resetting explicitly allowed test database'
        Invoke-MySql "DROP DATABASE IF EXISTS ``$database``; CREATE DATABASE ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
    }

    if ($Mode -eq 'Initialize' -or ($Mode -eq 'All' -and -not $HistoricalSql)) {
        $source = 'doc/database/population_miniterm.sql'
        Write-Host "[initialize] $source"
        $copy = New-DatabaseCopy $source
        Invoke-MySql "SOURCE $copy;"
    }

    if ($Mode -in @('Migrate', 'Repeat') -or ($Mode -eq 'All' -and $HistoricalSql)) {
        if ($Mode -eq 'All') {
            Write-Host "[initialize historical] $HistoricalSql"
            $copy = New-DatabaseCopy $HistoricalSql
            Invoke-MySql "SOURCE $copy;"
        }
        $passes = if ($Mode -eq 'Repeat' -or $Mode -eq 'All') { 2 } else { 1 }
        for ($pass = 1; $pass -le $passes; $pass++) {
            Write-Host "[migrate] pass $pass"
            Get-ChildItem 'doc/database/migrations/V4_*.sql' | Sort-Object Name | ForEach-Object {
                Write-Host "[migrate] $($_.Name)"
                $copy = New-DatabaseCopy $_.FullName
                Invoke-MySql "SOURCE $copy;"
            }
        }
    }

    Invoke-MySql "USE ``$database``; SELECT VERSION() AS mysql_version; SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema=DATABASE(); SELECT COUNT(*) AS permission_count FROM sys_permission; SELECT COUNT(*) AS phase_procedure_count FROM information_schema.routines WHERE routine_schema=DATABASE() AND routine_name LIKE 'phase%';"
    Write-Host '[result] MySQL verification completed successfully.'
} finally {
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
    foreach ($file in $temporaryFiles) { Remove-Item -LiteralPath $file -Force -ErrorAction SilentlyContinue }
}
