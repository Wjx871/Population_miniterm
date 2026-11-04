[CmdletBinding()]
param(
    [ValidateSet('Mock', 'Rabbit')]
    [string]$Mode = 'Mock',
    [int]$TimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

if ($Mode -eq 'Mock') {
    Push-Location $repoRoot
    try {
        & (Join-Path $repoRoot 'mvnw.cmd') '-Dtest=AiPrecheckIntegrationTest,DocumentAnalysisResponseValidatorTest,PopulationMinitermApplicationTest' test
        if ($LASTEXITCODE -ne 0) {
            throw "H2/Mock Maven verification failed with exit code $LASTEXITCODE"
        }
        Write-Host 'H2/Mock verification passed. This mode does not start or verify RabbitMQ.'
    }
    finally {
        Pop-Location
    }
    return
}

$docker = Get-Command docker -ErrorAction SilentlyContinue
if ($null -eq $docker) {
    throw 'Docker CLI is unavailable. No software was installed or changed. Use -Mode Mock for H2/Mock verification.'
}
& $docker.Source info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker Engine is unavailable. No container or volume was created. Use -Mode Mock for H2/Mock verification.'
}

$runId = [Guid]::NewGuid().ToString('N')
$containerName = "s3-rabbit-$runId"
$volumeName = "s3-rabbit-data-$runId"
$image = 'rabbitmq:4.3.7-alpine'
$runDirectory = Join-Path $repoRoot "target\s3-rabbit-demo-$runId"
$storageDirectory = Join-Path $runDirectory 'private-stage'
$stdoutLog = Join-Path $runDirectory 'backend.stdout.log'
$stderrLog = Join-Path $runDirectory 'backend.stderr.log'
$dockerLog = Join-Path $runDirectory 'docker.stderr.log'
$createdContainer = $false
$createdVolume = $false
$backendProcess = $null
$httpClient = $null
$storageCreated = $false
$savedEnvironment = @{}
$envNames = @(
    'SERVER_ADDRESS', 'SERVER_PORT',
    'AI_PRECHECK_ENABLED', 'AI_PRECHECK_RABBIT_ENABLED', 'AI_PRECHECK_STORAGE_DIR',
    'AI_PRECHECK_ANALYSIS_CLIENT', 'AI_PRECHECK_MOCK_MODE', 'AI_PRECHECK_MOCK_FAILS_BEFORE_SUCCESS',
    'AI_PRECHECK_QUEUE_CAPACITY', 'AI_PRECHECK_MAX_ATTEMPTS', 'AI_PRECHECK_OUTBOX_POLL_MS',
    'RABBITMQ_HOST', 'RABBITMQ_PORT', 'RABBITMQ_USERNAME', 'RABBITMQ_PASSWORD', 'RABBITMQ_VHOST',
    'SPRING_RABBITMQ_HOST', 'SPRING_RABBITMQ_PORT', 'SPRING_RABBITMQ_USERNAME',
    'SPRING_RABBITMQ_PASSWORD', 'SPRING_RABBITMQ_VIRTUALHOST', 'SPRING_RABBITMQ_PUBLISHERCONFIRMTYPE',
    'SPRING_RABBITMQ_PUBLISHERRETURNS', 'SPRING_RABBITMQ_TEMPLATE_MANDATORY',
    'DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'SPRING_DATASOURCE_URL',
    'SPRING_DATASOURCE_DRIVERCLASSNAME', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD',
    'SPRING_SQL_INIT_MODE', 'SPRING_APPLICATION_JSON'
)
foreach ($name in $envNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Get-FreeLoopbackPort {
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    }
    finally {
        $listener.Stop()
    }
}

function Test-LoopbackPort([int]$Port) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync('127.0.0.1', $Port)
        return $task.Wait(500) -and $client.Connected
    }
    catch {
        return $false
    }
    finally {
        $client.Dispose()
    }
}

