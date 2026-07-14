# Tobii X-Plane PC File Placement 260710

## Current PC role split

### X-Plane PC

Runs:

- X-Plane 11
- FlyWithLua
- Tobii Pro Spark
- Tobii SDK Python logger

The X-Plane PC records the Tobii gaze CSV.

### Java PC

Runs:

- IntelliJ / Java `XPlaneReceiverMain`
- X-Plane UDP receiver
- X-Plane CSV analysis
- Tobii/X-Plane merged analysis after the gaze CSV is copied from the X-Plane PC

## File that must be copied to the X-Plane PC

Copy this Java PC file:

```text
tools/tobii/session_xplane_tobii_logger.py
```

To this X-Plane PC folder:

```text
C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1\64
```

Final X-Plane PC path:

```text
C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1\64\session_xplane_tobii_logger.py
```

Run on the X-Plane PC:

```powershell
cd "C:\Users\ACSL-SERVER\Desktop\김석호\TobiiPro.SDK.Python.Windows_2.1.0.1\64"
py -3.10 session_xplane_tobii_logger.py
```

Default output on X-Plane PC:

```text
session_xplane_tobii_YYYYMMDD_HHMMSS_gaze.csv
```

The timestamp is generated automatically when the logger starts. You do not need to edit the file name for each run.

After a trial, copy that gaze CSV back to the Java PC:

```text
build_atc_tmp\
```

## Files that stay on the Java PC

These files are for analysis after the gaze CSV has been copied back to the Java PC:

```text
tools/tobii/analyze_xplane_tobii_session.py
resources/cessna_instrument_aoi_260710.csv
tools/tobii/draw_aoi_overlay.ps1
```

Current AOI rule:

```text
AIRSPEED, ATTITUDE, ALTITUDE, HEADING, VERTICAL_SPEED, NAV_GPS
```

are the only instrument AOIs.

Every valid gaze point outside those six boxes is classified as:

```text
OUTSIDE
```

`RUNWAY_ZONE`, `ADVISORY`, and `OUTSIDE_CENTER` are not used as separate AOIs anymore.

## Java PC analysis command

After copying the gaze CSV from X-Plane PC to `build_atc_tmp`, run on the Java PC. Replace the gaze file name with the actual timestamped file created on the X-Plane PC:

```powershell
py tools\tobii\analyze_xplane_tobii_session.py `
  --events logs\xplane\xplane_session_YYYYMMDD_HHMMSS_events.csv `
  --gaze build_atc_tmp\session_xplane_tobii_YYYYMMDD_HHMMSS_gaze.csv `
  --output-prefix build_atc_tmp\xplane_tobii_analysis_YYYYMMDD_HHMMSS
```

Expected outputs:

```text
build_atc_tmp\xplane_tobii_test_260710_instrument_policy_gaze_aoi.csv
build_atc_tmp\xplane_tobii_test_260710_instrument_policy_trial_gaze_summary.csv
```

## Java receiver automatic file names

For Java-only Tobii tests, clear IntelliJ `Program arguments`, or set only:

```text
9100
```

Do not set a fixed CSV path such as:

```text
build_atc_tmp\xplane_tobii_test_260710.csv
```

If no fixed CSV path is supplied, Java creates a fresh timestamped session under:

```text
logs\xplane\
```

Example:

```text
logs\xplane\xplane_session_20260710_153000.csv
logs\xplane\xplane_session_20260710_153000_intruder.csv
logs\xplane\xplane_session_20260710_153000_events.csv
```
