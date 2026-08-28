# Tobii Surround cockpit audio-cue pilot

Date: 2026-07-28

## Purpose

This pilot checks the coarse `center versus side external` classifier while the
actual X-Plane cockpit is visible. It does not validate precise gaze
coordinates on the side monitors.

The participant does not press a key. A PowerShell tool speaks nine Korean
instructions and records `CUE_START`, `SPEECH_END`, `MEASURE_START`,
`MEASURE_END`, and `CUE_END` timestamps to CSV. A Python analyzer aligns those
timestamps with the Tobii gaze CSV.

Tools:

- `tools/tobii/run_surround_cockpit_validation.ps1`
- `tools/tobii/analyze_surround_cockpit_validation.py`
- `tools/tobii/analyze_xplane_tobii_session.py`

## Prerequisites

- NVIDIA Surround remains one logical `5760x1080` display.
- The three physical monitors are each `1920x1080`.
- The center monitor occupies global x `1920..3840`.
- X-Plane lateral FOV remains fixed at the verified `122 deg`; all visual
  offsets remain `0 deg`.
- Tobii Active Display Area remains `1794 x 336 mm` with the corrected
  center-bottom tracker position and completed calibration.
- X-Plane is full screen and the normal center cockpit view is on the middle
  monitor.
- Tobii Pro Eye Tracker Manager is closed before collection.
- `session_xplane_tobii_logger.py` is already running and writing under
  `logs/tobii`.
- Java receiver is not required for this audio-cue setup check. Start it only
  for the later intruder trial.

Windows SAPI COM speech voice verified on 2026-07-28:

```text
Microsoft Heami Desktop - Korean / ko-KR
```

The cue runner uses `SAPI.SpVoice`. `System.Speech` could enumerate the
installed voice on this PC but could not select it, so it is not used.
Korean cue constants are stored as ASCII-safe UTF-8 Base64 and decoded at
runtime because Windows PowerShell 5.1 can misread non-ASCII text in a
BOM-less UTF-8 script.

## Cue protocol

Default timing:

- countdown: `8.0 s`
- settle after each spoken instruction: `1.5 s`
- measurement window: `2.5 s`
- gap between cues: `0.5 s`
- total runtime: approximately `65-75 s`, depending on TTS duration

Cue order:

1. `ATTITUDE`
2. left external
3. `AIRSPEED`
4. right external
5. `ALTITUDE`
6. left external
7. center outside view
8. right external
9. `ATTITUDE`

The voice is played synchronously. `SPEECH_END` is written after audible
completion, then the full settle interval runs. Only the later
`MEASURE_START..MEASURE_END` interval is scored.

## Collection

First start the ordinary Tobii logger:

```powershell
cd "C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1\64"
py -3.10 session_xplane_tobii_logger.py
```

Keep the X-Plane cockpit visible. In a second PowerShell window:

```powershell
cd "$env:USERPROFILE\Desktop\AISimulationProject"

.\tools\tobii\run_surround_cockpit_validation.ps1 `
  -ParticipantCode "PILOT_SELF_01"
```

The 8-second countdown allows time to click X-Plane and keep it in the
foreground. Follow the voice naturally with the eyes and head. Do not try to
keep the Spark tracking when looking at the outer side screens.

Cue output:

```text
logs\tobii\cockpit_validation\surround_cockpit_validation_YYYYMMDD_HHMMSS_cues.csv
```

After the completion voice, stop the Tobii logger with `Ctrl+C`.

## Analysis

From the project root:

```powershell
$cues = Get-ChildItem "logs\tobii\cockpit_validation\*_cues.csv" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

$gaze = Get-ChildItem "logs\tobii\session_xplane_tobii_*_gaze.csv" |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$prefix = "build_atc_tmp\surround_cockpit_pilot_$stamp"

py -3.10 tools\tobii\analyze_surround_cockpit_validation.py `
  --cues "$($cues.FullName)" `
  --gaze "$($gaze.FullName)" `
  --output-prefix "$prefix"
```

Outputs:

- `*_cue_results.csv`
- `*_summary.json`
- `*_gaze_classified.csv`
- `*_external_episodes.csv`

## Default classifier

- side seam guard: `120 px`
- measured side dwell: `200 ms`
- inferred continuous bilateral loss: `500~5000 ms`
- maximum sample gap inside an inferred episode: `100 ms`
- center AOI dwell: `200 ms`

Evidence classes:

- `MEASURED_SIDE_LEFT`
- `MEASURED_SIDE_RIGHT`
- `INFERRED_SIDE_UNKNOWN`
- `UNRESOLVED_TRACKING_LOSS`
- `CENTER_OUTSIDE_MEASURED`

Only measured evidence can carry a left/right direction.

## Pilot decision

`PILOT_PASS` requires:

- every cue sample coverage at least `80%`;
- side detection sensitivity at least `90%`;
- center specificity at least `90%`;
- expected center AOI dwell success at least `80%`.

With four side cues, the sensitivity threshold effectively requires `4/4`.
With five center cues, center specificity requires `5/5`.

The results are a setup check for the current participant and calibration.
They are not a general eye-tracker accuracy estimate.

If the pilot passes, keep Surround for one short natural X-Plane intruder
trial and inspect measured, inferred, and unresolved evidence separately. If
it fails, inspect the cue-level CSV before changing thresholds or restoring
the center-only display setup.

## Automated verification completed

The cue tool passed a no-speech shortened dry run:

- nine `CUE_START`
- nine `SPEECH_END`
- nine `MEASURE_START`
- nine `MEASURE_END`
- nine `CUE_END`
- one `SESSION_END`

The v2 shortened dry run writes `49` total rows. Runner version
`260728_v2_sync_speech` is stored in the session-start note.

A synthetic end-to-end dataset mixed measured left/right cues with inferred
loss-only cues. The analyzer returned:

```text
Status: PILOT_PASS
Side: 4/4
Center specificity: 5/5
Center AOI dwell: 5/5
Direction when evaluable: 2/2
```

No original X-Plane or Tobii experiment CSV was modified.

## First physical run

The first run paired cue session `20260728_141201` with gaze session
`20260728_140939` and returned:

```text
Status: PILOT_FAIL
Side: 4/4
Center specificity: 4/5
Center AOI dwell: 3/5
Direction when evaluable: 2/2
```

This run is diagnostic only. The participant reported delayed responses, and
the AIRSPEED window retained a measured LEFT episode for about `1.116 s`.
Runner v1 used asynchronous speech flag `3`, so settle time could expire before
the instruction finished. Runner v2 uses synchronous purge flag `2`; a new
physical run is required before making the setup decision.

## Corrected physical run

Runner v2 paired cue session `20260728_142411` with gaze session
`20260728_142328`:

```text
Status: PILOT_PASS
Minimum coverage: 93.6%
Side: 4/4
Center specificity: 5/5
Center target dwell: 5/5
Direction when evaluable: 3/3
Mean valid gaze rate: 67.8%
```

Three side cues had directly measured and correct direction. One left cue was
loss-only `INFERRED_SIDE_UNKNOWN` and therefore had no claimed direction.
Freeze the current Surround, FOV, calibration, and classifier settings. The
next step was one short natural X-Plane intruder diagnostic with measured,
inferred, and unresolved evidence inspected separately.

That diagnostic was completed in Java session
`session_surround_tobii_20260728_144151` with two clean trials. Both trials
were already operationally external before spawn, so the result validates the
merge pipeline but not an intruder-acquisition latency. Main data collection
must wait for participant status/countdown hiding, the standardized instrument
task, independent `3~10 s` spawn timing, and spawn-anchored response detection.
