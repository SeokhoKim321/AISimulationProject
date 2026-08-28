package com.example.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

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

    private static final double RESPONSE_BASELINE_WINDOW_S = 1.0;
    private static final double RESPONSE_HISTORY_RETENTION_S = 1.5;
    private static final double RESPONSE_PERSISTENCE_S = 0.25;
    private static final int MIN_RESPONSE_BASELINE_SAMPLES = 3;
    private static final String SPAWN_RESPONSE_METHOD = "pre_spawn_mean_sustained_delta_v1";
    private static final String DETECTABILITY_RESPONSE_METHOD = "pre_detectability_mean_sustained_delta_v2";
    private static final String VISUAL_OPPORTUNITY_RESPONSE_METHOD =
            "pre_visual_opportunity_mean_sustained_delta_v3";
    private static final String VISUAL_DETECTABILITY_PROFILE = "profile=visual_detectability_proxy_v32";
    private static final String VISUAL_OPPORTUNITY_PROFILE_PREFIX = "profile=visual_opportunity_proxy_v";

    private final String sessionId;
    private final XPlaneEventLogger eventLogger;
    private final Deque<XPlaneStateSample> stateHistory = new ArrayDeque<>();

    private XPlaneStateSample latestSample;
    private XPlaneIntruderSample latestIntruderSample;
    private ControlBaseline responseBaseline;
    private XPlaneStateSample responseCandidateStart;
    private boolean trialOpen;
    private int activeTrialId = -1;
    private boolean intruderExposureActive;
    private boolean hazardActive;
    private boolean responseActive;
    private boolean responseDetected;
    private int stableSampleCount;
    private String responseAnchor = "INTRUDER_SPAWNED";
    private String responseMethod = SPAWN_RESPONSE_METHOD;

    public XPlaneAutoEventDetector(String sessionId, XPlaneEventLogger eventLogger) {
        this.sessionId = sessionId;
        this.eventLogger = eventLogger;
    }

    public void onStateSample(XPlaneStateSample sample) {
        latestSample = sample;
        updateStateHistory(sample);
        maybeUpdateHazardState();
        maybeUpdateResponseState(sample);
    }

    public void onIntruderSample(XPlaneIntruderSample sample) {
        latestIntruderSample = sample;
        maybeUpdateHazardState();
    }

    public void beforeEventRecord(XPlaneEventRecord eventRecord) {
        if (eventRecord.eventType == XPlaneEventType.TRIAL_END) {
            closeResponseAtTrialEnd();
        }
    }

    public void onEventRecord(XPlaneEventRecord eventRecord) {
        switch (eventRecord.eventType) {
            case TRIAL_RESET:
                resetTrialState(eventRecord.trialId, false);
                break;
            case TRIAL_START:
                resetTrialState(eventRecord.trialId, true);
                break;
            case INTRUDER_SPAWNED:
                intruderExposureActive = true;
                if (!usesDeferredVisualAnchor(eventRecord)) {
                    openResponseWindow(
                            eventRecord,
                            "INTRUDER_SPAWNED",
                            SPAWN_RESPONSE_METHOD
                    );
                }
                break;
            case INTRUDER_VISUAL_OPPORTUNITY_ONSET:
                openResponseWindow(
                        eventRecord,
                        "INTRUDER_VISUAL_OPPORTUNITY_ONSET",
                        VISUAL_OPPORTUNITY_RESPONSE_METHOD
                );
                break;
            case INTRUDER_VISUALLY_DETECTABLE:
                if (!"INTRUDER_VISUAL_OPPORTUNITY_ONSET".equals(responseAnchor)) {
                    openResponseWindow(
                            eventRecord,
                            "INTRUDER_VISUALLY_DETECTABLE",
                            DETECTABILITY_RESPONSE_METHOD
                    );
                }
                break;
            case TRIAL_END:
                resetTrialState(eventRecord.trialId, false);
                break;
            default:
                break;
        }
    }

    private void updateStateHistory(XPlaneStateSample sample) {
        if (!trialOpen || sample.trialId != activeTrialId) {
            return;
        }

        if (!stateHistory.isEmpty() && sample.simTimeS < stateHistory.getLast().simTimeS) {
            stateHistory.clear();
        }
        stateHistory.addLast(sample);

        double oldestAllowedTimeS = sample.simTimeS - RESPONSE_HISTORY_RETENTION_S;
        while (!stateHistory.isEmpty() && stateHistory.getFirst().simTimeS < oldestAllowedTimeS) {
            stateHistory.removeFirst();
        }
    }

    private void openResponseWindow(
            XPlaneEventRecord eventRecord,
            String anchor,
            String method
    ) {
        if (!trialOpen || eventRecord.trialId != activeTrialId) {
            resetTrialState(eventRecord.trialId, true);
        }

        double anchorTimeS = eventRecord.xplaneSimTimeS != null
                ? eventRecord.xplaneSimTimeS
                : latestSample != null ? latestSample.simTimeS : Double.NaN;
        responseBaseline = ControlBaseline.fromSamples(
                stateHistory,
                eventRecord.trialId,
                anchorTimeS
        );
        responseCandidateStart = null;
        responseActive = false;
        responseDetected = false;
        stableSampleCount = 0;
        intruderExposureActive = true;
        responseAnchor = anchor;
        responseMethod = method;

        eventLogger.log(buildResponseBaselineEvent(eventRecord, responseBaseline));
    }

    private boolean usesDeferredVisualAnchor(XPlaneEventRecord eventRecord) {
        return eventRecord.detail != null
                && (eventRecord.detail.contains(VISUAL_DETECTABILITY_PROFILE)
                || eventRecord.detail.contains(VISUAL_OPPORTUNITY_PROFILE_PREFIX));
    }

    private void maybeUpdateResponseState(XPlaneStateSample sample) {
        if (!trialOpen
                || !intruderExposureActive
                || responseBaseline == null
                || !responseBaseline.valid
                || sample.trialId != activeTrialId) {
            return;
        }

        if (!responseActive) {
            if (responseDetected) {
                return;
            }

            ResponseDelta delta = ResponseDelta.from(sample, responseBaseline);
            if (!delta.triggered) {
                responseCandidateStart = null;
                return;
            }

            if (responseCandidateStart == null) {
                responseCandidateStart = sample;
                return;
            }

            double persistenceS = sample.simTimeS - responseCandidateStart.simTimeS;
            if (persistenceS < RESPONSE_PERSISTENCE_S) {
                return;
            }

            ResponseDelta candidateDelta = ResponseDelta.from(responseCandidateStart, responseBaseline);
            responseActive = true;
            responseDetected = true;
            stableSampleCount = 0;
            eventLogger.log(buildResponseEvent(
                    XPlaneEventType.PILOT_RESPONSE_START,
                    responseCandidateStart,
                    String.format(
                            Locale.ROOT,
                            "anchor=%s;method=%s;persistence_required_s=%.3f;persistence_actual_s=%.3f;confirmed_sim_time_s=%.3f;trigger_axis=%s;active_control_at_anchor=%s;baseline_samples=%d;pitch_delta=%.4f;roll_delta=%.4f;yaw_delta=%.4f;throttle_delta=%.4f",
                            responseAnchor,
                            responseMethod,
                            RESPONSE_PERSISTENCE_S,
                            persistenceS,
                            sample.simTimeS,
                            candidateDelta.triggerAxis,
                            responseBaseline.activeControlAtAnchor,
                            responseBaseline.sampleCount,
                            candidateDelta.pitchDelta,
                            candidateDelta.rollDelta,
                            candidateDelta.yawDelta,
                            candidateDelta.throttleDelta
                    ) + activeControlAnchorDetail(responseBaseline.activeControlAtAnchor)
            ));
            return;
        }

        if (isResponseSettled(sample, responseBaseline)) {
            stableSampleCount++;
            if (stableSampleCount >= REQUIRED_SETTLE_SAMPLES) {
                responseActive = false;
                stableSampleCount = 0;
                eventLogger.log(buildResponseEvent(
                        XPlaneEventType.PILOT_RESPONSE_END,
                        sample,
                        String.format(
                                Locale.ROOT,
                                "anchor=%s;method=%s;reason=control_stabilized;required_settle_samples=%d",
                                responseAnchor,
                                responseMethod,
                                REQUIRED_SETTLE_SAMPLES
                        )
                ));
            }
        } else {
            stableSampleCount = 0;
        }
    }

    private void closeResponseAtTrialEnd() {
        if (!responseActive || latestSample == null) {
            return;
        }

        responseActive = false;
        stableSampleCount = 0;
        eventLogger.log(buildResponseEvent(
                XPlaneEventType.PILOT_RESPONSE_END,
                latestSample,
                String.format(
                        Locale.ROOT,
                        "anchor=%s;method=%s;reason=trial_end",
                        responseAnchor,
                        responseMethod
                )
        ));
    }

    private void resetTrialState(int trialId, boolean open) {
        activeTrialId = trialId;
        trialOpen = open;
        intruderExposureActive = false;
        hazardActive = false;
        responseBaseline = null;
        responseCandidateStart = null;
        responseActive = false;
        responseDetected = false;
        stableSampleCount = 0;
        responseAnchor = "INTRUDER_SPAWNED";
        responseMethod = SPAWN_RESPONSE_METHOD;
        stateHistory.clear();
    }

    private void maybeUpdateHazardState() {
        if (!trialOpen
                || !intruderExposureActive
                || latestSample == null
                || latestIntruderSample == null
                || latestSample.trialId != activeTrialId
                || latestIntruderSample.trialId != activeTrialId) {
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

    private boolean isResponseSettled(XPlaneStateSample current, ControlBaseline baseline) {
        return Math.abs(current.pitchInput - baseline.pitchMean) <= PITCH_SETTLE_THRESHOLD
                && Math.abs(current.rollInput - baseline.rollMean) <= ROLL_SETTLE_THRESHOLD
                && Math.abs(current.yawInput - baseline.yawMean) <= YAW_SETTLE_THRESHOLD
                && Math.abs(current.throttleInput - baseline.throttleMean) <= THROTTLE_SETTLE_THRESHOLD;
    }

    private String activeControlAnchorDetail(boolean activeControlAtAnchor) {
        if ("INTRUDER_VISUAL_OPPORTUNITY_ONSET".equals(responseAnchor)) {
            return ";active_control_at_visual_opportunity=" + activeControlAtAnchor;
        }
        if ("INTRUDER_VISUALLY_DETECTABLE".equals(responseAnchor)) {
            return ";active_control_at_detectability=" + activeControlAtAnchor;
        }
        return ";active_control_at_spawn=" + activeControlAtAnchor;
    }

    private XPlaneEventRecord buildResponseBaselineEvent(
            XPlaneEventRecord anchorEvent,
            ControlBaseline baseline
    ) {
        XPlaneStateSample sample = latestSample;
        long timestampMs = sample != null ? sample.receivedTimestampMs : anchorEvent.recordedTimestampMs;
        Integer stateSampleIndex = sample != null ? sample.sampleIndex : null;
        Double simTimeS = anchorEvent.xplaneSimTimeS != null
                ? anchorEvent.xplaneSimTimeS
                : sample != null ? sample.simTimeS : null;
        String detail = String.format(
                Locale.ROOT,
                "anchor=%s;method=%s;baseline_window_requested_s=%.3f;baseline_window_actual_s=%.3f;baseline_samples=%d;baseline_valid=%s;active_control_at_anchor=%s;active_control_rule=baseline_peak_to_peak_threshold;pitch_mean=%.4f;roll_mean=%.4f;yaw_mean=%.4f;throttle_mean=%.4f;pitch_range=%.4f;roll_range=%.4f;yaw_range=%.4f;throttle_range=%.4f",
                responseAnchor,
                responseMethod,
                RESPONSE_BASELINE_WINDOW_S,
                baseline.actualWindowS,
                baseline.sampleCount,
                baseline.valid,
                baseline.activeControlAtAnchor,
                baseline.pitchMean,
                baseline.rollMean,
                baseline.yawMean,
                baseline.throttleMean,
                baseline.pitchRange,
                baseline.rollRange,
                baseline.yawRange,
                baseline.throttleRange
        ) + activeControlAnchorDetail(baseline.activeControlAtAnchor);
        return new XPlaneEventRecord(
                timestampMs,
                sessionId,
                anchorEvent.trialId,
                XPlaneEventType.PILOT_RESPONSE_BASELINE,
                "spawn_response_detector",
                detail,
                null,
                stateSampleIndex,
                null,
                simTimeS
        );
    }

    private XPlaneEventRecord buildResponseEvent(
            XPlaneEventType eventType,
            XPlaneStateSample sample,
            String detail
    ) {
        return new XPlaneEventRecord(
                sample.receivedTimestampMs,
                sessionId,
                sample.trialId,
                eventType,
                "spawn_response_detector",
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
                Locale.ROOT,
                "%s (horizontal_distance=%.3f, vertical_separation=%.3f, ownship_sample_index=%d, intruder_sample_index=%d)",
                detailPrefix,
                intruderSample.horizontalDistance,
                intruderSample.verticalSeparation,
                stateSample.sampleIndex,
                intruderSample.sampleIndex
        );

        return new XPlaneEventRecord(
                stateSample.receivedTimestampMs,
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

    private static class ControlBaseline {
        final int sampleCount;
        final double actualWindowS;
        final boolean valid;
        final boolean activeControlAtAnchor;
        final double pitchMean;
        final double rollMean;
        final double yawMean;
        final double throttleMean;
        final double pitchRange;
        final double rollRange;
        final double yawRange;
        final double throttleRange;

        ControlBaseline(
                int sampleCount,
                double actualWindowS,
                boolean valid,
                boolean activeControlAtAnchor,
                double pitchMean,
                double rollMean,
                double yawMean,
                double throttleMean,
                double pitchRange,
                double rollRange,
                double yawRange,
                double throttleRange
        ) {
            this.sampleCount = sampleCount;
            this.actualWindowS = actualWindowS;
            this.valid = valid;
            this.activeControlAtAnchor = activeControlAtAnchor;
            this.pitchMean = pitchMean;
            this.rollMean = rollMean;
            this.yawMean = yawMean;
            this.throttleMean = throttleMean;
            this.pitchRange = pitchRange;
            this.rollRange = rollRange;
            this.yawRange = yawRange;
            this.throttleRange = throttleRange;
        }

        static ControlBaseline fromSamples(
                Deque<XPlaneStateSample> history,
                int trialId,
                double spawnTimeS
        ) {
            List<XPlaneStateSample> samples = new ArrayList<>();
            for (XPlaneStateSample sample : history) {
                if (sample.trialId == trialId
                        && !Double.isNaN(spawnTimeS)
                        && sample.simTimeS >= spawnTimeS - RESPONSE_BASELINE_WINDOW_S
                        && sample.simTimeS <= spawnTimeS) {
                    samples.add(sample);
                }
            }

            if (samples.isEmpty()) {
                return new ControlBaseline(
                        0, 0.0, false, false,
                        0.0, 0.0, 0.0, 0.0,
                        0.0, 0.0, 0.0, 0.0
                );
            }

            double pitchSum = 0.0;
            double rollSum = 0.0;
            double yawSum = 0.0;
            double throttleSum = 0.0;
            double pitchMin = Double.POSITIVE_INFINITY;
            double pitchMax = Double.NEGATIVE_INFINITY;
            double rollMin = Double.POSITIVE_INFINITY;
            double rollMax = Double.NEGATIVE_INFINITY;
            double yawMin = Double.POSITIVE_INFINITY;
            double yawMax = Double.NEGATIVE_INFINITY;
            double throttleMin = Double.POSITIVE_INFINITY;
            double throttleMax = Double.NEGATIVE_INFINITY;

            for (XPlaneStateSample sample : samples) {
                pitchSum += sample.pitchInput;
                rollSum += sample.rollInput;
                yawSum += sample.yawInput;
                throttleSum += sample.throttleInput;
                pitchMin = Math.min(pitchMin, sample.pitchInput);
                pitchMax = Math.max(pitchMax, sample.pitchInput);
                rollMin = Math.min(rollMin, sample.rollInput);
                rollMax = Math.max(rollMax, sample.rollInput);
                yawMin = Math.min(yawMin, sample.yawInput);
                yawMax = Math.max(yawMax, sample.yawInput);
                throttleMin = Math.min(throttleMin, sample.throttleInput);
                throttleMax = Math.max(throttleMax, sample.throttleInput);
            }

            int sampleCount = samples.size();
            double pitchRange = pitchMax - pitchMin;
            double rollRange = rollMax - rollMin;
            double yawRange = yawMax - yawMin;
            double throttleRange = throttleMax - throttleMin;
            boolean activeControlAtAnchor = pitchRange >= PITCH_RESPONSE_THRESHOLD
                    || rollRange >= ROLL_RESPONSE_THRESHOLD
                    || yawRange >= YAW_RESPONSE_THRESHOLD
                    || throttleRange >= THROTTLE_RESPONSE_THRESHOLD;
            double actualWindowS = samples.get(sampleCount - 1).simTimeS - samples.get(0).simTimeS;

            return new ControlBaseline(
                    sampleCount,
                    actualWindowS,
                    sampleCount >= MIN_RESPONSE_BASELINE_SAMPLES,
                    activeControlAtAnchor,
                    pitchSum / sampleCount,
                    rollSum / sampleCount,
                    yawSum / sampleCount,
                    throttleSum / sampleCount,
                    pitchRange,
                    rollRange,
                    yawRange,
                    throttleRange
            );
        }
    }

    private static class ResponseDelta {
        final boolean triggered;
        final String triggerAxis;
        final double pitchDelta;
        final double rollDelta;
        final double yawDelta;
        final double throttleDelta;

        ResponseDelta(
                boolean triggered,
                String triggerAxis,
                double pitchDelta,
                double rollDelta,
                double yawDelta,
                double throttleDelta
        ) {
            this.triggered = triggered;
            this.triggerAxis = triggerAxis;
            this.pitchDelta = pitchDelta;
            this.rollDelta = rollDelta;
            this.yawDelta = yawDelta;
            this.throttleDelta = throttleDelta;
        }

        static ResponseDelta from(XPlaneStateSample sample, ControlBaseline baseline) {
            double pitchDelta = sample.pitchInput - baseline.pitchMean;
            double rollDelta = sample.rollInput - baseline.rollMean;
            double yawDelta = sample.yawInput - baseline.yawMean;
            double throttleDelta = sample.throttleInput - baseline.throttleMean;

            double pitchScore = Math.abs(pitchDelta) / PITCH_RESPONSE_THRESHOLD;
            double rollScore = Math.abs(rollDelta) / ROLL_RESPONSE_THRESHOLD;
            double yawScore = Math.abs(yawDelta) / YAW_RESPONSE_THRESHOLD;
            double throttleScore = Math.abs(throttleDelta) / THROTTLE_RESPONSE_THRESHOLD;

            String triggerAxis = "pitch";
            double maxScore = pitchScore;
            if (rollScore > maxScore) {
                triggerAxis = "roll";
                maxScore = rollScore;
            }
            if (yawScore > maxScore) {
                triggerAxis = "yaw";
                maxScore = yawScore;
            }
            if (throttleScore > maxScore) {
                triggerAxis = "throttle";
                maxScore = throttleScore;
            }

            return new ResponseDelta(
                    maxScore >= 1.0,
                    triggerAxis,
                    pitchDelta,
                    rollDelta,
                    yawDelta,
                    throttleDelta
            );
        }
    }
}
