package com.example.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class XPlaneRandomSpeedTaskAnalysisSmokeTest {
    private static final String SESSION_ID = "session_random_speed_task_smoke";

    public static void main(String[] args) throws Exception {
        Path statePath = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("build_atc_tmp", "xplane_random_speed_task_smoke.csv");
        Path eventPath = statePath.resolveSibling(
                statePath.getFileName().toString().replace(".csv", "_events.csv")
        );
        Path intruderPath = statePath.resolveSibling(
                statePath.getFileName().toString().replace(".csv", "_intruder.csv")
        );
        Files.createDirectories(statePath.toAbsolutePath().getParent());
        Files.deleteIfExists(statePath);
        Files.deleteIfExists(eventPath);
        Files.deleteIfExists(intruderPath);

        try (XPlaneExperimentLogger logger = new XPlaneExperimentLogger(statePath)) {
            double[] times = {3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 12.0};
            double[] speeds = {95.0, 97.0, 99.0, 100.0, 101.0, 100.0, 20.0};
            for (int i = 0; i < times.length; i++) {
                logger.log(state(i + 1, times[i], speeds[i]));
            }
        }

        Files.writeString(
                intruderPath,
                "received_timestamp_ms,trial_id,sample_index,sim_time_s,intruder_x,intruder_y,intruder_z,intruder_latitude_deg,intruder_longitude_deg,intruder_elevation_m,intruder_vx,intruder_vy,intruder_vz,horizontal_distance,vertical_separation,ownship_x,ownship_y,ownship_z\n"
                        + "1010000,1,1,10.0,0,0,0,37.46,126.44,300,0,0,0,50,5,0,0,0\n"
                        + "1012000,1,2,12.0,0,-3000,0,37.46,126.44,-2700,0,0,0,0,3000,0,0,0\n",
                StandardCharsets.UTF_8
        );

        try (XPlaneEventLogger logger = new XPlaneEventLogger(eventPath)) {
            log(logger, XPlaneEventType.TRIAL_RESET, 0.0, "trial_id=1");
            log(
                    logger,
                    XPlaneEventType.TRIAL_START,
                    0.0,
                    "trial_id=1;protocol=audio_task_v32;target_speed_kias=100"
            );
            log(
                    logger,
                    XPlaneEventType.TASK_COMMAND_AUDIO_START,
                    3.0,
                    "trial_id=1;protocol=audio_task_v32;target_speed_kias=100"
            );
            log(
                    logger,
                    XPlaneEventType.SCENARIO_SELECTED,
                    3.0,
                    "trial_id=1;planned_audio_to_spawn_s=5.000"
            );
            log(
                    logger,
                    XPlaneEventType.INTRUDER_SPAWNED,
                    8.0,
                    "trial_id=1;approach=left;own_latitude_deg=37.47;own_agl_m=251;own_roll_deg=1.6;own_throttle_input=0.55"
            );
            log(
                    logger,
                    XPlaneEventType.INTRUDER_VISUALLY_DETECTABLE,
                    9.0,
                    "trial_id=1;method=perspective_bbox_proxy_v1;operational_proxy=true;threshold_px=20;estimated_span_px=20.2;screen_x_px=4200;screen_y_px=510;slant_distance_m=920;horizontal_distance_m=918;relative_bearing_deg=24;relative_elevation_deg=-1.5;own_ias_kias=101.5"
            );
            log(
                    logger,
                    XPlaneEventType.PILOT_RESPONSE_BASELINE,
                    9.0,
                    "trial_id=1;anchor=INTRUDER_VISUALLY_DETECTABLE;method=pre_detectability_mean_sustained_delta_v2;baseline_valid=true;active_control_at_anchor=false"
            );
            log(logger, XPlaneEventType.TASK_COMMAND_AUDIO_END, 9.6, "trial_id=1");
            log(logger, XPlaneEventType.MIN_DISTANCE_REACHED, 10.0, "trial_id=1");
            log(logger, XPlaneEventType.TRIAL_END, 11.0, "trial_id=1");
        }

        XPlaneSessionAnalysisMain.XPlaneSessionSummary summary =
                XPlaneSessionAnalysisMain.analyze(statePath, intruderPath, eventPath);
        if (summary.trials.size() != 1 || !summary.trials.get(0).clean) {
            throw new IllegalStateException("v32 visual-detectability trial was not accepted as recording-complete");
        }

        XPlaneSessionAnalysisMain.XPlaneTrialSummary trial = summary.trials.get(0);
        assertNear(trial.targetSpeedKias, 100.0, "target speed");
        assertNear(trial.iasAtAudioStartKias, 95.0, "IAS at task audio start");
        assertNear(trial.iasAtSpawnKias, 100.0, "IAS at intruder spawn");
        assertNear(trial.iasAtDetectabilityKias, 101.5, "IAS at visual detectability");
        assertNear(trial.detectabilityTimeS, 9.0, "visual detectability time");
        assertNear(trial.detectabilityThresholdPx, 20.0, "visual detectability threshold");
        assertNear(trial.detectabilityEstimatedSpanPx, 20.2, "visual detectability span");
        assertNear(trial.detectabilitySlantDistanceM, 920.0, "visual detectability slant distance");
        if (!"INTRUDER_VISUALLY_DETECTABLE".equals(trial.responseAnchor)) {
            throw new IllegalStateException(
                    "v32 response anchor was not visual detectability: " + trial.responseAnchor
            );
        }
        if (!Boolean.FALSE.equals(trial.activeControlAtDetectability)) {
            throw new IllegalStateException("v32 active-control-at-detectability field was not parsed");
        }
        assertNear(trial.spawnLatitudeDeg, 37.47, "event latitude at intruder spawn");
        assertNear(trial.spawnLongitudeDeg, 126.44, "longitude at intruder spawn");
        assertNear(trial.spawnElevationM, 300.0, "elevation at intruder spawn");
        assertNear(trial.spawnAglM, 251.0, "event AGL at intruder spawn");
        assertNear(trial.spawnHeadingDeg, 325.0, "heading at intruder spawn");
        assertNear(trial.spawnPitchDeg, -2.0, "pitch at intruder spawn");
        assertNear(trial.spawnRollDeg, 1.6, "event roll at intruder spawn");
        assertNear(trial.spawnVerticalSpeedMps, -3.0, "vertical speed at intruder spawn");
        assertNear(trial.spawnPitchInput, 0.1, "pitch input at intruder spawn");
        assertNear(trial.spawnRollInput, -0.2, "roll input at intruder spawn");
        assertNear(trial.spawnYawInput, 0.03, "yaw input at intruder spawn");
        assertNear(trial.spawnThrottleInput, 0.55, "event throttle at intruder spawn");
        assertNear(trial.preSpawnMeanAbsSpeedErrorKias, 10.0 / 6.0, "pre-spawn speed MAE");
        assertNear(trial.preSpawnWithin5KiasRate, 1.0, "pre-spawn within-5-KIAS rate");
        assertNear(trial.minHorizontalDistance, 50.0, "in-trial minimum horizontal distance");
        assertNear(trial.minVerticalSeparation, 5.0, "vertical separation at in-trial minimum");
        assertNear(summary.minHorizontalDistance, 50.0, "session minimum horizontal distance");
        if (trial.stateSampleCount != 6 || trial.intruderSampleCount != 1) {
            throw new IllegalStateException(
                    "post-trial samples leaked into trial metrics: state="
                            + trial.stateSampleCount
                            + ", intruder="
                            + trial.intruderSampleCount
            );
        }
        if (!Boolean.TRUE.equals(trial.audioSpawnTimingValid)) {
            throw new IllegalStateException("v32 task-audio-to-spawn timing was not accepted");
        }

        verifyV39TargetBandDwellGate(statePath.toAbsolutePath().getParent());

        System.out.println("XPlane random-speed task analysis smoke test: PASS");
        System.out.println("Output: " + statePath.toAbsolutePath());
    }

    private static void verifyV39TargetBandDwellGate(Path outputDirectory) throws Exception {
        Path statePath = outputDirectory.resolve("xplane_target_band_dwell_gate_v39_smoke.csv");
        Path eventPath = outputDirectory.resolve("xplane_target_band_dwell_gate_v39_smoke_events.csv");
        Path intruderPath = outputDirectory.resolve("xplane_target_band_dwell_gate_v39_smoke_intruder.csv");
        Files.deleteIfExists(statePath);
        Files.deleteIfExists(eventPath);
        Files.deleteIfExists(intruderPath);

        try (XPlaneExperimentLogger logger = new XPlaneExperimentLogger(statePath)) {
            logger.log(state(1, 0.0, 104.0));
            logger.log(state(2, 3.0, 103.0));
            logger.log(state(3, 9.0, 101.0));
            logger.log(state(4, 15.0, 100.5));
            logger.log(state(5, 16.0, 100.0));
            logger.log(state(6, 20.0, 99.5));
        }

        Files.writeString(
                intruderPath,
                "received_timestamp_ms,trial_id,sample_index,sim_time_s,intruder_x,intruder_y,intruder_z,intruder_latitude_deg,intruder_longitude_deg,intruder_elevation_m,intruder_vx,intruder_vy,intruder_vz,horizontal_distance,vertical_separation,ownship_x,ownship_y,ownship_z\n"
                        + "1015000,1,4,15.0,100,0,0,37.46,126.44,300,-10,0,0,100,20,0,0,0\n"
                        + "1020000,1,6,20.0,30,0,0,37.46,126.44,300,-10,0,0,30,20,0,0,0\n",
                StandardCharsets.UTF_8
        );

        try (XPlaneEventLogger logger = new XPlaneEventLogger(eventPath)) {
            log(logger, XPlaneEventType.TRIAL_RESET, 0.0, "trial_id=1");
            log(
                    logger,
                    XPlaneEventType.TRIAL_START,
                    0.0,
                    "trial_id=1;protocol=audio_task_v39;target_speed_kias=100"
            );
            log(logger, XPlaneEventType.TASK_COMMAND_AUDIO_START, 3.0, "trial_id=1;protocol=audio_task_v39;target_speed_kias=100");
            log(
                    logger,
                    XPlaneEventType.SCENARIO_SELECTED,
                    3.0,
                    "trial_id=1;spawn_gate_method=bounded_speed_band_dwell_v2;planned_audio_to_spawn_s=7.000;candidate_audio_to_spawn_s=7.000;spawn_timeout_audio_to_spawn_s=14.000"
            );
            log(logger, XPlaneEventType.TASK_COMMAND_AUDIO_END, 9.5, "trial_id=1;protocol=audio_task_v39");
            log(
                    logger,
                    XPlaneEventType.INTRUDER_SPAWNED,
                    15.0,
                    "trial_id=1;profile=visual_opportunity_proxy_v39;approach=front;spawn_gate_method=bounded_speed_band_dwell_v2;speed_rate_diagnostic_only=true;planned_audio_to_spawn_s=7.000;actual_audio_to_spawn_s=12.000;spawn_timeout_audio_to_spawn_s=14.000;speed_stable_before_spawn=true;speed_error_at_spawn_kias=0.500;speed_rate_at_spawn_kias_s=-2.000;speed_rate_valid=true;speed_stability_duration_s=1.700;speed_gate_pass_audio_elapsed_s=12.000;spawn_wait_after_candidate_s=5.000;spawn_forced_by_timeout=false;geometry_calibration=true;light_assisted=false;lights=off;lights_persistent_force=true;ai_autopilot_override_requested=true;ai_autopilot_override_value=1;lights_readback_ok=true;lights_all_off=true;initial_estimated_span_px=8"
            );
            log(
                    logger,
                    XPlaneEventType.INTRUDER_VISUAL_OPPORTUNITY_ONSET,
                    16.0,
                    "trial_id=1;method=geometry_span_operational_proxy_v2;operational_proxy=true;recognition_claim=false;opportunity_basis=projected_span_threshold;light_assisted=false;lights=off;lights_write_ok=true;threshold_px=20;minimum_span_px=20;estimated_span_px=20.1;spawn_to_opportunity_s=1.0"
            );
            log(
                    logger,
                    XPlaneEventType.PILOT_RESPONSE_BASELINE,
                    16.0,
                    "trial_id=1;anchor=INTRUDER_VISUAL_OPPORTUNITY_ONSET;method=pre_visual_opportunity_mean_sustained_delta_v3;baseline_valid=true;active_control_at_anchor=false;baseline_samples=10"
            );
            log(
                    logger,
                    XPlaneEventType.PILOT_RESPONSE_START,
                    17.0,
                    "trial_id=1;anchor=INTRUDER_VISUAL_OPPORTUNITY_ONSET;method=pre_visual_opportunity_mean_sustained_delta_v3;trigger_axis=throttle"
            );
            log(logger, XPlaneEventType.MIN_DISTANCE_REACHED, 20.0, "trial_id=1");
            log(logger, XPlaneEventType.PILOT_RESPONSE_END, 20.5, "trial_id=1");
            log(logger, XPlaneEventType.TRIAL_END, 21.0, "trial_id=1");
        }

        XPlaneSessionAnalysisMain.XPlaneSessionSummary summary =
                XPlaneSessionAnalysisMain.analyze(statePath, intruderPath, eventPath);
        if (summary.trials.size() != 1 || !summary.trials.get(0).clean) {
            throw new IllegalStateException("v39 geometry-opportunity trial was not recording-complete");
        }
        XPlaneSessionAnalysisMain.XPlaneTrialSummary trial = summary.trials.get(0);
        if (!Boolean.TRUE.equals(trial.audioSpawnTimingValid)
                || !Boolean.TRUE.equals(trial.speedStableBeforeSpawn)
                || !Boolean.FALSE.equals(trial.spawnForcedByTimeout)) {
            throw new IllegalStateException("v39 target-band dwell spawn gate metadata was not accepted");
        }
        if (!"bounded_speed_band_dwell_v2".equals(trial.spawnGateMethod)) {
            throw new IllegalStateException("v39 spawn gate method was not parsed: " + trial.spawnGateMethod);
        }
        assertNear(trial.spawnTimeoutAudioToSpawnS, 14.0, "v39 spawn timeout");
        assertNear(trial.speedErrorAtSpawnKias, 0.5, "v39 speed error at spawn");
        assertNear(trial.speedRateAtSpawnKiasS, -2.0, "v39 diagnostic speed rate at spawn");
        assertNear(trial.speedStabilityDurationS, 1.7, "v39 target-band dwell duration");
        assertNear(trial.spawnWaitAfterCandidateS, 5.0, "v39 wait after candidate");

        if (!Boolean.TRUE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v39", 7.0, 3.0, 15.0, 14.0, true, false,
                0.5, -2.0, false, 1.7
        ))) {
            throw new IllegalStateException("v39 diagnostic-only speed rate incorrectly rejected stable spawn");
        }

        if (!Boolean.TRUE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v39", 7.0, 3.0, 17.0, 14.0, false, true,
                8.0, -0.2, true, 0.0
        ))) {
            throw new IllegalStateException("v39 forced-timeout branch was not accepted");
        }
        if (!Boolean.FALSE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v39", 7.0, 3.0, 15.0, 14.0, false, true,
                2.0, -0.2, true, 0.0
        ))) {
            throw new IllegalStateException("v39 early forced-timeout branch was incorrectly accepted");
        }
        if (!Boolean.FALSE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v39", 7.0, 3.0, 15.0, null, true, false,
                0.5, -0.2, true, 1.7
        ))) {
            throw new IllegalStateException("v39 missing timeout metadata was incorrectly accepted");
        }
        if (!Boolean.FALSE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v39", 7.0, 3.0, 15.0, 14.0, true, false,
                5.1, -0.2, true, 1.7
        ))) {
            throw new IllegalStateException("v39 out-of-band stable metadata was incorrectly accepted");
        }
        if (!Boolean.TRUE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v38", 7.0, 3.0, 15.0, 14.0, true, false,
                0.5, -2.0, false, 1.7
        ))) {
            throw new IllegalStateException("v38 backward target-band gate was not preserved");
        }
        if (!Boolean.TRUE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v37", 7.0, 3.0, 15.0, 14.0, true, false,
                0.5, -2.0, false, 1.7
        ))) {
            throw new IllegalStateException("v37 backward target-band gate was not preserved");
        }
        if (!Boolean.TRUE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v36", 7.0, 3.0, 15.0, 14.0, true, false,
                0.5, -2.0, false, 1.7
        ))) {
            throw new IllegalStateException("v36 backward target-band gate was not preserved");
        }
        if (!Boolean.FALSE.equals(XPlaneSessionAnalysisMain.audioSpawnTimingValid(
                "audio_task_v35", 7.0, 3.0, 15.0, 14.0, true, false,
                0.5, -2.0, true, 1.7
        ))) {
            throw new IllegalStateException("v35 backward rate gate was not preserved");
        }
    }

    private static void log(
            XPlaneEventLogger logger,
            XPlaneEventType eventType,
            double simTimeS,
            String detail
    ) {
        logger.log(new XPlaneEventRecord(
                1_000_000L + Math.round(simTimeS * 1000.0),
                SESSION_ID,
                1,
                eventType,
                "smoke_test",
                detail,
                null,
                null,
                null,
                simTimeS
        ));
    }

    private static XPlaneStateSample state(int sampleIndex, double simTimeS, double iasKias) {
        return new XPlaneStateSample(
                1_000_000L + Math.round(simTimeS * 1000.0),
                1,
                sampleIndex,
                simTimeS,
                0.0,
                0.0,
                0.0,
                37.46,
                126.44,
                300.0,
                250.0,
                0.0,
                0.0,
                0.0,
                325.0,
                -2.0,
                1.5,
                0.1,
                0.2,
                0.3,
                iasKias,
                60.0,
                -3.0,
                0.1,
                -0.2,
                0.03,
                0.5
        );
    }

    private static void assertNear(Double actual, double expected, String label) {
        if (actual == null || Math.abs(actual - expected) > 1e-9) {
            throw new IllegalStateException(
                    label + " expected " + expected + " but found " + actual
            );
        }
    }
}
