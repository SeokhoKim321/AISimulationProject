#!/usr/bin/env python3
"""
Tobii Pro Spark multi-monitor feasibility validation.

This tool does not configure NVIDIA Surround or calibrate the eye tracker.
It presents fixed gaze targets, records the Tobii Pro SDK stream, and exports
epoch/target/monitor metrics for a center-only baseline and a Surround-wide run.
"""

import argparse
import csv
import ctypes
import json
import math
import os
import random
import statistics
import sys
import threading
import time
from queue import Queue
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from tkinter import BOTH, Canvas, Tk
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple


TOOL_VERSION = "260724_v2"
SCHEMA_VERSION = 2
DEFAULT_PROJECT_ROOT = Path(r"C:\Users\ACSL-SERVER\Desktop\AISimulationProject")
DEFAULT_SDK_DIR = Path(
    r"C:\Users\ACSL-SERVER\Desktop\김석호"
    r"\TobiiPro.SDK.Python.Windows_2.1.0.1\64"
)
DEFAULT_TRACKER_SERIAL = "TPE01-100206101311"
DEFAULT_OUTPUT_SUBDIR = Path("logs") / "tobii" / "multimonitor_validation"
DLL_DIRECTORY_HANDLES: List[Any] = []

TARGET_LOCAL_POSITIONS = (
    ("UL", 0.20, 0.20),
    ("UR", 0.80, 0.20),
    ("C", 0.50, 0.50),
    ("LL", 0.20, 0.80),
    ("LR", 0.80, 0.80),
)

RAW_FIELDS = [
    "session_id",
    "condition",
    "run_label",
    "pc_time_ns",
    "pc_monotonic_ns",
    "pc_time_iso",
    "device_time_stamp",
    "system_time_stamp",
    "block",
    "epoch",
    "sequence",
    "phase",
    "phase_start_ns",
    "phase_start_monotonic_ns",
    "target_on_ns",
    "target_on_monotonic_ns",
    "target_id",
    "target_monitor",
    "target_position",
    "target_local_x",
    "target_local_y",
    "target_x_px",
    "target_y_px",
    "previous_target_id",
    "previous_target_monitor",
    "left_gaze_x",
    "left_gaze_y",
    "left_gaze_validity",
    "right_gaze_x",
    "right_gaze_y",
    "right_gaze_validity",
    "left_gaze_origin_user_x",
    "left_gaze_origin_user_y",
    "left_gaze_origin_user_z",
    "left_gaze_origin_validity",
    "right_gaze_origin_user_x",
    "right_gaze_origin_user_y",
    "right_gaze_origin_user_z",
    "right_gaze_origin_validity",
    "left_pupil_diameter",
    "left_pupil_validity",
    "right_pupil_diameter",
    "right_pupil_validity",
]

ANNOTATED_FIELDS = RAW_FIELDS + [
    "any_eye_valid",
    "binocular_valid",
    "avg_gaze_x",
    "avg_gaze_y",
    "mapped_gaze_x_px",
    "mapped_gaze_y_px",
    "in_active_display",
    "predicted_monitor",
    "correct_monitor",
    "error_px",
    "error_deg",
    "phase_elapsed_ms",
    "target_elapsed_ms",
]

EPOCH_FIELDS = [
    "session_id",
    "condition",
    "block",
    "epoch",
    "sequence",
    "target_id",
    "target_monitor",
    "target_position",
    "target_x_px",
    "target_y_px",
    "previous_target_id",
    "previous_target_monitor",
    "is_block_first",
    "received_n",
    "expected_n",
    "measure_duration_ms",
    "event_integrity",
    "sample_coverage",
    "sample_coverage_ok",
    "valid_n",
    "any_eye_valid_rate",
    "binocular_valid_rate",
    "in_display_valid_rate",
    "correct_monitor_valid_rate",
    "usable_monitor_rate",
    "centroid_x_px",
    "centroid_y_px",
    "centroid_error_px",
    "median_error_px",
    "p95_error_px",
    "median_error_deg",
    "p95_error_deg",
    "precision_rms_px",
    "dispersion_p95_px",
    "first_valid_latency_ms",
    "first_sustained_correct_latency_ms",
    "center_recovery_latency_ms",
    "invalid_episode_count_ge_100ms",
    "invalid_episode_total_ms",
    "longest_invalid_episode_ms",
    "invalid_time_rate_ge_100ms",
    "correct_monitor_acquired_within_1s",
]

TARGET_SUMMARY_FIELDS = [
    "session_id",
    "condition",
    "target_id",
    "target_monitor",
    "target_position",
    "repeat_n",
    "mean_sample_coverage",
    "worst_sample_coverage",
    "mean_valid_rate",
    "mean_binocular_valid_rate",
    "median_repeat_valid_rate",
    "worst_repeat_valid_rate",
    "mean_correct_monitor_valid_rate",
    "mean_usable_monitor_rate",
    "median_centroid_error_px",
    "p95_centroid_error_px",
    "median_error_deg",
    "p95_error_deg",
    "p95_epoch_sample_error_px",
    "mean_invalid_time_rate_ge_100ms",
    "max_longest_invalid_episode_ms",
    "correct_monitor_acquired_within_1s_rate",
    "median_first_valid_latency_ms",
    "median_first_sustained_correct_latency_ms",
]

MONITOR_SUMMARY_FIELDS = [
    "session_id",
    "condition",
    "target_monitor",
    "target_n",
    "epoch_n",
    "equal_target_sample_coverage",
    "equal_target_valid_rate",
    "equal_target_binocular_valid_rate",
    "equal_target_correct_monitor_rate",
    "equal_target_usable_monitor_rate",
    "median_target_centroid_error_px",
    "p95_epoch_centroid_error_px",
    "median_target_error_deg",
    "p95_epoch_error_deg",
    "equal_target_p95_sample_error_px",
    "worst_target_median_valid_rate",
    "equal_target_invalid_time_rate_ge_100ms",
    "max_longest_invalid_episode_ms",
    "correct_monitor_acquired_within_1s_rate",
    "median_first_valid_latency_ms",
    "median_first_sustained_correct_latency_ms",
    "recovery_attempt_n",
    "recovery_epoch_n",
    "recovery_success_rate",
    "median_center_recovery_latency_ms",
]

EVENT_FIELDS = [
    "session_id",
    "condition",
    "block",
    "epoch",
    "sequence",
    "event_type",
    "pc_time_ns",
    "pc_monotonic_ns",
    "pc_time_iso",
    "target_id",
    "target_monitor",
    "target_position",
    "target_local_x",
    "target_local_y",
    "target_x_px",
    "target_y_px",
    "previous_target_id",
    "previous_target_monitor",
]


@dataclass(frozen=True)
class Rect:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def width(self) -> int:
        return self.right - self.left

    @property
    def height(self) -> int:
        return self.bottom - self.top

    def contains(self, x: float, y: float) -> bool:
        return self.left <= x < self.right and self.top <= y < self.bottom


@dataclass(frozen=True)
class DisplayInfo:
    device_name: str
    bounds: Rect
    work_area: Rect
    primary: bool
    dpi_x: int
    dpi_y: int

    @property
    def scale_percent(self) -> float:
        return 100.0 * self.dpi_x / 96.0


@dataclass(frozen=True)
class Segment:
    name: str
    bounds: Rect
    source_display: str


@dataclass(frozen=True)
class Target:
    target_id: str
    monitor: str
    position: str
    local_x: float
    local_y: float
    x_px: float
    y_px: float


@dataclass(frozen=True)
class Epoch:
    block: int
    epoch: int
    sequence: int
    target: Target
    previous_target_id: str
    previous_target_monitor: str


def set_process_dpi_awareness() -> str:
    if os.name != "nt":
        return "not-windows"
    user32 = ctypes.windll.user32
    try:
        set_context = user32.SetProcessDpiAwarenessContext
        set_context.argtypes = [ctypes.c_void_p]
        set_context.restype = ctypes.c_int
        if set_context(ctypes.c_void_p(-4)):
            return "per-monitor-aware-v2"
    except (AttributeError, OSError):
        pass
    try:
        set_awareness = ctypes.windll.shcore.SetProcessDpiAwareness
        set_awareness.argtypes = [ctypes.c_int]
        set_awareness.restype = ctypes.c_long
        if set_awareness(2) == 0:
            return "per-monitor-aware"
    except (AttributeError, OSError):
        pass
    try:
        if user32.SetProcessDPIAware():
            return "system-aware"
    except (AttributeError, OSError):
        pass
    raise RuntimeError(
        "Unable to enable Windows DPI awareness; target pixel mapping would "
        "not be research-valid."
    )


def enumerate_windows_displays() -> List[DisplayInfo]:
    if os.name != "nt":
        raise RuntimeError("This validation GUI currently supports Windows only.")

    from ctypes import wintypes

    class RECT(ctypes.Structure):
        _fields_ = [
            ("left", ctypes.c_long),
            ("top", ctypes.c_long),
            ("right", ctypes.c_long),
            ("bottom", ctypes.c_long),
        ]

    class MONITORINFOEXW(ctypes.Structure):
        _fields_ = [
            ("cbSize", wintypes.DWORD),
            ("rcMonitor", RECT),
            ("rcWork", RECT),
            ("dwFlags", wintypes.DWORD),
            ("szDevice", wintypes.WCHAR * 32),
        ]

    user32 = ctypes.windll.user32
    shcore = ctypes.windll.shcore
    hmonitor_type = wintypes.HANDLE
    callback_type = ctypes.WINFUNCTYPE(
        wintypes.BOOL,
        hmonitor_type,
        wintypes.HDC,
        ctypes.POINTER(RECT),
        wintypes.LPARAM,
    )
    user32.GetMonitorInfoW.argtypes = [
        hmonitor_type,
        ctypes.POINTER(MONITORINFOEXW),
    ]
    user32.GetMonitorInfoW.restype = wintypes.BOOL
    user32.EnumDisplayMonitors.argtypes = [
        wintypes.HDC,
        ctypes.POINTER(RECT),
        callback_type,
        wintypes.LPARAM,
    ]
    user32.EnumDisplayMonitors.restype = wintypes.BOOL
    shcore.GetDpiForMonitor.argtypes = [
        hmonitor_type,
        ctypes.c_int,
        ctypes.POINTER(wintypes.UINT),
        ctypes.POINTER(wintypes.UINT),
    ]
    shcore.GetDpiForMonitor.restype = ctypes.c_long

    monitors: List[DisplayInfo] = []

    def callback(hmonitor, _hdc, _rect_ptr, _lparam):
        info = MONITORINFOEXW()
        info.cbSize = ctypes.sizeof(MONITORINFOEXW)
        if not user32.GetMonitorInfoW(hmonitor, ctypes.byref(info)):
            return 1

        dpi_x = ctypes.c_uint(96)
        dpi_y = ctypes.c_uint(96)
        try:
            shcore.GetDpiForMonitor(
                hmonitor,
                0,
                ctypes.byref(dpi_x),
                ctypes.byref(dpi_y),
            )
        except Exception:
            pass

        monitors.append(
            DisplayInfo(
                device_name=info.szDevice,
                bounds=Rect(
                    info.rcMonitor.left,
                    info.rcMonitor.top,
                    info.rcMonitor.right,
                    info.rcMonitor.bottom,
                ),
                work_area=Rect(
                    info.rcWork.left,
                    info.rcWork.top,
                    info.rcWork.right,
                    info.rcWork.bottom,
                ),
                primary=bool(info.dwFlags & 1),
                dpi_x=int(dpi_x.value),
                dpi_y=int(dpi_y.value),
            )
        )
        return 1

    callback_fn = callback_type(callback)
    if not user32.EnumDisplayMonitors(
        None,
        None,
        callback_fn,
        0,
    ):
        raise RuntimeError("EnumDisplayMonitors failed.")

    if not monitors:
        raise RuntimeError("No Windows display was detected.")
    return sorted(monitors, key=lambda monitor: (monitor.bounds.left, monitor.bounds.top))


def virtual_bounds(displays: Sequence[DisplayInfo]) -> Rect:
    return Rect(
        min(display.bounds.left for display in displays),
        min(display.bounds.top for display in displays),
        max(display.bounds.right for display in displays),
        max(display.bounds.bottom for display in displays),
    )


def primary_display(displays: Sequence[DisplayInfo]) -> DisplayInfo:
    for display in displays:
        if display.primary:
            return display
    return displays[0]


def build_segments(
    condition: str,
    displays: Sequence[DisplayInfo],
    allow_layout_mismatch: bool,
) -> Tuple[List[Segment], Rect, List[str]]:
    warnings: List[str] = []
    if condition == "CENTER_BASELINE":
        display = primary_display(displays)
        return (
            [Segment("center", display.bounds, display.device_name)],
            display.bounds,
            warnings,
        )

    if condition != "SURROUND_WIDE":
        raise ValueError(f"Unsupported condition: {condition}")

    if len(displays) != 1 and not allow_layout_mismatch:
        raise RuntimeError(
            "SURROUND_WIDE requires Windows to expose one logical display. "
            f"Detected {len(displays)} displays. Configure NVIDIA Surround first "
            "or use --allow-layout-mismatch only for a diagnostic dry run."
        )

    if len(displays) == 1:
        bounds = displays[0].bounds
        source = displays[0].device_name
    else:
        bounds = virtual_bounds(displays)
        source = "virtual-desktop-diagnostic"
        warnings.append(
            "SURROUND_WIDE was run with multiple Windows displays; this is "
            "diagnostic only and is not a valid Surround result."
        )

    third = bounds.width / 3.0
    edges = [
        bounds.left,
        round(bounds.left + third),
        round(bounds.left + 2.0 * third),
        bounds.right,
    ]
    segments = [
        Segment("left", Rect(edges[0], bounds.top, edges[1], bounds.bottom), source),
        Segment("center", Rect(edges[1], bounds.top, edges[2], bounds.bottom), source),
        Segment("right", Rect(edges[2], bounds.top, edges[3], bounds.bottom), source),
    ]

    if abs(bounds.width / max(bounds.height, 1) - 16.0 / 3.0) > 0.35:
        warnings.append(
            "The logical display aspect ratio is not close to 48:9. "
            "Verify Surround topology and resolution."
        )
    return segments, bounds, warnings


def build_targets(segments: Sequence[Segment]) -> List[Target]:
    targets: List[Target] = []
    for segment in segments:
        for position, local_x, local_y in TARGET_LOCAL_POSITIONS:
            targets.append(
                Target(
                    target_id=f"{segment.name.upper()}_{position}",
                    monitor=segment.name,
                    position=position,
                    local_x=local_x,
                    local_y=local_y,
                    x_px=segment.bounds.left + local_x * segment.bounds.width,
                    y_px=segment.bounds.top + local_y * segment.bounds.height,
                )
            )
    return targets


