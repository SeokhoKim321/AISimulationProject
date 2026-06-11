package com.example.ai;

public class XPlaneAutoEventDetector {
    private static final double HAZARD_HORIZONTAL_TRIGGER_METERS = 150.0;
    private static final double HAZARD_VERTICAL_TRIGGER_METERS = 30.0;
    private static final double HAZARD_HORIZONTAL_CLEAR_METERS = 200.0;
    private static final double HAZARD_VERTICAL_CLEAR_METERS = 50.0;

    private static final double PITCH_RESPONSE_THRESHOLD = 0.08;
    private static final double ROLL_RESPONSE_THRESHOLD = 0.08;
    private static final double YAW_RESPONSE_THRESHOLD = 0.05;
    private static final double THROTTLE_RESPONSE_THRESHOLD = 0.08;

    private static final double PITCH_SETTLE_THRESHOLD = 0.03;
    private static final double ROLL_SETTLE_THRESHOLD = 0.03;
    private static final double YAW_SETTLE_THRESHOLD = 0.02;
    private static final double THROTTLE_SETTLE_THRESHOLD = 0.03;
    private static final int REQUIRED_SETTLE_SAMPLES = 8;

    private final String sessionId;
    private final XPlaneEventLogger eventLogger;

    private XPlaneStateSample latestSample;
    private XPlaneIntruderSample latestIntruderSample;
    private XPlaneStateSample advisoryBaseline;
    private boolean advisoryActive;
    private boolean advisoryAutoManaged;
    private boolean hazardActive;
    private boolean responseActive;
    private int stableSampleCount;

    public XPlaneAutoEventDetector(String sessionId, XPlaneEventLogger eventLogger) {
        this.sessionId = sessionId;
        this.eventLogger = eventLogger;
    }

    public void onStateSample(XPlaneStateSample sample) {
        latestSample = sample;

        maybeUpdateHazardState();

        if (!advisoryActive || advisoryBaseline == null) {
            return;
        }

        if (!responseActive) {
            if (isResponseTriggered(sample, advisoryBaseline)) {
                responseActive = true;
                stableSampleCount = 0;
                eventLogger.log(buildAutoEvent(
                XPlaneEventType.PILOT_RESPONSE_START,
                sample,
                "Auto detected pilot response start after advisory"
                ));
            }
            return;
        }

        if (isResponseSettled(sample, advisoryBaseline)) {
            stableSampleCount++;
            if (stableSampleCount >= REQUIRED_SETTLE_SAMPLES) {
                responseActive = false;
                stableSampleCount = 0;
                eventLogger.log(buildAutoEvent(
                XPlaneEventType.PILOT_RESPONSE_END,
                sample,
                "Auto detected pilot response end after control stabilized"
                ));
            }
        } else {
            stableSampleCount = 0;
        }
    }

    public void onIntruderSample(XPlaneIntruderSample sample) {
        latestIntruderSample = sample;
        maybeUpdateHazardState();
    }

    public void onEventRecord(XPlaneEventRecord eventRecord) {
        if (eventRecord.eventType == XPlaneEventType.ADVISORY_SHOWN) {
            advisoryActive = true;
            advisoryAutoManaged = false;
            responseActive = false;
            stableSampleCount = 0;
            advisoryBaseline = latestSample;
        } else if (eventRecord.eventType == XPlaneEventType.ADVISORY_CLEARED) {
            closeAdvisoryWindow("Auto closed pilot response at advisory clear");
        }
    }

    private void maybeUpdateHazardState() {
        if (latestSample == null || latestIntruderSample == null) {
            return;
        }

        if (!hazardActive && isHazardTriggered(latestIntruderSample)) {
            hazardActive = true;
            eventLogger.log(buildHazardEvent(
                    XPlaneEventType.HAZARD_DETECTED,
                    latestSample,
                    latestIntruderSample,
                    "Auto detected hazard based on intruder separation"
            ));
            openAutomaticAdvisoryIfNeeded();
            return;
        }

        if (hazardActive && isHazardCleared(latestIntruderSample)) {
            hazardActive = false;
            eventLogger.log(buildHazardEvent(
                    XPlaneEventType.HAZARD_CLEARED,
                    latestSample,
                    latestIntruderSample,
                    "Auto cleared hazard after intruder separation recovered"
            ));
            closeAutomaticAdvisoryIfNeeded();
        }
    }

