param(
    [Parameter(Mandatory = $true)]
    [string]$ImagePath,

    [Parameter(Mandatory = $true)]
    [string]$AoiCsvPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

Add-Type -AssemblyName System.Drawing

$image = [System.Drawing.Image]::FromFile((Resolve-Path -LiteralPath $ImagePath))
$bitmap = New-Object System.Drawing.Bitmap $image
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

$font = New-Object System.Drawing.Font "Arial", 18, ([System.Drawing.FontStyle]::Bold)
$pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::Lime), 3
$brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(210, 0, 0, 0))
$textBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)

$aois = Import-Csv -LiteralPath $AoiCsvPath
foreach ($aoi in $aois) {
    $priority = [string]$aoi.priority
    $status = [string]$aoi.status
    if ($priority.ToLowerInvariant() -eq "exclude" -or $status.ToLowerInvariant() -eq "exclude") {
        continue
    }

    $x1 = [int][double]$aoi.x1
    $y1 = [int][double]$aoi.y1
    $x2 = [int][double]$aoi.x2
    $y2 = [int][double]$aoi.y2
    $width = $x2 - $x1
    $height = $y2 - $y1

    $graphics.DrawRectangle($pen, $x1, $y1, $width, $height)
    $labelRect = New-Object System.Drawing.RectangleF $x1, ([Math]::Max(0, $y1 - 24)), 360, 28
    $graphics.FillRectangle($brush, $labelRect)
    $graphics.DrawString($aoi.aoi, $font, $textBrush, $labelRect)
}

$graphics.Dispose()
$image.Dispose()
$bitmap.Save((Resolve-Path -LiteralPath (Split-Path -Parent $OutputPath)).Path + "\" + (Split-Path -Leaf $OutputPath), [System.Drawing.Imaging.ImageFormat]::Png)
$bitmap.Dispose()

Write-Host "Wrote $OutputPath"
