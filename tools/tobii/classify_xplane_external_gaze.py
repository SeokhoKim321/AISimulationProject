import argparse
import bisect
import csv
import html
import math
import statistics
from collections import Counter, defaultdict
from pathlib import Path


CLASSIFIER_VERSION = "260819_dynamic_external_context_v1"
EARTH_RADIUS_M = 6_371_000.0
DEFAULT_RUNWAY_CONFIG = "resources/rksi_runway34_geometry_260819.csv"
DEFAULT_SCREEN_WIDTH = 5760
DEFAULT_SCREEN_HEIGHT = 1080
DEFAULT_MONITOR_WIDTH = 1920
DEFAULT_PANEL_TOP_Y = 600.0
DEFAULT_HORIZONTAL_FOV_DEG = 122.0
DEFAULT_GRID_COLUMNS = 6
DEFAULT_GRID_ROWS = 3
DEFAULT_INTRUDER_MARGIN_PX = 45.0
DEFAULT_RUNWAY_MARGIN_PX = 45.0
DEFAULT_DWELL_MS = 200.0
DEFAULT_MAX_SAMPLE_GAP_MS = 100.0
DEFAULT_MAX_STATE_GAP_MS = 250.0
INTRUDER_WINGSPAN_M = 11.0
INTRUDER_LENGTH_M = 8.3

SEMANTIC_INTRUDER = "INTRUDER_SEARCH_OR_TRACKING"
SEMANTIC_RUNWAY = "RUNWAY_GUIDANCE"
SEMANTIC_OTHER = "OTHER_EXTERNAL"
SEMANTIC_UNRESOLVED = "UNRESOLVED"
SEMANTIC_NOT_EXTERNAL = "NOT_EXTERNAL"
SEMANTIC_NOT_IN_TRIAL = "NOT_IN_TRIAL"

OUTPUT_FIELDS = [
    "pc_time_ms",
    "trial_id",
    "approach",
    "gaze_px",
    "gaze_py",
    "valid_gaze",
    "display_region",
    "operational_external",
    "operational_aoi",
    "external_evidence",
    "grid_monitor",
    "grid_row",
    "grid_column",
    "grid_cell",
    "state_sample_index",
    "state_time_gap_ms",
    "projection_valid",
    "projection_visible",
    "intruder_screen_x_px",
    "intruder_screen_y_px",
    "intruder_span_px",
    "intruder_aoi_left_px",
    "intruder_aoi_top_px",
    "intruder_aoi_right_px",
    "intruder_aoi_bottom_px",
    "intruder_grid_cell",
    "gaze_intruder_distance_px",
    "intruder_dynamic_aoi_overlap",
    "intruder_grid_match",
    "runway_projection_valid",
    "runway_polygon_px",
    "gaze_runway_distance_px",
    "runway_dynamic_aoi_overlap",
    "semantic_candidate",
    "semantic_evidence",
    "semantic_class",
    "semantic_episode_id",
]

EPISODE_FIELDS = [
    "episode_id",
    "trial_id",
    "approach",
    "semantic_class",
    "semantic_evidence",
    "start_ms",
    "end_ms",
    "duration_ms",
    "sample_count",
    "start_grid_cell",
    "end_grid_cell",
]

SUMMARY_FIELDS = [
    "trial_id",
    "approach",
    "trial_start_ms",
    "trial_end_ms",
    "spawn_ms",
    "visual_opportunity_ms",
    "response_ms",
    "gaze_rows",
    "valid_gaze_rows",
    "operational_external_rows",
    "classified_external_rows",
    "intruder_rows",
    "intruder_dynamic_overlap_rows",
    "intruder_grid_match_rows",
    "intruder_side_match_rows",
    "runway_rows",
    "other_external_rows",
    "unresolved_rows",
    "intruder_rate_external",
    "runway_rate_external",
    "other_rate_external",
    "unresolved_rate_external",
    "first_intruder_episode_ms",
    "spawn_to_first_intruder_episode_s",
    "opportunity_to_first_intruder_episode_s",
    "intruder_evidence_before_opportunity",
    "first_dynamic_intruder_dwell_ms",
    "spawn_to_first_dynamic_intruder_dwell_s",
    "opportunity_to_first_dynamic_intruder_dwell_s",
    "dynamic_intruder_dwell_before_opportunity",
    "intruder_episode_count",
    "runway_episode_count",
    "other_episode_count",
    "unresolved_episode_count",
]

