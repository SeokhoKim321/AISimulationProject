import argparse
import json
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path

import analyze_xplane_tobii_session as analysis


VALIDATOR_VERSION = "260728_v1"
DEFAULT_GAZE_FREQUENCY_HZ = 60.0
DEFAULT_MIN_SAMPLE_COVERAGE = 0.8
DEFAULT_MIN_SIDE_SENSITIVITY = 0.9
DEFAULT_MIN_CENTER_SPECIFICITY = 0.9
DEFAULT_MIN_CENTER_DWELL_RATE = 0.8
CUE_RESULT_FIELDS = [
    "cue_index",
    "cue_id",
    "cue_text",
    "target_class",
    "target_direction",
    "expected_aoi",
    "measure_start_ms",
    "measure_end_ms",
    "measure_duration_ms",
    "gaze_rows",
    "expected_gaze_rows",
    "sample_coverage",
    "valid_gaze_rows",
    "valid_gaze_rate",
    "measured_side_rows",
    "inferred_side_rows",
    "unresolved_loss_rows",
    "center_outside_rows",
    "expected_aoi_rows",
    "expected_aoi_rate_all",
    "expected_aoi_rate_valid",
    "side_detected",
    "prediction_source",
    "predicted_direction",
    "direction_evaluable",
    "direction_correct",
    "center_side_false_positive",
    "expected_aoi_dwell_success",
    "classification_success",
]


def safe_rate(numerator, denominator):
    return numerator / denominator if denominator else None


def unique_fields(fields):
    seen = set()
    result = []
    for field in fields:
        if field not in seen:
            seen.add(field)
            result.append(field)
    return result


def first_event(rows, event_type):
    for row in rows:
        if row.get("event_type") == event_type:
            return row
    return None


def build_cues(cue_events):
    by_index = defaultdict(list)
    for row in cue_events:
        cue_index = analysis.parse_int(row.get("cue_index"), 0)
        if cue_index > 0:
            by_index[cue_index].append(row)

    cues = []
    for cue_index in sorted(by_index):
        rows = by_index[cue_index]
        cue_start = first_event(rows, "CUE_START")
        measure_start = first_event(rows, "MEASURE_START")
        measure_end = first_event(rows, "MEASURE_END")
        cue_end = first_event(rows, "CUE_END")
        if not all((cue_start, measure_start, measure_end, cue_end)):
            raise ValueError(
                f"cue {cue_index} is incomplete; expected "
                "CUE_START/MEASURE_START/MEASURE_END/CUE_END"
            )
        metadata = cue_start
        start_ms = analysis.parse_float(measure_start.get("pc_time_ms"))
        end_ms = analysis.parse_float(measure_end.get("pc_time_ms"))
        if math.isnan(start_ms) or math.isnan(end_ms) or end_ms <= start_ms:
            raise ValueError(f"cue {cue_index} has invalid measurement timestamps")
        cues.append(
            {
                "cue_index": cue_index,
                "cue_id": metadata.get("cue_id", ""),
                "cue_text": metadata.get("cue_text", ""),
                "target_class": metadata.get("target_class", ""),
                "target_direction": metadata.get("target_direction", ""),
                "expected_aoi": metadata.get("expected_aoi", ""),
                "measure_start_ms": start_ms,
                "measure_end_ms": end_ms,
            }
        )
    return cues