def valid_candidate_order(
    chosen: Sequence[Target],
    candidate: Target,
) -> bool:
    if chosen and chosen[-1].target_id == candidate.target_id:
        return False
    if (
        len(chosen) >= 2
        and chosen[-1].monitor == candidate.monitor
        and chosen[-2].monitor == candidate.monitor
        and len({target.monitor for target in chosen + [candidate]}) > 1
    ):
        return False
    return True


def randomized_block(
    targets: Sequence[Target],
    rng: random.Random,
    previous: Optional[Target],
) -> List[Target]:
    remaining = list(targets)
    chosen: List[Target] = []
    if previous is not None:
        chosen.append(previous)

    output: List[Target] = []
    while remaining:
        candidates = [
            target
            for target in remaining
            if valid_candidate_order(chosen, target)
        ]
        if not candidates:
            # A bounded restart is simpler and deterministic for only 15 targets.
            return randomized_block(targets, rng, previous)
        target = rng.choice(candidates)
        output.append(target)
        chosen.append(target)
        remaining.remove(target)
    return output


def build_epochs(
    targets: Sequence[Target],
    repeats: int,
    seed: int,
) -> List[Epoch]:
    rng = random.Random(seed)
    epochs: List[Epoch] = []
    previous: Optional[Target] = None
    epoch_number = 0
    sequence = 0
    for block in range(1, repeats + 1):
        order = randomized_block(targets, rng, previous)
        for target in order:
            epoch_number += 1
            sequence += 1
            epochs.append(
                Epoch(
                    block=block,
                    epoch=epoch_number,
                    sequence=sequence,
                    target=target,
                    previous_target_id=previous.target_id if previous else "",
                    previous_target_monitor=previous.monitor if previous else "",
                )
            )
            previous = target
    return epochs


def load_tobii_sdk(sdk_dir: Path):
    sdk_dir = sdk_dir.resolve()
    if not sdk_dir.exists():
        raise RuntimeError(f"Tobii SDK directory does not exist: {sdk_dir}")
    if hasattr(os, "add_dll_directory"):
        DLL_DIRECTORY_HANDLES.append(os.add_dll_directory(str(sdk_dir)))
    sys.path.insert(0, str(sdk_dir))
    try:
        import tobii_research as tr
    except ImportError as exc:
        raise RuntimeError(
            f"Unable to import tobii_research from {sdk_dir}: {exc}"
        ) from exc
    return tr


def optional_tracker_value(getter, fallback: str = "unsupported") -> Any:
    try:
        return getter()
    except Exception as exc:
        return f"{fallback}: {type(exc).__name__}"


def select_tracker(tr, serial: str):
    trackers = tr.find_all_eyetrackers()
    if not trackers:
        raise RuntimeError("No Tobii Pro eye tracker was found.")
    if serial:
        matching = [tracker for tracker in trackers if tracker.serial_number == serial]
        if not matching:
            found = ", ".join(tracker.serial_number for tracker in trackers)
            raise RuntimeError(
                f"Tracker serial {serial} was not found. Connected: {found}"
            )
        return matching[0]
    if len(trackers) != 1:
        found = ", ".join(tracker.serial_number for tracker in trackers)
        raise RuntimeError(
            "Multiple trackers are connected. Use --tracker-serial. "
            f"Connected: {found}"
        )
    return trackers[0]


def display_area_metadata(tracker) -> Dict[str, Any]:
    area = tracker.get_display_area()
    return {
        "top_left_mm": list(area.top_left),
        "top_right_mm": list(area.top_right),
        "bottom_left_mm": list(area.bottom_left),
        "bottom_right_mm": list(area.bottom_right),
        "width_mm": float(area.width),
        "height_mm": float(area.height),
        "diagonal_in": math.hypot(float(area.width), float(area.height)) / 25.4,
    }


def finite_number(value: Any) -> bool:
    try:
        return math.isfinite(float(value))
    except (TypeError, ValueError):
        return False


def float_or_nan(value: Any) -> float:
    return float(value) if finite_number(value) else math.nan


def point_from_gaze(gaze_data: Dict[str, Any], key: str) -> Tuple[float, float]:
    value = gaze_data.get(key)
    if value is None or len(value) < 2:
        return math.nan, math.nan
    return float(value[0]), float(value[1])


def point3_from_gaze(
    gaze_data: Dict[str, Any],
    key: str,
) -> Tuple[float, float, float]:
    value = gaze_data.get(key)
    if value is None or len(value) < 3:
        return math.nan, math.nan, math.nan
    return float(value[0]), float(value[1]), float(value[2])


def eye_valid(validity: Any, x: float, y: float) -> bool:
    return int(validity or 0) == 1 and finite_number(x) and finite_number(y)


def average_valid_gaze(
    left_x: float,
    left_y: float,
    left_validity: Any,
    right_x: float,
    right_y: float,
    right_validity: Any,
) -> Tuple[float, float, bool, bool]:
    left_ok = eye_valid(left_validity, left_x, left_y)
    right_ok = eye_valid(right_validity, right_x, right_y)
    points = []
    if left_ok:
        points.append((left_x, left_y))
    if right_ok:
        points.append((right_x, right_y))
    if not points:
        return math.nan, math.nan, False, False
    return (
        sum(point[0] for point in points) / len(points),
        sum(point[1] for point in points) / len(points),
        True,
        left_ok and right_ok,
    )


def iso_now() -> str:
    return datetime.now().isoformat(timespec="milliseconds")