GRID_SUMMARY_FIELDS = [
    "trial_id",
    "approach",
    "grid_cell",
    "external_rows",
    "intruder_rows",
    "runway_rows",
    "other_external_rows",
    "unresolved_rows",
    "intruder_rate",
    "runway_rate",
    "other_rate",
    "unresolved_rate",
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


def parse_bool(value):
    return str(value).strip().lower() in {"1", "true", "yes"}


def read_csv(path):
    with Path(path).open(newline="", encoding="utf-8") as file:
        return list(csv.DictReader(file))


def write_csv(path, rows, fieldnames):
    with Path(path).open("w", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def detail_value(detail, key):
    prefix = key + "="
    for part in (detail or "").split(";"):
        if part.startswith(prefix):
            return part[len(prefix):]
    return ""


def first_event(events, event_type):
    for event in events:
        if event.get("event_type") == event_type:
            return event
    return None


def event_ms(event):
    return parse_float(event.get("recorded_timestamp_ms")) if event else math.nan


def make_trial_contexts(events):
    by_trial = defaultdict(list)
    for event in events:
        trial_id = event.get("trial_id", "")
        if trial_id not in {"", "0"}:
            by_trial[trial_id].append(event)

    contexts = {}
    for trial_id, trial_events in by_trial.items():
        trial_events.sort(key=lambda row: parse_float(row.get("recorded_timestamp_ms")))
        start = first_event(trial_events, "TRIAL_START")
        end = first_event(trial_events, "TRIAL_END")
        spawn = first_event(trial_events, "INTRUDER_SPAWNED")
        opportunity = first_event(trial_events, "INTRUDER_VISUAL_OPPORTUNITY_ONSET")
        response = first_event(trial_events, "PILOT_RESPONSE_START")
        spawn_detail = spawn.get("detail", "") if spawn else ""
        contexts[trial_id] = {
            "trial_id": trial_id,
            "start_ms": event_ms(start),
            "end_ms": event_ms(end),
            "spawn_ms": event_ms(spawn),
            "opportunity_ms": event_ms(opportunity),
            "response_ms": event_ms(response),
            "approach": (detail_value(spawn_detail, "approach") or "unknown").lower(),
            "intruder_heading_deg": parse_float(detail_value(spawn_detail, "heading_deg")),
        }
    return contexts


def trial_for_time(contexts, timestamp_ms):
    for context in contexts.values():
        if context["start_ms"] <= timestamp_ms <= context["end_ms"]:
            return context
    return None


def build_state_index(state_rows):
    by_trial = defaultdict(list)
    for row in state_rows:
        trial_id = row.get("trial_id", "")
        if trial_id not in {"", "0"}:
            by_trial[trial_id].append(row)
    indexes = {}
    for trial_id, rows in by_trial.items():
        rows.sort(key=lambda row: parse_float(row.get("received_timestamp_ms")))
        indexes[trial_id] = (
            [parse_float(row.get("received_timestamp_ms")) for row in rows],
            rows,
        )
    return indexes


def build_intruder_index(intruder_rows):
    return {
        (row.get("trial_id", ""), row.get("sample_index", "")): row
        for row in intruder_rows
        if row.get("trial_id", "") not in {"", "0"}
    }


def nearest_state(state_index, trial_id, timestamp_ms):
    index = state_index.get(trial_id)
    if not index:
        return None, math.nan
    times, rows = index
    position = bisect.bisect_left(times, timestamp_ms)
    candidates = []
    if position < len(rows):
        candidates.append((abs(times[position] - timestamp_ms), rows[position]))
    if position > 0:
        candidates.append((abs(times[position - 1] - timestamp_ms), rows[position - 1]))
    if not candidates:
        return None, math.nan
    return min(candidates, key=lambda item: item[0])[1], min(item[0] for item in candidates)


def signed_angle_deg(angle_deg):
    return (angle_deg + 180.0) % 360.0 - 180.0


def project_intruder(state, intruder, intruder_heading_deg, screen_width, screen_height, horizontal_fov_deg):
    values = [
        parse_float(state.get("x")),
        parse_float(state.get("y")),
        parse_float(state.get("z")),
        parse_float(state.get("heading_deg")),
        parse_float(state.get("pitch_deg")),
        parse_float(intruder.get("intruder_x")),
        parse_float(intruder.get("intruder_y")),
        parse_float(intruder.get("intruder_z")),
        intruder_heading_deg,
    ]
    if any(math.isnan(value) for value in values):
        return {"valid": False, "visible": False}

    own_x, own_y, own_z, heading_deg, pitch_deg, intr_x, intr_y, intr_z, move_heading = values
    dx = intr_x - own_x
    dy = intr_y - own_y
    dz = intr_z - own_z
    horizontal_distance_m = math.hypot(dx, dz)
    slant_distance_m = math.hypot(horizontal_distance_m, dy)

    heading_rad = math.radians(heading_deg)
    forward_x = math.sin(heading_rad)
    forward_z = -math.cos(heading_rad)
    right_x = math.cos(heading_rad)
    right_z = math.sin(heading_rad)
    horizontal_forward_m = dx * forward_x + dz * forward_z
    camera_right_m = dx * right_x + dz * right_z

    pitch_rad = math.radians(pitch_deg)
    camera_depth_m = horizontal_forward_m * math.cos(pitch_rad) + dy * math.sin(pitch_rad)
    camera_up_m = dy * math.cos(pitch_rad) - horizontal_forward_m * math.sin(pitch_rad)
    focal_length_px = screen_width / (2.0 * math.tan(math.radians(horizontal_fov_deg * 0.5)))

    move_rad = math.radians(move_heading)
    move_x = math.sin(move_rad)
    move_z = -math.cos(move_rad)
    intruder_right_x = -move_z
    intruder_right_z = move_x
    wing_factor = abs(intruder_right_x * right_x + intruder_right_z * right_z)
    fuselage_factor = abs(move_x * right_x + move_z * right_z)
    horizontal_span_m = INTRUDER_WINGSPAN_M * wing_factor + INTRUDER_LENGTH_M * fuselage_factor

    if camera_depth_m <= 1.0:
        return {
            "valid": False,
            "visible": False,
            "horizontal_distance_m": horizontal_distance_m,
            "slant_distance_m": slant_distance_m,
        }

    screen_x_px = screen_width * 0.5 + focal_length_px * camera_right_m / camera_depth_m
    screen_y_px = screen_height * 0.5 - focal_length_px * camera_up_m / camera_depth_m
    span_px = focal_length_px * horizontal_span_m / camera_depth_m
    bearing_to_intruder = math.degrees(math.atan2(dx, -dz))
    relative_bearing_deg = signed_angle_deg(bearing_to_intruder - heading_deg)
    relative_elevation_deg = math.degrees(math.atan2(dy, max(horizontal_distance_m, 0.001))) - pitch_deg
    visible = 0.0 <= screen_x_px <= screen_width and 0.0 <= screen_y_px <= screen_height
    return {
        "valid": True,
        "visible": visible,
        "screen_x_px": screen_x_px,
        "screen_y_px": screen_y_px,
        "span_px": span_px,
        "horizontal_distance_m": horizontal_distance_m,
        "slant_distance_m": slant_distance_m,
        "relative_bearing_deg": relative_bearing_deg,
        "relative_elevation_deg": relative_elevation_deg,
    }


def geodetic_to_local(latitude_deg, longitude_deg, elevation_m, state):
    anchor_lat = parse_float(state.get("latitude_deg"))
    anchor_lon = parse_float(state.get("longitude_deg"))
    anchor_elevation = parse_float(state.get("elevation_m"))
    anchor_x = parse_float(state.get("x"))
    anchor_y = parse_float(state.get("y"))
    anchor_z = parse_float(state.get("z"))
    if any(
        math.isnan(value)
        for value in (anchor_lat, anchor_lon, anchor_elevation, anchor_x, anchor_y, anchor_z)
    ):
        return None
    north_m = math.radians(latitude_deg - anchor_lat) * EARTH_RADIUS_M
    east_m = (
        math.radians(longitude_deg - anchor_lon)
        * EARTH_RADIUS_M
        * math.cos(math.radians((latitude_deg + anchor_lat) * 0.5))
    )
    return {
        "x": anchor_x + east_m,
        "y": anchor_y + (elevation_m - anchor_elevation),
        "z": anchor_z - north_m,
    }


def runway_local_corners(runway, state):
    elevation_m = parse_float(runway.get("elevation_m"))
    near = geodetic_to_local(
        parse_float(runway.get("near_latitude_deg")),
        parse_float(runway.get("near_longitude_deg")),
        elevation_m,
        state,
    )
    far = geodetic_to_local(
        parse_float(runway.get("far_latitude_deg")),
        parse_float(runway.get("far_longitude_deg")),
        elevation_m,
        state,
    )
    if near is None or far is None:
        return []
    center_dx = far["x"] - near["x"]
    center_dz = far["z"] - near["z"]
    length = math.hypot(center_dx, center_dz)
    if length <= 1.0:
        return []
    half_width = parse_float(runway.get("width_m")) * 0.5
    perpendicular_x = -center_dz / length
    perpendicular_z = center_dx / length

    def offset(point, sign):
        return {
            "x": point["x"] + sign * half_width * perpendicular_x,
            "y": point["y"],
            "z": point["z"] + sign * half_width * perpendicular_z,
        }

    return [offset(near, -1), offset(near, 1), offset(far, 1), offset(far, -1)]


def project_point(state, point, screen_width, screen_height, horizontal_fov_deg):
    dx = point["x"] - parse_float(state.get("x"))
    dy = point["y"] - parse_float(state.get("y"))
    dz = point["z"] - parse_float(state.get("z"))
    heading_rad = math.radians(parse_float(state.get("heading_deg")))
    pitch_rad = math.radians(parse_float(state.get("pitch_deg")))
    horizontal_forward = dx * math.sin(heading_rad) + dz * -math.cos(heading_rad)
    camera_right = dx * math.cos(heading_rad) + dz * math.sin(heading_rad)
    camera_depth = horizontal_forward * math.cos(pitch_rad) + dy * math.sin(pitch_rad)
    camera_up = dy * math.cos(pitch_rad) - horizontal_forward * math.sin(pitch_rad)
    if camera_depth <= 1.0:
        return None
    focal = screen_width / (2.0 * math.tan(math.radians(horizontal_fov_deg * 0.5)))
    return (
        screen_width * 0.5 + focal * camera_right / camera_depth,
        screen_height * 0.5 - focal * camera_up / camera_depth,
    )


def project_runway(runway, state, screen_width, screen_height, horizontal_fov_deg):
    corners = runway_local_corners(runway, state)
    projected = [
        project_point(state, point, screen_width, screen_height, horizontal_fov_deg)
        for point in corners
    ]
    if len(projected) != 4 or any(point is None for point in projected):
        return {"valid": False, "polygon": []}
    return {"valid": True, "polygon": projected}


def point_in_polygon(x, y, polygon):
    inside = False
    count = len(polygon)
    for index in range(count):
        x1, y1 = polygon[index]
        x2, y2 = polygon[(index + 1) % count]
        if (y1 > y) != (y2 > y):
            crossing_x = (x2 - x1) * (y - y1) / (y2 - y1) + x1
            if x < crossing_x:
                inside = not inside
    return inside


def point_segment_distance(x, y, x1, y1, x2, y2):
    dx = x2 - x1
    dy = y2 - y1
    length_squared = dx * dx + dy * dy
    if length_squared <= 1e-9:
        return math.hypot(x - x1, y - y1)
    ratio = max(0.0, min(1.0, ((x - x1) * dx + (y - y1) * dy) / length_squared))
    nearest_x = x1 + ratio * dx
    nearest_y = y1 + ratio * dy
    return math.hypot(x - nearest_x, y - nearest_y)


def point_polygon_distance(x, y, polygon):
    if not polygon:
        return math.nan
    if point_in_polygon(x, y, polygon):
        return 0.0
    return min(
        point_segment_distance(x, y, *polygon[index], *polygon[(index + 1) % len(polygon)])
        for index in range(len(polygon))
    )


def polygon_text(polygon):
    return "|".join(f"{x:.3f}:{y:.3f}" for x, y in polygon)


def parse_polygon_text(value):
    polygon = []
    for pair in (value or "").split("|"):
        if not pair or ":" not in pair:
            continue
        x_text, y_text = pair.split(":", 1)
        x = parse_float(x_text)
        y = parse_float(y_text)
        if not math.isnan(x) and not math.isnan(y):
            polygon.append((x, y))
    return polygon


def grid_cell(px, py, screen_width, monitor_width, panel_top_y, columns, rows):
    if math.isnan(px) or math.isnan(py) or not (0 <= px <= screen_width and 0 <= py < panel_top_y):
        return "", "", "", ""
    monitor_index = min(int(px // monitor_width), int(screen_width // monitor_width) - 1)
    monitor_names = ["LEFT", "CENTER", "RIGHT"]
    monitor = monitor_names[monitor_index] if monitor_index < len(monitor_names) else f"M{monitor_index + 1}"
    local_x = px - monitor_index * monitor_width
    column = min(int(local_x / monitor_width * columns) + 1, columns)
    row = min(int(py / panel_top_y * rows) + 1, rows)
    return monitor, row, column, f"{monitor}_R{row}C{column}"


def annotate_semantic_episodes(rows, dwell_ms, max_sample_gap_ms):
    episodes = []
    episode_counter = 0
    index = 0
    while index < len(rows):
        candidate = rows[index]["semantic_candidate"]
        trial_id = rows[index]["trial_id"]
        if candidate in {SEMANTIC_NOT_EXTERNAL, SEMANTIC_NOT_IN_TRIAL}:
            rows[index]["semantic_class"] = candidate
            index += 1
            continue
        run_start = index
        run_end = index
        previous_time = parse_float(rows[index]["pc_time_ms"])
        index += 1
        while index < len(rows):
            current_time = parse_float(rows[index]["pc_time_ms"])
            if (
                rows[index]["semantic_candidate"] != candidate
                or rows[index]["trial_id"] != trial_id
                or math.isnan(current_time)
                or math.isnan(previous_time)
                or current_time - previous_time > max_sample_gap_ms
            ):
                break
            run_end = index
            previous_time = current_time
            index += 1

        start_ms = parse_float(rows[run_start]["pc_time_ms"])
        end_ms = parse_float(rows[run_end]["pc_time_ms"])
        duration_ms = max(0.0, end_ms - start_ms)
        confirmed_class = candidate
        if candidate != SEMANTIC_UNRESOLVED and duration_ms < dwell_ms:
            confirmed_class = SEMANTIC_UNRESOLVED
            for row_index in range(run_start, run_end + 1):
                rows[row_index]["semantic_class"] = SEMANTIC_UNRESOLVED
                rows[row_index]["semantic_evidence"] += ";TRANSIENT_LT_DWELL"
        else:
            for row_index in range(run_start, run_end + 1):
                rows[row_index]["semantic_class"] = confirmed_class

        episode_counter += 1
        episode_id = f"SEM_{episode_counter:04d}"
        evidence_counts = Counter(
            rows[row_index]["semantic_evidence"] for row_index in range(run_start, run_end + 1)
        )
        evidence = evidence_counts.most_common(1)[0][0]
        for row_index in range(run_start, run_end + 1):
            rows[row_index]["semantic_episode_id"] = episode_id
        episodes.append(
            {
                "episode_id": episode_id,
                "trial_id": trial_id,
                "approach": rows[run_start]["approach"],
                "semantic_class": confirmed_class,
                "semantic_evidence": evidence,
                "start_ms": start_ms,
                "end_ms": end_ms,
                "duration_ms": duration_ms,
                "sample_count": run_end - run_start + 1,
                "start_grid_cell": rows[run_start]["grid_cell"],
                "end_grid_cell": rows[run_end]["grid_cell"],
            }
        )
    return episodes


def annotate_dynamic_geometry(
    row,
    gaze_x,
    gaze_y,
    gaze_cell,
    timestamp_ms,
    state,
    intruder,
    context,
    runway,
    screen_width,
    screen_height,
    monitor_width,
    panel_top_y,
    horizontal_fov_deg,
    grid_columns,
    grid_rows,
    intruder_margin_px,
    runway_margin_px,
):
    projection = {"valid": False, "visible": False}
    intruder_active = context["spawn_ms"] <= timestamp_ms <= context["end_ms"]
    if intruder_active and intruder is not None:
        projection = project_intruder(
            state,
            intruder,
            context["intruder_heading_deg"],
            screen_width,
            screen_height,
            horizontal_fov_deg,
        )

    row["projection_valid"] = int(bool(projection.get("valid")))
    row["projection_visible"] = int(bool(projection.get("visible")))
    intruder_overlap = False
    intruder_grid_match = False
    if projection.get("valid"):
        intruder_x = projection["screen_x_px"]
        intruder_y = projection["screen_y_px"]
        span_px = projection["span_px"]
        half_width = max(intruder_margin_px, span_px * 0.5 + intruder_margin_px)
        half_height = max(intruder_margin_px, span_px * 0.25 + intruder_margin_px)
        left = intruder_x - half_width
        right = intruder_x + half_width
        top = intruder_y - half_height
        bottom = intruder_y + half_height
        intruder_overlap = (
            projection.get("visible", False)
            and not math.isnan(gaze_x)
            and not math.isnan(gaze_y)
            and left <= gaze_x <= right
            and top <= gaze_y <= bottom
        )
        distance_px = (
            math.hypot(gaze_x - intruder_x, gaze_y - intruder_y)
            if not math.isnan(gaze_x) and not math.isnan(gaze_y)
            else math.nan
        )
        _, _, _, intruder_cell = grid_cell(
            intruder_x,
            intruder_y,
            screen_width,
            monitor_width,
            panel_top_y,
            grid_columns,
            grid_rows,
        )
        intruder_grid_match = bool(gaze_cell and intruder_cell and gaze_cell == intruder_cell)
        row.update(
            {
                "intruder_screen_x_px": intruder_x,
                "intruder_screen_y_px": intruder_y,
                "intruder_span_px": span_px,
                "intruder_aoi_left_px": left,
                "intruder_aoi_top_px": top,
                "intruder_aoi_right_px": right,
                "intruder_aoi_bottom_px": bottom,
                "intruder_grid_cell": intruder_cell,
                "gaze_intruder_distance_px": distance_px,
                "intruder_dynamic_aoi_overlap": int(intruder_overlap),
                "intruder_grid_match": int(intruder_grid_match),
            }
        )

    runway_projection = project_runway(
        runway,
        state,
        screen_width,
        screen_height,
        horizontal_fov_deg,
    )
    runway_overlap = False
    if runway_projection["valid"]:
        polygon = runway_projection["polygon"]
        runway_distance_px = (
            point_polygon_distance(gaze_x, gaze_y, polygon)
            if not math.isnan(gaze_x) and not math.isnan(gaze_y)
            else math.nan
        )
        runway_overlap = not math.isnan(runway_distance_px) and runway_distance_px <= runway_margin_px
        row.update(
            {
                "runway_projection_valid": 1,
                "runway_polygon_px": polygon_text(polygon),
                "gaze_runway_distance_px": runway_distance_px,
                "runway_dynamic_aoi_overlap": int(runway_overlap),
            }
        )
    else:
        row["runway_projection_valid"] = 0
    return intruder_overlap, intruder_grid_match, runway_overlap


def classify_rows(
    state_rows,
    intruder_rows,
    events,
    gaze_rows,
    runway,
    screen_width=DEFAULT_SCREEN_WIDTH,
    screen_height=DEFAULT_SCREEN_HEIGHT,
    monitor_width=DEFAULT_MONITOR_WIDTH,
    panel_top_y=DEFAULT_PANEL_TOP_Y,
    horizontal_fov_deg=DEFAULT_HORIZONTAL_FOV_DEG,
    grid_columns=DEFAULT_GRID_COLUMNS,
    grid_rows=DEFAULT_GRID_ROWS,
    intruder_margin_px=DEFAULT_INTRUDER_MARGIN_PX,
    runway_margin_px=DEFAULT_RUNWAY_MARGIN_PX,
    max_state_gap_ms=DEFAULT_MAX_STATE_GAP_MS,
    dwell_ms=DEFAULT_DWELL_MS,
    max_sample_gap_ms=DEFAULT_MAX_SAMPLE_GAP_MS,
):
    contexts = make_trial_contexts(events)
    state_index = build_state_index(state_rows)
    intruder_index = build_intruder_index(intruder_rows)
    output = []

    for gaze in gaze_rows:
        timestamp_ms = parse_float(gaze.get("pc_time_ms"))
        context = trial_for_time(contexts, timestamp_ms)
        row = {field: "" for field in OUTPUT_FIELDS}
        row.update(
            {
                "pc_time_ms": timestamp_ms,
                "trial_id": context["trial_id"] if context else "",
                "approach": context["approach"] if context else "",
                "gaze_px": gaze.get("gaze_px", ""),
                "gaze_py": gaze.get("gaze_py", ""),
                "valid_gaze": gaze.get("valid_gaze", ""),
                "display_region": gaze.get("display_region", ""),
                "operational_external": gaze.get("operational_external", ""),
                "operational_aoi": gaze.get("operational_aoi", ""),
                "external_evidence": gaze.get("external_evidence", ""),
                "semantic_class": "",
                "semantic_episode_id": "",
            }
        )

        gaze_x = parse_float(gaze.get("gaze_px"))
        gaze_y = parse_float(gaze.get("gaze_py"))
        monitor, grid_row, grid_column, cell = grid_cell(
            gaze_x,
            gaze_y,
            screen_width,
            monitor_width,
            panel_top_y,
            grid_columns,
            grid_rows,
        )
        row.update(
            {
                "grid_monitor": monitor,
                "grid_row": grid_row,
                "grid_column": grid_column,
                "grid_cell": cell,
            }
        )

        if context is None:
            row["semantic_candidate"] = SEMANTIC_NOT_IN_TRIAL
            row["semantic_evidence"] = "OUTSIDE_TRIAL_WINDOW"
            output.append(row)
            continue

        state, state_gap_ms = nearest_state(state_index, context["trial_id"], timestamp_ms)
        row["state_time_gap_ms"] = state_gap_ms
        intruder_overlap = False
        intruder_grid_match = False
        runway_overlap = False
        state_available = state is not None and state_gap_ms <= max_state_gap_ms
        if state_available:
            row["state_sample_index"] = state.get("sample_index", "")
            intruder = intruder_index.get((context["trial_id"], state.get("sample_index", "")))
            intruder_overlap, intruder_grid_match, runway_overlap = annotate_dynamic_geometry(
                row,
                gaze_x,
                gaze_y,
                cell,
                timestamp_ms,
                state,
                intruder,
                context,
                runway,
                screen_width,
                screen_height,
                monitor_width,
                panel_top_y,
                horizontal_fov_deg,
                grid_columns,
                grid_rows,
                intruder_margin_px,
                runway_margin_px,
            )

        operational_external = parse_bool(gaze.get("operational_external"))
        if not operational_external:
            row["semantic_candidate"] = SEMANTIC_NOT_EXTERNAL
            row["semantic_evidence"] = "INSTRUMENT_PANEL_OR_UNCONFIRMED_SIDE"
            output.append(row)
            continue

        if not parse_bool(gaze.get("valid_gaze")):
            row["semantic_candidate"] = SEMANTIC_UNRESOLVED
            row["semantic_evidence"] = gaze.get("external_evidence", "TRACKING_LOSS")
            output.append(row)
            continue

        if not state_available:
            row["semantic_candidate"] = SEMANTIC_UNRESOLVED
            row["semantic_evidence"] = "NO_NEARBY_XPLANE_STATE"
            output.append(row)
            continue

        evidence = gaze.get("external_evidence", "")
        side_match = (
            timestamp_ms >= context["spawn_ms"]
            and (
                (context["approach"] == "left" and evidence == "MEASURED_SIDE_LEFT")
                or (context["approach"] == "right" and evidence == "MEASURED_SIDE_RIGHT")
            )
        )

        if intruder_overlap:
            row["semantic_candidate"] = SEMANTIC_INTRUDER
            row["semantic_evidence"] = "DYNAMIC_INTRUDER_AOI_OVERLAP"
        elif runway_overlap:
            row["semantic_candidate"] = SEMANTIC_RUNWAY
            row["semantic_evidence"] = "DYNAMIC_RUNWAY_AOI_OVERLAP"
        elif intruder_grid_match:
            row["semantic_candidate"] = SEMANTIC_INTRUDER
            row["semantic_evidence"] = "INTRUDER_GRID_CELL_MATCH_PROXY"
        elif side_match:
            row["semantic_candidate"] = SEMANTIC_INTRUDER
            row["semantic_evidence"] = "MEASURED_SIDE_DIRECTION_MATCH_PROXY"
        else:
            row["semantic_candidate"] = SEMANTIC_OTHER
            row["semantic_evidence"] = "EXTERNAL_RESIDUAL"
        output.append(row)

    episodes = annotate_semantic_episodes(output, dwell_ms, max_sample_gap_ms)
    return output, episodes, contexts


def summarize_trials(
    rows,
    episodes,
    contexts,
    dwell_ms=DEFAULT_DWELL_MS,
    max_sample_gap_ms=DEFAULT_MAX_SAMPLE_GAP_MS,
):
    rows_by_trial = defaultdict(list)
    episodes_by_trial = defaultdict(list)
    for row in rows:
        if row["trial_id"]:
            rows_by_trial[row["trial_id"]].append(row)
    for episode in episodes:
        episodes_by_trial[episode["trial_id"]].append(episode)

    summaries = []
    for trial_id in sorted(contexts, key=lambda value: int(value)):
        context = contexts[trial_id]
        trial_rows = rows_by_trial[trial_id]
        external_rows = [row for row in trial_rows if parse_bool(row["operational_external"])]
        counts = Counter(row["semantic_class"] for row in external_rows)
        classified_external = sum(
            counts[name]
            for name in (SEMANTIC_INTRUDER, SEMANTIC_RUNWAY, SEMANTIC_OTHER, SEMANTIC_UNRESOLVED)
        )
        trial_episodes = episodes_by_trial[trial_id]
        intruder_episodes = [
            episode for episode in trial_episodes if episode["semantic_class"] == SEMANTIC_INTRUDER
        ]
        first_intruder_ms = min(
            (parse_float(episode["start_ms"]) for episode in intruder_episodes),
            default=math.nan,
        )
        first_dynamic_intruder_ms = first_sustained_evidence(
            trial_rows,
            "DYNAMIC_INTRUDER_AOI_OVERLAP",
            dwell_ms,
            max_sample_gap_ms,
        )
        opportunity_ms = context["opportunity_ms"]
        intruder_dynamic_rows = sum(
            row["semantic_class"] == SEMANTIC_INTRUDER
            and row["semantic_evidence"].startswith("DYNAMIC_INTRUDER_AOI_OVERLAP")
            for row in external_rows
        )
        intruder_grid_rows = sum(
            row["semantic_class"] == SEMANTIC_INTRUDER
            and row["semantic_evidence"].startswith("INTRUDER_GRID_CELL_MATCH_PROXY")
            for row in external_rows
        )
        intruder_side_rows = sum(
            row["semantic_class"] == SEMANTIC_INTRUDER
            and row["semantic_evidence"].startswith("MEASURED_SIDE_DIRECTION_MATCH_PROXY")
            for row in external_rows
        )
        summaries.append(
            {
                "trial_id": trial_id,
                "approach": context["approach"],
                "trial_start_ms": context["start_ms"],
                "trial_end_ms": context["end_ms"],
                "spawn_ms": context["spawn_ms"],
                "visual_opportunity_ms": opportunity_ms,
                "response_ms": context["response_ms"],
                "gaze_rows": len(trial_rows),
                "valid_gaze_rows": sum(parse_bool(row["valid_gaze"]) for row in trial_rows),
                "operational_external_rows": len(external_rows),
                "classified_external_rows": classified_external,
                "intruder_rows": counts[SEMANTIC_INTRUDER],
                "intruder_dynamic_overlap_rows": intruder_dynamic_rows,
                "intruder_grid_match_rows": intruder_grid_rows,
                "intruder_side_match_rows": intruder_side_rows,
                "runway_rows": counts[SEMANTIC_RUNWAY],
                "other_external_rows": counts[SEMANTIC_OTHER],
                "unresolved_rows": counts[SEMANTIC_UNRESOLVED],
                "intruder_rate_external": counts[SEMANTIC_INTRUDER] / len(external_rows) if external_rows else "",
                "runway_rate_external": counts[SEMANTIC_RUNWAY] / len(external_rows) if external_rows else "",
                "other_rate_external": counts[SEMANTIC_OTHER] / len(external_rows) if external_rows else "",
                "unresolved_rate_external": counts[SEMANTIC_UNRESOLVED] / len(external_rows) if external_rows else "",
                "first_intruder_episode_ms": "" if math.isnan(first_intruder_ms) else first_intruder_ms,
                "spawn_to_first_intruder_episode_s": (
                    "" if math.isnan(first_intruder_ms) else (first_intruder_ms - context["spawn_ms"]) / 1000.0
                ),
                "opportunity_to_first_intruder_episode_s": (
                    "" if math.isnan(first_intruder_ms) else (first_intruder_ms - opportunity_ms) / 1000.0
                ),
                "intruder_evidence_before_opportunity": int(
                    not math.isnan(first_intruder_ms) and first_intruder_ms < opportunity_ms
                ),
                "first_dynamic_intruder_dwell_ms": (
                    "" if math.isnan(first_dynamic_intruder_ms) else first_dynamic_intruder_ms
                ),
                "spawn_to_first_dynamic_intruder_dwell_s": (
                    ""
                    if math.isnan(first_dynamic_intruder_ms)
                    else (first_dynamic_intruder_ms - context["spawn_ms"]) / 1000.0
                ),
                "opportunity_to_first_dynamic_intruder_dwell_s": (
                    ""
                    if math.isnan(first_dynamic_intruder_ms)
                    else (first_dynamic_intruder_ms - opportunity_ms) / 1000.0
                ),
                "dynamic_intruder_dwell_before_opportunity": int(
                    not math.isnan(first_dynamic_intruder_ms)
                    and first_dynamic_intruder_ms < opportunity_ms
                ),
                "intruder_episode_count": len(intruder_episodes),
                "runway_episode_count": sum(
                    episode["semantic_class"] == SEMANTIC_RUNWAY for episode in trial_episodes
                ),
                "other_episode_count": sum(
                    episode["semantic_class"] == SEMANTIC_OTHER for episode in trial_episodes
                ),
                "unresolved_episode_count": sum(
                    episode["semantic_class"] == SEMANTIC_UNRESOLVED for episode in trial_episodes
                ),
            }
        )
    return summaries


def first_sustained_evidence(rows, evidence_prefix, dwell_ms, max_sample_gap_ms):
    index = 0
    while index < len(rows):
        if not rows[index]["semantic_evidence"].startswith(evidence_prefix):
            index += 1
            continue
        start_ms = parse_float(rows[index]["pc_time_ms"])
        end_ms = start_ms
        previous_ms = start_ms
        index += 1
        while index < len(rows):
            current_ms = parse_float(rows[index]["pc_time_ms"])
            if (
                not rows[index]["semantic_evidence"].startswith(evidence_prefix)
                or math.isnan(current_ms)
                or math.isnan(previous_ms)
                or current_ms - previous_ms > max_sample_gap_ms
            ):
                break
            end_ms = current_ms
            previous_ms = current_ms
            index += 1
        if not math.isnan(start_ms) and end_ms - start_ms >= dwell_ms:
            return start_ms
    return math.nan


def summarize_grid(rows, contexts):
    grouped = defaultdict(list)
    for row in rows:
        if (
            row["trial_id"]
            and row["grid_cell"]
            and parse_bool(row["operational_external"])
        ):
            grouped[(row["trial_id"], row["grid_cell"])].append(row)

    output = []
    for (trial_id, cell), cell_rows in sorted(
        grouped.items(), key=lambda item: (int(item[0][0]), item[0][1])
    ):
        counts = Counter(row["semantic_class"] for row in cell_rows)
        total = len(cell_rows)
        output.append(
            {
                "trial_id": trial_id,
                "approach": contexts[trial_id]["approach"],
                "grid_cell": cell,
                "external_rows": total,
                "intruder_rows": counts[SEMANTIC_INTRUDER],
                "runway_rows": counts[SEMANTIC_RUNWAY],
                "other_external_rows": counts[SEMANTIC_OTHER],
                "unresolved_rows": counts[SEMANTIC_UNRESOLVED],
                "intruder_rate": counts[SEMANTIC_INTRUDER] / total,
                "runway_rate": counts[SEMANTIC_RUNWAY] / total,
                "other_rate": counts[SEMANTIC_OTHER] / total,
                "unresolved_rate": counts[SEMANTIC_UNRESOLVED] / total,
            }
        )
    return output


def validate_event_projection(rows, events):
    comparisons = []
    by_trial = defaultdict(list)
    for row in rows:
        if row["trial_id"]:
            by_trial[row["trial_id"]].append(row)
    for event in events:
        if event.get("event_type") not in {"INTRUDER_SPAWNED", "INTRUDER_VISUAL_OPPORTUNITY_ONSET"}:
            continue
        expected_x = parse_float(detail_value(event.get("detail", ""), "initial_screen_x_px"))
        expected_y = parse_float(detail_value(event.get("detail", ""), "initial_screen_y_px"))
        expected_span = parse_float(detail_value(event.get("detail", ""), "initial_estimated_span_px"))
        if event.get("event_type") == "INTRUDER_VISUAL_OPPORTUNITY_ONSET":
            expected_x = parse_float(detail_value(event.get("detail", ""), "screen_x_px"))
            expected_y = parse_float(detail_value(event.get("detail", ""), "screen_y_px"))
            expected_span = parse_float(detail_value(event.get("detail", ""), "estimated_span_px"))
        event_time = event_ms(event)
        candidates = [
            row
            for row in by_trial[event.get("trial_id", "")]
            if row["projection_valid"] == 1
        ]
        if not candidates or any(math.isnan(value) for value in (expected_x, expected_y, expected_span)):
            continue
        nearest = min(candidates, key=lambda row: abs(parse_float(row["pc_time_ms"]) - event_time))
        comparisons.append(
            {
                "trial_id": event.get("trial_id", ""),
                "event_type": event.get("event_type", ""),
                "event_time_ms": event_time,
                "nearest_gaze_time_ms": nearest["pc_time_ms"],
                "time_gap_ms": abs(parse_float(nearest["pc_time_ms"]) - event_time),
                "expected_x_px": expected_x,
                "computed_x_px": nearest["intruder_screen_x_px"],
                "x_error_px": parse_float(nearest["intruder_screen_x_px"]) - expected_x,
                "expected_y_px": expected_y,
                "computed_y_px": nearest["intruder_screen_y_px"],
                "y_error_px": parse_float(nearest["intruder_screen_y_px"]) - expected_y,
                "expected_span_px": expected_span,
                "computed_span_px": nearest["intruder_span_px"],
                "span_error_px": parse_float(nearest["intruder_span_px"]) - expected_span,
            }
        )
    return comparisons


def write_overview_svg(
    path,
    rows,
    contexts,
    screen_width,
    monitor_width,
    panel_top_y,
    grid_columns,
    grid_rows,
):
    canvas_width = 1500.0
    scale = canvas_width / screen_width
    panel_height = panel_top_y * scale
    label_height = 42.0
    panel_gap = 18.0
    legend_height = 60.0
    ordered_trials = sorted(contexts, key=lambda value: int(value))
    canvas_height = legend_height + len(ordered_trials) * (label_height + panel_height + panel_gap)
    colors = {
        SEMANTIC_INTRUDER: "#ef4444",
        SEMANTIC_RUNWAY: "#3b82f6",
        SEMANTIC_OTHER: "#94a3b8",
        SEMANTIC_UNRESOLVED: "#a855f7",
    }

    by_trial = defaultdict(list)
    for row in rows:
        if row["trial_id"]:
            by_trial[row["trial_id"]].append(row)

    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{canvas_width:.0f}" height="{canvas_height:.0f}" viewBox="0 0 {canvas_width:.0f} {canvas_height:.0f}">',
        '<rect width="100%" height="100%" fill="#0f172a"/>',
        '<text x="20" y="25" fill="#f8fafc" font-family="Segoe UI, sans-serif" font-size="18" font-weight="600">External gaze dynamic-AOI geometry overview</text>',
        '<text x="20" y="47" fill="#cbd5e1" font-family="Segoe UI, sans-serif" font-size="13">Spatial diagnostic only; colors are operational proxies, not direct awareness or intent.</text>',
    ]
    legend_x = 720
    for index, (label, color) in enumerate(colors.items()):
        x = legend_x + (index % 2) * 360
        y = 22 + (index // 2) * 24
        lines.append(f'<circle cx="{x}" cy="{y - 4}" r="4" fill="{color}"/>')
        lines.append(
            f'<text x="{x + 10}" y="{y}" fill="#e2e8f0" font-family="Segoe UI, sans-serif" font-size="12">{html.escape(label)}</text>'
        )

    current_y = legend_height
    for trial_id in ordered_trials:
        context = contexts[trial_id]
        trial_rows = by_trial[trial_id]
        label_y = current_y + 25
        panel_y = current_y + label_height
        title = f"Trial {trial_id}  {context['approach'].upper()}  full {screen_width} px surround exterior grid"
        lines.append(
            f'<text x="15" y="{label_y:.1f}" fill="#f8fafc" font-family="Segoe UI, sans-serif" font-size="15" font-weight="600">{html.escape(title)}</text>'
        )
        lines.append(
            f'<rect x="0" y="{panel_y:.1f}" width="{canvas_width:.1f}" height="{panel_height:.1f}" fill="#111827" stroke="#64748b" stroke-width="1"/>'
        )
        center_x = monitor_width * scale
        center_width = monitor_width * scale
        lines.append(
            f'<rect x="{center_x:.1f}" y="{panel_y:.1f}" width="{center_width:.1f}" height="{panel_height:.1f}" fill="#1e293b" opacity="0.75"/>'
        )
        for monitor_index in range(3):
            monitor_start = monitor_index * monitor_width
            for column in range(1, grid_columns):
                x = (monitor_start + monitor_width * column / grid_columns) * scale
                lines.append(
                    f'<line x1="{x:.1f}" y1="{panel_y:.1f}" x2="{x:.1f}" y2="{panel_y + panel_height:.1f}" stroke="#334155" stroke-width="0.7"/>'
                )
        for monitor_boundary in (float(monitor_width), float(monitor_width * 2)):
            x = monitor_boundary * scale
            lines.append(
                f'<line x1="{x:.1f}" y1="{panel_y:.1f}" x2="{x:.1f}" y2="{panel_y + panel_height:.1f}" stroke="#f8fafc" stroke-width="1.4"/>'
            )
        for grid_row_index in range(1, grid_rows):
            y = panel_y + panel_height * grid_row_index / grid_rows
            lines.append(
                f'<line x1="0" y1="{y:.1f}" x2="{canvas_width:.1f}" y2="{y:.1f}" stroke="#334155" stroke-width="0.7"/>'
            )

        intruder_points = []
        seen_samples = set()
        for row in trial_rows:
            sample = row["state_sample_index"]
            if not sample or sample in seen_samples or row["projection_visible"] != 1:
                continue
            x = parse_float(row["intruder_screen_x_px"])
            y = parse_float(row["intruder_screen_y_px"])
            if math.isnan(x) or math.isnan(y) or not (0 <= y < panel_top_y):
                continue
            seen_samples.add(sample)
            intruder_points.append((x * scale, panel_y + y * scale))
        if len(intruder_points) >= 2:
            points = " ".join(f"{x:.1f},{y:.1f}" for x, y in intruder_points)
            lines.append(
                f'<polyline points="{points}" fill="none" stroke="#f59e0b" stroke-width="2.2" opacity="0.9"/>'
            )

        runway_row = next(
            (
                row
                for row in trial_rows[len(trial_rows) // 2 :]
                if row["runway_projection_valid"] == 1 and row["runway_polygon_px"]
            ),
            None,
        )
        if runway_row:
            runway_polygon = parse_polygon_text(runway_row["runway_polygon_px"])
            if len(runway_polygon) == 4:
                points = " ".join(
                    f"{x * scale:.1f},{panel_y + y * scale:.1f}" for x, y in runway_polygon
                )
                lines.append(
                    f'<polygon points="{points}" fill="#2563eb" fill-opacity="0.18" stroke="#60a5fa" stroke-width="2"/>'
                )

        plotted = 0
        for row_index, row in enumerate(trial_rows):
            if row_index % 4 != 0 or not parse_bool(row["operational_external"]):
                continue
            x = parse_float(row["gaze_px"])
            y = parse_float(row["gaze_py"])
            if math.isnan(x) or math.isnan(y) or not (0 <= y < panel_top_y):
                continue
            color = colors.get(row["semantic_class"])
            if not color:
                continue
            lines.append(
                f'<circle cx="{x * scale:.1f}" cy="{panel_y + y * scale:.1f}" r="2.1" fill="{color}" fill-opacity="0.62"/>'
            )
            plotted += 1

        opportunity_rows = [
            row
            for row in trial_rows
            if row["projection_valid"] == 1
            and not math.isnan(parse_float(row["pc_time_ms"]))
        ]
        if opportunity_rows:
            opportunity_row = min(
                opportunity_rows,
                key=lambda row: abs(parse_float(row["pc_time_ms"]) - context["opportunity_ms"]),
            )
            left = parse_float(opportunity_row["intruder_aoi_left_px"])
            top = parse_float(opportunity_row["intruder_aoi_top_px"])
            right = parse_float(opportunity_row["intruder_aoi_right_px"])
            bottom = parse_float(opportunity_row["intruder_aoi_bottom_px"])
            if not any(math.isnan(value) for value in (left, top, right, bottom)):
                lines.append(
                    f'<rect x="{left * scale:.1f}" y="{panel_y + top * scale:.1f}" width="{(right - left) * scale:.1f}" height="{(bottom - top) * scale:.1f}" fill="none" stroke="#fbbf24" stroke-width="2" stroke-dasharray="5 3"/>'
                )
        lines.append(
            f'<text x="{canvas_width - 180:.1f}" y="{label_y:.1f}" fill="#94a3b8" font-family="Segoe UI, sans-serif" font-size="12">plotted gaze: {plotted}</text>'
        )
        current_y += label_height + panel_height + panel_gap

    lines.append("</svg>")
    Path(path).write_text("\n".join(lines), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Classify external gaze using a monitor grid, dynamic intruder AOI, "
            "and projected runway AOI. Outputs operational evidence, not awareness or intent."
        )
    )
    parser.add_argument("--state", required=True)
    parser.add_argument("--intruder", required=True)
    parser.add_argument("--events", required=True)
    parser.add_argument("--gaze-aoi", required=True)
    parser.add_argument("--runway-config", default=DEFAULT_RUNWAY_CONFIG)
    parser.add_argument("--runway-id", default="34")
    parser.add_argument("--output-prefix", required=True)
    parser.add_argument("--screen-width", type=int, default=DEFAULT_SCREEN_WIDTH)
    parser.add_argument("--screen-height", type=int, default=DEFAULT_SCREEN_HEIGHT)
    parser.add_argument("--monitor-width", type=int, default=DEFAULT_MONITOR_WIDTH)
    parser.add_argument("--panel-top-y", type=float, default=DEFAULT_PANEL_TOP_Y)
    parser.add_argument("--horizontal-fov-deg", type=float, default=DEFAULT_HORIZONTAL_FOV_DEG)
    parser.add_argument("--grid-columns", type=int, default=DEFAULT_GRID_COLUMNS)
    parser.add_argument("--grid-rows", type=int, default=DEFAULT_GRID_ROWS)
    parser.add_argument("--intruder-margin-px", type=float, default=DEFAULT_INTRUDER_MARGIN_PX)
    parser.add_argument("--runway-margin-px", type=float, default=DEFAULT_RUNWAY_MARGIN_PX)
    parser.add_argument("--dwell-ms", type=float, default=DEFAULT_DWELL_MS)
    parser.add_argument("--max-sample-gap-ms", type=float, default=DEFAULT_MAX_SAMPLE_GAP_MS)
    parser.add_argument("--max-state-gap-ms", type=float, default=DEFAULT_MAX_STATE_GAP_MS)
    args = parser.parse_args()

    if args.screen_width != args.monitor_width * 3:
        parser.error("current surround classifier requires three equal-width monitors")
    if args.grid_columns <= 0 or args.grid_rows <= 0:
        parser.error("grid dimensions must be positive")
    if min(args.intruder_margin_px, args.runway_margin_px, args.dwell_ms) < 0:
        parser.error("AOI margins and dwell must be non-negative")

    runway_rows = read_csv(args.runway_config)
    runway = next((row for row in runway_rows if row.get("runway_id") == args.runway_id), None)
    if runway is None:
        parser.error(f"runway {args.runway_id} not found in {args.runway_config}")

    state_rows = read_csv(args.state)
    intruder_rows = read_csv(args.intruder)
    events = read_csv(args.events)
    gaze_rows = read_csv(args.gaze_aoi)
    rows, episodes, contexts = classify_rows(
        state_rows,
        intruder_rows,
        events,
        gaze_rows,
        runway,
        args.screen_width,
        args.screen_height,
        args.monitor_width,
        args.panel_top_y,
        args.horizontal_fov_deg,
        args.grid_columns,
        args.grid_rows,
        args.intruder_margin_px,
        args.runway_margin_px,
        args.max_state_gap_ms,
        args.dwell_ms,
        args.max_sample_gap_ms,
    )
    summaries = summarize_trials(
        rows,
        episodes,
        contexts,
        args.dwell_ms,
        args.max_sample_gap_ms,
    )
    grid_summaries = summarize_grid(rows, contexts)
    projection_validation = validate_event_projection(rows, events)

    output_prefix = Path(args.output_prefix)
    output_prefix.parent.mkdir(parents=True, exist_ok=True)
    row_path = output_prefix.with_name(output_prefix.name + "_external_context_gaze.csv")
    episode_path = output_prefix.with_name(output_prefix.name + "_external_semantic_episodes.csv")
    summary_path = output_prefix.with_name(output_prefix.name + "_external_semantic_summary.csv")
    validation_path = output_prefix.with_name(output_prefix.name + "_projection_validation.csv")
    grid_path = output_prefix.with_name(output_prefix.name + "_external_grid_summary.csv")
    metadata_path = output_prefix.with_name(output_prefix.name + "_external_context_metadata.csv")
    overview_path = output_prefix.with_name(output_prefix.name + "_external_context_overview.svg")
    write_csv(row_path, rows, OUTPUT_FIELDS)
    write_csv(episode_path, episodes, EPISODE_FIELDS)
    write_csv(summary_path, summaries, SUMMARY_FIELDS)
    write_csv(grid_path, grid_summaries, GRID_SUMMARY_FIELDS)
    validation_fields = list(projection_validation[0].keys()) if projection_validation else ["trial_id"]
    write_csv(validation_path, projection_validation, validation_fields)
    metadata = {
        "classifier_version": CLASSIFIER_VERSION,
        "interpretation_guard": "operational proxy; not direct awareness or intent",
        "runway_airport_id": runway.get("airport_id", ""),
        "runway_id": runway.get("runway_id", ""),
        "screen_width_px": args.screen_width,
        "screen_height_px": args.screen_height,
        "monitor_width_px": args.monitor_width,
        "panel_top_y_px": args.panel_top_y,
        "horizontal_fov_deg": args.horizontal_fov_deg,
        "grid_columns_per_monitor": args.grid_columns,
        "grid_rows_per_monitor": args.grid_rows,
        "intruder_margin_px": args.intruder_margin_px,
        "runway_margin_px": args.runway_margin_px,
        "dwell_ms": args.dwell_ms,
        "max_sample_gap_ms": args.max_sample_gap_ms,
        "max_state_gap_ms": args.max_state_gap_ms,
    }
    write_csv(metadata_path, [metadata], list(metadata.keys()))
    write_overview_svg(
        overview_path,
        rows,
        contexts,
        args.screen_width,
        args.monitor_width,
        args.panel_top_y,
        args.grid_columns,
        args.grid_rows,
    )

    external_rows = [
        row
        for row in rows
        if row["trial_id"] and parse_bool(row["operational_external"])
    ]
    semantic_counts = Counter(row["semantic_class"] for row in external_rows)
    print("X-Plane external gaze context classification")
    print("Classifier:", CLASSIFIER_VERSION)
    print("Runway:", runway.get("airport_id"), runway.get("runway_id"))
    print("Grid:", f"{args.grid_columns}x{args.grid_rows} per monitor")
    print("Dwell:", f"{args.dwell_ms:.0f} ms")
    print("Intruder/runway margins:", f"{args.intruder_margin_px:.0f}/{args.runway_margin_px:.0f} px")
    print("External rows:", len(external_rows))
    for name in (SEMANTIC_INTRUDER, SEMANTIC_RUNWAY, SEMANTIC_OTHER, SEMANTIC_UNRESOLVED):
        count = semantic_counts[name]
        rate = count / len(external_rows) * 100.0 if external_rows else 0.0
        print(f"  {name}: {count} ({rate:.1f}%)")
    if projection_validation:
        absolute_x = [abs(parse_float(row["x_error_px"])) for row in projection_validation]
        absolute_y = [abs(parse_float(row["y_error_px"])) for row in projection_validation]
        absolute_span = [abs(parse_float(row["span_error_px"])) for row in projection_validation]
        print(
            "Projection validation median abs error px:",
            f"x={statistics.median(absolute_x):.3f}",
            f"y={statistics.median(absolute_y):.3f}",
            f"span={statistics.median(absolute_span):.3f}",
        )
    print("Wrote:", row_path)
    print("Wrote:", episode_path)
    print("Wrote:", summary_path)
    print("Wrote:", validation_path)
    print("Wrote:", grid_path)
    print("Wrote:", metadata_path)
    print("Wrote:", overview_path)
    print()
    print("Trial summary:")
    for row in summaries:
        def pct(key):
            value = row[key]
            return "n/a" if value == "" else f"{float(value) * 100.0:.1f}%"

        latency = row["opportunity_to_first_intruder_episode_s"]
        latency_text = "n/a" if latency == "" else f"{float(latency):.3f}s"
        print(
            f"trial {row['trial_id']} {row['approach']}",
            f"intruder={pct('intruder_rate_external')}",
            f"runway={pct('runway_rate_external')}",
            f"other={pct('other_rate_external')}",
            f"unresolved={pct('unresolved_rate_external')}",
            f"opportunity->intruder-evidence={latency_text}",
        )
    print()
    print("Interpretation guard: semantic classes are operational proxies, not direct awareness or intent labels.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
