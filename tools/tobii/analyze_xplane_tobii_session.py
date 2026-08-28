import argparse
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path


DEFAULT_SCREEN_WIDTH = 1920
DEFAULT_SCREEN_HEIGHT = 1080
DEFAULT_PANEL_TOP_Y = 600
DEFAULT_MONITOR_WIDTH = 1920
DEFAULT_CENTER_MONITOR_INDEX = 1
DEFAULT_SIDE_GUARD_PX = 120.0
DEFAULT_SIDE_DWELL_MS = 200.0
DEFAULT_SIDE_LOSS_MIN_MS = 500.0
DEFAULT_SIDE_LOSS_MAX_MS = 5000.0
DEFAULT_MAX_SAMPLE_GAP_MS = 100.0
ANALYZER_VERSION = "260811_intruder_ai_autopilot_override_v14"
LAYOUT_CENTER = "center"
LAYOUT_SURROUND_WIDE = "surround-wide"
DEFAULT_AOI_PATH = "resources/cessna_instrument_aoi_260710.csv"
INSTRUMENT_AOIS = ["AIRSPEED", "ATTITUDE", "ALTITUDE", "HEADING", "VERTICAL_SPEED", "NAV_GPS"]
TARGET_AOIS = ["OUTSIDE_VIEW", "PANEL_OTHER"] + INSTRUMENT_AOIS
OPERATIONAL_EXTERNAL_AOIS = {"OUTSIDE_VIEW", "UNTRACKED_OR_OFF_DISPLAY"}
MEASURED_SIDE_EVIDENCE = {"MEASURED_SIDE_LEFT", "MEASURED_SIDE_RIGHT"}
INFERRED_SIDE_EVIDENCE = {"INFERRED_SIDE_UNKNOWN"}
EXTERNAL_EPISODE_FIELDS = [
    "episode_id",
    "episode_type",
    "direction",
    "start_ms",
    "end_ms",
    "duration_ms",
    "sample_count",
    "pre_display_region",
    "post_display_region",
]
CLEAN_TRIAL_EVENTS = [
    "TRIAL_RESET",
    "TRIAL_START",
    "SCENARIO_SELECTED",
    "INTRUDER_SPAWNED",
    "HAZARD_DETECTED",
    "ADVISORY_SHOWN",
    "PILOT_RESPONSE_START",
    "HAZARD_CLEARED",
    "ADVISORY_CLEARED",
    "PILOT_RESPONSE_END",
    "TRIAL_END",
]
TASK_AUDIO_EVENTS = [
    "TASK_COMMAND_AUDIO_START",
    "TASK_COMMAND_AUDIO_END",
]
SPAWN_RESPONSE_RECORDING_EVENTS = [
    "TRIAL_RESET",
    "TRIAL_START",
    "SCENARIO_SELECTED",
    "INTRUDER_SPAWNED",
    "PILOT_RESPONSE_BASELINE",
    "MIN_DISTANCE_REACHED",
    "TRIAL_END",
]
OPERATIONAL_EXTERNAL_WINDOWS = [
    ("pre_2_to_1_s", -2.0, -1.0),
    ("pre_1_to_0_s", -1.0, 0.0),
    ("post_0_to_0_5_s", 0.0, 0.5),
    ("post_0_5_to_1_s", 0.5, 1.0),
    ("post_1_to_2_s", 1.0, 2.0),
    ("post_2_to_3_s", 2.0, 3.0),
    ("post_3_to_4_s", 3.0, 4.0),
    ("post_4_to_5_s", 4.0, 5.0),
]


def parse_float(value, default=math.nan):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def parse_int(value, default=0):
    try:
        return int(float(value))
    except (TypeError, ValueError):
        return default


def audio_spawn_timing_valid(
    protocol,
    planned_audio_to_spawn_s,
    actual_audio_to_spawn_s,
    spawn_timeout_audio_to_spawn_s=math.nan,
    speed_stable_before_spawn=False,
    spawn_forced_by_timeout=False,
    speed_error_at_spawn_kias=math.nan,
    speed_rate_at_spawn_kias_s=math.nan,
    speed_rate_valid_at_spawn=False,
    speed_stability_duration_s=math.nan,
):
    audio_protocols = {
        "audio_task_v27",
        "audio_task_v28",
        "audio_task_v29",
        "audio_task_v31",
        "audio_task_v32",
        "audio_task_v33",
        "audio_task_v34",
        "audio_task_v35",
        "audio_task_v36",
        "audio_task_v37",
        "audio_task_v38",
        "audio_task_v39",
    }
    if protocol not in audio_protocols:
        return ""
    if math.isnan(planned_audio_to_spawn_s) or math.isnan(actual_audio_to_spawn_s):
        return 0
    if protocol not in {"audio_task_v35", "audio_task_v36", "audio_task_v37", "audio_task_v38", "audio_task_v39"}:
        return int(
            3.0 <= planned_audio_to_spawn_s <= 10.0
            and 3.0 <= actual_audio_to_spawn_s <= 10.1
        )

    common_timing_valid = (
        6.0 <= planned_audio_to_spawn_s <= 10.0
        and not math.isnan(spawn_timeout_audio_to_spawn_s)
        and 13.9 <= spawn_timeout_audio_to_spawn_s <= 14.1
        and planned_audio_to_spawn_s - 0.1
        <= actual_audio_to_spawn_s
        <= spawn_timeout_audio_to_spawn_s + 0.1
    )
    forced_gate_state_valid = (
        spawn_forced_by_timeout
        and not speed_stable_before_spawn
        and actual_audio_to_spawn_s >= spawn_timeout_audio_to_spawn_s - 0.1
    )
    target_band_dwell_valid = (
        not spawn_forced_by_timeout
        and speed_stable_before_spawn
        and not math.isnan(speed_error_at_spawn_kias)
        and abs(speed_error_at_spawn_kias) <= 5.05
        and not math.isnan(speed_stability_duration_s)
        and speed_stability_duration_s >= 1.49
    )
    stable_gate_state_valid = target_band_dwell_valid
    if protocol == "audio_task_v35":
        stable_gate_state_valid = (
            target_band_dwell_valid
            and speed_rate_valid_at_spawn
            and not math.isnan(speed_rate_at_spawn_kias_s)
            and abs(speed_rate_at_spawn_kias_s) <= 1.05
        )
    return int(common_timing_valid and (forced_gate_state_valid or stable_gate_state_valid))


def raw_row_time_ms(row):
    pc_time_sec = parse_float(row.get("pc_time_sec"))
    if not math.isnan(pc_time_sec):
        return pc_time_sec * 1000.0
    pc_time_ns = parse_float(row.get("pc_time_ns"))
    if not math.isnan(pc_time_ns):
        return pc_time_ns / 1_000_000.0
    return parse_float(row.get("pc_time_ms"))


def raw_validity(row, logger_name, validation_name):
    value = row.get(logger_name)
    if value in (None, ""):
        value = row.get(validation_name)
    return parse_int(value)


def read_csv(path):
    with Path(path).open(newline="", encoding="utf-8") as file:
        return list(csv.DictReader(file))


def load_aois(path):
    aois = []
    for row in read_csv(path):
        priority = row.get("priority", "").strip().lower()
        status = row.get("status", "").strip().lower()
        if priority == "exclude" or status == "exclude":
            continue
        if row["aoi"] not in INSTRUMENT_AOIS:
            continue
        aois.append(
            {
                "name": row["aoi"],
                "x1": parse_float(row["x1"]),
                "y1": parse_float(row["y1"]),
                "x2": parse_float(row["x2"]),
                "y2": parse_float(row["y2"]),
                "priority": row.get("priority", ""),
                "status": row.get("status", ""),
            }
        )
    return aois


def classify_aoi(px, py, aois, panel_top_y):
    if math.isnan(px) or math.isnan(py):
        return "UNTRACKED_OR_OFF_DISPLAY"
    for aoi in aois:
        if aoi["x1"] <= px <= aoi["x2"] and aoi["y1"] <= py <= aoi["y2"]:
            return aoi["name"]
    return "OUTSIDE_VIEW" if py < panel_top_y else "PANEL_OTHER"


def display_region(px, screen_width, monitor_width, center_monitor_index):
    if math.isnan(px):
        return "UNTRACKED"
    if px < 0 or px > screen_width:
        return "OFF_DISPLAY"
    center_start = center_monitor_index * monitor_width
    center_end = center_start + monitor_width
    if px < center_start:
        return "LEFT_MONITOR"
    if px < center_end:
        return "CENTER_MONITOR"
    return "RIGHT_MONITOR"


def evidence_rate(gaze_rows, evidence_names):
    if not gaze_rows:
        return ""
    count = sum(1 for row in gaze_rows if row["external_evidence"] in evidence_names)
    return count / len(gaze_rows)


def first_event(events, event_type):
    for event in events:
        if event.get("event_type") == event_type:
            return event
    return None


def event_ms(event):
    if not event:
        return math.nan
    return parse_float(event.get("recorded_timestamp_ms"))


