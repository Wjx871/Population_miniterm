$root = "d:\Population_System\src\main\java"
$files = Get-ChildItem -Path $root -Recurse -Include *.java
$total = 0
$byDir = @{}
foreach ($f in $files) {
    $lines = (Get-Content $f.FullName | Measure-Object -Line).Lines
    $total += $lines
    $dir = Split-Path -Path (Split-Path -Path $f.FullName -Parent) -Leaf
    if (-not $byDir.ContainsKey($dir)) { $byDir[$dir] = 0 }
    $byDir[$dir] += $lines
}
"Total files: $($files.Count)"
"Total lines:  $total"
""
"By package:"
foreach ($k in ($byDir.Keys | Sort-Object)) {
    "  {0,-15} {1,6}" -f $k, $byDir[$k]
}