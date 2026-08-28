param(
    [string]$ParticipantCode = "PILOT_SELF_01",
    [string]$OutputDirectory = "",
    [double]$CountdownSeconds = 8.0,
    [double]$SettleSeconds = 1.5,
    [double]$MeasureSeconds = 2.5,
    [double]$InterCueSeconds = 0.5,
    [switch]$NoSpeech
)

$ErrorActionPreference = "Stop"
$runnerVersion = "260728_v2_sync_speech"

if ($CountdownSeconds -lt 0 -or
    $SettleSeconds -lt 0 -or
    $MeasureSeconds -le 0 -or
    $InterCueSeconds -lt 0) {
    throw "Timing values must satisfy countdown >= 0, settle >= 0, measure > 0, and inter-cue >= 0."
}

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot "logs\tobii\cockpit_validation"
}
if (-not [System.IO.Path]::IsPathRooted($OutputDirectory)) {
    $OutputDirectory = Join-Path $projectRoot $OutputDirectory
}
[System.IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null

$startedAt = [DateTimeOffset]::Now
$sessionId = "surround_cockpit_validation_{0}" -f $startedAt.ToString("yyyyMMdd_HHmmss")
$outputPath = Join-Path $OutputDirectory ($sessionId + "_cues.csv")
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$writer = New-Object System.IO.StreamWriter(
    $outputPath,
    $false,
    $utf8NoBom
)
$writer.AutoFlush = $true
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$script:voiceName = "NO_SPEECH"
$script:voiceCulture = ""
$script:sapiVoice = $null
$script:writer = $writer
$script:stopwatch = $stopwatch
$script:sessionId = $sessionId
$script:participantCode = $ParticipantCode

function ConvertTo-CsvField {
    param([object]$Value)
    $text = if ($null -eq $Value) { "" } else { [string]$Value }
    return '"' + $text.Replace('"', '""') + '"'
}

function Write-ValidationEvent {
    param(
        [string]$EventType,
        [int]$CueIndex = 0,
        [string]$CueId = "",
        [string]$CueText = "",
        [string]$TargetClass = "",
        [string]$TargetDirection = "",
        [string]$ExpectedAoi = "",
        [string]$Phase = "",
        [string]$Note = ""
    )

    $now = [DateTimeOffset]::Now
    $values = @(
        $script:sessionId,
        $script:participantCode,
        $now.ToString("o"),
        $now.ToUnixTimeMilliseconds(),
        [Math]::Round($script:stopwatch.Elapsed.TotalMilliseconds, 3),
        $EventType,
        $CueIndex,
        $CueId,
        $CueText,
        $TargetClass,
        $TargetDirection,
        $ExpectedAoi,
        $Phase,
        $script:voiceName,
        $script:voiceCulture,
        $Note
    )
    $line = ($values | ForEach-Object { ConvertTo-CsvField $_ }) -join ","
    $script:writer.WriteLine($line)
}

function Wait-ValidationSeconds {
    param([double]$Seconds)
    if ($Seconds -le 0) {
        return
    }
    [System.Threading.Thread]::Sleep([int][Math]::Round($Seconds * 1000.0))
}

function ConvertFrom-Utf8Base64 {
    param([string]$Value)
    return [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($Value)
    )
}

$cues = @(
    [PSCustomObject]@{
        Id = "CENTER_ATTITUDE_1"
        Text = (ConvertFrom-Utf8Base64 "7J6Q7IS46rOE66W8IOuwlOudvOuztOyLreyLnOyYpC4=")
        TargetClass = "CENTER_AOI"
        Direction = "CENTER"
        ExpectedAoi = "ATTITUDE"
    },
    [PSCustomObject]@{
        Id = "LEFT_EXTERNAL_1"
        Text = (ConvertFrom-Utf8Base64 "7Jm87Kq9IOyZuOu2gCDtmZjqsr3snYQg67CU652867O07Iut7Iuc7JikLg==")
        TargetClass = "SIDE_EXTERNAL"
        Direction = "LEFT"
        ExpectedAoi = ""
    },
    [PSCustomObject]@{
        Id = "CENTER_AIRSPEED"
        Text = (ConvertFrom-Utf8Base64 "7IaN64+E6rOE66W8IOuwlOudvOuztOyLreyLnOyYpC4=")
        TargetClass = "CENTER_AOI"
        Direction = "CENTER"
        ExpectedAoi = "AIRSPEED"
    },
    [PSCustomObject]@{
        Id = "RIGHT_EXTERNAL_1"
        Text = (ConvertFrom-Utf8Base64 "7Jik66W47Kq9IOyZuOu2gCDtmZjqsr3snYQg67CU652867O07Iut7Iuc7JikLg==")
        TargetClass = "SIDE_EXTERNAL"
        Direction = "RIGHT"
        ExpectedAoi = ""
    },
    [PSCustomObject]@{
        Id = "CENTER_ALTITUDE"
        Text = (ConvertFrom-Utf8Base64 "6rOg64+E6rOE66W8IOuwlOudvOuztOyLreyLnOyYpC4=")
        TargetClass = "CENTER_AOI"
        Direction = "CENTER"
        ExpectedAoi = "ALTITUDE"
    },
    [PSCustomObject]@{
        Id = "LEFT_EXTERNAL_2"
        Text = (ConvertFrom-Utf8Base64 "7Jm87Kq9IOyZuOu2gCDtmZjqsr3snYQg67CU652867O07Iut7Iuc7JikLg==")
        TargetClass = "SIDE_EXTERNAL"
        Direction = "LEFT"
        ExpectedAoi = ""
    },
    [PSCustomObject]@{
        Id = "CENTER_OUTSIDE"
        Text = (ConvertFrom-Utf8Base64 "7KCV66m0IOyZuOu2gCDtmZjqsr3snYQg67CU652867O07Iut7Iuc7JikLg==")
        TargetClass = "CENTER_OUTSIDE"
        Direction = "CENTER"
        ExpectedAoi = "OUTSIDE_VIEW"
    },
    [PSCustomObject]@{
        Id = "RIGHT_EXTERNAL_2"
        Text = (ConvertFrom-Utf8Base64 "7Jik66W47Kq9IOyZuOu2gCDtmZjqsr3snYQg67CU652867O07Iut7Iuc7JikLg==")
        TargetClass = "SIDE_EXTERNAL"
        Direction = "RIGHT"
        ExpectedAoi = ""
    },
    [PSCustomObject]@{
        Id = "CENTER_ATTITUDE_2"
        Text = (ConvertFrom-Utf8Base64 "7J6Q7IS46rOE6riw66W8IOuwlOudvOuztOyLreyLnOyYpC4=")
        TargetClass = "CENTER_AOI"
        Direction = "CENTER"
        ExpectedAoi = "ATTITUDE"
    }
)

$writer.WriteLine(
    "session_id,participant_code,pc_time_iso,pc_time_ms,monotonic_ms," +
    "event_type,cue_index,cue_id,cue_text,target_class,target_direction," +
    "expected_aoi,phase,voice_name,voice_culture,note"
)

try {
    if (-not $NoSpeech) {
        $script:sapiVoice = New-Object -ComObject SAPI.SpVoice
        $voiceTokens = $script:sapiVoice.GetVoices()
        if ($voiceTokens.Count -eq 0) {
            throw "No Windows SAPI voice is available."
        }
        $selectedToken = $voiceTokens.Item(0)
        for ($voiceIndex = 0; $voiceIndex -lt $voiceTokens.Count; $voiceIndex++) {
            $candidate = $voiceTokens.Item($voiceIndex)
            if ($candidate.GetDescription() -match "Korean|Heami|ko-KR") {
                $selectedToken = $candidate
                break
            }
        }
        $script:sapiVoice.Voice = $selectedToken
        $script:voiceName = $selectedToken.GetDescription()
        $script:voiceCulture = if ($script:voiceName -match "Korean|Heami") {
            "ko-KR"
        }
        else {
            ""
        }
        $script:sapiVoice.Rate = 0
        $script:sapiVoice.Volume = 100
    }

    $timingNote = (
        "runner_version={0};countdown_s={1};settle_s={2};measure_s={3};inter_cue_s={4};cue_n={5}" -f
        $runnerVersion,
        $CountdownSeconds,
        $SettleSeconds,
        $MeasureSeconds,
        $InterCueSeconds,
        $cues.Count
    )
    Write-ValidationEvent -EventType "SESSION_START" -Phase "session" -Note $timingNote
    Write-ValidationEvent -EventType "COUNTDOWN_START" -Phase "countdown"

    Write-Host ""
    Write-Host "Surround cockpit gaze validation"
    Write-Host "Session : $sessionId"
    Write-Host "Output  : $outputPath"
    Write-Host "Voice   : $($script:voiceName) $($script:voiceCulture)"
    Write-Host "Click X-Plane now and keep it in the foreground."
    Write-Host "Validation starts in $CountdownSeconds seconds."
    Write-Host ""

    if (-not $NoSpeech) {
        $script:sapiVoice.Speak(
            (ConvertFrom-Utf8Base64 "7Iuc7ISgIOqygOymneydhCDsi5zsnpHtlanri4jri6QuIOyXkeyKpCDtlIzroIjsnbgg7ZmU66m07J2EIOycoOyngO2VmOyLreyLnOyYpC4="),
            3
        ) | Out-Null
    }
    Wait-ValidationSeconds $CountdownSeconds
    Write-ValidationEvent -EventType "COUNTDOWN_END" -Phase "countdown"

    for ($index = 0; $index -lt $cues.Count; $index++) {
        $cue = $cues[$index]
        $cueNumber = $index + 1
        Write-ValidationEvent `
            -EventType "CUE_START" `
            -CueIndex $cueNumber `
            -CueId $cue.Id `
            -CueText $cue.Text `
            -TargetClass $cue.TargetClass `
            -TargetDirection $cue.Direction `
            -ExpectedAoi $cue.ExpectedAoi `
            -Phase "settle"

        if (-not $NoSpeech) {
            # Flag 2 purges queued speech but remains synchronous. The call
            # returns only after the instruction has finished playing.
            $script:sapiVoice.Speak($cue.Text, 2) | Out-Null
        }
        Write-ValidationEvent `
            -EventType "SPEECH_END" `
            -CueIndex $cueNumber `
            -CueId $cue.Id `
            -CueText $cue.Text `
            -TargetClass $cue.TargetClass `
            -TargetDirection $cue.Direction `
            -ExpectedAoi $cue.ExpectedAoi `
            -Phase "settle"

        Wait-ValidationSeconds $SettleSeconds
        Write-ValidationEvent `
            -EventType "MEASURE_START" `
            -CueIndex $cueNumber `
            -CueId $cue.Id `
            -CueText $cue.Text `
            -TargetClass $cue.TargetClass `
            -TargetDirection $cue.Direction `
            -ExpectedAoi $cue.ExpectedAoi `
            -Phase "measure"

        Wait-ValidationSeconds $MeasureSeconds
        Write-ValidationEvent `
            -EventType "MEASURE_END" `
            -CueIndex $cueNumber `
            -CueId $cue.Id `
            -CueText $cue.Text `
            -TargetClass $cue.TargetClass `
            -TargetDirection $cue.Direction `
            -ExpectedAoi $cue.ExpectedAoi `
            -Phase "measure"

        if (-not $NoSpeech) {
            $script:sapiVoice.Speak("", 3) | Out-Null
        }
        Write-ValidationEvent `
            -EventType "CUE_END" `
            -CueIndex $cueNumber `
            -CueId $cue.Id `
            -CueText $cue.Text `
            -TargetClass $cue.TargetClass `
            -TargetDirection $cue.Direction `
            -ExpectedAoi $cue.ExpectedAoi `
            -Phase "complete"

        if ($index -lt $cues.Count - 1) {
            Wait-ValidationSeconds $InterCueSeconds
        }
    }

    Write-ValidationEvent -EventType "SESSION_END" -Phase "session"
    if (-not $NoSpeech) {
        $script:sapiVoice.Speak(
            (ConvertFrom-Utf8Base64 "7Iuc7ISgIOqygOymneydtCDrgZ3rgqzsirXri4jri6Qu"),
            0
        ) | Out-Null
    }
    Write-Host "Validation complete."
    Write-Host "Saved: $outputPath"
}
catch {
    try {
        Write-ValidationEvent -EventType "ERROR" -Phase "error" -Note $_.Exception.Message
    }
    catch {
    }
    throw
}
finally {
    if ($null -ne $script:sapiVoice) {
        try {
            $script:sapiVoice.Speak("", 3) | Out-Null
        }
        catch {
        }
        [System.Runtime.InteropServices.Marshal]::FinalReleaseComObject(
            $script:sapiVoice
        ) | Out-Null
    }
    $script:stopwatch.Stop()
    $script:writer.Dispose()
}