def detail_value(detail, key):
    prefix = key + "="
    for part in (detail or "").split(";"):
        if part.startswith(prefix):
            return part[len(prefix):]
    return ""


def clean_trial_reason(events):
    counts = defaultdict(int)
    for event in events:
        counts[event.get("event_type", "")] += 1

    if counts["PILOT_RESPONSE_BASELINE"] > 0:
        return spawn_response_clean_trial_reason(events, counts)

    for event_type in CLEAN_TRIAL_EVENTS:
        if counts[event_type] == 0:
            return "missing " + event_type
        if counts[event_type] > 1:
            return f"duplicate {event_type} count={counts[event_type]}"

    trial_start = first_event(events, "TRIAL_START")
    protocol = detail_value(trial_start.get("detail", "") if trial_start else "", "protocol")
    task_audio_required = (
        protocol.startswith("audio_task_")
        or counts["TASK_COMMAND_AUDIO_START"] > 0
        or counts["TASK_COMMAND_AUDIO_END"] > 0
    )
    if task_audio_required:
        for event_type in TASK_AUDIO_EVENTS:
            if counts[event_type] == 0:
                return "missing " + event_type
            if counts[event_type] > 1:
                return f"duplicate {event_type} count={counts[event_type]}"

    search_start = 0
    previous = ""
    for expected in CLEAN_TRIAL_EVENTS:
        found = -1
        for index in range(search_start, len(events)):
            if events[index].get("event_type") == expected:
                found = index
                break
        if found < 0:
            return f"{expected} occurred before {previous}"
        search_start = found + 1
        previous = expected

    if task_audio_required:
        expected_audio_order = [
            "TRIAL_START",
            "TASK_COMMAND_AUDIO_START",
            "TASK_COMMAND_AUDIO_END",
            "TRIAL_END",
        ]
        search_start = 0
        previous = ""
        for expected in expected_audio_order:
            found = -1
            for index in range(search_start, len(events)):
                if events[index].get("event_type") == expected:
                    found = index
                    break
            if found < 0:
                return f"{expected} occurred before {previous}"
            search_start = found + 1
            previous = expected

    if protocol in {"audio_task_v27", "audio_task_v28", "audio_task_v29", "audio_task_v31", "audio_task_v32", "audio_task_v33", "audio_task_v34", "audio_task_v35", "audio_task_v36", "audio_task_v37", "audio_task_v38", "audio_task_v39"}:
        expected_spawn_order = [
            "TRIAL_START",
            "TASK_COMMAND_AUDIO_START",
            "SCENARIO_SELECTED",
            "INTRUDER_SPAWNED",
            "TRIAL_END",
        ]
        search_start = 0
        previous = ""
        for expected in expected_spawn_order:
            found = -1
            for index in range(search_start, len(events)):
                if events[index].get("event_type") == expected:
                    found = index
                    break
            if found < 0:
                return f"{expected} occurred before {previous}"
            search_start = found + 1
            previous = expected
    if protocol == "audio_task_v32":
        if counts["INTRUDER_VISUALLY_DETECTABLE"] == 0:
            return "missing INTRUDER_VISUALLY_DETECTABLE"
        if counts["INTRUDER_VISUALLY_DETECTABLE"] > 1:
            return (
                "duplicate INTRUDER_VISUALLY_DETECTABLE count="
                f"{counts['INTRUDER_VISUALLY_DETECTABLE']}"
            )
        reason = ordered_event_reason(
            events,
            [
                "INTRUDER_SPAWNED",
                "INTRUDER_VISUALLY_DETECTABLE",
                "TRIAL_END",
            ],
        )
        if reason:
            return reason
    if protocol in {"audio_task_v33", "audio_task_v34", "audio_task_v35", "audio_task_v36", "audio_task_v37", "audio_task_v38", "audio_task_v39"}:
        if counts["INTRUDER_VISUAL_OPPORTUNITY_ONSET"] == 0:
            return "missing INTRUDER_VISUAL_OPPORTUNITY_ONSET"
        if counts["INTRUDER_VISUAL_OPPORTUNITY_ONSET"] > 1:
            return (
                "duplicate INTRUDER_VISUAL_OPPORTUNITY_ONSET count="
                f"{counts['INTRUDER_VISUAL_OPPORTUNITY_ONSET']}"
            )
        reason = ordered_event_reason(
            events,
            [
                "INTRUDER_SPAWNED",
                "INTRUDER_VISUAL_OPPORTUNITY_ONSET",
                "TRIAL_END",
            ],
        )
        if reason:
            return reason
    return ""


def ordered_event_reason(events, expected_events):
    search_start = 0
    previous = ""
    for expected in expected_events:
        found = -1
        for index in range(search_start, len(events)):
            if events[index].get("event_type") == expected:
                found = index
                break
        if found < 0:
            return f"{expected} occurred before {previous}"
        search_start = found + 1
        previous = expected
    return ""


def optional_event_pair_reason(
    events,
    counts,
    start_event,
    end_event,
    lower_bound_event,
    upper_bound_event,
):
    start_count = counts[start_event]
    end_count = counts[end_event]
    if start_count == 0 and end_count == 0:
        return ""
    if start_count != 1:
        return (
            f"missing {start_event}"
            if start_count == 0
            else f"duplicate {start_event} count={start_count}"
        )
    if end_count != 1:
        return (
            f"missing {end_event}"
            if end_count == 0
            else f"duplicate {end_event} count={end_count}"
        )

    lower_ms = event_ms(first_event(events, lower_bound_event))
    start_ms = event_ms(first_event(events, start_event))
    end_ms = event_ms(first_event(events, end_event))
    upper_ms = event_ms(first_event(events, upper_bound_event))
    if any(math.isnan(value) for value in (lower_ms, start_ms, end_ms, upper_ms)):
        return f"missing event time for {start_event}/{end_event}"
    if start_ms < lower_ms:
        return f"{start_event} occurred before {lower_bound_event}"
    if end_ms < start_ms:
        return f"{end_event} occurred before {start_event}"
    if upper_ms < end_ms:
        return f"{upper_bound_event} occurred before {end_event}"
    return ""


def spawn_response_clean_trial_reason(events, counts):
    for event_type in SPAWN_RESPONSE_RECORDING_EVENTS:
        if counts[event_type] == 0:
            return "missing " + event_type
        if counts[event_type] > 1:
            return f"duplicate {event_type} count={counts[event_type]}"

    trial_start = first_event(events, "TRIAL_START")
    protocol = detail_value(
        trial_start.get("detail", "") if trial_start else "",
        "protocol",
    )
    task_audio_required = (
        protocol.startswith("audio_task_")
        or counts["TASK_COMMAND_AUDIO_START"] > 0
        or counts["TASK_COMMAND_AUDIO_END"] > 0
    )
    if task_audio_required:
        for event_type in TASK_AUDIO_EVENTS:
            if counts[event_type] == 0:
                return "missing " + event_type
            if counts[event_type] > 1:
                return f"duplicate {event_type} count={counts[event_type]}"

    reason = ordered_event_reason(events, SPAWN_RESPONSE_RECORDING_EVENTS)
    if reason:
        return reason
    if task_audio_required:
        reason = ordered_event_reason(
            events,
            [
                "TRIAL_START",
                "TASK_COMMAND_AUDIO_START",
                "TASK_COMMAND_AUDIO_END",
                "TRIAL_END",
            ],
        )
        if reason:
            return reason
    if protocol in {"audio_task_v27", "audio_task_v28", "audio_task_v29", "audio_task_v31", "audio_task_v32", "audio_task_v33", "audio_task_v34", "audio_task_v35", "audio_task_v36", "audio_task_v37", "audio_task_v38", "audio_task_v39"}:
        reason = ordered_event_reason(
            events,
            [
                "TRIAL_START",
                "TASK_COMMAND_AUDIO_START",
                "SCENARIO_SELECTED",
                "INTRUDER_SPAWNED",
                "TRIAL_END",
            ],
        )
        if reason:
            return reason

    if protocol == "audio_task_v32":
        if counts["INTRUDER_VISUALLY_DETECTABLE"] == 0:
            return "missing INTRUDER_VISUALLY_DETECTABLE"
        if counts["INTRUDER_VISUALLY_DETECTABLE"] > 1:
            return (
                "duplicate INTRUDER_VISUALLY_DETECTABLE count="
                f"{counts['INTRUDER_VISUALLY_DETECTABLE']}"
            )
        reason = ordered_event_reason(
            events,
            [
                "INTRUDER_SPAWNED",
                "INTRUDER_VISUALLY_DETECTABLE",
                "TRIAL_END",
            ],
        )
        if reason:
            return reason

    if protocol in {"audio_task_v33", "audio_task_v34", "audio_task_v35", "audio_task_v36", "audio_task_v37", "audio_task_v38", "audio_task_v39"}:
        if counts["INTRUDER_VISUAL_OPPORTUNITY_ONSET"] == 0:
            return "missing INTRUDER_VISUAL_OPPORTUNITY_ONSET"
        if counts["INTRUDER_VISUAL_OPPORTUNITY_ONSET"] > 1:
            return (
                "duplicate INTRUDER_VISUAL_OPPORTUNITY_ONSET count="
                f"{counts['INTRUDER_VISUAL_OPPORTUNITY_ONSET']}"
            )
        reason = ordered_event_reason(
            events,
            [
                "INTRUDER_SPAWNED",
                "INTRUDER_VISUAL_OPPORTUNITY_ONSET",
                "TRIAL_END",
            ],
        )
        if reason:
            return reason

    baseline = first_event(events, "PILOT_RESPONSE_BASELINE")
    baseline_valid = detail_value(
        baseline.get("detail", "") if baseline else "",
        "baseline_valid",
    )
    if baseline_valid.lower() != "true":
        return "response baseline invalid"

    response_lower_bound = (
        "INTRUDER_VISUAL_OPPORTUNITY_ONSET"
        if protocol in {"audio_task_v33", "audio_task_v34", "audio_task_v35", "audio_task_v36", "audio_task_v37", "audio_task_v38", "audio_task_v39"}
        else "INTRUDER_SPAWNED"
    )
    for event_pair in (
        (
            "PILOT_RESPONSE_START",
            "PILOT_RESPONSE_END",
            response_lower_bound,
            "TRIAL_END",
        ),
        (
            "HAZARD_DETECTED",
            "HAZARD_CLEARED",
            "INTRUDER_SPAWNED",
            "TRIAL_END",
        ),
        (
            "ADVISORY_SHOWN",
            "ADVISORY_CLEARED",
            "INTRUDER_SPAWNED",
            "TRIAL_END",
        ),
    ):
        reason = optional_event_pair_reason(events, counts, *event_pair)
        if reason:
            return reason
    return ""


