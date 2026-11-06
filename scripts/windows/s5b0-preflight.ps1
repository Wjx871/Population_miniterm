#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$Mode = 'Offline',
    [string]$RepoRoot = (Join-Path $PSScriptRoot '../..'),
    [string[]]$HealthUrl = @(),
    [switch]$AllowNonLocal,
    [object]$TimeoutSeconds = 5
)

# Reads an explicit source-file inventory, never environment values or actual credentials.
function Get-S5B0RequiredFiles {
    @(
        '.env.example', 'pom.xml', 'src/main/resources/application.properties',
        'doc/course/s1/architecture-decisions-and-confirmations.md',
        'doc/course/s5/s5a1-token-revocation-consistency.md', 'doc/course/s5/s5a1-verification.md',
        'doc/course/s5/s5a2-transaction-cache-boundaries.md', 'doc/course/s5/s5a2-verification.md',
        'doc/deployment/backend-v1-deployment.md', 'doc/deployment/backend-v1-operations.md',
        'doc/deployment/deployment-guide.md', 'doc/database/backend-v1-database-guide.md',
        'config/nginx.population.conf.example',
        'scripts/windows/check_backend_health.ps1', 'scripts/windows/verify_full_stack.ps1',
        'scripts/windows/s3-rabbitmq-demo.ps1', 'scripts/windows/s2-demo.ps1', 'scripts/verify-mysql.ps1',
        'src/main/java/com/wjx871/population/s2/trace/S2TraceContext.java',
        'src/main/java/com/wjx871/population/ai/precheck/AiPrecheckProperties.java',
        'src/main/java/com/wjx871/population/assistant/PolicyPythonProperties.java',
        'src/main/java/com/wjx871/population/system/HealthController.java',
        'doc/database/migrations/V4_015_ai_precheck_async.sql',
        'doc/database/migrations/V4_016_s4a_entity_resolution.sql',
        'doc/database/migrations/V4_017_s4a_resolution_retention_indexes.sql',
        'doc/database/migrations/V4_018_s4b_confirm_draft_binding.sql',
        'doc/course/s5/s5b0-environment-contract.md', 'doc/course/s5/s5b0-integration-runbook.md',
        'doc/course/s5/s5b0-team-handoff.md', 'doc/course/s5/s5b0-evidence-template.md',
        'doc/course/s5/s5b0-verification.md', 'doc/course/s5/s5b0-preflight-guide.md',
        'scripts/windows/s5b0-preflight.ps1', 'scripts/windows/tests/s5b0-preflight.tests.ps1'
    )
}

function Get-S5B0Inventory {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $files = @{}
    # Reject UNC/device/relative roots before any filesystem operation.
    if ($RepoRoot -notmatch '^[A-Za-z]:[\\/]') { throw 'S5B0_LOCAL_ROOT_REQUIRED' }
    $root = [IO.Path]::GetFullPath($RepoRoot)
    $drive = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($root))
    if ($drive.DriveType -notin @([IO.DriveType]::Fixed, [IO.DriveType]::Ram)) { throw 'S5B0_LOCAL_DRIVE_REQUIRED' }
    $safeDirectories = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    function Assert-LocalDirectory([string]$Directory) {
        if ($safeDirectories.Contains($Directory)) { return }
        $parent = [IO.Directory]::GetParent($Directory)
        if ($parent) { Assert-LocalDirectory $parent.FullName }
        $attributes = [IO.File]::GetAttributes($Directory)
        if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0 -or
            ($attributes -band [IO.FileAttributes]::Directory) -eq 0) { throw 'S5B0_REPARSE_DIRECTORY_REJECTED' }
        [void]$safeDirectories.Add($Directory)
    }
    Assert-LocalDirectory $root
    foreach ($relative in Get-S5B0RequiredFiles) {
        $path = Join-Path $root $relative
        try {
            Assert-LocalDirectory ([IO.Path]::GetDirectoryName($path))
            $attributes = [IO.File]::GetAttributes($path)
            if (($attributes -band ([IO.FileAttributes]::ReparsePoint -bor [IO.FileAttributes]::Directory)) -eq 0) {
                $files[$relative] = [IO.File]::ReadAllText($path)
            }
        } catch [IO.FileNotFoundException] { continue }
        catch [IO.DirectoryNotFoundException] { continue }
    }
    return ,$files
}

