# Tobii Gaze Visualization 260716

## Purpose

This document describes how to visualize Tobii gaze CSV data as screen-space gaze points and trial-level trajectories.

Current script:

```text
tools/tobii/visualize_tobii_gaze.py
```

The script requires no external plotting package. It writes SVG files that can be opened in a web browser.

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

Background image:

```text
resources/cessnacokpit.png
```

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

Invalid or off-screen samples are classified as:

```text
INVALID
```

## Example Command

```powershell
py tools\tobii\visualize_tobii_gaze.py `
  --gaze build_atc_tmp\session_xplane_tobii_test_260710_gaze.csv `
  --events build_atc_tmp\xplane_tobii_test_260710_events.csv `
  --aoi resources\cessna_instrument_aoi_260710.csv `
  --background resources\cessnacokpit.png `
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
