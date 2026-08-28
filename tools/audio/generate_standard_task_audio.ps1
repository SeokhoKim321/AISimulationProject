[CmdletBinding()]
param(
    [string]$OutputPath = "",
    [string]$Text = "Cessna zero one, continue straight in, maintain seven zero knots, track runway centerline.",
    [string]$PreferredVoicePattern = "Zira|English|en-US",
    [ValidateRange(-10, 10)]
    [int]$Rate = 1,
    [ValidateRange(0, 100)]
    [int]$Volume = 100
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
    $OutputPath = Join-Path $projectRoot "resources\audio\apisat_task_maintain_70kias_centerline_en_us.wav"
}

$fullOutputPath = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $fullOutputPath
if (-not (Test-Path -LiteralPath $outputDirectory -PathType Container)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}
if (Test-Path -LiteralPath $fullOutputPath) {
    throw "Refusing to overwrite existing audio file: $fullOutputPath"
}

$voice = $null
$tokens = $null
$selectedToken = $null
$fileStream = $null
$originalOutputStream = $null

try {
    $voice = New-Object -ComObject SAPI.SpVoice
    $tokens = $voice.GetVoices()
    if ($tokens.Count -eq 0) {
        throw "No Windows SAPI voice is available."
    }

    $selectedToken = $tokens.Item(0)
    for ($index = 0; $index -lt $tokens.Count; $index++) {
        $candidate = $tokens.Item($index)
        if ($candidate.GetDescription() -match $PreferredVoicePattern) {
            $selectedToken = $candidate
            break
        }
    }

    $voice.Voice = $selectedToken
    $voice.Rate = $Rate
    $voice.Volume = $Volume

    $fileStream = New-Object -ComObject SAPI.SpFileStream
    $fileStream.Open($fullOutputPath, 3, $false)
    $originalOutputStream = $voice.AudioOutputStream
    $voice.AudioOutputStream = $fileStream
    [void]$voice.Speak($Text, 0)
    $voice.AudioOutputStream = $originalOutputStream
    $fileStream.Close()

    [PSCustomObject]@{
        OutputPath = $fullOutputPath
        Voice = $selectedToken.GetDescription()
        Rate = $Rate
        Volume = $Volume
        Text = $Text
        Bytes = (Get-Item -LiteralPath $fullOutputPath).Length
    }
}
finally {
    if ($null -ne $fileStream) {
        try {
            $fileStream.Close()
        }
        catch {
        }
    }
    foreach ($comObject in @($originalOutputStream, $fileStream, $selectedToken, $tokens, $voice)) {
        if ($null -ne $comObject -and [System.Runtime.InteropServices.Marshal]::IsComObject($comObject)) {
            [void][System.Runtime.InteropServices.Marshal]::FinalReleaseComObject($comObject)
        }
    }
}
