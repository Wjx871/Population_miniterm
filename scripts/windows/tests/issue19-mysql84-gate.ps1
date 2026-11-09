[CmdletBinding()]
param(
    [int]$Port = 0,
    [int]$ConcurrencyRounds = 10,
    [switch]$SkipMaven
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# ---------------------------------------------------------------------------------------------
# Issue #19 real MySQL 8.4 gate.
#
# Creates a private, throw-away MySQL 8.4 instance (loopback only, non-3306 port, own datadir),
# builds the pre-fix V4_018 schema from the pinned baseline commit, reproduces the old 1062
# failure, applies V4_019 twice, verifies conditional uniqueness/history/new behaviour, runs
# real two-connection concurrency rounds, and records every observation under
# doc/development/evidence/issue19-mysql84*.txt.
#
# It never connects to port 3306, the course database, or any shared/production instance.
# ---------------------------------------------------------------------------------------------

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$bin = 'C:/Program Files/MySQL/MySQL Server 8.4/bin'
$mysqldExe = "$bin/mysqld.exe"
$mysqlExe = "$bin/mysql.exe"
$mysqladminExe = "$bin/mysqladmin.exe"
$baselineSha = '1f3f4427775f55b853a7b42d2cb759b4cdfa7411'
$tag = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$taskDir = Join-Path $env:TEMP ("population_mysql84_issue19_" + $tag)
$dataDir = Join-Path $taskDir 'data'
$ini = Join-Path $taskDir 'my.ini'
$schema = "population_miniterm_mysql_gate_issue19_$tag"
$adminUser = 'root'
$appUser = "i19_$tag"
$evidencePath = Join-Path $repo 'doc/development/evidence/issue19-mysql84.txt'
$schemaEvidencePath = Join-Path $repo 'doc/development/evidence/issue19-mysql84-schema.txt'

$script:evidence = New-Object System.Collections.Generic.List[string]
$script:schemaEvidence = New-Object System.Collections.Generic.List[string]
$script:checkFailures = New-Object System.Collections.Generic.List[string]
$script:aborted = $null
$serverStarted = $false
$rootPasswordSet = $false
$adminPassword = $null
$appPassword = $null

function New-HexSecret {
    param([int]$ByteCount)
    $bytes = New-Object byte[] $ByteCount
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return (($bytes | ForEach-Object { $_.ToString('x2') }) -join '')
}

$adminPassword = New-HexSecret 32
$appPassword = New-HexSecret 32

function Add-Evidence {
    param([string]$Text = '')
    $script:evidence.Add($Text)
    Write-Host $Text
}

function Add-SchemaEvidence {
    param([string]$Text = '')
    $script:schemaEvidence.Add($Text)
    Write-Host $Text
}

function Write-Utf8NoBom {
    param([string]$Path, [string]$Content)
    [IO.File]::WriteAllText($Path, $Content, [Text.UTF8Encoding]::new($false))
}

function Check {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if ($Condition) {
        Add-Evidence ("[PASS] " + $Name + $(if ($Detail) { " :: " + $Detail } else { '' }))
    } else {
        $script:checkFailures.Add($Name)
        Add-Evidence ("[FAIL] " + $Name + $(if ($Detail) { " :: " + $Detail } else { '' }))
    }
}

function Require {
    param([string]$Name, [bool]$Condition, [string]$Detail = '')
    if (-not $Condition) {
        throw ("REQUIRED CHECK FAILED: " + $Name + $(if ($Detail) { " :: " + $Detail } else { '' }))
    }
    Add-Evidence ("[PASS] " + $Name + $(if ($Detail) { " :: " + $Detail } else { '' }))
}

function Invoke-MySqlFile {
    param([string]$Path, [string]$Database, [string]$LogPath, [switch]$AsApp)
    $oldPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        $user = if ($AsApp) { $appUser } else { $adminUser }
        $pwd = if ($AsApp) { $appPassword } else { $adminPassword }
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $pwd, 'Process')
        $argLine = '/c "' + '"' + $mysqlExe + '" --protocol=TCP --host=127.0.0.1 --port=' + $Port +
            ' --user=' + $user + ' --default-character-set=utf8mb4'
        if ($Database) { $argLine += ' --database=' + $Database }
        $argLine += ' < "' + $Path + '" > "' + $LogPath + '" 2>&1"'
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $env:ComSpec
        $psi.Arguments = $argLine
        $psi.UseShellExecute = $false
        $psi.CreateNoWindow = $true
        $process = [Diagnostics.Process]::Start($psi)
        $null = $process.WaitForExit()
        return $process.ExitCode
    } finally {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldPwd, 'Process')
    }
}

function Invoke-MySqlText {
    param([string]$Sql, [string]$Database, [switch]$AsApp, [switch]$Table)
    $oldPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        $user = if ($AsApp) { $appUser } else { $adminUser }
        $pwd = if ($AsApp) { $appPassword } else { $adminPassword }
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $pwd, 'Process')
        $cliArgs = @('--protocol=TCP', '--host=127.0.0.1', ('--port=' + $Port), ('--user=' + $user),
            '--default-character-set=utf8mb4')
        if ($Database) { $cliArgs += '--database=' + $Database }
        if ($Table) { $cliArgs += '--table' } else { $cliArgs += @('--batch', '--skip-column-names') }
        $out = @($Sql | & $mysqlExe @cliArgs 2>&1)
        return [pscustomobject]@{ Output = $out; ExitCode = $LASTEXITCODE }
    } finally {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldPwd, 'Process')
    }
}

function Get-Scalar {
    param([string]$Sql, [string]$Database)
    $result = Invoke-MySqlText -Sql $Sql -Database $Database
    if ($result.ExitCode -ne 0) {
        throw ("Scalar query failed: " + ($result.Output | Select-Object -Last 2 | Out-String))
    }
    if ($result.Output.Count -eq 0) { return '' }
    return ([string]$result.Output[-1]).Trim()
}

function Start-MySqlClientProcess {
    param([string]$SqlFile, [string]$OutFile, [string]$ErrFile)
    $argLine = '/c "' + '"' + $mysqlExe + '" --protocol=TCP --host=127.0.0.1 --port=' + $Port +
        ' --user=' + $adminUser + ' --database=' + $schema +
        ' --default-character-set=utf8mb4 --batch --skip-column-names < "' + $SqlFile +
        '" > "' + $OutFile + '" 2> "' + $ErrFile + '"' + '"'
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $env:ComSpec
    $psi.Arguments = $argLine
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $oldPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $adminPassword, 'Process')
        return [Diagnostics.Process]::Start($psi)
    } finally {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldPwd, 'Process')
    }
}

function New-RewrittenSqlFile {
    param([string]$SourcePath, [string]$DestinationPath, [switch]$RewriteFreshHeader)
    $source = [IO.File]::ReadAllText($SourcePath, [Text.Encoding]::UTF8)
    if ($RewriteFreshHeader) {
        $pattern = '(?ms)\ACREATE DATABASE IF NOT EXISTS population_miniterm\s+DEFAULT CHARACTER SET utf8mb4\s+DEFAULT COLLATE utf8mb4_unicode_ci;\s+USE population_miniterm;'
        $replacement = "CREATE DATABASE IF NOT EXISTS $schema`r`n    DEFAULT CHARACTER SET utf8mb4`r`n    DEFAULT COLLATE utf8mb4_unicode_ci;`r`n`r`nUSE $schema;"
        $matches = [regex]::Matches($source, $pattern)
        if ($matches.Count -ne 1) { throw "Cannot rewrite fresh-install database header in $SourcePath" }
        $source = [regex]::Replace($source, $pattern, $replacement, 1)
    } else {
        $pattern = '(?im)^\s*USE\s+population_miniterm\s*;'
        $matches = [regex]::Matches($source, $pattern)
        if ($matches.Count -gt 1) { throw "Multiple USE statements in $SourcePath" }
        if ($matches.Count -eq 1) {
            $source = [regex]::Replace($source, $pattern, ('USE ' + $schema + ';'), 1)
        }
    }
    Write-Utf8NoBom -Path $DestinationPath -Content $source
}

