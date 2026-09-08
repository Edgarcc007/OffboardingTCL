$path = (Resolve-Path "src/main/resources/static/index.html").Path
$c = [IO.File]::ReadAllText($path)

function HexToStr($hex) {
    $bytes = $hex.Split(' ') | ForEach-Object { [Convert]::ToByte($_, 16) }
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

# Triple-encoded Spanish chars
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 82 C2 A1"), (HexToStr "C3 A1"))  # á
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 82 C2 A9"), (HexToStr "C3 A9"))  # é
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 82 C2 AD"), (HexToStr "C3 AD"))  # í
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 82 C2 B3"), (HexToStr "C3 B3"))  # ó
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 82 C2 BA"), (HexToStr "C3 BA"))  # ú
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 82 C2 B1"), (HexToStr "C3 B1"))  # ñ
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 82 C2 8D 41"), (HexToStr "C3 81"))  # Á (ÃƒÂA)
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 82 C2 81"), (HexToStr "C3 81"))  # Á (Ârea)
$c = $c.Replace((HexToStr "C3 83 C6 92 C3 A2 E2 82 AC C5 93 4E"), (HexToStr "C3 93 4E"))  # ÓN

# ¿ and ·
$c = $c.Replace((HexToStr "C3 83 E2 80 9A C3 82 C2 BF"), (HexToStr "C2 BF"))  # ¿
$c = $c.Replace((HexToStr "C3 83 E2 80 9A C3 82 C2 B7"), (HexToStr "C2 B7"))  # ·

# em-dash, en-dash, triangle
$c = $c.Replace((HexToStr "C3 83 C2 A2 C3 A2 E2 80 9A C2 AC C3 A2 E2 82 AC C2 9D"), (HexToStr "E2 80 94"))  # —
$c = $c.Replace((HexToStr "C3 83 C2 A2 C3 A2 E2 80 9A C2 AC C3 A2 E2 82 AC C5 93"), (HexToStr "E2 80 93"))  # –
$c = $c.Replace((HexToStr "C3 83 C2 A2 C3 A2 E2 82 AC E2 80 9C C3 82 C2 B6"), (HexToStr "E2 96 B6"))  # ▶

[IO.File]::WriteAllText($path, $c, [System.Text.Encoding]::UTF8)

$remaining = ([regex]::Matches($c, '\u00C3[^\s<>]{1,12}')).Count
Write-Host "Remaining mojibake matches: $remaining"
if ($remaining -gt 0) {
    [regex]::Matches($c, '\u00C3[^\s<>]{1,12}') | ForEach-Object { $_.Value } | Sort-Object -Unique
}
