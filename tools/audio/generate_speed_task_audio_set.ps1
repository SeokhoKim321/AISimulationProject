[CmdletBinding()]
param(
    [ValidateRange(-10, 10)]
    [int]$Rate = 1,
    [ValidateRange(0, 100)]
    [int]$Volume = 100
)

$ErrorActionPreference = "Stop"

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$generator = Join-Path $PSScriptRoot "generate_standard_task_audio.ps1"
$outputDirectory = Join-Path $projectRoot "resources\audio"

$tasks = @(
    [PSCustomObject]@{
        SpeedKias = 90
        SpokenSpeed = "nine zero"
    },
    [PSCustomObject]@{
        SpeedKias = 95
        SpokenSpeed = "nine five"
    },
    [PSCustomObject]@{
        SpeedKias = 100
        SpokenSpeed = "one zero zero"
    },
    [PSCustomObject]@{
        SpeedKias = 105
        SpokenSpeed = "one zero five"
    },
    [PSCustomObject]@{
        SpeedKias = 110
        SpokenSpeed = "one one zero"
    }
)

foreach ($task in $tasks) {
    $outputPath = Join-Path $outputDirectory (
        "apisat_task_maintain_{0}kias_centerline_en_us.wav" -f $task.SpeedKias
    )
    $text = (
        "Cessna zero one, continue straight in, maintain {0} knots, track runway centerline." -f
        $task.SpokenSpeed
    )

    if (Test-Path -LiteralPath $outputPath) {
        Write-Output "Keeping existing audio: $outputPath"
        continue
    }

    & $generator `
        -OutputPath $outputPath `
        -Text $text `
        -Rate $Rate `
        -Volume $Volume
}
