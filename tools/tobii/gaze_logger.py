import argparse
import csv
import os
import sys
import time
from datetime import datetime


CSV_HEADER = [
    "session_id",
    "pc_time_ns",
    "pc_time_iso",
    "device_time_stamp",
    "system_time_stamp",
    "left_gaze_x",
    "left_gaze_y",
    "left_gaze_validity",
    "right_gaze_x",
    "right_gaze_y",
    "right_gaze_validity",
    "left_pupil_diameter",
    "left_pupil_validity",
    "right_pupil_diameter",
    "right_pupil_validity",
]


def valid_tuple(value):
    if value is None or len(value) < 2:
        return "", ""
    return value[0], value[1]


def main():
    parser = argparse.ArgumentParser(
        description="Log Tobii Pro SDK gaze data to CSV."
    )
    parser.add_argument("--session-id", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--duration-sec", type=float, default=60.0)
    args = parser.parse_args()

    try:
        import tobii_research as tr
    except ImportError:
        print("ERROR: tobii_research package is not installed for this Python.")
        print("Install Tobii Pro SDK Python binding for Python 3.10, then run again.")
        return 2

    trackers = tr.find_all_eyetrackers()
    if not trackers:
        print("No Tobii eye tracker found.")
        return 1

    tracker = trackers[0]
    print(f"Using tracker: {tracker.model} {tracker.serial_number} {tracker.address}")
    print(f"Writing CSV: {os.path.abspath(args.output)}")
    print(f"Duration: {args.duration_sec:.1f} sec")

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)

    rows_written = 0
    start = time.monotonic()

    with open(args.output, "w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow(CSV_HEADER)

        def on_gaze_data(gaze_data):
            nonlocal rows_written
            pc_time_ns = time.time_ns()
            pc_time_iso = datetime.now().isoformat(timespec="milliseconds")

            left_x, left_y = valid_tuple(
                gaze_data.get("left_gaze_point_on_display_area")
            )
            right_x, right_y = valid_tuple(
                gaze_data.get("right_gaze_point_on_display_area")
            )

            writer.writerow(
                [
                    args.session_id,
                    pc_time_ns,
                    pc_time_iso,
                    gaze_data.get("device_time_stamp", ""),
                    gaze_data.get("system_time_stamp", ""),
                    left_x,
                    left_y,
                    gaze_data.get("left_gaze_point_validity", ""),
                    right_x,
                    right_y,
                    gaze_data.get("right_gaze_point_validity", ""),
                    gaze_data.get("left_pupil_diameter", ""),
                    gaze_data.get("left_pupil_validity", ""),
                    gaze_data.get("right_pupil_diameter", ""),
                    gaze_data.get("right_pupil_validity", ""),
                ]
            )
            rows_written += 1

        tracker.subscribe_to(
            tr.EYETRACKER_GAZE_DATA,
            on_gaze_data,
            as_dictionary=True,
        )

        try:
            while time.monotonic() - start < args.duration_sec:
                time.sleep(0.1)
        finally:
            tracker.unsubscribe_from(tr.EYETRACKER_GAZE_DATA, on_gaze_data)

    print(f"Rows written: {rows_written}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
