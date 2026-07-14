import argparse
import csv
import math
from collections import defaultdict
from pathlib import Path


DEFAULT_SCREEN_WIDTH = 1920
DEFAULT_SCREEN_HEIGHT = 1080
DEFAULT_AOI_PATH = "resources/cessna_instrument_aoi_260710.csv"
INSTRUMENT_AOIS = ["AIRSPEED", "ATTITUDE", "ALTITUDE", "HEADING", "VERTICAL_SPEED", "NAV_GPS"]
TARGET_AOIS = ["OUTSIDE"] + INSTRUMENT_AOIS


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


def classify_aoi(px, py, aois):
    if math.isnan(px) or math.isnan(py):
        return "INVALID"
    for aoi in aois:
        if aoi["x1"] <= px <= aoi["x2"] and aoi["y1"] <= py <= aoi["y2"]:
            return aoi["name"]
    return "OUTSIDE"


def first_event(events, event_type):
    for event in events:
        if event.get("event_type") == event_type:
            return event
    return None


def event_ms(event):
    if not event:
        return math.nan
    return parse_float(event.get("recorded_timestamp_ms"))


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


def enrich_gaze_rows(raw_rows, aois, screen_width, screen_height):
    enriched = []
    for row in raw_rows:
        avg_x = parse_float(row.get("avg_gaze_x"))
        avg_y = parse_float(row.get("avg_gaze_y"))
        left_valid = parse_int(row.get("left_validity"))
        right_valid = parse_int(row.get("right_validity"))
        valid = (left_valid == 1 or right_valid == 1) and not math.isnan(avg_x) and not math.isnan(avg_y)
        px = avg_x * screen_width if valid else math.nan
        py = avg_y * screen_height if valid else math.nan
        aoi = classify_aoi(px, py, aois) if valid else "INVALID"

        out = dict(row)
        out["pc_time_ms"] = parse_float(row.get("pc_time_sec")) * 1000.0
        out["gaze_px"] = px
        out["gaze_py"] = py
        out["valid_gaze"] = 1 if valid else 0
        out["aoi"] = aoi
        enriched.append(out)
    return enriched


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


