import math

import analyze_aoi_transition_matrix as matrix


def event(timestamp_ms, event_type):
    return {
        "recorded_timestamp_ms": str(timestamp_ms),
        "trial_id": "1",
        "event_type": event_type,
    }


def gaze(timestamp_ms, state=None, semantic=None, valid=True):
    external = semantic is not None
    return {
        "pc_time_ms": str(timestamp_ms),
        "trial_id": "1",
        "valid_gaze": "1" if valid else "0",
        "operational_external": "1" if external else "0",
        "operational_aoi": "OPERATIONAL_EXTERNAL" if external else (state or "UNRESOLVED_TRACKING_LOSS"),
        "semantic_class": semantic or "NOT_EXTERNAL",
    }


def add_bin(rows, start_ms, **kwargs):
    rows.extend(gaze(start_ms + offset, **kwargs) for offset in (20, 60, 100, 140, 180))


def probability(rows, phase, source, destination):
    match = next(
        row
        for row in rows
        if row["scope"] == "PARTICIPANT_EQUAL_MEAN"
        and row["phase"] == phase
        and row["source_aoi"] == source
    )
    value = match[destination]
    return math.nan if value == "" else float(value)


def main():
    gaze_rows = []
    add_bin(gaze_rows, 1000, state="AIRSPEED")
    add_bin(gaze_rows, 1200, state="AIRSPEED")
    add_bin(gaze_rows, 1400, state="ATTITUDE")
    add_bin(gaze_rows, 1600, state="ATTITUDE")
    add_bin(gaze_rows, 1800, semantic="INTRUDER_SEARCH_OR_TRACKING")
    add_bin(gaze_rows, 2000, valid=False)
    add_bin(gaze_rows, 2200, semantic="RUNWAY_GUIDANCE")
    add_bin(gaze_rows, 2400, semantic="RUNWAY_GUIDANCE")
    add_bin(gaze_rows, 2600, semantic="OTHER_EXTERNAL")

    sessions = [
        {
            "participant_code": "P001",
            "session_label": "synthetic",
            "gaze_rows": gaze_rows,
            "event_rows": [
                event(1000, "TRIAL_START"),
                event(1800, "INTRUDER_SPAWNED"),
                event(2200, "INTRUDER_VISUAL_OPPORTUNITY_ONSET"),
                event(3000, "TRIAL_END"),
            ],
        }
    ]

    binned = matrix.build_binned_states(sessions, bin_ms=200.0)
    observations = matrix.build_transition_observations(binned)
    episodes = matrix.build_dwell_episodes(binned, 200.0)
    dtmc_rows, _ = matrix.aggregate_all_scopes(observations, "DTMC")
    switch_rows, _ = matrix.aggregate_all_scopes(observations, "SWITCH")

    if not math.isclose(probability(dtmc_rows, "FULL_TRIAL", "AIRSPEED", "AIRSPEED"), 0.5):
        raise AssertionError("AIRSPEED self-transition probability must be 0.5")
    if not math.isclose(probability(dtmc_rows, "FULL_TRIAL", "AIRSPEED", "ATTITUDE"), 0.5):
        raise AssertionError("AIRSPEED-to-ATTITUDE probability must be 0.5")
    if not math.isclose(probability(switch_rows, "FULL_TRIAL", "AIRSPEED", "ATTITUDE"), 1.0):
        raise AssertionError("next-switch AIRSPEED-to-ATTITUDE probability must be 1.0")
    if any(
        row["source_aoi"] == "INTRUDER_AOI" and row["destination_aoi"] == "RUNWAY_AOI"
        for row in observations
    ):
        raise AssertionError("an unresolved bin must break the transition chain")
    if not any(
        row["phase"] == "BEFORE_SPAWN"
        and row["source_aoi"] == "ATTITUDE"
        and row["destination_aoi"] == "ATTITUDE"
        for row in observations
    ):
        raise AssertionError("before-spawn phase must preserve its own self-transition")
    if not any(
        episode["phase"] == "FULL_TRIAL"
        and episode["attention_state"] == "AIRSPEED"
        and math.isclose(episode["duration_s"], 0.4)
        for episode in episodes
    ):
        raise AssertionError("two adjacent 200 ms bins must form a 0.4 s dwell episode")

    print("analyze_aoi_transition_matrix smoke test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
