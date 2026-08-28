import argparse
import csv
import math
from collections import Counter, defaultdict
from pathlib import Path
from xml.sax.saxutils import escape


ANALYZER_VERSION = "260824_aoi_transition_matrix_v1"

INSTRUMENT_STATES = [
    "AIRSPEED",
    "ATTITUDE",
    "ALTITUDE",
    "HEADING",
    "VERTICAL_SPEED",
    "NAV_GPS",
]

STATE_ORDER = INSTRUMENT_STATES + [
    "PANEL_OTHER",
    "INTRUDER_AOI",
    "RUNWAY_AOI",
    "OTHER_EXTERNAL",
]

STATE_SHORT_LABELS = {
    "AIRSPEED": "AIRSPEED",
    "ATTITUDE": "ATTITUDE",
    "ALTITUDE": "ALTITUDE",
    "HEADING": "HEADING",
    "VERTICAL_SPEED": "VERT SPEED",
    "NAV_GPS": "NAV/GPS",
    "PANEL_OTHER": "PANEL OTHER",
    "INTRUDER_AOI": "INTRUDER",
    "RUNWAY_AOI": "RUNWAY",
    "OTHER_EXTERNAL": "OTHER EXT",
}

SEMANTIC_TO_STATE = {
    "INTRUDER_SEARCH_OR_TRACKING": "INTRUDER_AOI",
    "RUNWAY_GUIDANCE": "RUNWAY_AOI",
    "OTHER_EXTERNAL": "OTHER_EXTERNAL",
}

PHASE_ORDER = [
    "FULL_TRIAL",
    "BEFORE_SPAWN",
    "SPAWN_TO_OPPORTUNITY",
    "AFTER_OPPORTUNITY",
]

PHASE_LABELS = {
    "FULL_TRIAL": "Full trial",
    "BEFORE_SPAWN": "Before intruder spawn",
    "SPAWN_TO_OPPORTUNITY": "Spawn to 20 px visual opportunity",
    "AFTER_OPPORTUNITY": "After 20 px visual opportunity",
}


def parse_float(value, default=math.nan):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def parse_bool(value):
    return str(value).strip().lower() in {"1", "true", "yes", "y"}


def read_csv(path):
    with Path(path).open("r", newline="", encoding="utf-8-sig") as handle:
        return list(csv.DictReader(handle))


def write_csv(path, rows, fieldnames):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8-sig") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def first_event(events, event_type):
    candidates = [event for event in events if event.get("event_type") == event_type]
    if not candidates:
        return None
    return min(candidates, key=lambda event: parse_float(event.get("recorded_timestamp_ms")))


def event_time(event):
    if not event:
        return math.nan
    return parse_float(event.get("recorded_timestamp_ms"))


def make_trial_windows(events):
    by_trial = defaultdict(list)
    for event in events:
        trial_id = str(event.get("trial_id", "")).strip()
        if trial_id not in {"", "0"}:
            by_trial[trial_id].append(event)

    windows = {}
    for trial_id, trial_events in by_trial.items():
        start_ms = event_time(first_event(trial_events, "TRIAL_START"))
        spawn_ms = event_time(first_event(trial_events, "INTRUDER_SPAWNED"))
        opportunity_ms = event_time(first_event(trial_events, "INTRUDER_VISUAL_OPPORTUNITY_ONSET"))
        end_ms = event_time(first_event(trial_events, "TRIAL_END"))
        if any(math.isnan(value) for value in (start_ms, spawn_ms, opportunity_ms, end_ms)):
            continue
        if not start_ms < spawn_ms < opportunity_ms < end_ms:
            continue
        windows[trial_id] = {
            "FULL_TRIAL": (start_ms, end_ms),
            "BEFORE_SPAWN": (start_ms, spawn_ms),
            "SPAWN_TO_OPPORTUNITY": (spawn_ms, opportunity_ms),
            "AFTER_OPPORTUNITY": (opportunity_ms, end_ms),
        }
    return windows


def row_to_attention_state(row, include_panel_other=True):
    if not parse_bool(row.get("valid_gaze")):
        return None

    if parse_bool(row.get("operational_external")):
        return SEMANTIC_TO_STATE.get(row.get("semantic_class", ""))

    operational_aoi = row.get("operational_aoi", "")
    if operational_aoi in INSTRUMENT_STATES:
        return operational_aoi
    if include_panel_other and operational_aoi == "PANEL_OTHER":
        return "PANEL_OTHER"
    return None


