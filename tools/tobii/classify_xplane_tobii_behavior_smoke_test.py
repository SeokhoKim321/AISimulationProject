from classify_xplane_tobii_behavior import (
    AMBIGUOUS,
    AVOIDANCE_CANDIDATE,
    NO_VALID_RESPONSE,
    SPEED_TASK_CORRECTION,
    avoidance_kinematic_evidence,
    classify_behavior,
    speed_task_evidence,
)


def assert_class(expected, *args):
    actual, _ = classify_behavior(*args)
    if actual != expected:
        raise AssertionError(f"expected {expected}, got {actual}")


def main():
    assert_class(NO_VALID_RESPONSE, False, True, True, False, False)
    assert_class(NO_VALID_RESPONSE, True, False, True, False, False)
    assert_class(NO_VALID_RESPONSE, True, True, False, False, False)
    assert_class(SPEED_TASK_CORRECTION, True, True, True, True, False)
    assert_class(AVOIDANCE_CANDIDATE, True, True, True, False, True)
    assert_class(AMBIGUOUS, True, True, True, True, True)
    assert_class(AMBIGUOUS, True, True, True, False, False)
    speed_evidence, *_ = speed_task_evidence(100.0, 105.0, 102.0, "throttle", -0.1)
    if not speed_evidence:
        raise AssertionError("expected speed-task evidence for corrective throttle reduction")
    wrong_direction, *_ = speed_task_evidence(100.0, 105.0, 102.0, "throttle", 0.1)
    if wrong_direction:
        raise AssertionError("wrong-direction throttle must not be speed-task evidence")
    avoidance_evidence, improvement_m = avoidance_kinematic_evidence(10.0, 25.0)
    if not avoidance_evidence or improvement_m != 15.0:
        raise AssertionError("expected projected-CPA improvement evidence")
    weak_avoidance, _ = avoidance_kinematic_evidence(10.0, 18.0)
    if weak_avoidance:
        raise AssertionError("sub-threshold projected-CPA change must remain non-evidence")
    print("X-Plane + Tobii behavior classifier smoke test: PASS")


if __name__ == "__main__":
    main()
