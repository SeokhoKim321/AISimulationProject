# Tobii Gaze Visualization 260716

## Purpose

This document describes how to visualize Tobii gaze CSV data as screen-space gaze points and trial-level trajectories.

Current script:

```text
tools/tobii/visualize_tobii_gaze.py
```

The script requires no external plotting package. It writes SVG files that can be opened in a web browser.

For current `audio_task_v33` trials, the default event anchor is
`INTRUDER_VISUAL_OPPORTUNITY_ONSET`. This is a light-assisted operational
opportunity proxy, not a direct measurement of conscious intruder detection.
Historical figures can select `--event INTRUDER_VISUALLY_DETECTABLE`,
`--event ADVISORY_SHOWN`, or `--event INTRUDER_SPAWNED` explicitly.

## Inputs

Required:

```text
--gaze
```

Tobii gaze CSV created by `session_xplane_tobii_logger.py`.

Optional but recommended:

```text
--events
```

X-Plane event CSV created by `XPlaneReceiverMain`.

AOI file:

```text
resources/cessna_instrument_aoi_260710.csv
```

Current default background image:

```text
AOI그림.png
```

This image is the current full-screen X-Plane cockpit view captured on 2026-07-16. It supersedes `resources/cessnacokpit.png` for new gaze visualizations.

## Current AOI Rule

Only the following six instrument AOIs are treated as instruments:

```text
AIRSPEED
ATTITUDE
ALTITUDE
HEADING
VERTICAL_SPEED
NAV_GPS
```

Any valid gaze point outside those six boxes is classified as:

```text
OUTSIDE
```

Samples without a valid coordinate on the tracked front display are classified as:

```text
UNTRACKED_OR_OFF_DISPLAY
```

## Example Command

```powershell
py tools\tobii\visualize_tobii_gaze.py `
  --gaze build_atc_tmp\session_xplane_tobii_test_260710_gaze.csv `
  --events build_atc_tmp\xplane_tobii_test_260710_events.csv `
  --aoi resources\cessna_instrument_aoi_260710.csv `
  --background "AOI그림.png" `
  --output-prefix build_atc_tmp\xplane_tobii_test_260710_visual `
  --trial 8
```

## Generated Files

The first successful test generated:

```text
build_atc_tmp\xplane_tobii_test_260710_visual_gaze_scatter.svg
build_atc_tmp\xplane_tobii_test_260710_visual_trial_8_advisory_shown_trajectory.svg
build_atc_tmp\xplane_tobii_test_260710_visual_trial_8_aoi_timeline.svg
```

## Output Meaning

### Gaze Scatter

```text
*_gaze_scatter.svg
```

Shows all valid gaze samples as points over the cockpit background. Color changes from blue to red as time progresses.

### Trial Trajectory

```text
*_trial_<trial_id>_advisory_shown_trajectory.svg
```

Shows gaze movement around `ADVISORY_SHOWN`. The default window is:

```text
-2 seconds to +5 seconds
```

The gaze samples are connected in time order.

### AOI Timeline

```text
*_trial_<trial_id>_aoi_timeline.svg
```

Shows which AOI the gaze was in over time during one trial. Event markers such as `INTRUDER_SPAWNED`, `ADVISORY_SHOWN`, and `PILOT_RESPONSE_START` are drawn on the timeline.

## Interpretation Note

These plots visualize gaze points and AOI transitions. They do not by themselves prove that the pilot visually acquired the intruder. For reporting, use them together with:

```text
valid_gaze_rate
AOI dwell time
advisory_to_first_OUTSIDE_entry_s
advisory_to_response_s
```

## 2026-07-16 Current Cockpit View Verification

- `AOI그림.png` is `1918x1073` and closely matches the Tobii `1920x1080` full-screen coordinate system.
- Its cockpit viewpoint and instrument centers match the previous visualization background closely.
- The six current AOI rectangles were overlaid on the new image and correctly cover their intended instruments.
- Generated verification overlay: `build_atc_tmp/AOI그림_current_aoi_overlay.png`.
- New visualizations use `AOI그림.png` by default. The old `resources/cessnacokpit.png` remains only as a historical reference.

## 2026-07-16 Non-Instrument Gaze Split

The previous single `OUTSIDE` fallback is superseded by two categories:

```text
OUTSIDE_VIEW: valid gaze above y=600 px
PANEL_OTHER: valid gaze at or below y=600 px that is outside the six instrument AOIs
```

The six instrument AOIs are evaluated first. For example, a point in the 20 px gap between ATTITUDE and HEADING is classified as `PANEL_OTHER`, not forced into either instrument.

The threshold can be adjusted with:

```powershell
--panel-top-y 600
```

Reanalysis of the 2026-07-16 same-PC session produced:

```text
OUTSIDE_VIEW: 8457
PANEL_OTHER: 2231
ATTITUDE-HEADING gap: 70, all PANEL_OTHER
```

## 2026-07-16 Fixed Event-Relative Time Panels

The visualizer now generates an additional SVG for each selected trial:

```text
*_trial_<trial_id>_<event>_time_bins.svg
```

For the default `ADVISORY_SHOWN` event, the six panels are:

```text
-2s to -1s
-1s to  0s
 0s to +1s
+1s to +2s
+2s to +3s
+3s to +5s
```

Each panel shows only valid gaze samples in its labeled interval. All points use the same magenta color, so time is communicated by explicit panels rather than a blue-to-red gradient. Each panel reports both valid and plotted sample counts. An empty panel therefore means there was no valid gaze in that interval.

