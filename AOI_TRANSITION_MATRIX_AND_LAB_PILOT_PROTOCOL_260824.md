# AOI transition matrix and lab participant pilot protocol

## 1. IRB boundary

This protocol is ready for use after the applicable IRB approval or formal exemption determination.
Do not collect data from laboratory members before that decision if the data may be used in a
thesis, APISAT paper, journal paper, or other generalizable research. A technical test collected
before approval must remain a system-development test and must not later be relabeled as main
participant data.

Use non-identifying participant codes such as `P001`. Do not put a participant name, employee
number, or student number in file names. Keep the code key separately under the approved data
management procedure.

Laboratory members without pilot qualifications or relevant flight experience are not a sample of
pilots. Their results may support feasibility, procedure validation, and novice comparison, but must
not be reported as representative pilot behavior. Record qualification and experience as the
categories approved by the IRB.

## 2. Benchmark matrix and project adaptation

Hanak, Novak, and Chudy, *Cognitive Agent Evaluation for Synthetic Pilot Training* (DASC 2025),
model visual attention using a Discrete-Time Markov Chain. For source attention zone `i` and next
zone `j`, the matrix element is:

```text
S_i(j) = P(s_(t+1) = j | s_t = i)
```

Every row therefore sums to one. A diagonal element such as `ATTITUDE -> ATTITUDE` represents
continued attention in the same zone. The reference paper builds separate matrices for different
tactical states and uses self-transitions to encode attention persistence.

This project adapts that structure as follows:

- observation unit: dominant eligible AOI in each fixed `200 ms` bin
- instrument AOIs: `AIRSPEED`, `ATTITUDE`, `ALTITUDE`, `HEADING`, `VERTICAL_SPEED`, `NAV_GPS`
- additional AOIs: `PANEL_OTHER`, `INTRUDER_AOI`, `RUNWAY_AOI`, `OTHER_EXTERNAL`
- unresolved or invalid bins: excluded and used to break the transition chain
- phase-specific matrices:
  - `FULL_TRIAL`
  - `BEFORE_SPAWN`
  - `SPAWN_TO_OPPORTUNITY`
  - `AFTER_OPPORTUNITY`

The `200 ms` states are not called fixations because the project does not yet use a separately
validated fixation detector. `INTRUDER_AOI` and `RUNWAY_AOI` are also operational gaze-to-object
evidence, not proof of recognition, awareness, or intention. The current `45 px` object margin
remains provisional until controlled target-gaze validation freezes it.

Two matrices are produced:

1. DTMC probability matrix: includes self-transitions and is closest to the benchmark paper.
2. Next-switch probability matrix: excludes self-transitions and directly answers, "After leaving
   this AOI, which AOI was viewed next?"

For multiple participants, the primary visualization uses the participant-equal mean of each
participant's row probability. Pooled transition counts and each participant's matrix are retained
as sensitivity and audit outputs so that a participant with more valid gaze samples does not silently
dominate the group figure.

## 3. Added analysis tool

Files:

- `tools/tobii/analyze_aoi_transition_matrix.py`
- `tools/tobii/analyze_aoi_transition_matrix_smoke_test.py`
- `resources/aoi_transition_session_manifest_template_260824.csv`

Per-session inputs:

- `*_external_context_gaze.csv` from `classify_xplane_external_gaze.py`
- matching X-Plane `*_events.csv`

Outputs:

- `*_binned_attention_states.csv`
- `*_transition_observations.csv`
- `*_dwell_episodes.csv`
- `*_dtmc_count_matrix.csv`
- `*_dtmc_probability_matrix.csv`
- `*_dtmc_probability_matrix.svg`
- `*_switch_count_matrix.csv`
- `*_switch_probability_matrix.csv`
- `*_switch_probability_matrix.svg`
- `*_summary.csv`
- `*_metadata.csv`

Single-session example:

```powershell
py -3.10 "tools\tobii\analyze_aoi_transition_matrix.py" `
  --context-gaze "build_atc_tmp\xplane_v39_integrated_20260812_155103_external_context_gaze.csv" `
  --events "logs\xplane\xplane_v39_integrated_20260812_155103_events.csv" `
  --participant-code "PILOT_SELF_01" `
  --session-label "v39_integrated_20260812_155103" `
  --output-prefix "build_atc_tmp\xplane_v39_integrated_20260812_155103_aoi_transition"
```

Multi-participant aggregation uses a working copy of the manifest template. Set `include=1` only
after confirming that the context-gaze and event paths match the same session.

```powershell
py -3.10 "tools\tobii\analyze_aoi_transition_matrix.py" `
  --manifest "build_atc_tmp\aoi_transition_session_manifest_260824.csv" `
  --output-prefix "build_atc_tmp\lab_participants_aoi_transition_260824"
