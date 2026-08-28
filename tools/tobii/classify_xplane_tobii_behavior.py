import argparse
import csv
import math
from collections import Counter, defaultdict
from pathlib import Path


CLASSIFIER_VERSION = "260804_behavior_evidence_v1"
PRE_RESPONSE_OFFSET_S = -0.5
POST_RESPONSE_OFFSET_S = 3.0
MIN_SPEED_ERROR_KIAS = 2.0
MIN_SPEED_ERROR_REDUCTION_KIAS = 1.0
MAX_PRE_RESPONSE_CPA_3D_M = 60.0
MIN_CPA_3D_IMPROVEMENT_M = 10.0
MIN_POST_RESPONSE_CPA_3D_M = 20.0
EXTERNAL_TIMING_TOLERANCE_S = 0.1
MAX_NEAREST_SAMPLE_GAP_S = 0.25

NO_VALID_RESPONSE = "NO_VALID_RESPONSE"
SPEED_TASK_CORRECTION = "SPEED_TASK_CORRECTION"
AVOIDANCE_CANDIDATE = "AVOIDANCE_CANDIDATE"
AMBIGUOUS = "AMBIGUOUS"


def parse_args():
    parser = argparse.ArgumentParser(
        description=(
            "Classify X-Plane trial control responses using speed-task, "
            "3-D projected-CPA, and Tobii external-view evidence."
        )
    )
    parser.add_argument("--state", required=True)
    parser.add_argument("--intruder", required=True)
    parser.add_argument("--events", required=True)
    parser.add_argument("--gaze-summary", required=True)
    parser.add_argument("--output", required=True)
    return parser.parse_args()


def read_csv(path):
    with open(path, "r", newline="", encoding="utf-8-sig") as stream:
        return list(csv.DictReader(stream))


def parse_float(value):
    if value is None or str(value).strip() == "":
        return None
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) else None


def parse_int(value):
    number = parse_float(value)
    return None if number is None else int(number)


def parse_bool(value):
    if value is None:
        return None
    normalized = str(value).strip().lower()
    if normalized in {"1", "true", "yes"}:
        return True
    if normalized in {"0", "false", "no"}:
        return False
    return None


def detail_values(detail):
    values = {}
    for part in (detail or "").split(";"):
        key, separator, value = part.partition("=")
        if separator:
            values[key.strip()] = value.strip()
    return values


def group_by_trial(rows):
    grouped = defaultdict(list)
    for row in rows:
        trial_id = parse_int(row.get("trial_id"))
        if trial_id is not None and trial_id > 0:
            grouped[trial_id].append(row)
    return grouped


def first_event(events, event_type):
    return next((row for row in events if row.get("event_type") == event_type), None)


def event_time(event):
    return parse_float(event.get("xplane_sim_time_s")) if event else None


def nearest_row(rows, target_time_s):
    if target_time_s is None:
        return None
    candidates = []
    for row in rows:
        sim_time_s = parse_float(row.get("sim_time_s"))
        if sim_time_s is not None:
            candidates.append((abs(sim_time_s - target_time_s), row))
    if not candidates:
        return None
    nearest_delta_s, nearest = min(candidates, key=lambda item: item[0])
    return nearest if nearest_delta_s <= MAX_NEAREST_SAMPLE_GAP_S else None


def projected_cpa_3d(state, intruder, trial_end_time_s):
    if state is None or intruder is None or trial_end_time_s is None:
        return None, None
    sample_time_s = parse_float(state.get("sim_time_s"))
    if sample_time_s is None:
        return None, None
    horizon_s = max(0.0, trial_end_time_s - sample_time_s)
    position = []
    velocity = []
    for axis in ("x", "y", "z"):
        intruder_position = parse_float(intruder.get(f"intruder_{axis}"))
        ownship_position = parse_float(intruder.get(f"ownship_{axis}"))
        intruder_velocity = parse_float(intruder.get(f"intruder_v{axis}"))
        ownship_velocity = parse_float(state.get(f"v{axis}"))
        if None in (
            intruder_position,
            ownship_position,
            intruder_velocity,
            ownship_velocity,
        ):
            return None, None
        position.append(intruder_position - ownship_position)
        velocity.append(intruder_velocity - ownship_velocity)

    velocity_squared = sum(value * value for value in velocity)
    if velocity_squared <= 1e-9:
        cpa_time_s = 0.0
    else:
        cpa_time_s = -sum(
            position[index] * velocity[index] for index in range(3)
        ) / velocity_squared
        cpa_time_s = min(horizon_s, max(0.0, cpa_time_s))
    cpa_distance_m = math.sqrt(
        sum(
            (position[index] + velocity[index] * cpa_time_s) ** 2
            for index in range(3)
        )
    )
    return cpa_distance_m, cpa_time_s


