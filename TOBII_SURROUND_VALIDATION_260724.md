# Tobii Pro Spark 3-monitor NVIDIA Surround validation

Date: 2026-07-24
Tool: `tools/tobii/multimonitor_gaze_validation.py`
Tool version: `260724_v2`, schema version `2`

## 1. Purpose and current status

This is Stage 2 of the APISAT experiment redesign. Its purpose is to determine
whether one Tobii Pro Spark can provide usable gaze data across three flat,
co-planar monitors after NVIDIA Surround exposes them as one logical display.

Current status as of 2026-07-27 is:

```text
PRECISE WIDE AOI no-go / COARSE SIDE_EXTERNAL conditional go / X-Plane pilot pending
```

The validation tool, its self-test, the current-layout dry run, and the
research-valid layout guard have been checked. A research-valid center baseline
was collected on 2026-07-27. Two physical Surround-wide runs were also
completed. Their strict automated status is `INCONCLUSIVE`, but the replicated
side-monitor dropouts support an engineering/operational no-go decision for
whole-surface gaze measurement in this tested setup. The later coarse
central-versus-side operational endpoint remains a separate conditional-go
question.

The current pre-Surround setup is:

- Windows extended desktop with three independent `1920x1080` displays
- left `DISPLAY1`: `x=-1920..0`
- center primary `DISPLAY2`: `x=0..1920`
- right `DISPLAY3`: `x=1920..3840`
- one Tobii Pro Spark, serial `TPE01-100206101311`, running at `60 Hz`
- tracker Active Display Area: `598 x 336 mm`, approximately one 27-inch 16:9
  center display

Completed center baseline:

- session: `tobii_multimonitor_center_baseline_20260727_154315`
- viewing distance: `750 mm`
- status: `RESEARCH_VALID_COMPLETE`
- epochs: `15/15`; condition median sample coverage: `100.0%`
- center valid gaze: `96.5%`; binocular valid gaze: `95.6%`
- correct center-monitor classification among valid samples: `99.9%`
- median target-centroid error: `36.1 px`
- target median centroid-error range: `22.5~83.1 px`
- equal-target radial sample `p95`: `75.3 px`

This baseline is adequate for the matched center-degradation comparison.

On 2026-07-27, NVIDIA Surround was enabled and Windows was verified to expose
one primary `5760x1080` display at `(0,0)`. After correcting an intermediate
tracker-position error, the wide Active Display Area was `1794 x 336 mm`, its
UCS display-plane depth was approximately `z=-60 mm`, and calibration data was
stored. Both physical runs used `750 mm` viewing distance, flat/co-planar
panels, and `0 mm` bezel correction.

Physical results:

- first session: `tobii_multimonitor_surround_wide_20260727_155743`
- retry session: `tobii_multimonitor_surround_wide_20260727_160333`
- both completed all `45/45` planned epochs
- first left/center/right valid rates: `44.4% / 93.5% / 42.1%`
- retry left/center/right valid rates: `37.5% / 96.3% / 40.1%`
- multiple outer side targets had `0%` valid gaze across all three repeats in
  both runs
- finite side median target-centroid errors were approximately `170~371 px`
  in the first run and `297~346 px` in the retry

The automatic comparison result remains `INCONCLUSIVE`, not `FAIL`. One or two
`LEFT_LL` epochs per run fell below the `80%` stream-coverage integrity gate,
and those epochs also contained zero valid gaze. The repeated side-validity
failure is nevertheless strong enough for a project engineering/operational
no-go: one center-mounted Spark is not reliable for whole-surface measurement
across this flat three-monitor geometry. This is a setup-specific result, not a
universal claim about every possible Spark wide-display setup.

For the narrower experiment endpoint, the project now distinguishes precise
side AOI feasibility from coarse side-external detection. The latter uses:

- valid side gaze beyond a `120 px` seam guard for at least `200 ms`, or
- bilateral invalidity lasting `500~5000 ms`, bracketed by valid samples, with
  no stream gap above `100 ms`.

The implementation is `tools/tobii/analyze_xplane_tobii_session.py` version
`260727_surround_external_v1`; the reproducible labeled-target checker is
`tools/tobii/validate_surround_external_classifier.py`.

The two physical sessions supplied `90` labeled epochs. Post-hoc application
of the fixed rule detected `59/60` side epochs and rejected `30/30` center
epochs. Direction was available and correct for `24/24` measured-direction
epochs. These values were obtained from the same single-participant data used
to select the rule and are not independent accuracy estimates.