```

## 4. One-session-per-person procedure

The experimental unit is one participant session containing six automatic trials. Every session
contains `LEFT`, `RIGHT`, and `FRONT` twice each in a newly randomized balanced order. The
participant starts the session once and does not press a key for individual trials. The automatic
logic performs the speed instruction, target-band gate, intruder generation, trial closure, washout,
and next-trial start.

Keep these conditions fixed across participants:

- the same saved X-Plane starting situation, approximately 10 NM final and 2100 ft
- active Lua `260811_intruder_ai_autopilot_override_v39`
- NVIDIA Surround `5760 x 1080`
- X-Plane horizontal FOV `122 deg`; lateral, vertical, and roll offsets `0 deg`
- the same aircraft, runway 34, weather, controller sensitivity, audio volume, and seating geometry
- Tobii whole-wide display setup and a fresh participant calibration
- center monitor containing the primary instrument panel
- no countdown, intruder timing, debug geometry, or advisory on the participant display

Before the recorded session:

1. Complete the approved consent procedure and assign a participant code.
2. Record only the approved qualification/experience categories.
3. Allow standardized control familiarization without an intruder.
4. Test the task audio outside a trial, without revealing the intruder schedule.
5. Load the common saved X-Plane situation again.
6. Perform Tobii calibration for that participant and confirm initial valid gaze.
7. Confirm FlyWithLua v39, `5760x1080`, FOV `122 deg`, controller neutral state, and audio volume.

During the recorded session:

1. Start the Java receiver.
2. Start the Tobii logger and confirm tracker discovery plus valid initial samples.
3. Return focus to X-Plane and begin the automatic six-trial session once.
4. Do not provide additional timing or intruder cues.
5. After `SESSION COMPLETE`, stop Tobii and Java with `Ctrl+C`.

If X-Plane crashes, the controller malfunctions, audio is missing, or the logger/receiver stops,
mark the session as aborted. Reload the common saved situation and repeat the full session; do not
splice partial trials from two runs into one session.

## 5. Collection commands

Use a new code for every participant. The examples below use `P001`.

### PowerShell 1: Java receiver

```powershell
Set-Location -LiteralPath "$env:USERPROFILE\Desktop\AISimulationProject"

$participant = "P001"
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$base = "logs\xplane\xplane_main_${participant}_$stamp"
$session = "session_main_${participant}_$stamp"

java -cp "build_atc_tmp" "com.example.ai.XPlaneReceiverMain" `
  9100 `
  "$base.csv" `
  "$session"
```

Normal output ends with `Waiting for X-Plane state on UDP port 9100`. The command then continues
running until `Ctrl+C`.

### PowerShell 2: Tobii logger

```powershell
Set-Location -LiteralPath "$env:USERPROFILE\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1\64"

$participant = "P001"

py -3.10 "$env:USERPROFILE\Desktop\AISimulationProject\tools\tobii\session_xplane_tobii_logger.py" `
  --participant-code "$participant" `
  --duration-s 900
```

The output is saved under `AISimulationProject\logs\tobii` with the participant code and a new
timestamp. Stop it with `Ctrl+C` after `SESSION COMPLETE`.

## 6. Per-session post-processing

Return to the project folder. Use the same `$participant`, `$stamp`, and `$base` values from the
Java receiver window, and select the gaze file created for that participant's run.

```powershell
Set-Location -LiteralPath "$env:USERPROFILE\Desktop\AISimulationProject"

$participant = "P001"
$stamp = "YYYYMMDD_HHMMSS"
$base = "logs\xplane\xplane_main_${participant}_$stamp"
$state = "$base.csv"
$intruder = "${base}_intruder.csv"
$events = "${base}_events.csv"
$gaze = Get-ChildItem -LiteralPath "logs\tobii" `
  -Filter "session_xplane_tobii_${participant}_*_gaze.csv" | `
  Sort-Object LastWriteTime -Descending | `
  Select-Object -First 1 -ExpandProperty FullName
$analysis = "build_atc_tmp\xplane_main_${participant}_$stamp"
```

Merge Tobii gaze with the X-Plane trial timeline:

```powershell
py -3.10 "tools\tobii\analyze_xplane_tobii_session.py" `
  --events "$events" `
  --gaze "$gaze" `
  --aoi "resources\cessna_instrument_aoi_260710.csv" `
  --output-prefix "$analysis" `
  --layout-mode "surround-wide" `
  --screen-width 5760 `
  --screen-height 1080
```

Add the grid, dynamic intruder AOI, and projected runway AOI context:

```powershell
py -3.10 "tools\tobii\classify_xplane_external_gaze.py" `
  --state "$state" `
  --intruder "$intruder" `
  --events "$events" `
  --gaze-aoi "${analysis}_gaze_aoi.csv" `
  --runway-config "resources\rksi_runway34_geometry_260819.csv" `
  --runway-id "34" `
  --output-prefix "$analysis"
```

Build that participant's transition matrices:

```powershell
py -3.10 "tools\tobii\analyze_aoi_transition_matrix.py" `
  --context-gaze "${analysis}_external_context_gaze.csv" `
  --events "$events" `
  --participant-code "$participant" `
  --session-label "session_main_${participant}_$stamp" `
  --output-prefix "${analysis}_aoi_transition"
```

Do not choose the newest gaze file automatically if another participant or Tobii test was run after
the matching X-Plane session. In that case, set `$gaze` to the exact file and verify timestamp overlap.

## 7. Session acceptance check

Before adding a session to the group manifest, verify:

- STATE, INTRUDER, EVENT, and Tobii gaze files all exist and overlap in PC time
- one session contains six completed trials
- `LEFT`, `RIGHT`, and `FRONT` each occur twice
- every trial has `TRIAL_START`, `INTRUDER_SPAWNED`,
  `INTRUDER_VISUAL_OPPORTUNITY_ONSET`, and `TRIAL_END`
- no positive light re-enable count and no participant-visible intruder light
- no X-Plane, FlyWithLua, controller, Java receiver, or Tobii logger failure
- gaze validity and exclusion reasons are preserved rather than silently deleting a participant

The exact participant/session exclusion threshold must be frozen in the IRB-approved protocol or
analysis plan before the main dataset is inspected. With only laboratory members and one session
per person, results remain descriptive and exploratory; they are not adequate for a population-level
claim about pilots.
