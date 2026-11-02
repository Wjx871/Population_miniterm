[CmdletBinding()]
param(
    [string]$ApiBaseUrl,
    [string]$MySqlClient = 'mysql'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$databaseUrl = $env:DB_URL
$databaseUser = $env:DB_USERNAME
$databasePassword = $env:DB_PASSWORD
$testPassword = $env:MYSQL84_GATE_API_TEST_PASSWORD
$serverPort = $env:SERVER_PORT
if ([string]::IsNullOrWhiteSpace($databaseUrl)) { throw 'Set DB_URL for the isolated test instance; the script will not infer a default database.' }
$urlMatch = [regex]::Match(
    $databaseUrl,
    '^jdbc:mysql://(?<host>[^/:?]+):(?<port>\d+)/(?<database>[A-Za-z0-9_]+)\?(?<query>.*)$',
    [Text.RegularExpressions.RegexOptions]::IgnoreCase)

if (-not $urlMatch.Success) { throw 'DB_URL must be a complete jdbc:mysql://127.0.0.1:port/database?... URL.' }
if ($urlMatch.Groups['host'].Value -ne '127.0.0.1') { throw 'MySQL gate refuses non-loopback database hosts.' }
if ([int]$urlMatch.Groups['port'].Value -eq 3306) { throw 'MySQL gate refuses the default/course MySQL port 3306.' }
$databaseName = $urlMatch.Groups['database'].Value
$databasePort = [int]$urlMatch.Groups['port'].Value
if ($databaseName -notmatch '^population_miniterm_mysql_gate_[a-zA-Z0-9_]+$' -or $databaseName -eq 'population_miniterm') {
    throw 'MySQL gate requires an explicitly named population_miniterm_mysql_gate_* test database.'
}
if ($urlMatch.Groups['query'].Value -notmatch '(?i)(?:^|&)createDatabaseIfNotExist=false(?:&|$)') {
    throw 'DB_URL must explicitly set createDatabaseIfNotExist=false.'
}
if ([string]::IsNullOrWhiteSpace($databaseUser) -or $databaseUser -eq 'root') {
    throw 'Use the dedicated non-root test database account.'
}
if ([string]::IsNullOrWhiteSpace($databasePassword) -or [string]::IsNullOrWhiteSpace($testPassword)) {
    throw 'Set DB_PASSWORD and MYSQL84_GATE_API_TEST_PASSWORD in the current process environment.'
}
if ($serverPort -notmatch '^\d+$') { throw 'Set SERVER_PORT to the explicitly configured API loopback port.' }
if ($env:REDIS_ENABLED -ne 'false' -or $env:POLICY_AI_ENABLED -ne 'false') {
    throw 'The MySQL gate requires REDIS_ENABLED=false and POLICY_AI_ENABLED=false.'
}
if ([string]::IsNullOrWhiteSpace($env:APP_UPLOAD_DIR) -or [string]::IsNullOrWhiteSpace($env:EXPORT_DIR)) {
    throw 'Set isolated APP_UPLOAD_DIR and EXPORT_DIR paths before starting the API.'
}

$apiBase = if (-not [string]::IsNullOrWhiteSpace($ApiBaseUrl)) { $ApiBaseUrl } elseif ($env:MYSQL84_GATE_API_BASE_URL) { $env:MYSQL84_GATE_API_BASE_URL } else { "http://127.0.0.1:$serverPort" }
$apiUri = [uri]$apiBase
if ($apiUri.Scheme -ne 'http' -or $apiUri.Host -ne '127.0.0.1' -or $apiUri.Port -ne [int]$serverPort) {
    throw 'API smoke test only accepts the explicitly configured http://127.0.0.1:$SERVER_PORT endpoint.'
}
$ApiBaseUrl = $apiBase

$health = Invoke-RestMethod -Uri ($ApiBaseUrl.TrimEnd('/') + '/api/health') -TimeoutSec 15
if ($health.code -ne 200 -or $health.data.database -ne 'UP' -or $health.data.redisEnabled -ne $false) {
    throw 'Health endpoint did not report MySQL UP with Redis disabled.'
}

$loginBody = @{ username = 'viewer'; password = $testPassword } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Uri ($ApiBaseUrl.TrimEnd('/') + '/api/auth/login') `
    -Method Post -ContentType 'application/json' -Body $loginBody -TimeoutSec 15
$user = $login.data.user
if (-not $login.data.token -or $user.roleCode -ne 'QUERY_VIEWER' -or $user.dataScope -ne 'ALL' -or
    $user.permissions -notcontains 'statistics:view') {
    throw 'Viewer login did not load the expected V4_012 ALL scope and statistics permission.'
}
$headers = @{ Authorization = "Bearer $($login.data.token)" }
$overview = (Invoke-RestMethod -Uri ($ApiBaseUrl.TrimEnd('/') + '/api/statistics/overview?periodDays=30&expiryDays=30') `
    -Headers $headers -TimeoutSec 20).data
