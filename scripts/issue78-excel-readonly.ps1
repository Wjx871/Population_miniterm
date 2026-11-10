param(
    [Parameter(Mandatory = $true)][string]$EvidenceRoot,
    [Parameter(Mandatory = $true)][string]$OutputPath
)
$ErrorActionPreference = 'Stop'
if (Test-Path -LiteralPath $OutputPath) { throw 'Output already exists; refusing to overwrite.' }
$excel = $null
$blank = $null
$results = [System.Collections.Generic.List[object]]::new()
try {
    # Dedicated invisible instance; never attach to the user's running Excel instance.
    $excel = New-Object -ComObject Excel.Application
    $excel.Visible = $false
    $excel.DisplayAlerts = $false
    $excel.EnableEvents = $false
    $excel.AskToUpdateLinks = $false
    $excel.AutomationSecurity = 3 # msoAutomationSecurityForceDisable
    $blank = $excel.Workbooks.Add()
    $excel.Calculation = -4135 # xlCalculationManual
    $excel.CalculateBeforeSave = $false
    $version = $excel.Version
    foreach ($directory in Get-ChildItem -LiteralPath $EvidenceRoot -Directory | Where-Object Name -Match '^(normal|sensitive)-') {
        $manifest = Get-Content -LiteralPath (Join-Path $directory.FullName 'manifest.json') -Raw -Encoding utf8 | ConvertFrom-Json
        if ($manifest.xmlFormulaElements -ne 0 -or $manifest.externalLinkParts -ne 0 -or $manifest.externalRelationships -ne 0) {
            throw 'Only previously inspected formula-free, external-link-free production workbooks may be opened.'
        }
        $path = Join-Path $directory.FullName 'download.xlsx'
        $before = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($before -ne $manifest.sha256) { throw 'XLSX hash differs from inspected manifest.' }
        $book = $null
        $sheet = $null
        try {
            $book = $excel.Workbooks.Open($path, 0, $true) # UpdateLinks=0, ReadOnly=true
            $sheet = $book.Worksheets.Item(1)
            $formulaCells = 0
            $checks = [System.Collections.Generic.List[object]]::new()
            foreach ($entry in $manifest.allCells.PSObject.Properties) {
                $cell = $sheet.Range($entry.Name)
                try {
                    if ($cell.HasFormula) { $formulaCells++ }
                    $actual = $cell.Value2
                    $checks.Add([ordered]@{
                        cell = $entry.Name
                        hasFormula = [bool]$cell.HasFormula
                        value = $actual
                        expected = $entry.Value.text
                        exactTextMatch = ($actual -is [string] -and $actual -ceq $entry.Value.text)
                    })
                } finally { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($cell) }
            }
            $results.Add([ordered]@{
                workbook = $directory.Name
                sha256 = $before
                readOnly = [bool]$book.ReadOnly
                connectionCount = $book.Connections.Count
                formulaCells = $formulaCells
                textMismatchCount = @($checks | Where-Object { -not $_.exactTextMatch }).Count
                cells = $checks
            })
        } finally {
            if ($sheet) { [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($sheet) }
            if ($book) {
                $book.Close($false)
                [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($book)
            }
        }
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $before) {
            throw 'Read-only Excel verification changed the original XLSX.'
        }
    }
    $report = [ordered]@{
        excelVersion = $version
        automationSecurity = 'ForceDisable'
        updateLinks = 0
        calculation = 'Manual'
        enableEvents = $false
        savedWorkbooks = $false
        results = $results
    }
    $serialized = $report | ConvertTo-Json -Depth 12
    [IO.File]::WriteAllText($OutputPath, $serialized, [Text.UTF8Encoding]::new($false))
    $results | ForEach-Object { [pscustomobject]$_ } |
        Select-Object workbook, formulaCells, textMismatchCount, readOnly, connectionCount
} finally {
    if ($blank) {
        $blank.Close($false)
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($blank)
    }
    if ($excel) {
        $excel.Quit()
        [void][Runtime.InteropServices.Marshal]::FinalReleaseComObject($excel)
    }
}
