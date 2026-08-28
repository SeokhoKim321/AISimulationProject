import argparse
import csv
import html
import math
from pathlib import Path


DEFAULT_SCREEN_WIDTH = 1920
DEFAULT_SCREEN_HEIGHT = 1080
DEFAULT_PANEL_TOP_Y = 600
DEFAULT_AOI_PATH = "resources/cessna_instrument_aoi_260710.csv"
DEFAULT_BACKGROUND = "AOI그림.png"
INSTRUMENT_AOIS = ["AIRSPEED", "ATTITUDE", "ALTITUDE", "HEADING", "VERTICAL_SPEED", "NAV_GPS"]
AOI_COLORS = {
    "OUTSIDE_VIEW": "#2f80ed",
    "PANEL_OTHER": "#7f8c8d",
    "AIRSPEED": "#f2994a",
    "ATTITUDE": "#27ae60",
    "ALTITUDE": "#eb5757",
    "HEADING": "#9b51e0",
    "VERTICAL_SPEED": "#56ccf2",
    "NAV_GPS": "#f2c94c",
    "UNTRACKED_OR_OFF_DISPLAY": "#9aa0a6",
}


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
        if row.get("aoi") not in INSTRUMENT_AOIS:
            continue
        aois.append(
            {
                "name": row["aoi"],
                "x1": parse_float(row["x1"]),
                "y1": parse_float(row["y1"]),
                "x2": parse_float(row["x2"]),
                "y2": parse_float(row["y2"]),
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


def enrich_gaze(raw_rows, aois, screen_width, screen_height, panel_top_y):
    rows = []
    for row in raw_rows:
        avg_x = parse_float(row.get("avg_gaze_x"))
        avg_y = parse_float(row.get("avg_gaze_y"))
        left_valid = parse_int(row.get("left_validity"))
        right_valid = parse_int(row.get("right_validity"))
        valid = (left_valid == 1 or right_valid == 1) and not math.isnan(avg_x) and not math.isnan(avg_y)
        in_screen = valid and 0.0 <= avg_x <= 1.0 and 0.0 <= avg_y <= 1.0
        px = avg_x * screen_width if in_screen else math.nan
        py = avg_y * screen_height if in_screen else math.nan
        pc_time_ms = parse_float(row.get("pc_time_sec")) * 1000.0
        aoi = classify_aoi(px, py, aois, panel_top_y) if in_screen else "UNTRACKED_OR_OFF_DISPLAY"
        out = dict(row)
        out.update(
            {
                "pc_time_ms": pc_time_ms,
                "gaze_px": px,
                "gaze_py": py,
                "valid_gaze": 1 if in_screen else 0,
                "aoi": aoi,
            }
        )
        rows.append(out)
    return rows


def event_ms(event):
    return parse_float(event.get("recorded_timestamp_ms"))


def choose_trial(events, requested_trial, event_type):
    if requested_trial:
        return str(requested_trial)
    for event in events:
        if event.get("event_type") == event_type and event.get("trial_id") not in ("", "0"):
            return event.get("trial_id")
    for event in events:
        if event.get("trial_id") not in ("", "0"):
            return event.get("trial_id")
    return ""


def trial_events(events, trial_id):
    return [event for event in events if event.get("trial_id") == str(trial_id)]


def first_event(events, event_type):
    for event in events:
        if event.get("event_type") == event_type:
            return event
    return None


def downsample(rows, max_points):
    if len(rows) <= max_points:
        return rows
    step = max(1, math.ceil(len(rows) / max_points))
    return rows[::step]


def color_gradient(index, count):
    if count <= 1:
        ratio = 0.0
    else:
        ratio = index / (count - 1)
    r = int(40 + 215 * ratio)
    g = int(110 * (1.0 - abs(ratio - 0.5) * 1.4))
    b = int(230 * (1.0 - ratio) + 35 * ratio)
    return f"#{r:02x}{max(0, min(255, g)):02x}{max(0, min(255, b)):02x}"


def svg_header(width, height):
    return [
        '<?xml version="1.0" encoding="UTF-8"?>',
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        '<style>',
        'text { font-family: Arial, sans-serif; }',
        '.title { font-size: 26px; font-weight: 700; fill: white; }',
        '.label { font-size: 18px; font-weight: 700; fill: white; }',
        '.small { font-size: 14px; fill: white; }',
        '.box { fill: none; stroke: #00ff38; stroke-width: 3; }',
        '.panel { fill: rgba(0,0,0,0.68); }',
        '</style>',
    ]


def add_background(lines, background, output_path, width, height):
    if not background:
        lines.append(f'<rect x="0" y="0" width="{width}" height="{height}" fill="#1d2730"/>')
        return
    try:
        href = Path(background).resolve().relative_to(Path(output_path).resolve().parent)
        href_text = href.as_posix()
    except ValueError:
        href_text = Path(background).resolve().as_uri()
    lines.append(f'<image href="{html.escape(href_text)}" x="0" y="0" width="{width}" height="{height}" preserveAspectRatio="none"/>')


def add_aoi_boxes(lines, aois):
    for aoi in aois:
        x = aoi["x1"]
        y = aoi["y1"]
        width = aoi["x2"] - aoi["x1"]
        height = aoi["y2"] - aoi["y1"]
        name = html.escape(aoi["name"])
        lines.append(f'<rect class="box" x="{x:.1f}" y="{y:.1f}" width="{width:.1f}" height="{height:.1f}"/>')
        lines.append(f'<rect class="panel" x="{x:.1f}" y="{max(0, y - 24):.1f}" width="{max(110, len(name) * 12):.1f}" height="26"/>')
        lines.append(f'<text class="label" x="{x + 4:.1f}" y="{max(20, y - 5):.1f}">{name}</text>')


def write_svg(path, lines):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with Path(path).open("w", encoding="utf-8") as file:
        file.write("\n".join(lines))


def write_scatter_svg(path, gaze_rows, aois, background, width, height, max_points):
    valid_rows = [row for row in gaze_rows if row["valid_gaze"] == 1]
    plot_rows = downsample(valid_rows, max_points)
    lines = svg_header(width, height)
    add_background(lines, background, path, width, height)
    lines.append('<rect x="0" y="0" width="760" height="80" fill="rgba(0,0,0,0.6)"/>')
    lines.append('<text class="title" x="24" y="34">Tobii Gaze Scatter</text>')
    lines.append(f'<text class="small" x="24" y="60">valid samples: {len(valid_rows)} / plotted: {len(plot_rows)}</text>')
    add_aoi_boxes(lines, aois)
    for index, row in enumerate(plot_rows):
        color = color_gradient(index, len(plot_rows))
        lines.append(
            f'<circle cx="{row["gaze_px"]:.1f}" cy="{row["gaze_py"]:.1f}" r="3.0" '
            f'fill="{color}" fill-opacity="0.38" stroke="none"/>'
        )
    lines.append('</svg>')
    write_svg(path, lines)


def write_window_svg(path, gaze_rows, events, trial_id, event_type, before_s, after_s, aois, background, width, height, max_points):
    event = first_event(events, event_type)
    if not event:
        return None
    center_ms = event_ms(event)
    start_ms = center_ms - before_s * 1000.0
    end_ms = center_ms + after_s * 1000.0
    window_rows = [row for row in gaze_rows if row["valid_gaze"] == 1 and start_ms <= row["pc_time_ms"] <= end_ms]
    plot_rows = downsample(window_rows, max_points)

    centroid_window_ms = 200.0
    centroids = []
    slice_start_ms = start_ms
    while slice_start_ms < end_ms:
        slice_end_ms = min(end_ms, slice_start_ms + centroid_window_ms)
        slice_rows = [row for row in window_rows if slice_start_ms <= row["pc_time_ms"] < slice_end_ms]
        if slice_rows:
            centroids.append(
                {
                    "x": sum(row["gaze_px"] for row in slice_rows) / len(slice_rows),
                    "y": sum(row["gaze_py"] for row in slice_rows) / len(slice_rows),
                    "midpoint_s": ((slice_start_ms + slice_end_ms) / 2.0 - center_ms) / 1000.0,
                }
            )
        slice_start_ms = slice_end_ms

    footer_height = 250
    canvas_height = height + footer_height
    lines = svg_header(width, canvas_height)
    lines.append(
        '<defs><marker id="trajectory-arrow" markerWidth="12" markerHeight="12" refX="9" refY="4" '
        'orient="auto" markerUnits="strokeWidth"><path d="M0,0 L0,8 L10,4 z" fill="#ffffff" '
        'stroke="#111820" stroke-width="1"/></marker></defs>'
    )
    add_background(lines, background, path, width, height)
    lines.append('<rect x="0" y="0" width="940" height="104" fill="rgba(0,0,0,0.65)"/>')
    lines.append(f'<text class="title" x="24" y="34">Trial {html.escape(str(trial_id))} Gaze Trajectory Around {html.escape(event_type)}</text>')
    lines.append(f'<text class="small" x="24" y="60">window: -{before_s:.1f}s to +{after_s:.1f}s, valid samples: {len(window_rows)}, plotted: {len(plot_rows)}</text>')
    lines.append('<text class="small" x="24" y="82">small dots: raw gaze; large circles and arrows: consecutive 200 ms gaze centroids</text>')
    add_aoi_boxes(lines, aois)

    for index, row in enumerate(plot_rows):
        color = color_gradient(index, len(plot_rows))
        lines.append(
            f'<circle cx="{row["gaze_px"]:.1f}" cy="{row["gaze_py"]:.1f}" r="2.7" '
            f'fill="{color}" fill-opacity="0.34" stroke="none"/>'
        )

    for index, (previous, current) in enumerate(zip(centroids, centroids[1:])):
        lines.append(
            f'<line x1="{previous["x"]:.1f}" y1="{previous["y"]:.1f}" '
            f'x2="{current["x"]:.1f}" y2="{current["y"]:.1f}" '
            f'stroke="#111820" stroke-opacity="0.9" stroke-width="9"/>'
        )
        lines.append(
            f'<line x1="{previous["x"]:.1f}" y1="{previous["y"]:.1f}" '
            f'x2="{current["x"]:.1f}" y2="{current["y"]:.1f}" '
            f'stroke="#ffffff" stroke-opacity="0.95" stroke-width="4" marker-end="url(#trajectory-arrow)"/>'
        )

    for index, centroid in enumerate(centroids):
        color = color_gradient(index, len(centroids))
        lines.append(
            f'<circle cx="{centroid["x"]:.1f}" cy="{centroid["y"]:.1f}" r="10" '
            f'fill="{color}" fill-opacity="0.95" stroke="#ffffff" stroke-width="2.5"/>'
        )

    visible_events = [event_row for event_row in events if start_ms <= event_ms(event_row) <= end_ms]
    footer_top = height
    axis_left = 40
    axis_right = width - 40
    axis_y = footer_top + 48
    lines.append(f'<rect x="0" y="{footer_top}" width="{width}" height="{footer_height}" fill="#111820"/>')
    lines.append(f'<text class="label" x="24" y="{footer_top + 25}">Events relative to {html.escape(event_type)}</text>')
    lines.append(f'<line x1="{axis_left}" y1="{axis_y}" x2="{axis_right}" y2="{axis_y}" stroke="#d7e1ea" stroke-width="2"/>')

    event_items = []
    for event_number, event_row in enumerate(visible_events, start=1):
        event_time = event_ms(event_row)
        x = axis_left + (event_time - start_ms) / (end_ms - start_ms) * (axis_right - axis_left)
        lines.append(f'<line x1="{x:.1f}" y1="{axis_y - 9}" x2="{x:.1f}" y2="{axis_y + 9}" stroke="#ffdf3a" stroke-width="2"/>')
        lines.append(f'<circle cx="{x:.1f}" cy="{axis_y}" r="11" fill="#ffdf3a" stroke="#111" stroke-width="2"/>')
        lines.append(f'<text x="{x:.1f}" y="{axis_y + 4:.1f}" text-anchor="middle" font-size="10" font-weight="700" fill="#111">{event_number}</text>')
        event_items.append(
            (
                event_number,
                (event_time - center_ms) / 1000.0,
                event_row.get("event_type", ""),
            )
        )

    columns = 3
    rows_per_column = max(1, math.ceil(len(event_items) / columns))
    column_width = width / columns
    list_top = footer_top + 86
    for index, (event_number, relative_s, event_name) in enumerate(event_items):
        column = index // rows_per_column
        row_index = index % rows_per_column
        x = 28 + column * column_width
        y = list_top + row_index * 31
        lines.append(f'<circle cx="{x + 10:.1f}" cy="{y - 5:.1f}" r="10" fill="#ffdf3a" stroke="#111" stroke-width="2"/>')
        lines.append(f'<text x="{x + 10:.1f}" y="{y - 1:.1f}" text-anchor="middle" font-size="10" font-weight="700" fill="#111">{event_number}</text>')
        lines.append(f'<text class="small" x="{x + 28:.1f}" y="{y:.1f}">{relative_s:+.3f}s  {html.escape(event_name)}</text>')

    lines.append('</svg>')
    write_svg(path, lines)
    return path


def write_time_bins_svg(path, gaze_rows, events, trial_id, event_type, aois, background, screen_width, screen_height, max_points):
    event = first_event(events, event_type)
    if not event:
        return None

    bins = [(-2.0, -1.0), (-1.0, 0.0), (0.0, 0.5), (0.5, 1.0), (1.0, 2.0), (2.0, 3.0), (3.0, 4.0), (4.0, 5.0)]
    columns = 4
    rows = math.ceil(len(bins) / columns)
    panel_width = 460
    panel_height = panel_width * screen_height / screen_width
    gap = 20
    title_height = 78
    label_height = 42
    canvas_width = columns * panel_width + (columns + 1) * gap
    canvas_height = title_height + rows * (panel_height + label_height) + (rows + 1) * gap
    center_ms = event_ms(event)
    centroid_window_ms = 200.0

    lines = svg_header(canvas_width, canvas_height)
    lines.append('<defs><marker id="gaze-arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth"><path d="M0,0 L0,6 L9,3 z" fill="white"/></marker></defs>')
    lines.append(f'<rect x="0" y="0" width="{canvas_width}" height="{canvas_height}" fill="#111820"/>')
    lines.append(f'<text class="title" x="20" y="36">Trial {html.escape(str(trial_id))} Fixed Time Bins Around {html.escape(event_type)}</text>')
    lines.append('<text class="small" x="20" y="62">Circles are 200 ms binned gaze centroids; labels are event-relative midpoint times and arrows show consecutive movement.</text>')

    for index, (start_s, end_s) in enumerate(bins):
        column = index % columns
        row_index = index // columns
        panel_x = gap + column * (panel_width + gap)
        panel_y = title_height + gap + row_index * (panel_height + label_height + gap)
        start_ms = center_ms + start_s * 1000.0
        end_ms = center_ms + end_s * 1000.0
        all_bin_rows = [
            gaze_row
            for gaze_row in gaze_rows
            if start_ms <= gaze_row["pc_time_ms"] < end_ms
        ]
        bin_rows = [gaze_row for gaze_row in all_bin_rows if gaze_row["valid_gaze"] == 1]
        untracked_count = len(all_bin_rows) - len(bin_rows)
        scale = panel_width / screen_width

        centroids = []
        slice_start_ms = start_ms
        slice_index = 0
        total_slices = math.ceil((end_ms - start_ms) / centroid_window_ms)
        while slice_start_ms < end_ms:
            slice_end_ms = min(end_ms, slice_start_ms + centroid_window_ms)
            slice_rows = [
                gaze_row
                for gaze_row in bin_rows
                if slice_start_ms <= gaze_row["pc_time_ms"] < slice_end_ms
            ]
            if slice_rows:
                centroid_x = sum(gaze_row["gaze_px"] for gaze_row in slice_rows) / len(slice_rows)
                centroid_y = sum(gaze_row["gaze_py"] for gaze_row in slice_rows) / len(slice_rows)
                midpoint_s = ((slice_start_ms + slice_end_ms) / 2.0 - center_ms) / 1000.0
                centroids.append(
                    {
                        "slice_index": slice_index,
                        "x": centroid_x,
                        "y": centroid_y,
                        "count": len(slice_rows),
                        "midpoint_s": midpoint_s,
                        "color": color_gradient(slice_index, total_slices),
                    }
                )
            slice_start_ms = slice_end_ms
            slice_index += 1

        lines.append(f'<g transform="translate({panel_x:.1f},{panel_y:.1f}) scale({scale:.8f})">')
        add_background(lines, background, path, screen_width, screen_height)
        add_aoi_boxes(lines, aois)
        for previous, current in zip(centroids, centroids[1:]):
            if current["slice_index"] != previous["slice_index"] + 1:
                continue
            lines.append(
                f'<line x1="{previous["x"]:.1f}" y1="{previous["y"]:.1f}" '
                f'x2="{current["x"]:.1f}" y2="{current["y"]:.1f}" '
                f'stroke="white" stroke-opacity="0.86" stroke-width="5" marker-end="url(#gaze-arrow)"/>'
            )
        for centroid in centroids:
            radius = 14.0 + min(8.0, centroid["count"] * 0.6)
            lines.append(
                f'<circle cx="{centroid["x"]:.1f}" cy="{centroid["y"]:.1f}" r="{radius:.1f}" '
                f'fill="{centroid["color"]}" fill-opacity="0.92" stroke="white" stroke-width="3"/>'
            )
            lines.append(
                f'<text x="{centroid["x"] + radius + 7:.1f}" y="{centroid["y"] - radius - 5:.1f}" '
                f'font-size="42" font-weight="700" fill="white" stroke="#111" stroke-width="5" paint-order="stroke">'
                f'{centroid["midpoint_s"]:+.1f}s</text>'
            )
        lines.append('</g>')
        lines.append(
            f'<rect x="{panel_x:.1f}" y="{panel_y + panel_height:.1f}" width="{panel_width:.1f}" height="{label_height:.1f}" fill="#263442"/>'
        )
        label = f'{start_s:+.1f}s to {end_s:+.1f}s'
        lines.append(
            f'<text class="label" x="{panel_x + 12:.1f}" y="{panel_y + panel_height + 27:.1f}">{label}</text>'
        )
        lines.append(
            f'<text class="small" x="{panel_x + 155:.1f}" y="{panel_y + panel_height + 27:.1f}">front: {len(bin_rows)}/{len(all_bin_rows)}, untracked/off: {untracked_count}, centroids: {len(centroids)}</text>'
        )

    lines.append('</svg>')
    write_svg(path, lines)
    return path


def write_timeline_svg(path, gaze_rows, events, trial_id, panel_top_y, width=1400):
    start_event = first_event(events, "TRIAL_START")
    end_event = first_event(events, "TRIAL_END")
    if not start_event:
        return None
    start_ms = event_ms(start_event)
    end_ms = event_ms(end_event) if end_event else max(row["pc_time_ms"] for row in gaze_rows)
    trial_rows = [row for row in gaze_rows if start_ms <= row["pc_time_ms"] <= end_ms]
    if not trial_rows:
        return None

    visible_events = [event_row for event_row in events if start_ms <= event_ms(event_row) <= end_ms]
    event_columns = 2
    event_list_rows = math.ceil(len(visible_events) / event_columns)
    aoi_names = ["OUTSIDE_VIEW", "PANEL_OTHER"] + INSTRUMENT_AOIS + ["UNTRACKED_OR_OFF_DISPLAY"]
    aoi_columns = 3
    aoi_rows = math.ceil(len(aoi_names) / aoi_columns)

    left = 80
    right = width - 40
    top = 150
    bar_height = 44
    duration = max(1.0, end_ms - start_ms)
    event_list_top = top + bar_height + 78
    legend_top = event_list_top + event_list_rows * 26 + 38
    height = legend_top + aoi_rows * 30 + 36

    lines = svg_header(width, height)
    lines.append(f'<rect x="0" y="0" width="{width}" height="{height}" fill="#17212b"/>')
    lines.append(f'<text class="title" x="24" y="38">Trial {html.escape(str(trial_id))} AOI Timeline</text>')
    lines.append(f'<text class="small" x="24" y="64">Non-instrument gaze: OUTSIDE_VIEW above y={panel_top_y:g}, PANEL_OTHER at or below y={panel_top_y:g}.</text>')
    lines.append('<text class="small" x="24" y="86">Numbered markers correspond to the event list below; times are relative to TRIAL_START.</text>')

    current_aoi = None
    segment_start = None
    last_time = None
    for row in trial_rows:
        if row["valid_gaze"] != 1:
            aoi = "UNTRACKED_OR_OFF_DISPLAY"
        else:
            aoi = row["aoi"]
        t = row["pc_time_ms"]
        if current_aoi is None:
            current_aoi = aoi
            segment_start = t
        elif aoi != current_aoi:
            add_timeline_segment(lines, current_aoi, segment_start, last_time or t, start_ms, duration, left, right, top, bar_height)
            current_aoi = aoi
            segment_start = t
        last_time = t
    add_timeline_segment(lines, current_aoi, segment_start, last_time or end_ms, start_ms, duration, left, right, top, bar_height)

    axis_y = top + bar_height + 24
    lines.append(f'<line x1="{left}" y1="{axis_y}" x2="{right}" y2="{axis_y}" stroke="white" stroke-opacity="0.45"/>')
    for tick_index in range(5):
        ratio = tick_index / 4.0
        x = left + ratio * (right - left)
        relative_s = ratio * duration / 1000.0
        lines.append(f'<line x1="{x:.1f}" y1="{axis_y - 5}" x2="{x:.1f}" y2="{axis_y + 5}" stroke="white"/>')
        lines.append(f'<text class="small" x="{x - 12:.1f}" y="{axis_y + 22}">{relative_s:.1f}s</text>')

    last_x_by_lane = [-math.inf, -math.inf, -math.inf, -math.inf]
    event_items = []
    for event_number, event_row in enumerate(visible_events, start=1):
        t = event_ms(event_row)
        x = left + (t - start_ms) / duration * (right - left)
        lane = 0
        for candidate_lane, last_x in enumerate(last_x_by_lane):
            if x - last_x >= 30:
                lane = candidate_lane
                break
        last_x_by_lane[lane] = x
        marker_y = top - 18 - lane * 25
        lines.append(f'<line x1="{x:.1f}" y1="{marker_y + 10:.1f}" x2="{x:.1f}" y2="{top + bar_height}" stroke="#ffdf3a" stroke-opacity="0.8" stroke-width="2"/>')
        lines.append(f'<circle cx="{x:.1f}" cy="{marker_y:.1f}" r="10" fill="#ffdf3a" stroke="#111" stroke-width="2"/>')
        lines.append(f'<text x="{x:.1f}" y="{marker_y + 4:.1f}" text-anchor="middle" font-size="11" font-weight="700" fill="#111">{event_number}</text>')
        event_items.append((event_number, (t - start_ms) / 1000.0, event_row.get("event_type", "")))

    lines.append(f'<text class="label" x="{left}" y="{event_list_top - 18}">Events</text>')
    rows_per_column = math.ceil(len(event_items) / event_columns)
    for index, (event_number, relative_s, event_name) in enumerate(event_items):
        column = index // rows_per_column
        row_index = index % rows_per_column
        x = left + column * 650
        y = event_list_top + row_index * 26
        lines.append(f'<circle cx="{x + 10}" cy="{y - 5}" r="9" fill="#ffdf3a"/>')
        lines.append(f'<text x="{x + 10}" y="{y - 1}" text-anchor="middle" font-size="10" font-weight="700" fill="#111">{event_number}</text>')
        lines.append(f'<text class="small" x="{x + 28}" y="{y}">{relative_s:+.3f}s  {html.escape(event_name)}</text>')

    lines.append(f'<text class="label" x="{left}" y="{legend_top - 18}">AOI colors</text>')
    for index, aoi in enumerate(aoi_names):
        x = left + (index % aoi_columns) * 420
        y = legend_top + (index // aoi_columns) * 30
        color = AOI_COLORS.get(aoi, "#ffffff")
        lines.append(f'<rect x="{x}" y="{y - 14}" width="18" height="18" fill="{color}"/>')
        lines.append(f'<text class="small" x="{x + 26}" y="{y}">{html.escape(aoi)}</text>')

    lines.append('</svg>')
    write_svg(path, lines)
    return path


def add_timeline_segment(lines, aoi, start_t, end_t, trial_start, duration, left, right, top, bar_height):
    if start_t is None or end_t is None:
        return
    x1 = left + (start_t - trial_start) / duration * (right - left)
    x2 = left + (end_t - trial_start) / duration * (right - left)
    width = max(1.0, x2 - x1)
    color = AOI_COLORS.get(aoi, "#ffffff")
    lines.append(f'<rect x="{x1:.1f}" y="{top}" width="{width:.1f}" height="{bar_height}" fill="{color}" fill-opacity="0.82"/>')


def main():
    parser = argparse.ArgumentParser(description="Create SVG gaze visualizations from Tobii gaze CSV and X-Plane event CSV.")
    parser.add_argument("--gaze", required=True)
    parser.add_argument("--events")
    parser.add_argument("--aoi", default=DEFAULT_AOI_PATH)
    parser.add_argument("--background", default=DEFAULT_BACKGROUND)
    parser.add_argument("--output-prefix", required=True)
    parser.add_argument("--trial")
    parser.add_argument("--event", default="INTRUDER_VISUAL_OPPORTUNITY_ONSET")
    parser.add_argument("--before", type=float, default=2.0)
    parser.add_argument("--after", type=float, default=5.0)
    parser.add_argument("--screen-width", type=int, default=DEFAULT_SCREEN_WIDTH)
    parser.add_argument("--screen-height", type=int, default=DEFAULT_SCREEN_HEIGHT)
    parser.add_argument("--panel-top-y", type=float, default=DEFAULT_PANEL_TOP_Y)
    parser.add_argument("--max-points", type=int, default=5000)
    args = parser.parse_args()

    aois = load_aois(args.aoi)
    gaze_rows = enrich_gaze(read_csv(args.gaze), aois, args.screen_width, args.screen_height, args.panel_top_y)
    events = read_csv(args.events) if args.events else []
    output_prefix = Path(args.output_prefix)

    scatter_path = output_prefix.with_name(output_prefix.name + "_gaze_scatter.svg")
    write_scatter_svg(scatter_path, gaze_rows, aois, args.background, args.screen_width, args.screen_height, args.max_points)
    print("Wrote:", scatter_path)

    if events:
        trial_id = choose_trial(events, args.trial, args.event)
        selected_events = trial_events(events, trial_id)
        if selected_events:
            window_path = output_prefix.with_name(output_prefix.name + f"_trial_{trial_id}_{args.event.lower()}_trajectory.svg")
            written_window = write_window_svg(
                window_path,
                gaze_rows,
                selected_events,
                trial_id,
                args.event,
                args.before,
                args.after,
                aois,
                args.background,
                args.screen_width,
                args.screen_height,
                args.max_points,
            )
            if written_window:
                print("Wrote:", written_window)

            time_bins_path = output_prefix.with_name(output_prefix.name + f"_trial_{trial_id}_{args.event.lower()}_time_bins.svg")
            written_time_bins = write_time_bins_svg(
                time_bins_path,
                gaze_rows,
                selected_events,
                trial_id,
                args.event,
                aois,
                args.background,
                args.screen_width,
                args.screen_height,
                args.max_points,
            )
            if written_time_bins:
                print("Wrote:", written_time_bins)

            timeline_path = output_prefix.with_name(output_prefix.name + f"_trial_{trial_id}_aoi_timeline.svg")
            written_timeline = write_timeline_svg(timeline_path, gaze_rows, selected_events, trial_id, args.panel_top_y)
            if written_timeline:
                print("Wrote:", written_timeline)
        else:
            print("No trial events found for visualization.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