function Wait-LoopbackPort([int]$Port, [int]$Seconds, [string]$Description) {
    $until = [DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $until) {
        if (Test-LoopbackPort $Port) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "$Description did not become ready on 127.0.0.1:$Port"
}

function Stop-OwnedProcessTree([System.Diagnostics.Process]$Process) {
    if ($null -eq $Process -or $Process.HasExited) { return }
    $rootId = [int]$Process.Id
    $ownedRoot = Get-CimInstance Win32_Process -Filter "ProcessId=$rootId" -ErrorAction SilentlyContinue
    if ($null -eq $ownedRoot) { return }
    $expectedStart = $Process.StartTime.ToUniversalTime()
    $actualStart = [System.Management.ManagementDateTimeConverter]::ToDateTime($ownedRoot.CreationDate).ToUniversalTime()
    if ([Math]::Abs(($actualStart - $expectedStart).TotalSeconds) -gt 2) {
        throw 'Backend PID no longer matches the process started by this script; refusing to stop it.'
    }
    & (Join-Path $env:SystemRoot 'System32\taskkill.exe') /PID $rootId /T /F *> $null
}

function Get-ResponseJson([System.Net.Http.HttpResponseMessage]$Response) {
    $body = $Response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $Response.IsSuccessStatusCode) {
        throw "HTTP $([int]$Response.StatusCode) from the isolated demo backend: $body"
    }
    return ($body | ConvertFrom-Json)
}

function Get-LoginToken([string]$BaseUrl, [string]$Username) {
    $json = @{ username = $Username; password = '123456' } | ConvertTo-Json -Compress
    $content = [System.Net.Http.StringContent]::new($json, [System.Text.Encoding]::UTF8, 'application/json')
    $response = $httpClient.PostAsync("$BaseUrl/api/auth/login", $content).GetAwaiter().GetResult()
    $body = Get-ResponseJson $response
    return [string]$body.data.token
}

function New-SyntheticTask([string]$BaseUrl, [string]$Token, [string]$Key) {
    $fileText = "%PDF-1.7`n% Synthetic S3 integration fixture. No real personal data.`n%%EOF`n"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($fileText)
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $filePart = [System.Net.Http.ByteArrayContent]::new($bytes)
    $filePart.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('application/pdf')
    $multipart.Add($filePart, 'file', 'synthetic-proof.pdf')
    $uploadRequest = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        "$BaseUrl/api/ai/materials?businessType=MIGRATION_IN&documentRoleHint=IDENTITY_CARD_FRONT")
    $uploadRequest.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    $uploadRequest.Headers.Add('Idempotency-Key', "upload-$Key")
    $uploadRequest.Content = $multipart
    $uploadResponse = $httpClient.SendAsync($uploadRequest).GetAwaiter().GetResult()
    if ([int]$uploadResponse.StatusCode -ne 201) {
        throw "Synthetic upload expected HTTP 201, received $([int]$uploadResponse.StatusCode): $($uploadResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult())"
    }
    $stageId = [string](Get-ResponseJson $uploadResponse).data.stageMaterialId
    $taskJson = @{
        requestSchemaVersion = '1.0'
        taskType = 'DOCUMENT_PRECHECK'
        businessType = 'MIGRATION_IN'
        migrationType = 'OUTSIDE_CITY'
        stageMaterialIds = @($stageId)
        idempotencyKey = "task-$Key"
    } | ConvertTo-Json -Depth 4 -Compress
    $taskContent = [System.Net.Http.StringContent]::new($taskJson, [System.Text.Encoding]::UTF8, 'application/json')
    $taskRequest = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post, "$BaseUrl/api/ai/tasks")
    $taskRequest.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    $taskRequest.Content = $taskContent
    $taskResponse = $httpClient.SendAsync($taskRequest).GetAwaiter().GetResult()
    if ([int]$taskResponse.StatusCode -ne 202) {
        throw "Task acceptance expected HTTP 202, received $([int]$taskResponse.StatusCode): $($taskResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult())"
    }
    return (Get-ResponseJson $taskResponse).data
}

