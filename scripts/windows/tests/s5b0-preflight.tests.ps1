[CmdletBinding()]
param(
    [string]$RepoRoot = (Join-Path $PSScriptRoot '..\..\..')
)

# Standalone, module-free tests. All HTTP observations below are simulated.
# This script creates no fixtures, starts no services, and performs no cleanup.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:S5B0Passed = 0
$script:S5B0Failed = 0

function Assert-S5B0Test {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Invoke-S5B0TestCase {
    param([string]$Name, [scriptblock]$Body)
    try {
        & $Body
        $script:S5B0Passed++
        Write-Host "[PASS] $Name"
    } catch {
        $script:S5B0Failed++
        # Assertion failures are intentionally fixed messages, not HTTP bodies.
        Write-Host "[FAIL] $Name : $($_.Exception.Message)"
    }
}

function Copy-S5B0TestInventory {
    param([hashtable]$Inventory)
    $copy = @{}
    foreach ($key in $Inventory.Keys) { $copy[$key] = [string]$Inventory[$key] }
    return $copy
}

function Assert-S5B0NoMarker {
    param($Value, [string]$Marker)
    $serialized = ConvertTo-Json -InputObject $Value -Depth 20 -Compress
    Assert-S5B0Test (-not $serialized.Contains($Marker)) 'A rejected URL, response body, or exception leaked into the result.'
}

function Assert-S5B0SimulatedResult {
    param($Result, [string]$ExpectedHttpStatus, [int]$ExpectedCount = 1)
    Assert-S5B0Test ($Result.mode -eq 'ReadOnlyProbe') 'The probe mode was lost.'
    Assert-S5B0Test ($Result.evidenceClass -eq 'MOCK_TRANSPORT') 'An injected transport was presented as real HTTP evidence.'
    $http = @($Result.checks | Where-Object { $_.category -eq 'SIMULATED_HTTP' })
    Assert-S5B0Test ($http.Count -eq $ExpectedCount) 'The simulated HTTP check count is incorrect.'
    Assert-S5B0Test (@($Result.checks | Where-Object { $_.category -eq 'HTTP_HEALTH' }).Count -eq 0) 'Mock evidence used a real HTTP category.'
    Assert-S5B0Test ($Result.overallStatus -ne 'PASS') 'A mock probe claimed overall integration success.'
    Assert-S5B0Test (@($Result.checks | Where-Object { $_.status -eq 'NOT_VERIFIED' }).Count -gt 0) 'Real integration was not explicitly left unverified.'
    foreach ($check in $http) {
        Assert-S5B0Test ($check.status -eq $ExpectedHttpStatus) 'A simulated HTTP check has an unexpected status.'
    }
}

$preflightPath = Join-Path $PSScriptRoot '..\s5b0-preflight.ps1'
if (-not (Test-Path -LiteralPath $preflightPath -PathType Leaf)) {
    throw 'The S5-B0 preflight library must exist before these tests run.'
}

# Dot-sourcing must only register functions; it must not run the CLI.
$dotSourceOutput = @(. $preflightPath)
if ($dotSourceOutput.Count -ne 0) { throw 'Dot-sourcing the preflight script executed work or emitted results.' }
$inventory = Get-S5B0Inventory -RepoRoot $RepoRoot
if ($inventory -isnot [hashtable] -or $inventory.Count -eq 0) {
    throw 'The repository reader did not return the expected nonempty inventory hashtable.'
}
$memoryInventory = Copy-S5B0TestInventory $inventory
$reader = {
    param($RequestedRoot)
    $copy = @{}
    foreach ($key in $memoryInventory.Keys) { $copy[$key] = [string]$memoryInventory[$key] }
    return $copy
}.GetNewClosure()
$healthyBody = '{"code":200,"data":{"database":"UP","redisEnabled":false,"redisStatus":"DISABLED","cacheMode":"MYSQL_FALLBACK"}}'
$goodUrl = 'http://127.0.0.1:8080/api/health'

Invoke-S5B0TestCase 'Offline is static, preserves its in-memory fixture, and never calls transport' {
    $fixture = Copy-S5B0TestInventory $memoryInventory
    $before = ConvertTo-Json -InputObject $fixture -Depth 5 -Compress
    $state = @{ Reads = 0; Calls = 0 }
    $neverCreatedRoot = Join-Path $RepoRoot ('__s5b0_memory_only_' + [guid]::NewGuid().ToString('N'))
    Assert-S5B0Test (-not (Test-Path -LiteralPath $neverCreatedRoot)) 'The isolated in-memory root unexpectedly exists.'
    $fixtureReader = {
        param($RequestedRoot)
        $state.Reads++
        return $fixture
    }.GetNewClosure()
    $forbiddenTransport = {
        param([uri]$Uri, [int]$Timeout)
        $state.Calls++
        throw 'OFFLINE_TRANSPORT_MUST_NOT_RUN'
    }.GetNewClosure()
    $result = Invoke-S5B0Preflight -Mode Offline -RepoRoot $neverCreatedRoot -RepositoryReader $fixtureReader -Transport $forbiddenTransport
    Assert-S5B0Test ($state.Reads -eq 1 -and $state.Calls -eq 0) 'Offline did not use only the supplied repository reader.'
    Assert-S5B0Test ($result.mode -eq 'Offline' -and $result.evidenceClass -eq 'STATIC_ONLY') 'Offline reported active or mock probe evidence.'
    Assert-S5B0Test (@($result.checks | Where-Object { $_.category -in @('HTTP_HEALTH', 'SIMULATED_HTTP') }).Count -eq 0) 'Offline emitted an HTTP check.'
    Assert-S5B0Test ($result.overallStatus -ne 'PASS' -and $result.exitCode -ne 0) 'Offline claimed live integration success.'
    Assert-S5B0Test ((ConvertTo-Json -InputObject $fixture -Depth 5 -Compress) -eq $before) 'Offline mutated the supplied inventory.'
    Assert-S5B0Test (-not (Test-Path -LiteralPath $neverCreatedRoot)) 'Offline created its repository root or file fixtures.'
    $defaultResult = Invoke-S5B0Preflight -RepoRoot $neverCreatedRoot -RepositoryReader $fixtureReader -Transport $forbiddenTransport
    Assert-S5B0Test ($defaultResult.mode -eq 'Offline' -and $defaultResult.evidenceClass -eq 'STATIC_ONLY' -and $state.Calls -eq 0) 'Omitting Mode did not retain the safe Offline default.'
}

Invoke-S5B0TestCase 'Missing repository files produce BLOCKED without fabricated inventory' {
    $emptyReader = { param($RequestedRoot) return @{} }
    $result = Invoke-S5B0Preflight -Mode Offline -RepoRoot $RepoRoot -RepositoryReader $emptyReader
    Assert-S5B0Test ($result.overallStatus -eq 'BLOCKED' -and $result.exitCode -eq 2) 'Missing files were not reported as BLOCKED/2.'
    Assert-S5B0Test (@($result.checks | Where-Object { $_.status -eq 'BLOCKED' }).Count -gt 0) 'Missing files had no blocked check.'
    Assert-S5B0Test ($result.evidenceClass -eq 'STATIC_ONLY') 'Missing files were represented as runtime evidence.'
}

Invoke-S5B0TestCase 'UNC repository roots are rejected before any filesystem reader is reached' {
    foreach ($uncRoot in @('\\s5b0-preflight.invalid\read-forbidden', '\\?\UNC\s5b0-preflight.invalid\read-forbidden')) {
        # The library's first raw-root guard was statically checked before this
        # suite ran: it precedes GetFullPath, DriveInfo, and every file API.
        # Assert that exact pre-rejection, not a later inaccessible-share error.
        $preRejected = $false
        try { $null = Get-S5B0Inventory -RepoRoot $uncRoot }
        catch { $preRejected = $_.Exception.Message -ceq 'S5B0_LOCAL_ROOT_REQUIRED' }
        Assert-S5B0Test $preRejected 'A UNC root did not take the raw parameter-rejection branch.'
        $result = Invoke-S5B0Preflight -Mode Offline -RepoRoot $uncRoot
        Assert-S5B0Test ($result.overallStatus -eq 'BLOCKED' -and $result.exitCode -eq 2) 'A UNC repository root was not blocked.'
        Assert-S5B0Test (@($result.checks | Where-Object { $_.id -eq 'REPO-READ' -and $_.status -eq 'BLOCKED' }).Count -eq 1) 'UNC rejection did not produce the safe repository-read result.'
    }
}

Invoke-S5B0TestCase 'Explicit loopback IPv4, IPv6, and mapped IPv4 health URLs are accepted' {
    foreach ($value in @(
        'http://127.0.0.1:8080/api/health',
        'https://localhost:8443/api/health',
        'http://[::1]:8080/api/health',
        'http://[::ffff:127.0.0.1]:8080/api/health'
    )) {
        $target = ConvertTo-S5B0HealthTarget -Value $value
        Assert-S5B0Test ($target.Valid -and $target.IsLoopback -and $target.Uri -is [uri]) 'A canonical loopback target was rejected or misclassified.'
        Assert-S5B0Test ($target.Uri.AbsolutePath -eq '/api/health') 'A valid target changed the health path.'
    }
}

Invoke-S5B0TestCase 'Nonlocal and mapped remote targets require the explicit switch' {
    foreach ($value in @('https://example.invalid:8443/api/health', 'http://192.0.2.20:8080/api/health', 'http://[::ffff:192.0.2.20]:8080/api/health')) {
        $denied = ConvertTo-S5B0HealthTarget -Value $value
        Assert-S5B0Test (-not $denied.Valid) 'A nonlocal target was allowed without the switch.'
        $allowed = ConvertTo-S5B0HealthTarget -Value $value -AllowNonLocal
        Assert-S5B0Test ($allowed.Valid -and -not $allowed.IsLoopback) 'The switch did not preserve nonlocal classification.'
    }
}

Invoke-S5B0TestCase 'Sensitive URLs and all non-health routes are rejected before transport' {
    $marker = 'S5B0_URL_SECRET_CANARY'
    $values = @(
        "http://user:${marker}@127.0.0.1:8080/api/health",
        "http://127.0.0.1:8080/api/health?token=$marker",
        "http://127.0.0.1:8080/api/health#$marker",
        'http://127.0.0.1:8080/api/auth/login',
        'http://127.0.0.1:8080/api/materials/1/download',
        'http://127.0.0.1:8080/api/ai/materials/stg_123',
        'http://127.0.0.1:8080/api/exports/1/download',
        'http://127.0.0.1:8080/api/system/health',
        'http://127.0.0.1:8080/api/health/',
        'file:///C:/private/api/health',
        'ftp://127.0.0.1:8080/api/health',
        '/api/health'
    )
    foreach ($value in $values) {
        $state = @{ Calls = 0 }
        $transport = { param([uri]$Uri, [int]$Timeout) $state.Calls++; throw 'VALIDATION_MUST_RUN_FIRST' }.GetNewClosure()
        $target = ConvertTo-S5B0HealthTarget -Value $value -AllowNonLocal
        Assert-S5B0Test (-not $target.Valid) 'A sensitive or non-health URL was accepted.'
        Assert-S5B0NoMarker $target.Reason $marker
        $result = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($value) -AllowNonLocal -RepositoryReader $reader -Transport $transport
        Assert-S5B0Test ($state.Calls -eq 0 -and $result.overallStatus -eq 'FAIL' -and $result.exitCode -eq 1) 'A rejected URL reached transport or did not fail.'
        Assert-S5B0NoMarker $result $marker
    }
}

Invoke-S5B0TestCase 'Encoded paths, backslashes, dot segments, and scoped IPv6 are rejected' {
    foreach ($value in @(
        'http://127.0.0.1:8080/%61pi/health',
        'http://127.0.0.1:8080/api/%68ealth',
        'http://127.0.0.1:8080/api%2fhealth',
        'http://127.0.0.1:8080/api/health%3ftoken=value',
        'http://127.0.0.1:8080/api/other/../health',
        'http://127.0.0.1:8080\api\health',
        'http://127.0.0.1:8080/api//health',
        'http://[::1%2512]:8080/api/health',
        'http://[fe80::1%12]:8080/api/health'
    )) {
        $target = ConvertTo-S5B0HealthTarget -Value $value -AllowNonLocal
        Assert-S5B0Test (-not $target.Valid) 'An encoded, normalized, or scoped address bypassed validation.'
    }
}

Invoke-S5B0TestCase 'Decimal, octal, hexadecimal, and shortened IPv4 spellings stay rejected' {
    foreach ($hostValue in @('2130706433', '0177.0.0.1', '0x7f000001', '0x7f.0.0.1', '127.1', '127.000.000.001')) {
        $value = 'http://' + $hostValue + ':8080/api/health'
        $target = ConvertTo-S5B0HealthTarget -Value $value -AllowNonLocal
        Assert-S5B0Test (-not $target.Valid) 'A noncanonical IPv4 spelling was accepted.'
    }
}

Invoke-S5B0TestCase 'Missing health targets block and invalid mode or timeout fails before transport' {
    $state = @{ Calls = 0 }
    $transport = { param([uri]$Uri, [int]$Timeout) $state.Calls++; throw 'INVALID_ARGUMENTS_MUST_NOT_PROBE' }.GetNewClosure()
    $missing = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -RepositoryReader $reader -Transport $transport
    Assert-S5B0Test ($missing.overallStatus -eq 'BLOCKED' -and $missing.exitCode -eq 2) 'An implicit health target was selected.'
    $marker = 'S5B0_PARAMETER_SECRET_CANARY'
    foreach ($timeout in @(0, -1, 31, [int]::MaxValue, '1.5', 'NaN', $marker)) {
        $result = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($goodUrl) -TimeoutSeconds $timeout -RepositoryReader $reader -Transport $transport
        Assert-S5B0Test ($result.overallStatus -eq 'FAIL' -and $result.exitCode -eq 1) 'An unsafe timeout was accepted.'
        Assert-S5B0NoMarker $result $marker
    }
    $invalidMode = Invoke-S5B0Preflight -Mode $marker -RepoRoot $RepoRoot -RepositoryReader $reader -Transport $transport
    Assert-S5B0Test ($invalidMode.overallStatus -eq 'FAIL' -and $invalidMode.exitCode -eq 1) 'An invalid mode did not return a structured failure.'
    Assert-S5B0NoMarker $invalidMode $marker
    $tooMany = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @((1..9) | ForEach-Object { $goodUrl }) -RepositoryReader $reader -Transport $transport
    Assert-S5B0Test ($tooMany.overallStatus -eq 'FAIL' -and $tooMany.exitCode -eq 1) 'The explicit target-count ceiling was not enforced.'
    Assert-S5B0Test ($state.Calls -eq 0) 'Invalid arguments reached transport.'
}

Invoke-S5B0TestCase 'Offline rejects probe options without invoking transport' {
    $state = @{ Calls = 0 }
    $transport = { param([uri]$Uri, [int]$Timeout) $state.Calls++; throw 'OFFLINE_OPTIONS_MUST_NOT_PROBE' }.GetNewClosure()
    $withUrl = Invoke-S5B0Preflight -Mode Offline -RepoRoot $RepoRoot -HealthUrl @($goodUrl) -RepositoryReader $reader -Transport $transport
    $withRemoteSwitch = Invoke-S5B0Preflight -Mode Offline -RepoRoot $RepoRoot -AllowNonLocal -RepositoryReader $reader -Transport $transport
    foreach ($result in @($withUrl, $withRemoteSwitch)) {
        Assert-S5B0Test ($result.overallStatus -eq 'FAIL' -and $result.exitCode -eq 1) 'Offline accepted a probe-only option.'
    }
    Assert-S5B0Test ($state.Calls -eq 0) 'An Offline option triggered transport.'
}

Invoke-S5B0TestCase 'Healthy fake responses are labeled simulated and keep integration unverified' {
    $state = @{ Calls = 0; Uris = @(); Timeouts = @() }
    $transport = {
        param([uri]$Uri, [int]$Timeout)
        $state.Calls++
        $state.Uris += $Uri.AbsoluteUri
        $state.Timeouts += $Timeout
        return @{ StatusCode = 200; Body = $healthyBody }
    }.GetNewClosure()
    $result = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($goodUrl, 'http://[::1]:8081/api/health') -TimeoutSeconds 5 -RepositoryReader $reader -Transport $transport
    Assert-S5B0Test ($state.Calls -eq 2) 'The explicit health targets were not each simulated once.'
    Assert-S5B0Test (@($state.Timeouts | Where-Object { $_ -ne 5 }).Count -eq 0) 'The validated timeout was not passed to transport.'
    Assert-S5B0Test ($state.Uris[0] -eq $goodUrl -and $state.Uris[1] -eq 'http://[::1]:8081/api/health') 'A target was guessed, changed, or followed.'
    Assert-S5B0SimulatedResult $result 'PASS' 2
    $checks = @($result.checks | Where-Object { $_.category -eq 'SIMULATED_HTTP' })
    Assert-S5B0Test ($checks[0].id -eq 'HTTP-01' -and $checks[1].id -eq 'HTTP-02') 'HTTP evidence IDs are not stable and ordered.'
}

Invoke-S5B0TestCase 'Nonlocal probes call only the supplied mock after explicit authorization' {
    $remoteUrl = 'https://example.invalid:8443/api/health'
    $state = @{ Calls = 0 }
    $transport = { param([uri]$Uri, [int]$Timeout) $state.Calls++; return @{ StatusCode = 200; Body = $healthyBody } }.GetNewClosure()
    $denied = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($remoteUrl) -RepositoryReader $reader -Transport $transport
    Assert-S5B0Test ($denied.overallStatus -eq 'FAIL' -and $state.Calls -eq 0) 'A remote target was probed without explicit authorization.'
    $allowed = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($remoteUrl) -AllowNonLocal -RepositoryReader $reader -Transport $transport
    Assert-S5B0Test ($state.Calls -eq 1) 'An explicitly authorized target did not use the supplied mock exactly once.'
    Assert-S5B0SimulatedResult $allowed 'PASS'
}

Invoke-S5B0TestCase 'Redirects and non-200 statuses fail without a second simulated request' {
    foreach ($status in @(301, 302, 307, 308, 401, 403, 404, 500, 503)) {
        $state = @{ Calls = 0 }
        $marker = 'S5B0_RESPONSE_SECRET_CANARY'
        $transport = {
            param([uri]$Uri, [int]$Timeout)
            $state.Calls++
            return @{ StatusCode = $status; Body = $marker }
        }.GetNewClosure()
        $result = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($goodUrl) -RepositoryReader $reader -Transport $transport
        Assert-S5B0Test ($state.Calls -eq 1 -and $result.overallStatus -eq 'FAIL' -and $result.exitCode -eq 1) 'An HTTP failure was followed, retried, or accepted.'
        Assert-S5B0SimulatedResult $result 'FAIL'
        Assert-S5B0NoMarker $result $marker
    }
}

Invoke-S5B0TestCase 'Invalid JSON, missing health fields, database DOWN, and wrong business code fail' {
    foreach ($body in @(
        'not-json S5B0_BODY_SECRET_CANARY',
        '{"code":200,"data":{}}',
        '{"code":200,"data":{"database":"DOWN"}}',
        '{"code":500,"data":{"database":"UP"}}',
        '{"code":true,"data":{"database":"UP"}}',
        '{"code":"200","data":{"database":"UP"}}',
        '{"code":[200],"data":{"database":"UP"}}',
        '{"code":200,"data":{"database":["UP"]}}'
    )) {
        $transport = { param([uri]$Uri, [int]$Timeout) return @{ StatusCode = 200; Body = $body } }.GetNewClosure()
        $result = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($goodUrl) -RepositoryReader $reader -Transport $transport
        Assert-S5B0Test ($result.overallStatus -eq 'FAIL' -and $result.exitCode -eq 1) 'An invalid health contract was accepted.'
        Assert-S5B0SimulatedResult $result 'FAIL'
        Assert-S5B0NoMarker $result 'S5B0_BODY_SECRET_CANARY'
    }
}

Invoke-S5B0TestCase 'Unknown successful-response fields are never copied into evidence' {
    $marker = 'S5B0_EXTRA_FIELD_SECRET_CANARY'
    $body = '{"code":200,"data":{"database":"UP","redisEnabled":false,"redisStatus":"DISABLED","cacheMode":"MYSQL_FALLBACK","privateToken":"' + $marker + '"},"debug":"' + $marker + '"}'
    $transport = { param([uri]$Uri, [int]$Timeout) return @{ StatusCode = 200; Body = $body } }.GetNewClosure()
    $result = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($goodUrl) -RepositoryReader $reader -Transport $transport
    Assert-S5B0SimulatedResult $result 'PASS'
    Assert-S5B0NoMarker $result $marker
}

Invoke-S5B0TestCase 'Oversized simulated health bodies fail at the shared sixteen-KiB boundary' {
    $body = 'x' * 16385
    $transport = { param([uri]$Uri, [int]$Timeout) return @{ StatusCode = 200; Body = $body } }.GetNewClosure()
    $result = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($goodUrl) -RepositoryReader $reader -Transport $transport
    Assert-S5B0Test ($result.overallStatus -eq 'FAIL' -and $result.exitCode -eq 1) 'An oversized simulated body was accepted.'
    Assert-S5B0SimulatedResult $result 'FAIL'
    Assert-S5B0Test (@($result.checks | Where-Object { $_.id -eq 'HTTP-01' -and $_.reason -eq 'HEALTH_RESPONSE_TOO_LARGE' }).Count -eq 1) 'The shared response limit did not produce its fixed error code.'
}

Invoke-S5B0TestCase 'Timeouts and transport errors return sanitized structured failures' {
    $marker = 'S5B0_EXCEPTION_SECRET_CANARY'
    foreach ($kind in @('Timeout', 'Transport')) {
        $state = @{ Calls = 0 }
        $transport = {
            param([uri]$Uri, [int]$Timeout)
            $state.Calls++
            if ($kind -eq 'Timeout') { throw [TimeoutException]::new($marker) }
            throw [InvalidOperationException]::new($marker)
        }.GetNewClosure()
        $result = Invoke-S5B0Preflight -Mode ReadOnlyProbe -RepoRoot $RepoRoot -HealthUrl @($goodUrl) -RepositoryReader $reader -Transport $transport
        Assert-S5B0Test ($state.Calls -eq 1 -and $result.overallStatus -eq 'FAIL' -and $result.exitCode -eq 1) 'A transport failure did not return FAIL/1.'
        Assert-S5B0SimulatedResult $result 'FAIL'
        Assert-S5B0NoMarker $result $marker
    }
}

Write-Host ("S5-B0 preflight tests: {0} passed, {1} failed. Evidence: MOCK_TESTS_ONLY; no real HTTP or integration verification." -f $script:S5B0Passed, $script:S5B0Failed)
if ($script:S5B0Failed -gt 0) { exit 1 }
exit 0