$floating = (Invoke-RestMethod -Uri ($ApiBaseUrl.TrimEnd('/') + '/api/statistics/floating-population') `
    -Headers $headers -TimeoutSec 20).data
$expiry = (Invoke-RestMethod -Uri ($ApiBaseUrl.TrimEnd('/') + '/api/statistics/certificate-expiry?days=30') `
    -Headers $headers -TimeoutSec 20).data
$charts = (Invoke-RestMethod -Uri ($ApiBaseUrl.TrimEnd('/') + '/api/statistics/charts?days=30&limit=7') `
    -Headers $headers -TimeoutSec 20).data

$client = (Get-Command $MySqlClient -ErrorAction Stop).Source
$previousMysqlPassword = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
try {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $databasePassword, 'Process')
    $identitySql = 'SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER();'
    $identityOutput = @($identitySql | & $client --protocol=TCP --host=127.0.0.1 --port=$databasePort `
        "--user=$databaseUser" "--database=$databaseName" --batch --skip-column-names)
    if ($LASTEXITCODE -ne 0 -or $identityOutput.Count -ne 1) { throw 'Could not verify selected MySQL instance identity.' }
    $identity = ([string]$identityOutput[0]).Split([char]9)
    if ($identity.Count -ne 6 -or $identity[0] -notmatch '^8\.4\.' -or [int]$identity[1] -ne $databasePort -or
        $identity[2] -ne '127.0.0.1' -or $identity[4] -ne $databaseName -or $identity[5] -notmatch ('^' + [regex]::Escape($databaseUser) + '@')) {
        throw 'Connected MySQL identity does not match the requested loopback 8.4 test instance/schema/user.'
    }
    $sql = @"
SELECT
 (SELECT COUNT(*) FROM person p JOIN residence r ON r.person_id=p.person_id AND r.status='ACTIVE'),
 (SELECT COUNT(*) FROM floating_population f WHERE f.status='ACTIVE' AND f.current_flag=1),
 (SELECT COUNT(*) FROM residence_permit rp WHERE rp.status='ACTIVE' AND rp.current_flag=1),
 (SELECT COUNT(*) FROM residence_permit rp WHERE rp.status='ACTIVE' AND rp.current_flag=1
    AND rp.valid_until BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE,INTERVAL 30 DAY)),
 (SELECT COUNT(*) FROM sys_approval_request ar JOIN business_application a ON a.application_id=ar.application_id
    WHERE ar.status='PENDING'),
 (SELECT COUNT(*) FROM migration_in mi JOIN business_application a ON a.application_id=mi.application_id
    WHERE mi.business_status='COMPLETED' AND mi.in_date BETWEEN DATE_SUB(CURRENT_DATE,INTERVAL 29 DAY) AND CURRENT_DATE),
 (SELECT COUNT(*) FROM migration_out mo JOIN business_application a ON a.application_id=mo.application_id
    WHERE mo.business_status='COMPLETED' AND mo.out_date BETWEEN DATE_SUB(CURRENT_DATE,INTERVAL 29 DAY) AND CURRENT_DATE);
"@
    $sqlOutput = $sql | & $client --protocol=TCP --host=127.0.0.1 --port=$databasePort `
        "--user=$databaseUser" "--database=$databaseName" `
        --default-character-set=utf8mb4 --batch --skip-column-names
    if ($LASTEXITCODE -ne 0) { throw 'Direct MySQL comparison query failed.' }
    $values = ([string]($sqlOutput | Select-Object -Last 1)).Split([char]9)
    if ($values.Count -ne 7) { throw 'Direct MySQL comparison query returned an unexpected column count.' }

    $matches = ([long]$overview.registeredPopulation -eq [long]$values[0]) -and
        ([long]$overview.activeFloatingPopulation -eq [long]$values[1]) -and
        ([long]$overview.activeResidencePermits -eq [long]$values[2]) -and
        ([long]$overview.expiringResidencePermits -eq [long]$values[3]) -and
        ([long]$overview.pendingApprovals -eq [long]$values[4]) -and
        ([long]$overview.migrationInPeriod -eq [long]$values[5]) -and
        ([long]$overview.migrationOutPeriod -eq [long]$values[6]) -and
        ([long]$floating.active -eq [long]$values[1]) -and
        ([long]$expiry.expiringResidencePermits -eq [long]$values[3])
    if (-not $matches) { throw 'Statistics API values differ from direct MySQL counts.' }
    if (@($charts.migrationTrend.dates).Count -ne 7 -or @($charts.migrationTrend.inFlow).Count -ne 7 -or
        @($charts.migrationTrend.outFlow).Count -ne 7) {
        throw 'Statistics trend API did not return seven actual points.'
    }

    Write-Output 'PASS health: MySQL UP, Redis disabled'
    Write-Output 'PASS login: QUERY_VIEWER, scope ALL, statistics:view loaded'
    Write-Output ("PASS statistics API equals direct MySQL counts: registered={0}, floating={1}, permits={2}, expiring={3}, pending={4}, migrationIn={5}, migrationOut={6}" -f $values[0],$values[1],$values[2],$values[3],$values[4],$values[5],$values[6])
    Write-Output 'PASS statistics chart: seven trend points returned'
}
finally {
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousMysqlPassword, 'Process')
}