def observed_minimum_3d(intruder_rows, response_time_s):
    best = None
    for row in intruder_rows:
        sim_time_s = parse_float(row.get("sim_time_s"))
        horizontal_m = parse_float(row.get("horizontal_distance"))
        vertical_m = parse_float(row.get("vertical_separation"))
        if (
            sim_time_s is None
            or horizontal_m is None
            or vertical_m is None
            or response_time_s is None
            or sim_time_s < response_time_s
        ):
            continue
        distance_3d_m = math.hypot(horizontal_m, vertical_m)
        candidate = (distance_3d_m, horizontal_m, vertical_m, sim_time_s)
        if best is None or candidate[0] < best[0]:
            best = candidate
    return best or (None, None, None, None)


def speed_task_evidence(
    target_speed_kias,
    ias_before_kias,
    ias_after_kias,
    trigger_axis,
    throttle_delta,
):
    if None in (target_speed_kias, ias_before_kias, ias_after_kias):
        return False, None, None, None, False
    error_before = ias_before_kias - target_speed_kias
    error_after = ias_after_kias - target_speed_kias
    error_reduction = abs(error_before) - abs(error_after)
    throttle_direction_consistent = False
    if trigger_axis == "throttle" and throttle_delta is not None:
        throttle_direction_consistent = (
            (error_before > 0.0 and throttle_delta < -0.05)
            or (error_before < 0.0 and throttle_delta > 0.05)
        )
    elif trigger_axis == "pitch":
        # For non-throttle triggers, direction is not inferred from control sign.
        # Only the observed speed-error reduction is used as task-consistent evidence.
        throttle_direction_consistent = True

    evidence = (
        abs(error_before) >= MIN_SPEED_ERROR_KIAS
        and error_reduction >= MIN_SPEED_ERROR_REDUCTION_KIAS
        and throttle_direction_consistent
    )
    return (
        evidence,
        error_before,
        error_after,
        error_reduction,
        throttle_direction_consistent,
    )


def avoidance_kinematic_evidence(cpa_before_m, cpa_after_m):
    if cpa_before_m is None or cpa_after_m is None:
        return False, None
    improvement_m = cpa_after_m - cpa_before_m
    evidence = (
        cpa_before_m <= MAX_PRE_RESPONSE_CPA_3D_M
        and cpa_after_m >= MIN_POST_RESPONSE_CPA_3D_M
        and improvement_m >= MIN_CPA_3D_IMPROVEMENT_M
    )
    return evidence, improvement_m


def classify_behavior(
    recording_clean,
    response_present,
    baseline_valid,
    speed_evidence,
    avoidance_evidence,
):
    if not recording_clean:
        return NO_VALID_RESPONSE, "RECORDING_INCOMPLETE"
    if not response_present:
        return NO_VALID_RESPONSE, "PILOT_RESPONSE_START_MISSING"
    if baseline_valid is not True:
        return NO_VALID_RESPONSE, "RESPONSE_BASELINE_INVALID"
    if speed_evidence and avoidance_evidence:
        return AMBIGUOUS, "DUAL_SPEED_AND_AVOIDANCE_EVIDENCE"
    if speed_evidence:
        return SPEED_TASK_CORRECTION, "SPEED_ERROR_REDUCTION_WITHOUT_AVOIDANCE_EVIDENCE"
    if avoidance_evidence:
        return AVOIDANCE_CANDIDATE, "CPA_IMPROVEMENT_WITH_EXTERNAL_VIEW_BEFORE_RESPONSE"
    return AMBIGUOUS, "CONTROL_CHANGE_WITHOUT_SPECIFIC_PURPOSE_EVIDENCE"