def select_bin_state(state_samples):
    if not state_samples:
        return None
    counts = Counter(state for _, state in state_samples)
    maximum = max(counts.values())
    tied = {state for state, count in counts.items() if count == maximum}
    for _, state in reversed(state_samples):
        if state in tied:
            return state
    return None


def bin_phase_rows(rows, start_ms, end_ms, bin_ms, include_panel_other=True):
    bins = defaultdict(list)
    all_bin_counts = Counter()
    for row in rows:
        timestamp_ms = parse_float(row.get("pc_time_ms"))
        if math.isnan(timestamp_ms) or timestamp_ms < start_ms or timestamp_ms >= end_ms:
            continue
        bin_index = int((timestamp_ms - start_ms) // bin_ms)
        all_bin_counts[bin_index] += 1
        state = row_to_attention_state(row, include_panel_other=include_panel_other)
        if state:
            bins[bin_index].append((timestamp_ms, state))

    output = []
    for bin_index in sorted(bins):
        state = select_bin_state(bins[bin_index])
        if not state:
            continue
        output.append(
            {
                "bin_index": bin_index,
                "bin_start_ms": start_ms + bin_index * bin_ms,
                "bin_end_ms": min(end_ms, start_ms + (bin_index + 1) * bin_ms),
                "attention_state": state,
                "eligible_sample_count": len(bins[bin_index]),
                "all_sample_count": all_bin_counts[bin_index],
            }
        )
    return output


def build_binned_states(sessions, bin_ms=200.0, include_panel_other=True):
    output = []
    for session in sessions:
        rows_by_trial = defaultdict(list)
        for row in session["gaze_rows"]:
            trial_id = str(row.get("trial_id", "")).strip()
            if trial_id:
                rows_by_trial[trial_id].append(row)

        windows = make_trial_windows(session["event_rows"])
        for trial_id in sorted(windows, key=lambda value: int(value) if value.isdigit() else value):
            trial_rows = rows_by_trial.get(trial_id, [])
            for phase in PHASE_ORDER:
                start_ms, end_ms = windows[trial_id][phase]
                binned = bin_phase_rows(
                    trial_rows,
                    start_ms,
                    end_ms,
                    bin_ms,
                    include_panel_other=include_panel_other,
                )
                for row in binned:
                    output.append(
                        {
                            "participant_code": session["participant_code"],
                            "session_label": session["session_label"],
                            "trial_id": trial_id,
                            "phase": phase,
                            **row,
                        }
                    )
    return output


def build_transition_observations(binned_rows):
    grouped = defaultdict(list)
    for row in binned_rows:
        key = (
            row["participant_code"],
            row["session_label"],
            row["trial_id"],
            row["phase"],
        )
        grouped[key].append(row)

    observations = []
    for key, rows in grouped.items():
        rows.sort(key=lambda row: row["bin_index"])
        for source, destination in zip(rows, rows[1:]):
            if destination["bin_index"] - source["bin_index"] != 1:
                continue
            observations.append(
                {
                    "participant_code": key[0],
                    "session_label": key[1],
                    "trial_id": key[2],
                    "phase": key[3],
                    "source_bin_index": source["bin_index"],
                    "destination_bin_index": destination["bin_index"],
                    "source_aoi": source["attention_state"],
                    "destination_aoi": destination["attention_state"],
                    "is_self_transition": int(
                        source["attention_state"] == destination["attention_state"]
                    ),
                }
            )
    return observations


def build_dwell_episodes(binned_rows, bin_ms):
    grouped = defaultdict(list)
    for row in binned_rows:
        key = (
            row["participant_code"],
            row["session_label"],
            row["trial_id"],
            row["phase"],
        )
        grouped[key].append(row)

    episodes = []
    for key, rows in grouped.items():
        rows.sort(key=lambda row: row["bin_index"])
        episode_id = 0
        index = 0
        while index < len(rows):
            start = index
            while (
                index + 1 < len(rows)
                and rows[index + 1]["bin_index"] == rows[index]["bin_index"] + 1
                and rows[index + 1]["attention_state"] == rows[start]["attention_state"]
            ):
                index += 1
            episode_id += 1
            bin_count = rows[index]["bin_index"] - rows[start]["bin_index"] + 1
            episodes.append(
                {
                    "participant_code": key[0],
                    "session_label": key[1],
                    "trial_id": key[2],
                    "phase": key[3],
                    "episode_id": episode_id,
                    "attention_state": rows[start]["attention_state"],
                    "start_bin_index": rows[start]["bin_index"],
                    "end_bin_index": rows[index]["bin_index"],
                    "bin_count": bin_count,
                    "duration_s": bin_count * bin_ms / 1000.0,
                }
            )
            index += 1
    return episodes


def aggregate_matrix(observations, transition_type, scope="POOLED_TRANSITIONS", scope_id="ALL"):
    selected = observations
    if scope == "PARTICIPANT":
        selected = [row for row in observations if row["participant_code"] == scope_id]
    if transition_type == "SWITCH":
        selected = [row for row in selected if not row["is_self_transition"]]

    counts = Counter(
        (row["phase"], row["source_aoi"], row["destination_aoi"])
        for row in selected
    )

    rows = []
    for phase in PHASE_ORDER:
        for source in STATE_ORDER:
            total = sum(counts[(phase, source, destination)] for destination in STATE_ORDER)
            row = {
                "scope": scope,
                "scope_id": scope_id,
                "phase": phase,
                "source_aoi": source,
                "total_outgoing": total,
                "contributing_participants": len(
                    {
                        observation["participant_code"]
                        for observation in selected
                        if observation["phase"] == phase and observation["source_aoi"] == source
                    }
                ),
            }
            for destination in STATE_ORDER:
                count = counts[(phase, source, destination)]
                row[destination] = "" if total == 0 else f"{count / total:.6f}"
            rows.append(row)
    return rows, counts


def participant_equal_mean_rows(participant_rows):
    rows_by_key = defaultdict(list)
    for row in participant_rows:
        if row["scope"] == "PARTICIPANT" and row["total_outgoing"]:
            rows_by_key[(row["phase"], row["source_aoi"])].append(row)

    output = []
    for phase in PHASE_ORDER:
        for source in STATE_ORDER:
            contributors = rows_by_key.get((phase, source), [])
            row = {
                "scope": "PARTICIPANT_EQUAL_MEAN",
                "scope_id": "ALL",
                "phase": phase,
                "source_aoi": source,
                "total_outgoing": "",
                "contributing_participants": len(contributors),
            }
            for destination in STATE_ORDER:
                values = [parse_float(item[destination]) for item in contributors]
                values = [value for value in values if not math.isnan(value)]
                row[destination] = "" if not values else f"{sum(values) / len(values):.6f}"
            output.append(row)
    return output


def aggregate_all_scopes(observations, transition_type):
    pooled_rows, pooled_counts = aggregate_matrix(observations, transition_type)
    participants = sorted({row["participant_code"] for row in observations})
    participant_rows = []
    for participant in participants:
        rows, _ = aggregate_matrix(
            observations,
            transition_type,
            scope="PARTICIPANT",
            scope_id=participant,
        )
        participant_rows.extend(rows)
    equal_mean_rows = participant_equal_mean_rows(participant_rows)
    return equal_mean_rows + pooled_rows + participant_rows, pooled_counts


def aggregate_count_rows(observations, transition_type):
    selected = observations
    if transition_type == "SWITCH":
        selected = [row for row in selected if not row["is_self_transition"]]

    scopes = [("POOLED_TRANSITIONS", "ALL", selected)]
    participants = sorted({row["participant_code"] for row in selected})
    scopes.extend(
        (
            "PARTICIPANT",
            participant,
            [row for row in selected if row["participant_code"] == participant],
        )
        for participant in participants
    )

    output = []
    for scope, scope_id, scope_rows in scopes:
        counts = Counter(
            (row["phase"], row["source_aoi"], row["destination_aoi"])
            for row in scope_rows
        )
        for phase in PHASE_ORDER:
            for source in STATE_ORDER:
                row = {
                    "scope": scope,
                    "scope_id": scope_id,
                    "phase": phase,
                    "source_aoi": source,
                    "total_outgoing": sum(
                        counts[(phase, source, destination)] for destination in STATE_ORDER
                    ),
                    "contributing_participants": len(
                        {
                            observation["participant_code"]
                            for observation in scope_rows
                            if observation["phase"] == phase
                            and observation["source_aoi"] == source
                        }
                    ),
                }
                for destination in STATE_ORDER:
                    row[destination] = counts[(phase, source, destination)]
                output.append(row)
    return output


def validate_probability_rows(rows):
    for row in rows:
        values = [parse_float(row.get(destination)) for destination in STATE_ORDER]
        values = [value for value in values if not math.isnan(value)]
        if values and not math.isclose(sum(values), 1.0, abs_tol=0.00001):
            raise ValueError(
                "probability row does not sum to one: "
                f"{row['scope']} {row['scope_id']} {row['phase']} {row['source_aoi']}"
            )


def matrix_summary(observations, binned_rows, bin_ms):
    output = []
    for phase in PHASE_ORDER:
        phase_bins = [row for row in binned_rows if row["phase"] == phase]
        phase_transitions = [row for row in observations if row["phase"] == phase]
        self_count = sum(row["is_self_transition"] for row in phase_transitions)
        switch_count = len(phase_transitions) - self_count
        output.append(
            {
                "phase": phase,
                "participant_count": len({row["participant_code"] for row in phase_bins}),
                "session_count": len({(row["participant_code"], row["session_label"]) for row in phase_bins}),
                "trial_count": len(
                    {
                        (row["participant_code"], row["session_label"], row["trial_id"])
                        for row in phase_bins
                    }
                ),
                "time_bin_ms": bin_ms,
                "eligible_time_bins": len(phase_bins),
                "dtmc_transitions": len(phase_transitions),
                "self_transitions": self_count,
                "aoi_switches": switch_count,
                "self_transition_rate": ""
                if not phase_transitions
                else f"{self_count / len(phase_transitions):.6f}",
            }
        )
    return output


def color_for_probability(probability):
    probability = max(0.0, min(1.0, probability))
    low = (247, 251, 255)
    high = (8, 81, 156)
    values = [round(low[index] + (high[index] - low[index]) * probability) for index in range(3)]
    return "#{:02x}{:02x}{:02x}".format(*values)


def matrix_value(rows_by_key, phase, source, destination):
    row = rows_by_key.get((phase, source))
    if not row or row.get(destination, "") == "":
        return None
    return parse_float(row[destination])


def write_matrix_svg(path, matrix_rows, transition_type, bin_ms):
    primary = [row for row in matrix_rows if row["scope"] == "PARTICIPANT_EQUAL_MEAN"]
    rows_by_key = {(row["phase"], row["source_aoi"]): row for row in primary}
    cell = 54
    grid_x = 250
    grid_y_offset = 145
    panel_height = grid_y_offset + len(STATE_ORDER) * cell + 90
    width = grid_x + len(STATE_ORDER) * cell + 70
    height = 95 + panel_height * len(PHASE_ORDER)
    title = (
        "AOI DTMC probability matrix (self-transition included)"
        if transition_type == "DTMC"
        else "AOI next-switch probability matrix (self-transition excluded)"
    )
    subtitle = (
        f"Participant-equal mean; dominant AOI per {bin_ms:g} ms bin; rows are current AOI and columns are next AOI."
        if transition_type == "DTMC"
        else "Participant-equal mean; each row is normalized over actual changes to a different AOI."
    )

    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        "<style>text{font-family:Segoe UI,Arial,sans-serif}.title{font-size:24px;font-weight:700}.panel{font-size:18px;font-weight:700}.small{font-size:12px}.cell{font-size:11px;font-weight:600}</style>",
        '<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<text class="title" x="28" y="36">{escape(title)}</text>',
        f'<text class="small" x="28" y="62">{escape(subtitle)}</text>',
    ]

    for phase_index, phase in enumerate(PHASE_ORDER):
        panel_y = 85 + phase_index * panel_height
        grid_y = panel_y + grid_y_offset
        lines.append(
            f'<text class="panel" x="28" y="{panel_y + 28}">{escape(PHASE_LABELS[phase])}</text>'
        )
        lines.append(
            f'<text class="small" x="28" y="{panel_y + 50}">Source AOI (row) to destination AOI (column)</text>'
        )
        for column, destination in enumerate(STATE_ORDER):
            x = grid_x + column * cell + cell / 2
            y = grid_y - 10
            label = STATE_SHORT_LABELS[destination]
            lines.append(
                f'<text class="small" text-anchor="start" transform="translate({x:.1f},{y:.1f}) rotate(-55)">{escape(label)}</text>'
            )

        for row_index, source in enumerate(STATE_ORDER):
            y = grid_y + row_index * cell
            lines.append(
                f'<text class="small" text-anchor="end" x="{grid_x - 10}" y="{y + cell / 2 + 4:.1f}">{escape(STATE_SHORT_LABELS[source])}</text>'
            )
            for column, destination in enumerate(STATE_ORDER):
                x = grid_x + column * cell
                value = matrix_value(rows_by_key, phase, source, destination)
                fill = "#eeeeee" if value is None else color_for_probability(value)
                lines.append(
                    f'<rect x="{x}" y="{y}" width="{cell}" height="{cell}" fill="{fill}" stroke="#c8c8c8"/>'
                )
                text_value = "-" if value is None else f"{value:.2f}"
                text_color = "#ffffff" if value is not None and value >= 0.55 else "#111111"
                lines.append(
                    f'<text class="cell" text-anchor="middle" x="{x + cell / 2}" y="{y + cell / 2 + 4}" fill="{text_color}">{text_value}</text>'
                )

    lines.append("</svg>")
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def resolve_input_path(value, base_dir):
    path = Path(value)
    if path.is_absolute():
        return path
    cwd_path = Path.cwd() / path
    if cwd_path.exists():
        return cwd_path
    return base_dir / path