function Get-Sha256 {
    param([string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash
}

function Write-EvidenceFiles {
    $generated = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz')
    $header = @(
        '================================================================================',
        'Issue #19 - cancelled MIGRATION_IN draft must release its transfer batch',
        'Real MySQL 8.4 verification evidence',
        '================================================================================',
        ('Generated: ' + $generated),
        ('Gate script: ' + (Join-Path $repo 'scripts/windows/tests/issue19-mysql84-gate.ps1')),
        ('Worktree: ' + $repo),
        ('Baseline commit: ' + $baselineSha),
        ('Temporary instance dir: ' + $taskDir),
        ('Schema: ' + $schema),
        ('Port: ' + $Port),
        ''
    )
    $footer = @('', ('RESULT: ' + $(if ($script:aborted) { 'ABORTED - ' + $script:aborted } elseif ($script:checkFailures.Count -eq 0) { 'PASS - all checks passed' } else { 'FAIL - ' + $script:checkFailures.Count + ' check(s) failed: ' + ($script:checkFailures -join '; ') })))
    Write-Utf8NoBom -Path $evidencePath -Content (($header + $script:evidence + $footer) -join "`r`n")
    Write-Utf8NoBom -Path $schemaEvidencePath -Content (($header + $script:schemaEvidence + $footer) -join "`r`n")
    Write-Host ("EVIDENCE=" + $evidencePath)
    Write-Host ("SCHEMA_EVIDENCE=" + $schemaEvidencePath)
    Write-Host ("TEMP_INSTANCE_DIR=" + $taskDir)
}

try {
    Add-Evidence '================================================================================'
    Add-Evidence 'Issue #19 real MySQL 8.4 gate'
    Add-Evidence '================================================================================'
    Add-Evidence ('Started: ' + (Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz'))
    Add-Evidence ('Repo: ' + $repo)
    Add-Evidence ('Baseline commit: ' + $baselineSha)

    # -----------------------------------------------------------------------------------------
    # 0. Environment guard
    # -----------------------------------------------------------------------------------------
    if ($Port -eq 0) {
        $selected = 0
        foreach ($candidate in (13370..13399)) {
            $listener = Get-NetTCPConnection -State Listen -LocalPort $candidate -ErrorAction SilentlyContinue
            if (-not $listener) { $selected = $candidate; break }
        }
        if ($selected -eq 0) { throw 'No free port found in 13370-13399; refusing to touch any other port.' }
        $Port = $selected
    }
    if ($Port -eq 3306 -or $Port -lt 1024 -or $Port -gt 65535) { throw 'Refusing to use the default/course port or an invalid port.' }
    if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port $Port is already in use; no process will be touched." }
    if (-not (Test-Path -LiteralPath $mysqldExe)) { throw "MySQL 8.4 binary not found: $mysqldExe" }
    $versionText = (& $mysqldExe --version 2>&1 | Out-String).Trim()
    if ($versionText -notmatch '8\.4\.') { throw "Expected MySQL 8.4 binary, got: $versionText" }
    Add-Evidence ('mysqld --version: ' + $versionText)
    Add-Evidence ('Selected port: ' + $Port + ' (checked free, non-3306)')

    # Worktree identity: must be the expected feature branch and baseline HEAD.
    $headSha = (& git -C $repo rev-parse HEAD 2>&1 | Out-String).Trim()
    $headBranch = (& git -C $repo rev-parse --abbrev-ref HEAD 2>&1 | Out-String).Trim()
    Add-Evidence ('Worktree HEAD: ' + $headSha + ' branch=' + $headBranch)
    & git -C $repo cat-file -e ($baselineSha + '^{commit}') 2>$null
    $baselineCommitPresent = ($LASTEXITCODE -eq 0)
    Require 'Worktree HEAD includes the pinned baseline commit' $baselineCommitPresent ''
    $baselineFileInGit = Join-Path $taskDir 'population_miniterm.baseline.sql'
    New-Item -ItemType Directory -Path $taskDir -Force | Out-Null
    $baselineText = (& git -C $repo show ($baselineSha + ':doc/database/population_miniterm.sql') 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($baselineText)) { throw 'Could not read the baseline population_miniterm.sql from git.' }
    if ($baselineText -match 'active_transfer_batch_no' -or $baselineText -match 'uk_migration_in_active_batch') {
        throw 'Baseline schema unexpectedly already contains the #19 fix; wrong baseline commit?'
    }
    if ($baselineText -notmatch 'uk_migration_in_batch') { throw 'Baseline schema does not contain uk_migration_in_batch.' }
    Write-Utf8NoBom -Path $baselineFileInGit -Content $baselineText
    Add-Evidence ('Baseline population_miniterm.sql sha256=' + (Get-Sha256 $baselineFileInGit))

    # -----------------------------------------------------------------------------------------
    # 1. Create private instance
    # -----------------------------------------------------------------------------------------
    $null = New-Item -ItemType Directory -Path $taskDir -Force
    $aclOutput = & icacls $taskDir /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' 'Administrators:(OI)(CI)F' 2>&1
    if ($LASTEXITCODE -ne 0) { throw ('Unable to restrict temporary directory ACL: ' + ($aclOutput | Out-String)) }
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
    Write-Utf8NoBom -Path $ini -Content $config
    & $mysqldExe "--defaults-file=$ini" --initialize-insecure *> (Join-Path $taskDir 'initialize.log')
    if ($LASTEXITCODE -ne 0) {
        Add-Evidence ('initialize.log tail: ' + ((Get-Content (Join-Path $taskDir 'initialize.log') -Tail 30) -join ' / '))
        throw 'mysqld --initialize-insecure failed.'
    }
    Add-Evidence 'initialize-insecure: PASS'
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $mysqldExe
    $psi.Arguments = "--defaults-file=$ini"
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $serverProc = [Diagnostics.Process]::Start($psi)
    $serverStarted = $true
    [Environment]::SetEnvironmentVariable('MYSQL_PWD', '', 'Process')
    $ready = $false
    for ($i = 0; $i -lt 300; $i++) {
        $null = 'SELECT 1;' | & $mysqlExe --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch --skip-column-names 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Milliseconds 100
    }
    if (-not $ready) { throw 'Private mysqld did not become ready.' }
    Add-Evidence ('Private mysqld ready: PASS (pid=' + $serverProc.Id + ', datadir=' + $dataDir + ')')
    $null = 'ALTER USER ''root''@''localhost'' IDENTIFIED BY ''' + $adminPassword + ''';' | & $mysqlExe --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root --batch
    if ($LASTEXITCODE -ne 0) { throw 'Failed to set private root password.' }
    $rootPasswordSet = $true

    $adminIdentity = Invoke-MySqlText -Sql 'SELECT @@version,@@version_comment,@@port,@@bind_address,@@transaction_isolation,@@datadir,@@sql_mode;' -Database ''
    Require 'Administrator identity query succeeds' ($adminIdentity.ExitCode -eq 0) ($adminIdentity.Output -join ' | ')
    $adminRow = ([string]$adminIdentity.Output[-1]).Split([char]9)
    Require 'Server is MySQL 8.4' ($adminRow[0] -match '^8\.4\.') $adminRow[0]
    Require 'Server port matches selected non-3306 port' (([int]($adminRow[2])) -eq $Port) ('port=' + $adminRow[2])
    Require 'Server bind-address is 127.0.0.1' ($adminRow[3] -eq '127.0.0.1') $adminRow[3]
    Require 'Server transaction isolation is REPEATABLE-READ' ($adminRow[4] -eq 'REPEATABLE-READ') $adminRow[4]
    Add-SchemaEvidence '--- administrator identity (root, private instance) ---'
    Add-SchemaEvidence ('SELECT @@version,@@version_comment,@@port,@@bind_address,@@transaction_isolation,@@datadir,@@sql_mode; => ' + ($adminRow -join ' | '))

    $createDbPath = Join-Path $taskDir 'create-database.sql'
    Write-Utf8NoBom -Path $createDbPath -Content ("CREATE DATABASE IF NOT EXISTS ``$schema`` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;")
    $createDbExit = Invoke-MySqlFile -Path $createDbPath -Database '' -LogPath (Join-Path $taskDir 'create-database.log')
    if ($createDbExit -ne 0) { throw 'Failed to create isolated schema.' }
    $grantSql = "CREATE USER IF NOT EXISTS '$appUser'@'127.0.0.1' IDENTIFIED BY '$appPassword'; ALTER USER '$appUser'@'127.0.0.1' IDENTIFIED BY '$appPassword'; GRANT SELECT,INSERT,UPDATE,DELETE ON ``$schema``.* TO '$appUser'@'127.0.0.1';"
    $grantLog = Join-Path $taskDir 'grant.log'
    Write-Utf8NoBom -Path (Join-Path $taskDir 'grant.sql') -Content $grantSql
    $grantExit = Invoke-MySqlFile -Path (Join-Path $taskDir 'grant.sql') -Database '' -LogPath $grantLog
    Require 'Non-root application account created with schema-scoped DML only' ($grantExit -eq 0) ('exit=' + $grantExit)
    $grants = Invoke-MySqlText -Sql "SHOW GRANTS FOR '$appUser'@'127.0.0.1';" -Database ''
    Add-SchemaEvidence '--- application account grants ---'
    foreach ($line in $grants.Output) { Add-SchemaEvidence ([string]$line) }
    $appIdentity = Invoke-MySqlText -Sql 'SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER();' -Database $schema -AsApp
    Require 'Application account identity query succeeds' ($appIdentity.ExitCode -eq 0) ($appIdentity.Output -join ' | ')
    $appRow = ([string]$appIdentity.Output[-1]).Split([char]9)
    Require 'Application connection reports expected version' ($appRow[0] -match '^8\.4\.') $appRow[0]
    Require 'Application connection reports expected port' (([int]($appRow[1])) -eq $Port) ('port=' + $appRow[1])
    Require 'Application connection reports 127.0.0.1' ($appRow[2] -eq '127.0.0.1') $appRow[2]
    Require 'Application connection reports REPEATABLE-READ' ($appRow[3] -eq 'REPEATABLE-READ') $appRow[3]
    Require 'Application connection reports the isolated schema' ($appRow[4] -eq $schema) $appRow[4]
    Require 'Application connection uses the non-root account' ($appRow[5] -match ('^' + [regex]::Escape($appUser) + '@')) $appRow[5]
    Add-SchemaEvidence '--- application identity (non-root) ---'
    Add-SchemaEvidence ('SELECT @@version,@@port,@@bind_address,@@transaction_isolation,DATABASE(),CURRENT_USER(); => ' + ($appRow -join ' | '))
    Add-SchemaEvidence ('datadir: ' + $dataDir)
    Add-SchemaEvidence ('instance task dir: ' + $taskDir)

    # -----------------------------------------------------------------------------------------
    # 2. Build the historical V4_018 schema (baseline + V4_011..V4_013 + V4_015..V4_018)
    # -----------------------------------------------------------------------------------------
    $baselineGenerated = Join-Path $taskDir 'population_miniterm.baseline.generated.sql'
    New-RewrittenSqlFile -SourcePath $baselineFileInGit -DestinationPath $baselineGenerated -RewriteFreshHeader
    $baselineLog = Join-Path $taskDir 'baseline-load.log'
    $baselineExit = Invoke-MySqlFile -Path $baselineGenerated -Database '' -LogPath $baselineLog
    Require 'Historical baseline schema loads successfully' ($baselineExit -eq 0) ('exit=' + $baselineExit + ', log=' + $baselineLog)
    Add-Evidence ('Baseline schema load: PASS (sha256=' + (Get-Sha256 $baselineFileInGit) + ')')

    $v4014Columns = Get-Scalar -Sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='person' AND column_name IN ('initial_scope_user_id','initial_scope_department_id','initial_scope_region_code');" -Database $schema
    Require 'Baseline already contains V4_014 initial-scope columns (so V4_014 is not re-applied)' ($v4014Columns -eq '3') ('count=' + $v4014Columns)
    Add-Evidence 'V4_014: baseline schema already contains the three initial_scope_* columns; no separate apply needed.'

    $migrations = @(
        'V4_011_application_return.sql',
        'V4_012_dashboard_viewer_scope.sql',
        'V4_013_person_idcard_image.sql',
        'V4_015_ai_precheck_async.sql',
        'V4_016_s4a_entity_resolution.sql',
        'V4_017_s4a_resolution_retention_indexes.sql',
        'V4_018_s4b_confirm_draft_binding.sql'
    )
    Add-Evidence ''
    Add-Evidence '--- historical migration chain to V4_018 ---'
    foreach ($migration in $migrations) {
        $sourcePath = Join-Path $repo ('doc/database/migrations/' + $migration)
        if (-not (Test-Path -LiteralPath $sourcePath)) { throw "Missing migration file: $migration" }
        $generatedPath = Join-Path $taskDir ($migration + '.generated.sql')
        New-RewrittenSqlFile -SourcePath $sourcePath -DestinationPath $generatedPath
        $logPath = Join-Path $taskDir ($migration + '.log')
        $exit = Invoke-MySqlFile -Path $generatedPath -Database $schema -LogPath $logPath
        $sha = Get-Sha256 $sourcePath
        Require ('Apply ' + $migration) ($exit -eq 0) ('exit=' + $exit + ', sha256=' + $sha)
        Add-Evidence ('  ' + $migration + ' -> exit 0, sha256=' + $sha)
        if ($exit -ne 0) { Add-Evidence ((Get-Content $logPath -Tail 10) -join ' / ') }
    }

    $v4018OldIndex = Get-Scalar -Sql "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='migration_in' AND index_name='uk_migration_in_batch' AND non_unique=0 AND column_name='transfer_batch_no';" -Database $schema
    Require 'V4_018 schema has old unconditional uk_migration_in_batch' ($v4018OldIndex -eq '1') ('count=' + $v4018OldIndex)
    $v4018ActiveColumn = Get-Scalar -Sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='migration_in' AND column_name='active_transfer_batch_no';" -Database $schema
    Require 'V4_018 schema has no active_transfer_batch_no yet' ($v4018ActiveColumn -eq '0') ('count=' + $v4018ActiveColumn)
    $v4018IndexOutput = Invoke-MySqlText -Sql 'SHOW INDEX FROM migration_in;' -Database $schema -Table
    Add-Evidence ''
    Add-Evidence '--- V4_018 migration_in SHOW INDEX (pre-fix) ---'
    foreach ($line in $v4018IndexOutput.Output) { Add-Evidence ([string]$line) }
    Add-SchemaEvidence '--- V4_018 pre-fix SHOW INDEX FROM migration_in ---'
    foreach ($line in $v4018IndexOutput.Output) { Add-SchemaEvidence ([string]$line) }

    # -----------------------------------------------------------------------------------------
    # 3. Old-version RED: cancelled draft still blocks the batch with duplicate key 1062
    # -----------------------------------------------------------------------------------------
    $redSeedSql = @"
USE $schema;
DELETE FROM residence_archive;
DELETE FROM migration_in;
DELETE FROM migration_out;
DELETE FROM residence;
DELETE FROM household_member;
DELETE FROM application_material;
DELETE FROM sys_approval_log;
DELETE FROM sys_approval_request;
DELETE FROM cancellation_record;
DELETE FROM business_application;
DELETE FROM household WHERE household_id BETWEEN 93001 AND 93020;
DELETE FROM person WHERE person_id BETWEEN 93001 AND 93020;

INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code,version) VALUES
(93001,'Out Person','1','110101199001010011','Dongcheng old address','正常','REGISTERED',0),
(93002,'Target Head','1','110105199002020022','Chaoyang target address','正常','REGISTERED',0),
(93003,'In Person','2','110101199303030033',NULL,'正常','PENDING',0),
(93004,'Target Head Two','1','110105199404040044','Chaoyang target address two','正常','REGISTERED',0);
INSERT INTO household(household_id,household_no,head_person_id,address,region_code,establish_date,status,version) VALUES
(93001,'H-93001',93001,'Dongcheng old address','110101','2020-01-01','正常',0),
(93002,'H-93002',93002,'Chaoyang target address','110105','2020-01-01','正常',0),
(93004,'H-93004',93004,'Chaoyang target address two','110105','2020-01-01','正常',0);
INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) VALUES
(93001,93001,'HEAD','2020-01-01','有效',0),
(93002,93002,'HEAD','2020-01-01','有效',0),
(93004,93004,'HEAD','2020-01-01','有效',0);
INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by,version) VALUES
(93001,93001,'Dongcheng old address','110101','BIRTH','2020-01-01','2020-01-01','有效',3,0),
(93002,93002,'Chaoyang target address','110105','BIRTH','2020-01-01','2020-01-01','有效',3,0),
(93004,93004,'Chaoyang target address two','110105','BIRTH','2020-01-01','2020-01-01','有效',3,0);

INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id)
VALUES('APP-RED-OUT-$tag','MIGRATION_OUT','Issue19 RED completed out',3,'COMPLETED','red completed out',0,93001);
SET @out_app = LAST_INSERT_ID();
INSERT INTO migration_out(application_id,person_id,migration_type,from_region_code,from_household_id,from_address_snapshot,to_region_code,to_address,out_date,reason,transfer_batch_no,business_status,operator_id,executed_at,version)
VALUES(@out_app,93001,'IN_CITY_CROSS_DISTRICT','110101',93001,'Dongcheng old address','110105','Chaoyang new address','2026-11-01','red completed out','TR-RED-$tag','COMPLETED',3,NOW(),0);
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-RED-IN-A-$tag','MIGRATION_IN','Issue19 RED draft A',3,'DRAFT','red draft A',0,93001,93002);
SET @in_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@in_app,93001,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93002,'2026-11-02','red draft A','TR-RED-$tag','DRAFT',0);
UPDATE business_application SET status='CANCELLED', version=version+1 WHERE application_id=@in_app;
SELECT 'RED_CANCELLED_DRAFT' AS section, a.application_id, a.status AS app_status, m.in_id, m.business_status, m.transfer_batch_no, m.version AS migration_version
FROM business_application a JOIN migration_in m ON m.application_id=a.application_id WHERE a.application_id=@in_app;
SELECT 'RED_INDEX' AS section;
SHOW INDEX FROM migration_in;
"@
    $redSeedPath = Join-Path $taskDir 'red-seed.sql'
    Write-Utf8NoBom -Path $redSeedPath -Content $redSeedSql
    $redSeedLog = Join-Path $taskDir 'red-seed.log'
    $redSeedExit = Invoke-MySqlFile -Path $redSeedPath -Database $schema -LogPath $redSeedLog
    Require 'Prepare old-version RED fixture (completed OUT + draft A cancelled only at application level)' ($redSeedExit -eq 0) ('exit=' + $redSeedExit)
    $redState = Get-Scalar -Sql "SELECT CONCAT(app_status,'|',detail_status,'|',transfer_batch_no) FROM (SELECT a.status app_status,m.business_status detail_status,m.transfer_batch_no FROM business_application a JOIN migration_in m ON m.application_id=a.application_id WHERE a.application_no='APP-RED-IN-A-$tag') x;" -Database $schema
    Require 'Old-version cancelled draft keeps migration_in DRAFT and original batch' ($redState -eq "CANCELLED|DRAFT|TR-RED-$tag") ('actual=' + $redState)
    Add-Evidence ''
    Add-Evidence '--- OLD VERSION RED: cancelled draft A snapshot (application CANCELLED, migration_in DRAFT) ---'
    Add-Evidence ('application_status|migration_status|transfer_batch_no = ' + $redState)
    Add-Evidence ('RED fixture: completed same-city cross-district OUT batch TR-RED-' + $tag + ' is legitimate and reusable only if a cancelled DRAFT releases it.')
    Add-Evidence '--- RED duplicate insert SQL ---'
    Add-Evidence 'START TRANSACTION; INSERT business_application(APP-RED-IN-B...); INSERT migration_in(... transfer_batch_no=TR-RED-' + $tag + ' ... business_status=DRAFT); COMMIT;'

    $redDuplicateSql = @"
USE $schema;
START TRANSACTION;
SELECT CONCAT('RED_B_TX_CONNECTION=',CONNECTION_ID());
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-RED-IN-B-$tag','MIGRATION_IN','Issue19 RED draft B',3,'DRAFT','red draft B',0,93001,93002);
SET @b_app = LAST_INSERT_ID();
SELECT CONCAT('RED_B_APPLICATION=',@b_app);
SELECT CONCAT('RED_B_TRX=',trx_id) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=CONNECTION_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@b_app,93001,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93002,'2026-11-02','red draft B','TR-RED-$tag','DRAFT',0);
SELECT 'RED_B_UNEXPECTED_SUCCESS';
COMMIT;
"@
    $redDuplicatePath = Join-Path $taskDir 'red-duplicate.sql'
    Write-Utf8NoBom -Path $redDuplicatePath -Content $redDuplicateSql
    $redDuplicateLog = Join-Path $taskDir 'red-duplicate.log'
    $redDuplicateExit = Invoke-MySqlFile -Path $redDuplicatePath -Database $schema -LogPath $redDuplicateLog
    $redLogText = (Get-Content -LiteralPath $redDuplicateLog -Raw -ErrorAction SilentlyContinue)
    $redIs1062 = ($redDuplicateExit -ne 0) -and ($redLogText -match 'ERROR 1062') -and ($redLogText -match 'uk_migration_in_batch')
    Require 'Old V4_018 schema fails with real duplicate key 1062 on uk_migration_in_batch' $redIs1062 ('exit=' + $redDuplicateExit)
    Add-Evidence ('RED duplicate attempt exit=' + $redDuplicateExit)
    Add-Evidence ('RED duplicate error: ' + (($redLogText -split "`r?`n" | Where-Object { $_ -match 'ERROR 1062' }) -join ' '))
    $redOrphan = Get-Scalar -Sql "SELECT COUNT(*) FROM business_application WHERE application_no='APP-RED-IN-B-$tag';" -Database $schema
    $redBatchRows = Get-Scalar -Sql "SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no='TR-RED-$tag';" -Database $schema
    Require 'RED failed insert rolled back its business_application row' ($redOrphan -eq '0') ('orphan=' + $redOrphan)
    Require 'RED batch still has exactly the one cancelled DRAFT row' ($redBatchRows -eq '1') ('rows=' + $redBatchRows)
    Add-Evidence ('RED transaction aftermath: orphan_application=' + $redOrphan + ', migration_in_rows_for_batch=' + $redBatchRows + ' (implicit rollback on duplicate error)')

    # -----------------------------------------------------------------------------------------
    # 4. Historical cancelled data + upgrade to V4_019 (twice)
    # -----------------------------------------------------------------------------------------
    $historySql = @"
USE $schema;
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-HIST-CANCELLED-001-$tag','MIGRATION_IN','Issue19 history cancelled',3,'CANCELLED','history cancelled',1,93001,93004);
SET @h_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@h_app,93001,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93004,'2026-10-01','history cancelled','TR-HIST-$tag-0007','DRAFT',3);
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-HIST-CANCELLED-002-$tag','MIGRATION_IN','Issue19 history legacy cancelled',3,'CANCELLED','history legacy',5,93003,93004);
SET @l_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@l_app,93003,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93004,'2026-10-02','history legacy','TR-HIST-$tag-0008','CANCELLED',2);
SELECT 'PRE_UPGRADE' AS section, a.application_no, a.status AS app_status, m.in_id, m.business_status, m.transfer_batch_no, m.version
FROM business_application a JOIN migration_in m ON m.application_id=a.application_id
WHERE a.application_no LIKE 'APP-HIST-%-$tag' ORDER BY m.in_id;
SELECT 'PRE_UPGRADE_COUNTS' AS section, (SELECT COUNT(*) FROM migration_in) AS migration_in_rows, (SELECT COUNT(*) FROM business_application) AS application_rows;
"@
    $historyPath = Join-Path $taskDir 'history-seed.sql'
    Write-Utf8NoBom -Path $historyPath -Content $historySql
    $historyLog = Join-Path $taskDir 'history-seed.log'
    $historyExit = Invoke-MySqlFile -Path $historyPath -Database $schema -LogPath $historyLog
    Require 'Prepare historical CANCELLED+DRAFT rows before upgrade' ($historyExit -eq 0) ('exit=' + $historyExit)
    $preCounts = Get-Scalar -Sql 'SELECT CONCAT((SELECT COUNT(*) FROM migration_in),''|'',(SELECT COUNT(*) FROM business_application));' -Database $schema
    Add-Evidence ('Pre-upgrade row counts (migration_in|business_application) = ' + $preCounts)
    $historyLogText = (Get-Content -LiteralPath $historyLog -Raw -ErrorAction SilentlyContinue)
    Add-Evidence '--- pre-upgrade historical snapshot (desensitized) ---'
    foreach ($line in ($historyLogText -split "`r?`n" | Where-Object { $_ -match '^(PRE_UPGRADE|.*APP-HIST-)' })) { Add-Evidence ('  ' + $line) }

    $v4019Source = Join-Path $repo 'doc/database/migrations/V4_019_migration_batch_reuse.sql'
    Require 'V4_019 migration file exists' (Test-Path -LiteralPath $v4019Source) $v4019Source
    $v4019Generated = Join-Path $taskDir 'V4_019.generated.sql'
    New-RewrittenSqlFile -SourcePath $v4019Source -DestinationPath $v4019Generated
    Add-Evidence ('V4_019 sha256=' + (Get-Sha256 $v4019Source))
    Add-Evidence ''
    Add-Evidence '--- V4_019 first application ---'
    $v4019Log1 = Join-Path $taskDir 'v4_019-first.log'
    $v4019Exit1 = Invoke-MySqlFile -Path $v4019Generated -Database $schema -LogPath $v4019Log1
    Require 'Apply V4_019 first time' ($v4019Exit1 -eq 0) ('exit=' + $v4019Exit1)
    $v4019Text1 = (Get-Content -LiteralPath $v4019Log1 -Raw -ErrorAction SilentlyContinue)
    Add-Evidence 'V4_019 first-run output:'
    foreach ($line in ($v4019Text1 -split "`r?`n" | Select-Object -First 160)) { Add-Evidence ('  ' + $line) }
    Add-Evidence ''
    Add-Evidence '--- V4_019 second application (repeatability) ---'
    $v4019Log2 = Join-Path $taskDir 'v4_019-second.log'
    $v4019Exit2 = Invoke-MySqlFile -Path $v4019Generated -Database $schema -LogPath $v4019Log2
    Require 'Apply V4_019 second time (repeatable)' ($v4019Exit2 -eq 0) ('exit=' + $v4019Exit2)
    $v4019Text2 = (Get-Content -LiteralPath $v4019Log2 -Raw -ErrorAction SilentlyContinue)
    Add-Evidence 'V4_019 second-run output:'
    foreach ($line in ($v4019Text2 -split "`r?`n" | Select-Object -First 100)) { Add-Evidence ('  ' + $line) }

    # Structure assertions after upgrade.
    $oldIndexCount = Get-Scalar -Sql "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='migration_in' AND index_name='uk_migration_in_batch';" -Database $schema
    $newIndexCount = Get-Scalar -Sql "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='migration_in' AND index_name='uk_migration_in_active_batch' AND non_unique=0 AND column_name='active_transfer_batch_no';" -Database $schema
    Require 'Old uk_migration_in_batch has been dropped' ($oldIndexCount -eq '0') ('count=' + $oldIndexCount)
    Require 'Conditional unique index uk_migration_in_active_batch exists' ($newIndexCount -eq '1') ('count=' + $newIndexCount)
    $activeCol = Get-Scalar -Sql "SELECT CONCAT(EXTRA,'|',GENERATION_EXPRESSION) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='migration_in' AND column_name='active_transfer_batch_no';" -Database $schema
    Require 'active_transfer_batch_no is a STORED generated column with CANCELLED->NULL' (($activeCol -match 'STORED GENERATED') -and ($activeCol -match 'CANCELLED') -and ($activeCol -match 'transfer_batch_no')) ('definition=' + $activeCol)
    Add-SchemaEvidence '--- V4_019 migration_in SHOW CREATE TABLE ---'
    $showCreate = Invoke-MySqlText -Sql 'SHOW CREATE TABLE migration_in;' -Database $schema -Table
    Add-SchemaEvidence (($showCreate.Output | Out-String).TrimEnd())
    $postIndex = Invoke-MySqlText -Sql 'SHOW INDEX FROM migration_in;' -Database $schema -Table
    Add-SchemaEvidence '--- V4_019 migration_in SHOW INDEX ---'
    Add-SchemaEvidence (($postIndex.Output | Out-String).TrimEnd())
    $statRows = Invoke-MySqlText -Sql "SELECT index_name,non_unique,seq_in_index,column_name,index_type FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='migration_in' ORDER BY index_name,seq_in_index;" -Database $schema -Table
    Add-SchemaEvidence '--- V4_019 information_schema.statistics (migration_in) ---'
    Add-SchemaEvidence (($statRows.Output | Out-String).TrimEnd())
    $colRows = Invoke-MySqlText -Sql "SELECT column_name,column_type,is_nullable,extra,generation_expression FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='migration_in' ORDER BY ordinal_position;" -Database $schema -Table
    Add-SchemaEvidence '--- V4_019 information_schema.columns (migration_in) ---'
    Add-SchemaEvidence (($colRows.Output | Out-String).TrimEnd())
    $backupShow = Invoke-MySqlText -Sql 'SHOW CREATE TABLE migration_in_issue19_backup;' -Database $schema -Table
    Add-SchemaEvidence '--- V4_019 migration_in_issue19_backup structure ---'
    Add-SchemaEvidence (($backupShow.Output | Out-String).TrimEnd())

    # Historical compatibility checks.
    $histSnap = Get-Scalar -Sql "SELECT CONCAT(m.in_id,'|',a.status,'|',m.business_status,'|',m.transfer_batch_no,'|',m.version,'|',COALESCE(m.active_transfer_batch_no,'NULL')) FROM business_application a JOIN migration_in m ON m.application_id=a.application_id WHERE a.application_no='APP-HIST-CANCELLED-001-$tag';" -Database $schema
    $legacySnap = Get-Scalar -Sql "SELECT CONCAT(m.in_id,'|',a.status,'|',m.business_status,'|',m.transfer_batch_no,'|',m.version,'|',COALESCE(m.active_transfer_batch_no,'NULL')) FROM business_application a JOIN migration_in m ON m.application_id=a.application_id WHERE a.application_no='APP-HIST-CANCELLED-002-$tag';" -Database $schema
    Require 'Historical CANCELLED+DRAFT row is state-aligned to CANCELLED during upgrade' ($histSnap -match '\|CANCELLED\|CANCELLED\|TR-HIST-' + [regex]::Escape($tag) + '-0007\|4\|NULL$') ('actual=' + $histSnap)
    Require 'Historical already-CANCELLED row is not touched by upgrade' ($legacySnap -match '\|CANCELLED\|CANCELLED\|TR-HIST-' + [regex]::Escape($tag) + '-0008\|2\|NULL$') ('actual=' + $legacySnap)
    $backupCount = Get-Scalar -Sql "SELECT COUNT(*) FROM migration_in_issue19_backup WHERE original_transfer_batch_no='TR-HIST-$tag-0007' AND original_business_status='DRAFT' AND original_version=3 AND application_status='CANCELLED';" -Database $schema
    Require 'Backup table retains original DRAFT/batch/version snapshot' ($backupCount -eq '1') ('count=' + $backupCount)
    $postCounts = Get-Scalar -Sql 'SELECT CONCAT((SELECT COUNT(*) FROM migration_in),''|'',(SELECT COUNT(*) FROM business_application));' -Database $schema
    Require 'Upgrade did not delete rows (counts unchanged)' ($postCounts -eq $preCounts) ('before=' + $preCounts + ' after=' + $postCounts)
    Add-Evidence ''
    Add-Evidence '--- historical data upgrade snapshot (desensitized: synthetic batch, no person identity selected) ---'
    Add-Evidence ('APP-HIST-CANCELLED-001 (original application CANCELLED + detail DRAFT): ' + $histSnap + '  [in_id|app_status|detail_status|batch|version|active_batch]')
    Add-Evidence ('APP-HIST-CANCELLED-002 (already CANCELLED detail): ' + $legacySnap + '  [in_id|app_status|detail_status|batch|version|active_batch]')
    Add-Evidence ('Backup rows for TR-HIST-' + $tag + '-0007 with original DRAFT/version3: ' + $backupCount)

    # -----------------------------------------------------------------------------------------
    # 5. New behaviour on V4_019 (SQL level)
    # -----------------------------------------------------------------------------------------
    Add-Evidence ''
    Add-Evidence '--- new behaviour on V4_019 ---'
    $newASql = @"
USE $schema;
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-NEW-REUSE-B-$tag','MIGRATION_IN','Issue19 reuse after cancel',3,'DRAFT','reuse after cancel',0,93001,93002);
SET @b_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@b_app,93001,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93002,'2026-11-02','reuse after cancel','TR-RED-$tag','DRAFT',0);
SELECT CONCAT('NEW_A_REUSE_OK|',@b_app,'|',LAST_INSERT_ID(),'|',(SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no='TR-RED-$tag' AND business_status<>'CANCELLED'));
"@
    $newAPath = Join-Path $taskDir 'new-a-reuse.sql'
    Write-Utf8NoBom -Path $newAPath -Content $newASql
    $newAExit = Invoke-MySqlFile -Path $newAPath -Database $schema -LogPath (Join-Path $taskDir 'new-a.log')
    $newALog = Get-Content -LiteralPath (Join-Path $taskDir 'new-a.log') -Raw
    $newARow = $newALog -split "`r?`n" | Where-Object { $_ -match '^NEW_A_REUSE_OK\|' } | Select-Object -First 1
    Require 'Cancelled batch can be reused by a new DRAFT' (($newAExit -eq 0) -and ($newARow -match '\|1$')) ('exit=' + $newAExit + ', result=' + $newARow)
    Add-Evidence ('A. cancelled-reuse: PASS (' + $newARow + ') [appId|inId|activeRows]')

    $newBSql = @"
USE $schema;
START TRANSACTION;
SELECT CONCAT('NEW_B_CONNECTION=',CONNECTION_ID());
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-NEW-DUP-B-$tag','MIGRATION_IN','Issue19 duplicate active',3,'DRAFT','duplicate active',0,93001,93004);
SET @dup_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@dup_app,93001,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93004,'2026-11-03','duplicate active','TR-RED-$tag','DRAFT',0);
SELECT 'NEW_B_UNEXPECTED_SUCCESS';
COMMIT;
"@
    $newBPath = Join-Path $taskDir 'new-b-duplicate.sql'
    Write-Utf8NoBom -Path $newBPath -Content $newBSql
    $newBExit = Invoke-MySqlFile -Path $newBPath -Database $schema -LogPath (Join-Path $taskDir 'new-b.log')
    $newBLog = Get-Content -LiteralPath (Join-Path $taskDir 'new-b.log') -Raw
    $newBActive = Get-Scalar -Sql "SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no='TR-RED-$tag' AND business_status<>'CANCELLED';" -Database $schema
    Require 'Active DRAFT still exclusively occupies the batch (1062 on uk_migration_in_active_batch)' (($newBExit -ne 0) -and ($newBLog -match 'ERROR 1062') -and ($newBLog -match 'uk_migration_in_active_batch')) ('exit=' + $newBExit)
    Require 'Duplicate active attempt leaves exactly one active row' ($newBActive -eq '1') ('active=' + $newBActive)
    Add-Evidence ('B. active exclusivity: PASS (exit=' + $newBExit + ', active_rows=' + $newBActive + ', error=' + (($newBLog -split "`r?`n" | Where-Object { $_ -match 'ERROR 1062' }) -join ' ') + ')')

    $newCSql = @"
USE $schema;
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-NEW-COMPLETED-001-$tag','MIGRATION_IN','Issue19 completed consume',3,'COMPLETED','completed consume',3,93003,93004);
SET @c_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version,executed_at)
VALUES(@c_app,93003,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93004,'2026-11-04','completed consume','TR-NEW-COMPLETED-$tag','COMPLETED',0,NOW());
SELECT CONCAT('NEW_C_COMPLETED_INSERTED|',@c_app);
START TRANSACTION;
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-NEW-COMPLETED-002-$tag','MIGRATION_IN','Issue19 completed duplicate',3,'DRAFT','completed duplicate',0,93003,93002);
SET @c2_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@c2_app,93003,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93002,'2026-11-05','completed duplicate','TR-NEW-COMPLETED-$tag','DRAFT',0);
SELECT 'NEW_C_UNEXPECTED_SUCCESS';
COMMIT;
"@
    $newCPath = Join-Path $taskDir 'new-c-completed.sql'
    Write-Utf8NoBom -Path $newCPath -Content $newCSql
    $newCExit = Invoke-MySqlFile -Path $newCPath -Database $schema -LogPath (Join-Path $taskDir 'new-c.log')
    $newCLog = Get-Content -LiteralPath (Join-Path $taskDir 'new-c.log') -Raw
    $newCRows = Get-Scalar -Sql "SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no='TR-NEW-COMPLETED-$tag';" -Database $schema
    Require 'COMPLETED batch is permanently rejected for a new DRAFT' (($newCExit -ne 0) -and ($newCLog -match 'ERROR 1062') -and ($newCLog -match 'uk_migration_in_active_batch')) ('exit=' + $newCExit)
    Require 'COMPLETED batch retains exactly one row' ($newCRows -eq '1') ('rows=' + $newCRows)
    Add-Evidence ('C. completed permanent consumption: PASS (exit=' + $newCExit + ', rows=' + $newCRows + ', error=' + (($newCLog -split "`r?`n" | Where-Object { $_ -match 'ERROR 1062' }) -join ' ') + ')')

    $newDSql = @"
USE $schema;
START TRANSACTION;
SELECT CONCAT('NEW_D_CONNECTION=',CONNECTION_ID());
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-NEW-ROLLBACK-B-$tag','MIGRATION_IN','Issue19 rollback first',3,'DRAFT','rollback first',0,93003,93002);
SET @r1_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@r1_app,93003,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93002,'2026-11-06','rollback first','TR-NEW-ROLLBACK-$tag','DRAFT',0);
SELECT CONCAT('NEW_D_FIRST_INSERT_OK|',@r1_app,'|',LAST_INSERT_ID());
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-NEW-ROLLBACK-C-$tag','MIGRATION_IN','Issue19 rollback second',3,'DRAFT','rollback second',0,93003,93004);
SET @r2_app = LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@r2_app,93003,'IN_CITY_CROSS_DISTRICT','110101','Dongcheng old address','110105',93004,'2026-11-06','rollback second','TR-NEW-ROLLBACK-$tag','DRAFT',0);
SELECT 'NEW_D_UNEXPECTED_SUCCESS';
COMMIT;
"@
    $newDPath = Join-Path $taskDir 'new-d-rollback.sql'
    Write-Utf8NoBom -Path $newDPath -Content $newDSql
    $newDExit = Invoke-MySqlFile -Path $newDPath -Database $schema -LogPath (Join-Path $taskDir 'new-d.log')
    $newDLog = Get-Content -LiteralPath (Join-Path $taskDir 'new-d.log') -Raw
    $newDOrphans = Get-Scalar -Sql "SELECT COUNT(*) FROM business_application WHERE application_no IN ('APP-NEW-ROLLBACK-B-$tag','APP-NEW-ROLLBACK-C-$tag');" -Database $schema
    $newDRows = Get-Scalar -Sql "SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no='TR-NEW-ROLLBACK-$tag';" -Database $schema
    Require 'Failed duplicate inside a transaction rolls back application + migration detail together' (($newDExit -ne 0) -and ($newDLog -match 'ERROR 1062') -and ($newDOrphans -eq '0') -and ($newDRows -eq '0')) ('exit=' + $newDExit + ', orphan_apps=' + $newDOrphans + ', rows=' + $newDRows)
    Add-Evidence ('D. transaction rollback: PASS (exit=' + $newDExit + ', orphan_applications=' + $newDOrphans + ', migration_rows=' + $newDRows + ')')

    # -----------------------------------------------------------------------------------------
    # 6. Real two-connection concurrency rounds
    # -----------------------------------------------------------------------------------------
    Add-Evidence ''
    Add-Evidence ('--- concurrency: ' + $ConcurrencyRounds + ' claim rounds + ' + $ConcurrencyRounds + ' cancel/create interleave rounds ---')
    $concurrencyRows = New-Object System.Collections.Generic.List[string]

    function Invoke-ConcurrencyRound {
        param([int]$Round, [string]$Kind)
        $batch = "TR-CONC-$tag-$Kind-$Round"
        if ($Kind -eq 'claim') {
            $scriptA = @"
USE $schema;
START TRANSACTION;
SELECT CONCAT('CONN=',CONNECTION_ID());
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id)
VALUES('APP-CONC-$tag-$Kind-$Round-A','MIGRATION_IN','conc A',3,'DRAFT','conc',0,93001);
SET @app=LAST_INSERT_ID();
SELECT CONCAT('APP=',@app);
SELECT CONCAT('TRX=',trx_id) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=CONNECTION_ID();
DO SLEEP(0.9);
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@app,93001,'IN_CITY_CROSS_DISTRICT','110101','addr','110105',93002,'2026-11-09','conc','$batch','DRAFT',0);
SELECT 'INSERT_OK';
DO SLEEP(0.9);
COMMIT;
SELECT 'COMMITTED';
"@
            $scriptB = $scriptA.Replace("APP-CONC-$tag-$Kind-$Round-A", "APP-CONC-$tag-$Kind-$Round-B")
        } else {
            $prepSql = @"
USE $schema;
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-IL-$tag-$Kind-$Round-A','MIGRATION_IN','interleave A',3,'DRAFT','il',0,93003,93002);
SET @a=LAST_INSERT_ID();
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@a,93003,'IN_CITY_CROSS_DISTRICT','110101','addr','110105',93002,'2026-11-09','il','$batch','DRAFT',0);
"@
            Write-Utf8NoBom -Path (Join-Path $taskDir "prep-$Kind-$Round.sql") -Content $prepSql
            $null = Invoke-MySqlFile -Path (Join-Path $taskDir "prep-$Kind-$Round.sql") -Database $schema -LogPath (Join-Path $taskDir "prep-$Kind-$Round.log")
            if ($Round % 2 -eq 1) {
                # create attempts while A is still active; cancel commits after.
                $scriptA = @"
USE $schema;
START TRANSACTION;
SELECT CONCAT('CONN=',CONNECTION_ID());
UPDATE business_application SET status='CANCELLED',version=version+1 WHERE application_no='APP-IL-$tag-$Kind-$Round-A';
SELECT CONCAT('TRX=',trx_id) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=CONNECTION_ID();
DO SLEEP(0.9);
UPDATE migration_in SET business_status='CANCELLED',version=version+1 WHERE transfer_batch_no='$batch';
SELECT 'CANCEL_OK';
COMMIT;
SELECT 'CANCEL_COMMITTED';
"@
                $scriptB = @"
USE $schema;
START TRANSACTION;
SELECT CONCAT('CONN=',CONNECTION_ID());
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-IL-$tag-$Kind-$Round-B','MIGRATION_IN','interleave B',3,'DRAFT','il',0,93003,93004);
SET @app=LAST_INSERT_ID();
SELECT CONCAT('APP=',@app);
SELECT CONCAT('TRX=',trx_id) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=CONNECTION_ID();
DO SLEEP(0.3);
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@app,93003,'IN_CITY_CROSS_DISTRICT','110101','addr','110105',93004,'2026-11-09','il','$batch','DRAFT',0);
SELECT 'INSERT_OK';
COMMIT;
SELECT 'CREATE_COMMITTED';
"@
            } else {
                # cancel detail update first, then create waits for commit and succeeds.
                $scriptA = @"
USE $schema;
START TRANSACTION;
SELECT CONCAT('CONN=',CONNECTION_ID());
UPDATE business_application SET status='CANCELLED',version=version+1 WHERE application_no='APP-IL-$tag-$Kind-$Round-A';
SELECT CONCAT('TRX=',trx_id) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=CONNECTION_ID();
UPDATE migration_in SET business_status='CANCELLED',version=version+1 WHERE transfer_batch_no='$batch';
SELECT 'CANCEL_OK';
DO SLEEP(0.9);
COMMIT;
SELECT 'CANCEL_COMMITTED';
"@
                $scriptB = @"
USE $schema;
START TRANSACTION;
SELECT CONCAT('CONN=',CONNECTION_ID());
INSERT INTO business_application(application_no,business_type,title,applicant_user_id,status,reason,version,target_person_id,target_household_id)
VALUES('APP-IL-$tag-$Kind-$Round-B','MIGRATION_IN','interleave B',3,'DRAFT','il',0,93003,93002);
SET @app=LAST_INSERT_ID();
SELECT CONCAT('APP=',@app);
SELECT CONCAT('TRX=',trx_id) FROM information_schema.innodb_trx WHERE trx_mysql_thread_id=CONNECTION_ID();
DO SLEEP(0.3);
INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,business_status,version)
VALUES(@app,93003,'IN_CITY_CROSS_DISTRICT','110101','addr','110105',93002,'2026-11-09','il','$batch','DRAFT',0);
SELECT 'INSERT_OK';
COMMIT;
SELECT 'CREATE_COMMITTED';
"@
            }
        }
        $fileA = Join-Path $taskDir "round-$Kind-$Round-a.sql"
        $fileB = Join-Path $taskDir "round-$Kind-$Round-b.sql"
        Write-Utf8NoBom -Path $fileA -Content $scriptA
        Write-Utf8NoBom -Path $fileB -Content $scriptB
        $outA = Join-Path $taskDir "round-$Kind-$Round-a.out"
        $errA = Join-Path $taskDir "round-$Kind-$Round-a.err"
        $outB = Join-Path $taskDir "round-$Kind-$Round-b.out"
        $errB = Join-Path $taskDir "round-$Kind-$Round-b.err"
        $sw = [Diagnostics.Stopwatch]::StartNew()
        $pA = Start-MySqlClientProcess -SqlFile $fileA -OutFile $outA -ErrFile $errA
        Start-Sleep -Milliseconds 120
        $pB = Start-MySqlClientProcess -SqlFile $fileB -OutFile $outB -ErrFile $errB
        $waitSamples = New-Object System.Collections.Generic.List[string]
        while ((-not $pA.HasExited -or -not $pB.HasExited) -and $sw.Elapsed.TotalSeconds -lt 40) {
            $waitResult = Invoke-MySqlText -Sql "SELECT CONCAT(trx_id,':',trx_state,':',trx_mysql_thread_id) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'; SELECT CONCAT('DLW=',COUNT(*)) FROM performance_schema.data_lock_waits;" -Database $schema
            foreach ($line in $waitResult.Output) {
                if (-not [string]::IsNullOrWhiteSpace([string]$line) -and -not $waitSamples.Contains([string]$line)) {
                    $waitSamples.Add([string]$line)
                }
            }
            Start-Sleep -Milliseconds 120
        }
        $null = $pA.WaitForExit(15000)
        $null = $pB.WaitForExit(15000)
        $sw.Stop()
        $outAText = (Get-Content -LiteralPath $outA -Raw -ErrorAction SilentlyContinue)
        $errAText = (Get-Content -LiteralPath $errA -Raw -ErrorAction SilentlyContinue)
        $outBText = (Get-Content -LiteralPath $outB -Raw -ErrorAction SilentlyContinue)
        $errBText = (Get-Content -LiteralPath $errB -Raw -ErrorAction SilentlyContinue)
        $active = Get-Scalar -Sql "SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no='$batch' AND business_status<>'CANCELLED';" -Database $schema
        $finalRows = Get-Scalar -Sql "SELECT GROUP_CONCAT(CONCAT(in_id,':',business_status) ORDER BY in_id) FROM migration_in WHERE transfer_batch_no='$batch';" -Database $schema
        $connA = (($outAText -split "`r?`n" | Where-Object { $_ -match '^CONN=' }) -join ','); $connB = (($outBText -split "`r?`n" | Where-Object { $_ -match '^CONN=' }) -join ',')
        $trxA = (($outAText -split "`r?`n" | Where-Object { $_ -match '^TRX=' }) -join ','); $trxB = (($outBText -split "`r?`n" | Where-Object { $_ -match '^TRX=' }) -join ',')
        $connANum = ($connA -replace '^CONN=',''); $connBNum = ($connB -replace '^CONN=','')
        $trxANum = ($trxA -replace '^TRX=',''); $trxBNum = ($trxB -replace '^TRX=','')
        foreach ($sample in $waitSamples) {
            if ($sample -match '^(\d+):LOCK WAIT:(\d+)$') {
                if ($trxANum -eq '' -and $matches[2] -eq $connANum) { $trxANum = $matches[1] }
                if ($trxBNum -eq '' -and $matches[2] -eq $connBNum) { $trxBNum = $matches[1] }
            }
        }
        $outcomeA = if ($outAText -match 'COMMITTED' -or $outAText -match 'CANCEL_COMMITTED') { 'SUCCESS' } elseif ($errAText -match 'ERROR 1062') { 'DUPLICATE' } elseif ($errAText -match 'ERROR 1213') { 'DEADLOCK' } else { 'OTHER' }
        $outcomeB = if ($outBText -match 'COMMITTED' -or $outBText -match 'CREATE_COMMITTED') { 'SUCCESS' } elseif ($errBText -match 'ERROR 1062') { 'DUPLICATE' } elseif ($errBText -match 'ERROR 1213') { 'DEADLOCK' } else { 'OTHER' }
        $detail = ('round=' + $Round + ' kind=' + $Kind + ' batch=' + $batch +
            ' | A(conn=' + $connANum + ',trx=' + $trxANum + ',exit=' + $pA.ExitCode + ',outcome=' + $outcomeA + ')' +
            ' | B(conn=' + $connBNum + ',trx=' + $trxBNum + ',exit=' + $pB.ExitCode + ',outcome=' + $outcomeB + ')' +
            ' | wait_events=' + $(if ($waitSamples.Count) { $waitSamples -join ';' } else { 'none-observed' }) +
            ' | final_active=' + $active + ' | final_rows=' + $finalRows + ' | elapsed_ms=' + ([int]$sw.Elapsed.TotalMilliseconds))
        $concurrencyRows.Add($detail)
        Add-Evidence $detail
        if ($Kind -eq 'claim') {
            Check ('claim round ' + $Round + ' exactly one winner') (($outcomeA -eq 'SUCCESS') -xor ($outcomeB -eq 'SUCCESS')) ('A=' + $outcomeA + ', B=' + $outcomeB)
            Check ('claim round ' + $Round + ' one active row') ($active -eq '1') ('active=' + $active)
        } else {
            Check ('interleave round ' + $Round + ' no double active claim') ((([int]$active) -le 1)) ('active=' + $active + ', rows=' + $finalRows)
            Check ('interleave round ' + $Round + ' cancel succeeds') ($outcomeA -eq 'SUCCESS' -or $outcomeA -eq 'DEADLOCK') ('A=' + $outcomeA)
        }
    }

    for ($r = 1; $r -le $ConcurrencyRounds; $r++) { Invoke-ConcurrencyRound -Round $r -Kind 'claim' }
    for ($r = 1; $r -le $ConcurrencyRounds; $r++) { Invoke-ConcurrencyRound -Round $r -Kind 'interleave' }

    # -----------------------------------------------------------------------------------------
    # 7. Maven regression (H2 paths, same worktree)
    # -----------------------------------------------------------------------------------------
    Add-Evidence ''
    Add-Evidence '--- Maven regression ---'
    if ($SkipMaven) {
        Add-Evidence 'Maven regression: SKIPPED by -SkipMaven'
    } else {
        $mvnLog = Join-Path $taskDir 'maven-regression.log'
        Push-Location $repo
        $previousEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & .\mvnw.cmd '-Dtest=Issue19MigrationBatchReuseIntegrationTest,AiConfirmIntegrationTest,AiPrecheckIntegrationTest,Phase03MigrationIntegrationTest' test *> $mvnLog
            $mvnExit = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousEap
            Pop-Location
        }
        Add-Evidence ('Maven command: .\mvnw.cmd -Dtest=Issue19MigrationBatchReuseIntegrationTest,AiConfirmIntegrationTest,AiPrecheckIntegrationTest,Phase03MigrationIntegrationTest test')
        Add-Evidence ('Maven exit code: ' + $mvnExit)
        Add-Evidence 'Maven log tail:'
        foreach ($line in (Get-Content -LiteralPath $mvnLog -Tail 30)) { Add-Evidence ('  ' + $line) }
        if ($mvnExit -ne 0) {
            Check 'Maven regression suite' $false ('exit=' + $mvnExit + ', log=' + $mvnLog)
        } else {
            Check 'Maven regression suite' $true ('exit=0, log=' + $mvnLog)
        }
        $surefireClasses = @(
            'com.wjx871.population.migration.Issue19MigrationBatchReuseIntegrationTest',
            'com.wjx871.population.ai.precheck.AiConfirmIntegrationTest',
            'com.wjx871.population.ai.precheck.AiPrecheckIntegrationTest',
            'com.wjx871.population.migration.Phase03MigrationIntegrationTest'
        )
        foreach ($class in $surefireClasses) {
            $report = Join-Path $repo ('target/surefire-reports/TEST-' + $class + '.xml')
            if (Test-Path -LiteralPath $report) {
                [xml]$xml = Get-Content -LiteralPath $report -Raw
                $attrs = $xml.testsuite
                Add-Evidence ('  ' + $class + ': tests=' + $attrs.tests + ' failures=' + $attrs.failures + ' errors=' + $attrs.errors + ' skipped=' + $attrs.skipped)
            } else {
                Add-Evidence ('  ' + $class + ': surefire report missing (' + $report + ')')
            }
        }
    }

    $script:aborted = $null
} catch {
    $script:aborted = $_.Exception.Message
    Add-Evidence ('FATAL: ' + $script:aborted)
    Add-Evidence ($_.ScriptStackTrace)
} finally {
    if ($serverStarted) {
        if ($rootPasswordSet) {
            $oldPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
            [Environment]::SetEnvironmentVariable('MYSQL_PWD', $adminPassword, 'Process')
            & $mysqladminExe --protocol=TCP --host=127.0.0.1 "--port=$Port" --user=root shutdown 2>&1 | Out-Null
            [Environment]::SetEnvironmentVariable('MYSQL_PWD', $oldPwd, 'Process')
        }
        $stopped = $false
        for ($i = 0; $i -lt 100; $i++) {
            if (-not (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)) { $stopped = $true; break }
            Start-Sleep -Milliseconds 200
        }
        Add-Evidence ('Temporary mysqld shutdown: ' + $(if ($stopped) { 'PASS (port ' + $Port + ' no longer listening)' } else { 'FAIL (port still listening)' }))
        if (-not $stopped) { $script:checkFailures.Add('temporary mysqld shutdown') }
    }
    Write-EvidenceFiles
}

if ($script:aborted -or $script:checkFailures.Count -gt 0) { exit 1 }
exit 0