    private void openAutomaticAdvisoryIfNeeded() {
        if (advisoryActive || latestSample == null) {
            return;
        }

        advisoryActive = true;
        advisoryAutoManaged = true;
        responseActive = false;
        stableSampleCount = 0;
        advisoryBaseline = latestSample;
        eventLogger.log(buildAutoEvent(
                XPlaneEventType.ADVISORY_SHOWN,
                latestSample,
                "Auto generated advisory at hazard onset",
                "auto_advisory"
        ));
    }

    private void closeAutomaticAdvisoryIfNeeded() {
        if (!advisoryActive || !advisoryAutoManaged || latestSample == null) {
            return;
        }

        eventLogger.log(buildAutoEvent(
                XPlaneEventType.ADVISORY_CLEARED,
                latestSample,
                "Auto cleared advisory at hazard recovery",
                "auto_advisory"
        ));
        closeAdvisoryWindow("Auto closed pilot response at advisory clear");
    }

    private void closeAdvisoryWindow(String responseEndDetail) {
        advisoryActive = false;
        advisoryAutoManaged = false;
        advisoryBaseline = null;
        stableSampleCount = 0;

        if (responseActive && latestSample != null) {
            responseActive = false;
            eventLogger.log(buildAutoEvent(
                    XPlaneEventType.PILOT_RESPONSE_END,
                    latestSample,
                    responseEndDetail
            ));
        }
    }

    private boolean isHazardTriggered(XPlaneIntruderSample intruderSample) {
        return intruderSample.horizontalDistance <= HAZARD_HORIZONTAL_TRIGGER_METERS
                && intruderSample.verticalSeparation <= HAZARD_VERTICAL_TRIGGER_METERS;
    }

    private boolean isHazardCleared(XPlaneIntruderSample intruderSample) {
        return intruderSample.horizontalDistance >= HAZARD_HORIZONTAL_CLEAR_METERS
                || intruderSample.verticalSeparation >= HAZARD_VERTICAL_CLEAR_METERS;
    }

    private boolean isResponseTriggered(XPlaneStateSample current, XPlaneStateSample baseline) {
        return Math.abs(current.pitchInput - baseline.pitchInput) >= PITCH_RESPONSE_THRESHOLD
                || Math.abs(current.rollInput - baseline.rollInput) >= ROLL_RESPONSE_THRESHOLD
                || Math.abs(current.yawInput - baseline.yawInput) >= YAW_RESPONSE_THRESHOLD
                || Math.abs(current.throttleInput - baseline.throttleInput) >= THROTTLE_RESPONSE_THRESHOLD;
    }

    private boolean isResponseSettled(XPlaneStateSample current, XPlaneStateSample baseline) {
        return Math.abs(current.pitchInput - baseline.pitchInput) <= PITCH_SETTLE_THRESHOLD
                && Math.abs(current.rollInput - baseline.rollInput) <= ROLL_SETTLE_THRESHOLD
                && Math.abs(current.yawInput - baseline.yawInput) <= YAW_SETTLE_THRESHOLD
                && Math.abs(current.throttleInput - baseline.throttleInput) <= THROTTLE_SETTLE_THRESHOLD;
    }

    private XPlaneEventRecord buildAutoEvent(XPlaneEventType eventType, XPlaneStateSample sample, String detail) {
        return buildAutoEvent(eventType, sample, detail, "auto_detector");
    }

    private XPlaneEventRecord buildAutoEvent(
            XPlaneEventType eventType,
            XPlaneStateSample sample,
            String detail,
            String source
    ) {
        return new XPlaneEventRecord(
                System.currentTimeMillis(),
                sessionId,
                sample.trialId,
                eventType,
                source,
                detail,
                null,
                sample.sampleIndex,
                null,
                sample.simTimeS
        );
    }

    private XPlaneEventRecord buildHazardEvent(
            XPlaneEventType eventType,
            XPlaneStateSample stateSample,
            XPlaneIntruderSample intruderSample,
            String detailPrefix
    ) {
        String detail = String.format(
                "%s (horizontal_distance=%.3f, vertical_separation=%.3f, ownship_sample_index=%d, intruder_sample_index=%d)",
                detailPrefix,
                intruderSample.horizontalDistance,
                intruderSample.verticalSeparation,
                stateSample.sampleIndex,
                intruderSample.sampleIndex
        );

        return new XPlaneEventRecord(
                System.currentTimeMillis(),
                sessionId,
                stateSample.trialId,
                eventType,
                "hazard_detector",
                detail,
                null,
                stateSample.sampleIndex,
                intruderSample.sampleIndex,
                stateSample.simTimeS
        );
    }
}