Analyze a new X-Plane/Surround session with:

```powershell
py -3.10 tools\tobii\analyze_xplane_tobii_session.py `
  --events "logs\xplane\<session>_events.csv" `
  --gaze "logs\tobii\<session>_gaze.csv" `
  --output-prefix "build_atc_tmp\<session>_surround_external" `
  --layout-mode surround-wide
```

The wide defaults are `5760x1080`, monitor width `1920 px`, center index `1`,
seam guard `120 px`, measured dwell `200 ms`, inferred-loss range
`500~5000 ms`, and maximum sample gap `100 ms`. The analysis additionally
writes `*_external_episodes.csv`.

Reproduce the labeled-target check with:

```powershell
py -3.10 tools\tobii\validate_surround_external_classifier.py `
  --gaze "logs\tobii\multimonitor_validation\tobii_multimonitor_surround_wide_20260727_155743_gaze_annotated.csv" `
  --gaze "logs\tobii\multimonitor_validation\tobii_multimonitor_surround_wide_20260727_160333_gaze_annotated.csv" `
  --output-prefix "logs\tobii\multimonitor_validation\surround_coarse_external_validation_20260727"
```

NVIDIA Surround only changes the Windows logical coordinate space. It does not
by itself expand the tracker's physical gaze-angle or head-position coverage.
Tobii lists Spark's optimal 16:9 screen size as up to 27 inches and operating
distance as 45–95 cm. Tobii's advanced-setup guidance also describes a maximum
gaze angle of 35 degrees and says that wide or multi-display success depends on
geometry. These are reasons to measure feasibility, not grounds to assume
success or failure.

Official references:

- [Tobii Pro Spark specifications](https://www.tobii.com/products/eye-trackers/screen-based/tobii-pro-spark)
- [Tobii advanced screen-based setup guidance](https://www.tobii.com/resource-center/learn-articles/how-to-use-screen-based-eye-trackers)
- [NVIDIA Surround configuration](https://www.nvidia.com/content/Control-Panel-Help/vLatest/en-us/mergedProjects/3D%20Settings/NVIDIA_Surround_Configuration_.htm)
- [Tobii Pro display-area coordinates](https://developer.tobiipro.com/commonconcepts/coordinatesystems.html)

## 2. What the tool does and does not do

The tool:

- displays known targets;
- subscribes directly to the selected Spark by serial number;
- records raw, unclamped left/right gaze and gaze-origin samples;
- maps normalized gaze into the condition's active logical display;
- calculates epoch, target, and monitor summaries;
- produces a visual SVG report;
- compares a completed center baseline with a completed Surround run.

The tool does not:

- enable or disable NVIDIA Surround;
- configure the Tobii Active Display Area;
- perform Tobii Eye Tracker Manager calibration;
- resume an interrupted session;
- prove fine-grained runway or intruder AOI accuracy.

## 3. Experimental controls

Keep these conditions the same in both runs:

- same participant and normal flight posture;
- same chair, seat height, torso location, and Spark mount;
- monitors flat and co-planar;
- same monitor brightness, room lighting, and eyewear;
- same physical eye-to-center-screen distance;
- 100% Windows scaling;
- no X-Plane, ordinary Tobii logger, or other gaze subscriber running.

Measure and record:

- one panel's visible width and height in millimetres;
- eye-to-center-screen distance in millimetres;
- the physical gap represented by each bezel in millimetres.

The current tracker display-area measurement is `598 x 336 mm`. Use those values
only if they match the actual panels.

## 4. Target protocol

`CENTER_BASELINE` uses five center-monitor targets repeated in three blocks:

```text
5 targets x 3 repeats = 15 epochs
```

`SURROUND_WIDE` uses five targets on each physical monitor, repeated in three
blocks:

```text
3 monitors x 5 targets x 3 repeats = 45 epochs
```

Each monitor uses:

```text
UL=(20%,20%)  UR=(80%,20%)  C=(50%,50%)
LL=(20%,80%)  LR=(80%,80%)
```

Default epoch timing:

```text
home center marker 1.50 s
blank              0.25 s
target             2.50 s
  settle excluded  1.00 s
  measured         1.50 s