def first_operational_external_after(gaze_rows, start_ms, end_ms=None):
    if math.isnan(start_ms):
        return None
    for row in gaze_rows:
        t = row["pc_time_ms"]
        if t < start_ms:
            continue
        if end_ms is not None and t > end_ms:
            return None
        if row["operational_aoi"] == "OPERATIONAL_EXTERNAL":
            return row
    return None


def last_gaze_before(gaze_rows, event_time_ms, lookback_ms=500.0):
    if math.isnan(event_time_ms):
        return None
    lower_bound = event_time_ms - lookback_ms
    last = None
    for row in gaze_rows:
        t = row["pc_time_ms"]
        if t > event_time_ms:
            break
        if t >= lower_bound:
            last = row
    return last


def operational_external_rate(gaze_rows, start_ms, end_ms):
    rows = [row for row in gaze_rows if start_ms <= row["pc_time_ms"] < end_ms]
    if not rows:
        return ""
    external = sum(1 for row in rows if row["operational_aoi"] == "OPERATIONAL_EXTERNAL")
    return external / len(rows)


def first_gaze_after(gaze_rows, start_ms, end_ms=None, aoi_name=None):
    if math.isnan(start_ms):
        return None
    for row in gaze_rows:
        t = row["pc_time_ms"]
        if t < start_ms:
            continue
        if end_ms is not None and t > end_ms:
            return None
        if row["valid_gaze"] != 1:
            continue
        if aoi_name is None or row["aoi"] == aoi_name:
            return row
    return None


def first_dwell_after(gaze_rows, start_ms, end_ms=None, aoi_name=None, dwell_ms=200.0):
    if math.isnan(start_ms):
        return None

    index = 0
    while index < len(gaze_rows):
        row = gaze_rows[index]
        t = row["pc_time_ms"]
        if t < start_ms:
            index += 1
            continue
        if end_ms is not None and t > end_ms:
            return None
        if row["valid_gaze"] != 1 or (aoi_name is not None and row["aoi"] != aoi_name):
            index += 1
            continue

        dwell_start = row
        dwell_end_time = t
        index += 1
        while index < len(gaze_rows):
            next_row = gaze_rows[index]
            next_time = next_row["pc_time_ms"]
            if end_ms is not None and next_time > end_ms:
                break
            if next_row["valid_gaze"] != 1 or (aoi_name is not None and next_row["aoi"] != aoi_name):
                break
            dwell_end_time = next_time
            index += 1

        if dwell_end_time - dwell_start["pc_time_ms"] >= dwell_ms:
            return dwell_start

    return None


def last_valid_gaze_before(gaze_rows, event_time_ms, lookback_ms=500.0):
    if math.isnan(event_time_ms):
        return None
    lower_bound = event_time_ms - lookback_ms
    last = None
    for row in gaze_rows:
        t = row["pc_time_ms"]
        if t > event_time_ms:
            break
        if t >= lower_bound and row["valid_gaze"] == 1:
            last = row
    return last


def first_entry_after(gaze_rows, start_ms, end_ms=None, aoi_name=None, lookback_ms=500.0):
    if math.isnan(start_ms) or aoi_name is None:
        return None

    previous_valid = last_valid_gaze_before(gaze_rows, start_ms, lookback_ms)
    previous_aoi = previous_valid["aoi"] if previous_valid else None

    for row in gaze_rows:
        t = row["pc_time_ms"]
        if t < start_ms:
            continue
        if end_ms is not None and t > end_ms:
            return None
        if row["valid_gaze"] != 1:
            continue

        current_aoi = row["aoi"]
        if current_aoi == aoi_name and previous_aoi != aoi_name:
            return row
        previous_aoi = current_aoi

    return None


def count_gaze_in_window(gaze_rows, start_ms, end_ms):
    rows = [row for row in gaze_rows if start_ms <= row["pc_time_ms"] <= end_ms]
    valid = [row for row in rows if row["valid_gaze"] == 1]
    return rows, valid


def enrich_gaze_rows(
    raw_rows,
    aois,
    screen_width,
    screen_height,
    panel_top_y,
    layout_mode=LAYOUT_CENTER,
    monitor_width=DEFAULT_MONITOR_WIDTH,
    center_monitor_index=DEFAULT_CENTER_MONITOR_INDEX,
    side_guard_px=DEFAULT_SIDE_GUARD_PX,
):
    enriched = []
    for row in raw_rows:
        avg_x = parse_float(row.get("avg_gaze_x"))
        avg_y = parse_float(row.get("avg_gaze_y"))
        left_valid = raw_validity(row, "left_validity", "left_gaze_validity")
        right_valid = raw_validity(row, "right_validity", "right_gaze_validity")
        valid = (left_valid == 1 or right_valid == 1) and not math.isnan(avg_x) and not math.isnan(avg_y)
        px = avg_x * screen_width if valid else math.nan
        py = avg_y * screen_height if valid else math.nan
        if not valid:
            region = "UNTRACKED"
        elif layout_mode == LAYOUT_SURROUND_WIDE:
            region = display_region(px, screen_width, monitor_width, center_monitor_index)
        else:
            region = "CENTER_MONITOR" if 0 <= px <= screen_width else "OFF_DISPLAY"
        center_px = px
        side_candidate_direction = ""

        if layout_mode == LAYOUT_SURROUND_WIDE:
            center_start = center_monitor_index * monitor_width
            center_end = center_start + monitor_width
            center_px = px - center_start if valid else math.nan
            if not valid or region == "OFF_DISPLAY":
                aoi = "UNTRACKED_OR_OFF_DISPLAY"
            elif region == "LEFT_MONITOR":
                aoi = "SIDE_MONITOR_LEFT"
            elif region == "RIGHT_MONITOR":
                aoi = "SIDE_MONITOR_RIGHT"
            else:
                aoi = classify_aoi(center_px, py, aois, panel_top_y)

            if valid and 0 <= px <= screen_width:
                if px <= center_start - side_guard_px:
                    side_candidate_direction = "LEFT"
                elif px >= center_end + side_guard_px:
                    side_candidate_direction = "RIGHT"
        else:
            aoi = classify_aoi(px, py, aois, panel_top_y) if valid else "UNTRACKED_OR_OFF_DISPLAY"

        out = dict(row)
        out["pc_time_ms"] = raw_row_time_ms(row)
        out["gaze_px"] = px
        out["gaze_py"] = py
        out["center_gaze_px"] = center_px
        out["display_region"] = region
        out["side_candidate_direction"] = side_candidate_direction
        out["valid_gaze"] = 1 if valid else 0
        out["aoi"] = aoi
        out["external_evidence"] = "NONE"
        out["external_direction"] = ""
        out["external_episode_id"] = ""
        out["operational_external"] = 0
        out["operational_aoi"] = aoi
        enriched.append(out)
    return enriched