function Wait-TaskStatus([string]$BaseUrl, [string]$Token, [string]$TaskId, [string[]]$Expected, [int]$Seconds) {
    $until = [DateTime]::UtcNow.AddSeconds($Seconds)
    $latest = $null
    while ([DateTime]::UtcNow -lt $until) {
        $request = [System.Net.Http.HttpRequestMessage]::new(
            [System.Net.Http.HttpMethod]::Get, "$BaseUrl/api/ai/tasks/$TaskId")
        $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
        $response = $httpClient.SendAsync($request).GetAwaiter().GetResult()
        $latest = Get-ResponseJson $response
        $status = [string]$latest.data.status
        if ($Expected -contains $status) { return $latest.data }
        if ($status -eq 'FAILED') { throw "Demo task $TaskId reached FAILED: $($latest.data.failure.code)" }
        Start-Sleep -Seconds 1
    }
    throw "Task $TaskId did not reach one of [$($Expected -join ', ')] before timeout; last status was $($latest.data.status)"
}

function Assert-BackendEvidence([string]$TaskId, [bool]$RequireRetry) {
    $until = [DateTime]::UtcNow.AddSeconds(10)
    $logText = ''
    while ([DateTime]::UtcNow -lt $until) {
        foreach ($path in @($stdoutLog, $stderrLog)) {
            if (Test-Path -LiteralPath $path) { $logText += [IO.File]::ReadAllText($path) }
        }
        $published = $logText.Contains("AI outbox publish confirmed") -and $logText.Contains("taskId=$TaskId")
        $consumed = $logText.Contains("AI precheck task started taskId=$TaskId")
        $retried = $logText.Contains("AI precheck retry scheduled taskId=$TaskId")
        if ($published -and $consumed -and (-not $RequireRetry -or $retried)) { return }
        Start-Sleep -Milliseconds 250
    }
    throw "Backend log evidence for publisher confirm/worker$(if ($RequireRetry) { '/retry' }) was not found for task $TaskId. Logs: $runDirectory"
}