def load_sessions(args):
    specs = []
    if args.manifest:
        manifest_path = Path(args.manifest)
        for row in read_csv(manifest_path):
            if row.get("include", "1").strip().lower() in {"0", "false", "no", "n"}:
                continue
            specs.append(
                {
                    "participant_code": row["participant_code"].strip(),
                    "session_label": row.get("session_label", "").strip() or Path(row["context_gaze_csv"]).stem,
                    "context_path": resolve_input_path(row["context_gaze_csv"], manifest_path.parent),
                    "events_path": resolve_input_path(row["events_csv"], manifest_path.parent),
                }
            )
    elif args.context_gaze and args.events:
        specs.append(
            {
                "participant_code": args.participant_code,
                "session_label": args.session_label or Path(args.context_gaze).stem,
                "context_path": Path(args.context_gaze),
                "events_path": Path(args.events),
            }
        )
    else:
        raise ValueError("use --manifest or provide both --context-gaze and --events")

    if not specs:
        raise ValueError("no included sessions were found")

    sessions = []
    for spec in specs:
        if not spec["participant_code"]:
            raise ValueError("participant_code must not be blank")
        if not spec["context_path"].exists():
            raise FileNotFoundError(spec["context_path"])
        if not spec["events_path"].exists():
            raise FileNotFoundError(spec["events_path"])
        sessions.append(
            {
                **spec,
                "gaze_rows": read_csv(spec["context_path"]),
                "event_rows": read_csv(spec["events_path"]),
            }
        )
    return sessions