def annotate_external_evidence(
    gaze_rows,
    layout_mode=LAYOUT_CENTER,
    side_dwell_ms=DEFAULT_SIDE_DWELL_MS,
    side_loss_min_ms=DEFAULT_SIDE_LOSS_MIN_MS,
    side_loss_max_ms=DEFAULT_SIDE_LOSS_MAX_MS,
    max_sample_gap_ms=DEFAULT_MAX_SAMPLE_GAP_MS,
):
    episodes = []

    for row in gaze_rows:
        aoi = row["aoi"]
        if aoi == "OUTSIDE_VIEW":
            row["external_evidence"] = "CENTER_OUTSIDE_MEASURED"
            row["operational_external"] = 1

    if layout_mode == LAYOUT_CENTER:
        for row in gaze_rows:
            if row["aoi"] == "UNTRACKED_OR_OFF_DISPLAY":
                row["external_evidence"] = "LEGACY_UNTRACKED_ASSUMED_EXTERNAL"
                row["operational_external"] = 1
            row["operational_aoi"] = (
                "OPERATIONAL_EXTERNAL" if row["operational_external"] == 1 else row["aoi"]
            )
        return episodes

    episode_counter = 0
    index = 0
    while index < len(gaze_rows):
        direction = gaze_rows[index]["side_candidate_direction"]
        if not direction:
            index += 1
            continue

        run_start = index
        run_end = index
        previous_time = gaze_rows[index]["pc_time_ms"]
        index += 1
        while index < len(gaze_rows):
            row = gaze_rows[index]
            current_time = row["pc_time_ms"]
            if (
                row["side_candidate_direction"] != direction
                or math.isnan(current_time)
                or math.isnan(previous_time)
                or current_time - previous_time > max_sample_gap_ms
            ):
                break
            run_end = index
            previous_time = current_time
            index += 1

        start_ms = gaze_rows[run_start]["pc_time_ms"]
        end_ms = gaze_rows[run_end]["pc_time_ms"]
        duration_ms = end_ms - start_ms
        if duration_ms >= side_dwell_ms:
            episode_counter += 1
            episode_id = f"MEASURED_{episode_counter:04d}"
            evidence = f"MEASURED_SIDE_{direction}"
            for row_index in range(run_start, run_end + 1):
                gaze_rows[row_index]["external_evidence"] = evidence
                gaze_rows[row_index]["external_direction"] = direction
                gaze_rows[row_index]["external_episode_id"] = episode_id
                gaze_rows[row_index]["operational_external"] = 1
            episodes.append(
                {
                    "episode_id": episode_id,
                    "episode_type": "MEASURED_SIDE",
                    "direction": direction,
                    "start_ms": start_ms,
                    "end_ms": end_ms,
                    "duration_ms": duration_ms,
                    "sample_count": run_end - run_start + 1,
                    "pre_display_region": (
                        gaze_rows[run_start - 1]["display_region"] if run_start > 0 else ""
                    ),
                    "post_display_region": (
                        gaze_rows[run_end + 1]["display_region"] if run_end + 1 < len(gaze_rows) else ""
                    ),
                }
            )

    index = 0
    while index < len(gaze_rows):
        if gaze_rows[index]["valid_gaze"] == 1:
            index += 1
            continue

        run_start = index
        run_end = index
        previous_time = gaze_rows[index]["pc_time_ms"]
        index += 1
        while index < len(gaze_rows):
            row = gaze_rows[index]
            current_time = row["pc_time_ms"]
            if (
                row["valid_gaze"] == 1
                or math.isnan(current_time)
                or math.isnan(previous_time)
                or current_time - previous_time > max_sample_gap_ms
            ):
                break
            run_end = index
            previous_time = current_time
            index += 1

        start_ms = gaze_rows[run_start]["pc_time_ms"]
        end_ms = gaze_rows[run_end]["pc_time_ms"]
        duration_ms = end_ms - start_ms
        previous_valid = gaze_rows[run_start - 1] if run_start > 0 else None
        next_valid = gaze_rows[run_end + 1] if run_end + 1 < len(gaze_rows) else None
        bracketed_by_valid = (
            previous_valid is not None
            and next_valid is not None
            and previous_valid["valid_gaze"] == 1
            and next_valid["valid_gaze"] == 1
        )
        qualifies = (
            side_loss_min_ms <= duration_ms <= side_loss_max_ms
            and bracketed_by_valid
        )
        if qualifies:
            episode_counter += 1
            episode_id = f"INFERRED_{episode_counter:04d}"
            for row_index in range(run_start, run_end + 1):
                gaze_rows[row_index]["external_evidence"] = "INFERRED_SIDE_UNKNOWN"
                gaze_rows[row_index]["external_direction"] = "UNKNOWN"
                gaze_rows[row_index]["external_episode_id"] = episode_id
                gaze_rows[row_index]["operational_external"] = 1
            episodes.append(
                {
                    "episode_id": episode_id,
                    "episode_type": "INFERRED_SIDE",
                    "direction": "UNKNOWN",
                    "start_ms": start_ms,
                    "end_ms": end_ms,
                    "duration_ms": duration_ms,
                    "sample_count": run_end - run_start + 1,
                    "pre_display_region": previous_valid["display_region"],
                    "post_display_region": next_valid["display_region"],
                }
            )

    for row in gaze_rows:
        if row["operational_external"] == 1:
            row["operational_aoi"] = "OPERATIONAL_EXTERNAL"
        elif row["valid_gaze"] != 1:
            row["external_evidence"] = "UNRESOLVED_TRACKING_LOSS"
            row["operational_aoi"] = "UNRESOLVED_TRACKING_LOSS"
        elif row["aoi"] in ("SIDE_MONITOR_LEFT", "SIDE_MONITOR_RIGHT"):
            row["external_evidence"] = (
                "SIDE_COORDINATE_TRANSIENT"
                if row["side_candidate_direction"]
                else "SIDE_BOUNDARY_AMBIGUOUS"
            )
            row["operational_aoi"] = row["external_evidence"]
        else:
            row["operational_aoi"] = row["aoi"]

    return episodes


def write_csv(path, rows, fieldnames):
    with Path(path).open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def fmt_latency(row, key):
    value = row.get(key, "")
    if value == "" or value is None:
        return "n/a"
    return f"{float(value):.3f}s"


def aggregate_trial_metrics(trial_rows):
    clean_rows = [row for row in trial_rows if row["clean"] == 1]
    groups = [("all", clean_rows)]
    for approach in ("front", "left", "right"):
        groups.append((approach, [row for row in clean_rows if row["approach"] == approach]))

    metrics = [
        "trial_start_to_task_audio_s",
        "task_audio_duration_s",
        "planned_task_audio_to_spawn_s",
        "task_audio_start_to_spawn_s",
        "task_audio_spawn_timing_valid",
        "task_audio_start_to_detectability_s",
        "task_audio_start_to_visual_opportunity_s",
        "spawn_to_visual_opportunity_s",
        "visual_opportunity_to_response_s",
        "spawn_to_detectability_s",
        "detectability_to_response_s",
        "spawn_to_response_s",
        "spawn_to_hazard_s",
        "advisory_to_response_s",
        "hazard_window_s",
        "response_duration_s",
        "advisory_to_first_operational_external_s",
        "operational_external_before_advisory",
        "spawn_to_first_operational_external_s",
        "operational_external_before_spawn",
        "detectability_to_first_operational_external_s",
        "operational_external_before_detectability",
        "visual_opportunity_to_first_operational_external_s",
        "operational_external_before_visual_opportunity",
        "trial_operational_external_rate",
        "trial_measured_side_external_rate",
        "trial_inferred_side_external_rate",
        "trial_unresolved_tracking_loss_rate",
    ] + [
        f"operational_external_rate_{name}"
        for name, _, _ in OPERATIONAL_EXTERNAL_WINDOWS
    ] + [
        f"spawn_operational_external_rate_{name}"
        for name, _, _ in OPERATIONAL_EXTERNAL_WINDOWS
    ] + [
        f"detectability_operational_external_rate_{name}"
        for name, _, _ in OPERATIONAL_EXTERNAL_WINDOWS
    ] + [
        f"visual_opportunity_operational_external_rate_{name}"
        for name, _, _ in OPERATIONAL_EXTERNAL_WINDOWS
    ]

    aggregate_rows = []
    for group_name, group_rows in groups:
        for metric in metrics:
            values = []
            for row in group_rows:
                value = row.get(metric, "")
                if value == "" or value is None:
                    continue
                parsed = parse_float(value)
                if not math.isnan(parsed):
                    values.append(parsed)
            aggregate_rows.append(
                {
                    "group": group_name,
                    "clean_trial_count": len(group_rows),
                    "metric": metric,
                    "n": len(values),
                    "mean": statistics.fmean(values) if values else "",
                    "median": statistics.median(values) if values else "",
                    "stdev": statistics.stdev(values) if len(values) > 1 else "",
                    "min": min(values) if values else "",
                    "max": max(values) if values else "",
                }
            )
    return aggregate_rows


