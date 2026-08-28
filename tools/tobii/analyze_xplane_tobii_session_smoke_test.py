import math

from analyze_xplane_tobii_session import audio_spawn_timing_valid


def assert_timing(expected, *args):
    actual = audio_spawn_timing_valid(*args)
    if actual != expected:
        raise AssertionError(f"expected {expected!r}, got {actual!r}; args={args!r}")


def main():
    assert_timing(1, "audio_task_v34", 7.0, 7.0)
    assert_timing(0, "audio_task_v34", 7.0, 11.0)
    assert_timing(
        1,
        "audio_task_v39",
        7.0,
        12.0,
        14.0,
        True,
        False,
        0.5,
        -2.0,
        False,
        1.7,
    )
    assert_timing(
        1,
        "audio_task_v39",
        7.0,
        14.0,
        14.0,
        False,
        True,
    )
    assert_timing(
        0,
        "audio_task_v39",
        7.0,
        12.0,
        14.0,
        False,
        True,
    )
    assert_timing(
        0,
        "audio_task_v39",
        7.0,
        12.0,
        14.0,
        False,
        False,
    )
    assert_timing(
        0,
        "audio_task_v39",
        7.0,
        12.0,
        14.0,
        True,
        False,
        5.1,
        -0.2,
        True,
        1.7,
    )
    assert_timing(
        0,
        "audio_task_v35",
        7.0,
        12.0,
        14.0,
        True,
        False,
        0.5,
        -2.0,
        True,
        1.7,
    )
    assert_timing(
        1,
        "audio_task_v38",
        7.0,
        12.0,
        14.0,
        True,
        False,
        0.5,
        -2.0,
        False,
        1.7,
    )
    assert_timing(
        1,
        "audio_task_v37",
        7.0,
        12.0,
        14.0,
        True,
        False,
        0.5,
        -2.0,
        False,
        1.7,
    )
    assert_timing(
        1,
        "audio_task_v36",
        7.0,
        12.0,
        14.0,
        True,
        False,
        0.5,
        -2.0,
        False,
        1.7,
    )
    assert_timing(0, "audio_task_v36", math.nan, 12.0, 14.0, True, False)
    assert_timing("", "legacy_protocol", 7.0, 7.0)
    print("X-Plane + Tobii session analyzer smoke test: PASS")


if __name__ == "__main__":
    main()
