[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$BasePort,

    [ValidateRange(1, 20)]
    [int]$CandidateCount = 5
)

$lastPort = $BasePort + $CandidateCount - 1
if ($lastPort -gt 65535) {
    [Console]::Error.WriteLine("FRONTEND_PORT=$BasePort leaves too few valid ports for $CandidateCount candidates.")
    exit 1
}

for ($port = $BasePort; $port -le $lastPort; $port++) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($connections) {
        continue
    }

    $listener = $null
    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, $port)
        $listener.Start()
        Write-Output $port
        exit 0
    } catch {
        continue
    } finally {
        if ($null -ne $listener) {
            try { $listener.Stop() } catch { }
        }
    }
}

[Console]::Error.WriteLine("No available frontend port found in range $BasePort-$lastPort.")
exit 1
