[CmdletBinding()]
param(
    [string]$Database = ('population_ops_verify_' + (Get-Date -Format 'yyyyMMdd_HHmmss')),
    [string]$MySqlClient = 'mysql',
    [switch]$SkipApplication
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
if ($Database -notmatch '^population_ops_verify_[A-Za-z0-9_]+$') {
    throw 'Only new population_ops_verify_* databases are allowed.'
}
$local = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $root 'start.local.env')) {
    if ($line -match '^\s*([^#=]+)=(.*)$') { $local[$matches[1].Trim()] = $matches[2] }
}
function Setting([string]$Name, [string]$Default = '') {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($value)) { return $value }
    if ($local.ContainsKey($Name)) { return $local[$Name] }
    return $Default
}
$url = Setting 'DB_URL'
if ($url -notmatch '^jdbc:mysql://(?<host>[^/:?]+)(?::(?<port>\d+))?/(?<db>[A-Za-z0-9_]+)') {
    throw 'Cannot parse DB_URL; credentials are not printed.'
}
$dbHost = $matches['host']
$dbPort = if ($matches['port']) { $matches['port'] } else { '3306' }
if ($dbHost -notin @('localhost', '127.0.0.1')) { throw 'Native verification requires a local MySQL host.' }
$username = Setting 'DB_USERNAME' 'root'
$password = Setting 'DB_PASSWORD'
$client = (Get-Command $MySqlClient -ErrorAction Stop).Source
$outputDir = Join-Path $root ('target/ops-native-verification/' + $Database)
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$envNames = @('MYSQL_PWD','DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','REDIS_ENABLED',
    'SCHEDULING_ENABLED','REPLICA_ENABLED','SERVER_PORT','INSTANCE_ID','APP_UPLOAD_DIR','EXPORT_DIR','LOG_DIR',
    'POLICY_AI_ENABLED','IDCARD_OCR_URL','OPS_BEARER_TOKEN')
$previous = @{}
foreach ($name in $envNames) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
$savedEncoding = $OutputEncoding
$processes = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
$results = [System.Collections.Generic.List[string]]::new()