First verified output:

```text
build_atc_tmp/xplane_tobii_20260716_161359_time_bins_trial_1_advisory_shown_time_bins.svg
```

## 2026-07-16 Three-Monitor Interpretation Rule

- The experiment uses three monitors, but Tobii Pro Spark tracks only the front monitor.
- `INVALID` is replaced by `UNTRACKED_OR_OFF_DISPLAY` in new analysis outputs.
- This category means that no valid gaze coordinate was observed on the tracked front display.
- It may represent a gaze toward a side monitor, head/eye tracking loss, occlusion, blink, or an out-of-range position. The current gaze CSV cannot distinguish these causes.
- An empty time-bin panel must be described as "no valid gaze observed on the tracked front display," not automatically as tracker failure.
- Fixed time-bin labels report `front valid/total` and `untracked/off-display` counts so empty panels are not mistaken for missing raw data.

Final reanalysis of the verified session:

```text
total gaze rows: 16928
front-display valid rows: 12414 (73.3%)
UNTRACKED_OR_OFF_DISPLAY: 4514 (26.7%)
```

## 2026-07-16 Hybrid Event-Relative Bins

The earlier six-panel layout was first refined into seven mixed-width bins and is now finalized as eight bins:

```text
-2.0 to -1.0 s
-1.0 to  0.0 s
 0.0 to +0.5 s
+0.5 to +1.0 s
+1.0 to +2.0 s
+2.0 to +3.0 s
+3.0 to +4.0 s
+4.0 to +5.0 s
```

The first second after `ADVISORY_SHOWN` is split into 0.5 s panels because observed pilot response latencies are often within that interval. Longer pre-event and recovery periods retain wider bins to limit figure complexity.

Verified representative output:

```text
build_atc_tmp/xplane_tobii_20260716_161359_hybrid_bins_trial_4_advisory_shown_time_bins.svg
```

Current layout refinement:

- The final `+3~+5 s` bin is split into `+3~+4 s` and `+4~+5 s`.
- The figure now contains eight panels in a complete `4 x 2` layout.

## 2026-07-16 200 ms Gaze Centroid Movement

The eight-panel figure now summarizes raw gaze samples as `200 ms binned gaze centroids`:

- Each circle is the mean screen coordinate of valid gaze samples in one 200 ms window.
- Circle color changes discretely with time within that panel.
- The label beside each circle is the event-relative midpoint time, such as `+0.1s` or `+0.3s`.
- Consecutive non-empty 200 ms windows are connected with directional arrows.
- Missing 200 ms windows are not interpolated, and arrows do not bridge non-consecutive windows.
- Circle size reflects the number of valid samples contributing to that centroid.

These centroids are not fixation detections. Use the term `200 ms binned gaze centroid`, not `fixation`.

Verified trial 4 output contains 36 centroids and 28 movement arrows:

```text
build_atc_tmp/xplane_tobii_20260716_161359_centroids_trial_4_advisory_shown_time_bins.svg
```

## 2026-07-20 AOI Timeline Layout

The AOI timeline no longer prints rotated event names directly under densely clustered markers.

- The timeline uses compact numbered event markers.
- Markers occurring at the same or nearby time are staggered across lanes.
- A separate two-column list shows `number`, `TRIAL_START-relative time`, and `event name`.
- AOI colors are shown in a separate three-column legend.
- Relative-time axis ticks are displayed below the AOI bar.
- The SVG height grows from its content; the verified trial 4 timeline is `1400x592`.

Updated file:

```text
build_atc_tmp/xplane_tobii_20260716_161359_centroids_trial_4_aoi_timeline.svg
```

## 2026-07-20 Operational External and Trial Aggregation

The raw categories remain unchanged, but analysis adds:

```text
OPERATIONAL_EXTERNAL = OUTSIDE_VIEW + UNTRACKED_OR_OFF_DISPLAY
```

This is an explicit experiment-policy assumption because the left and right monitors represent the outside environment. Raw `aoi` remains available, and the derived value is stored separately as `operational_aoi`.

The analyzer now writes:

```text
*_trial_aggregate_summary.csv
```

Aggregation rules:

- Include strict clean trials only.
- Give every trial equal weight; do not pool all gaze samples across trials.
- Produce `all`, `front`, `left`, and `right` groups.
- Report `n`, mean, median, standard deviation, minimum, and maximum.
- Event-relative gaze windows are included only when the complete window lies inside that trial.

Verified clean-trial results (`n=9`):

```text
mean advisory_to_response_s: 0.903
mean trial_operational_external_rate: 0.726
operational external before advisory: 8/9 trials (88.9%)
```

Because most trials were already operationally external before advisory, post-advisory first-entry latency is not a strong primary metric for this session.

## 2026-08-04 v33 anchor update

`analyze_xplane_tobii_session.py` version
`260804_visual_opportunity_v9` adds these current-protocol fields:

- visual-opportunity event presence and geometry/light metadata
- `spawn_to_visual_opportunity_s`
- `visual_opportunity_to_response_s`
- `visual_opportunity_to_first_operational_external_s`
- event-relative operational-external rate windows
- first AOI gaze, 200 ms dwell, and AOI entry after visual opportunity
- AOI/external state immediately before visual opportunity

For v33 clean trials, exactly one opportunity event must occur in
`INTRUDER_SPAWNED -> INTRUDER_VISUAL_OPPORTUNITY_ONSET -> TRIAL_END` order.
The later 20 px detectability event remains available for diagnostic plots but
is not required.
