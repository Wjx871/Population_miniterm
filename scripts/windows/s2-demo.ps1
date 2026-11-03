param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$targetRoot = Join-Path $repoRoot 'target'
$logRoot = Join-Path (Join-Path $targetRoot 's2-demo-logs') (Get-Date -Format 'yyyyMMdd-HHmmss-fff')
$mavenWrapper = Join-Path $repoRoot 'mvnw.cmd'
if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $java = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $java = (Get-Command java.exe -ErrorAction Stop).Source
}
$javaHome = Split-Path -Parent (Split-Path -Parent $java)
if ((Test-Path -LiteralPath (Join-Path $javaHome 'release')) -and
    (Select-String -LiteralPath (Join-Path $javaHome 'release') -Pattern '^JAVA_VERSION="17\.' -Quiet)) {
    Write-Output "Runtime JDK: $javaHome (Java 17)"
} else {
    Write-Output "Runtime JDK: $javaHome (Java 17 target is compiled with --release 17; this host runtime is not JDK 17)"
}

New-Item -ItemType Directory -Path $logRoot -Force | Out-Null
& $mavenWrapper -q -DskipTests compile
if ($LASTEXITCODE -ne 0) { throw 'Maven compile failed.' }
& $mavenWrapper -q dependency:build-classpath '-DincludeScope=runtime' "-Dmdep.outputFile=target/s2-runtime.classpath"
if ($LASTEXITCODE -ne 0) { throw 'Could not build the standalone runtime classpath.' }
$dependencies = (Get-Content -LiteralPath (Join-Path $targetRoot 's2-runtime.classpath') -Raw).Trim()
$classPath = (Join-Path $targetRoot 'classes') + ';' + $dependencies
$logConfig = Join-Path $repoRoot 'src\main\resources\log4j2-spring.xml'
$owned = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()

function Assert-PortFree([int]$Port) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
        $owners = ($listeners | ForEach-Object { "$($_.LocalAddress):$($_.LocalPort) pid=$($_.OwningProcess)" }) -join ', '
        throw "Port $Port is already in use: $owners"
    }
}

function Assert-LoopbackListener([int]$Port, [int]$ProcessId) {
    $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
    if ($listeners.Count -eq 0) { throw "No LISTEN socket found for port $Port." }
    foreach ($listener in $listeners) {
        if ($listener.LocalAddress -ne '127.0.0.1') {
            throw "Port $Port is exposed on unexpected address $($listener.LocalAddress)."
        }
        if ($listener.OwningProcess -ne $ProcessId) {
            throw "Port $Port is owned by PID $($listener.OwningProcess), expected service PID $ProcessId."
        }
    }
    $listeners | Select-Object LocalAddress,LocalPort,OwningProcess | Format-Table -AutoSize
}

function Start-DemoServer([string]$Label, [string]$MainClass, [string[]]$Properties) {
    $stdout = Join-Path $logRoot "$Label.stdout.log"
    $stderr = Join-Path $logRoot "$Label.stderr.log"
    $arguments = @("-Dlog4j.configurationFile=$logConfig", "-DLOG_DIR=$logRoot") + $Properties + @('-cp', $classPath, $MainClass)
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $repoRoot `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $owned.Add($process)
    Write-Host "$Label started PID=$($process.Id)"
    return $process
}

function Wait-Listener([int]$Port, [int]$ProcessId, [string]$Label) {
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
        if (-not $process) { throw "$Label exited before opening port $Port." }
        $listeners = @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
        if ($listeners.Count -gt 0) {
            Assert-LoopbackListener -Port $Port -ProcessId $ProcessId
            return
        }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "$Label did not open port $Port within 20 seconds."
}

function Stop-OwnedProcess([System.Diagnostics.Process]$Process) {
    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -ErrorAction SilentlyContinue
        try { Wait-Process -Id $Process.Id -Timeout 10 -ErrorAction SilentlyContinue } catch {}
    }
}

function Invoke-DemoClient([string]$Label, [string]$MainClass, [string[]]$Properties, [int]$ExpectedExitCode) {
    $arguments = @("-Dlog4j.configurationFile=$logConfig", "-DLOG_DIR=$logRoot") + $Properties + @('-cp', $classPath, $MainClass)
    $output = @(& $java @arguments 2>&1 | ForEach-Object { $_.ToString() })
    $actualExitCode = $LASTEXITCODE
    $output | ForEach-Object { Write-Host $_ }
    [IO.File]::WriteAllLines((Join-Path $logRoot "$Label.log"), $output, [Text.Encoding]::UTF8)
    if ($actualExitCode -ne $ExpectedExitCode) {
        throw "$Label exit code $actualExitCode did not match expected $ExpectedExitCode."
    }
    return ($output -join "`n")
}

