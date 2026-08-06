Add-Type -AssemblyName System.Drawing

$root = $PSScriptRoot
$res = "$root\res"

function New-RoundRectPath($rect, $radius) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $radius * 2
    $p.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
    $p.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
    $p.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
    $p.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function Draw-Note($g, $s, $ox, $oy) {
    $white = [System.Drawing.Brushes]::White
    $head1 = New-Object System.Drawing.RectangleF((44 * $s + $ox), (126 * $s + $oy), (46 * $s), (36 * $s))
    $head2 = New-Object System.Drawing.RectangleF((104 * $s + $ox), (126 * $s + $oy), (46 * $s), (36 * $s))
    $stem1 = New-Object System.Drawing.RectangleF((84 * $s + $ox), (48 * $s + $oy), (10 * $s), (100 * $s))
    $stem2 = New-Object System.Drawing.RectangleF((144 * $s + $ox), (48 * $s + $oy), (10 * $s), (100 * $s))
    $beam = New-Object System.Drawing.RectangleF((84 * $s + $ox), (48 * $s + $oy), (70 * $s), (12 * $s))
    $g.FillEllipse($white, $head1)
    $g.FillEllipse($white, $head2)
    $g.FillRectangle($white, $stem1)
    $g.FillRectangle($white, $stem2)
    $g.FillRectangle($white, $beam)
}

function New-Icon($size, $path) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)
    $rect = New-Object System.Drawing.Rectangle(0, 0, $size, $size)
    $r = [Math]::Max(8, [int]($size * 0.22))
    $rr = New-RoundRectPath $rect $r
    $lg = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, [System.Drawing.Color]::FromArgb(255, 30, 58, 138), [System.Drawing.Color]::FromArgb(255, 56, 189, 248), 90)
    $g.FillPath($lg, $rr)
    Draw-Note $g ($size / 192.0) 0 0
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $bmp.Dispose()
}

$sizes = @{ "mipmap-mdpi" = 48; "mipmap-hdpi" = 72; "mipmap-xhdpi" = 96; "mipmap-xxhdpi" = 144; "mipmap-xxxhdpi" = 192 }
foreach ($k in $sizes.Keys) {
    $dir = "$res\$k"
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    New-Icon $sizes[$k] "$dir\ic_launcher.png"
}

New-Item -ItemType Directory -Path "$res\mipmap-xxxhdpi" -Force | Out-Null
$bmp = New-Object System.Drawing.Bitmap(432, 432)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)
$s = 1.5
$ox = (432 - 146 * $s) / 2
$oy = (432 - 114 * $s) / 2
Draw-Note $g $s $ox $oy
$bmp.Save("$res\mipmap-xxxhdpi\ic_launcher_foreground.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()

New-Item -ItemType Directory -Path "$res\values" -Force | Out-Null
Set-Content -Path "$res\values\colors.xml" -Encoding UTF8 -Value '<?xml version="1.0" encoding="utf-8"?>', '<resources>', '    <color name="ic_launcher_background">#1E3A8A</color>', '</resources>'

New-Item -ItemType Directory -Path "$res\mipmap-anydpi-v26" -Force | Out-Null
Set-Content -Path "$res\mipmap-anydpi-v26\ic_launcher.xml" -Encoding UTF8 -Value '<?xml version="1.0" encoding="utf-8"?>', '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">', '    <background android:drawable="@color/ic_launcher_background"/>', '    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>', '</adaptive-icon>'

Write-Output "ICONS DONE"
Get-ChildItem $res -Recurse -File | Select-Object -ExpandProperty FullName
