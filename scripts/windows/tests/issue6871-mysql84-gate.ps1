[CmdletBinding()]
param([string]$TestSelector = 'Issue6871MySqlIT', [int]$Port = 13392)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($Port -eq 3306 -or $Port -lt 1024 -or $Port -gt 65535) { throw 'Use a dedicated non-default test port.' }
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw 'Selected port is already in use; no process will be touched.' }
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$bin = 'C:/Program Files/MySQL/MySQL Server 8.4/bin'
$tag = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$taskDir = Join-Path $env:TEMP ('population_mysql84_issue6871_' + $tag)
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
innodb-buffer-pool-size=256M
log-error=$($taskDir.Replace('\','/'))/mysql-error.log
"@
[IO.File]::WriteAllText($ini, $config, [Text.UTF8Encoding]::new($false))
& "$bin/mysqld.exe" "--defaults-file=$ini" --initialize-insecure *> (Join-Path $taskDir 'initialize.log')
if ($LASTEXITCODE -ne 0) { throw "Initialization failed; evidence preserved at $taskDir" }
$proc = Start-Process -FilePath "$bin/mysqld.exe" -ArgumentList "--defaults-file=$ini" -WindowStyle Hidden -PassThru
function New-RandomHexSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}
$appPassword = New-RandomHexSecret
$adminPassword = New-RandomHexSecret
$schema = "population_miniterm_mysql_gate_issue6871_$tag"
$env:MYSQL84_GATE_DB_URL = "jdbc:mysql://127.0.0.1:$Port/${schema}?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=false"
$env:MYSQL84_GATE_DB_USERNAME = "i6871_$tag"
$env:MYSQL84_GATE_DB_PASSWORD = $appPassword
$env:MYSQL84_GATE_ADMIN_USERNAME = 'root'
$env:MYSQL84_GATE_ADMIN_PASSWORD = $adminPassword
$env:MYSQL84_GATE_API_TEST_PASSWORD = 'Issue6871-Test-Password'
$credentials = @{url=$env:MYSQL84_GATE_DB_URL; appUser=$env:MYSQL84_GATE_DB_USERNAME; appPassword=$appPassword; adminPassword=$adminPassword; pid=$proc.Id; port=$Port; taskDir=$taskDir}
($credentials | ConvertTo-Json -Compress | ConvertTo-SecureString -AsPlainText -Force | ConvertFrom-SecureString) |
    Set-Content -LiteralPath (Join-Path $taskDir 'credentials.dpapi')
$previousPwd = $env:MYSQL_PWD
$result = 1
try {
    $env:MYSQL_PWD = ''
    $ready = $false
    for ($i=0; $i -lt 200; $i++) {
        $null = 'SELECT 1;' | & "$bin/mysql.exe" --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch --skip-column-names 2>$null
        if ($LASTEXITCODE -eq 0) { $ready=$true; break }
        if ($proc.HasExited) { throw "New test instance exited; see $taskDir" }
        Start-Sleep -Milliseconds 100
    }
    if (!$ready) { throw "New test instance not ready; see $taskDir" }
    $null = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$adminPassword';" | & "$bin/mysql.exe" --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch
    if ($LASTEXITCODE -ne 0) { throw 'Failed to set new private instance administrator credentials.' }
    $env:MYSQL_PWD = $adminPassword
    # Load the repository's canonical schema through a UTF-8 file redirect. The older
    # init_database.ps1 pipes SQL text through PowerShell 5's native encoder, which can
    # corrupt CJK literals before mysql.exe sees them; this gate deliberately uses the
    # same SQL file with an explicit UTF-8 byte stream instead.
    $sourceSql = Join-Path $repo 'doc/database/population_miniterm.sql'
    $schemaSql = Join-Path $taskDir 'schema.generated.sql'
    $source = [IO.File]::ReadAllText($sourceSql, [Text.Encoding]::UTF8)
    $headerPattern = '(?ms)\ACREATE DATABASE IF NOT EXISTS population_miniterm\s+DEFAULT CHARACTER SET utf8mb4\s+DEFAULT COLLATE utf8mb4_unicode_ci;\s+USE population_miniterm;'
    $headerReplacement = "CREATE DATABASE IF NOT EXISTS $schema`r`n    DEFAULT CHARACTER SET utf8mb4`r`n    DEFAULT COLLATE utf8mb4_unicode_ci;`r`n`r`nUSE $schema;"
    $source = [regex]::Replace($source, $headerPattern, $headerReplacement, 1)
    [IO.File]::WriteAllText($schemaSql, $source, [Text.UTF8Encoding]::new($false))
    cmd /c "`"$bin/mysql.exe`" --protocol=TCP --host=127.0.0.1 --port=$Port --user=root --default-character-set=utf8mb4 < `"$schemaSql`"" *> (Join-Path $taskDir 'schema.log')
    if ($LASTEXITCODE -ne 0) { throw "Schema load failed; see $taskDir" }
    $grantSql = "CREATE USER IF NOT EXISTS '$env:MYSQL84_GATE_DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$appPassword'; ALTER USER '$env:MYSQL84_GATE_DB_USERNAME'@'127.0.0.1' IDENTIFIED BY '$appPassword'; GRANT SELECT,INSERT,UPDATE,DELETE ON ``$schema``.* TO '$env:MYSQL84_GATE_DB_USERNAME'@'127.0.0.1';"
    $grantSql | & "$bin/mysql.exe" --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch *> (Join-Path $taskDir 'grant.log')
    if ($LASTEXITCODE -ne 0) { throw "Application account setup failed; see $taskDir" }

    Push-Location $repo
    try {
        # PS 5.1 surfaces native stderr as ErrorRecord; Maven prints the Mockito
        # self-attach warning there, so keep it non-terminating and read the exit code.
        $ErrorActionPreference = 'Continue'
        & ./mvnw.cmd "-Dtest=$TestSelector" `
            '-Dlogging.level.com.wjx871.population.query.Phase11QueryMapper=DEBUG' `
            '-Dlogging.level.com.wjx871.population.query.ComprehensiveQueryMapper=DEBUG' `
            test *> (Join-Path $taskDir 'maven.log')
        $result = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        Get-Content -LiteralPath (Join-Path $taskDir 'maven.log') -Tail 30
        Write-Output "ISSUE6871_GATE_EVIDENCE=$taskDir"
        Write-Output "ISSUE6871_GATE_EXIT=$result"
    } finally { Pop-Location }

    $env:MYSQL_PWD = $appPassword
    $explain = Join-Path $taskDir 'explain.txt'
    $sql = @"
USE $schema;
SELECT '=== SHOW INDEX residence ===' AS section;
SHOW INDEX FROM residence;
SELECT '=== SHOW INDEX floating_population ===' AS section;
SHOW INDEX FROM floating_population;
SELECT '=== SHOW INDEX residence_permit ===' AS section;
SHOW INDEX FROM residence_permit;
SELECT '=== SHOW INDEX migration_in ===' AS section;
SHOW INDEX FROM migration_in;
SELECT '=== SHOW INDEX migration_out ===' AS section;
SHOW INDEX FROM migration_out;
SELECT '=== PHASE11 LIST EXPLAIN ===' AS section;
EXPLAIN FORMAT=JSON
SELECT p.person_id,p.name,p.gender,p.birth_date,p.id_card masked_identity_no,p.phone masked_phone,p.current_status_code person_status,h.household_id,h.household_no,hp.name head_person_name,hm.relationship,(h.head_person_id=p.person_id) household_head,r.region_code current_region_code,r.registered_address current_address,r.status residence_status,f.floating_id,f.status floating_status,f.arrival_date,rp.permit_id,rp.permit_no masked_permit_no,rp.status permit_status,rp.valid_until permit_valid_until,CASE WHEN latest_in.last_in_date IS NULL AND latest_out.last_out_date IS NULL THEN NULL WHEN latest_out.last_out_date IS NULL THEN latest_in.last_in_date WHEN latest_in.last_in_date IS NULL THEN latest_out.last_out_date WHEN latest_out.last_out_date>=latest_in.last_in_date THEN latest_out.last_out_date ELSE latest_in.last_in_date END last_migration_date,CASE WHEN latest_in.last_in_date IS NULL AND latest_out.last_out_date IS NULL THEN NULL WHEN latest_out.last_out_date IS NULL THEN 'IN' WHEN latest_in.last_in_date IS NULL THEN 'OUT' WHEN latest_out.last_out_date>=latest_in.last_in_date THEN 'OUT' ELSE 'IN' END last_migration_direction
FROM person p LEFT JOIN residence r ON r.person_id=p.person_id AND r.status='ACTIVE' LEFT JOIN household h ON h.household_id=r.household_id LEFT JOIN person hp ON hp.person_id=h.head_person_id LEFT JOIN household_member hm ON hm.household_id=h.household_id AND hm.person_id=p.person_id AND hm.status='ACTIVE' LEFT JOIN floating_population f ON f.person_id=p.person_id AND f.status='ACTIVE' AND f.current_flag=1 LEFT JOIN residence_permit rp ON rp.person_id=p.person_id AND rp.status='ACTIVE' AND rp.current_flag=1 LEFT JOIN (SELECT person_id,MAX(in_date) last_in_date FROM migration_in WHERE business_status='COMPLETED' GROUP BY person_id) latest_in ON latest_in.person_id=p.person_id LEFT JOIN (SELECT person_id,MAX(out_date) last_out_date FROM migration_out WHERE business_status='COMPLETED' GROUP BY person_id) latest_out ON latest_out.person_id=p.person_id
WHERE p.name LIKE '%Q6871%' AND (EXISTS(SELECT 1 FROM residence rs WHERE rs.person_id=p.person_id AND rs.status='ACTIVE' AND rs.region_code IN ('980000','980100','980101')) OR EXISTS(SELECT 1 FROM floating_population fs WHERE fs.person_id=p.person_id AND fs.status='ACTIVE' AND fs.current_flag=1 AND fs.current_region_code IN ('980000','980100','980101')))
GROUP BY p.person_id,p.name,p.gender,p.birth_date,p.id_card,p.phone,p.current_status_code,h.household_id,h.household_no,h.head_person_id,hp.name,hm.relationship,r.region_code,r.registered_address,r.status,f.floating_id,f.status,f.arrival_date,rp.permit_id,rp.permit_no,rp.status,rp.valid_until,latest_in.last_in_date,latest_out.last_out_date
ORDER BY p.person_id DESC LIMIT 10 OFFSET 0;
SELECT '=== PHASE11 COUNT EXPLAIN ===' AS section;
EXPLAIN FORMAT=JSON
SELECT COUNT(DISTINCT p.person_id)
FROM person p LEFT JOIN residence r ON r.person_id=p.person_id AND r.status='ACTIVE' LEFT JOIN household h ON h.household_id=r.household_id LEFT JOIN person hp ON hp.person_id=h.head_person_id LEFT JOIN household_member hm ON hm.household_id=h.household_id AND hm.person_id=p.person_id AND hm.status='ACTIVE' LEFT JOIN floating_population f ON f.person_id=p.person_id AND f.status='ACTIVE' AND f.current_flag=1 LEFT JOIN residence_permit rp ON rp.person_id=p.person_id AND rp.status='ACTIVE' AND rp.current_flag=1 LEFT JOIN (SELECT person_id,MAX(in_date) last_in_date FROM migration_in WHERE business_status='COMPLETED' GROUP BY person_id) latest_in ON latest_in.person_id=p.person_id LEFT JOIN (SELECT person_id,MAX(out_date) last_out_date FROM migration_out WHERE business_status='COMPLETED' GROUP BY person_id) latest_out ON latest_out.person_id=p.person_id
WHERE p.name LIKE '%Q6871%' AND (EXISTS(SELECT 1 FROM residence rs WHERE rs.person_id=p.person_id AND rs.status='ACTIVE' AND rs.region_code IN ('980000','980100','980101')) OR EXISTS(SELECT 1 FROM floating_population fs WHERE fs.person_id=p.person_id AND fs.status='ACTIVE' AND fs.current_flag=1 AND fs.current_region_code IN ('980000','980100','980101')));
SELECT '=== LATEST MIGRATION AGGREGATE EXPLAIN ===' AS section;
EXPLAIN FORMAT=JSON SELECT person_id,MAX(in_date) FROM migration_in WHERE business_status='COMPLETED' GROUP BY person_id;
EXPLAIN FORMAT=JSON SELECT person_id,MAX(out_date) FROM migration_out WHERE business_status='COMPLETED' GROUP BY person_id;
SELECT '=== ACTUAL COUNTS ===' AS section;
SELECT COUNT(*) AS person_count FROM person;
SELECT COUNT(*) AS query_rows FROM person p WHERE p.name LIKE '%Q6871%';
"@
    [IO.File]::WriteAllText($explain, $sql, [Text.UTF8Encoding]::new($false))
    $ErrorActionPreference = 'Continue'
    $explainOut = $sql | & "$bin/mysql.exe" --protocol=TCP --host=127.0.0.1 "--port=$Port" `
        "--user=$env:MYSQL84_GATE_DB_USERNAME" "--database=$schema" --default-character-set=utf8mb4 --table 2>&1
    $explainOut | Out-File -LiteralPath (Join-Path $taskDir 'explain-output.txt') -Encoding utf8
    $ErrorActionPreference = 'Stop'
    $explainOut | Select-Object -Last 8
} finally {
    $env:MYSQL_PWD = $adminPassword
    if (!$proc.HasExited) {
        & "$bin/mysqladmin.exe" --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root shutdown 2>$null
        $null = $proc.WaitForExit(10000)
    }
    $env:MYSQL_PWD = $previousPwd
}
exit $result