function Record([string]$Message) { $results.Add($Message); Write-Host "[PASS] $Message" }
function Sql([string]$Statement, [switch]$WithDatabase, [string]$TargetDatabase = $Database) {
    $args = @('--protocol=TCP', "--host=$dbHost", "--port=$dbPort", "--user=$username", '--default-character-set=utf8mb4', '--batch', '--skip-column-names')
    if ($WithDatabase) { $args += "--database=$TargetDatabase" }
    $value = $Statement | & $client @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MySQL verification failed: $($value | Select-Object -Last 1)" }
    return ($value -join "`n")
}
function FreePort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { return $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
function Start-Backend([int]$Port, [string]$Id) {
    $env:SERVER_PORT = [string]$Port
    $env:INSTANCE_ID = $Id
    $env:LOG_DIR = Join-Path $outputDir $Id
    # Oracle's javapath launcher spawns another JVM on Windows; start the actual
    # java.home executable so the tracked PID is the server we must stop.
    $homeLine = & java -XshowSettings:properties -version 2>&1 | Select-String '^\s*java.home\s*=' | Select-Object -First 1
    if (-not $homeLine) { throw 'Cannot determine the actual JVM executable.' }
    $runtimeHome = $homeLine.ToString().Split('=', 2)[1].Trim()
    $java = Join-Path $runtimeHome 'bin/java.exe'
    $jar = Join-Path $root 'target/population-miniterm-1.0.0.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the current JAR before native verification.' }
    $process = Start-Process -FilePath $java -ArgumentList @('-jar', ('"' + $jar + '"')) -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $outputDir "$Id.stdout.log") `
        -RedirectStandardError (Join-Path $outputDir "$Id.stderr.log") -WindowStyle Hidden -PassThru
    $processes.Add($process)
    return $process
}
function Wait-Backend([int]$Port, [System.Diagnostics.Process]$Process, [string]$ExpectedInstance) {
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($Process.HasExited) { throw 'Verification backend exited; inspect its local logs.' }
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$Port/api/health" -UseBasicParsing -TimeoutSec 2
            $body = $response.Content | ConvertFrom-Json
            if ($body.data.database -eq 'UP' -and $response.Headers['X-Instance-Id'] -eq $ExpectedInstance) { return }
        } catch { }
        Start-Sleep -Milliseconds 250
    }
    throw 'Verification backend did not become healthy within 90 seconds.'
}
function Assert-Revoked([int]$Port, [string]$Token) {
    try {
        Invoke-RestMethod -Uri "http://127.0.0.1:$Port/api/auth/me" -Headers @{Authorization="Bearer $Token"} -TimeoutSec 5 | Out-Null
    } catch {
        if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -eq 401) { return }
        throw 'Expected HTTP 401 for revoked token.'
    }
    throw 'Revoked token was accepted.'
}
function Download-SharedMaterial([int]$Port, [long]$MaterialId, [string]$Token, [byte[]]$Expected) {
    $http = [Net.Http.HttpClient]::new()
    try {
        $http.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
        $actual = $http.GetByteArrayAsync("http://127.0.0.1:$Port/api/materials/$MaterialId/download").GetAwaiter().GetResult()
        if ([Convert]::ToBase64String($actual) -ne [Convert]::ToBase64String($Expected)) { throw 'Shared material bytes differ.' }
    } finally { $http.Dispose() }
}

try {
    $env:MYSQL_PWD = $password
    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    $exists = Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$Database';"
    if ([int]$exists -ne 0) { throw 'Refusing an existing database; choose a new verification name.' }
    Sql "CREATE DATABASE $Database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | Out-Null
    Record "Created isolated database $Database"
    $baseSql = [IO.File]::ReadAllText((Join-Path $root 'doc/database/population_miniterm.sql'))
    Sql ($baseSql.Replace('population_miniterm', $Database)) -WithDatabase | Out-Null
    # The canonical fresh snapshot already incorporates V4_001 through V4_010.
    # Historical migrations assume pre-rename columns and must not be replayed over that snapshot.
    foreach ($migration in Get-ChildItem (Join-Path $root 'doc/database/migrations') -Filter 'V4_*.sql' | Where-Object Name -GE 'V4_011' | Sort-Object Name) {
        Write-Host "[INFO] Applying $($migration.Name)"
        Sql ([IO.File]::ReadAllText($migration.FullName).Replace('population_miniterm', $Database)) -WithDatabase | Out-Null
    }
    $latest = [IO.File]::ReadAllText((Join-Path $root 'doc/database/migrations/V4_014_shared_token_revocation.sql'))
    Sql $latest -WithDatabase | Out-Null
    Record 'Fresh snapshot plus V4_011 through V4_014, and repeated V4_014 applied'
    Record ('MySQL version: ' + (Sql 'SELECT VERSION();' -WithDatabase))
    if ($SkipApplication) { return }

    $env:DB_URL = "jdbc:mysql://${dbHost}:$dbPort/${Database}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
    $env:DB_USERNAME = $username
    $env:DB_PASSWORD = $password
    $env:JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $env:REDIS_ENABLED = 'false'
    $env:REPLICA_ENABLED = 'false'
    $env:SCHEDULING_ENABLED = 'false'
    $env:POLICY_AI_ENABLED = 'false'
    $env:IDCARD_OCR_URL = ''
    $env:APP_UPLOAD_DIR = Join-Path $outputDir 'uploads'
    $env:EXPORT_DIR = Join-Path $outputDir 'exports'
    $portA = FreePort
    $a = Start-Backend $portA 'instance-a'
    $portB = FreePort
    while ($portB -eq $portA) { $portB = FreePort }
    $b = Start-Backend $portB 'instance-b'
    Wait-Backend $portA $a 'instance-a'
    Wait-Backend $portB $b 'instance-b'
    Record 'Two independent JVMs connected to the isolated MySQL database; Redis disabled'
    $login = Invoke-RestMethod -Uri "http://127.0.0.1:$portA/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body '{"username":"admin","password":"123456"}' -TimeoutSec 10
    $token = $login.data.token
    $me = Invoke-RestMethod -Uri "http://127.0.0.1:$portB/api/auth/me" -Headers @{Authorization="Bearer $token"} -TimeoutSec 10
    if ($me.code -ne 200) { throw 'Instance B rejected a valid token before logout.' }
    Record 'Token issued by A was accepted by B before logout'
    $draft = Invoke-RestMethod -Uri "http://127.0.0.1:$portA/api/applications" -Method Post -ContentType 'application/json' `
        -Headers @{Authorization="Bearer $token"} -Body '{"businessType":"GENERAL_SERVICE","title":"Ops fixture","reason":"Isolated file verification"}' -TimeoutSec 10
    $fixture = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2nWQAAAAASUVORK5CYII=')
    $http = [Net.Http.HttpClient]::new()
    $form = [Net.Http.MultipartFormDataContent]::new()
    try {
        $http.DefaultRequestHeaders.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
        $part = [Net.Http.ByteArrayContent]::new($fixture)
        $part.Headers.ContentType = [Net.Http.Headers.MediaTypeHeaderValue]::new('image/png')
        $form.Add($part, 'file', 'ops-fixture.png')
        $form.Add([Net.Http.StringContent]::new('APPLICATION_FORM'), 'materialType')
        $form.Add([Net.Http.StringContent]::new('Ops fixture'), 'materialName')
        $uploadResponse = $http.PostAsync("http://127.0.0.1:$portA/api/applications/$($draft.data.applicationId)/materials", $form).GetAwaiter().GetResult()
        $uploadResponse.EnsureSuccessStatusCode() | Out-Null
        $material = $uploadResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
        $materialId = $material.data.materialId
        $uploadResponse.Dispose()
    } finally { $form.Dispose(); $http.Dispose() }
    Download-SharedMaterial $portB $materialId $token $fixture
    Record 'Material uploaded through A downloaded byte-for-byte through B using shared storage'
    Invoke-RestMethod -Uri "http://127.0.0.1:$portA/api/auth/logout" -Method Post -Headers @{Authorization="Bearer $token"} -TimeoutSec 10 | Out-Null
    Assert-Revoked $portB $token
    Record 'A logout was enforced as HTTP 401 by B without Redis'
    if (-not $b.HasExited) { $b.Kill(); $b.WaitForExit() }
    $bRestarted = Start-Backend $portB 'instance-b-restarted'
    Wait-Backend $portB $bRestarted 'instance-b-restarted'
    Assert-Revoked $portB $token
    Record 'Restarted B still rejected the revoked token using durable primary state'
    $count = Sql 'SELECT COUNT(*) FROM security_token_revocation;' -WithDatabase
    if ([int]$count -ne 1) { throw 'Unexpected durable revocation count.' }
    Record 'Exactly one hashed revocation record persisted'

    $freshLogin = Invoke-RestMethod -Uri "http://127.0.0.1:$portA/api/auth/login" -Method Post -ContentType 'application/json' `
        -Body '{"username":"admin","password":"123456"}' -TimeoutSec 10
    $freshToken = $freshLogin.data.token
    Download-SharedMaterial $portB $materialId $freshToken $fixture
    Record 'Material remained accessible after B restarted'
    $env:OPS_BEARER_TOKEN = $freshToken
    & python (Join-Path $root 'scripts/perf/read-load.py') --url "http://127.0.0.1:$portA/api/statistics/overview" `
        --requests 100 --concurrency 4 --warmup 10 --output (Join-Path $outputDir 'baseline.json')
    if ($LASTEXITCODE -ne 0) { throw 'Read-only load probe failed.' }
    Record '100-request statistics baseline completed (4 concurrent, 10 warmup requests); raw timings saved'

    $restored = $Database + '_restored'
    if ([int](Sql "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$restored';") -ne 0) { throw 'Restore database already exists.' }
    $dumper = Join-Path (Split-Path $client) 'mysqldump.exe'
    $dumpArgs = @('--protocol=TCP', "--host=$dbHost", "--port=$dbPort", "--user=$username", '--default-character-set=utf8mb4',
        '--single-transaction','--set-gtid-purged=OFF','--no-tablespaces','--routines','--triggers','--skip-add-drop-table',$Database)
    $dump = & $dumper @dumpArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Database backup failed.' }
    $dumpText = $dump -join "`n"
    [IO.File]::WriteAllText((Join-Path $outputDir 'backup.sql'), $dumpText, [Text.UTF8Encoding]::new($false))
    Sql "CREATE DATABASE $restored CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" | Out-Null
    Sql $dumpText -WithDatabase -TargetDatabase $restored | Out-Null
    $restoredUploads = Join-Path $outputDir 'restored-uploads'
    Copy-Item -LiteralPath $env:APP_UPLOAD_DIR -Destination $restoredUploads -Recurse
    $env:DB_URL = $env:DB_URL.Replace("/$Database`?", "/$restored`?")
    $env:APP_UPLOAD_DIR = $restoredUploads
    $portC = FreePort
    $c = Start-Backend $portC 'restored-instance'
    Wait-Backend $portC $c 'restored-instance'
    Download-SharedMaterial $portC $materialId $freshToken $fixture
    Assert-Revoked $portC $token
    Record 'Database and uploaded files restored into a new database/directory; restored JVM served matching bytes and enforced revocation'
} finally {
    foreach ($process in $processes) {
        if (-not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
        $process.Dispose()
    }
    foreach ($name in $envNames) { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    $OutputEncoding = $savedEncoding
    $results | Set-Content -LiteralPath (Join-Path $outputDir 'results.txt') -Encoding UTF8
    Write-Host "[INFO] Evidence: $outputDir; isolated database retained, verification JVMs stopped."
}
