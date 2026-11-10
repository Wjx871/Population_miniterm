[CmdletBinding()]
param(
    [string]$TestSelection = 'Issue3OcrMySql84IT,Issue3CommitMySql84IT,Issue3ResourceMySql84IT,Phase14PersonIdCardOcrIntegrationTest',
    [string]$OutputRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$mysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.4\bin'
$mysqldExe = Join-Path $mysqlBin 'mysqld.exe'
$mysqlExe = Join-Path $mysqlBin 'mysql.exe'
foreach ($exe in @($mysqldExe, $mysqlExe)) {
    if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { throw "Required MySQL 8.4 executable not found: $exe" }
}
$binaryVersion = (& $mysqldExe --no-defaults --version | Out-String).Trim()
if ($binaryVersion -notmatch '8\.4\.') { throw 'Expected the isolated MySQL 8.4 binary.' }

$classFile = Join-Path $repo 'src\test\java\com\wjx871\population\person\idcard\Issue3OcrMySql84IT.java'
$baseTest = Join-Path $repo 'src\test\java\com\wjx871\population\person\idcard\Issue3OcrTransactionIntegrationTest.java'
$freshSql = Join-Path $repo 'doc\database\population_miniterm.sql'
foreach ($requiredPath in @($classFile, $baseTest, $freshSql, (Join-Path $repo 'mvnw.cmd'))) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) { throw "Required gate input not found: $requiredPath" }
}

if ([string]::IsNullOrWhiteSpace($TestSelection) -or $TestSelection -notmatch '^[A-Za-z0-9_,]+$') {
    throw 'TestSelection may contain only Maven test class names separated by commas.'
}

$stamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssfffZ')
$runId = [Guid]::NewGuid().ToString('N')
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $runRoot = Join-Path $env:TEMP "population_mysql84_issue3_${stamp}_${runId}"
} else {
    $runRoot = [IO.Path]::GetFullPath($OutputRoot)
}
if (Test-Path -LiteralPath $runRoot) { throw "Refusing to overwrite existing run directory: $runRoot" }
$null = New-Item -ItemType Directory -Path $runRoot -ErrorAction Stop
$aclOutput = & icacls $runRoot /inheritance:r /grant:r "${env:USERNAME}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' 'Administrators:(OI)(CI)F' 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict the private instance directory ACL.' }

$datadir = Join-Path $runRoot 'data'
$reportDir = Join-Path $runRoot 'surefire-reports'
$mysqldOut = Join-Path $runRoot 'mysqld.stdout.log'
$mysqldErr = Join-Path $runRoot 'mysqld.stderr.log'
$portProbe = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$portProbe.Start()
try { $port = $portProbe.LocalEndpoint.Port } finally { $portProbe.Stop() }
if ($port -eq 3306) { throw 'Refusing the default database port.' }
$schema = "population_miniterm_mysql_gate_issue3_$($runId.Substring(0,12))"
$appUser = "i3_$($runId.Substring(0,12))"
$adminPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$appPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$server = $null
$ownedEndpoint = $false
$processEnvNames = @('MYSQL84_GATE_DB_URL','MYSQL84_GATE_DB_USERNAME','MYSQL84_GATE_DB_PASSWORD',
    'MYSQL84_GATE_ADMIN_USERNAME','MYSQL84_GATE_ADMIN_PASSWORD','MYSQL84_GATE_EXPECTED_DATADIR','MYSQL_PWD',
    'SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD',
    'SPRING_DATASOURCE_DRIVER_CLASS_NAME','SPRING_SQL_INIT_MODE',
    'SPRING_DATASOURCE_HIKARI_TRANSACTION_ISOLATION')
$oldEnv = @{}
foreach ($name in $processEnvNames) { $oldEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }

function Write-NewFile {
    param([string]$Path, [string]$Content)
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
        try { $writer.Write($Content) } finally { $writer.Dispose() }
    } finally { if ($stream) { $stream.Dispose() } }
}