function ConvertTo-S5B0HealthTarget {
    param([AllowNull()][AllowEmptyString()][string]$Value, [switch]$AllowNonLocal)
    $invalid = { param($reason) [pscustomobject]@{ Valid = $false; Reason = $reason; Uri = $null; IsLoopback = $false } }
    if ([string]::IsNullOrEmpty($Value) -or $Value.Length -gt 2048 -or $Value -match '[^\x21-\x7E]') {
        return (& $invalid 'INVALID_URL')
    }
    # Validate raw input BEFORE Uri canonicalization.
    if ($Value.IndexOfAny([char[]]'%@?#\') -ge 0) { return (& $invalid 'UNSAFE_URL_SYNTAX') }
    if ($Value -cnotmatch '^(https?)://([^/]+)/api/health$') { return (& $invalid 'HEALTH_PATH_REQUIRED') }
    $scheme = $Matches[1]
    $authority = $Matches[2]
    $hostText = ''
    $portText = ''
    $address = $null
    $loopback = $false
    if ($authority.StartsWith('[')) {
        if ($authority -cnotmatch '^\[([0-9A-Fa-f:.]+)\](?::([0-9]+))?$') { return (& $invalid 'INVALID_IPV6_AUTHORITY') }
        $hostText = $Matches[1]
        if ($Matches.ContainsKey(2)) { $portText = $Matches[2] }
        if (-not [Net.IPAddress]::TryParse($hostText, [ref]$address) -or $address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetworkV6) {
            return (& $invalid 'INVALID_IPV6')
        }
        if ($hostText.Contains('.') -and ($hostText -split ':')[-1] -cnotmatch '^(0|[1-9][0-9]{0,2})(\.(0|[1-9][0-9]{0,2})){3}$') {
            return (& $invalid 'NON_CANONICAL_IP')
        }
        $effective = if ($address.IsIPv4MappedToIPv6) { $address.MapToIPv4() } else { $address }
        $loopback = [Net.IPAddress]::IsLoopback($effective)
    } else {
        if ($authority -cnotmatch '^([A-Za-z0-9.-]+)(?::([0-9]+))?$') { return (& $invalid 'INVALID_AUTHORITY') }
        $hostText = $Matches[1]
        if ($Matches.ContainsKey(2)) { $portText = $Matches[2] }
        if ($hostText -ieq 'localhost') {
            # No DNS for localhost. HTTPS still requires a valid certificate for the pinned IP.
            $hostText = '127.0.0.1'
            $loopback = $true
        } elseif ([Net.IPAddress]::TryParse($hostText, [ref]$address)) {
            if ($address.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork -or $hostText -cnotmatch '^(0|[1-9][0-9]{0,2})(\.(0|[1-9][0-9]{0,2})){3}$') {
                return (& $invalid 'NON_CANONICAL_IP')
            }
            $loopback = [Net.IPAddress]::IsLoopback($address)
        } else {
            if ($hostText -match '^(?:[0-9.]+|0[xX][0-9A-Fa-f.]+)$' -or $hostText.Length -gt 253) { return (& $invalid 'NON_CANONICAL_IP') }
            foreach ($label in $hostText.Split('.')) {
                if ($label -cnotmatch '^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$') { return (& $invalid 'INVALID_HOST') }
            }
        }
    }
    if ($portText -ne '') {
        $port = 0
        if ($portText -cnotmatch '^[1-9][0-9]{0,4}$' -or -not [int]::TryParse($portText, [ref]$port) -or $port -gt 65535) { return (& $invalid 'INVALID_PORT') }
    }
    if (-not $loopback -and -not $AllowNonLocal) { return (& $invalid 'NON_LOCAL_AUTHORIZATION_REQUIRED') }
    $targetHost = if ($hostText.Contains(':')) { "[$hostText]" } else { $hostText }
    $targetPort = if ($portText) { ":$portText" } else { '' }
    $uri = $null
    if (-not [Uri]::TryCreate("$($scheme)://$($targetHost)$($targetPort)/api/health", [UriKind]::Absolute, [ref]$uri)) { return (& $invalid 'INVALID_URL') }
    return [pscustomobject]@{ Valid = $true; Reason = 'EXPLICIT_HEALTH_TARGET'; Uri = $uri; IsLoopback = $loopback }
}

function Invoke-S5B0HealthTransport {
    param([Parameter(Mandatory)][Uri]$Uri, [int]$TimeoutSeconds)
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.AllowAutoRedirect = $false
    $handler.UseProxy = $false
    $handler.UseDefaultCredentials = $false
    $handler.UseCookies = $false
    $handler.MaxResponseHeadersLength = 16
    # Keep platform TLS validation. Never install a certificate bypass callback.
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    $deadline = [Threading.CancellationTokenSource]::new([TimeSpan]::FromSeconds($TimeoutSeconds))
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, $Uri)
    $response = $null
    $stream = $null
    $memory = $null
    try {
        $response = $client.SendAsync($request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead, $deadline.Token).GetAwaiter().GetResult()
        $status = [int]$response.StatusCode
        if ($status -ne 200) { return @{ StatusCode = $status; Body = '' } }
        if ($response.Content.Headers.ContentLength -gt 16384) { throw 'S5B0_RESPONSE_TOO_LARGE' }
        $stream = $response.Content.ReadAsStreamAsync($deadline.Token).GetAwaiter().GetResult()
        $memory = [IO.MemoryStream]::new()
        $buffer = [byte[]]::new(2048)
        while (($count = $stream.ReadAsync($buffer, 0, $buffer.Length, $deadline.Token).GetAwaiter().GetResult()) -gt 0) {
            if ($memory.Length + $count -gt 16384) { throw 'S5B0_RESPONSE_TOO_LARGE' }
            $memory.Write($buffer, 0, $count)
        }
        return @{ StatusCode = $status; Body = [Text.UTF8Encoding]::new($false, $true).GetString($memory.ToArray()) }
    } finally {
        if ($memory) { $memory.Dispose() }
        if ($stream) { $stream.Dispose() }
        if ($response) { $response.Dispose() }
        $request.Dispose()
        $deadline.Dispose()
        $client.Dispose()
    }
}

function Invoke-S5B0Preflight {
    [CmdletBinding()]
    param(
        [string]$Mode = 'Offline', [string]$RepoRoot = (Join-Path $PSScriptRoot '../..'),
        [string[]]$HealthUrl = @(), [switch]$AllowNonLocal, [object]$TimeoutSeconds = 5,
        [scriptblock]$RepositoryReader, [scriptblock]$Transport
    )
    $checks = [Collections.Generic.List[object]]::new()
    $add = { param($id, $category, $status, $reason) $checks.Add([pscustomobject]@{ id = $id; category = $category; status = $status; reason = $reason }) }
    $valid = $true
    if ($Mode -cnotin @('Offline', 'ReadOnlyProbe')) {
        & $add 'PARAM-MODE' 'STATIC' 'FAIL' 'Mode must be Offline or ReadOnlyProbe.'
        $valid = $false
    }
    $parsedTimeout = 0
    if (-not [int]::TryParse([string]$TimeoutSeconds, [ref]$parsedTimeout) -or $parsedTimeout -lt 1 -or $parsedTimeout -gt 30) {
        & $add 'PARAM-TIMEOUT' 'STATIC' 'FAIL' 'TimeoutSeconds must be between 1 and 30.'
        $valid = $false
    } else {
        $TimeoutSeconds = $parsedTimeout
    }
    if ($Mode -eq 'Offline' -and (@($HealthUrl).Count -gt 0 -or $AllowNonLocal)) {
        & $add 'PARAM-OFFLINE' 'STATIC' 'FAIL' 'Offline rejects probe targets and non-local authorization; no request sent.'
        $valid = $false
    }
    if (@($HealthUrl).Count -gt 8) {
        & $add 'PARAM-TARGETS' 'STATIC' 'FAIL' 'At most eight explicit targets are allowed; no discovery or scanning.'
        $valid = $false
    }
    $targets = [Collections.Generic.List[object]]::new()
    if ($Mode -eq 'ReadOnlyProbe') {
        if (@($HealthUrl).Count -eq 0) {
            & $add 'PARAM-TARGETS' 'STATIC' 'BLOCKED' 'Explicit HealthUrl is required; no targets inferred.'
            $valid = $false
        }
        foreach ($value in $HealthUrl) {
            $target = ConvertTo-S5B0HealthTarget -Value $value -AllowNonLocal:$AllowNonLocal
            if (-not $target.Valid) {
                & $add ('HTTP-{0:d2}' -f ($targets.Count + 1)) 'STATIC' 'FAIL' $target.Reason
                $valid = $false
            }
            $targets.Add($target)
        }
    }
    if ($valid) {
        try {
            $files = if ($RepositoryReader) { & $RepositoryReader $RepoRoot } else { Get-S5B0Inventory -RepoRoot $RepoRoot }
            if ($files -isnot [Collections.IDictionary]) { throw 'S5B0_INVALID_INVENTORY' }
            $missing = @(Get-S5B0RequiredFiles | Where-Object { -not $files.Contains($_) })
            if ($missing.Count) { & $add 'REPO-FILES' 'STATIC' 'BLOCKED' ('Required files missing: ' + ($missing -join ', ')) }
            else { & $add 'REPO-FILES' 'STATIC' 'PASS' 'All required repository files exist; this does not prove deployment.' }
            $properties = [string]$files['src/main/resources/application.properties']
            $expected = @(
                'SERVER_PORT','DB_URL','DB_USERNAME','DB_PASSWORD','JWT_SECRET','JWT_REVOCATION_MODE',
                'JWT_REVOCATION_PRODUCTION','REDIS_ENABLED','REDIS_HOST','REDIS_PORT','REDIS_DATABASE',
                'REDIS_KEY_PREFIX','REDIS_TIMEOUT','APP_UPLOAD_DIR','EXPORT_DIR','AI_PRECHECK_STORAGE_DIR',
                'AI_PRECHECK_ENABLED','AI_PRECHECK_RABBIT_ENABLED','AI_PRECHECK_ANALYSIS_CLIENT',
                'AI_PRECHECK_EXCHANGE','AI_PRECHECK_QUEUE','AI_PRECHECK_ROUTING_KEY','AI_PRECHECK_DLX',
                'AI_PRECHECK_DLQ','AI_PRECHECK_DLQ_ROUTING_KEY','RABBITMQ_HOST','RABBITMQ_PORT',
                'RABBITMQ_USERNAME','RABBITMQ_PASSWORD','RABBITMQ_VHOST',
                'POLICY_PYTHON_ENABLED','POLICY_PYTHON_CLIENT','POLICY_PYTHON_BASE_URL','POLICY_PYTHON_AUTH_TOKEN'
            )
            $absent = @($expected | Where-Object { -not $properties.Contains('$' + '{' + $_ + ':') })
            if ($absent.Count) { & $add 'CONFIG-NAMES' 'STATIC' 'BLOCKED' ('Source configuration names missing or changed: ' + ($absent -join ', ')) }
            else { & $add 'CONFIG-NAMES' 'STATIC' 'PASS' 'Required environment placeholder names match source; no values expanded.' }
            $contract = [string]$files['doc/course/s5/s5b0-environment-contract.md']
            $ownersComplete = $true
            foreach ($number in 1..14) {
                $serviceId = 'SVC-{0:d2}' -f $number
                if ($contract -notmatch ('(?m)^\|\s*' + $serviceId + '\s*\|\s*[^|\r\n]*(张锦源|赵书亚|王家兴|张蕊)[^|\r\n]*\|')) { $ownersComplete = $false }
            }
            & $add 'SERVICE-OWNERS' 'STATIC' $(if ($ownersComplete) { 'PASS' } else { 'BLOCKED' }) $(if ($ownersComplete) { 'All fourteen candidate services have a named owner; ownership is not team confirmation.' } else { 'Fourteen-service ownership matrix is incomplete.' })
            $knownGaps = $true
            foreach ($number in 1..8) { if (-not $contract.Contains(('GAP-{0:d2}' -f $number))) { $knownGaps = $false } }
            & $add 'KNOWN-GAPS' 'STATIC' $(if ($knownGaps) { 'PASS' } else { 'BLOCKED' }) $(if ($knownGaps) { 'Eight known implementation/deployment gaps are recorded.' } else { 'Known gap register is incomplete.' })
            $handoff = [string]$files['doc/course/s5/s5b0-team-handoff.md']
            $gatesPresent = $true
            foreach ($number in 1..10) { if (-not $handoff.Contains(('B1-{0:d2}' -f $number))) { $gatesPresent = $false } }
            & $add 'B1-GATES' 'STATIC' $(if ($gatesPresent) { 'PASS' } else { 'BLOCKED' }) $(if ($gatesPresent) { 'Ten S5-B1 prerequisites are recorded; no deployment is triggered.' } else { 'S5-B1 prerequisite register is incomplete.' })
            & $add 'ENV-CONFIRMATION' 'READINESS' 'BLOCKED' 'PENDING_CONFIRMATION: endpoints, versions, credential injection, storage and owner approvals need separate evidence.'
        } catch {
            & $add 'REPO-READ' 'STATIC' 'BLOCKED' 'Repository inventory could not be read safely; no exception details emitted.'
        }
        if ($Mode -eq 'ReadOnlyProbe') {
            $index = 0
            foreach ($target in $targets) {
                $index++
                $id = 'HTTP-{0:d2}' -f $index
                $category = if ($Transport) { 'SIMULATED_HTTP' } else { 'HTTP_HEALTH' }
                try {
                    $reply = if ($Transport) { & $Transport $target.Uri $TimeoutSeconds } else { Invoke-S5B0HealthTransport -Uri $target.Uri -TimeoutSeconds $TimeoutSeconds }
                    $statusCode = [int]$reply.StatusCode
                    if ($statusCode -ne 200) {
                        $reason = if ($statusCode -ge 300 -and $statusCode -lt 400) { 'REDIRECT_REJECTED' } else { 'HTTP_STATUS_NOT_200' }
                        & $add $id $category 'FAIL' $reason
                        continue
                    }
                    if ([Text.Encoding]::UTF8.GetByteCount([string]$reply.Body) -gt 16384) { throw 'S5B0_RESPONSE_TOO_LARGE' }
                    try { $payload = $reply.Body | ConvertFrom-Json -AsHashtable -ErrorAction Stop }
                    catch { & $add $id $category 'FAIL' 'INVALID_HEALTH_JSON'; continue }
                    if ($payload -isnot [Collections.IDictionary] -or
                        ($payload['code'] -isnot [int] -and $payload['code'] -isnot [long]) -or $payload['code'] -ne 200 -or
                        $payload['data'] -isnot [Collections.IDictionary] -or
                        $payload['data']['database'] -isnot [string] -or $payload['data']['database'] -cne 'UP') {
                        & $add $id $category 'FAIL' 'HEALTH_CONTRACT_NOT_SATISFIED'
                    } else {
                        & $add $id $category 'PASS' 'HTTP_HEALTH_CONTRACT_ONLY; no distributed consistency or readiness conclusion.'
                    }
                } catch {
                    $exception = $_.Exception
                    $reason = 'HTTP_REQUEST_FAILED'
                    for ($depth = 0; $depth -lt 10 -and $null -ne $exception; $depth++) {
                        if ($exception -is [OperationCanceledException] -or $exception -is [TimeoutException] -or ($exception -is [Net.WebException] -and $exception.Status -eq [Net.WebExceptionStatus]::Timeout)) { $reason = 'HTTP_TIMEOUT' }
                        if ($exception.Message -ceq 'S5B0_RESPONSE_TOO_LARGE') { $reason = 'HEALTH_RESPONSE_TOO_LARGE' }
                        $exception = $exception.InnerException
                    }
                    & $add $id $category 'FAIL' $reason
                }
            }
        }
    }
    & $add 'REAL-INTEGRATION' 'REAL_ENVIRONMENT' 'NOT_VERIFIED' 'Redis/Nginx A-B, physical MySQL routing, RabbitMQ, Python, shared storage and frontend require separately authorized real acceptance.'
    $overall = 'PASS'
    $exitCode = 0
    foreach ($pair in @(@('FAIL',1), @('BLOCKED',2), @('NOT_VERIFIED',3))) {
        if (@($checks | Where-Object status -eq $pair[0]).Count -gt 0) { $overall = $pair[0]; $exitCode = $pair[1]; break }
    }
    $evidence = if ($Mode -eq 'Offline') { 'STATIC_ONLY' } elseif ($Transport) { 'MOCK_TRANSPORT' } else { 'HTTP_HEALTH_ONLY' }
    $safeMode = if ($Mode -cin @('Offline','ReadOnlyProbe')) { $Mode } else { 'REJECTED' }
    return [pscustomobject]@{ schemaVersion = 1; mode = $safeMode; overallStatus = $overall; exitCode = $exitCode; evidenceClass = $evidence; checks = $checks.ToArray() }
}

if ($MyInvocation.InvocationName -ne '.') {
    try {
        $result = Invoke-S5B0Preflight -Mode $Mode -RepoRoot $RepoRoot -HealthUrl $HealthUrl -AllowNonLocal:$AllowNonLocal -TimeoutSeconds $TimeoutSeconds
        $result | ConvertTo-Json -Depth 8
        exit $result.exitCode
    } catch {
        [pscustomobject]@{ schemaVersion = 1; mode = 'REJECTED'; overallStatus = 'FAIL'; exitCode = 1; evidenceClass = 'STATIC_ONLY'; checks = @(@{ id = 'ENTRYPOINT'; category = 'STATIC'; status = 'FAIL'; reason = 'Preflight rejected; sensitive error details suppressed.' }) } | ConvertTo-Json -Depth 8
        exit 1
    }
}