blank              0.25 s
```

Look at the small white center of each target. Natural eye/head rotation is
allowed, but do not move the chair or torso. Press `Space` to start each block.
At a block break, rest without changing the seat position and press `Space`
again. Use `Esc` or close the validation window to abort safely.

Do not use `Ctrl+C` as the normal abort method because it does not guarantee the
partial-output save path.

## 5. Step A — center-only baseline

Before changing NVIDIA or Tobii display setup:

1. Confirm that Windows still reports three independent displays.
2. In Tobii Eye Tracker Manager, retain or repeat the normal center-monitor
   display setup and calibration.
3. Close X-Plane and `session_xplane_tobii_logger.py`.
4. Measure the geometry and set the PowerShell variables below.

```powershell
cd "C:\Users\ACSL-SERVER\Desktop\AISimulationProject"

$panelWidthMm = 598
$panelHeightMm = 336
$viewDistanceMm = 700
$participantCode = "PILOT_SELF_01"
$setupId = "SPARK_FLAT3_01"

py -3.10 tools\tobii\multimonitor_gaze_validation.py self-test

py -3.10 tools\tobii\multimonitor_gaze_validation.py collect `
  --condition CENTER_BASELINE `
  --monitor-width-mm $panelWidthMm `
  --monitor-height-mm $panelHeightMm `
  --view-distance-mm $viewDistanceMm `
  --flat-coplanar yes `
  --participant-code $participantCode `
  --setup-id $setupId `
  --calibration-id "CENTER_ETM_01" `
  --run-label center_before_surround `
  --calibration-note "ETM center-display calibration"
```

`700` above is only a placeholder example. Replace it with the measured
eye-to-screen distance before collecting research data.

The baseline must be a new target-based run. Do not substitute an older X-Plane
gaze-validity percentage because it did not use the same five physical targets.

## 6. Step B — enable Surround and recalibrate

After the center baseline is saved:

1. Open NVIDIA Control Panel.
2. Go to `Configure Surround, PhysX`.
3. Enable `Span displays with Surround` and select `Configure`.
4. Select a `1 x 3` topology and arrange left, center, right in physical order.
5. For the first test, use the common native resolution `5760x1080`, a common
   refresh rate, 100% scaling, and no bezel correction.
6. Apply and confirm that Windows exposes exactly one logical `5760x1080`
   display.
7. Open Tobii Eye Tracker Manager.
8. Configure the entire wide logical display as the Active Display Area and
   complete a new whole-display calibration.

If Tobii Eye Tracker Manager cannot define or calibrate the whole wide area,
record that as a practical Stage 2 failure. Do not bypass it and call a
diagnostic run a research-valid result.

Check the Windows layout without starting the tracker GUI:

```powershell
py -3.10 tools\tobii\multimonitor_gaze_validation.py collect `
  --condition SURROUND_WIDE `
  --dry-run
```

This command must report one Windows display and 45 epochs. The tool refuses a
normal `SURROUND_WIDE` run when Windows still exposes multiple displays.

## 7. Step C — Surround-wide measurement

Set the actual bezel value and use the same seating geometry as the baseline:

```powershell
$bezelMm = 0

py -3.10 tools\tobii\multimonitor_gaze_validation.py collect `
  --condition SURROUND_WIDE `
  --monitor-width-mm $panelWidthMm `
  --monitor-height-mm $panelHeightMm `
  --bezel-mm $bezelMm `
  --view-distance-mm $viewDistanceMm `
  --flat-coplanar yes `
  --participant-code $participantCode `
  --setup-id $setupId `
  --calibration-id "SURROUND_ETM_01" `
  --run-label surround_wide `
  --calibration-note "ETM whole-wide-display calibration"
```

`--bezel-mm` means the total physical gap between two adjacent visible display
areas, not the width of one plastic bezel. Replace `0` with that measured
inter-panel gap if the configured Active Display Area includes it. With bezel
correction disabled, keep the first test and its geometry notes simple and
explicit.

For a normal research-valid run, never add:

```text
--allow-layout-mismatch
--allow-display-area-mismatch
```

Those options exist only for software diagnosis. The normal guard also refuses
a wide run when the tracker's Active Display Area width differs by more than
15% from approximately:

```text
3 x panel width + 2 x bezel gap
```

## 8. Outputs

Each completed or safely aborted collection normally writes nine files under:

```text
logs\tobii\multimonitor_validation
```

Session prefix:

```text
tobii_multimonitor_<condition>_YYYYMMDD_HHMMSS
```

Files:

- `_metadata.json`
- `_target_events.csv`
- `_gaze_raw.csv`
- `_gaze_annotated.csv`
- `_epoch_metrics.csv`
- `_target_summary.csv`
- `_monitor_summary.csv`
- `_summary.json`
- `_report.svg`