def json_safe(value: Any) -> Any:
    if isinstance(value, dict):
        return {str(key): json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [json_safe(item) for item in value]
    if isinstance(value, float) and not math.isfinite(value):
        return None
    return value


def percentile(values: Sequence[float], percentile_value: float) -> float:
    finite_values = sorted(float(value) for value in values if finite_number(value))
    if not finite_values:
        return math.nan
    if len(finite_values) == 1:
        return finite_values[0]
    rank = (len(finite_values) - 1) * percentile_value / 100.0
    low = math.floor(rank)
    high = math.ceil(rank)
    if low == high:
        return finite_values[low]
    fraction = rank - low
    return finite_values[low] + fraction * (
        finite_values[high] - finite_values[low]
    )


def mean_or_nan(values: Iterable[float]) -> float:
    finite_values = [float(value) for value in values if finite_number(value)]
    return statistics.fmean(finite_values) if finite_values else math.nan


def strict_mean_or_nan(values: Iterable[float]) -> float:
    items = list(values)
    if not items or any(not finite_number(value) for value in items):
        return math.nan
    return statistics.fmean(float(value) for value in items)


def median_or_nan(values: Iterable[float]) -> float:
    finite_values = [float(value) for value in values if finite_number(value)]
    return statistics.median(finite_values) if finite_values else math.nan


def rect_to_dict(rect: Rect) -> Dict[str, int]:
    return asdict(rect)


def display_to_dict(display: DisplayInfo) -> Dict[str, Any]:
    return {
        "device_name": display.device_name,
        "bounds": rect_to_dict(display.bounds),
        "work_area": rect_to_dict(display.work_area),
        "primary": display.primary,
        "dpi_x": display.dpi_x,
        "dpi_y": display.dpi_y,
        "scale_percent": display.scale_percent,
    }


def segment_to_dict(segment: Segment) -> Dict[str, Any]:
    return {
        "name": segment.name,
        "bounds": rect_to_dict(segment.bounds),
        "source_display": segment.source_display,
    }


def target_to_dict(target: Target) -> Dict[str, Any]:
    return asdict(target)


def unique_session_id(condition: str, output_dir: Path) -> str:
    base = (
        f"tobii_multimonitor_{condition.lower()}_"
        f"{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    )
    candidate = base
    suffix = 1
    while any(output_dir.glob(f"{candidate}_*")):
        suffix += 1
        candidate = f"{base}_{suffix}"
    return candidate


class StreamingCsvSink:
    def __init__(
        self,
        path: Path,
        fieldnames: Sequence[str],
    ):
        self.path = path
        self.fieldnames = list(fieldnames)
        self.lock = threading.Lock()
        self.file = path.open("x", newline="", encoding="utf-8")
        self.writer = csv.DictWriter(
            self.file,
            fieldnames=self.fieldnames,
            extrasaction="ignore",
        )
        self.writer.writeheader()
        self.file.flush()
        self.closed = False

    def write(self, row: Dict[str, Any]) -> None:
        with self.lock:
            if self.closed:
                raise RuntimeError(f"CSV sink is already closed: {self.path}")
            self.writer.writerow(row)
            self.file.flush()

    def close(self) -> None:
        with self.lock:
            if self.closed:
                return
            self.file.flush()
            self.file.close()
            self.closed = True


class ValidationRecorder:
    def __init__(
        self,
        tr,
        tracker,
        session_id: str,
        condition: str,
        run_label: str,
        raw_path: Path,
        nominal_frequency_hz: float,
    ):
        self.tr = tr
        self.tracker = tracker
        self.session_id = session_id
        self.condition = condition
        self.run_label = run_label
        self.raw_path = raw_path
        self.nominal_frequency_hz = nominal_frequency_hz
        self.lock = threading.Lock()
        self.context: Dict[str, Any] = {
            "block": "",
            "epoch": "",
            "sequence": "",
            "phase": "idle",
            "phase_start_ns": time.time_ns(),
            "phase_start_monotonic_ns": time.monotonic_ns(),
            "target_on_ns": "",
            "target_on_monotonic_ns": "",
            "target_id": "",
            "target_monitor": "",
            "target_position": "",
            "target_local_x": "",
            "target_local_y": "",
            "target_x_px": "",
            "target_y_px": "",
            "previous_target_id": "",
            "previous_target_monitor": "",
        }
        self.rows: List[Dict[str, Any]] = []
        self.subscribed = False
        self.writer_error: Optional[BaseException] = None
        self.raw_queue: Queue = Queue()
        self.raw_file = raw_path.open("x", newline="", encoding="utf-8")
        self.raw_writer = csv.DictWriter(
            self.raw_file,
            fieldnames=RAW_FIELDS,
            extrasaction="ignore",
        )
        self.raw_writer.writeheader()
        self.raw_file.flush()
        self.writer_thread = threading.Thread(
            target=self._writer_loop,
            name="tobii-validation-raw-writer",
            daemon=True,
        )
        self.writer_thread.start()

    def _writer_loop(self) -> None:
        last_flush = time.monotonic()
        try:
            while True:
                row = self.raw_queue.get()
                if row is None:
                    break
                self.raw_writer.writerow(row)
                if time.monotonic() - last_flush >= 1.0:
                    self.raw_file.flush()
                    last_flush = time.monotonic()
        except BaseException as exc:
            self.writer_error = exc
        finally:
            try:
                self.raw_file.flush()
            finally:
                self.raw_file.close()

    def set_context(
        self,
        phase: str,
        epoch: Optional[Epoch],
        target_on_ns: Any = "",
        target_on_monotonic_ns: Any = "",
    ) -> Tuple[int, int]:
        timestamp_ns = time.time_ns()
        monotonic_ns = time.monotonic_ns()
        if phase == "settle" and not finite_number(target_on_ns):
            target_on_ns = timestamp_ns
            target_on_monotonic_ns = monotonic_ns
        context: Dict[str, Any] = {
            "block": epoch.block if epoch else "",
            "epoch": epoch.epoch if epoch else "",
            "sequence": epoch.sequence if epoch else "",
            "phase": phase,
            "phase_start_ns": timestamp_ns,
            "phase_start_monotonic_ns": monotonic_ns,
            "target_on_ns": target_on_ns,
            "target_on_monotonic_ns": target_on_monotonic_ns,
            "target_id": epoch.target.target_id if epoch else "",
            "target_monitor": epoch.target.monitor if epoch else "",
            "target_position": epoch.target.position if epoch else "",
            "target_local_x": epoch.target.local_x if epoch else "",
            "target_local_y": epoch.target.local_y if epoch else "",
            "target_x_px": epoch.target.x_px if epoch else "",
            "target_y_px": epoch.target.y_px if epoch else "",
            "previous_target_id": epoch.previous_target_id if epoch else "",
            "previous_target_monitor": (
                epoch.previous_target_monitor if epoch else ""
            ),
        }
        with self.lock:
            self.context = context
        return timestamp_ns, monotonic_ns

    def callback(self, gaze_data: Dict[str, Any]) -> None:
        timestamp_ns = time.time_ns()
        with self.lock:
            context = dict(self.context)

        left_x, left_y = point_from_gaze(
            gaze_data,
            "left_gaze_point_on_display_area",
        )
        right_x, right_y = point_from_gaze(
            gaze_data,
            "right_gaze_point_on_display_area",
        )
        left_origin = point3_from_gaze(
            gaze_data,
            "left_gaze_origin_in_user_coordinate_system",
        )
        right_origin = point3_from_gaze(
            gaze_data,
            "right_gaze_origin_in_user_coordinate_system",
        )

        row = {
            "session_id": self.session_id,
            "condition": self.condition,
            "run_label": self.run_label,
            "pc_time_ns": timestamp_ns,
            "pc_monotonic_ns": time.monotonic_ns(),
            "pc_time_iso": iso_now(),
            "device_time_stamp": gaze_data.get("device_time_stamp", ""),
            "system_time_stamp": gaze_data.get("system_time_stamp", ""),
            **context,
            "left_gaze_x": left_x,
            "left_gaze_y": left_y,
            "left_gaze_validity": gaze_data.get(
                "left_gaze_point_validity",
                0,
            ),
            "right_gaze_x": right_x,
            "right_gaze_y": right_y,
            "right_gaze_validity": gaze_data.get(
                "right_gaze_point_validity",
                0,
            ),
            "left_gaze_origin_user_x": left_origin[0],
            "left_gaze_origin_user_y": left_origin[1],
            "left_gaze_origin_user_z": left_origin[2],
            "left_gaze_origin_validity": gaze_data.get(
                "left_gaze_origin_validity",
                0,
            ),
            "right_gaze_origin_user_x": right_origin[0],
            "right_gaze_origin_user_y": right_origin[1],
            "right_gaze_origin_user_z": right_origin[2],
            "right_gaze_origin_validity": gaze_data.get(
                "right_gaze_origin_validity",
                0,
            ),
            "left_pupil_diameter": gaze_data.get("left_pupil_diameter", ""),
            "left_pupil_validity": gaze_data.get("left_pupil_validity", ""),
            "right_pupil_diameter": gaze_data.get("right_pupil_diameter", ""),
            "right_pupil_validity": gaze_data.get("right_pupil_validity", ""),
        }
        with self.lock:
            self.rows.append(row)
        self.raw_queue.put(row)

    def subscribe(self) -> None:
        if self.subscribed:
            return
        self.tracker.subscribe_to(
            self.tr.EYETRACKER_GAZE_DATA,
            self.callback,
            as_dictionary=True,
        )
        self.subscribed = True

    def unsubscribe(self) -> None:
        if not self.subscribed:
            return
        self.tracker.unsubscribe_from(
            self.tr.EYETRACKER_GAZE_DATA,
            self.callback,
        )
        self.subscribed = False

    def snapshot_rows(self) -> List[Dict[str, Any]]:
        with self.lock:
            return [dict(row) for row in self.rows]

    def recent_stream_status(
        self,
        window_sec: float = 1.25,
    ) -> Tuple[int, int]:
        cutoff = time.monotonic_ns() - round(window_sec * 1_000_000_000)
        with self.lock:
            rows = [
                row
                for row in self.rows
                if int(row["pc_monotonic_ns"]) >= cutoff
            ]
        valid_n = 0
        for row in rows:
            _, _, any_valid, _ = average_valid_gaze(
                float(row["left_gaze_x"]),
                float(row["left_gaze_y"]),
                row["left_gaze_validity"],
                float(row["right_gaze_x"]),
                float(row["right_gaze_y"]),
                row["right_gaze_validity"],
            )
            valid_n += int(any_valid)
        return len(rows), valid_n

    def close_writer(self) -> None:
        if self.writer_thread.is_alive():
            self.raw_queue.put(None)
            self.writer_thread.join(timeout=5.0)
        if self.writer_thread.is_alive():
            raise RuntimeError("Raw gaze writer did not stop within 5 seconds.")
        if self.writer_error is not None:
            raise RuntimeError(
                f"Raw gaze writer failed: {self.writer_error}"
            ) from self.writer_error


class ValidationGui:
    def __init__(
        self,
        recorder: ValidationRecorder,
        session_id: str,
        condition: str,
        displays: Sequence[DisplayInfo],
        active_rect: Rect,
        segments: Sequence[Segment],
        epochs: Sequence[Epoch],
        event_sink: StreamingCsvSink,
        home_sec: float,
        blank_sec: float,
        target_sec: float,
        settle_sec: float,
    ):
        self.recorder = recorder
        self.session_id = session_id
        self.condition = condition
        self.displays = list(displays)
        self.active_rect = active_rect
        self.segments = list(segments)
        self.epochs = list(epochs)
        self.event_sink = event_sink
        self.home_sec = home_sec
        self.blank_sec = blank_sec
        self.target_sec = target_sec
        self.settle_sec = settle_sec
        self.bounds = active_rect
        self.events: List[Dict[str, Any]] = []
        self.current_index = -1
        self.current_block = 0
        self.waiting_for_space = True
        self.aborted = False
        self.completed = False
        self.target_on_ns: Any = ""
        self.target_on_monotonic_ns: Any = ""
        self.after_jobs: List[str] = []
        self.last_space_monotonic_ns = 0
        self.block_break_ready_monotonic_ns = 0
        self.pending_break_epoch: Optional[Epoch] = None
        self.callback_error = ""

        self.root = Tk()
        self.root.title("Tobii Multi-monitor Validation")
        self.root.overrideredirect(True)
        self.root.attributes("-topmost", True)
        self.root.geometry(
            f"{self.bounds.width}x{self.bounds.height}"
            f"{self.bounds.left:+d}{self.bounds.top:+d}"
        )
        self.root.configure(background="#20242a", cursor="none")
        self.canvas = Canvas(
            self.root,
            background="#20242a",
            highlightthickness=0,
            cursor="none",
        )
        self.canvas.pack(fill=BOTH, expand=True)
        self.root.bind("<Escape>", self.abort)
        self.root.bind("<KeyRelease-space>", self.on_space)
        self.root.protocol("WM_DELETE_WINDOW", self.abort)
        self.root.report_callback_exception = self._handle_callback_exception
        self.root.update_idletasks()
        actual = Rect(
            self.root.winfo_rootx(),
            self.root.winfo_rooty(),
            self.root.winfo_rootx() + self.root.winfo_width(),
            self.root.winfo_rooty() + self.root.winfo_height(),
        )
        if any(
            abs(expected - observed) > 2
            for expected, observed in zip(
                asdict(self.bounds).values(),
                asdict(actual).values(),
            )
        ):
            self.root.destroy()
            raise RuntimeError(
                "Tk validation window bounds do not match the active display: "
                f"expected={rect_to_dict(self.bounds)}, "
                f"actual={rect_to_dict(actual)}"
            )
        self.actual_window_bounds = actual

        self.home_x, self.home_y = self._home_point()
        self._show_instruction(
            "Tobii multi-monitor validation\n\n"
            f"Condition: {condition}\n"
            f"Targets: {len(epochs)} ({len(set(epoch.block for epoch in epochs))} blocks)\n\n"
            "Look at the small center dot of every target.\n"
            "Natural eye/head rotation is allowed; keep the seat and torso fixed.\n"
            "Press SPACE to start each block. Press ESC to abort safely."
        )

    def _canvas_xy(self, global_x: float, global_y: float) -> Tuple[float, float]:
        return global_x - self.bounds.left, global_y - self.bounds.top

    def _home_point(self) -> Tuple[float, float]:
        center = next(
            (segment for segment in self.segments if segment.name == "center"),
            self.segments[0],
        )
        return (
            center.bounds.left + center.bounds.width / 2.0,
            center.bounds.top + center.bounds.height / 2.0,
        )

    def _clear(self) -> None:
        self.canvas.delete("all")

    def _show_instruction(self, text: str) -> None:
        self._clear()
        x, y = self._canvas_xy(self.home_x, self.home_y)
        self.canvas.create_text(
            x,
            y,
            text=text,
            fill="#f0f3f5",
            font=("Segoe UI", 22),
            justify="center",
            width=1100,
        )
        self.canvas.update_idletasks()

    def _draw_marker(
        self,
        global_x: float,
        global_y: float,
        outer_color: str,
        center_color: str,
    ) -> None:
        self._clear()
        x, y = self._canvas_xy(global_x, global_y)
        radius = 32
        self.canvas.create_oval(
            x - radius,
            y - radius,
            x + radius,
            y + radius,
            outline=outer_color,
            width=4,
        )
        self.canvas.create_line(
            x - 18,
            y,
            x + 18,
            y,
            fill=outer_color,
            width=2,
        )
        self.canvas.create_line(
            x,
            y - 18,
            x,
            y + 18,
            fill=outer_color,
            width=2,
        )
        center_radius = 6
        self.canvas.create_oval(
            x - center_radius,
            y - center_radius,
            x + center_radius,
            y + center_radius,
            fill=center_color,
            outline=center_color,
        )
        self.canvas.update_idletasks()

    def _log_event(self, event_type: str, epoch: Optional[Epoch]) -> int:
        timestamp_ns = time.time_ns()
        monotonic_ns = time.monotonic_ns()
        row = {
            "session_id": self.session_id,
            "condition": self.condition,
            "block": epoch.block if epoch else "",
            "epoch": epoch.epoch if epoch else "",
            "sequence": epoch.sequence if epoch else "",
            "event_type": event_type,
            "pc_time_ns": timestamp_ns,
            "pc_monotonic_ns": monotonic_ns,
            "pc_time_iso": iso_now(),
            "target_id": epoch.target.target_id if epoch else "",
            "target_monitor": epoch.target.monitor if epoch else "",
            "target_position": epoch.target.position if epoch else "",
            "target_local_x": epoch.target.local_x if epoch else "",
            "target_local_y": epoch.target.local_y if epoch else "",
            "target_x_px": epoch.target.x_px if epoch else "",
            "target_y_px": epoch.target.y_px if epoch else "",
            "previous_target_id": epoch.previous_target_id if epoch else "",
            "previous_target_monitor": (
                epoch.previous_target_monitor if epoch else ""
            ),
        }
        self.events.append(row)
        self.event_sink.write(row)
        return timestamp_ns

    def schedule(self, delay_sec: float, callback) -> None:
        job = self.root.after(max(1, round(delay_sec * 1000.0)), callback)
        self.after_jobs.append(job)

    def run(self) -> Tuple[bool, bool, List[Dict[str, Any]]]:
        self.root.focus_force()
        self.root.mainloop()
        return self.completed, self.aborted, list(self.events)

    def _handle_callback_exception(
        self,
        exception_type,
        exception_value,
        _traceback,
    ) -> None:
        self.callback_error = (
            f"{exception_type.__name__}: {exception_value}"
        )
        try:
            self.abort()
        except Exception:
            self.aborted = True
            try:
                self.root.destroy()
            except Exception:
                pass

    def on_space(self, _event=None) -> None:
        if not self.waiting_for_space:
            return
        now_ns = time.monotonic_ns()
        if now_ns - self.last_space_monotonic_ns < 500_000_000:
            return
        self.last_space_monotonic_ns = now_ns
        if now_ns < self.block_break_ready_monotonic_ns:
            return
        if self.current_index < 0:
            sample_n, valid_n = self.recorder.recent_stream_status()
            minimum_samples = max(
                1,
                round(self.recorder.nominal_frequency_hz * 0.75),
            )
            if sample_n < minimum_samples or valid_n == 0:
                self._show_instruction(
                    "Gaze-stream preflight is not ready.\n\n"
                    f"Recent samples: {sample_n} "
                    f"(need at least {minimum_samples})\n"
                    f"Valid samples: {valid_n} (need at least 1)\n\n"
                    "Look at the center of this message, correct your "
                    "position if needed, wait one second, and press SPACE."
                )
                return
        if self.pending_break_epoch is not None:
            self._log_event("BLOCK_BREAK_END", self.pending_break_epoch)
            self.pending_break_epoch = None
        self.waiting_for_space = False
        self._start_next_epoch()

    def _start_next_epoch(self) -> None:
        next_index = self.current_index + 1
        if next_index >= len(self.epochs):
            self.finish()
            return

        next_epoch = self.epochs[next_index]
        if self.current_block and next_epoch.block != self.current_block:
            completed_block = self.current_block
            # Mark the pending block before waiting. Otherwise pressing SPACE
            # would encounter the same boundary and remain in an endless break.
            self.current_block = next_epoch.block
            self.waiting_for_space = True
            self.pending_break_epoch = next_epoch
            self.block_break_ready_monotonic_ns = (
                time.monotonic_ns() + 1_000_000_000
            )
            self._show_instruction(
                f"Block {completed_block} complete.\n\n"
                "Rest briefly without changing the seat position.\n"
                f"Press SPACE for block {next_epoch.block}."
            )
            self.recorder.set_context("block_break", None)
            self._log_event("BLOCK_BREAK_START", next_epoch)
            return

        self.current_index = next_index
        self.current_block = next_epoch.block
        self._show_home(next_epoch)

    def _show_home(self, epoch: Epoch) -> None:
        self._draw_marker(
            self.home_x,
            self.home_y,
            outer_color="#c7d0d9",
            center_color="#ffffff",
        )
        self.recorder.set_context("home", epoch)
        self._log_event("HOME_ON", epoch)
        self.schedule(self.home_sec, lambda: self._preblank(epoch))

    def _preblank(self, epoch: Epoch) -> None:
        self._clear()
        self.canvas.update_idletasks()
        self.recorder.set_context("pre_blank", epoch)
        self._log_event("HOME_OFF", epoch)
        self.schedule(self.blank_sec, lambda: self._show_target(epoch))

    def _show_target(self, epoch: Epoch) -> None:
        self._draw_marker(
            epoch.target.x_px,
            epoch.target.y_px,
            outer_color="#58d7ff",
            center_color="#ffffff",
        )
        (
            self.target_on_ns,
            self.target_on_monotonic_ns,
        ) = self.recorder.set_context("settle", epoch)
        self._log_event("TARGET_ON", epoch)
        self.schedule(self.settle_sec, lambda: self._begin_measure(epoch))
        self.schedule(self.target_sec, lambda: self._end_target(epoch))

    def _begin_measure(self, epoch: Epoch) -> None:
        self.recorder.set_context(
            "measure",
            epoch,
            self.target_on_ns,
            self.target_on_monotonic_ns,
        )
        self._log_event("MEASURE_START", epoch)

    def _end_target(self, epoch: Epoch) -> None:
        self._clear()
        self.canvas.update_idletasks()
        self.recorder.set_context(
            "post_blank",
            epoch,
            self.target_on_ns,
            self.target_on_monotonic_ns,
        )
        self._log_event("MEASURE_END", epoch)
        self._log_event("TARGET_OFF", epoch)
        self.schedule(self.blank_sec, self._start_next_epoch)

    def abort(self, _event=None) -> None:
        if self.completed or self.aborted:
            return
        self.aborted = True
        self.recorder.set_context("aborted", None)
        self._log_event("SESSION_ABORTED", None)
        for job in self.after_jobs:
            try:
                self.root.after_cancel(job)
            except Exception:
                pass
        self.root.destroy()

    def finish(self) -> None:
        if self.completed:
            return
        self.completed = True
        self.recorder.set_context("complete", None)
        self._log_event("SESSION_COMPLETE", None)
        self._show_instruction(
            "Validation complete.\n\n"
            "You may relax. Results are being written."
        )
        self.root.update_idletasks()
        self.root.after(900, self.root.destroy)


def annotate_rows(
    raw_rows: Sequence[Dict[str, Any]],
    active_rect: Rect,
    segments: Sequence[Segment],
    monitor_width_mm: Optional[float],
    monitor_height_mm: Optional[float],
    view_distance_mm: Optional[float],
) -> List[Dict[str, Any]]:
    annotated: List[Dict[str, Any]] = []
    segment_by_name = {segment.name: segment for segment in segments}
    for raw in raw_rows:
        row = dict(raw)
        left_x = float(raw["left_gaze_x"])
        left_y = float(raw["left_gaze_y"])
        right_x = float(raw["right_gaze_x"])
        right_y = float(raw["right_gaze_y"])
        avg_x, avg_y, any_valid, binocular_valid = average_valid_gaze(
            left_x,
            left_y,
            raw["left_gaze_validity"],
            right_x,
            right_y,
            raw["right_gaze_validity"],
        )

        mapped_x = (
            active_rect.left + avg_x * active_rect.width
            if any_valid
            else math.nan
        )
        mapped_y = (
            active_rect.top + avg_y * active_rect.height
            if any_valid
            else math.nan
        )
        predicted_monitor = ""
        if any_valid:
            for segment in segments:
                if segment.bounds.contains(mapped_x, mapped_y):
                    predicted_monitor = segment.name
                    break
            if not predicted_monitor:
                predicted_monitor = "OUTSIDE"

        target_monitor = str(raw.get("target_monitor", ""))
        correct_monitor = (
            any_valid
            and bool(target_monitor)
            and predicted_monitor == target_monitor
        )
        in_active_display = (
            any_valid
            and 0.0 <= avg_x < 1.0
            and 0.0 <= avg_y < 1.0
        )

        error_px = math.nan
        error_deg = math.nan
        if (
            any_valid
            and finite_number(raw.get("target_x_px"))
            and finite_number(raw.get("target_y_px"))
        ):
            dx_px = mapped_x - float(raw["target_x_px"])
            dy_px = mapped_y - float(raw["target_y_px"])
            error_px = math.hypot(dx_px, dy_px)
            segment = segment_by_name.get(target_monitor)
            if (
                segment
                and monitor_width_mm
                and monitor_height_mm
                and view_distance_mm
            ):
                dx_mm = dx_px * monitor_width_mm / segment.bounds.width
                dy_mm = dy_px * monitor_height_mm / segment.bounds.height
                error_mm = math.hypot(dx_mm, dy_mm)
                error_deg = math.degrees(
                    math.atan2(error_mm, view_distance_mm)
                )

        phase_start_ns = raw.get("phase_start_monotonic_ns")
        target_on_ns = raw.get("target_on_monotonic_ns")
        row.update(
            {
                "any_eye_valid": int(any_valid),
                "binocular_valid": int(binocular_valid),
                "avg_gaze_x": avg_x,
                "avg_gaze_y": avg_y,
                "mapped_gaze_x_px": mapped_x,
                "mapped_gaze_y_px": mapped_y,
                "in_active_display": int(in_active_display),
                "predicted_monitor": predicted_monitor,
                "correct_monitor": int(correct_monitor),
                "error_px": error_px,
                "error_deg": error_deg,
                "phase_elapsed_ms": (
                    (
                        int(raw["pc_monotonic_ns"])
                        - int(phase_start_ns)
                    )
                    / 1_000_000.0
                    if finite_number(phase_start_ns)
                    else math.nan
                ),
                "target_elapsed_ms": (
                    (
                        int(raw["pc_monotonic_ns"])
                        - int(target_on_ns)
                    )
                    / 1_000_000.0
                    if finite_number(target_on_ns)
                    else math.nan
                ),
            }
        )
        annotated.append(row)
    return annotated


def sustained_latency_ms(
    rows: Sequence[Dict[str, Any]],
    origin_ns: int,
    predicate,
    minimum_ms: float = 100.0,
) -> float:
    run_start: Optional[int] = None
    last_time: Optional[int] = None
    nominal_gap_ns = 50_000_000
    for row in sorted(rows, key=lambda item: int(item["pc_monotonic_ns"])):
        timestamp = int(row["pc_monotonic_ns"])
        if predicate(row):
            if (
                run_start is None
                or (last_time is not None and timestamp - last_time > nominal_gap_ns)
            ):
                run_start = timestamp
            last_time = timestamp
            if timestamp - run_start >= minimum_ms * 1_000_000.0:
                return (run_start - origin_ns) / 1_000_000.0
        else:
            run_start = None
            last_time = None
    return math.nan


def invalid_episodes(
    rows: Sequence[Dict[str, Any]],
    nominal_frequency_hz: float,
    minimum_ms: float = 100.0,
) -> Tuple[int, float, float]:
    if not rows:
        return 0, 0.0, 0.0
    sample_period_ms = 1000.0 / max(nominal_frequency_hz, 1.0)
    episodes: List[float] = []
    start_ns: Optional[int] = None
    last_ns: Optional[int] = None
    for row in sorted(rows, key=lambda item: int(item["pc_monotonic_ns"])):
        timestamp = int(row["pc_monotonic_ns"])
        if int(row["any_eye_valid"]) == 0:
            if start_ns is None:
                start_ns = timestamp
            last_ns = timestamp
        elif start_ns is not None and last_ns is not None:
            duration = (last_ns - start_ns) / 1_000_000.0 + sample_period_ms
            if duration >= minimum_ms:
                episodes.append(duration)
            start_ns = None
            last_ns = None
    if start_ns is not None and last_ns is not None:
        duration = (last_ns - start_ns) / 1_000_000.0 + sample_period_ms
        if duration >= minimum_ms:
            episodes.append(duration)
    return (
        len(episodes),
        sum(episodes),
        max(episodes) if episodes else 0.0,
    )


def compute_epoch_metrics(
    annotated_rows: Sequence[Dict[str, Any]],
    events: Sequence[Dict[str, Any]],
    epochs: Sequence[Epoch],
    nominal_frequency_hz: float,
    measure_sec: float,
) -> List[Dict[str, Any]]:
    event_lookup: Dict[Tuple[int, str], List[int]] = {}
    for event in events:
        if finite_number(event.get("epoch")):
            event_lookup.setdefault(
                (int(event["epoch"]), event["event_type"]),
                [],
            ).append(
                int(event["pc_monotonic_ns"])
            )

    rows_by_epoch: Dict[int, List[Dict[str, Any]]] = {}
    for row in annotated_rows:
        if finite_number(row.get("epoch")):
            rows_by_epoch.setdefault(int(row["epoch"]), []).append(row)

    metrics: List[Dict[str, Any]] = []
    first_epoch_by_block: Dict[int, int] = {}
    for planned_epoch in epochs:
        first_epoch_by_block.setdefault(
            planned_epoch.block,
            planned_epoch.epoch,
        )
    required_events = (
        "HOME_ON",
        "HOME_OFF",
        "TARGET_ON",
        "MEASURE_START",
        "MEASURE_END",
        "TARGET_OFF",
    )
    for epoch in epochs:
        all_epoch_rows = rows_by_epoch.get(epoch.epoch, [])
        measure_rows = [
            row for row in all_epoch_rows if row.get("phase") == "measure"
        ]
        valid_rows = [
            row for row in measure_rows if int(row["any_eye_valid"]) == 1
        ]
        binocular_rows = [
            row for row in measure_rows if int(row["binocular_valid"]) == 1
        ]
        event_times = {
            event_type: event_lookup.get((epoch.epoch, event_type), [])
            for event_type in required_events
        }
        exact_event_counts = all(
            len(event_times[event_type]) == 1
            for event_type in required_events
        )
        ordered_event_times = (
            [event_times[event_type][0] for event_type in required_events]
            if exact_event_counts
            else []
        )
        event_order_ok = (
            exact_event_counts
            and all(
                left <= right
                for left, right in zip(
                    ordered_event_times,
                    ordered_event_times[1:],
                )
            )
        )
        measure_duration_ms = (
            (
                event_times["MEASURE_END"][0]
                - event_times["MEASURE_START"][0]
            )
            / 1_000_000.0
            if exact_event_counts
            else math.nan
        )
        duration_ok = (
            finite_number(measure_duration_ms)
            and measure_sec * 900.0 <= measure_duration_ms
            <= measure_sec * 1200.0
        )
        event_integrity = event_order_ok and duration_ok
        expected_n = (
            max(
                1,
                round(
                    nominal_frequency_hz
                    * float(measure_duration_ms)
                    / 1000.0
                ),
            )
            if finite_number(measure_duration_ms)
            and measure_duration_ms > 0.0
            else max(1, round(nominal_frequency_hz * measure_sec))
        )

        received_n = len(measure_rows)
        valid_n = len(valid_rows)
        sample_coverage = received_n / expected_n
        sample_coverage_ok = (
            event_integrity and 0.80 <= sample_coverage <= 1.20
        )
        mapped_x = [
            float(row["mapped_gaze_x_px"])
            for row in valid_rows
            if finite_number(row["mapped_gaze_x_px"])
        ]
        mapped_y = [
            float(row["mapped_gaze_y_px"])
            for row in valid_rows
            if finite_number(row["mapped_gaze_y_px"])
        ]
        centroid_x = median_or_nan(mapped_x)
        centroid_y = median_or_nan(mapped_y)
        centroid_error = (
            math.hypot(
                centroid_x - epoch.target.x_px,
                centroid_y - epoch.target.y_px,
            )
            if finite_number(centroid_x) and finite_number(centroid_y)
            else math.nan
        )

        errors_px = [
            float(row["error_px"])
            for row in valid_rows
            if finite_number(row["error_px"])
        ]
        errors_deg = [
            float(row["error_deg"])
            for row in valid_rows
            if finite_number(row["error_deg"])
        ]
        distances_from_centroid = (
            [
                math.hypot(x - centroid_x, y - centroid_y)
                for x, y in zip(mapped_x, mapped_y)
            ]
            if finite_number(centroid_x) and finite_number(centroid_y)
            else []
        )
        step_distances = []
        previous_valid: Optional[Dict[str, Any]] = None
        for row in sorted(
            valid_rows,
            key=lambda item: int(item["pc_monotonic_ns"]),
        ):
            if previous_valid is not None:
                gap_ms = (
                    int(row["pc_monotonic_ns"])
                    - int(previous_valid["pc_monotonic_ns"])
                ) / 1_000_000.0
                if gap_ms <= 50.0:
                    step_distances.append(
                        math.hypot(
                            float(row["mapped_gaze_x_px"])
                            - float(previous_valid["mapped_gaze_x_px"]),
                            float(row["mapped_gaze_y_px"])
                            - float(previous_valid["mapped_gaze_y_px"]),
                        )
                    )
            previous_valid = row

        target_on_values = event_lookup.get((epoch.epoch, "TARGET_ON"), [])
        target_on_ns = (
            target_on_values[0] if len(target_on_values) == 1 else 0
        )
        first_valid_latency = math.nan
        valid_after_onset = [
            row
            for row in all_epoch_rows
            if int(row["any_eye_valid"]) == 1
            and target_on_ns
            and int(row["pc_monotonic_ns"]) >= target_on_ns
            and row.get("phase") in ("settle", "measure")
        ]
        if valid_after_onset:
            first_valid_latency = (
                min(
                    int(row["pc_monotonic_ns"])
                    for row in valid_after_onset
                )
                - target_on_ns
            ) / 1_000_000.0

        first_sustained = (
            sustained_latency_ms(
                [
                    row
                    for row in all_epoch_rows
                    if row.get("phase") in ("settle", "measure")
                ],
                target_on_ns,
                lambda row: int(row["correct_monitor"]) == 1,
            )
            if target_on_ns
            else math.nan
        )

        home_on_values = event_lookup.get((epoch.epoch, "HOME_ON"), [])
        home_on_ns = home_on_values[0] if len(home_on_values) == 1 else 0
        center_recovery = (
            sustained_latency_ms(
                [row for row in all_epoch_rows if row.get("phase") == "home"],
                home_on_ns,
                lambda row: (
                    int(row["any_eye_valid"]) == 1
                    and row.get("predicted_monitor") == "center"
                ),
            )
            if home_on_ns
            else math.nan
        )
        invalid_count, invalid_total, invalid_longest = invalid_episodes(
            measure_rows,
            nominal_frequency_hz,
        )
        invalid_time_rate = (
            invalid_total / measure_duration_ms
            if finite_number(measure_duration_ms)
            and measure_duration_ms > 0.0
            else math.nan
        )
        is_block_first = (
            epoch.epoch == first_epoch_by_block[epoch.block]
        )

        metrics.append(
            {
                "session_id": annotated_rows[0]["session_id"]
                if annotated_rows
                else "",
                "condition": annotated_rows[0]["condition"]
                if annotated_rows
                else "",
                "block": epoch.block,
                "epoch": epoch.epoch,
                "sequence": epoch.sequence,
                "target_id": epoch.target.target_id,
                "target_monitor": epoch.target.monitor,
                "target_position": epoch.target.position,
                "target_x_px": epoch.target.x_px,
                "target_y_px": epoch.target.y_px,
                "previous_target_id": epoch.previous_target_id,
                "previous_target_monitor": epoch.previous_target_monitor,
                "is_block_first": int(is_block_first),
                "received_n": received_n,
                "expected_n": expected_n,
                "measure_duration_ms": measure_duration_ms,
                "event_integrity": int(event_integrity),
                "sample_coverage": sample_coverage,
                "sample_coverage_ok": int(sample_coverage_ok),
                "valid_n": valid_n,
                "any_eye_valid_rate": valid_n / received_n
                if received_n
                else math.nan,
                "binocular_valid_rate": len(binocular_rows) / received_n
                if received_n
                else math.nan,
                "in_display_valid_rate": (
                    sum(int(row["in_active_display"]) for row in valid_rows)
                    / valid_n
                    if valid_n
                    else math.nan
                ),
                "correct_monitor_valid_rate": (
                    sum(int(row["correct_monitor"]) for row in valid_rows)
                    / valid_n
                    if valid_n
                    else math.nan
                ),
                "usable_monitor_rate": (
                    sum(int(row["correct_monitor"]) for row in measure_rows)
                    / received_n
                    if received_n
                    else math.nan
                ),
                "centroid_x_px": centroid_x,
                "centroid_y_px": centroid_y,
                "centroid_error_px": centroid_error,
                "median_error_px": median_or_nan(errors_px),
                "p95_error_px": percentile(errors_px, 95.0),
                "median_error_deg": median_or_nan(errors_deg),
                "p95_error_deg": percentile(errors_deg, 95.0),
                "precision_rms_px": (
                    math.sqrt(
                        statistics.fmean(
                            distance * distance for distance in step_distances
                        )
                    )
                    if step_distances
                    else math.nan
                ),
                "dispersion_p95_px": percentile(
                    distances_from_centroid,
                    95.0,
                ),
                "first_valid_latency_ms": first_valid_latency,
                "first_sustained_correct_latency_ms": first_sustained,
                "center_recovery_latency_ms": center_recovery,
                "invalid_episode_count_ge_100ms": invalid_count,
                "invalid_episode_total_ms": invalid_total,
                "longest_invalid_episode_ms": invalid_longest,
                "invalid_time_rate_ge_100ms": invalid_time_rate,
                "correct_monitor_acquired_within_1s": int(
                    finite_number(first_sustained)
                    and first_sustained <= 1000.0
                ),
            }
        )
    return metrics


def compute_target_summaries(
    epoch_metrics: Sequence[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    grouped: Dict[str, List[Dict[str, Any]]] = {}
    for row in epoch_metrics:
        grouped.setdefault(str(row["target_id"]), []).append(row)

    output: List[Dict[str, Any]] = []
    for target_id in sorted(grouped):
        rows = grouped[target_id]
        output.append(
            {
                "session_id": rows[0]["session_id"],
                "condition": rows[0]["condition"],
                "target_id": target_id,
                "target_monitor": rows[0]["target_monitor"],
                "target_position": rows[0]["target_position"],
                "repeat_n": len(rows),
                "mean_sample_coverage": strict_mean_or_nan(
                    row["sample_coverage"] for row in rows
                ),
                "worst_sample_coverage": min(
                    (
                        float(row["sample_coverage"])
                        for row in rows
                        if finite_number(row["sample_coverage"])
                    ),
                    default=math.nan,
                ),
                "mean_valid_rate": strict_mean_or_nan(
                    row["any_eye_valid_rate"] for row in rows
                ),
                "mean_binocular_valid_rate": strict_mean_or_nan(
                    row["binocular_valid_rate"] for row in rows
                ),
                "median_repeat_valid_rate": median_or_nan(
                    row["any_eye_valid_rate"] for row in rows
                ),
                "worst_repeat_valid_rate": min(
                    (
                        float(row["any_eye_valid_rate"])
                        for row in rows
                        if finite_number(row["any_eye_valid_rate"])
                    ),
                    default=math.nan,
                ),
                "mean_correct_monitor_valid_rate": strict_mean_or_nan(
                    row["correct_monitor_valid_rate"] for row in rows
                ),
                "mean_usable_monitor_rate": strict_mean_or_nan(
                    row["usable_monitor_rate"] for row in rows
                ),
                "median_centroid_error_px": median_or_nan(
                    row["centroid_error_px"] for row in rows
                ),
                "p95_centroid_error_px": percentile(
                    [
                        row["centroid_error_px"]
                        for row in rows
                        if finite_number(row["centroid_error_px"])
                    ],
                    95.0,
                ),
                "median_error_deg": median_or_nan(
                    row["median_error_deg"] for row in rows
                ),
                "p95_error_deg": percentile(
                    [
                        row["p95_error_deg"]
                        for row in rows
                        if finite_number(row["p95_error_deg"])
                    ],
                    95.0,
                ),
                "p95_epoch_sample_error_px": percentile(
                    [
                        row["p95_error_px"]
                        for row in rows
                        if finite_number(row["p95_error_px"])
                    ],
                    95.0,
                ),
                "mean_invalid_time_rate_ge_100ms": strict_mean_or_nan(
                    row["invalid_time_rate_ge_100ms"] for row in rows
                ),
                "max_longest_invalid_episode_ms": max(
                    (
                        float(row["longest_invalid_episode_ms"])
                        for row in rows
                        if finite_number(row["longest_invalid_episode_ms"])
                    ),
                    default=math.nan,
                ),
                "correct_monitor_acquired_within_1s_rate": (
                    strict_mean_or_nan(
                        row["correct_monitor_acquired_within_1s"]
                        for row in rows
                    )
                ),
                "median_first_valid_latency_ms": median_or_nan(
                    row["first_valid_latency_ms"] for row in rows
                ),
                "median_first_sustained_correct_latency_ms": median_or_nan(
                    row["first_sustained_correct_latency_ms"] for row in rows
                ),
            }
        )
    return output


def compute_monitor_summaries(
    target_summaries: Sequence[Dict[str, Any]],
    epoch_metrics: Sequence[Dict[str, Any]],
) -> List[Dict[str, Any]]:
    target_groups: Dict[str, List[Dict[str, Any]]] = {}
    epoch_groups: Dict[str, List[Dict[str, Any]]] = {}
    for row in target_summaries:
        target_groups.setdefault(str(row["target_monitor"]), []).append(row)
    for row in epoch_metrics:
        epoch_groups.setdefault(str(row["target_monitor"]), []).append(row)

    output: List[Dict[str, Any]] = []
    for monitor in ("left", "center", "right"):
        targets = target_groups.get(monitor, [])
        epochs = epoch_groups.get(monitor, [])
        recovery_attempts = [
            row
            for row in epoch_metrics
            if row.get("previous_target_monitor") == monitor
            and int(row.get("is_block_first", 0)) == 0
        ]
        recovery_epochs = [
            row
            for row in recovery_attempts
            if finite_number(row.get("center_recovery_latency_ms"))
        ]
        if not targets:
            continue
        output.append(
            {
                "session_id": targets[0]["session_id"],
                "condition": targets[0]["condition"],
                "target_monitor": monitor,
                "target_n": len(targets),
                "epoch_n": len(epochs),
                "equal_target_sample_coverage": strict_mean_or_nan(
                    row["mean_sample_coverage"] for row in targets
                ),
                "equal_target_valid_rate": strict_mean_or_nan(
                    row["mean_valid_rate"] for row in targets
                ),
                "equal_target_binocular_valid_rate": strict_mean_or_nan(
                    row["mean_binocular_valid_rate"] for row in targets
                ),
                "equal_target_correct_monitor_rate": strict_mean_or_nan(
                    row["mean_correct_monitor_valid_rate"] for row in targets
                ),
                "equal_target_usable_monitor_rate": strict_mean_or_nan(
                    row["mean_usable_monitor_rate"] for row in targets
                ),
                "median_target_centroid_error_px": median_or_nan(
                    row["median_centroid_error_px"] for row in targets
                ),
                "p95_epoch_centroid_error_px": percentile(
                    [
                        row["centroid_error_px"]
                        for row in epochs
                        if finite_number(row["centroid_error_px"])
                    ],
                    95.0,
                ),
                "median_target_error_deg": median_or_nan(
                    row["median_error_deg"] for row in targets
                ),
                "p95_epoch_error_deg": percentile(
                    [
                        row["p95_error_deg"]
                        for row in epochs
                        if finite_number(row["p95_error_deg"])
                    ],
                    95.0,
                ),
                "equal_target_p95_sample_error_px": strict_mean_or_nan(
                    row["p95_epoch_sample_error_px"] for row in targets
                ),
                "worst_target_median_valid_rate": min(
                    (
                        float(row["median_repeat_valid_rate"])
                        for row in targets
                        if finite_number(row["median_repeat_valid_rate"])
                    ),
                    default=math.nan,
                ),
                "equal_target_invalid_time_rate_ge_100ms": (
                    strict_mean_or_nan(
                        row["mean_invalid_time_rate_ge_100ms"]
                        for row in targets
                    )
                ),
                "max_longest_invalid_episode_ms": max(
                    (
                        float(row["max_longest_invalid_episode_ms"])
                        for row in targets
                        if finite_number(
                            row["max_longest_invalid_episode_ms"]
                        )
                    ),
                    default=math.nan,
                ),
                "correct_monitor_acquired_within_1s_rate": (
                    strict_mean_or_nan(
                        row["correct_monitor_acquired_within_1s_rate"]
                        for row in targets
                    )
                ),
                "median_first_valid_latency_ms": median_or_nan(
                    row["median_first_valid_latency_ms"] for row in targets
                ),
                "median_first_sustained_correct_latency_ms": median_or_nan(
                    row["median_first_sustained_correct_latency_ms"]
                    for row in targets
                ),
                "recovery_attempt_n": len(recovery_attempts),
                "recovery_epoch_n": len(recovery_epochs),
                "recovery_success_rate": (
                    len(recovery_epochs) / len(recovery_attempts)
                    if recovery_attempts
                    else math.nan
                ),
                "median_center_recovery_latency_ms": median_or_nan(
                    row["center_recovery_latency_ms"]
                    for row in recovery_epochs
                ),
            }
        )
    return output


def assess_collection_quality(
    condition: str,
    epochs: Sequence[Epoch],
    epoch_metrics: Sequence[Dict[str, Any]],
    target_summaries: Sequence[Dict[str, Any]],
) -> Dict[str, Any]:
    expected_target_n = 5 if condition == "CENTER_BASELINE" else 15
    expected_epoch_n = expected_target_n * 3
    reasons: List[str] = []

    if len(epochs) != expected_epoch_n:
        reasons.append(
            f"planned_epoch_count={len(epochs)}, expected={expected_epoch_n}"
        )
    if len(epoch_metrics) != expected_epoch_n:
        reasons.append(
            "epoch_metric_count="
            f"{len(epoch_metrics)}, expected={expected_epoch_n}"
        )
    if len(target_summaries) != expected_target_n:
        reasons.append(
            "target_summary_count="
            f"{len(target_summaries)}, expected={expected_target_n}"
        )

    bad_repeat_targets = [
        str(row["target_id"])
        for row in target_summaries
        if int(row.get("repeat_n", 0)) != 3
    ]
    if bad_repeat_targets:
        reasons.append(
            "targets_without_exactly_three_repeats="
            + ",".join(bad_repeat_targets)
        )

    bad_event_epochs = [
        int(row["epoch"])
        for row in epoch_metrics
        if int(row.get("event_integrity", 0)) != 1
    ]
    if bad_event_epochs:
        reasons.append(
            "event_integrity_failed_epochs="
            + ",".join(str(value) for value in bad_event_epochs)
        )

    bad_coverage_epochs = [
        int(row["epoch"])
        for row in epoch_metrics
        if int(row.get("sample_coverage_ok", 0)) != 1
    ]
    if bad_coverage_epochs:
        reasons.append(
            "sample_coverage_failed_epochs="
            + ",".join(str(value) for value in bad_coverage_epochs)
        )

    missing_rate_epochs = [
        int(row["epoch"])
        for row in epoch_metrics
        if not finite_number(row.get("any_eye_valid_rate"))
    ]
    if missing_rate_epochs:
        reasons.append(
            "missing_valid_rate_epochs="
            + ",".join(str(value) for value in missing_rate_epochs)
        )

    coverages = [
        float(row["sample_coverage"])
        for row in epoch_metrics
        if finite_number(row.get("sample_coverage"))
    ]
    median_coverage = median_or_nan(coverages)
    if (
        not finite_number(median_coverage)
        or median_coverage < 0.90
        or median_coverage > 1.10
    ):
        reasons.append(
            "condition_median_sample_coverage_outside_0.90_to_1.10"
        )

    return {
        "complete": not reasons,
        "status": "DATA_COMPLETE" if not reasons else "DATA_INCOMPLETE",
        "condition": condition,
        "planned_epoch_n": len(epochs),
        "expected_epoch_n": expected_epoch_n,
        "target_summary_n": len(target_summaries),
        "expected_target_n": expected_target_n,
        "median_sample_coverage": median_coverage,
        "bad_event_epochs": bad_event_epochs,
        "bad_coverage_epochs": bad_coverage_epochs,
        "missing_rate_epochs": missing_rate_epochs,
        "reasons": reasons,
    }


def write_csv(
    path: Path,
    fieldnames: Sequence[str],
    rows: Sequence[Dict[str, Any]],
) -> None:
    with path.open("x", newline="", encoding="utf-8") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames, extrasaction="ignore")
        writer.writeheader()
        for row in rows:
            writer.writerow(row)


def write_json(path: Path, value: Dict[str, Any]) -> None:
    with path.open("x", encoding="utf-8") as file:
        json.dump(json_safe(value), file, indent=2, ensure_ascii=False)
        file.write("\n")


def xml_escape(text: Any) -> str:
    return (
        str(text)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
    )


def write_svg_report(
    path: Path,
    session_id: str,
    condition: str,
    active_rect: Rect,
    segments: Sequence[Segment],
    target_summaries: Sequence[Dict[str, Any]],
    epoch_metrics: Sequence[Dict[str, Any]],
    monitor_summaries: Sequence[Dict[str, Any]],
    research_status: str,
    quality: Dict[str, Any],
    layout_warnings: Sequence[str],
) -> None:
    width = 1600
    plot_box_x = 80
    plot_y = 130
    plot_box_width = 1440
    plot_box_height = 360
    scale = min(
        plot_box_width / active_rect.width,
        plot_box_height / active_rect.height,
    )
    rendered_width = active_rect.width * scale
    rendered_height = active_rect.height * scale
    plot_x = plot_box_x + (plot_box_width - rendered_width) / 2.0
    plot_y = plot_y + (plot_box_height - rendered_height) / 2.0
    scale_x = scale
    scale_y = scale
    summary_y = 560
    height = 800
    quality_passed_epoch_n = sum(
        1
        for row in epoch_metrics
        if int(row.get("event_integrity", 0)) == 1
        and int(row.get("sample_coverage_ok", 0)) == 1
        and finite_number(row.get("any_eye_valid_rate"))
    )

    target_lookup = {row["target_id"]: row for row in target_summaries}
    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" '
        f'viewBox="0 0 {width} {height}">',
        '<rect width="100%" height="100%" fill="#f6f8fa"/>',
        f'<text x="60" y="50" font-family="Segoe UI, sans-serif" '
        f'font-size="26" font-weight="700">{xml_escape(session_id)}</text>',
        f'<text x="60" y="85" font-family="Segoe UI, sans-serif" '
        f'font-size="18">Condition: {xml_escape(condition)}</text>',
        f'<text x="620" y="85" font-family="Segoe UI, sans-serif" '
        f'font-size="18" font-weight="700">Research status: '
        f'{xml_escape(research_status)}</text>',
        f'<text x="60" y="112" font-family="Segoe UI, sans-serif" '
        f'font-size="15">Planned epochs: '
        f'{quality.get("planned_epoch_n", 0)}/'
        f'{quality.get("expected_epoch_n", 0)} | quality-passed: '
        f'{quality_passed_epoch_n}/{quality.get("expected_epoch_n", 0)} | '
        f'median sample coverage: '
        f'{format_percent(quality.get("median_sample_coverage"))} | '
        f'layout warnings: {len(layout_warnings)}</text>',
    ]
    if research_status != "RESEARCH_VALID_COMPLETE":
        lines.append(
            '<text x="800" y="315" text-anchor="middle" '
            'font-family="Segoe UI, sans-serif" font-size="64" '
            'font-weight="800" fill="#d62728" fill-opacity="0.18" '
            f'transform="rotate(-12 800 315)">'
            f'{xml_escape(research_status)}</text>'
        )
    colors = {"left": "#5b8ff9", "center": "#61d9a0", "right": "#f6bd16"}
    for segment in segments:
        x = plot_x + (segment.bounds.left - active_rect.left) * scale_x
        y = plot_y + (segment.bounds.top - active_rect.top) * scale_y
        w = segment.bounds.width * scale_x
        h = segment.bounds.height * scale_y
        lines.append(
            f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" '
            f'fill="#ffffff" stroke="{colors.get(segment.name, "#333")}" '
            f'stroke-width="3"/>'
        )
        lines.append(
            f'<text x="{x + 12:.1f}" y="{y + 28:.1f}" '
            f'font-family="Segoe UI, sans-serif" font-size="18" '
            f'fill="{colors.get(segment.name, "#333")}">{segment.name.upper()}</text>'
        )

    for epoch in epoch_metrics:
        if not finite_number(epoch["centroid_x_px"]):
            continue
        target_id = str(epoch["target_id"])
        target = target_lookup.get(target_id)
        if target is None:
            continue
        target_epoch = next(
            (
                candidate
                for candidate in epoch_metrics
                if candidate["target_id"] == target_id
                and candidate["epoch"] == epoch["epoch"]
            ),
            epoch,
        )
        tx = plot_x + (
            float(target_epoch["target_x_px"]) - active_rect.left
        ) * scale_x
        ty = plot_y + (
            float(target_epoch["target_y_px"]) - active_rect.top
        ) * scale_y
        gx = plot_x + (
            float(epoch["centroid_x_px"]) - active_rect.left
        ) * scale_x
        gy = plot_y + (
            float(epoch["centroid_y_px"]) - active_rect.top
        ) * scale_y
        color = colors.get(str(epoch["target_monitor"]), "#444")
        lines.extend(
            [
                f'<line x1="{tx:.1f}" y1="{ty:.1f}" x2="{gx:.1f}" y2="{gy:.1f}" '
                f'stroke="{color}" stroke-opacity="0.35"/>',
                f'<circle cx="{gx:.1f}" cy="{gy:.1f}" r="3.5" '
                f'fill="{color}" fill-opacity="0.65"/>',
            ]
        )

    for target in target_summaries:
        epoch = next(
            row for row in epoch_metrics if row["target_id"] == target["target_id"]
        )
        x = plot_x + (float(epoch["target_x_px"]) - active_rect.left) * scale_x
        y = plot_y + (float(epoch["target_y_px"]) - active_rect.top) * scale_y
        lines.append(
            f'<circle cx="{x:.1f}" cy="{y:.1f}" r="8" fill="none" '
            f'stroke="#111" stroke-width="2"/>'
        )
        has_centroid = any(
            row["target_id"] == target["target_id"]
            and finite_number(row.get("centroid_x_px"))
            for row in epoch_metrics
        )
        if not has_centroid:
            lines.extend(
                [
                    f'<line x1="{x - 10:.1f}" y1="{y - 10:.1f}" '
                    f'x2="{x + 10:.1f}" y2="{y + 10:.1f}" '
                    'stroke="#d62728" stroke-width="4"/>',
                    f'<line x1="{x - 10:.1f}" y1="{y + 10:.1f}" '
                    f'x2="{x + 10:.1f}" y2="{y - 10:.1f}" '
                    'stroke="#d62728" stroke-width="4"/>',
                ]
            )

    lines.append(
        f'<text x="70" y="{summary_y - 20}" font-family="Segoe UI, sans-serif" '
        f'font-size="20" font-weight="700">Monitor summary (equal-target weighted)</text>'
    )
    headers = [
        "Monitor",
        "Valid rate",
        "Classification",
        "Usable",
        "Median centroid error",
        "Worst target valid",
    ]
    column_x = [80, 260, 470, 700, 900, 1230]
    for x, header in zip(column_x, headers):
        lines.append(
            f'<text x="{x}" y="{summary_y + 18}" '
            f'font-family="Segoe UI, sans-serif" font-size="16" '
            f'font-weight="700">{header}</text>'
        )
    for index, row in enumerate(monitor_summaries):
        y = summary_y + 58 + index * 48
        values = [
            str(row["target_monitor"]).upper(),
            format_percent(row["equal_target_valid_rate"]),
            format_percent(row["equal_target_correct_monitor_rate"]),
            format_percent(row["equal_target_usable_monitor_rate"]),
            format_px(row["median_target_centroid_error_px"]),
            format_percent(row["worst_target_median_valid_rate"]),
        ]
        for x, value in zip(column_x, values):
            lines.append(
                f'<text x="{x}" y="{y}" font-family="Segoe UI, sans-serif" '
                f'font-size="17">{xml_escape(value)}</text>'
            )
    lines.append("</svg>")
    with path.open("x", encoding="utf-8") as file:
        file.write("\n".join(lines))
        file.write("\n")


def format_percent(value: Any) -> str:
    return f"{100.0 * float(value):.1f}%" if finite_number(value) else "n/a"


def format_px(value: Any) -> str:
    return f"{float(value):.1f} px" if finite_number(value) else "n/a"


def geometry_metadata(args, condition: str) -> Dict[str, Any]:
    result: Dict[str, Any] = {
        "monitor_width_mm": args.monitor_width_mm,
        "monitor_height_mm": args.monitor_height_mm,
        "bezel_mm": args.bezel_mm,
        "bezel_correction": args.bezel_correction,
        "view_distance_mm": args.view_distance_mm,
        "flat_coplanar": args.flat_coplanar,
    }
    if args.monitor_width_mm and args.view_distance_mm:
        multiplier = 0.0 if condition == "CENTER_BASELINE" else 1.3
        result["outer_20_percent_target_angle_deg"] = math.degrees(
            math.atan2(
                multiplier * args.monitor_width_mm,
                args.view_distance_mm,
            )
        )
        result["three_monitor_outer_edge_angle_deg"] = math.degrees(
            math.atan2(
                1.5 * args.monitor_width_mm,
                args.view_distance_mm,
            )
        )
    return result


def collect_command(args) -> int:
    dpi_awareness = set_process_dpi_awareness()
    project_root = Path(
        os.environ.get("AISIMULATION_PROJECT_DIR", str(DEFAULT_PROJECT_ROOT))
    )
    output_dir = (
        Path(args.output_dir)
        if args.output_dir
        else project_root / DEFAULT_OUTPUT_SUBDIR
    )
    output_dir.mkdir(parents=True, exist_ok=True)
    displays = enumerate_windows_displays()

    try:
        segments, active_rect, layout_warnings = build_segments(
            args.condition,
            displays,
            args.allow_layout_mismatch,
        )
    except RuntimeError as exc:
        if not args.dry_run:
            session_id = unique_session_id(args.condition, output_dir)
            preflight_path = output_dir / f"{session_id}_preflight.json"
            write_json(
                preflight_path,
                {
                    "schema_version": SCHEMA_VERSION,
                    "tool_version": TOOL_VERSION,
                    "session_id": session_id,
                    "condition": args.condition,
                    "status": "CONFIGURATION_FAILED",
                    "saved_at": iso_now(),
                    "reason": str(exc),
                    "dpi_awareness": dpi_awareness,
                    "windows_displays": [
                        display_to_dict(display) for display in displays
                    ],
                },
            )
            print("Preflight:", preflight_path)
        raise

    targets = build_targets(segments)
    epochs = build_epochs(targets, args.repeats, args.seed)

    print("Condition:", args.condition)
    print("DPI mode :", dpi_awareness)
    print("Displays :", len(displays))
    for display in displays:
        print(
            " ",
            display.device_name,
            rect_to_dict(display.bounds),
            "primary=" + str(display.primary),
            f"scale={display.scale_percent:.1f}%",
        )
    print("Active mapping rect:", rect_to_dict(active_rect))
    print("Targets:", len(targets), "x repeats", args.repeats, "=", len(epochs))
    for warning in layout_warnings:
        print("WARNING:", warning)

    if args.dry_run:
        for epoch in epochs:
            print(
                f"B{epoch.block} E{epoch.epoch:02d}",
                epoch.target.target_id,
                f"({epoch.target.x_px:.1f}, {epoch.target.y_px:.1f})",
            )
        print("Dry run complete. Tracker and GUI were not started.")
        return 0

    tr = load_tobii_sdk(Path(args.sdk_dir))
    tracker = select_tracker(tr, args.tracker_serial)
    nominal_frequency = float(tracker.get_gaze_output_frequency())
    tracker_display_area = display_area_metadata(tracker)
    tracker_metadata = {
        "address": tracker.address,
        "model": tracker.model,
        "serial_number": tracker.serial_number,
        "device_name": tracker.device_name,
        "firmware_version": tracker.firmware_version,
        "runtime_version": getattr(tracker, "runtime_version", ""),
        "gaze_output_frequency_hz": nominal_frequency,
        "eye_tracking_mode": optional_tracker_value(
            tracker.get_eye_tracking_mode
        ),
        "active_display_area": tracker_display_area,
    }

    expected_width = (
        args.monitor_width_mm
        if args.condition == "CENTER_BASELINE"
        else 3.0 * args.monitor_width_mm + 2.0 * args.bezel_mm
    )
    expected_height = args.monitor_height_mm
    width_mismatch = (
        abs(tracker_display_area["width_mm"] - expected_width)
        / expected_width
    )
    height_mismatch = (
        abs(tracker_display_area["height_mm"] - expected_height)
        / expected_height
    )
    area_mismatch = max(width_mismatch, height_mismatch)

    session_id = unique_session_id(args.condition, output_dir)
    paths = {
        "metadata_json": output_dir / f"{session_id}_metadata.json",
        "target_events_csv": output_dir / f"{session_id}_target_events.csv",
        "gaze_raw_csv": output_dir / f"{session_id}_gaze_raw.csv",
        "gaze_annotated_csv": output_dir / f"{session_id}_gaze_annotated.csv",
        "epoch_metrics_csv": output_dir / f"{session_id}_epoch_metrics.csv",
        "target_summary_csv": output_dir / f"{session_id}_target_summary.csv",
        "monitor_summary_csv": output_dir / f"{session_id}_monitor_summary.csv",
        "summary_json": output_dir / f"{session_id}_summary.json",
        "report_svg": output_dir / f"{session_id}_report.svg",
    }
    if area_mismatch > 0.15:
        message = (
            "Tracker Active Display Area mismatch: "
            f"actual={tracker_display_area['width_mm']:.1f} x "
            f"{tracker_display_area['height_mm']:.1f} mm, "
            f"expected~{expected_width:.1f} x {expected_height:.1f} mm."
        )
        layout_warnings.append(message)
        print("WARNING:", message)
        if not args.allow_display_area_mismatch:
            preflight_path = output_dir / f"{session_id}_preflight.json"
            write_json(
                preflight_path,
                {
                    "schema_version": SCHEMA_VERSION,
                    "tool_version": TOOL_VERSION,
                    "session_id": session_id,
                    "condition": args.condition,
                    "status": "CONFIGURATION_FAILED",
                    "saved_at": iso_now(),
                    "reason": message,
                    "dpi_awareness": dpi_awareness,
                    "windows_displays": [
                        display_to_dict(display) for display in displays
                    ],
                    "tracker": tracker_metadata,
                    "expected_active_display_area_mm": {
                        "width": expected_width,
                        "height": expected_height,
                    },
                    "preflight_output": str(preflight_path),
                },
            )
            print("Preflight:", preflight_path)
            print(
                "Refusing the physical run. Correct the Tobii display setup "
                "or use --allow-display-area-mismatch only for diagnosis."
            )
            return 3

    setup_reasons: List[str] = []
    if args.allow_layout_mismatch:
        setup_reasons.append("allow_layout_mismatch_used")
    if args.allow_display_area_mismatch:
        setup_reasons.append("allow_display_area_mismatch_used")
    if layout_warnings:
        setup_reasons.append("layout_or_display_area_warning_present")
    if args.repeats != 3:
        setup_reasons.append("repeats_must_equal_3")
    if args.flat_coplanar != "yes":
        setup_reasons.append("flat_coplanar_must_be_yes")
    if args.bezel_correction != "off":
        setup_reasons.append("bezel_correction_must_be_off")
    if not args.participant_code.strip():
        setup_reasons.append("participant_code_required")
    if not args.setup_id.strip():
        setup_reasons.append("setup_id_required")
    if not args.calibration_id.strip():
        setup_reasons.append("calibration_id_required")
    standard_timing = (
        abs(args.home_sec - 1.5) < 1e-9
        and abs(args.blank_sec - 0.25) < 1e-9
        and abs(args.target_sec - 2.5) < 1e-9
        and abs(args.settle_sec - 1.0) < 1e-9
    )
    if not standard_timing:
        setup_reasons.append("nonstandard_protocol_timing")
    if args.condition == "CENTER_BASELINE" and len(displays) != 3:
        setup_reasons.append(
            "center_baseline_requires_three_independent_displays"
        )
    if any(abs(display.scale_percent - 100.0) > 0.1 for display in displays):
        setup_reasons.append("windows_display_scaling_must_equal_100_percent")
    research_valid_setup = not setup_reasons

    run_label = args.run_label or args.condition.lower()
    started_at = iso_now()
    started_monotonic_ns = time.monotonic_ns()
    print("Tracker   :", tracker.model, tracker.serial_number)
    print(
        "DisplayArea:",
        f"{tracker_display_area['width_mm']:.1f} x "
        f"{tracker_display_area['height_mm']:.1f} mm",
    )
    print("Session   :", session_id)
    print("Output dir:", output_dir)
    print("Close X-Plane and the normal Tobii logger before continuing.")
    if setup_reasons:
        print("DIAGNOSTIC ONLY:", ", ".join(setup_reasons))

    event_sink = StreamingCsvSink(
        paths["target_events_csv"],
        EVENT_FIELDS,
    )
    try:
        recorder = ValidationRecorder(
            tr,
            tracker,
            session_id,
            args.condition,
            run_label,
            paths["gaze_raw_csv"],
            nominal_frequency,
        )
    except Exception:
        event_sink.close()
        raise
    gui: Optional[ValidationGui] = None
    completed = False
    aborted = False
    events: List[Dict[str, Any]] = []
    runtime_error = ""
    try:
        gui = ValidationGui(
            recorder,
            session_id,
            args.condition,
            displays,
            active_rect,
            segments,
            epochs,
            event_sink,
            args.home_sec,
            args.blank_sec,
            args.target_sec,
            args.settle_sec,
        )
        recorder.subscribe()
        completed, aborted, events = gui.run()
        if gui.callback_error:
            runtime_error = gui.callback_error
    except (Exception, KeyboardInterrupt) as exc:
        aborted = True
        runtime_error = f"{type(exc).__name__}: {exc}"
        print("Runtime error; preserving partial data:", runtime_error)
        if gui is not None and not gui.aborted and not gui.completed:
            try:
                gui.abort()
            except Exception:
                pass
    finally:
        try:
            recorder.unsubscribe()
        except Exception as exc:
            aborted = True
            unsubscribe_message = f"{type(exc).__name__}: {exc}"
            runtime_error = (
                runtime_error + " | " + unsubscribe_message
                if runtime_error
                else unsubscribe_message
            )
        try:
            recorder.close_writer()
        except Exception as exc:
            aborted = True
            writer_message = f"{type(exc).__name__}: {exc}"
            runtime_error = (
                runtime_error + " | " + writer_message
                if runtime_error
                else writer_message
            )
        if gui is not None:
            events = list(gui.events)
        try:
            event_sink.close()
        except Exception as exc:
            aborted = True
            event_message = f"{type(exc).__name__}: {exc}"
            runtime_error = (
                runtime_error + " | " + event_message
                if runtime_error
                else event_message
            )
    raw_rows = recorder.snapshot_rows()

    annotated_rows = annotate_rows(
        raw_rows,
        active_rect,
        segments,
        args.monitor_width_mm,
        args.monitor_height_mm,
        args.view_distance_mm,
    )
    epoch_metrics = compute_epoch_metrics(
        annotated_rows,
        events,
        epochs,
        nominal_frequency,
        args.target_sec - args.settle_sec,
    )
    target_summaries = compute_target_summaries(epoch_metrics)
    monitor_summaries = compute_monitor_summaries(
        target_summaries,
        epoch_metrics,
    )
    quality = assess_collection_quality(
        args.condition,
        epochs,
        epoch_metrics,
        target_summaries,
    )
    ended_at = iso_now()
    elapsed_sec = (
        time.monotonic_ns() - started_monotonic_ns
    ) / 1_000_000_000.0
    if not completed or aborted:
        research_status = "INCOMPLETE"
    elif not research_valid_setup:
        research_status = "DIAGNOSTIC_ONLY"
    elif not quality["complete"]:
        research_status = "DATA_INCOMPLETE"
    else:
        research_status = "RESEARCH_VALID_COMPLETE"

    metadata = {
        "schema_version": SCHEMA_VERSION,
        "tool_version": TOOL_VERSION,
        "session_id": session_id,
        "condition": args.condition,
        "run_label": run_label,
        "started_at": started_at,
        "ended_at": ended_at,
        "elapsed_sec": elapsed_sec,
        "completed": completed,
        "aborted": aborted,
        "runtime_error": runtime_error,
        "research_status": research_status,
        "research_valid_setup": research_valid_setup,
        "research_valid_data": bool(quality["complete"]),
        "research_valid": research_status == "RESEARCH_VALID_COMPLETE",
        "diagnostic_only": not research_valid_setup,
        "setup_reasons": setup_reasons,
        "dpi_awareness": dpi_awareness,
        "tracker": tracker_metadata,
        "windows_displays": [display_to_dict(display) for display in displays],
        "virtual_bounds": rect_to_dict(virtual_bounds(displays)),
        "active_gaze_mapping_rect": rect_to_dict(active_rect),
        "actual_validation_window_bounds": (
            rect_to_dict(gui.actual_window_bounds) if gui else None
        ),
        "physical_segments": [segment_to_dict(segment) for segment in segments],
        "targets": [target_to_dict(target) for target in targets],
        "protocol": {
            "repeats": args.repeats,
            "home_sec": args.home_sec,
            "blank_sec": args.blank_sec,
            "target_sec": args.target_sec,
            "settle_sec": args.settle_sec,
            "measure_sec": args.target_sec - args.settle_sec,
            "seed": args.seed,
            "valid_definition": (
                "At least one eye has validity=1 and finite x/y."
            ),
            "coordinates_are_unclamped": True,
            "target_positions": list(TARGET_LOCAL_POSITIONS),
            "target_onset_is_software_timestamp": True,
        },
        "geometry": geometry_metadata(args, args.condition),
        "setup_notes": {
            "participant_code": args.participant_code,
            "setup_id": args.setup_id,
            "calibration_id": args.calibration_id,
            "lighting": args.lighting,
            "eyewear": args.eyewear,
            "calibration_note": args.calibration_note,
            "tracker_mount": args.tracker_mount,
        },
        "layout_warnings": layout_warnings,
        "active_display_area_check": {
            "expected_width_mm": expected_width,
            "expected_height_mm": expected_height,
            "width_mismatch_fraction": width_mismatch,
            "height_mismatch_fraction": height_mismatch,
            "maximum_mismatch_fraction": area_mismatch,
            "tolerance_fraction": 0.15,
        },
        "collection_quality": quality,
        "outputs": {key: str(path) for key, path in paths.items()},
    }
    summary = {
        "metadata": metadata,
        "collection_quality": quality,
        "epoch_metrics": epoch_metrics,
        "monitor_summaries": monitor_summaries,
        "target_summaries": target_summaries,
    }

    write_csv(paths["gaze_annotated_csv"], ANNOTATED_FIELDS, annotated_rows)
    write_csv(paths["epoch_metrics_csv"], EPOCH_FIELDS, epoch_metrics)
    write_csv(
        paths["target_summary_csv"],
        TARGET_SUMMARY_FIELDS,
        target_summaries,
    )
    write_csv(
        paths["monitor_summary_csv"],
        MONITOR_SUMMARY_FIELDS,
        monitor_summaries,
    )
    write_json(paths["summary_json"], summary)
    write_svg_report(
        paths["report_svg"],
        session_id,
        args.condition,
        active_rect,
        segments,
        target_summaries,
        epoch_metrics,
        monitor_summaries,
        research_status,
        quality,
        layout_warnings,
    )
    # Metadata is deliberately written last. Its presence means all other
    # declared artifacts reached their normal final-write path.
    write_json(paths["metadata_json"], metadata)

    print()
    print("Completed:", completed, "Aborted:", aborted)
    print("Research status:", research_status)
    print("Raw samples:", len(raw_rows))
    for row in monitor_summaries:
        print(
            f"{str(row['target_monitor']).upper():>6}",
            "valid=" + format_percent(row["equal_target_valid_rate"]),
            "classification="
            + format_percent(row["equal_target_correct_monitor_rate"]),
            "median_error="
            + format_px(row["median_target_centroid_error_px"]),
        )
    print("Summary:", paths["summary_json"])
    print("Report :", paths["report_svg"])
    return (
        0
        if research_status == "RESEARCH_VALID_COMPLETE"
        else 4
    )


def summary_by_monitor(summary: Dict[str, Any]) -> Dict[str, Dict[str, Any]]:
    return {
        str(row["target_monitor"]): row
        for row in summary.get("monitor_summaries", [])
    }


def decision_row(
    criterion: str,
    monitor: str,
    value: Any,
    threshold: str,
    passed: Optional[bool],
    note: str,
) -> Dict[str, Any]:
    return {
        "criterion": criterion,
        "monitor": monitor,
        "value": value,
        "threshold": threshold,
        "pass": "" if passed is None else int(passed),
        "note": note,
    }


def compare_summaries(
    baseline: Dict[str, Any],
    surround: Dict[str, Any],
) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    baseline_metadata = baseline.get("metadata", {})
    surround_metadata = surround.get("metadata", {})
    baseline_condition = baseline_metadata.get("condition")
    surround_condition = surround_metadata.get("condition")
    if baseline_condition != "CENTER_BASELINE":
        raise RuntimeError(
            f"Baseline condition must be CENTER_BASELINE, got {baseline_condition}"
        )
    if surround_condition != "SURROUND_WIDE":
        raise RuntimeError(
            f"Surround condition must be SURROUND_WIDE, got {surround_condition}"
        )

    baseline_monitors = summary_by_monitor(baseline)
    surround_monitors = summary_by_monitor(surround)
    if "center" not in baseline_monitors:
        raise RuntimeError("Baseline summary has no center monitor result.")

    decisions: List[Dict[str, Any]] = []
    baseline_complete = bool(baseline_metadata.get("completed")) and not bool(
        baseline_metadata.get("aborted")
    )
    surround_complete = bool(surround_metadata.get("completed")) and not bool(
        surround_metadata.get("aborted")
    )
    surround_display_count = len(
        surround_metadata.get("windows_displays", [])
    )
    surround_single_display = surround_display_count == 1

    surround_geometry = surround_metadata.get("geometry", {})
    surround_tracker = surround_metadata.get("tracker", {})
    monitor_width_mm = float_or_nan(
        surround_geometry.get("monitor_width_mm")
    )
    bezel_mm = float_or_nan(surround_geometry.get("bezel_mm"))
    active_width_mm = float_or_nan(
        surround_tracker.get("active_display_area", {}).get("width_mm")
    )
    expected_active_width_mm = (
        3.0 * monitor_width_mm + 2.0 * bezel_mm
        if finite_number(monitor_width_mm) and finite_number(bezel_mm)
        else math.nan
    )
    active_width_mismatch = (
        abs(active_width_mm - expected_active_width_mm)
        / expected_active_width_mm
        if (
            finite_number(active_width_mm)
            and finite_number(expected_active_width_mm)
            and expected_active_width_mm > 0.0
        )
        else math.nan
    )
    active_width_pass = (
        finite_number(active_width_mismatch)
        and active_width_mismatch <= 0.15
    )
    baseline_research_valid = bool(baseline_metadata.get("research_valid"))
    surround_research_valid = bool(surround_metadata.get("research_valid"))
    baseline_quality_complete = bool(
        baseline.get("collection_quality", {}).get("complete")
    )
    surround_quality_complete = bool(
        surround.get("collection_quality", {}).get("complete")
    )

    baseline_protocol = baseline_metadata.get("protocol", {})
    surround_protocol = surround_metadata.get("protocol", {})
    protocol_keys = (
        "repeats",
        "home_sec",
        "blank_sec",
        "target_sec",
        "settle_sec",
        "measure_sec",
        "target_positions",
    )
    protocol_match = all(
        baseline_protocol.get(key) == surround_protocol.get(key)
        for key in protocol_keys
    )
    protocol_counts_ok = (
        baseline_protocol.get("repeats") == 3
        and surround_protocol.get("repeats") == 3
        and len(baseline.get("epoch_metrics", [])) == 15
        and len(surround.get("epoch_metrics", [])) == 45
        and len(baseline.get("target_summaries", [])) == 5
        and len(surround.get("target_summaries", [])) == 15
    )

    baseline_tracker = baseline_metadata.get("tracker", {})
    same_tracker = (
        bool(baseline_tracker.get("serial_number"))
        and baseline_tracker.get("serial_number")
        == surround_tracker.get("serial_number")
    )
    baseline_frequency = float_or_nan(
        baseline_tracker.get("gaze_output_frequency_hz")
    )
    surround_frequency = float_or_nan(
        surround_tracker.get("gaze_output_frequency_hz")
    )
    same_frequency = (
        finite_number(baseline_frequency)
        and finite_number(surround_frequency)
        and abs(baseline_frequency - surround_frequency) <= 0.1
    )

    baseline_geometry = baseline_metadata.get("geometry", {})
    same_panel_geometry = all(
        finite_number(baseline_geometry.get(key))
        and finite_number(surround_geometry.get(key))
        and abs(
            float(baseline_geometry[key]) - float(surround_geometry[key])
        )
        <= 1.0
        for key in ("monitor_width_mm", "monitor_height_mm")
    )
    baseline_distance = float_or_nan(
        baseline_geometry.get("view_distance_mm")
    )
    surround_distance = float_or_nan(
        surround_geometry.get("view_distance_mm")
    )
    view_distance_match = (
        finite_number(baseline_distance)
        and finite_number(surround_distance)
        and abs(baseline_distance - surround_distance) <= 20.0
    )
    baseline_notes = baseline_metadata.get("setup_notes", {})
    surround_notes = surround_metadata.get("setup_notes", {})
    same_participant = (
        bool(baseline_notes.get("participant_code"))
        and baseline_notes.get("participant_code")
        == surround_notes.get("participant_code")
    )
    same_setup_id = (
        bool(baseline_notes.get("setup_id"))
        and baseline_notes.get("setup_id")
        == surround_notes.get("setup_id")
    )

    def center_segment_size(metadata: Dict[str, Any]) -> Tuple[int, int]:
        for segment in metadata.get("physical_segments", []):
            if segment.get("name") == "center":
                bounds = segment.get("bounds", {})
                return (
                    int(bounds.get("right", 0)) - int(bounds.get("left", 0)),
                    int(bounds.get("bottom", 0)) - int(bounds.get("top", 0)),
                )
        return 0, 0

    baseline_center_size = center_segment_size(baseline_metadata)
    surround_center_size = center_segment_size(surround_metadata)
    center_pixel_geometry_match = (
        baseline_center_size[0] > 0
        and baseline_center_size == surround_center_size
    )
    compatible_schema = (
        baseline_metadata.get("schema_version") == SCHEMA_VERSION
        and surround_metadata.get("schema_version") == SCHEMA_VERSION
        and baseline_metadata.get("tool_version")
        == surround_metadata.get("tool_version")
    )

    comparability_passes = [
        baseline_complete,
        surround_complete,
        surround_single_display,
        active_width_pass,
        baseline_research_valid,
        surround_research_valid,
        baseline_quality_complete,
        surround_quality_complete,
        protocol_match,
        protocol_counts_ok,
        same_tracker,
        same_frequency,
        same_panel_geometry,
        view_distance_match,
        same_participant,
        same_setup_id,
        center_pixel_geometry_match,
        compatible_schema,
    ]
    decisions.extend(
        [
            decision_row(
                "run_complete",
                "baseline",
                int(baseline_complete),
                "required",
                baseline_complete,
                "Partial or aborted runs cannot be used for comparison.",
            ),
            decision_row(
                "run_complete",
                "surround",
                int(surround_complete),
                "required",
                surround_complete,
                "Partial or aborted runs cannot be used for comparison.",
            ),
            decision_row(
                "single_logical_windows_display",
                "surround",
                surround_display_count,
                "= 1",
                surround_single_display,
                "Multiple displays indicate extended desktop, not Surround.",
            ),
            decision_row(
                "active_display_area_width_mismatch",
                "surround",
                active_width_mismatch,
                "<= 0.15",
                active_width_pass,
                (
                    f"actual={format_px(active_width_mm).replace(' px', ' mm')}, "
                    "expected="
                    f"{format_px(expected_active_width_mm).replace(' px', ' mm')}"
                ),
            ),
            decision_row(
                "research_valid_collection",
                "baseline",
                int(baseline_research_valid),
                "required",
                baseline_research_valid,
                str(baseline_metadata.get("research_status", "")),
            ),
            decision_row(
                "research_valid_collection",
                "surround",
                int(surround_research_valid),
                "required",
                surround_research_valid,
                str(surround_metadata.get("research_status", "")),
            ),
            decision_row(
                "collection_quality_complete",
                "baseline",
                int(baseline_quality_complete),
                "required",
                baseline_quality_complete,
                "All 15 epochs must pass event and stream checks.",
            ),
            decision_row(
                "collection_quality_complete",
                "surround",
                int(surround_quality_complete),
                "required",
                surround_quality_complete,
                "All 45 epochs must pass event and stream checks.",
            ),
            decision_row(
                "protocol_match",
                "pair",
                int(protocol_match),
                "required",
                protocol_match,
                "Target layout and timing must match.",
            ),
            decision_row(
                "protocol_counts",
                "pair",
                int(protocol_counts_ok),
                "15/45 epochs; 5/15 targets; 3 repeats",
                protocol_counts_ok,
                "Missing targets or repeats make the result inconclusive.",
            ),
            decision_row(
                "same_tracker_serial",
                "pair",
                int(same_tracker),
                "required",
                same_tracker,
                (
                    f"baseline={baseline_tracker.get('serial_number')}, "
                    f"surround={surround_tracker.get('serial_number')}"
                ),
            ),
            decision_row(
                "same_gaze_frequency",
                "pair",
                abs(baseline_frequency - surround_frequency)
                if finite_number(baseline_frequency)
                and finite_number(surround_frequency)
                else math.nan,
                "<= 0.1 Hz difference",
                same_frequency,
                (
                    f"baseline={baseline_frequency}, "
                    f"surround={surround_frequency}"
                ),
            ),
            decision_row(
                "same_panel_geometry",
                "pair",
                int(same_panel_geometry),
                "<= 1 mm difference",
                same_panel_geometry,
                "Panel width and height must match.",
            ),
            decision_row(
                "view_distance_difference_mm",
                "pair",
                abs(baseline_distance - surround_distance)
                if finite_number(baseline_distance)
                and finite_number(surround_distance)
                else math.nan,
                "<= 20 mm",
                view_distance_match,
                (
                    f"baseline={baseline_distance}, "
                    f"surround={surround_distance}"
                ),
            ),
            decision_row(
                "same_participant_code",
                "pair",
                int(same_participant),
                "required",
                same_participant,
                (
                    f"baseline={baseline_notes.get('participant_code')}, "
                    f"surround={surround_notes.get('participant_code')}"
                ),
            ),
            decision_row(
                "same_setup_id",
                "pair",
                int(same_setup_id),
                "required",
                same_setup_id,
                (
                    f"baseline={baseline_notes.get('setup_id')}, "
                    f"surround={surround_notes.get('setup_id')}"
                ),
            ),
            decision_row(
                "center_pixel_geometry_match",
                "pair",
                int(center_pixel_geometry_match),
                "required",
                center_pixel_geometry_match,
                (
                    f"baseline={baseline_center_size}, "
                    f"surround={surround_center_size}"
                ),
            ),
            decision_row(
                "compatible_schema_and_tool",
                "pair",
                int(compatible_schema),
                "required",
                compatible_schema,
                (
                    f"schema={baseline_metadata.get('schema_version')}/"
                    f"{surround_metadata.get('schema_version')}"
                ),
            ),
        ]
    )

    side_passes: List[bool] = []
    for monitor in ("left", "right"):
        row = surround_monitors.get(monitor)
        if row is None:
            comparability_passes.append(False)
            decisions.append(
                decision_row(
                    "side_result_present",
                    monitor,
                    "",
                    "required",
                    False,
                    "Missing monitor result.",
                )
            )
            side_passes.append(False)
            continue
        valid = float_or_nan(row.get("equal_target_valid_rate"))
        classification = float_or_nan(
            row.get("equal_target_correct_monitor_rate")
        )
        worst_target = float_or_nan(
            row.get("worst_target_median_valid_rate")
        )
        valid_pass = finite_number(valid) and valid >= 0.70
        classification_pass = (
            finite_number(classification) and classification >= 0.90
        )
        worst_pass = finite_number(worst_target) and worst_target >= 0.50
        side_passes.extend([valid_pass, classification_pass, worst_pass])
        decisions.extend(
            [
                decision_row(
                    "equal_target_valid_rate",
                    monitor,
                    valid,
                    ">= 0.70",
                    valid_pass,
                    "Invalid samples stay in the valid-rate denominator.",
                ),
                decision_row(
                    "correct_monitor_among_valid",
                    monitor,
                    classification,
                    ">= 0.90",
                    classification_pass,
                    "Uses unclamped gaze coordinates.",
                ),
                decision_row(
                    "worst_target_median_valid_rate",
                    monitor,
                    worst_target,
                    ">= 0.50",
                    worst_pass,
                    "Prevents one blind corner being hidden by the mean.",
                ),
            ]
        )

    baseline_center = baseline_monitors["center"]
    surround_center = surround_monitors.get("center")
    comparability_passes.append(surround_center is not None)
    baseline_center_valid = float_or_nan(
        baseline_center.get("equal_target_valid_rate")
    )
    baseline_error = float_or_nan(
        baseline_center.get("median_target_centroid_error_px")
    )
    baseline_adequate = (
        finite_number(baseline_center_valid)
        and baseline_center_valid >= 0.70
        and finite_number(baseline_error)
        and baseline_error > 0.0
    )
    comparability_passes.append(baseline_adequate)
    decisions.append(
        decision_row(
            "baseline_center_adequate",
            "baseline",
            baseline_center_valid,
            "valid >= 0.70 and finite nonzero centroid error",
            baseline_adequate,
            f"centroid_error={format_px(baseline_error)}",
        )
    )

    center_error_pass = False
    center_valid_pass = False
    degradation = math.nan
    if surround_center is not None:
        surround_center_valid = float_or_nan(
            surround_center.get("equal_target_valid_rate")
        )
        surround_error = float_or_nan(
            surround_center.get("median_target_centroid_error_px")
        )
        center_valid_pass = (
            finite_number(surround_center_valid)
            and surround_center_valid >= 0.70
        )
        if (
            finite_number(baseline_error)
            and finite_number(surround_error)
            and baseline_error > 0.0
        ):
            degradation = (surround_error - baseline_error) / baseline_error
            center_error_pass = degradation <= 0.20
        decisions.extend(
            [
                decision_row(
                    "equal_target_valid_rate",
                    "center",
                    surround_center_valid,
                    ">= 0.70",
                    center_valid_pass,
                    "Prevents a sparse center centroid from passing.",
                ),
                decision_row(
                    "center_median_error_degradation",
                    "center",
                    degradation,
                    "<= 0.20",
                    center_error_pass,
                    (
                        f"baseline={format_px(baseline_error)}, "
                        f"surround={format_px(surround_error)}"
                    ),
                ),
            ]
        )
    else:
        decisions.append(
            decision_row(
                "center_result_present",
                "center",
                "",
                "required",
                False,
                "Missing Surround center result.",
            )
        )

    comparable = all(comparability_passes)
    performance_pass = (
        all(side_passes) and center_valid_pass and center_error_pass
    )
    if not comparable:
        decision_status = "INCONCLUSIVE"
    elif performance_pass:
        decision_status = "PASS"
    else:
        decision_status = "FAIL"
    monitor_level_pass = decision_status == "PASS"
    decision_summary = {
        "baseline_session_id": baseline.get("metadata", {}).get("session_id"),
        "surround_session_id": surround.get("metadata", {}).get("session_id"),
        "monitor_level_pass": monitor_level_pass,
        "decision_status": decision_status,
        "comparable_research_data": comparable,
        "performance_thresholds_pass": performance_pass,
        "fine_aoi_pass": None,
        "fine_aoi_note": (
            "Not decided by monitor-level validation. Compare side p95 error "
            "with the future runway/intruder AOI margin."
        ),
        "center_degradation_fraction": degradation,
        "temporary_thresholds": {
            "each_side_valid_rate_min": 0.70,
            "each_side_classification_min": 0.90,
            "each_side_worst_target_median_valid_min": 0.50,
            "center_valid_rate_min": 0.70,
            "baseline_center_valid_rate_min": 0.70,
            "center_error_degradation_max": 0.20,
        },
    }
    return decisions, decision_summary


def compare_command(args) -> int:
    baseline_path = Path(args.baseline_summary)
    surround_path = Path(args.surround_summary)
    with baseline_path.open("r", encoding="utf-8") as file:
        baseline = json.load(file)
    with surround_path.open("r", encoding="utf-8") as file:
        surround = json.load(file)

    decisions, decision_summary = compare_summaries(baseline, surround)
    if args.output_prefix:
        prefix = Path(args.output_prefix)
    else:
        prefix = surround_path.with_name(
            surround_path.name.replace("_summary.json", "_comparison")
        )
    csv_path = Path(str(prefix) + "_decision.csv")
    json_path = Path(str(prefix) + "_decision.json")
    if csv_path.exists() or json_path.exists():
        raise RuntimeError(
            f"Comparison output already exists: {csv_path} or {json_path}"
        )
    write_csv(
        csv_path,
        ["criterion", "monitor", "value", "threshold", "pass", "note"],
        decisions,
    )
    write_json(
        json_path,
        {"summary": decision_summary, "criteria": decisions},
    )
    print("DECISION_STATUS:", decision_summary["decision_status"])
    print("MONITOR_LEVEL_PASS:", decision_summary["monitor_level_pass"])
    print("FINE_AOI_PASS: not evaluated")
    for row in decisions:
        print(
            row["monitor"],
            row["criterion"],
            "value=" + str(row["value"]),
            "pass=" + str(row["pass"]),
        )
    print("Decision CSV :", csv_path)
    print("Decision JSON:", json_path)
    if decision_summary["decision_status"] == "PASS":
        return 0
    if decision_summary["decision_status"] == "FAIL":
        return 5
    return 6


def self_test_command(_args) -> int:
    display = DisplayInfo(
        device_name="TEST",
        bounds=Rect(0, 0, 5760, 1080),
        work_area=Rect(0, 0, 5760, 1040),
        primary=True,
        dpi_x=96,
        dpi_y=96,
    )
    segments, active_rect, warnings = build_segments(
        "SURROUND_WIDE",
        [display],
        False,
    )
    assert not warnings
    assert [segment.bounds.width for segment in segments] == [1920, 1920, 1920]
    targets = build_targets(segments)
    assert len(targets) == 15
    epochs = build_epochs(targets, 3, 260724)
    assert len(epochs) == 45
    for block in range(1, 4):
        block_epochs = [epoch for epoch in epochs if epoch.block == block]
        assert len({epoch.target.target_id for epoch in block_epochs}) == 15
    center_target = next(target for target in targets if target.target_id == "CENTER_C")
    assert center_target.x_px == 2880.0
    assert center_target.y_px == 540.0
    assert active_rect.width == 5760

    perfect_rows = []
    for epoch in epochs:
        perfect_rows.append(
            {
                "session_id": "self_test",
                "condition": "SURROUND_WIDE",
                "block": epoch.block,
                "epoch": epoch.epoch,
                "sequence": epoch.sequence,
                "phase": "measure",
                "pc_time_ns": epoch.epoch * 1_000_000_000,
                "pc_monotonic_ns": epoch.epoch * 1_000_000_000,
                "phase_start_ns": epoch.epoch * 1_000_000_000,
                "phase_start_monotonic_ns": epoch.epoch * 1_000_000_000,
                "target_on_ns": epoch.epoch * 1_000_000_000,
                "target_on_monotonic_ns": epoch.epoch * 1_000_000_000,
                "target_id": epoch.target.target_id,
                "target_monitor": epoch.target.monitor,
                "target_position": epoch.target.position,
                "target_local_x": epoch.target.local_x,
                "target_local_y": epoch.target.local_y,
                "target_x_px": epoch.target.x_px,
                "target_y_px": epoch.target.y_px,
                "left_gaze_x": (
                    epoch.target.x_px - active_rect.left
                ) / active_rect.width,
                "left_gaze_y": (
                    epoch.target.y_px - active_rect.top
                ) / active_rect.height,
                "left_gaze_validity": 1,
                "right_gaze_x": (
                    epoch.target.x_px - active_rect.left
                ) / active_rect.width,
                "right_gaze_y": (
                    epoch.target.y_px - active_rect.top
                ) / active_rect.height,
                "right_gaze_validity": 1,
            }
        )
    annotated = annotate_rows(
        perfect_rows,
        active_rect,
        segments,
        598.0,
        336.0,
        800.0,
    )
    assert all(row["correct_monitor"] == 1 for row in annotated)
    assert all(abs(row["error_px"]) < 1e-9 for row in annotated)
    print("Self-test passed.")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Tobii Pro Spark multi-monitor feasibility validation."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    collect = subparsers.add_parser(
        "collect",
        help="Present targets and collect one validation run.",
    )
    collect.add_argument(
        "--condition",
        required=True,
        choices=("CENTER_BASELINE", "SURROUND_WIDE"),
    )
    collect.add_argument("--run-label", default="")
    collect.add_argument("--output-dir")
    collect.add_argument("--sdk-dir", default=str(DEFAULT_SDK_DIR))
    collect.add_argument("--tracker-serial", default=DEFAULT_TRACKER_SERIAL)
    collect.add_argument("--repeats", type=int, default=3)
    collect.add_argument("--home-sec", type=float, default=1.5)
    collect.add_argument("--blank-sec", type=float, default=0.25)
    collect.add_argument("--target-sec", type=float, default=2.5)
    collect.add_argument("--settle-sec", type=float, default=1.0)
    collect.add_argument("--seed", type=int, default=260724)
    collect.add_argument("--monitor-width-mm", type=float)
    collect.add_argument("--monitor-height-mm", type=float)
    collect.add_argument(
        "--bezel-mm",
        type=float,
        help=(
            "Physical gap in mm between adjacent visible display areas; "
            "not one plastic bezel's width."
        ),
    )
    collect.add_argument(
        "--bezel-correction",
        choices=("off", "on"),
        default="off",
    )
    collect.add_argument("--view-distance-mm", type=float)
    collect.add_argument(
        "--flat-coplanar",
        choices=("yes", "no", "unknown"),
        default="unknown",
    )
    collect.add_argument("--lighting", default="")
    collect.add_argument("--eyewear", default="")
    collect.add_argument("--calibration-note", default="")
    collect.add_argument("--participant-code", default="")
    collect.add_argument("--setup-id", default="")
    collect.add_argument("--calibration-id", default="")
    collect.add_argument("--tracker-mount", default="center-monitor-bottom")
    collect.add_argument("--allow-layout-mismatch", action="store_true")
    collect.add_argument("--allow-display-area-mismatch", action="store_true")
    collect.add_argument("--dry-run", action="store_true")
    collect.set_defaults(func=collect_command)

    compare = subparsers.add_parser(
        "compare",
        help="Compare CENTER_BASELINE and SURROUND_WIDE summaries.",
    )
    compare.add_argument("--baseline-summary", required=True)
    compare.add_argument("--surround-summary", required=True)
    compare.add_argument("--output-prefix")
    compare.set_defaults(func=compare_command)

    self_test = subparsers.add_parser(
        "self-test",
        help="Run deterministic geometry and annotation checks.",
    )
    self_test.set_defaults(func=self_test_command)
    return parser


def validate_args(args) -> None:
    if args.command != "collect":
        return
    if args.repeats < 1:
        raise ValueError("--repeats must be at least 1.")
    if args.home_sec <= 0 or args.blank_sec < 0 or args.target_sec <= 0:
        raise ValueError("Protocol durations must be positive.")
    if args.settle_sec < 0 or args.settle_sec >= args.target_sec:
        raise ValueError("--settle-sec must be >=0 and < --target-sec.")
    if args.dry_run:
        return
    for name in ("monitor_width_mm", "monitor_height_mm", "view_distance_mm"):
        value = getattr(args, name)
        if value is None or value <= 0.0:
            raise ValueError(
                f"--{name.replace('_', '-')} must be a measured positive "
                "value for a physical collection."
            )
    if args.condition == "SURROUND_WIDE" and args.bezel_mm is None:
        raise ValueError(
            "--bezel-mm must be supplied explicitly for SURROUND_WIDE."
        )
    if args.bezel_mm is not None and args.bezel_mm < 0.0:
        raise ValueError("--bezel-mm cannot be negative.")
    for value_name in (
        "monitor_width_mm",
        "monitor_height_mm",
        "view_distance_mm",
    ):
        value = getattr(args, value_name)
        if value is not None and value <= 0:
            raise ValueError(f"--{value_name.replace('_', '-')} must be > 0.")


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        validate_args(args)
        return int(args.func(args))
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        if os.environ.get("TOBII_VALIDATION_DEBUG") == "1":
            raise
        return 2


if __name__ == "__main__":
    sys.exit(main())