def main():
    parser = argparse.ArgumentParser(description="Analyze X-Plane events with Tobii gaze CSV.")
    parser.add_argument("--events", required=True)
    parser.add_argument("--gaze", required=True)
    parser.add_argument("--aoi", default=DEFAULT_AOI_PATH)
    parser.add_argument("--output-prefix", required=True)
    parser.add_argument("--layout-mode", choices=[LAYOUT_CENTER, LAYOUT_SURROUND_WIDE], default=LAYOUT_CENTER)
    parser.add_argument("--screen-width", type=int)
    parser.add_argument("--screen-height", type=int, default=DEFAULT_SCREEN_HEIGHT)
    parser.add_argument("--panel-top-y", type=float, default=DEFAULT_PANEL_TOP_Y)
    parser.add_argument("--monitor-width", type=int, default=DEFAULT_MONITOR_WIDTH)
    parser.add_argument("--center-monitor-index", type=int, default=DEFAULT_CENTER_MONITOR_INDEX)
    parser.add_argument("--side-guard-px", type=float, default=DEFAULT_SIDE_GUARD_PX)
    parser.add_argument("--side-dwell-ms", type=float, default=DEFAULT_SIDE_DWELL_MS)
    parser.add_argument("--side-loss-min-ms", type=float, default=DEFAULT_SIDE_LOSS_MIN_MS)
    parser.add_argument("--side-loss-max-ms", type=float, default=DEFAULT_SIDE_LOSS_MAX_MS)
    parser.add_argument("--max-sample-gap-ms", type=float, default=DEFAULT_MAX_SAMPLE_GAP_MS)
    parser.add_argument("--dwell-ms", type=float, default=200.0)
    parser.add_argument("--event-lookback-ms", type=float, default=500.0)
    args = parser.parse_args()

    screen_width = args.screen_width
    if screen_width is None:
        screen_width = (
            args.monitor_width * 3
            if args.layout_mode == LAYOUT_SURROUND_WIDE
            else DEFAULT_SCREEN_WIDTH
        )
    if args.layout_mode == LAYOUT_SURROUND_WIDE:
        expected_width = args.monitor_width * 3
        if screen_width != expected_width:
            parser.error(
                f"surround-wide requires screen width {expected_width} "
                f"for three {args.monitor_width}px monitors; got {screen_width}"
            )
        if args.center_monitor_index != 1:
            parser.error("surround-wide currently requires --center-monitor-index 1")
    if args.side_loss_min_ms < 0 or args.side_loss_max_ms < args.side_loss_min_ms:
        parser.error("side loss thresholds must satisfy 0 <= min <= max")
    if args.side_dwell_ms < 0 or args.side_guard_px < 0 or args.max_sample_gap_ms <= 0:
        parser.error("side dwell/guard must be non-negative and sample gap must be positive")

    events = read_csv(args.events)
    gaze_raw = read_csv(args.gaze)
    aois = load_aois(args.aoi)
    gaze = enrich_gaze_rows(
        gaze_raw,
        aois,
        screen_width,
        args.screen_height,
        args.panel_top_y,
        args.layout_mode,
        args.monitor_width,
        args.center_monitor_index,
        args.side_guard_px,
    )
    external_episodes = annotate_external_evidence(
        gaze,
        args.layout_mode,
        args.side_dwell_ms,
        args.side_loss_min_ms,
        args.side_loss_max_ms,
        args.max_sample_gap_ms,
    )
    valid_gaze = [row for row in gaze if row["valid_gaze"] == 1]

    events_by_trial = defaultdict(list)
    for event in events:
        events_by_trial[event.get("trial_id", "")].append(event)

    output_prefix = Path(args.output_prefix)
    output_prefix.parent.mkdir(parents=True, exist_ok=True)

    enriched_fields = list(gaze_raw[0].keys()) + [
        "pc_time_ms",
        "gaze_px",
        "gaze_py",
        "center_gaze_px",
        "display_region",
        "side_candidate_direction",
        "valid_gaze",
        "aoi",
        "external_evidence",
        "external_direction",
        "external_episode_id",
        "operational_external",
        "operational_aoi",
    ]
    enriched_path = output_prefix.with_name(output_prefix.name + "_gaze_aoi.csv")
    write_csv(enriched_path, gaze, enriched_fields)
    episode_path = output_prefix.with_name(output_prefix.name + "_external_episodes.csv")
    write_csv(episode_path, external_episodes, EXTERNAL_EPISODE_FIELDS)

    trial_rows = []
    for trial_id in sorted(events_by_trial, key=lambda value: int(value) if value.isdigit() else -1):
        if trial_id in ("", "0"):
            continue

        trial_events = events_by_trial[trial_id]
        start = first_event(trial_events, "TRIAL_START")
        end = first_event(trial_events, "TRIAL_END")
        task_audio_start = first_event(trial_events, "TASK_COMMAND_AUDIO_START")
        task_audio_end = first_event(trial_events, "TASK_COMMAND_AUDIO_END")
        scenario = first_event(trial_events, "SCENARIO_SELECTED")
        spawn = first_event(trial_events, "INTRUDER_SPAWNED")
        visual_opportunity = first_event(
            trial_events, "INTRUDER_VISUAL_OPPORTUNITY_ONSET"
        )
        detectable = first_event(trial_events, "INTRUDER_VISUALLY_DETECTABLE")
        response_baseline = first_event(trial_events, "PILOT_RESPONSE_BASELINE")
        hazard = first_event(trial_events, "HAZARD_DETECTED")
        advisory = first_event(trial_events, "ADVISORY_SHOWN")
        response = first_event(trial_events, "PILOT_RESPONSE_START")
        hazard_clear = first_event(trial_events, "HAZARD_CLEARED")
        response_end = first_event(trial_events, "PILOT_RESPONSE_END")
        incomplete_reason = clean_trial_reason(trial_events)
        approach = detail_value(spawn.get("detail", "") if spawn else "", "approach") or "unknown"

        start_ms = event_ms(start)
        end_ms = event_ms(end)
        if math.isnan(end_ms) and trial_events:
            end_ms = event_ms(trial_events[-1])

        trial_gaze, trial_valid = count_gaze_in_window(gaze, start_ms, end_ms) if not math.isnan(start_ms) else ([], [])

        row = {
            "trial_id": trial_id,
            "has_trial_start": 1 if start else 0,
            "has_trial_end": 1 if end else 0,
            "has_task_audio_start": 1 if task_audio_start else 0,
            "has_task_audio_end": 1 if task_audio_end else 0,
            "has_intruder_spawned": 1 if spawn else 0,
            "has_intruder_visual_opportunity_onset": 1 if visual_opportunity else 0,
            "has_intruder_visually_detectable": 1 if detectable else 0,
            "has_pilot_response_baseline": 1 if response_baseline else 0,
            "has_advisory_shown": 1 if advisory else 0,
            "has_pilot_response_start": 1 if response else 0,
            "clean": 1 if not incomplete_reason else 0,
            "incomplete_reason": incomplete_reason,
            "approach": approach,
            "target_speed_kias": (
                detail_value(start.get("detail", "") if start else "", "target_speed_kias")
                or detail_value(
                    task_audio_start.get("detail", "") if task_audio_start else "",
                    "target_speed_kias",
                )
            ),
            "trial_start_ms": "" if math.isnan(start_ms) else int(start_ms),
            "trial_end_ms": "" if math.isnan(end_ms) else int(end_ms),
            "trial_duration_s": "" if math.isnan(start_ms) or math.isnan(end_ms) else (end_ms - start_ms) / 1000.0,
            "gaze_rows": len(trial_gaze),
            "valid_gaze_rows": len(trial_valid),
            "valid_gaze_rate": (len(trial_valid) / len(trial_gaze)) if trial_gaze else "",
        }

        spawn_ms = event_ms(spawn)
        visual_opportunity_ms = event_ms(visual_opportunity)
        detectable_ms = event_ms(detectable)
        task_audio_start_ms = event_ms(task_audio_start)
        task_audio_end_ms = event_ms(task_audio_end)
        hazard_ms = event_ms(hazard)
        advisory_ms = event_ms(advisory)
        response_ms = event_ms(response)
        hazard_clear_ms = event_ms(hazard_clear)
        response_end_ms = event_ms(response_end)
        row["trial_start_to_task_audio_s"] = (
            ""
            if math.isnan(start_ms) or math.isnan(task_audio_start_ms)
            else (task_audio_start_ms - start_ms) / 1000.0
        )
        row["task_audio_duration_s"] = (
            ""
            if math.isnan(task_audio_start_ms) or math.isnan(task_audio_end_ms)
            else (task_audio_end_ms - task_audio_start_ms) / 1000.0
        )
        scenario_detail = scenario.get("detail", "") if scenario else ""
        spawn_detail = spawn.get("detail", "") if spawn else ""
        planned_audio_to_spawn_s = parse_float(
            detail_value(
                scenario_detail,
                "planned_audio_to_spawn_s",
            )
        )
        row["task_audio_start_to_spawn_s"] = (
            ""
            if math.isnan(task_audio_start_ms) or math.isnan(spawn_ms)
            else (spawn_ms - task_audio_start_ms) / 1000.0
        )
        row["planned_task_audio_to_spawn_s"] = (
            "" if math.isnan(planned_audio_to_spawn_s) else planned_audio_to_spawn_s
        )
        row["spawn_gate_method"] = (
            detail_value(spawn_detail, "spawn_gate_method")
            or detail_value(scenario_detail, "spawn_gate_method")
        )
        spawn_timeout_audio_to_spawn_s = parse_float(
            detail_value(spawn_detail, "spawn_timeout_audio_to_spawn_s")
            or detail_value(scenario_detail, "spawn_timeout_audio_to_spawn_s")
        )
        row["spawn_timeout_audio_to_spawn_s"] = (
            ""
            if math.isnan(spawn_timeout_audio_to_spawn_s)
            else spawn_timeout_audio_to_spawn_s
        )
        row["speed_stable_before_spawn"] = detail_value(
            spawn_detail, "speed_stable_before_spawn"
        )
        row["speed_error_at_spawn_kias"] = detail_value(
            spawn_detail, "speed_error_at_spawn_kias"
        )
        row["speed_rate_at_spawn_kias_s"] = detail_value(
            spawn_detail, "speed_rate_at_spawn_kias_s"
        )
        row["speed_rate_valid_at_spawn"] = detail_value(
            spawn_detail, "speed_rate_valid"
        )
        row["speed_stability_duration_s"] = detail_value(
            spawn_detail, "speed_stability_duration_s"
        )
        row["speed_gate_pass_audio_elapsed_s"] = detail_value(
            spawn_detail, "speed_gate_pass_audio_elapsed_s"
        )
        row["spawn_wait_after_candidate_s"] = detail_value(
            spawn_detail, "spawn_wait_after_candidate_s"
        )
        row["spawn_forced_by_timeout"] = detail_value(
            spawn_detail, "spawn_forced_by_timeout"
        )
        protocol = detail_value(start.get("detail", "") if start else "", "protocol")
        actual_audio_to_spawn_s = parse_float(row["task_audio_start_to_spawn_s"])
        row["task_audio_spawn_timing_valid"] = audio_spawn_timing_valid(
            protocol,
            planned_audio_to_spawn_s,
            actual_audio_to_spawn_s,
            spawn_timeout_audio_to_spawn_s,
            row["speed_stable_before_spawn"].lower() == "true",
            row["spawn_forced_by_timeout"].lower() == "true",
            parse_float(row["speed_error_at_spawn_kias"]),
            parse_float(row["speed_rate_at_spawn_kias_s"]),
            row["speed_rate_valid_at_spawn"].lower() == "true",
            parse_float(row["speed_stability_duration_s"]),
        )
        response_baseline_detail = (
            response_baseline.get("detail", "") if response_baseline else ""
        )
        response_detail = response.get("detail", "") if response else ""
        row["response_anchor"] = (
            detail_value(response_detail, "anchor")
            or detail_value(response_baseline_detail, "anchor")
        )
        row["response_method"] = (
            detail_value(response_detail, "method")
            or detail_value(response_baseline_detail, "method")
        )
        row["response_baseline_valid"] = detail_value(
            response_baseline_detail,
            "baseline_valid",
        )
        row["response_baseline_samples"] = detail_value(
            response_baseline_detail,
            "baseline_samples",
        )
        row["response_baseline_window_s"] = detail_value(
            response_baseline_detail,
            "baseline_window_actual_s",
        )
        row["active_control_at_spawn"] = detail_value(
            response_baseline_detail,
            "active_control_at_spawn",
        )
        row["active_control_at_anchor"] = detail_value(
            response_baseline_detail,
            "active_control_at_anchor",
        )
        row["active_control_at_detectability"] = (
            row["active_control_at_anchor"]
            if row["response_anchor"] == "INTRUDER_VISUALLY_DETECTABLE"
            else ""
        )
        row["active_control_at_visual_opportunity"] = (
            row["active_control_at_anchor"]
            if row["response_anchor"] == "INTRUDER_VISUAL_OPPORTUNITY_ONSET"
            else ""
        )
        row["response_trigger_axis"] = detail_value(response_detail, "trigger_axis")
        row["response_persistence_s"] = detail_value(
            response_detail,
            "persistence_actual_s",
        )
        detectability_detail = detectable.get("detail", "") if detectable else ""
        row["detectability_method"] = detail_value(
            detectability_detail,
            "method",
        )
        row["detectability_operational_proxy"] = detail_value(
            detectability_detail,
            "operational_proxy",
        )
        for output_name, detail_name in (
            ("detectability_threshold_px", "threshold_px"),
            ("detectability_estimated_span_px", "estimated_span_px"),
            ("detectability_screen_x_px", "screen_x_px"),
            ("detectability_screen_y_px", "screen_y_px"),
            ("detectability_slant_distance_m", "slant_distance_m"),
            ("detectability_horizontal_distance_m", "horizontal_distance_m"),
            ("detectability_relative_bearing_deg", "relative_bearing_deg"),
            ("detectability_relative_elevation_deg", "relative_elevation_deg"),
            ("ias_at_detectability_kias", "own_ias_kias"),
        ):
            value = parse_float(detail_value(detectability_detail, detail_name))
            row[output_name] = "" if math.isnan(value) else value
        visual_opportunity_detail = (
            visual_opportunity.get("detail", "") if visual_opportunity else ""
        )
        row["visual_opportunity_method"] = detail_value(
            visual_opportunity_detail,
            "method",
        )
        row["visual_opportunity_operational_proxy"] = detail_value(
            visual_opportunity_detail,
            "operational_proxy",
        )
        row["visual_opportunity_recognition_claim"] = detail_value(
            visual_opportunity_detail,
            "recognition_claim",
        )
        row["visual_opportunity_lights_write_ok"] = detail_value(
            visual_opportunity_detail,
            "lights_write_ok",
        )
        for output_name, detail_name in (
            ("visual_opportunity_minimum_span_px", "minimum_span_px"),
            ("visual_opportunity_estimated_span_px", "estimated_span_px"),
            ("visual_opportunity_screen_x_px", "screen_x_px"),
            ("visual_opportunity_screen_y_px", "screen_y_px"),
            ("visual_opportunity_slant_distance_m", "slant_distance_m"),
            ("visual_opportunity_horizontal_distance_m", "horizontal_distance_m"),
            ("visual_opportunity_relative_bearing_deg", "relative_bearing_deg"),
            ("visual_opportunity_relative_elevation_deg", "relative_elevation_deg"),
            ("ias_at_visual_opportunity_kias", "own_ias_kias"),
        ):
            value = parse_float(detail_value(visual_opportunity_detail, detail_name))
            row[output_name] = "" if math.isnan(value) else value
        row["task_audio_start_to_visual_opportunity_s"] = (
            ""
            if math.isnan(task_audio_start_ms) or math.isnan(visual_opportunity_ms)
            else (visual_opportunity_ms - task_audio_start_ms) / 1000.0
        )
        row["spawn_to_visual_opportunity_s"] = (
            ""
            if math.isnan(spawn_ms) or math.isnan(visual_opportunity_ms)
            else (visual_opportunity_ms - spawn_ms) / 1000.0
        )
        row["visual_opportunity_to_response_s"] = (
            ""
            if math.isnan(visual_opportunity_ms)
            or math.isnan(response_ms)
            or response_ms < visual_opportunity_ms
            else (response_ms - visual_opportunity_ms) / 1000.0
        )
        if math.isnan(visual_opportunity_ms):
            row["response_vs_visual_opportunity"] = "NO_VISUAL_OPPORTUNITY_EVENT"
        elif math.isnan(response_ms):
            row["response_vs_visual_opportunity"] = "NO_RESPONSE_EVENT"
        elif response_ms < visual_opportunity_ms:
            row["response_vs_visual_opportunity"] = "RESPONSE_BEFORE_VISUAL_OPPORTUNITY"
        else:
            row["response_vs_visual_opportunity"] = "POST_VISUAL_OPPORTUNITY_RESPONSE"
        row["task_audio_start_to_detectability_s"] = (
            ""
            if math.isnan(task_audio_start_ms) or math.isnan(detectable_ms)
            else (detectable_ms - task_audio_start_ms) / 1000.0
        )
        row["spawn_to_detectability_s"] = (
            ""
            if math.isnan(spawn_ms) or math.isnan(detectable_ms)
            else (detectable_ms - spawn_ms) / 1000.0
        )
        row["detectability_to_response_s"] = (
            ""
            if math.isnan(detectable_ms)
            or math.isnan(response_ms)
            or response_ms < detectable_ms
            else (response_ms - detectable_ms) / 1000.0
        )
        if math.isnan(detectable_ms):
            row["response_vs_detectability"] = "NO_DETECTABILITY_EVENT"
        elif math.isnan(response_ms):
            row["response_vs_detectability"] = "NO_RESPONSE_EVENT"
        elif response_ms < detectable_ms:
            row["response_vs_detectability"] = "RESPONSE_BEFORE_DETECTABILITY"
        else:
            row["response_vs_detectability"] = "POST_DETECTABILITY_RESPONSE"
        row["spawn_to_response_s"] = (
            ""
            if math.isnan(spawn_ms) or math.isnan(response_ms)
            else (response_ms - spawn_ms) / 1000.0
        )
        trial_start_detail = start.get("detail", "") if start else ""
        for sound_name in ("master", "interior", "engine", "prop", "enviro", "radio"):
            sound_value = parse_float(
                detail_value(trial_start_detail, f"sound_{sound_name}")
            )
            row[f"sound_{sound_name}_ratio"] = (
                "" if math.isnan(sound_value) else sound_value
            )
        if not math.isnan(spawn_ms) and not math.isnan(advisory_ms):
            row["spawn_to_advisory_s"] = (advisory_ms - spawn_ms) / 1000.0
        else:
            row["spawn_to_advisory_s"] = ""
        if not math.isnan(advisory_ms) and not math.isnan(response_ms):
            row["advisory_to_response_s"] = (response_ms - advisory_ms) / 1000.0
        else:
            row["advisory_to_response_s"] = ""
        row["spawn_to_hazard_s"] = (
            "" if math.isnan(spawn_ms) or math.isnan(hazard_ms) else (hazard_ms - spawn_ms) / 1000.0
        )
        row["hazard_window_s"] = (
            "" if math.isnan(hazard_ms) or math.isnan(hazard_clear_ms) else (hazard_clear_ms - hazard_ms) / 1000.0
        )
        row["response_duration_s"] = (
            "" if math.isnan(response_ms) or math.isnan(response_end_ms) else (response_end_ms - response_ms) / 1000.0
        )

        row["trial_operational_external_rate"] = (
            sum(1 for gaze_row in trial_gaze if gaze_row["operational_aoi"] == "OPERATIONAL_EXTERNAL") / len(trial_gaze)
            if trial_gaze else ""
        )
        row["trial_measured_side_external_rate"] = evidence_rate(
            trial_gaze, MEASURED_SIDE_EVIDENCE
        )
        row["trial_inferred_side_external_rate"] = evidence_rate(
            trial_gaze, INFERRED_SIDE_EVIDENCE
        )
        row["trial_unresolved_tracking_loss_rate"] = evidence_rate(
            trial_gaze, {"UNRESOLVED_TRACKING_LOSS"}
        )
        trial_external_episodes = [
            episode
            for episode in external_episodes
            if not math.isnan(start_ms)
            and not math.isnan(end_ms)
            and episode["end_ms"] >= start_ms
            and episode["start_ms"] <= end_ms
        ]
        row["measured_side_episode_count"] = sum(
            1 for episode in trial_external_episodes if episode["episode_type"] == "MEASURED_SIDE"
        )
        row["inferred_side_episode_count"] = sum(
            1 for episode in trial_external_episodes if episode["episode_type"] == "INFERRED_SIDE"
        )
        for window_name, relative_start_s, relative_end_s in OPERATIONAL_EXTERNAL_WINDOWS:
            window_start_ms = advisory_ms + relative_start_s * 1000.0
            window_end_ms = advisory_ms + relative_end_s * 1000.0
            window_inside_trial = (
                not math.isnan(advisory_ms)
                and not math.isnan(start_ms)
                and not math.isnan(end_ms)
                and window_start_ms >= start_ms
                and window_end_ms <= end_ms
            )
            row[f"operational_external_rate_{window_name}"] = (
                operational_external_rate(gaze, window_start_ms, window_end_ms) if window_inside_trial else ""
            )

        first_operational_external = first_operational_external_after(gaze, advisory_ms, end_ms)
        row["advisory_to_first_operational_external_s"] = (
            "" if first_operational_external is None else (first_operational_external["pc_time_ms"] - advisory_ms) / 1000.0
        )
        before_advisory_any = last_gaze_before(gaze, advisory_ms, args.event_lookback_ms)
        row["operational_external_before_advisory"] = (
            1 if before_advisory_any and before_advisory_any["operational_aoi"] == "OPERATIONAL_EXTERNAL" else 0
        )

        for window_name, relative_start_s, relative_end_s in OPERATIONAL_EXTERNAL_WINDOWS:
            window_start_ms = spawn_ms + relative_start_s * 1000.0
            window_end_ms = spawn_ms + relative_end_s * 1000.0
            window_inside_trial = (
                not math.isnan(spawn_ms)
                and not math.isnan(start_ms)
                and not math.isnan(end_ms)
                and window_start_ms >= start_ms
                and window_end_ms <= end_ms
            )
            row[f"spawn_operational_external_rate_{window_name}"] = (
                operational_external_rate(gaze, window_start_ms, window_end_ms)
                if window_inside_trial
                else ""
            )

        for window_name, relative_start_s, relative_end_s in OPERATIONAL_EXTERNAL_WINDOWS:
            window_start_ms = detectable_ms + relative_start_s * 1000.0
            window_end_ms = detectable_ms + relative_end_s * 1000.0
            window_inside_trial = (
                not math.isnan(detectable_ms)
                and not math.isnan(start_ms)
                and not math.isnan(end_ms)
                and window_start_ms >= start_ms
                and window_end_ms <= end_ms
            )
            row[f"detectability_operational_external_rate_{window_name}"] = (
                operational_external_rate(gaze, window_start_ms, window_end_ms)
                if window_inside_trial
                else ""
            )

        for window_name, relative_start_s, relative_end_s in OPERATIONAL_EXTERNAL_WINDOWS:
            window_start_ms = visual_opportunity_ms + relative_start_s * 1000.0
            window_end_ms = visual_opportunity_ms + relative_end_s * 1000.0
            window_inside_trial = (
                not math.isnan(visual_opportunity_ms)
                and not math.isnan(start_ms)
                and not math.isnan(end_ms)
                and window_start_ms >= start_ms
                and window_end_ms <= end_ms
            )
            row[f"visual_opportunity_operational_external_rate_{window_name}"] = (
                operational_external_rate(gaze, window_start_ms, window_end_ms)
                if window_inside_trial
                else ""
            )

        first_spawn_operational_external = first_operational_external_after(
            gaze, spawn_ms, end_ms
        )
        row["spawn_to_first_operational_external_s"] = (
            ""
            if first_spawn_operational_external is None
            else (first_spawn_operational_external["pc_time_ms"] - spawn_ms) / 1000.0
        )
        before_spawn_any = last_gaze_before(
            gaze, spawn_ms, args.event_lookback_ms
        )
        row["operational_external_before_spawn"] = (
            1
            if before_spawn_any
            and before_spawn_any["operational_aoi"] == "OPERATIONAL_EXTERNAL"
            else 0
        )

        first_detectable_operational_external = first_operational_external_after(
            gaze, detectable_ms, end_ms
        )
        row["detectability_to_first_operational_external_s"] = (
            ""
            if first_detectable_operational_external is None
            else (
                first_detectable_operational_external["pc_time_ms"] - detectable_ms
            )
            / 1000.0
        )
        before_detectable_any = last_gaze_before(
            gaze, detectable_ms, args.event_lookback_ms
        )
        row["operational_external_before_detectability"] = (
            1
            if before_detectable_any
            and before_detectable_any["operational_aoi"] == "OPERATIONAL_EXTERNAL"
            else 0
        )

        first_visual_opportunity_operational_external = first_operational_external_after(
            gaze, visual_opportunity_ms, end_ms
        )
        row["visual_opportunity_to_first_operational_external_s"] = (
            ""
            if first_visual_opportunity_operational_external is None
            else (
                first_visual_opportunity_operational_external["pc_time_ms"]
                - visual_opportunity_ms
            )
            / 1000.0
        )
        before_visual_opportunity_any = last_gaze_before(
            gaze, visual_opportunity_ms, args.event_lookback_ms
        )
        row["operational_external_before_visual_opportunity"] = (
            1
            if before_visual_opportunity_any
            and before_visual_opportunity_any["operational_aoi"]
            == "OPERATIONAL_EXTERNAL"
            else 0
        )

        for aoi_name in TARGET_AOIS:
            first_after_advisory = first_gaze_after(gaze, advisory_ms, end_ms, aoi_name)
            first_after_spawn = first_gaze_after(gaze, spawn_ms, end_ms, aoi_name)
            first_after_visual_opportunity = first_gaze_after(
                gaze, visual_opportunity_ms, end_ms, aoi_name
            )
            first_after_detectable = first_gaze_after(gaze, detectable_ms, end_ms, aoi_name)
            first_dwell_after_advisory = first_dwell_after(gaze, advisory_ms, end_ms, aoi_name, args.dwell_ms)
            first_dwell_after_spawn = first_dwell_after(gaze, spawn_ms, end_ms, aoi_name, args.dwell_ms)
            first_dwell_after_visual_opportunity = first_dwell_after(
                gaze, visual_opportunity_ms, end_ms, aoi_name, args.dwell_ms
            )
            first_dwell_after_detectable = first_dwell_after(gaze, detectable_ms, end_ms, aoi_name, args.dwell_ms)
            first_entry_after_advisory = first_entry_after(gaze, advisory_ms, end_ms, aoi_name, args.event_lookback_ms)
            first_entry_after_spawn = first_entry_after(gaze, spawn_ms, end_ms, aoi_name, args.event_lookback_ms)
            first_entry_after_visual_opportunity = first_entry_after(
                gaze,
                visual_opportunity_ms,
                end_ms,
                aoi_name,
                args.event_lookback_ms,
            )
            first_entry_after_detectable = first_entry_after(gaze, detectable_ms, end_ms, aoi_name, args.event_lookback_ms)
            row[f"advisory_to_first_{aoi_name}_gaze_s"] = (
                "" if first_after_advisory is None else (first_after_advisory["pc_time_ms"] - advisory_ms) / 1000.0
            )
            row[f"spawn_to_first_{aoi_name}_gaze_s"] = (
                "" if first_after_spawn is None else (first_after_spawn["pc_time_ms"] - spawn_ms) / 1000.0
            )
            row[f"visual_opportunity_to_first_{aoi_name}_gaze_s"] = (
                ""
                if first_after_visual_opportunity is None
                else (
                    first_after_visual_opportunity["pc_time_ms"]
                    - visual_opportunity_ms
                )
                / 1000.0
            )
            row[f"detectability_to_first_{aoi_name}_gaze_s"] = (
                ""
                if first_after_detectable is None
                else (first_after_detectable["pc_time_ms"] - detectable_ms) / 1000.0
            )
            row[f"advisory_to_first_{aoi_name}_dwell_s"] = (
                "" if first_dwell_after_advisory is None else (first_dwell_after_advisory["pc_time_ms"] - advisory_ms) / 1000.0
            )
            row[f"spawn_to_first_{aoi_name}_dwell_s"] = (
                "" if first_dwell_after_spawn is None else (first_dwell_after_spawn["pc_time_ms"] - spawn_ms) / 1000.0
            )
            row[f"visual_opportunity_to_first_{aoi_name}_dwell_s"] = (
                ""
                if first_dwell_after_visual_opportunity is None
                else (
                    first_dwell_after_visual_opportunity["pc_time_ms"]
                    - visual_opportunity_ms
                )
                / 1000.0
            )
            row[f"detectability_to_first_{aoi_name}_dwell_s"] = (
                ""
                if first_dwell_after_detectable is None
                else (first_dwell_after_detectable["pc_time_ms"] - detectable_ms) / 1000.0
            )
            row[f"advisory_to_first_{aoi_name}_entry_s"] = (
                "" if first_entry_after_advisory is None else (first_entry_after_advisory["pc_time_ms"] - advisory_ms) / 1000.0
            )
            row[f"spawn_to_first_{aoi_name}_entry_s"] = (
                "" if first_entry_after_spawn is None else (first_entry_after_spawn["pc_time_ms"] - spawn_ms) / 1000.0
            )
            row[f"visual_opportunity_to_first_{aoi_name}_entry_s"] = (
                ""
                if first_entry_after_visual_opportunity is None
                else (
                    first_entry_after_visual_opportunity["pc_time_ms"]
                    - visual_opportunity_ms
                )
                / 1000.0
            )
            row[f"detectability_to_first_{aoi_name}_entry_s"] = (
                ""
                if first_entry_after_detectable is None
                else (first_entry_after_detectable["pc_time_ms"] - detectable_ms) / 1000.0
            )

        first_valid_after_advisory = first_gaze_after(gaze, advisory_ms, end_ms)
        first_valid_after_spawn = first_gaze_after(gaze, spawn_ms, end_ms)
        first_valid_after_visual_opportunity = first_gaze_after(
            gaze, visual_opportunity_ms, end_ms
        )
        first_valid_after_detectable = first_gaze_after(gaze, detectable_ms, end_ms)
        last_before_advisory = last_valid_gaze_before(gaze, advisory_ms, args.event_lookback_ms)
        last_before_spawn = last_valid_gaze_before(gaze, spawn_ms, args.event_lookback_ms)
        last_before_visual_opportunity = last_valid_gaze_before(
            gaze, visual_opportunity_ms, args.event_lookback_ms
        )
        last_before_detectable = last_valid_gaze_before(
            gaze, detectable_ms, args.event_lookback_ms
        )
        row["advisory_to_first_valid_gaze_s"] = (
            "" if first_valid_after_advisory is None else (first_valid_after_advisory["pc_time_ms"] - advisory_ms) / 1000.0
        )
        row["spawn_to_first_valid_gaze_s"] = (
            "" if first_valid_after_spawn is None else (first_valid_after_spawn["pc_time_ms"] - spawn_ms) / 1000.0
        )
        row["visual_opportunity_to_first_valid_gaze_s"] = (
            ""
            if first_valid_after_visual_opportunity is None
            else (
                first_valid_after_visual_opportunity["pc_time_ms"]
                - visual_opportunity_ms
            )
            / 1000.0
        )
        row["detectability_to_first_valid_gaze_s"] = (
            ""
            if first_valid_after_detectable is None
            else (first_valid_after_detectable["pc_time_ms"] - detectable_ms) / 1000.0
        )
        row["aoi_before_advisory"] = "" if last_before_advisory is None else last_before_advisory["aoi"]
        row["aoi_before_spawn"] = "" if last_before_spawn is None else last_before_spawn["aoi"]
        row["aoi_before_visual_opportunity"] = (
            ""
            if last_before_visual_opportunity is None
            else last_before_visual_opportunity["aoi"]
        )
        row["aoi_before_detectability"] = (
            "" if last_before_detectable is None else last_before_detectable["aoi"]
        )
        for aoi_name in TARGET_AOIS:
            row[f"already_{aoi_name}_before_advisory"] = 1 if row["aoi_before_advisory"] == aoi_name else 0
            row[f"already_{aoi_name}_before_spawn"] = 1 if row["aoi_before_spawn"] == aoi_name else 0
            row[f"already_{aoi_name}_before_visual_opportunity"] = (
                1 if row["aoi_before_visual_opportunity"] == aoi_name else 0
            )
            row[f"already_{aoi_name}_before_detectability"] = (
                1 if row["aoi_before_detectability"] == aoi_name else 0
            )

        trial_rows.append(row)

    trial_fields = list(trial_rows[0].keys()) if trial_rows else []
    trial_path = output_prefix.with_name(output_prefix.name + "_trial_gaze_summary.csv")
    if trial_rows:
        write_csv(trial_path, trial_rows, trial_fields)

    aggregate_rows = aggregate_trial_metrics(trial_rows)
    aggregate_fields = ["group", "clean_trial_count", "metric", "n", "mean", "median", "stdev", "min", "max"]
    aggregate_path = output_prefix.with_name(output_prefix.name + "_trial_aggregate_summary.csv")
    write_csv(aggregate_path, aggregate_rows, aggregate_fields)

    print("X-Plane + Tobii gaze analysis")
    print("Analyzer version:", ANALYZER_VERSION)
    print("Events:", args.events)
    print("Gaze:", args.gaze)
    print("AOI:", args.aoi)
    print("Layout:", args.layout_mode, f"{screen_width}x{args.screen_height}")
    print("Gaze rows:", len(gaze))
    print("Valid gaze rows:", len(valid_gaze), f"({len(valid_gaze) / len(gaze) * 100:.1f}%)" if gaze else "")
    print("Dwell threshold:", f"{args.dwell_ms:.0f} ms")
    print("Event lookback:", f"{args.event_lookback_ms:.0f} ms")
    if args.layout_mode == LAYOUT_SURROUND_WIDE:
        print(
            "Side rule:",
            f"guard={args.side_guard_px:.0f}px",
            f"dwell={args.side_dwell_ms:.0f}ms",
            f"loss={args.side_loss_min_ms:.0f}~{args.side_loss_max_ms:.0f}ms",
        )
        print(
            "External episodes:",
            sum(1 for episode in external_episodes if episode["episode_type"] == "MEASURED_SIDE"),
            "measured,",
            sum(1 for episode in external_episodes if episode["episode_type"] == "INFERRED_SIDE"),
            "inferred",
        )
    print("Wrote:", enriched_path)
    print("Wrote:", episode_path)
    print("Wrote:", trial_path)
    print("Wrote:", aggregate_path)
    print()
    print("Trial summary:")
    for row in trial_rows:
        print(
            "trial",
            row["trial_id"],
            "clean" if row["clean"] == 1 else "incomplete",
            "approach",
            row["approach"],
            "valid",
            f"{float(row['valid_gaze_rate']) * 100:.1f}%" if row["valid_gaze_rate"] != "" else "n/a",
            "adv->resp",
            fmt_latency(row, "advisory_to_response_s"),
            "spawn->resp",
            fmt_latency(row, "spawn_to_response_s"),
            "opportunity->resp",
            fmt_latency(row, "visual_opportunity_to_response_s"),
            row["response_vs_visual_opportunity"],
            "detectable->resp",
            fmt_latency(row, "detectability_to_response_s"),
            row["response_vs_detectability"],
            "spawn->operational-external",
            fmt_latency(row, "spawn_to_first_operational_external_s"),
            "opportunity->operational-external",
            fmt_latency(row, "visual_opportunity_to_first_operational_external_s"),
            "detectable->operational-external",
            fmt_latency(row, "detectability_to_first_operational_external_s"),
            "trial operational-external",
            f"{float(row['trial_operational_external_rate']) * 100:.1f}%" if row["trial_operational_external_rate"] != "" else "n/a",
            "before spawn",
            "yes" if row["operational_external_before_spawn"] == 1 else "no",
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
