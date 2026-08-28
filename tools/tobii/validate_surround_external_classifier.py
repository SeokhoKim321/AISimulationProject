import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

import analyze_xplane_tobii_session as analysis


VALIDATOR_VERSION = "260727_v1"
EPOCH_FIELDS = [
    "session_id",
    "epoch",
    "target_id",
    "true_monitor",
    "true_side",
    "predicted_side",
    "prediction_source",
    "predicted_direction",
    "direction_correct",
    "classification_correct",
]


def safe_rate(numerator, denominator):
    return numerator / denominator if denominator else None


def classify_epoch(rows):
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
            "Validate the coarse Surround side-external classifier against "
            "known target-monitor epochs."
        )
    )
    parser.add_argument("--gaze", action="append", required=True)
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
    args = parser.parse_args()

    expected_width = args.monitor_width * 3
    if args.screen_width != expected_width:
        parser.error(
            f"expected three {args.monitor_width}px monitors "
            f"({expected_width}px total), got {args.screen_width}px"
        )
    if args.center_monitor_index != 1:
        parser.error("the current three-monitor validator requires center index 1")

    aois = analysis.load_aois(args.aoi)
    epoch_rows = []
    for gaze_path in args.gaze:
        raw_rows = analysis.read_csv(gaze_path)
        enriched = analysis.enrich_gaze_rows(
            raw_rows,
            aois,
            args.screen_width,
            args.screen_height,
            args.panel_top_y,
            analysis.LAYOUT_SURROUND_WIDE,
            args.monitor_width,
            args.center_monitor_index,
            args.side_guard_px,
        )
        analysis.annotate_external_evidence(
            enriched,
            analysis.LAYOUT_SURROUND_WIDE,
            args.side_dwell_ms,
            args.side_loss_min_ms,
            args.side_loss_max_ms,
            args.max_sample_gap_ms,
        )

        by_epoch = defaultdict(list)
        for row in enriched:
            if not row.get("epoch") or row.get("phase") not in ("settle", "measure"):
                continue
            by_epoch[int(row["epoch"])].append(row)

        for epoch in sorted(by_epoch):
            rows = by_epoch[epoch]
            first = rows[0]
            true_monitor = first.get("target_monitor", "")
            if true_monitor not in ("left", "center", "right"):
                raise ValueError(
                    f"{gaze_path}: epoch {epoch} has invalid target_monitor={true_monitor!r}"
                )
            predicted_side, source, direction = classify_epoch(rows)
            true_side = true_monitor != "center"
            direction_correct = (
                int(direction.lower() == true_monitor)
                if direction in ("LEFT", "RIGHT") and true_side
                else ""
            )
            epoch_rows.append(
                {
                    "session_id": first.get("session_id", Path(gaze_path).stem),
                    "epoch": epoch,
                    "target_id": first.get("target_id", ""),
                    "true_monitor": true_monitor,
                    "true_side": int(true_side),
                    "predicted_side": int(predicted_side),
                    "prediction_source": source,
                    "predicted_direction": direction,
                    "direction_correct": direction_correct,
                    "classification_correct": int(true_side == predicted_side),
                }
            )

    true_positive = sum(
        1 for row in epoch_rows if row["true_side"] == 1 and row["predicted_side"] == 1
    )
    false_negative = sum(
        1 for row in epoch_rows if row["true_side"] == 1 and row["predicted_side"] == 0
    )
    false_positive = sum(
        1 for row in epoch_rows if row["true_side"] == 0 and row["predicted_side"] == 1
    )
    true_negative = sum(
        1 for row in epoch_rows if row["true_side"] == 0 and row["predicted_side"] == 0
    )
    direction_rows = [
        row for row in epoch_rows if row["direction_correct"] != ""
    ]
    direction_correct = sum(row["direction_correct"] for row in direction_rows)

    summary = {
        "validator_version": VALIDATOR_VERSION,
        "analyzer_version": analysis.ANALYZER_VERSION,
        "inputs": args.gaze,
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
        },
        "epoch_n": len(epoch_rows),
        "side_epoch_n": true_positive + false_negative,
        "center_epoch_n": true_negative + false_positive,
        "true_positive": true_positive,
        "false_negative": false_negative,
        "false_positive": false_positive,
        "true_negative": true_negative,
        "sensitivity": safe_rate(true_positive, true_positive + false_negative),
        "specificity": safe_rate(true_negative, true_negative + false_positive),
        "accuracy": safe_rate(
            true_positive + true_negative,
            true_positive + false_negative + false_positive + true_negative,
        ),
        "direction_evaluable_n": len(direction_rows),
        "direction_correct_n": direction_correct,
        "direction_accuracy_when_evaluable": safe_rate(
            direction_correct, len(direction_rows)
        ),
        "interpretation": (
            "Post-hoc single-participant target-task validation. "
            "Do not report these values as independent main-experiment accuracy."
        ),
    }

    output_prefix = Path(args.output_prefix)
    output_prefix.parent.mkdir(parents=True, exist_ok=True)
    epoch_path = output_prefix.with_name(output_prefix.name + "_epochs.csv")
    summary_path = output_prefix.with_name(output_prefix.name + "_summary.json")
    analysis.write_csv(epoch_path, epoch_rows, EPOCH_FIELDS)
    with summary_path.open("w", encoding="utf-8") as file:
        json.dump(summary, file, ensure_ascii=False, indent=2)

    print("Surround coarse external classifier validation")
    print("Epochs:", len(epoch_rows))
    print(
        "Confusion:",
        f"TP={true_positive}",
        f"FN={false_negative}",
        f"FP={false_positive}",
        f"TN={true_negative}",
    )
    print(
        "Sensitivity:",
        "n/a" if summary["sensitivity"] is None else f"{summary['sensitivity'] * 100:.1f}%",
    )
    print(
        "Specificity:",
        "n/a" if summary["specificity"] is None else f"{summary['specificity'] * 100:.1f}%",
    )
    print(
        "Direction when evaluable:",
        f"{direction_correct}/{len(direction_rows)}",
    )
    print("Wrote:", epoch_path)
    print("Wrote:", summary_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
