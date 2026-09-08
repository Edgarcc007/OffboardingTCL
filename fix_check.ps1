$path = (Resolve-Path "src/main/resources/static/index.html").Path
$c = [IO.File]::ReadAllText($path)

# Show what unique patterns remain
$matches = [regex]::Matches($c, '\u00C3[^\s<>]{1,12}')
$unique = @{}
foreach ($m in $matches) { $unique[$m.Value] = $true }
foreach ($k in ($unique.Keys | Sort-Object)) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($k)
    $hex = ($bytes | ForEach-Object { '{0:X2}' -f $_ }) -join ' '
    Write-Host "$hex  =>  $k"
}
