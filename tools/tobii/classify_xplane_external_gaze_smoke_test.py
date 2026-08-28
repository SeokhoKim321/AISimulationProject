import math

import classify_xplane_external_gaze as classifier


def state_row(timestamp_ms, sample_index, trial_id="1"):
    return {
        "received_timestamp_ms": str(timestamp_ms),
        "trial_id": trial_id,
        "sample_index": str(sample_index),
        "x": "0",
        "y": "100",
        "z": "0",
        "latitude_deg": "37.40000000",
        "longitude_deg": "126.48000000",
        "elevation_m": "106.096",
        "heading_deg": "325",
        "pitch_deg": "0",
    }


def gaze_row(timestamp_ms, x, y, external=True, evidence="CENTER_OUTSIDE_MEASURED"):
    return {
        "pc_time_ms": str(timestamp_ms),
        "gaze_px": str(x),
        "gaze_py": str(y),
        "valid_gaze": "1",
        "display_region": "CENTER_MONITOR",
        "operational_external": "1" if external else "0",
        "operational_aoi": "OPERATIONAL_EXTERNAL" if external else "AIRSPEED",
        "external_evidence": evidence,
    }


def event(timestamp_ms, event_type, detail="", trial_id="1"):
    return {
        "recorded_timestamp_ms": str(timestamp_ms),
        "trial_id": trial_id,
        "event_type": event_type,
        "detail": detail,
    }


def main():
    events = [
        event(900, "TRIAL_START"),
        event(1000, "INTRUDER_SPAWNED", "approach=front;heading_deg=145"),
        event(1200, "INTRUDER_VISUAL_OPPORTUNITY_ONSET"),
        event(1800, "PILOT_RESPONSE_START"),
        event(2200, "TRIAL_END"),
    ]
    states = [state_row(timestamp, index) for index, timestamp in enumerate(range(900, 2300, 100), 1)]
    intruders = []
    for state in states:
        intruders.append(
            {
                "trial_id": "1",
                "sample_index": state["sample_index"],
                "intruder_x": "0",
                "intruder_y": "100",
                "intruder_z": "-1000",
            }
        )
    projection = classifier.project_intruder(states[2], intruders[2], 145.0, 5760, 1080, 122.0)
    if not projection["valid"] or not projection["visible"]:
        raise AssertionError("synthetic intruder must project into the display")

    gaze = []
    for timestamp in range(900, 2201, 20):
        if 1200 <= timestamp <= 1500:
            gaze.append(gaze_row(timestamp, projection["screen_x_px"], projection["screen_y_px"]))
        elif 1600 <= timestamp <= 1900:
            gaze.append(gaze_row(timestamp, 2880, 500))
        else:
            gaze.append(gaze_row(timestamp, 2800, 250, external=False))

    runway = {
        "near_latitude_deg": "37.44343592",
        "near_longitude_deg": "126.44169978",
        "far_latitude_deg": "37.47281563",
        "far_longitude_deg": "126.41554514",
        "elevation_m": "6.096",
        "width_m": "60.05",
    }
    rows, episodes, contexts = classifier.classify_rows(
        states,
        intruders,
        events,
        gaze,
        runway,
        intruder_margin_px=20.0,
        runway_margin_px=20.0,
        dwell_ms=200.0,
    )
    intruder_rows = [row for row in rows if row["semantic_class"] == classifier.SEMANTIC_INTRUDER]
    if not intruder_rows:
        raise AssertionError("expected a confirmed intruder dynamic-AOI episode")
    if not any(row["semantic_evidence"] == "DYNAMIC_INTRUDER_AOI_OVERLAP" for row in intruder_rows):
        raise AssertionError("intruder episode must retain the dynamic-AOI evidence")
    if not any(episode["semantic_class"] == classifier.SEMANTIC_INTRUDER for episode in episodes):
        raise AssertionError("expected intruder episode output")
    if not all(row["semantic_class"] for row in rows):
        raise AssertionError("every row must receive a semantic class")

    summaries = classifier.summarize_trials(rows, episodes, contexts)
    if len(summaries) != 1 or summaries[0]["intruder_episode_count"] < 1:
        raise AssertionError("trial summary must count intruder evidence")
    if summaries[0]["intruder_dynamic_overlap_rows"] < 1:
        raise AssertionError("trial summary must preserve direct dynamic-overlap rows")
    grid_summary = classifier.summarize_grid(rows, contexts)
    if not grid_summary:
        raise AssertionError("grid summary must not be empty")

    monitor, row, column, cell = classifier.grid_cell(2500, 300, 5760, 1920, 600, 6, 3)
    if (monitor, row, column, cell) != ("CENTER", 2, 2, "CENTER_R2C2"):
        raise AssertionError("unexpected grid mapping")

    polygon = [(0, 0), (100, 0), (100, 100), (0, 100)]
    if classifier.point_polygon_distance(50, 50, polygon) != 0.0:
        raise AssertionError("inside polygon distance must be zero")
    if not math.isclose(classifier.point_polygon_distance(120, 50, polygon), 20.0):
        raise AssertionError("outside polygon distance is incorrect")

    print("classify_xplane_external_gaze smoke test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