def build_trial_result(trial_id, state_rows, intruder_rows, events, gaze):
    trial_start = first_event(events, "TRIAL_START")
    opportunity = first_event(events, "INTRUDER_VISUAL_OPPORTUNITY_ONSET")
    response = first_event(events, "PILOT_RESPONSE_START")
    baseline = first_event(events, "PILOT_RESPONSE_BASELINE")
    trial_end = first_event(events, "TRIAL_END")
    start_detail = detail_values(trial_start.get("detail", "") if trial_start else "")
    response_detail = detail_values(response.get("detail", "") if response else "")
    baseline_detail = detail_values(baseline.get("detail", "") if baseline else "")

    response_time_s = event_time(response)
    opportunity_time_s = event_time(opportunity)
    trial_end_time_s = event_time(trial_end)
    before_time_s = (
        None if response_time_s is None else response_time_s + PRE_RESPONSE_OFFSET_S
    )
    after_time_s = (
        None if response_time_s is None else response_time_s + POST_RESPONSE_OFFSET_S
    )
    state_before = nearest_row(state_rows, before_time_s)
    state_after = nearest_row(state_rows, after_time_s)
    intruder_before = nearest_row(intruder_rows, before_time_s)
    intruder_after = nearest_row(intruder_rows, after_time_s)
    cpa_before_m, cpa_before_time_s = projected_cpa_3d(
        state_before, intruder_before, trial_end_time_s
    )
    cpa_after_m, cpa_after_time_s = projected_cpa_3d(
        state_after, intruder_after, trial_end_time_s
    )

    target_speed_kias = parse_float(start_detail.get("target_speed_kias"))
    ias_before_kias = parse_float(state_before.get("ias_mps")) if state_before else None
    ias_after_kias = parse_float(state_after.get("ias_mps")) if state_after else None
    trigger_axis = response_detail.get("trigger_axis", "")
    throttle_delta = parse_float(response_detail.get("throttle_delta"))
    pitch_delta = parse_float(response_detail.get("pitch_delta"))
    roll_delta = parse_float(response_detail.get("roll_delta"))
    yaw_delta = parse_float(response_detail.get("yaw_delta"))
    (
        has_speed_evidence,
        speed_error_before_kias,
        speed_error_after_kias,
        speed_error_reduction_kias,
        throttle_direction_consistent,
    ) = speed_task_evidence(
        target_speed_kias,
        ias_before_kias,
        ias_after_kias,
        trigger_axis,
        throttle_delta,
    )
    has_kinematic_evidence, cpa_improvement_m = avoidance_kinematic_evidence(
        cpa_before_m, cpa_after_m
    )

    response_latency_s = (
        None
        if response_time_s is None or opportunity_time_s is None
        else response_time_s - opportunity_time_s
    )
    first_external_s = parse_float(
        gaze.get("visual_opportunity_to_first_operational_external_s")
    )
    external_before_response = (
        first_external_s is not None
        and response_latency_s is not None
        and first_external_s <= response_latency_s + EXTERNAL_TIMING_TOLERANCE_S
    )
    active_control_at_anchor = parse_bool(
        gaze.get("active_control_at_visual_opportunity")
    )
    has_avoidance_evidence = (
        has_kinematic_evidence
        and external_before_response
        and active_control_at_anchor is not True
    )
    recording_clean = parse_bool(gaze.get("clean")) is True
    baseline_valid = parse_bool(
        gaze.get("response_baseline_valid")
    )
    behavior_class, behavior_reason = classify_behavior(
        recording_clean,
        response is not None,
        baseline_valid,
        has_speed_evidence,
        has_avoidance_evidence,
    )
    min_3d_m, min_horizontal_m, min_vertical_m, min_time_s = observed_minimum_3d(
        intruder_rows, response_time_s
    )

    return {
        "classifier_version": CLASSIFIER_VERSION,
        "trial_id": trial_id,
        "recording_clean": recording_clean,
        "gaze_valid_rate": parse_float(gaze.get("valid_gaze_rate")),
        "spawn_gate_method": gaze.get("spawn_gate_method", ""),
        "speed_stable_before_spawn": parse_bool(
            gaze.get("speed_stable_before_spawn")
        ),
        "speed_error_at_spawn_kias": parse_float(
            gaze.get("speed_error_at_spawn_kias")
        ),
        "speed_rate_at_spawn_kias_s": parse_float(
            gaze.get("speed_rate_at_spawn_kias_s")
        ),
        "spawn_forced_by_timeout": parse_bool(
            gaze.get("spawn_forced_by_timeout")
        ),
        "behavior_class": behavior_class,
        "behavior_reason": behavior_reason,
        "approach": gaze.get("approach", start_detail.get("scheduled_approach", "")),
        "target_speed_kias": target_speed_kias,
        "response_anchor": gaze.get("response_anchor", response_detail.get("anchor", "")),
        "response_trigger_axis": trigger_axis,
        "response_time_s": response_time_s,
        "visual_opportunity_to_response_s": response_latency_s,
        "active_control_at_visual_opportunity": active_control_at_anchor,
        "pitch_delta": pitch_delta,
        "roll_delta": roll_delta,
        "yaw_delta": yaw_delta,
        "throttle_delta": throttle_delta,
        "pre_response_offset_s": PRE_RESPONSE_OFFSET_S,
        "post_response_offset_s": POST_RESPONSE_OFFSET_S,
        "ias_pre_response_kias": ias_before_kias,
        "ias_post_response_kias": ias_after_kias,
        "speed_error_pre_response_kias": speed_error_before_kias,
        "speed_error_post_response_kias": speed_error_after_kias,
        "speed_error_reduction_kias": speed_error_reduction_kias,
        "throttle_direction_consistent": throttle_direction_consistent,
        "speed_task_evidence": has_speed_evidence,
        "projected_cpa_3d_pre_response_m": cpa_before_m,
        "projected_cpa_time_pre_response_s": cpa_before_time_s,
        "projected_cpa_3d_post_response_m": cpa_after_m,
        "projected_cpa_time_post_response_s": cpa_after_time_s,
        "projected_cpa_3d_improvement_m": cpa_improvement_m,
        "avoidance_kinematic_evidence": has_kinematic_evidence,
        "first_operational_external_after_opportunity_s": first_external_s,
        "operational_external_before_response": external_before_response,
        "operational_external_before_opportunity": parse_bool(
            gaze.get("operational_external_before_visual_opportunity")
        ),
        "avoidance_evidence": has_avoidance_evidence,
        "observed_min_3d_after_response_m": min_3d_m,
        "observed_min_horizontal_after_response_m": min_horizontal_m,
        "observed_vertical_at_min_3d_m": min_vertical_m,
        "observed_min_3d_time_s": min_time_s,
        "response_to_observed_min_3d_s": (
            None
            if min_time_s is None or response_time_s is None
            else min_time_s - response_time_s
        ),
        "threshold_min_speed_error_kias": MIN_SPEED_ERROR_KIAS,
        "threshold_min_speed_error_reduction_kias": MIN_SPEED_ERROR_REDUCTION_KIAS,
        "threshold_max_pre_response_cpa_3d_m": MAX_PRE_RESPONSE_CPA_3D_M,
        "threshold_min_cpa_3d_improvement_m": MIN_CPA_3D_IMPROVEMENT_M,
        "threshold_min_post_response_cpa_3d_m": MIN_POST_RESPONSE_CPA_3D_M,
        "threshold_external_timing_tolerance_s": EXTERNAL_TIMING_TOLERANCE_S,
        "threshold_max_nearest_sample_gap_s": MAX_NEAREST_SAMPLE_GAP_S,
    }