def main():
    parser = argparse.ArgumentParser(description="Analyze X-Plane events with Tobii gaze CSV.")
    parser.add_argument("--events", required=True)
    parser.add_argument("--gaze", required=True)
    parser.add_argument("--aoi", default=DEFAULT_AOI_PATH)
    parser.add_argument("--output-prefix", required=True)
    parser.add_argument("--screen-width", type=int, default=DEFAULT_SCREEN_WIDTH)
    parser.add_argument("--screen-height", type=int, default=DEFAULT_SCREEN_HEIGHT)
    parser.add_argument("--dwell-ms", type=float, default=200.0)
    parser.add_argument("--event-lookback-ms", type=float, default=500.0)
    args = parser.parse_args()

    events = read_csv(args.events)
    gaze_raw = read_csv(args.gaze)
    aois = load_aois(args.aoi)
    gaze = enrich_gaze_rows(gaze_raw, aois, args.screen_width, args.screen_height)
    valid_gaze = [row for row in gaze if row["valid_gaze"] == 1]

    events_by_trial = defaultdict(list)
    for event in events:
        events_by_trial[event.get("trial_id", "")].append(event)

    output_prefix = Path(args.output_prefix)
    output_prefix.parent.mkdir(parents=True, exist_ok=True)

    enriched_fields = list(gaze_raw[0].keys()) + ["pc_time_ms", "gaze_px", "gaze_py", "valid_gaze", "aoi"]
    enriched_path = output_prefix.with_name(output_prefix.name + "_gaze_aoi.csv")
    write_csv(enriched_path, gaze, enriched_fields)

    trial_rows = []
    for trial_id in sorted(events_by_trial, key=lambda value: int(value) if value.isdigit() else -1):
        if trial_id in ("", "0"):
            continue

        trial_events = events_by_trial[trial_id]
        start = first_event(trial_events, "TRIAL_START")
        end = first_event(trial_events, "TRIAL_END")
        spawn = first_event(trial_events, "INTRUDER_SPAWNED")
        advisory = first_event(trial_events, "ADVISORY_SHOWN")
        response = first_event(trial_events, "PILOT_RESPONSE_START")

        start_ms = event_ms(start)
        end_ms = event_ms(end)
        if math.isnan(end_ms) and trial_events:
            end_ms = event_ms(trial_events[-1])

        trial_gaze, trial_valid = count_gaze_in_window(gaze, start_ms, end_ms) if not math.isnan(start_ms) else ([], [])

        row = {
            "trial_id": trial_id,
            "has_trial_start": 1 if start else 0,
            "has_trial_end": 1 if end else 0,
            "has_intruder_spawned": 1 if spawn else 0,
            "has_advisory_shown": 1 if advisory else 0,
            "has_pilot_response_start": 1 if response else 0,
            "trial_start_ms": "" if math.isnan(start_ms) else int(start_ms),
            "trial_end_ms": "" if math.isnan(end_ms) else int(end_ms),
            "trial_duration_s": "" if math.isnan(start_ms) or math.isnan(end_ms) else (end_ms - start_ms) / 1000.0,
            "gaze_rows": len(trial_gaze),
            "valid_gaze_rows": len(trial_valid),
            "valid_gaze_rate": (len(trial_valid) / len(trial_gaze)) if trial_gaze else "",
        }

        spawn_ms = event_ms(spawn)
        advisory_ms = event_ms(advisory)
        response_ms = event_ms(response)
        if not math.isnan(spawn_ms) and not math.isnan(advisory_ms):
            row["spawn_to_advisory_s"] = (advisory_ms - spawn_ms) / 1000.0
        else:
            row["spawn_to_advisory_s"] = ""
        if not math.isnan(advisory_ms) and not math.isnan(response_ms):
            row["advisory_to_response_s"] = (response_ms - advisory_ms) / 1000.0
        else:
            row["advisory_to_response_s"] = ""

        for aoi_name in TARGET_AOIS:
            first_after_advisory = first_gaze_after(gaze, advisory_ms, end_ms, aoi_name)
            first_after_spawn = first_gaze_after(gaze, spawn_ms, end_ms, aoi_name)
            first_dwell_after_advisory = first_dwell_after(gaze, advisory_ms, end_ms, aoi_name, args.dwell_ms)
            first_dwell_after_spawn = first_dwell_after(gaze, spawn_ms, end_ms, aoi_name, args.dwell_ms)
            first_entry_after_advisory = first_entry_after(gaze, advisory_ms, end_ms, aoi_name, args.event_lookback_ms)
            first_entry_after_spawn = first_entry_after(gaze, spawn_ms, end_ms, aoi_name, args.event_lookback_ms)
            row[f"advisory_to_first_{aoi_name}_gaze_s"] = (
                "" if first_after_advisory is None else (first_after_advisory["pc_time_ms"] - advisory_ms) / 1000.0
            )
            row[f"spawn_to_first_{aoi_name}_gaze_s"] = (
                "" if first_after_spawn is None else (first_after_spawn["pc_time_ms"] - spawn_ms) / 1000.0
            )
            row[f"advisory_to_first_{aoi_name}_dwell_s"] = (
                "" if first_dwell_after_advisory is None else (first_dwell_after_advisory["pc_time_ms"] - advisory_ms) / 1000.0
            )
            row[f"spawn_to_first_{aoi_name}_dwell_s"] = (
                "" if first_dwell_after_spawn is None else (first_dwell_after_spawn["pc_time_ms"] - spawn_ms) / 1000.0
            )
            row[f"advisory_to_first_{aoi_name}_entry_s"] = (
                "" if first_entry_after_advisory is None else (first_entry_after_advisory["pc_time_ms"] - advisory_ms) / 1000.0
            )
            row[f"spawn_to_first_{aoi_name}_entry_s"] = (
                "" if first_entry_after_spawn is None else (first_entry_after_spawn["pc_time_ms"] - spawn_ms) / 1000.0
            )

        first_valid_after_advisory = first_gaze_after(gaze, advisory_ms, end_ms)
        first_valid_after_spawn = first_gaze_after(gaze, spawn_ms, end_ms)
        last_before_advisory = last_valid_gaze_before(gaze, advisory_ms, args.event_lookback_ms)
        last_before_spawn = last_valid_gaze_before(gaze, spawn_ms, args.event_lookback_ms)
        row["advisory_to_first_valid_gaze_s"] = (
            "" if first_valid_after_advisory is None else (first_valid_after_advisory["pc_time_ms"] - advisory_ms) / 1000.0
        )
        row["spawn_to_first_valid_gaze_s"] = (
            "" if first_valid_after_spawn is None else (first_valid_after_spawn["pc_time_ms"] - spawn_ms) / 1000.0
        )
        row["aoi_before_advisory"] = "" if last_before_advisory is None else last_before_advisory["aoi"]
        row["aoi_before_spawn"] = "" if last_before_spawn is None else last_before_spawn["aoi"]
        for aoi_name in TARGET_AOIS:
            row[f"already_{aoi_name}_before_advisory"] = 1 if row["aoi_before_advisory"] == aoi_name else 0
            row[f"already_{aoi_name}_before_spawn"] = 1 if row["aoi_before_spawn"] == aoi_name else 0

        trial_rows.append(row)

    trial_fields = list(trial_rows[0].keys()) if trial_rows else []
    trial_path = output_prefix.with_name(output_prefix.name + "_trial_gaze_summary.csv")
    if trial_rows:
        write_csv(trial_path, trial_rows, trial_fields)

    print("X-Plane + Tobii gaze analysis")
    print("Events:", args.events)
    print("Gaze:", args.gaze)
    print("AOI:", args.aoi)
    print("Gaze rows:", len(gaze))
    print("Valid gaze rows:", len(valid_gaze), f"({len(valid_gaze) / len(gaze) * 100:.1f}%)" if gaze else "")
    print("Dwell threshold:", f"{args.dwell_ms:.0f} ms")
    print("Event lookback:", f"{args.event_lookback_ms:.0f} ms")
    print("Wrote:", enriched_path)
    print("Wrote:", trial_path)
    print()
    print("Trial summary:")
    for row in trial_rows:
        print(
            "trial",
            row["trial_id"],
            "valid",
            f"{float(row['valid_gaze_rate']) * 100:.1f}%" if row["valid_gaze_rate"] != "" else "n/a",
            "adv->resp",
            fmt_latency(row, "advisory_to_response_s"),
            "adv->outside",
            fmt_latency(row, "advisory_to_first_OUTSIDE_gaze_s"),
            "adv->outside dwell",
            fmt_latency(row, "advisory_to_first_OUTSIDE_dwell_s"),
            "adv->outside entry",
            fmt_latency(row, "advisory_to_first_OUTSIDE_entry_s"),
            "before adv",
            row["aoi_before_advisory"] or "n/a",
            "spawn->outside",
            fmt_latency(row, "spawn_to_first_OUTSIDE_gaze_s"),
        )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
