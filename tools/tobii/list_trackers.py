import sys


def main():
    try:
        import tobii_research as tr
    except ImportError:
        print("ERROR: tobii_research package is not installed for this Python.")
        print("Install Tobii Pro SDK Python binding for Python 3.10, then run again.")
        return 2

    trackers = tr.find_all_eyetrackers()
    if not trackers:
        print("No Tobii eye tracker found.")
        print("Check USB/network connection, Eye Tracker Manager, and firewall.")
        return 1

    print(f"Found {len(trackers)} eye tracker(s):")
    for index, tracker in enumerate(trackers, start=1):
        print(f"[{index}] address={tracker.address}")
        print(f"    model={tracker.model}")
        print(f"    serial_number={tracker.serial_number}")
        print(f"    device_name={tracker.device_name}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
