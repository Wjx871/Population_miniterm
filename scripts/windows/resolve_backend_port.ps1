[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$BasePort,

    [ValidateSet('SelectAvailable', 'FindRunning')]
    [string]$Mode = 'SelectAvailable',

    [ValidateRange(1, 20)]
    [int]$CandidateCount = 5
)

$lastPort = $BasePort + $CandidateCount - 1
if ($lastPort -gt 65535) {
    [Console]::Error.WriteLine("SERVER_PORT=$BasePort leaves too few valid ports for $CandidateCount candidates.")
    exit 1
}

function Test-BackendResponding {
    param([int]$Port)

    try {
        $uri = "http://127.0.0.1:$Port/api/health"
        $response = Invoke-WebRequest -Uri $uri -Method Get -TimeoutSec 1 -UseBasicParsing -ErrorAction Stop
        $payload = $response.Content | ConvertFrom-Json -ErrorAction Stop
        return ($response.StatusCode -eq 200 -and
            $payload.code -eq 200 -and
            $null -ne $payload.data -and
            -not [string]::IsNullOrWhiteSpace([string]$payload.data.database))
    } catch {
        return $false
    }
}

function Test-PortAvailable {
    param([int]$Port)

    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $Port)
        $listener.Start()
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $listener) {
            try { $listener.Stop() } catch { }
        }
    }
}

for ($port = $BasePort; $port -le $lastPort; $port++) {
    if (Test-BackendResponding -Port $port) {
        Write-Output $port
        exit 0
    }

    if ($Mode -eq 'FindRunning') {
        continue
    }

    if (Test-PortAvailable -Port $port) {
        Write-Output $port
        exit 0
    }
}

if ($Mode -eq 'SelectAvailable') {
    [Console]::Error.WriteLine("No available backend port found in range $BasePort-$lastPort.")
}
exit 1