def classify_side_window(rows):
    measured = [
        row
        for row in rows
        if row["external_evidence"] in analysis.MEASURED_SIDE_EVIDENCE
    ]
    inferred = [
        row
        for row in rows
        if row["external_evidence"] in analysis.INFERRED_SIDE_EVIDENCE
    ]
    if measured:
        directions = Counter(row["external_direction"] for row in measured)
        return True, "MEASURED_SIDE", directions.most_common(1)[0][0]
    if inferred:
        return True, "INFERRED_SIDE", "UNKNOWN"
    return False, "NONE", ""


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Analyze timestamped cockpit audio cues with a Tobii Surround gaze log."
        )
    )
    parser.add_argument("--cues", required=True)
    parser.add_argument("--gaze", required=True)
    parser.add_argument("--output-prefix", required=True)
    parser.add_argument("--aoi", default=analysis.DEFAULT_AOI_PATH)
    parser.add_argument("--screen-width", type=int, default=5760)
    parser.add_argument("--screen-height", type=int, default=1080)
    parser.add_argument("--monitor-width", type=int, default=1920)
    parser.add_argument("--center-monitor-index", type=int, default=1)
    parser.add_argument("--panel-top-y", type=float, default=600.0)
    parser.add_argument("--side-guard-px", type=float, default=120.0)
    parser.add_argument("--side-dwell-ms", type=float, default=200.0)
    parser.add_argument("--side-loss-min-ms", type=float, default=500.0)
    parser.add_argument("--side-loss-max-ms", type=float, default=5000.0)
    parser.add_argument("--max-sample-gap-ms", type=float, default=100.0)
    parser.add_argument("--aoi-dwell-ms", type=float, default=200.0)
    parser.add_argument("--gaze-frequency-hz", type=float, default=DEFAULT_GAZE_FREQUENCY_HZ)
    parser.add_argument("--min-sample-coverage", type=float, default=DEFAULT_MIN_SAMPLE_COVERAGE)
    parser.add_argument("--min-side-sensitivity", type=float, default=DEFAULT_MIN_SIDE_SENSITIVITY)
    parser.add_argument("--min-center-specificity", type=float, default=DEFAULT_MIN_CENTER_SPECIFICITY)
    parser.add_argument("--min-center-dwell-rate", type=float, default=DEFAULT_MIN_CENTER_DWELL_RATE)
    args = parser.parse_args()

    if args.screen_width != args.monitor_width * 3:
        parser.error("screen width must equal three physical monitor widths")
    if args.center_monitor_index != 1:
        parser.error("the current cockpit validator requires center monitor index 1")
    if args.gaze_frequency_hz <= 0 or args.aoi_dwell_ms < 0:
        parser.error("gaze frequency must be positive and AOI dwell non-negative")
    for name in (
        "min_sample_coverage",
        "min_side_sensitivity",
        "min_center_specificity",
        "min_center_dwell_rate",
    ):
        value = getattr(args, name)
        if not 0 <= value <= 1:
            parser.error(f"--{name.replace('_', '-')} must be between 0 and 1")

    cue_events = analysis.read_csv(args.cues)
    gaze_raw = analysis.read_csv(args.gaze)
    cues = build_cues(cue_events)
    if not cues:
        raise ValueError("no complete cues found")

    aois = analysis.load_aois(args.aoi)
    gaze = analysis.enrich_gaze_rows(
        gaze_raw,
        aois,
        args.screen_width,
        args.screen_height,
        args.panel_top_y,
        analysis.LAYOUT_SURROUND_WIDE,
        args.monitor_width,
        args.center_monitor_index,
        args.side_guard_px,
    )
    episodes = analysis.annotate_external_evidence(
        gaze,
        analysis.LAYOUT_SURROUND_WIDE,
        args.side_dwell_ms,
        args.side_loss_min_ms,
        args.side_loss_max_ms,
        args.max_sample_gap_ms,
    )

    cue_results = []
    for cue in cues:
        start_ms = cue["measure_start_ms"]
        end_ms = cue["measure_end_ms"]
        rows = [
            row for row in gaze if start_ms <= row["pc_time_ms"] < end_ms
        ]
        valid_rows = [row for row in rows if row["valid_gaze"] == 1]
        measured_rows = [
            row
            for row in rows
            if row["external_evidence"] in analysis.MEASURED_SIDE_EVIDENCE
        ]
        inferred_rows = [
            row
            for row in rows
            if row["external_evidence"] in analysis.INFERRED_SIDE_EVIDENCE
        ]
        unresolved_rows = [
            row
            for row in rows
            if row["external_evidence"] == "UNRESOLVED_TRACKING_LOSS"
        ]
        center_outside_rows = [
            row
            for row in rows
            if row["external_evidence"] == "CENTER_OUTSIDE_MEASURED"
        ]
        expected_aoi = cue["expected_aoi"]
        expected_aoi_rows = [
            row for row in rows if expected_aoi and row["aoi"] == expected_aoi
        ]
        side_detected, prediction_source, predicted_direction = classify_side_window(rows)
        target_class = cue["target_class"]
        target_direction = cue["target_direction"]
        expected_rows = (
            (end_ms - start_ms) / 1000.0 * args.gaze_frequency_hz
        )
        sample_coverage = safe_rate(len(rows), expected_rows)
        aoi_dwell = (
            analysis.first_dwell_after(
                gaze,
                start_ms,
                end_ms,
                expected_aoi,
                args.aoi_dwell_ms,
            )
            if expected_aoi
            else None
        )
        direction_evaluable = int(predicted_direction in ("LEFT", "RIGHT"))
        direction_correct = (
            int(predicted_direction == target_direction)
            if direction_evaluable and target_class == "SIDE_EXTERNAL"
            else ""
        )
        center_side_false_positive = (
            int(side_detected) if target_class != "SIDE_EXTERNAL" else ""
        )
        expected_aoi_dwell_success = int(aoi_dwell is not None) if expected_aoi else ""

        if target_class == "SIDE_EXTERNAL":
            classification_success = int(side_detected)
        elif expected_aoi:
            classification_success = int(not side_detected and aoi_dwell is not None)
        else:
            classification_success = int(not side_detected)

        cue_results.append(
            {
                **cue,
                "measure_duration_ms": end_ms - start_ms,
                "gaze_rows": len(rows),
                "expected_gaze_rows": expected_rows,
                "sample_coverage": sample_coverage,
                "valid_gaze_rows": len(valid_rows),
                "valid_gaze_rate": safe_rate(len(valid_rows), len(rows)),
                "measured_side_rows": len(measured_rows),
                "inferred_side_rows": len(inferred_rows),
                "unresolved_loss_rows": len(unresolved_rows),
                "center_outside_rows": len(center_outside_rows),
                "expected_aoi_rows": len(expected_aoi_rows),
                "expected_aoi_rate_all": safe_rate(len(expected_aoi_rows), len(rows)),
                "expected_aoi_rate_valid": safe_rate(
                    len(expected_aoi_rows), len(valid_rows)
                ),
                "side_detected": int(side_detected),
                "prediction_source": prediction_source,
                "predicted_direction": predicted_direction,
                "direction_evaluable": direction_evaluable,
                "direction_correct": direction_correct,
                "center_side_false_positive": center_side_false_positive,
                "expected_aoi_dwell_success": expected_aoi_dwell_success,
                "classification_success": classification_success,
            }
        )

    side_rows = [
        row for row in cue_results if row["target_class"] == "SIDE_EXTERNAL"
    ]
    center_rows = [
        row for row in cue_results if row["target_class"] != "SIDE_EXTERNAL"
    ]
    center_aoi_rows = [row for row in center_rows if row["expected_aoi"]]
    direction_rows = [
        row for row in side_rows if row["direction_evaluable"] == 1
    ]
    coverage_complete = all(
        row["sample_coverage"] is not None
        and row["sample_coverage"] >= args.min_sample_coverage
        for row in cue_results
    )
    side_sensitivity = safe_rate(
        sum(row["side_detected"] for row in side_rows), len(side_rows)
    )
    center_specificity = safe_rate(
        sum(1 - row["center_side_false_positive"] for row in center_rows),
        len(center_rows),
    )
    center_dwell_rate = safe_rate(
        sum(row["expected_aoi_dwell_success"] for row in center_aoi_rows),
        len(center_aoi_rows),
    )
    direction_accuracy = safe_rate(
        sum(row["direction_correct"] for row in direction_rows),
        len(direction_rows),
    )
    pilot_pass = (
        coverage_complete
        and side_sensitivity is not None
        and center_specificity is not None
        and center_dwell_rate is not None
        and side_sensitivity >= args.min_side_sensitivity
        and center_specificity >= args.min_center_specificity
        and center_dwell_rate >= args.min_center_dwell_rate
    )
    status = "PILOT_PASS" if pilot_pass else (
        "DATA_INCOMPLETE" if not coverage_complete else "PILOT_FAIL"
    )

    cue_start_ms = min(cue["measure_start_ms"] for cue in cues)
    cue_end_ms = max(cue["measure_end_ms"] for cue in cues)
    validation_gaze = [
        row
        for row in gaze
        if cue_start_ms - 1000.0 <= row["pc_time_ms"] <= cue_end_ms + 1000.0
    ]
    validation_episodes = [
        episode
        for episode in episodes
        if episode["end_ms"] >= cue_start_ms
        and episode["start_ms"] <= cue_end_ms
    ]

    summary = {
        "validator_version": VALIDATOR_VERSION,
        "analyzer_version": analysis.ANALYZER_VERSION,
        "status": status,
        "pilot_pass": pilot_pass,
        "inputs": {
            "cues": args.cues,
            "gaze": args.gaze,
            "aoi": args.aoi,
        },
        "parameters": {
            "screen_width": args.screen_width,
            "screen_height": args.screen_height,
            "monitor_width": args.monitor_width,
            "center_monitor_index": args.center_monitor_index,
            "panel_top_y": args.panel_top_y,
            "side_guard_px": args.side_guard_px,
            "side_dwell_ms": args.side_dwell_ms,
            "side_loss_min_ms": args.side_loss_min_ms,
            "side_loss_max_ms": args.side_loss_max_ms,
            "max_sample_gap_ms": args.max_sample_gap_ms,
            "aoi_dwell_ms": args.aoi_dwell_ms,
            "gaze_frequency_hz": args.gaze_frequency_hz,
        },
        "thresholds": {
            "min_sample_coverage": args.min_sample_coverage,
            "min_side_sensitivity": args.min_side_sensitivity,
            "min_center_specificity": args.min_center_specificity,
            "min_center_dwell_rate": args.min_center_dwell_rate,
        },
        "cue_n": len(cue_results),
        "side_cue_n": len(side_rows),
        "center_cue_n": len(center_rows),
        "center_aoi_cue_n": len(center_aoi_rows),
        "coverage_complete": coverage_complete,
        "minimum_cue_sample_coverage": min(
            row["sample_coverage"] for row in cue_results
            if row["sample_coverage"] is not None
        ),
        "side_detected_n": sum(row["side_detected"] for row in side_rows),
        "side_sensitivity": side_sensitivity,
        "center_false_positive_n": sum(
            row["center_side_false_positive"] for row in center_rows
        ),
        "center_specificity": center_specificity,
        "center_aoi_dwell_success_n": sum(
            row["expected_aoi_dwell_success"] for row in center_aoi_rows
        ),
        "center_aoi_dwell_rate": center_dwell_rate,
        "direction_evaluable_n": len(direction_rows),
        "direction_correct_n": sum(
            row["direction_correct"] for row in direction_rows
        ),
        "direction_accuracy_when_evaluable": direction_accuracy,
        "mean_valid_gaze_rate": statistics.fmean(
            row["valid_gaze_rate"]
            for row in cue_results
            if row["valid_gaze_rate"] is not None
        ),
        "interpretation": (
            "Pilot setup validation only. Inferred side evidence has no "
            "left/right direction and is not a precise gaze coordinate."
        ),
    }

    output_prefix = Path(args.output_prefix)
    output_prefix.parent.mkdir(parents=True, exist_ok=True)
    cue_result_path = output_prefix.with_name(output_prefix.name + "_cue_results.csv")
    summary_path = output_prefix.with_name(output_prefix.name + "_summary.json")
    gaze_path = output_prefix.with_name(output_prefix.name + "_gaze_classified.csv")
    episode_path = output_prefix.with_name(output_prefix.name + "_external_episodes.csv")
    analysis.write_csv(cue_result_path, cue_results, CUE_RESULT_FIELDS)
    with summary_path.open("w", encoding="utf-8") as file:
        json.dump(summary, file, ensure_ascii=False, indent=2)
    gaze_fields = unique_fields(
        list(gaze_raw[0].keys())
        + [
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
    )
    analysis.write_csv(gaze_path, validation_gaze, gaze_fields)
    analysis.write_csv(
        episode_path,
        validation_episodes,
        analysis.EXTERNAL_EPISODE_FIELDS,
    )

    print("Surround cockpit audio-cue validation")
    print("Status:", status)
    print(
        "Side:",
        f"{summary['side_detected_n']}/{summary['side_cue_n']}",
        f"({side_sensitivity * 100:.1f}%)",
    )
    print(
        "Center specificity:",
        f"{len(center_rows) - summary['center_false_positive_n']}/{len(center_rows)}",
        f"({center_specificity * 100:.1f}%)",
    )
    print(
        "Center AOI dwell:",
        f"{summary['center_aoi_dwell_success_n']}/{summary['center_aoi_cue_n']}",
        f"({center_dwell_rate * 100:.1f}%)",
    )
    print(
        "Direction when evaluable:",
        f"{summary['direction_correct_n']}/{summary['direction_evaluable_n']}",
    )
    print("Wrote:", cue_result_path)
    print("Wrote:", summary_path)
    print("Wrote:", gaze_path)
    print("Wrote:", episode_path)
    return 0 if pilot_pass else 5


if __name__ == "__main__":
    raise SystemExit(main())
