import argparse
import csv
import html
import math
from pathlib import Path


DEFAULT_SCREEN_WIDTH = 1920
DEFAULT_SCREEN_HEIGHT = 1080
DEFAULT_AOI_PATH = "resources/cessna_instrument_aoi_260710.csv"
DEFAULT_BACKGROUND = "resources/cessnacokpit.png"
INSTRUMENT_AOIS = ["AIRSPEED", "ATTITUDE", "ALTITUDE", "HEADING", "VERTICAL_SPEED", "NAV_GPS"]
AOI_COLORS = {
    "OUTSIDE": "#2f80ed",
    "AIRSPEED": "#f2994a",
    "ATTITUDE": "#27ae60",
    "ALTITUDE": "#eb5757",
    "HEADING": "#9b51e0",
    "VERTICAL_SPEED": "#56ccf2",
    "NAV_GPS": "#f2c94c",
    "INVALID": "#9aa0a6",
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


def classify_aoi(px, py, aois):
    if math.isnan(px) or math.isnan(py):
        return "INVALID"
    for aoi in aois:
        if aoi["x1"] <= px <= aoi["x2"] and aoi["y1"] <= py <= aoi["y2"]:
            return aoi["name"]
    return "OUTSIDE"


def enrich_gaze(raw_rows, aois, screen_width, screen_height):
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
        aoi = classify_aoi(px, py, aois) if in_screen else "INVALID"
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

    lines = svg_header(width, height)
    add_background(lines, background, path, width, height)
    lines.append('<rect x="0" y="0" width="940" height="104" fill="rgba(0,0,0,0.65)"/>')
    lines.append(f'<text class="title" x="24" y="34">Trial {html.escape(str(trial_id))} Gaze Trajectory Around {html.escape(event_type)}</text>')
    lines.append(f'<text class="small" x="24" y="60">window: -{before_s:.1f}s to +{after_s:.1f}s, valid samples: {len(window_rows)}, plotted: {len(plot_rows)}</text>')
    lines.append('<text class="small" x="24" y="82">color: blue=start, red=end</text>')
    add_aoi_boxes(lines, aois)

    if len(plot_rows) >= 2:
        points = " ".join(f'{row["gaze_px"]:.1f},{row["gaze_py"]:.1f}' for row in plot_rows)
        lines.append(f'<polyline points="{points}" fill="none" stroke="#ffffff" stroke-opacity="0.45" stroke-width="2"/>')

    for index, row in enumerate(plot_rows):
        color = color_gradient(index, len(plot_rows))
        radius = 4.5 if index in (0, len(plot_rows) - 1) else 3.2
        lines.append(
            f'<circle cx="{row["gaze_px"]:.1f}" cy="{row["gaze_py"]:.1f}" r="{radius:.1f}" '
            f'fill="{color}" fill-opacity="0.82" stroke="white" stroke-opacity="0.35" stroke-width="0.6"/>'
        )

    for event_row in events:
        event_time = event_ms(event_row)
        if start_ms <= event_time <= end_ms:
            x = 24 + (event_time - start_ms) / (end_ms - start_ms) * 520
            y = height - 74
            label = html.escape(event_row.get("event_type", ""))
            lines.append(f'<line x1="{x:.1f}" y1="{y - 18}" x2="{x:.1f}" y2="{y + 18}" stroke="#ffdf3a" stroke-width="3"/>')
            lines.append(f'<text class="small" x="{x + 6:.1f}" y="{y:.1f}">{label}</text>')

    lines.append('</svg>')
    write_svg(path, lines)
    return path


def write_timeline_svg(path, gaze_rows, events, trial_id, width=1400, height=340):
    start_event = first_event(events, "TRIAL_START")
    end_event = first_event(events, "TRIAL_END")
    if not start_event:
        return None
    start_ms = event_ms(start_event)
    end_ms = event_ms(end_event) if end_event else max(row["pc_time_ms"] for row in gaze_rows)
    trial_rows = [row for row in gaze_rows if start_ms <= row["pc_time_ms"] <= end_ms]
    if not trial_rows:
        return None

    left = 80
    right = width - 40
    top = 86
    bar_height = 44
    duration = max(1.0, end_ms - start_ms)

    lines = svg_header(width, height)
    lines.append(f'<rect x="0" y="0" width="{width}" height="{height}" fill="#17212b"/>')
    lines.append(f'<text class="title" x="24" y="38">Trial {html.escape(str(trial_id))} AOI Timeline</text>')
    lines.append(f'<text class="small" x="24" y="64">A gaze sample outside six instrument AOIs is classified as OUTSIDE.</text>')

    current_aoi = None
    segment_start = None
    last_time = None
    for row in trial_rows:
        if row["valid_gaze"] != 1:
            aoi = "INVALID"
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

    lines.append(f'<line x1="{left}" y1="{top + bar_height + 24}" x2="{right}" y2="{top + bar_height + 24}" stroke="white" stroke-opacity="0.45"/>')
    for event_row in events:
        t = event_ms(event_row)
        if start_ms <= t <= end_ms:
            x = left + (t - start_ms) / duration * (right - left)
            label = html.escape(event_row.get("event_type", ""))
            lines.append(f'<line x1="{x:.1f}" y1="{top - 20}" x2="{x:.1f}" y2="{top + bar_height + 48}" stroke="#ffdf3a" stroke-width="2"/>')
            lines.append(f'<text class="small" transform="translate({x + 4:.1f},{top + bar_height + 68}) rotate(35)">{label}</text>')

    legend_x = left
    legend_y = height - 80
    for index, aoi in enumerate(["OUTSIDE"] + INSTRUMENT_AOIS + ["INVALID"]):
        x = legend_x + (index % 4) * 300
        y = legend_y + (index // 4) * 30
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
    parser.add_argument("--event", default="ADVISORY_SHOWN")
    parser.add_argument("--before", type=float, default=2.0)
    parser.add_argument("--after", type=float, default=5.0)
    parser.add_argument("--screen-width", type=int, default=DEFAULT_SCREEN_WIDTH)
    parser.add_argument("--screen-height", type=int, default=DEFAULT_SCREEN_HEIGHT)
    parser.add_argument("--max-points", type=int, default=5000)
    args = parser.parse_args()

    aois = load_aois(args.aoi)
    gaze_rows = enrich_gaze(read_csv(args.gaze), aois, args.screen_width, args.screen_height)
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

            timeline_path = output_prefix.with_name(output_prefix.name + f"_trial_{trial_id}_aoi_timeline.svg")
            written_timeline = write_timeline_svg(timeline_path, gaze_rows, selected_events, trial_id)
            if written_timeline:
                print("Wrote:", written_timeline)
        else:
            print("No trial events found for visualization.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
