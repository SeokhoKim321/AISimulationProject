package com.example.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class XPlaneAutoEventDetectorSmokeTest {
    public static void main(String[] args) throws Exception {
        Path outputPath = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("build_atc_tmp", "xplane_spawn_response_smoke_events.csv");
        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        Files.deleteIfExists(outputPath);

        try (XPlaneEventLogger logger = new XPlaneEventLogger(outputPath)) {
            XPlaneAutoEventDetector detector = new XPlaneAutoEventDetector(
                    "session_spawn_response_smoke",
                    logger
            );
            runSustainedResponseTrial(detector, logger);
            runUnstableBaselineWithoutResponseTrial(detector, logger);
            runTrialEndClosureTrial(detector, logger);
            runVisualDetectabilityAnchoredTrial(detector, logger);
            runVisualOpportunityAnchoredTrial(detector, logger);
        }

        Path statePath = outputPath.resolveSibling(
                outputPath.getFileName().toString().replace("_events.csv", ".csv")
        );
        Path intruderPath = outputPath.resolveSibling(
                outputPath.getFileName().toString().replace("_events.csv", "_intruder.csv")
        );
        Files.writeString(
                statePath,
                "received_timestamp_ms,trial_id,sample_index,sim_time_s,x,y,z,latitude_deg,longitude_deg,elevation_m,y_agl_m,vx,vy,vz,heading_deg,pitch_deg,roll_deg,p_rate,q_rate,r_rate,ias_mps,tas_mps,vertical_speed_mps,pitch_input,roll_input,yaw_input,throttle_input\n",
                StandardCharsets.UTF_8
        );
        Files.writeString(
                intruderPath,
                "received_timestamp_ms,trial_id,sample_index,sim_time_s,intruder_x,intruder_y,intruder_z,intruder_latitude_deg,intruder_longitude_deg,intruder_elevation_m,intruder_vx,intruder_vy,intruder_vz,horizontal_distance,vertical_separation,ownship_x,ownship_y,ownship_z\n",
                StandardCharsets.UTF_8
        );

        List<EventRow> rows = readRows(outputPath);
        assertCount(rows, 1, "PILOT_RESPONSE_BASELINE", 1);
        assertCount(rows, 1, "PILOT_RESPONSE_START", 1);
        assertCount(rows, 1, "PILOT_RESPONSE_END", 1);
        EventRow trial1Start = first(rows, 1, "PILOT_RESPONSE_START");
        assertNear(trial1Start.simTimeS, 1.2, 1e-9, "trial 1 response start");
        assertContains(trial1Start.detail, "anchor=INTRUDER_SPAWNED");
        assertContains(trial1Start.detail, "persistence_actual_s=0.300");

        assertCount(rows, 2, "PILOT_RESPONSE_BASELINE", 1);
        assertCount(rows, 2, "PILOT_RESPONSE_START", 0);
        assertCount(rows, 2, "PILOT_RESPONSE_END", 0);
        EventRow trial2Baseline = first(rows, 2, "PILOT_RESPONSE_BASELINE");
        assertContains(trial2Baseline.detail, "baseline_valid=true");
        assertContains(trial2Baseline.detail, "active_control_at_spawn=true");

        assertCount(rows, 3, "PILOT_RESPONSE_BASELINE", 1);
        assertCount(rows, 3, "PILOT_RESPONSE_START", 1);
        assertCount(rows, 3, "PILOT_RESPONSE_END", 1);
        int responseEndIndex = indexOf(rows, 3, "PILOT_RESPONSE_END");
        int trialEndIndex = indexOf(rows, 3, "TRIAL_END");
        if (responseEndIndex < 0 || trialEndIndex < 0 || responseEndIndex >= trialEndIndex) {
            throw new IllegalStateException("Trial 3 response end was not logged before TRIAL_END");
        }
        assertContains(first(rows, 3, "PILOT_RESPONSE_END").detail, "reason=trial_end");

        assertCount(rows, 4, "PILOT_RESPONSE_BASELINE", 1);
        assertCount(rows, 4, "PILOT_RESPONSE_START", 1);
        assertCount(rows, 4, "PILOT_RESPONSE_END", 1);
        EventRow trial4Baseline = first(rows, 4, "PILOT_RESPONSE_BASELINE");
        EventRow trial4Start = first(rows, 4, "PILOT_RESPONSE_START");
        assertContains(trial4Baseline.detail, "anchor=INTRUDER_VISUALLY_DETECTABLE");
        assertContains(trial4Baseline.detail, "active_control_at_anchor=false");
        assertContains(trial4Start.detail, "anchor=INTRUDER_VISUALLY_DETECTABLE");
        assertNear(trial4Start.simTimeS, 12.2, 1e-9, "trial 4 detectability-anchored response start");
        int visualEventIndex = indexOf(rows, 4, "INTRUDER_VISUALLY_DETECTABLE");
        int visualBaselineIndex = indexOf(rows, 4, "PILOT_RESPONSE_BASELINE");
        if (visualEventIndex < 0 || visualBaselineIndex <= visualEventIndex) {
            throw new IllegalStateException("Trial 4 response baseline did not open after visual detectability");
        }

        assertCount(rows, 5, "PILOT_RESPONSE_BASELINE", 1);
        assertCount(rows, 5, "PILOT_RESPONSE_START", 1);
        assertCount(rows, 5, "PILOT_RESPONSE_END", 1);
        EventRow trial5Baseline = first(rows, 5, "PILOT_RESPONSE_BASELINE");
        EventRow trial5Start = first(rows, 5, "PILOT_RESPONSE_START");
        assertContains(trial5Baseline.detail, "anchor=INTRUDER_VISUAL_OPPORTUNITY_ONSET");
        assertContains(trial5Baseline.detail, "active_control_at_visual_opportunity=false");
        assertContains(trial5Start.detail, "method=pre_visual_opportunity_mean_sustained_delta_v3");
        assertNear(trial5Start.simTimeS, 24.1, 1e-9, "trial 5 opportunity-anchored response start");
        int opportunityEventIndex = indexOf(rows, 5, "INTRUDER_VISUAL_OPPORTUNITY_ONSET");
        int opportunityBaselineIndex = indexOf(rows, 5, "PILOT_RESPONSE_BASELINE");
        if (opportunityEventIndex < 0 || opportunityBaselineIndex <= opportunityEventIndex) {
            throw new IllegalStateException("Trial 5 response baseline did not open after visual opportunity");
        }

        XPlaneSessionAnalysisMain.XPlaneSessionSummary summary =
                XPlaneSessionAnalysisMain.analyze(statePath, intruderPath, outputPath);
        if (summary.trials.size() != 5
                || summary.trials.stream().anyMatch(trial -> !trial.clean)) {
            throw new IllegalStateException("Spawn-response recording-complete analysis did not accept all smoke trials");
        }
        XPlaneSessionAnalysisMain.XPlaneTrialSummary trial5Summary = summary.trials.get(4);
        if (!Boolean.TRUE.equals(trial5Summary.visualOpportunityOperationalProxy)
                || !Boolean.FALSE.equals(trial5Summary.visualOpportunityRecognitionClaim)
                || !Boolean.TRUE.equals(trial5Summary.visualOpportunityLightsWriteOk)) {
            throw new IllegalStateException("Trial 5 visual-opportunity metadata was not preserved");
        }
        assertNear(
                trial5Summary.responseStartTimeS - trial5Summary.visualOpportunityTimeS,
                0.1,
                1e-9,
                "trial 5 visual-opportunity-to-response latency"
        );

        System.out.println("XPlaneAutoEventDetector smoke test: PASS");
        System.out.println("Output: " + outputPath.toAbsolutePath());
    }

    private static void runSustainedResponseTrial(
            XPlaneAutoEventDetector detector,
            XPlaneEventLogger logger
    ) {
        deliverEvent(detector, logger, event(1, XPlaneEventType.TRIAL_RESET, 0.0));
        deliverEvent(detector, logger, event(1, XPlaneEventType.TRIAL_START, 0.0));
        deliverEvent(detector, logger, event(1, XPlaneEventType.SCENARIO_SELECTED, 0.1));
        for (int i = 0; i <= 10; i++) {
            detector.onStateSample(state(1, i, i * 0.1, 0.0));
        }
        deliverEvent(detector, logger, event(1, XPlaneEventType.INTRUDER_SPAWNED, 1.1));
        detector.onStateSample(state(1, 12, 1.2, 0.10));
        detector.onStateSample(state(1, 13, 1.3, 0.10));
        detector.onStateSample(state(1, 14, 1.4, 0.10));
        detector.onStateSample(state(1, 15, 1.5, 0.10));
        for (int i = 16; i <= 23; i++) {
            detector.onStateSample(state(1, i, i * 0.1, 0.0));
        }
        deliverEvent(detector, logger, event(1, XPlaneEventType.MIN_DISTANCE_REACHED, 2.3));
        deliverEvent(detector, logger, event(1, XPlaneEventType.TRIAL_END, 2.4));
    }

    private static void runUnstableBaselineWithoutResponseTrial(
            XPlaneAutoEventDetector detector,
            XPlaneEventLogger logger
    ) {
        deliverEvent(detector, logger, event(2, XPlaneEventType.TRIAL_RESET, 3.0));
        deliverEvent(detector, logger, event(2, XPlaneEventType.TRIAL_START, 3.0));
        deliverEvent(detector, logger, event(2, XPlaneEventType.SCENARIO_SELECTED, 3.1));
        for (int i = 0; i <= 10; i++) {
            double pitch = i % 2 == 0 ? 0.0 : 0.10;
            detector.onStateSample(state(2, 100 + i, 3.0 + i * 0.1, pitch));
        }
        deliverEvent(detector, logger, event(2, XPlaneEventType.INTRUDER_SPAWNED, 4.1));
        for (int i = 0; i < 8; i++) {
            detector.onStateSample(state(2, 112 + i, 4.2 + i * 0.1, 0.05));
        }
        deliverEvent(detector, logger, event(2, XPlaneEventType.MIN_DISTANCE_REACHED, 4.9));
        deliverEvent(detector, logger, event(2, XPlaneEventType.TRIAL_END, 5.0));
    }

    private static void runTrialEndClosureTrial(
            XPlaneAutoEventDetector detector,
            XPlaneEventLogger logger
    ) {
        deliverEvent(detector, logger, event(3, XPlaneEventType.TRIAL_RESET, 6.0));
        deliverEvent(detector, logger, event(3, XPlaneEventType.TRIAL_START, 6.0));
        deliverEvent(detector, logger, event(3, XPlaneEventType.SCENARIO_SELECTED, 6.1));
        for (int i = 0; i <= 10; i++) {
            detector.onStateSample(state(3, 200 + i, 6.0 + i * 0.1, 0.0));
        }
        deliverEvent(detector, logger, event(3, XPlaneEventType.INTRUDER_SPAWNED, 7.1));
        detector.onStateSample(state(3, 212, 7.2, 0.10));
        detector.onStateSample(state(3, 213, 7.3, 0.10));
        detector.onStateSample(state(3, 214, 7.4, 0.10));
        detector.onStateSample(state(3, 215, 7.5, 0.10));
        detector.onStateSample(state(3, 216, 7.6, 0.10));
        deliverEvent(detector, logger, event(3, XPlaneEventType.MIN_DISTANCE_REACHED, 7.65));
        deliverEvent(detector, logger, event(3, XPlaneEventType.TRIAL_END, 7.7));
    }

    private static void runVisualDetectabilityAnchoredTrial(
            XPlaneAutoEventDetector detector,
            XPlaneEventLogger logger
    ) {
        deliverEvent(detector, logger, event(4, XPlaneEventType.TRIAL_RESET, 10.0));
        deliverEvent(
                detector,
                logger,
                event(4, XPlaneEventType.TRIAL_START, 10.0, "trial_id=4;protocol=audio_task_v32")
        );
        deliverEvent(detector, logger, event(4, XPlaneEventType.TASK_COMMAND_AUDIO_START, 10.2));
        deliverEvent(
                detector,
                logger,
                event(4, XPlaneEventType.SCENARIO_SELECTED, 10.2, "trial_id=4;planned_audio_to_spawn_s=0.9")
        );
        for (int i = 0; i <= 10; i++) {
            detector.onStateSample(state(4, 300 + i, 10.0 + i * 0.1, 0.0));
        }
        deliverEvent(detector, logger, event(4, XPlaneEventType.TASK_COMMAND_AUDIO_END, 10.9));
        deliverEvent(
                detector,
                logger,
                event(
                        4,
                        XPlaneEventType.INTRUDER_SPAWNED,
                        11.1,
                        "trial_id=4;profile=visual_detectability_proxy_v32;approach=front"
                )
        );
        for (int i = 12; i <= 20; i++) {
            detector.onStateSample(state(4, 300 + i, 10.0 + i * 0.1, 0.0));
        }
        deliverEvent(
                detector,
                logger,
                event(
                        4,
                        XPlaneEventType.INTRUDER_VISUALLY_DETECTABLE,
                        12.1,
                        "trial_id=4;method=perspective_bbox_proxy_v1;threshold_px=20;estimated_span_px=20.1"
                )
        );
        detector.onStateSample(state(4, 322, 12.2, 0.10));
        detector.onStateSample(state(4, 323, 12.3, 0.10));
        detector.onStateSample(state(4, 324, 12.4, 0.10));
        detector.onStateSample(state(4, 325, 12.5, 0.10));
        deliverEvent(detector, logger, event(4, XPlaneEventType.MIN_DISTANCE_REACHED, 13.0));
        deliverEvent(detector, logger, event(4, XPlaneEventType.TRIAL_END, 13.1));
    }

    private static void runVisualOpportunityAnchoredTrial(
            XPlaneAutoEventDetector detector,
            XPlaneEventLogger logger
    ) {
        deliverEvent(detector, logger, event(5, XPlaneEventType.TRIAL_RESET, 20.0));
        deliverEvent(
                detector,
                logger,
                event(5, XPlaneEventType.TRIAL_START, 20.0, "trial_id=5;protocol=audio_task_v39")
        );
        deliverEvent(detector, logger, event(5, XPlaneEventType.TASK_COMMAND_AUDIO_START, 20.2));
        deliverEvent(
                detector,
                logger,
                event(5, XPlaneEventType.SCENARIO_SELECTED, 20.2, "trial_id=5;planned_audio_to_spawn_s=3.0")
        );
        deliverEvent(detector, logger, event(5, XPlaneEventType.TASK_COMMAND_AUDIO_END, 20.9));
        for (int i = 0; i <= 10; i++) {
            detector.onStateSample(state(5, 400 + i, 22.2 + i * 0.1, 0.0));
        }
        deliverEvent(
                detector,
                logger,
                event(
                        5,
                        XPlaneEventType.INTRUDER_SPAWNED,
                        23.2,
                        "trial_id=5;profile=visual_opportunity_proxy_v39;approach=left;light_assisted=false;lights=off;lights_persistent_force=true;ai_autopilot_override_requested=true;ai_autopilot_override_value=1;lights_readback_ok=true;lights_all_off=true"
                )
        );
        detector.onStateSample(state(5, 411, 23.3, 0.0));
        detector.onStateSample(state(5, 412, 23.4, 0.0));
        detector.onStateSample(state(5, 413, 23.5, 0.0));
        detector.onStateSample(state(5, 414, 23.6, 0.0));
        deliverEvent(
                detector,
                logger,
                event(
                        5,
                        XPlaneEventType.INTRUDER_VISUAL_OPPORTUNITY_ONSET,
                        24.0,
                        "trial_id=5;method=geometry_span_operational_proxy_v2;operational_proxy=true;recognition_claim=false;opportunity_basis=projected_span_threshold;light_assisted=false;lights=off;lights_write_ok=true;threshold_px=20;minimum_span_px=20;estimated_span_px=20.1;screen_x_px=1882;screen_y_px=540;slant_distance_m=1100;horizontal_distance_m=1100;relative_bearing_deg=-28;relative_elevation_deg=0;spawn_to_opportunity_s=0.8;own_ias_kias=100"
                )
        );
        deliverEvent(
                detector,
                logger,
                event(
                        5,
                        XPlaneEventType.INTRUDER_VISUALLY_DETECTABLE,
                        24.0,
                        "trial_id=5;method=perspective_bbox_proxy_v1;threshold_px=20;estimated_span_px=20.1"
                )
        );
        for (int i = 0; i < 8; i++) {
            detector.onStateSample(state(5, 420 + i, 24.1 + i * 0.1, 0.10));
        }
        deliverEvent(detector, logger, event(5, XPlaneEventType.MIN_DISTANCE_REACHED, 25.0));
        deliverEvent(detector, logger, event(5, XPlaneEventType.TRIAL_END, 25.1));
    }

    private static void deliverEvent(
            XPlaneAutoEventDetector detector,
            XPlaneEventLogger logger,
            XPlaneEventRecord event
    ) {
        detector.beforeEventRecord(event);
        logger.log(event);
        detector.onEventRecord(event);
    }

    private static XPlaneEventRecord event(
            int trialId,
            XPlaneEventType eventType,
            double simTimeS
    ) {
        return event(trialId, eventType, simTimeS, "trial_id=" + trialId);
    }

    private static XPlaneEventRecord event(
            int trialId,
            XPlaneEventType eventType,
            double simTimeS,
            String detail
    ) {
        return new XPlaneEventRecord(
                1_000_000L + Math.round(simTimeS * 1000.0),
                "session_spawn_response_smoke",
                trialId,
                eventType,
                "smoke_test",
                detail,
                null,
                null,
                null,
                simTimeS
        );
    }

    private static XPlaneStateSample state(
            int trialId,
            int sampleIndex,
            double simTimeS,
            double pitchInput
    ) {
        return new XPlaneStateSample(
                1_000_000L + Math.round(simTimeS * 1000.0),
                trialId,
                sampleIndex,
                simTimeS,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                100.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                70.0,
                36.0,
                0.0,
                pitchInput,
                0.0,
                0.0,
                0.5
        );
    }

    private static List<EventRow> readRows(Path path) throws Exception {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<EventRow> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] values = lines.get(i).split(",", -1);
            rows.add(new EventRow(
                    Integer.parseInt(values[2]),
                    values[3],
                    values[5],
                    Double.parseDouble(values[9])
            ));
        }
        return rows;
    }

    private static void assertCount(
            List<EventRow> rows,
            int trialId,
            String eventType,
            int expected
    ) {
        long actual = rows.stream()
                .filter(row -> row.trialId == trialId && row.eventType.equals(eventType))
                .count();
        if (actual != expected) {
            throw new IllegalStateException(
                    "Expected " + expected + " " + eventType + " rows for trial "
                            + trialId + " but found " + actual
            );
        }
    }

    private static EventRow first(List<EventRow> rows, int trialId, String eventType) {
        return rows.stream()
                .filter(row -> row.trialId == trialId && row.eventType.equals(eventType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing " + eventType + " for trial " + trialId
                ));
    }

    private static int indexOf(List<EventRow> rows, int trialId, String eventType) {
        for (int i = 0; i < rows.size(); i++) {
            EventRow row = rows.get(i);
            if (row.trialId == trialId && row.eventType.equals(eventType)) {
                return i;
            }
        }
        return -1;
    }

    private static void assertNear(double actual, double expected, double tolerance, String label) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new IllegalStateException(
                    label + " expected " + expected + " but found " + actual
            );
        }
    }

    private static void assertContains(String value, String expected) {
        if (!value.contains(expected)) {
            throw new IllegalStateException(
                    "Expected detail to contain '" + expected + "' but found: " + value
            );
        }
    }

    private record EventRow(int trialId, String eventType, String detail, double simTimeS) {
    }
}