function Invoke-MySql {
    param([string]$Sql, [string]$User, [string]$Password)
    $previousPwd = [Environment]::GetEnvironmentVariable('MYSQL_PWD', 'Process')
    $previousOut = [Console]::OutputEncoding
    $previousIn = [Console]::InputEncoding
    try {
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $Password, 'Process')
        [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
        [Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
        $output = @($Sql | & $mysqlExe --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$port" "--user=$User" --connect-timeout=2 --default-character-set=utf8mb4 --batch --skip-column-names 2>&1)
        if ($LASTEXITCODE -ne 0) {
            $diagnostic = ($output -join ' ').Replace($adminPassword, '[REDACTED]').Replace($appPassword, '[REDACTED]')
            throw "MySQL command failed (exit $LASTEXITCODE): $diagnostic"
        }
        return $output
    } finally {
        [Console]::OutputEncoding = $previousOut
        [Console]::InputEncoding = $previousIn
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $previousPwd, 'Process')
    }
}

try {
    $null = New-Item -ItemType Directory -Path $datadir -ErrorAction Stop
    $null = New-Item -ItemType Directory -Path $reportDir -ErrorAction Stop

    Write-Host "ISSUE3_MYSQL_RUN=$runRoot"
    & $mysqldExe --no-defaults --initialize-insecure "--basedir=$mysqlBin\.." "--datadir=$datadir" *> (Join-Path $runRoot 'mysql-initialize.log')
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialize failed (exit $LASTEXITCODE); inspect the private run log." }

    $serverArgs = @(
        '--no-defaults',
        "--basedir=`"$($mysqlBin -replace '\\','/')/..`"",
        "--datadir=`"$($datadir -replace '\\','/')`"",
        "--port=$port", '--bind-address=127.0.0.1', '--mysqlx=0',
        '--transaction-isolation=REPEATABLE-READ', '--character-set-server=utf8mb4',
        '--collation-server=utf8mb4_unicode_ci', '--console'
    ) -join ' '
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = $mysqldExe
    $startInfo.Arguments = $serverArgs
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $server = [Diagnostics.Process]::Start($startInfo)
    if (-not $server) { throw 'Could not start the private MySQL instance.' }
    $stdoutTask = $server.StandardOutput.ReadToEndAsync()
    $stderrTask = $server.StandardError.ReadToEndAsync()

    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($server.HasExited) { throw "Private MySQL exited during startup (code $($server.ExitCode))." }
        try {
            $null = Invoke-MySql -Sql 'SELECT 1;' -User 'root' -Password ''
            $ready = $true
            break
        } catch { Start-Sleep -Milliseconds 500 }
    }
    if (-not $ready) { throw 'Private MySQL did not become ready within 30 seconds.' }

    # Verify ownership before changing accounts or schemas, including a possible bind-port race.
    $identity = @(Invoke-MySql -Sql 'SELECT @@datadir,@@port,@@version,@@transaction_isolation;' -User 'root' -Password '')
    $identityColumns = ([string]$identity[-1]).Split([char]9)
    $actualData = [IO.Path]::GetFullPath($identityColumns[0]).TrimEnd('\','/')
    $expectedData = [IO.Path]::GetFullPath($datadir).TrimEnd('\','/')
    if ($actualData -ne $expectedData -or [int]$identityColumns[1] -ne $port -or
        $identityColumns[2] -notmatch '^8\.4\.' -or $identityColumns[3] -ne 'REPEATABLE-READ') {
        throw 'The endpoint is not the private MySQL 8.4 instance; refusing all mutations.'
    }
    $ownedEndpoint = $true
    Write-NewFile -Path (Join-Path $runRoot 'identity.txt') -Content (
        "binary=$binaryVersion`nport=$port`ndatadir=$actualData`nisolation=$($identityColumns[3])`nschema=$schema`nappUser=$appUser`n")

    $bootstrapSql = "ALTER USER 'root'@'localhost' IDENTIFIED BY '$adminPassword';`n" +
        "CREATE DATABASE ``$schema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`n" +
        "CREATE USER '$appUser'@'127.0.0.1' IDENTIFIED BY '$appPassword';`n" +
        "GRANT SELECT,INSERT,UPDATE,DELETE ON ``$schema``.* TO '$appUser'@'127.0.0.1';`n"
    $null = Invoke-MySql -Sql $bootstrapSql -User 'root' -Password ''
    $probeSql = "UPDATE performance_schema.setup_instruments SET ENABLED='YES',TIMED='YES' WHERE NAME='transaction';`n" +
        "UPDATE performance_schema.setup_consumers SET ENABLED='YES' WHERE NAME IN " +
        "('events_transactions_current','events_transactions_history','events_statements_history');"
    $null = Invoke-MySql -Sql $probeSql -User 'root' -Password $adminPassword

    function Initialize-PrivateSchema {
        param([string]$schema)
        $generatedSql = Join-Path $runRoot ($schema + '-fresh-schema.sql')
        $sql = [IO.File]::ReadAllText($freshSql, [Text.Encoding]::UTF8)
        $headerPattern = '(?ms)\ACREATE DATABASE IF NOT EXISTS population_miniterm\s+DEFAULT CHARACTER SET utf8mb4\s+DEFAULT COLLATE utf8mb4_unicode_ci;\s+USE population_miniterm;'
        $replacement = "CREATE DATABASE IF NOT EXISTS ``$schema`` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;`r`nUSE ``$schema``;"
        if ([regex]::Matches($sql, $headerPattern).Count -ne 1) { throw 'Fresh SQL header did not match the expected single database declaration.' }
        $rewrittenSql = [regex]::Replace($sql, $headerPattern, $replacement, 1)
        Write-NewFile -Path $generatedSql -Content $rewrittenSql
        [Environment]::SetEnvironmentVariable('MYSQL_PWD', $adminPassword, 'Process')
        $previousOut = [Console]::OutputEncoding
        $previousIn = [Console]::InputEncoding
        try {
            [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
            [Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
            Get-Content -LiteralPath $generatedSql -Raw -Encoding UTF8 |
                & $mysqlExe --no-defaults --protocol=TCP --host=127.0.0.1 "--port=$port" --user=root "--database=$schema" --default-character-set=utf8mb4
            if ($LASTEXITCODE -ne 0) { throw "Fresh schema import failed (exit $LASTEXITCODE); inspect private run logs." }
        } finally {
            [Console]::OutputEncoding = $previousOut
            [Console]::InputEncoding = $previousIn
            [Environment]::SetEnvironmentVariable('MYSQL_PWD', $null, 'Process')
        }

        # The canonical SQL contains V4_001..010; published migrations add the later application schema.
        $schemaHashes = @("fresh=$((Get-FileHash -LiteralPath $freshSql -Algorithm SHA256).Hash)")
        foreach ($migration in @('V4_011_application_return.sql', 'V4_012_dashboard_viewer_scope.sql',
                'V4_013_person_idcard_image.sql', 'V4_015_ai_precheck_async.sql',
                'V4_016_s4a_entity_resolution.sql', 'V4_017_s4a_resolution_retention_indexes.sql',
                'V4_018_s4b_confirm_draft_binding.sql')) {
            $sourcePath = Join-Path $repo ('doc/database/migrations/' + $migration)
            $migrationSql = [IO.File]::ReadAllText($sourcePath, [Text.Encoding]::UTF8)
            $migrationSql = [regex]::Replace($migrationSql, '(?im)^\s*USE\s+population_miniterm\s*;', ('USE ' + $schema + ';'))
            $null = Invoke-MySql -Sql ("USE ``$schema``;`n" + $migrationSql) -User 'root' -Password $adminPassword
            $schemaHashes += "$migration=$((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash)"
        }
        Write-NewFile -Path (Join-Path $runRoot ($schema + '-schema-inputs.txt')) -Content ($schemaHashes -join "`n")

        # Keep the canonical synthetic fixture IDs used by the H2 integration tests.
        $seedSql = "USE ``$schema``; UPDATE sys_user SET user_id=user_id+10000; " +
            "UPDATE sys_user SET user_id=CASE username WHEN 'viewer' THEN 1 WHEN 'population' THEN 2 " +
            "WHEN 'admin' THEN 3 WHEN 'household' THEN 6 WHEN 'approver' THEN 7 ELSE user_id END; " +
            "SELECT user_id,username FROM sys_user ORDER BY user_id;"
        # Phase14's H2 schema also has an outsider approver; canonical MySQL seed does not.
        # Copy only synthetic fixture credentials/role, and keep its region separate.
        $seedSql += " INSERT INTO sys_department(department_code,department_name,region_code,status,sort_no) " +
            "VALUES('APPROVAL_OTHER','Synthetic outside approval','120000','ENABLED',6); " +
            "INSERT INTO sys_user(username,password_hash,role_id,department_id,real_name,status) " +
            "SELECT 'approver-other',u.password_hash,u.role_id,d.department_id,'Synthetic outsider','ENABLED' " +
            "FROM sys_user u JOIN sys_department d ON d.department_code='APPROVAL_OTHER' WHERE u.username='approver';"
        $seedIdentity = Invoke-MySql -Sql $seedSql -User 'root' -Password $adminPassword
        Write-NewFile -Path (Join-Path $runRoot ($schema + '-test-seed.txt')) -Content ($seedIdentity -join "`n")
    }

    [Environment]::SetEnvironmentVariable('MYSQL84_GATE_DB_URL', "jdbc:mysql://127.0.0.1:$port/$schema`?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=false", 'Process')
    [Environment]::SetEnvironmentVariable('MYSQL84_GATE_DB_USERNAME', $appUser, 'Process')
    [Environment]::SetEnvironmentVariable('MYSQL84_GATE_DB_PASSWORD', $appPassword, 'Process')
    [Environment]::SetEnvironmentVariable('MYSQL84_GATE_ADMIN_USERNAME', 'root', 'Process')
    [Environment]::SetEnvironmentVariable('MYSQL84_GATE_ADMIN_PASSWORD', $adminPassword, 'Process')
    [Environment]::SetEnvironmentVariable('MYSQL84_GATE_EXPECTED_DATADIR', $datadir, 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_URL', $env:MYSQL84_GATE_DB_URL, 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_USERNAME', $appUser, 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_PASSWORD', $appPassword, 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_DRIVER_CLASS_NAME', 'com.mysql.cj.jdbc.Driver', 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_SQL_INIT_MODE', 'never', 'Process')
    [Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_HIKARI_TRANSACTION_ISOLATION', 'TRANSACTION_REPEATABLE_READ', 'Process')

    $failedClasses = [Collections.Generic.List[string]]::new()
    $classIndex = 0
    foreach ($testClass in $TestSelection.Split(',')) {
        $classSchema = if ($classIndex -eq 0) { $schema } else { $schema + '_' + $classIndex }
        if ($classIndex -gt 0) {
            $null = Invoke-MySql -Sql ("CREATE DATABASE ``$classSchema`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; " +
                "GRANT SELECT,INSERT,UPDATE,DELETE ON ``$classSchema``.* TO '$appUser'@'127.0.0.1';") -User 'root' -Password $adminPassword
        }
        Initialize-PrivateSchema -schema $classSchema
        $classUrl = "jdbc:mysql://127.0.0.1:$port/$classSchema`?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=false"
        [Environment]::SetEnvironmentVariable('MYSQL84_GATE_DB_URL', $classUrl, 'Process')
        [Environment]::SetEnvironmentVariable('SPRING_DATASOURCE_URL', $classUrl, 'Process')
        $classLog = Join-Path $runRoot ($testClass + '.maven.log')
        Push-Location $repo
        try {
            $previousEap = $ErrorActionPreference
            $ErrorActionPreference = 'Continue'
            try {
                & .\mvnw.cmd ('-Dtest=' + $testClass) '-Dspring.test.mockmvc.print=NONE' test *> $classLog
                $mavenExit = $LASTEXITCODE
            } finally { $ErrorActionPreference = $previousEap }
            Get-ChildItem -LiteralPath (Join-Path $repo 'target/surefire-reports') -File -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match ([regex]::Escape('.' + $testClass + '.')) } |
                ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $reportDir $_.Name) }
            Write-Host "ISSUE3_MYSQL_TEST=$testClass EXIT=$mavenExit SCHEMA=$classSchema LOG=$classLog"
            if ($mavenExit -ne 0) { $failedClasses.Add($testClass) }
        } finally { Pop-Location }
        $classIndex++
    }
    if ($failedClasses.Count -gt 0) { throw ('Maven gate failed: ' + ($failedClasses -join ',') + '; inspect ' + $runRoot) }

    Write-Host "Issue3 MySQL gate completed. Run directory: $runRoot"
    Write-Host "Surefire reports: $reportDir"
} finally {
    foreach ($name in $processEnvNames) { [Environment]::SetEnvironmentVariable($name, $oldEnv[$name], 'Process') }
    if ($ownedEndpoint -and $server -and -not $server.HasExited) {
        try {
            $null = Invoke-MySql -Sql 'SHUTDOWN;' -User 'root' -Password $adminPassword
            if (-not $server.WaitForExit(15000)) { throw 'MySQL graceful shutdown timed out.' }
        } catch {
            if ($server -and -not $server.HasExited) { Write-Warning 'Could not confirm graceful shutdown of the private MySQL process; datadir was retained.' }
        }
    }
    if ($server -and $server.HasExited) {
        Write-NewFile -Path $mysqldOut -Content $stdoutTask.Result
        Write-NewFile -Path $mysqldErr -Content $stderrTask.Result
        Write-Host "ISSUE3_MYSQL_SHUTDOWN=confirmed pid=$($server.Id)"
    }
}
