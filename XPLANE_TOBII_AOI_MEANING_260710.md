# X-Plane Tobii AOI Meaning 260710

## Purpose

This note defines what the current Tobii AOI labels mean in the X-Plane Cessna cockpit view.

The current AOI file is:

```text
resources/cessna_instrument_aoi_260710.csv
```

The current overlay check image is:

```text
build_atc_tmp/cessna_instrument_aoi_overlay_260710.png
```

## AOI label meaning

| AOI | Meaning | Current interpretation |
|---|---|---|
| `AIRSPEED` | Airspeed indicator | Speed monitoring AOI. |
| `ATTITUDE` | Attitude indicator / artificial horizon | Pitch/roll attitude monitoring AOI. |
| `ALTITUDE` | Altimeter | Altitude monitoring AOI. |
| `VERTICAL_SPEED` | Vertical speed indicator | Climb/descent rate monitoring AOI. |
| `HEADING` | Heading indicator / directional gyro | Heading/course monitoring AOI. |
| `NAV_GPS` | GPS/navigation display | Navigation display AOI. |
| `OUTSIDE` | Any valid gaze outside the six instrument AOIs above | Outside/cockpit-other gaze. This includes windshield view, runway area, advisory text, panel background, and any non-instrument region. |
| `INVALID` | Invalid gaze sample | Tobii did not provide a valid averaged gaze point. |

## Important interpretation rules

1. Only six fixed instrument AOIs are treated as instruments.
2. Any valid gaze outside those six AOI boxes is classified as `OUTSIDE`.
3. `RUNWAY_ZONE`, `ADVISORY`, and `OUTSIDE_CENTER` are no longer used as separate AOIs.
4. Single-sample first gaze hits are weaker evidence than dwell-based hits.
5. The current analysis script reports 200 ms dwell timing and post-event AOI entry timing.
6. If the pilot was already looking at an AOI before `ADVISORY_SHOWN`, that trial should not be described as a post-advisory gaze shift to that AOI.

## Current first merged analysis finding

In `xplane_tobii_test_260710`, several trials were already in `OUTSIDE` immediately before advisory. Therefore, the more defensible gaze metric is:

```text
advisory_to_first_OUTSIDE_entry_s
```

not just:

```text
advisory_to_first_OUTSIDE_gaze_s
```

The current six instrument AOIs were visually checked against the cockpit screenshot and accepted as the fixed instrument AOIs.
