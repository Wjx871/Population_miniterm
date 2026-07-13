[CmdletBinding()]
param(
    [string]$FrontendBaseUrl = 'http://localhost:5180',
    [string]$BackendBaseUrl = 'http://127.0.0.1:8080',
    [string]$TestPassword,
    [switch]$IncludeAllRoles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-Http {
    param([string]$Method, [string]$Url, [hashtable]$Headers, [object]$Body)
    try {
        $args = @{ Uri = $Url; Method = $Method; UseBasicParsing = $true; ErrorAction = 'Stop' }
        if ($Headers) { $args.Headers = $Headers }
        if ($null -ne $Body) { $args.Body = ($Body | ConvertTo-Json -Compress); $args.ContentType = 'application/json' }
        $response = Invoke-WebRequest @args
        $raw = $response.Content
        return [pscustomobject]@{ Status = [int]$response.StatusCode; Raw = $raw; Json = $(try { $raw | ConvertFrom-Json } catch { $null }) }
    } catch {
        $httpResponse = $_.Exception.Response
        if (-not $httpResponse) { throw "网络请求失败：$Url；$($_.Exception.Message)" }
        $reader = New-Object IO.StreamReader($httpResponse.GetResponseStream())
        $raw = $reader.ReadToEnd(); $reader.Dispose()
        return [pscustomobject]@{ Status = [int]$httpResponse.StatusCode; Raw = $raw; Json = $(try { $raw | ConvertFrom-Json } catch { $null }) }
    }
}

function Assert-ApiSuccess {
    param($Response, [string]$Step, [string]$Url)
    if ($Response.Status -lt 200 -or $Response.Status -ge 300 -or $null -eq $Response.Json -or $Response.Json.code -lt 200 -or $Response.Json.code -ge 300) {
        throw "$Step 失败：$Url；HTTP=$($Response.Status)，业务 code=$($Response.Json.code)"
    }
    Write-Host "[PASS] $Step"
}

if ([string]::IsNullOrEmpty($TestPassword)) { $TestPassword = [Environment]::GetEnvironmentVariable('TEST_ACCOUNT_PASSWORD') }
if ([string]::IsNullOrEmpty($TestPassword)) { throw '请通过 -TestPassword 或 TEST_ACCOUNT_PASSWORD 提供课程测试账号密码。' }

$frontend = $FrontendBaseUrl.TrimEnd('/')
$backend = $BackendBaseUrl.TrimEnd('/')
Assert-ApiSuccess (Invoke-Http -Method Get -Url "$backend/api/health") 'Backend health and database' "$backend/api/health"
Assert-ApiSuccess (Invoke-Http -Method Get -Url "$frontend/") 'Frontend static entry' "$frontend/"

$safeReadCandidates = @(
    @{ Permission = 'person:view'; Path = '/api/persons?page=0&size=1' },
    @{ Permission = 'household:view'; Path = '/api/households?page=0&size=1' },
    @{ Permission = 'region:view'; Path = '/api/admin-regions' },
    @{ Permission = 'dictionary:view'; Path = '/api/dictionaries?page=0&size=1' },
    @{ Permission = 'application:view'; Path = '/api/applications?page=0&size=1' },
    @{ Permission = 'key-population:view'; Path = '/api/key-populations?page=0&size=1' },
    @{ Permission = 'data:export:log:view'; Path = '/api/exports?page=0&size=1' },
    @{ Permission = 'log:view'; Path = '/api/logs/operations?page=0&size=1' }
)
$accounts = if ($IncludeAllRoles) { @('viewer','population','household','approver','admin') } else { @('admin') }
$expectedRoles = @{ viewer = 'QUERY_VIEWER'; population = 'POPULATION_MANAGER'; household = 'HOUSEHOLD_MANAGER'; approver = 'APPROVER'; admin = 'SYSTEM_ADMIN' }

foreach ($username in $accounts) {
    $loginUrl = "$frontend/api/auth/login"
    $login = Invoke-Http -Method Post -Url $loginUrl -Body @{ username = $username; password = $TestPassword }
    Assert-ApiSuccess $login "Frontend proxy login ($username)" $loginUrl
    if ([string]::IsNullOrWhiteSpace([string]$login.Json.data.token)) { throw "登录响应缺少 token：$username" }
    $headers = @{ Authorization = "$($login.Json.data.tokenType) $($login.Json.data.token)" }
    $meUrl = "$frontend/api/auth/me"
    $me = Invoke-Http -Method Get -Url $meUrl -Headers $headers
    Assert-ApiSuccess $me "JWT /auth/me ($username)" $meUrl
    if ($me.Json.data.roleCode -ne $expectedRoles[$username]) { throw "账号 $username 的 roleCode 不符合课程账号契约。" }
    $permissions = @($me.Json.data.permissions)
    if ($permissions.Count -eq 0) { throw "账号 $username 的 permissions 为空。" }

    $allowed = $safeReadCandidates | Where-Object { $permissions -contains $_.Permission } | Select-Object -First 1
    if (-not $allowed) { throw "账号 $username 没有可安全验证的允许 GET 权限。" }
    $allowedUrl = "$frontend$($allowed.Path)"
    Assert-ApiSuccess (Invoke-Http -Method Get -Url $allowedUrl -Headers $headers) "Allowed GET ($username, $($allowed.Permission))" $allowedUrl

    if ($me.Json.data.roleCode -eq 'SYSTEM_ADMIN') {
        Write-Host '[SKIP] SYSTEM_ADMIN 不执行拒绝边界测试。'
        continue
    }
    $forbidden = $safeReadCandidates | Where-Object { $permissions -notcontains $_.Permission } | Select-Object -First 1
    if (-not $forbidden) {
        Write-Host "[SKIP] $username 未找到可安全验证的拒绝 GET。"
        continue
    }
    $forbiddenUrl = "$frontend$($forbidden.Path)"
    $forbiddenResponse = Invoke-Http -Method Get -Url $forbiddenUrl -Headers $headers
    if ($forbiddenResponse.Status -ne 403 -and $forbiddenResponse.Json.code -ne 403) {
        throw "Forbidden GET ($username, $($forbidden.Permission)) 未返回 403：$forbiddenUrl；HTTP=$($forbiddenResponse.Status)，业务 code=$($forbiddenResponse.Json.code)"
    }
    Write-Host "[PASS] Forbidden GET ($username, $($forbidden.Permission))"
}
