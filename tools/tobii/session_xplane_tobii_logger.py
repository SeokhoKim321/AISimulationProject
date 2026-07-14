import csv
import math
import queue
import sys
import time
from datetime import datetime
from pathlib import Path

import tobii_research as tr


SESSION_PREFIX = "session_xplane_tobii"
RECORD_SECONDS = 600
PRINT_FIRST_ROWS = 5


FIELDNAMES = [
    "session_id",
    "pc_time_sec",
    "pc_time_ns",
    "pc_time_iso",
    "device_time_stamp",
    "system_time_stamp",
    "left_gaze_x",
    "left_gaze_y",
    "right_gaze_x",
    "right_gaze_y",
    "avg_gaze_x",
    "avg_gaze_y",
    "left_validity",
    "right_validity",
    "left_pupil_diameter",
    "right_pupil_diameter",
    "left_pupil_validity",
    "right_pupil_validity",
]


def point_or_nan(gaze_data, key):
    value = gaze_data.get(key)
    if value is None or len(value) < 2:
        return math.nan, math.nan
    return value[0], value[1]


def average_valid_gaze(left_x, left_y, left_valid, right_x, right_y, right_valid):
    points = []
    if left_valid == 1 and not math.isnan(left_x) and not math.isnan(left_y):
        points.append((left_x, left_y))
    if right_valid == 1 and not math.isnan(right_x) and not math.isnan(right_y):
        points.append((right_x, right_y))
    if not points:
        return math.nan, math.nan
    return (
        sum(point[0] for point in points) / len(points),
        sum(point[1] for point in points) / len(points),
    )


def main():
    started_at = datetime.now()
    session_id = f"{SESSION_PREFIX}_{started_at.strftime('%Y%m%d_%H%M%S')}"
    output_csv = Path(f"{session_id}_gaze.csv")

    trackers = tr.find_all_eyetrackers()
    print("trackers:", len(trackers))

    if not trackers:
        print("No tracker found.")
        return 1

    tracker = trackers[0]
    print("Using:", tracker.model, tracker.serial_number)
    print("Address:", tracker.address)
    print("Firmware:", tracker.firmware_version)
    print("Session:", session_id)
    print("Output :", output_csv)

    data_queue = queue.Queue()
    sample_count = 0

    def gaze_callback(gaze_data):
        nonlocal sample_count
        sample_count += 1
        data_queue.put(gaze_data)

    with output_csv.open("x", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=FIELDNAMES)
        writer.writeheader()

        tracker.subscribe_to(
            tr.EYETRACKER_GAZE_DATA,
            gaze_callback,
            as_dictionary=True,
        )

        print("Collecting gaze for", RECORD_SECONDS, "seconds...")
        print("Press Ctrl+C to stop early.")

        start_time = time.time()
        printed = 0

        try:
            while time.time() - start_time < RECORD_SECONDS:
                try:
                    gaze_data = data_queue.get(timeout=0.2)
                except queue.Empty:
                    continue

                left_x, left_y = point_or_nan(
                    gaze_data, "left_gaze_point_on_display_area"
                )
                right_x, right_y = point_or_nan(
                    gaze_data, "right_gaze_point_on_display_area"
                )
                left_valid = gaze_data.get("left_gaze_point_validity", 0)
                right_valid = gaze_data.get("right_gaze_point_validity", 0)
                avg_x, avg_y = average_valid_gaze(
                    left_x,
                    left_y,
                    left_valid,
                    right_x,
                    right_y,
                    right_valid,
                )

                row = {
                    "session_id": session_id,
                    "pc_time_sec": time.time(),
                    "pc_time_ns": time.time_ns(),
                    "pc_time_iso": datetime.now().isoformat(timespec="milliseconds"),
                    "device_time_stamp": gaze_data.get("device_time_stamp", ""),
                    "system_time_stamp": gaze_data.get("system_time_stamp", ""),
                    "left_gaze_x": left_x,
                    "left_gaze_y": left_y,
                    "right_gaze_x": right_x,
                    "right_gaze_y": right_y,
                    "avg_gaze_x": avg_x,
                    "avg_gaze_y": avg_y,
                    "left_validity": left_valid,
                    "right_validity": right_valid,
                    "left_pupil_diameter": gaze_data.get("left_pupil_diameter", ""),
                    "right_pupil_diameter": gaze_data.get("right_pupil_diameter", ""),
                    "left_pupil_validity": gaze_data.get("left_pupil_validity", ""),
                    "right_pupil_validity": gaze_data.get("right_pupil_validity", ""),
                }

                writer.writerow(row)

                if printed < PRINT_FIRST_ROWS:
                    print(row)
                    printed += 1

        except KeyboardInterrupt:
            print("Stopped by user.")
        finally:
            tracker.unsubscribe_from(
                tr.EYETRACKER_GAZE_DATA,
                gaze_callback,
            )

    print("gaze samples:", sample_count)
    print("Saved:", output_csv)
    return 0


if __name__ == "__main__":
    sys.exit(main())