try {
    if (Test-Path -LiteralPath $runDirectory) {
        throw 'Unique demo output directory unexpectedly exists; refusing to reuse or clean it.'
    }
    New-Item -ItemType Directory -Path $runDirectory -ErrorAction Stop | Out-Null
    New-Item -ItemType Directory -Path $storageDirectory -ErrorAction Stop | Out-Null
    $storageCreated = $true
    $dockerLog = Join-Path $runDirectory 'docker.stderr.log'

    $port = Get-FreeLoopbackPort
    $credentials = [Guid]::NewGuid().ToString('N')
    $password = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')

    & $docker.Source inspect $containerName *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Unique demo container name unexpectedly exists; refusing to reuse it.' }
    & $docker.Source inspect --type volume $volumeName *> $null
    if ($LASTEXITCODE -eq 0) { throw 'Unique demo volume name unexpectedly exists; refusing to reuse it.' }

    $createdVolumeName = & $docker.Source volume create $volumeName 2>> $dockerLog
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create the uniquely named RabbitMQ demo volume.' }
    $createdVolume = $true
    $dockerArgs = @(
        'run', '--detach', '--name', $containerName,
        '--publish', "127.0.0.1:${port}:5672",
        '--volume', "${volumeName}:/var/lib/rabbitmq",
        '--env', "RABBITMQ_DEFAULT_USER=$credentials",
        '--env', "RABBITMQ_DEFAULT_PASS=$password",
        $image
    )
    $containerId = & $docker.Source @dockerArgs 2>> $dockerLog
    if ($LASTEXITCODE -ne 0) { throw "Failed to start the isolated RabbitMQ container. See $dockerLog" }
    $createdContainer = $true
    Wait-LoopbackPort $port 120 'RabbitMQ AMQP'

    $backendPort = Get-FreeLoopbackPort
    $baseUrl = "http://127.0.0.1:$backendPort"
    $h2Url = "jdbc:h2:mem:s3demo$runId;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
    $springSettings = @{
        server = @{ address = '127.0.0.1'; port = $backendPort }
        spring = @{
            datasource = @{ url = $h2Url; 'driver-class-name' = 'org.h2.Driver'; username = 'sa'; password = '' }
            sql = @{ init = @{ mode = 'always' } }
            rabbitmq = @{
                host = '127.0.0.1'; port = $port; username = $credentials; password = $password
                'virtual-host' = '/'; 'connection-timeout' = '2s'; 'publisher-confirm-type' = 'correlated'
                'publisher-returns' = $true; template = @{ mandatory = $true }
            }
        }
        population = @{
            ai = @{
                precheck = @{
                    enabled = $true; 'rabbit-enabled' = $true; 'storage-dir' = $storageDirectory
                    'analysis-client' = 'MOCK'; 'mock-mode' = 'SUCCESS'; 'mock-failures-before-success' = 1
                    'queue-capacity' = 1000; 'max-processing-attempts' = 3; backoff = @('PT0.01S', 'PT0.02S')
                }
            }
        }
    }
    $env:SPRING_APPLICATION_JSON = $springSettings | ConvertTo-Json -Depth 10 -Compress

    # Keep aliases aligned with application.properties, and independently force H2 through Spring's canonical properties.
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = [string]$backendPort
    $env:AI_PRECHECK_ENABLED = 'true'
    $env:AI_PRECHECK_RABBIT_ENABLED = 'true'
    $env:AI_PRECHECK_STORAGE_DIR = $storageDirectory
    $env:AI_PRECHECK_ANALYSIS_CLIENT = 'MOCK'
    $env:AI_PRECHECK_MOCK_MODE = 'SUCCESS'
    $env:AI_PRECHECK_MOCK_FAILS_BEFORE_SUCCESS = '1'
    $env:AI_PRECHECK_QUEUE_CAPACITY = '1000'
    $env:AI_PRECHECK_MAX_ATTEMPTS = '3'
    $env:AI_PRECHECK_OUTBOX_POLL_MS = '500'
    $env:RABBITMQ_HOST = '127.0.0.1'
    $env:RABBITMQ_PORT = [string]$port
    $env:RABBITMQ_USERNAME = $credentials
    $env:RABBITMQ_PASSWORD = $password
    $env:RABBITMQ_VHOST = '/'
    $env:DB_URL = $h2Url
    $env:DB_USERNAME = 'sa'
    $env:DB_PASSWORD = ''
    $env:SPRING_DATASOURCE_URL = $h2Url
    $env:SPRING_DATASOURCE_DRIVERCLASSNAME = 'org.h2.Driver'
    $env:SPRING_DATASOURCE_USERNAME = 'sa'
    $env:SPRING_DATASOURCE_PASSWORD = ''
    $env:SPRING_SQL_INIT_MODE = 'always'
    $env:SPRING_RABBITMQ_HOST = '127.0.0.1'
    $env:SPRING_RABBITMQ_PORT = [string]$port
    $env:SPRING_RABBITMQ_USERNAME = $credentials
    $env:SPRING_RABBITMQ_PASSWORD = $password
    $env:SPRING_RABBITMQ_VIRTUALHOST = '/'
    $env:SPRING_RABBITMQ_PUBLISHERCONFIRMTYPE = 'correlated'
    $env:SPRING_RABBITMQ_PUBLISHERRETURNS = 'true'
    $env:SPRING_RABBITMQ_TEMPLATE_MANDATORY = 'true'

    $mavenWrapper = Join-Path $repoRoot 'mvnw.cmd'
    $backendProcess = Start-Process -FilePath $mavenWrapper `
        -ArgumentList @('spring-boot:run', '-Dspring-boot.run.useTestClasspath=true') `
        -WorkingDirectory $repoRoot -PassThru -WindowStyle Hidden `
        -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    $healthReady = $false
    $healthUntil = [DateTime]::UtcNow.AddSeconds(180)
    while ([DateTime]::UtcNow -lt $healthUntil) {
        if ($backendProcess.HasExited) {
            throw "Maven backend process exited with code $($backendProcess.ExitCode). See $stdoutLog and $stderrLog"
        }
        try {
            $health = $httpClient
            if ($null -eq $health) {
                Add-Type -AssemblyName System.Net.Http
                $handler = [System.Net.Http.HttpClientHandler]::new()
                $handler.UseProxy = $false
                $httpClient = [System.Net.Http.HttpClient]::new($handler)
            }
            $healthResponse = $httpClient.GetAsync("$baseUrl/api/health").GetAwaiter().GetResult()
            if ($healthResponse.IsSuccessStatusCode) { $healthReady = $true; break }
        }
        catch { }
        Start-Sleep -Seconds 1
    }
    if (-not $healthReady) { throw "Isolated H2 backend did not become ready. See $stdoutLog and $stderrLog" }
    $jdbcLogFound = $false
    foreach ($path in @($stdoutLog, $stderrLog)) {
        if ((Test-Path -LiteralPath $path) -and (Select-String -Path $path -SimpleMatch "jdbc:h2:mem:s3demo$runId" -Quiet)) {
            $jdbcLogFound = $true
        }
    }
    if (-not $jdbcLogFound) { throw 'The demo backend did not report its unique H2 in-memory database URL; refusing to continue.' }

    $token = Get-LoginToken $baseUrl 'household'
    $first = New-SyntheticTask $baseUrl $token "retry-$runId"
    $firstResult = Wait-TaskStatus $baseUrl $token $first.taskId @('SUCCEEDED') $TimeoutSeconds
    Assert-BackendEvidence ([string]$first.taskId) $true
    Write-Host "RabbitMQ task with injected transient failure reached $($firstResult.status); retry was logged."

    & $docker.Source stop --time 10 $containerName *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to stop the RabbitMQ container created by this script.' }
    $duringOutage = New-SyntheticTask $baseUrl $token "outage-$runId"
    if ([string]$duringOutage.status -ne 'PENDING') {
        throw "Broker outage task should be durably accepted as PENDING, got $($duringOutage.status)"
    }
    $stillPending = Wait-TaskStatus $baseUrl $token $duringOutage.taskId @('PENDING') 8
    Write-Host "While the script-owned Broker was stopped, task acceptance returned 202/$($stillPending.status)."

    & $docker.Source start $containerName *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Failed to restart the RabbitMQ container created by this script.' }
    Wait-LoopbackPort $port 120 'RabbitMQ AMQP recovery'
    $recovered = Wait-TaskStatus $baseUrl $token $duringOutage.taskId @('SUCCEEDED') $TimeoutSeconds
    Assert-BackendEvidence ([string]$duringOutage.taskId) $true
    Write-Host "After Broker recovery, the persisted Outbox task reached $($recovered.status)."
    Write-Host "Backend logs: $runDirectory"
    Write-Host 'This is a real RabbitMQ run only when the script has completed successfully.'
}
finally {
    if ($null -ne $backendProcess) {
        Stop-OwnedProcessTree $backendProcess
    }
    if ($null -ne $httpClient) { $httpClient.Dispose() }
    if ($createdContainer) {
        & $docker.Source rm --force $containerName *> $null
    }
    if ($createdVolume) {
        & $docker.Source volume rm $volumeName *> $null
    }
    if ($storageCreated -and (Test-Path -LiteralPath $storageDirectory)) {
        $resolvedStorage = (Resolve-Path -LiteralPath $storageDirectory).Path
        $resolvedRun = (Resolve-Path -LiteralPath $runDirectory).Path
        if ($resolvedStorage.StartsWith($resolvedRun + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedStorage -Recurse -Force
        }
    }
    foreach ($name in $envNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process')
    }
}