def main():
    parser = argparse.ArgumentParser(
        description="Build paper-style AOI transition probability matrices from external-context gaze output."
    )
    parser.add_argument("--context-gaze")
    parser.add_argument("--events")
    parser.add_argument("--participant-code", default="PILOT_SELF_01")
    parser.add_argument("--session-label", default="")
    parser.add_argument("--manifest")
    parser.add_argument("--output-prefix", required=True)
    parser.add_argument("--bin-ms", type=float, default=200.0)
    parser.add_argument("--exclude-panel-other", action="store_true")
    args = parser.parse_args()

    if args.bin_ms <= 0:
        parser.error("--bin-ms must be positive")

    sessions = load_sessions(args)
    include_panel_other = not args.exclude_panel_other
    binned_rows = build_binned_states(
        sessions,
        bin_ms=args.bin_ms,
        include_panel_other=include_panel_other,
    )
    observations = build_transition_observations(binned_rows)
    episodes = build_dwell_episodes(binned_rows, args.bin_ms)
    dtmc_rows, _ = aggregate_all_scopes(observations, "DTMC")
    switch_rows, _ = aggregate_all_scopes(observations, "SWITCH")
    validate_probability_rows(dtmc_rows)
    validate_probability_rows(switch_rows)
    dtmc_count_rows = aggregate_count_rows(observations, "DTMC")
    switch_count_rows = aggregate_count_rows(observations, "SWITCH")
    summary_rows = matrix_summary(observations, binned_rows, args.bin_ms)

    prefix = Path(args.output_prefix)
    matrix_fields = [
        "scope",
        "scope_id",
        "phase",
        "source_aoi",
        "total_outgoing",
        "contributing_participants",
    ] + STATE_ORDER
    write_csv(
        prefix.with_name(prefix.name + "_binned_attention_states.csv"),
        binned_rows,
        [
            "participant_code",
            "session_label",
            "trial_id",
            "phase",
            "bin_index",
            "bin_start_ms",
            "bin_end_ms",
            "attention_state",
            "eligible_sample_count",
            "all_sample_count",
        ],
    )
    write_csv(
        prefix.with_name(prefix.name + "_transition_observations.csv"),
        observations,
        [
            "participant_code",
            "session_label",
            "trial_id",
            "phase",
            "source_bin_index",
            "destination_bin_index",
            "source_aoi",
            "destination_aoi",
            "is_self_transition",
        ],
    )
    write_csv(
        prefix.with_name(prefix.name + "_dwell_episodes.csv"),
        episodes,
        [
            "participant_code",
            "session_label",
            "trial_id",
            "phase",
            "episode_id",
            "attention_state",
            "start_bin_index",
            "end_bin_index",
            "bin_count",
            "duration_s",
        ],
    )
    write_csv(prefix.with_name(prefix.name + "_dtmc_probability_matrix.csv"), dtmc_rows, matrix_fields)
    write_csv(prefix.with_name(prefix.name + "_switch_probability_matrix.csv"), switch_rows, matrix_fields)
    write_csv(prefix.with_name(prefix.name + "_dtmc_count_matrix.csv"), dtmc_count_rows, matrix_fields)
    write_csv(prefix.with_name(prefix.name + "_switch_count_matrix.csv"), switch_count_rows, matrix_fields)
    write_csv(
        prefix.with_name(prefix.name + "_summary.csv"),
        summary_rows,
        list(summary_rows[0].keys()) if summary_rows else ["phase"],
    )

    metadata_rows = [
        {"key": "analyzer_version", "value": ANALYZER_VERSION},
        {"key": "bin_ms", "value": args.bin_ms},
        {"key": "state_selection", "value": "dominant eligible AOI per fixed time bin"},
        {"key": "dtmc_definition", "value": "adjacent-bin transition including self-transition"},
        {"key": "switch_definition", "value": "adjacent-bin AOI change excluding self-transition"},
        {"key": "unresolved_policy", "value": "invalid/unresolved bins break transition chains"},
        {"key": "panel_other_included", "value": str(include_panel_other).lower()},
        {"key": "semantic_caution", "value": "intruder/runway labels are operational AOI evidence, not confirmed awareness or intent"},
        {"key": "benchmark", "value": "Hanak et al., Cognitive Agent Evaluation for Synthetic Pilot Training, DASC 2025, Eq. 3-4 and Fig. 6"},
    ]
    write_csv(
        prefix.with_name(prefix.name + "_metadata.csv"),
        metadata_rows,
        ["key", "value"],
    )
    write_matrix_svg(
        prefix.with_name(prefix.name + "_dtmc_probability_matrix.svg"),
        dtmc_rows,
        "DTMC",
        args.bin_ms,
    )
    write_matrix_svg(
        prefix.with_name(prefix.name + "_switch_probability_matrix.svg"),
        switch_rows,
        "SWITCH",
        args.bin_ms,
    )

    print("AOI transition matrix analysis complete")
    print("Analyzer version:", ANALYZER_VERSION)
    print("Sessions:", len(sessions))
    print("Participants:", len({session["participant_code"] for session in sessions}))
    print("Binned states:", len(binned_rows))
    print("DTMC transitions:", len(observations))
    print("AOI switches:", sum(not row["is_self_transition"] for row in observations))
    print("Output prefix:", prefix)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