Raw gaze is retained without clamping or interpolation. An aborted run records
`completed=false` and `aborted=true`; it is not resumed and must not be used for
the final comparison. Raw gaze is queued to a dedicated writer and flushed
approximately once per second; target events are flushed as they occur instead
of either stream being held only until GUI shutdown.
`_metadata.json` is written last; its normal presence means that the other
declared outputs reached their final-write path.

If Windows layout or the Tobii Active Display Area fails before the GUI starts,
the tool writes only `*_preflight.json` with status
`CONFIGURATION_FAILED`. Preserve that file as evidence that the configuration
itself was not feasible.

A research-valid collection also requires:

- exactly 15 baseline epochs or 45 Surround epochs;
- exactly three repeats for every target;
- one correctly ordered event sequence per epoch:
  `HOME_ON → HOME_OFF → TARGET_ON → MEASURE_START → MEASURE_END → TARGET_OFF`;
- actual measured duration within 90–120% of the planned 1.5 s window;
- each epoch's sample coverage between 80–120%;
- condition median sample coverage between 90–110%;
- no diagnostic override, layout warning, missing epoch, or missing valid-rate
  calculation.

The SVG carries a visible status/watermark when a collection is diagnostic or
incomplete.

## 9. Comparison and temporary go/no-go rule

Use the two completed `_summary.json` files:

```powershell
py -3.10 tools\tobii\multimonitor_gaze_validation.py compare `
  --baseline-summary "logs\tobii\multimonitor_validation\<CENTER_BASELINE_summary.json>" `
  --surround-summary "logs\tobii\multimonitor_validation\<SURROUND_WIDE_summary.json>"
```

The comparison writes:

```text
*_comparison_decision.csv
*_comparison_decision.json
```

The temporary project go/no-go criteria are not Tobii specifications:

- left valid gaze rate: at least `70%`;
- right valid gaze rate: at least `70%`;
- correct physical-monitor classification among valid samples: at least `90%`
  for each side separately;
- each side's worst target median repeat-valid rate: at least `50%`;
- Surround center valid gaze rate: at least `70%`;
- baseline center valid gaze rate: at least `70%`, with a finite nonzero
  centroid-error estimate;
- Surround center median target-centroid error: no more than `20%` worse than
  the matched center baseline.

Invalid samples remain in the valid-rate denominator. Left and right are never
pooled, so one failed side cannot be hidden by the other.

The comparison result has three states:

- `PASS`: setup/data comparability and all performance thresholds pass;
- `FAIL`: comparable research-valid data were collected, but at least one
  performance threshold fails;
- `INCONCLUSIVE`: setup, protocol, stream, baseline quality, or pair matching
  is inadequate.

The tool also requires the same participant code and setup ID, the same tracker
serial/frequency, matching target
timing, the same panel geometry, eye-to-screen distance within 20 mm, matching
center pixel geometry, and schema-compatible files. Missing data therefore
cannot be mistaken for a hardware performance failure.

Exit codes are `0=PASS`, `5=FAIL`, and `6=INCONCLUSIVE` for comparison.
Physical collect returns `0` only for a research-valid complete run; safe
abort, diagnostic-only, or incomplete collection returns `4`.

`MONITOR_LEVEL_PASS` means only that coarse left/center/right use is feasible
under the measured setup. `FINE_AOI_PASS` remains unevaluated. The monitor
summary now carries a conservative per-target aggregate of epoch-level radial
sample `p95` error, dropout, binocular-validity, acquisition, and recovery
indicators. Before using side-monitor runway or dynamic intruder AOIs, compare
those side errors with the planned AOI margin in a separate test.

## 10. Interpretation and fallback

If the monitor-level test passes:

- retain the result as a hardware feasibility finding;
- perform a later fine-AOI validation before changing the paper's gaze claims;
- do not yet reinterpret older center-only CSV data as measured side gaze.

If either side fails for precise coordinates:

- do not use side coordinates as precise AOIs or intruder fixation;
- measured side coordinates may still support a coarse left/right external
  event after dwell and seam-guard checks;
- sustained loss may support only `INFERRED_SIDE_UNKNOWN`;
- do not infer precise left/right gaze from the failed setup.

Keep Surround through one short X-Plane pilot only. In wide coordinates, the
center monitor is global `x=1920..3840`; existing instrument AOIs use
`center_x = global_x - 1920`. If center AOIs or coarse side-event detection are
inadequate, restore extended desktop and repeat the ordinary center-monitor
Tobii setup/calibration. If adequate, freeze the thresholds and keep measured,
inferred, and unresolved evidence separate in every analysis.