try {
    $rmiRegistryPort = 1199
    $rmiObjectPort = 1200
    $soapPort = 8091
    Assert-PortFree $rmiRegistryPort
    Assert-PortFree $rmiObjectPort
    Assert-PortFree $soapPort

    $rmiProperties = @("-Ds2.rmi.registry-port=$rmiRegistryPort", "-Ds2.rmi.object-port=$rmiObjectPort")
    $rmiTraceId = 'trace-demo-rmi-20261103'
    $rmiServer = Start-DemoServer 'rmi' 'com.wjx871.population.s2.rmi.RmiServerMain' $rmiProperties
    Wait-Listener $rmiRegistryPort $rmiServer.Id 'RMI Registry'
    Wait-Listener $rmiObjectPort $rmiServer.Id 'RMI Remote Object'
    $rmiOutput = Invoke-DemoClient 'rmi-success' 'com.wjx871.population.s2.rmi.RmiClientMain' `
        ($rmiProperties + "-Ds2.traceId=$rmiTraceId") 0
    if ($rmiOutput -notmatch [regex]::Escape("127.0.0.1:$rmiObjectPort") -or
        $rmiOutput -notmatch [regex]::Escape("traceId=$rmiTraceId")) {
        throw 'RMI client evidence did not include the expected loopback stub endpoint and traceId.'
    }
    Stop-OwnedProcess $rmiServer
    $rmiServer = $null
    $rmiUnavailable = Invoke-DemoClient 'rmi-stopped' 'com.wjx871.population.s2.rmi.RmiClientMain' `
        ($rmiProperties + "-Ds2.traceId=$rmiTraceId") 2
    if ($rmiUnavailable -notmatch 'REMOTE_UNAVAILABLE') { throw 'Stopped RMI service was not classified as REMOTE_UNAVAILABLE.' }

    $delayedRmi = Start-DemoServer 'rmi-delayed' 'com.wjx871.population.s2.rmi.RmiServerMain' `
        ($rmiProperties + '-Ds2.rmi.demo-delay-millis=3500')
    Wait-Listener $rmiRegistryPort $delayedRmi.Id 'Delayed RMI service'
    $rmiTimeout = Invoke-DemoClient 'rmi-timeout' 'com.wjx871.population.s2.rmi.RmiClientMain' `
        ($rmiProperties + "-Ds2.traceId=$rmiTraceId") 2
    if ($rmiTimeout -notmatch 'REMOTE_TIMEOUT') { throw 'Delayed RMI call was not classified as REMOTE_TIMEOUT.' }
    Stop-OwnedProcess $delayedRmi

    $soapProperties = @("-Ds2.soap.port=$soapPort")
    $soapTraceId = 'trace-demo-soap-20261103'
    $soapServer = Start-DemoServer 'soap' 'com.wjx871.population.s2.soap.SoapServerMain' $soapProperties
    Wait-Listener $soapPort $soapServer.Id 'SOAP service'
    $wsdlUrl = "http://127.0.0.1:$soapPort/services/statistics?wsdl"
    $wsdl = Invoke-WebRequest -Uri $wsdlUrl -TimeoutSec 5 -UseBasicParsing
    if ($wsdl.StatusCode -ne 200 -or $wsdl.Content -notmatch 'definitions') { throw 'WSDL was not returned as a valid service description.' }
    Write-Output "WSDL verified: $wsdlUrl (HTTP $($wsdl.StatusCode))"
    $wsdlServiceNames = [regex]::Matches($wsdl.Content, '<(?:\w+:)?service[^>]*\bname="([^"]+)"') |
        ForEach-Object { $_.Groups[1].Value }
    Write-Output ('WSDL service QName local names: ' + ($wsdlServiceNames -join ', '))
    $soapOutput = Invoke-DemoClient 'soap-success' 'com.wjx871.population.s2.soap.SoapClientMain' `
        ($soapProperties + "-Ds2.traceId=$soapTraceId") 0
    if ($soapOutput -notmatch [regex]::Escape("traceId=$soapTraceId")) { throw 'SOAP response did not preserve traceId.' }
    $soapFault = Invoke-DemoClient 'soap-invalid' 'com.wjx871.population.s2.soap.SoapClientMain' `
        ($soapProperties + "-Ds2.traceId=$soapTraceId" + '-Ds2.demo.invalid=true') 3
    if ($soapFault -notmatch 'SOAP Fault') { throw 'Invalid SOAP request did not yield the expected SOAP Fault.' }
    Stop-OwnedProcess $soapServer
    $soapServer = $null
    $soapUnavailable = Invoke-DemoClient 'soap-unavailable' 'com.wjx871.population.s2.soap.SoapClientMain' `
        ($soapProperties + "-Ds2.traceId=$soapTraceId") 4
    if ($soapUnavailable -notmatch 'SOAP client unavailable') { throw 'Stopped SOAP service was not reported as unavailable.' }

    Write-Output "S2 cross-process demo passed. Logs and PID-correlated evidence: $logRoot"
} finally {
    foreach ($process in $owned) { Stop-OwnedProcess $process }
}