def write_results(path, results):
    output_path = Path(path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = list(results[0].keys()) if results else []
    with open(output_path, "w", newline="", encoding="utf-8") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(results)


def format_optional(value, digits=1):
    return "n/a" if value is None else f"{value:.{digits}f}"


def main():
    args = parse_args()
    states_by_trial = group_by_trial(read_csv(args.state))
    intruders_by_trial = group_by_trial(read_csv(args.intruder))
    events_by_trial = group_by_trial(read_csv(args.events))
    gaze_rows = read_csv(args.gaze_summary)
    gaze_by_trial = {
        parse_int(row.get("trial_id")): row
        for row in gaze_rows
        if parse_int(row.get("trial_id")) is not None
    }
    trial_ids = sorted(
        set(states_by_trial)
        | set(intruders_by_trial)
        | set(events_by_trial)
        | set(gaze_by_trial)
    )
    results = [
        build_trial_result(
            trial_id,
            states_by_trial.get(trial_id, []),
            intruders_by_trial.get(trial_id, []),
            events_by_trial.get(trial_id, []),
            gaze_by_trial.get(trial_id, {}),
        )
        for trial_id in trial_ids
    ]
    write_results(args.output, results)

    counts = Counter(row["behavior_class"] for row in results)
    print("X-Plane + Tobii behavior evidence classification")
    print(f"Classifier: {CLASSIFIER_VERSION}")
    print(f"Trials: {len(results)}")
    print("Counts: " + ", ".join(f"{key}={value}" for key, value in sorted(counts.items())))
    for row in results:
        print(
            f"trial {row['trial_id']}: {row['behavior_class']} ({row['behavior_reason']}); "
            f"speed={row['speed_task_evidence']}, avoidance={row['avoidance_evidence']}, "
            f"CPA3D={format_optional(row['projected_cpa_3d_pre_response_m'])}"
            f"->{format_optional(row['projected_cpa_3d_post_response_m'])}m, "
            f"speed_error_reduction={format_optional(row['speed_error_reduction_kias'])}kt, "
            f"external_before_response={row['operational_external_before_response']}"
        )
    print(f"Wrote: {args.output}")


if __name__ == "__main__":
    main()
