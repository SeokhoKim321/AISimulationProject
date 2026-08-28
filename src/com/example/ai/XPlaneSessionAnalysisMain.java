package com.example.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class XPlaneSessionAnalysisMain {
    private static final List<String> CLEAN_TRIAL_EVENTS = List.of(
            "TRIAL_RESET",
            "TRIAL_START",
            "SCENARIO_SELECTED",
            "INTRUDER_SPAWNED",
            "HAZARD_DETECTED",
            "ADVISORY_SHOWN",
            "PILOT_RESPONSE_START",
            "HAZARD_CLEARED",
            "ADVISORY_CLEARED",
            "PILOT_RESPONSE_END",
            "TRIAL_END"
    );
    private static final List<String> TASK_AUDIO_EVENTS = List.of(
            "TASK_COMMAND_AUDIO_START",
            "TASK_COMMAND_AUDIO_END"
    );
    private static final List<String> SPAWN_RESPONSE_RECORDING_EVENTS = List.of(
            "TRIAL_RESET",
            "TRIAL_START",
            "SCENARIO_SELECTED",
            "INTRUDER_SPAWNED",
            "PILOT_RESPONSE_BASELINE",
            "MIN_DISTANCE_REACHED",
            "TRIAL_END"
    );

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java com.example.ai.XPlaneSessionAnalysisMain <state_csv_path>");
            return;
        }

        Path stateCsv = Paths.get(args[0]).toAbsolutePath();
        String stateFileName = stateCsv.getFileName().toString();
        Path sessionDir = stateCsv.getParent();
        Path intruderCsv = sessionDir.resolve(stateFileName.replace(".csv", "_intruder.csv"));
        Path eventCsv = sessionDir.resolve(stateFileName.replace(".csv", "_events.csv"));

        XPlaneSessionSummary summary = analyze(stateCsv, intruderCsv, eventCsv);
        printSummary(summary, stateCsv, intruderCsv, eventCsv);
    }

    public static XPlaneSessionSummary analyze(Path stateCsv, Path intruderCsv, Path eventCsv) throws IOException {
        List<String[]> eventRows = readCsv(eventCsv);
        CsvTable stateTable = readCsvTable(stateCsv);
        CsvTable intruderTable = readCsvTable(intruderCsv);
        List<String[]> intruderRows = intruderTable.rows;
        List<String[]> stateRows = stateTable.rows;
        StateColumns stateColumns = StateColumns.from(stateTable);
        IntruderColumns intruderColumns = IntruderColumns.from(intruderTable);

        String sessionId = eventRows.isEmpty() ? "unknown_session" : valueAt(eventRows.get(0), 1);

        int hazardDetectedCount = 0;
        int hazardClearedCount = 0;
        int advisoryShownCount = 0;
        int advisoryClearedCount = 0;
        int responseStartCount = 0;
        int responseEndCount = 0;

        Double firstHazardTime = null;
        Double firstHazardClearTime = null;
        Double firstAdvisoryTime = null;
        Double firstAdvisoryClearTime = null;
        Double firstResponseStartTime = null;
        Double firstResponseEndTime = null;

        for (String[] row : eventRows) {
            String eventType = valueAt(row, 3);
            Double simTime = parseNullableDouble(valueAt(row, 9));

            switch (eventType) {
                case "HAZARD_DETECTED":
                    hazardDetectedCount++;
                    if (firstHazardTime == null) {
                        firstHazardTime = simTime;
                    }
                    break;
                case "HAZARD_CLEARED":
                    hazardClearedCount++;
                    if (firstHazardClearTime == null) {
                        firstHazardClearTime = simTime;
                    }
                    break;
                case "ADVISORY_SHOWN":
                    advisoryShownCount++;
                    if (firstAdvisoryTime == null) {
                        firstAdvisoryTime = simTime;
                    }
                    break;
                case "ADVISORY_CLEARED":
                    advisoryClearedCount++;
                    if (firstAdvisoryClearTime == null) {
                        firstAdvisoryClearTime = simTime;
                    }
                    break;
                case "PILOT_RESPONSE_START":
                    responseStartCount++;
                    if (firstResponseStartTime == null) {
                        firstResponseStartTime = simTime;
                    }
                    break;
                case "PILOT_RESPONSE_END":
                    responseEndCount++;
                    if (firstResponseEndTime == null) {
                        firstResponseEndTime = simTime;
                    }
                    break;
                default:
                    break;
            }
        }

        List<XPlaneTrialSummary> trials = analyzeTrials(
                stateRows,
                intruderRows,
                eventRows,
                stateColumns,
                intruderColumns
        );
        double minHorizontalDistance = Double.NaN;
        double minVerticalSeparation = Double.NaN;
        Double minDistanceSimTime = null;
        for (XPlaneTrialSummary trial : trials) {
            if (trial.minHorizontalDistance == null || Double.isNaN(trial.minHorizontalDistance)) {
                continue;
            }
            if (Double.isNaN(minHorizontalDistance) || trial.minHorizontalDistance < minHorizontalDistance) {
                minHorizontalDistance = trial.minHorizontalDistance;
                minVerticalSeparation = trial.minVerticalSeparation == null
                        ? Double.NaN
                        : trial.minVerticalSeparation;
                minDistanceSimTime = trial.minDistanceSimTimeS;
            }
        }

        Double stateStartTime = stateRows.isEmpty() ? null : parseNullableDouble(valueAt(stateRows.get(0), 3));
        Double stateEndTime = stateRows.isEmpty() ? null : parseNullableDouble(valueAt(stateRows.get(stateRows.size() - 1), 3));

        return new XPlaneSessionSummary(
                sessionId,
                stateRows.size(),
                intruderRows.size(),
                stateStartTime,
                stateEndTime,
                hazardDetectedCount,
                hazardClearedCount,
                advisoryShownCount,
                advisoryClearedCount,
                responseStartCount,
                responseEndCount,
                firstHazardTime,
                firstHazardClearTime,
                firstAdvisoryTime,
                firstAdvisoryClearTime,
                firstResponseStartTime,
                firstResponseEndTime,
                minHorizontalDistance,
                minVerticalSeparation,
                minDistanceSimTime,
                trials
        );
    }

    private static List<XPlaneTrialSummary> analyzeTrials(
            List<String[]> stateRows,
            List<String[]> intruderRows,
            List<String[]> eventRows,
            StateColumns stateColumns,
            IntruderColumns intruderColumns
    ) {
        Map<Integer, List<String[]>> statesByTrial = groupByTrialId(stateRows, 1);
        Map<Integer, List<String[]>> intrudersByTrial = groupByTrialId(intruderRows, 1);
        Map<Integer, List<String[]>> eventsByTrial = groupByTrialId(eventRows, 2);

        TreeMap<Integer, Boolean> trialIds = new TreeMap<>();
        for (Integer trialId : statesByTrial.keySet()) {
            trialIds.put(trialId, true);
        }
        for (Integer trialId : intrudersByTrial.keySet()) {
            trialIds.put(trialId, true);
        }
        for (Integer trialId : eventsByTrial.keySet()) {
            trialIds.put(trialId, true);
        }

        List<XPlaneTrialSummary> trials = new ArrayList<>();
        for (Integer trialId : trialIds.keySet()) {
            if (trialId == 0) {
                continue;
            }

            List<String[]> trialEvents = eventsByTrial.getOrDefault(trialId, List.of());
            Map<String, Integer> eventCounts = eventCounts(trialEvents);
            Double trialStartTimeS = firstEventTime(trialEvents, "TRIAL_START");
            Double trialEndTimeS = firstEventTime(trialEvents, "TRIAL_END");
            List<String[]> trialStates = rowsWithinTimeWindow(
                    statesByTrial.getOrDefault(trialId, List.of()),
                    stateColumns.simTimeIndex,
                    trialStartTimeS,
                    trialEndTimeS
            );
            List<String[]> trialIntruders = rowsWithinTimeWindow(
                    intrudersByTrial.getOrDefault(trialId, List.of()),
                    intruderColumns.simTimeIndex,
                    trialStartTimeS,
                    trialEndTimeS
            );

            double minHorizontalDistance = Double.NaN;
            double minVerticalSeparation = Double.NaN;
            Double minDistanceSimTime = null;
            for (String[] row : trialIntruders) {
                Double horizontalDistance = parseNullableDouble(valueAt(row, intruderColumns.horizontalDistanceIndex));
                Double verticalSeparation = parseNullableDouble(valueAt(row, intruderColumns.verticalSeparationIndex));
                Double simTime = parseNullableDouble(valueAt(row, intruderColumns.simTimeIndex));
                if (horizontalDistance == null || verticalSeparation == null) {
                    continue;
                }

                if (Double.isNaN(minHorizontalDistance) || horizontalDistance < minHorizontalDistance) {
                    minHorizontalDistance = horizontalDistance;
                    minVerticalSeparation = verticalSeparation;
                    minDistanceSimTime = simTime;
                }
            }

            String spawnDetail = firstEventDetail(trialEvents, "INTRUDER_SPAWNED");
            String visualOpportunityDetail = firstEventDetail(
                    trialEvents,
                    "INTRUDER_VISUAL_OPPORTUNITY_ONSET"
            );
            String detectabilityDetail = firstEventDetail(trialEvents, "INTRUDER_VISUALLY_DETECTABLE");
            String scenarioDetail = firstEventDetail(trialEvents, "SCENARIO_SELECTED");
            String trialStartDetail = firstEventDetail(trialEvents, "TRIAL_START");
            String taskAudioStartDetail = firstEventDetail(trialEvents, "TASK_COMMAND_AUDIO_START");
            String responseBaselineDetail = firstEventDetail(trialEvents, "PILOT_RESPONSE_BASELINE");
            String responseStartDetail = firstEventDetail(trialEvents, "PILOT_RESPONSE_START");
            Integer advisoryStateSampleIndex = firstEventStateSampleIndex(trialEvents, "ADVISORY_SHOWN");
            Integer responseStartStateSampleIndex = firstEventStateSampleIndex(trialEvents, "PILOT_RESPONSE_START");
            Double taskAudioStartTimeS = firstEventTime(trialEvents, "TASK_COMMAND_AUDIO_START");
            Double taskAudioEndTimeS = firstEventTime(trialEvents, "TASK_COMMAND_AUDIO_END");
            Double spawnTimeS = firstEventTime(trialEvents, "INTRUDER_SPAWNED");
            Double visualOpportunityTimeS = firstEventTime(
                    trialEvents,
                    "INTRUDER_VISUAL_OPPORTUNITY_ONSET"
            );
            Double detectabilityTimeS = firstEventTime(trialEvents, "INTRUDER_VISUALLY_DETECTABLE");
            StateSnapshot spawnState = nearestStateSnapshotAtTime(
                    trialStates,
                    spawnTimeS,
                    stateColumns
            );
            Double plannedAudioToSpawnS = parseNullableDouble(
                    valueFromDetail(scenarioDetail, "planned_audio_to_spawn_s")
            );
            String spawnGateMethod = valueFromDetail(spawnDetail, "spawn_gate_method");
            if (spawnGateMethod.isBlank()) {
                spawnGateMethod = valueFromDetail(scenarioDetail, "spawn_gate_method");
            }
            Double spawnTimeoutAudioToSpawnS = detailValueOrFallback(
                    spawnDetail,
                    "spawn_timeout_audio_to_spawn_s",
                    parseNullableDouble(valueFromDetail(scenarioDetail, "spawn_timeout_audio_to_spawn_s"))
            );
            Boolean speedStableBeforeSpawn = parseNullableBoolean(
                    valueFromDetail(spawnDetail, "speed_stable_before_spawn")
            );
            Double speedErrorAtSpawnKias = parseNullableDouble(
                    valueFromDetail(spawnDetail, "speed_error_at_spawn_kias")
            );
            Double speedRateAtSpawnKiasS = parseNullableDouble(
                    valueFromDetail(spawnDetail, "speed_rate_at_spawn_kias_s")
            );
            Boolean speedRateValidAtSpawn = parseNullableBoolean(
                    valueFromDetail(spawnDetail, "speed_rate_valid")
            );
            Double speedStabilityDurationS = parseNullableDouble(
                    valueFromDetail(spawnDetail, "speed_stability_duration_s")
            );
            Double speedGatePassAudioElapsedS = parseNullableDouble(
                    valueFromDetail(spawnDetail, "speed_gate_pass_audio_elapsed_s")
            );
            Double spawnWaitAfterCandidateS = parseNullableDouble(
                    valueFromDetail(spawnDetail, "spawn_wait_after_candidate_s")
            );
            Boolean spawnForcedByTimeout = parseNullableBoolean(
                    valueFromDetail(spawnDetail, "spawn_forced_by_timeout")
            );
            Boolean audioSpawnTimingValid = audioSpawnTimingValid(
                    valueFromDetail(trialStartDetail, "protocol"),
                    plannedAudioToSpawnS,
                    taskAudioStartTimeS,
                    spawnTimeS,
                    spawnTimeoutAudioToSpawnS,
                    speedStableBeforeSpawn,
                    spawnForcedByTimeout,
                    speedErrorAtSpawnKias,
                    speedRateAtSpawnKiasS,
                    speedRateValidAtSpawn,
                    speedStabilityDurationS
            );
            String targetSpeedValue = valueFromDetail(trialStartDetail, "target_speed_kias");
            if (targetSpeedValue.isBlank()) {
                targetSpeedValue = valueFromDetail(taskAudioStartDetail, "target_speed_kias");
            }
            Double targetSpeedKias = parseNullableDouble(targetSpeedValue);
            Double iasAtAudioStartKias = nearestStateValueAtTime(
                    trialStates,
                    taskAudioStartTimeS,
                    stateColumns.iasIndex
            );
            Double iasAtSpawnKias = nearestStateValueAtTime(
                    trialStates,
                    spawnTimeS,
                    stateColumns.iasIndex
            );
            iasAtSpawnKias = detailValueOrFallback(
                    spawnDetail,
                    "own_ias_kias",
                    iasAtSpawnKias
            );
            Double iasAtVisualOpportunityKias = detailValueOrFallback(
                    visualOpportunityDetail,
                    "own_ias_kias",
                    nearestStateValueAtTime(
                            trialStates,
                            visualOpportunityTimeS,
                            stateColumns.iasIndex
                    )
            );
            String visualOpportunityMethod = valueFromDetail(visualOpportunityDetail, "method");
            Boolean visualOpportunityOperationalProxy = parseNullableBoolean(
                    valueFromDetail(visualOpportunityDetail, "operational_proxy")
            );
            Boolean visualOpportunityRecognitionClaim = parseNullableBoolean(
                    valueFromDetail(visualOpportunityDetail, "recognition_claim")
            );
            Boolean visualOpportunityLightsWriteOk = parseNullableBoolean(
                    valueFromDetail(visualOpportunityDetail, "lights_write_ok")
            );
            Double visualOpportunityMinimumSpanPx = parseNullableDouble(
                    valueFromDetail(visualOpportunityDetail, "minimum_span_px")
            );
            Double visualOpportunityEstimatedSpanPx = parseNullableDouble(
                    valueFromDetail(visualOpportunityDetail, "estimated_span_px")
            );
            Double visualOpportunityScreenXPx = parseNullableDouble(
                    valueFromDetail(visualOpportunityDetail, "screen_x_px")
            );
            Double visualOpportunityScreenYPx = parseNullableDouble(
                    valueFromDetail(visualOpportunityDetail, "screen_y_px")
            );
            Double visualOpportunitySlantDistanceM = parseNullableDouble(
                    valueFromDetail(visualOpportunityDetail, "slant_distance_m")
            );
            Double visualOpportunityHorizontalDistanceM = parseNullableDouble(
                    valueFromDetail(visualOpportunityDetail, "horizontal_distance_m")
            );
            Double visualOpportunityRelativeBearingDeg = parseNullableDouble(
                    valueFromDetail(visualOpportunityDetail, "relative_bearing_deg")
            );
            Double visualOpportunityRelativeElevationDeg = parseNullableDouble(
                    valueFromDetail(visualOpportunityDetail, "relative_elevation_deg")
            );
            Double iasAtDetectabilityKias = detailValueOrFallback(
                    detectabilityDetail,
                    "own_ias_kias",
                    nearestStateValueAtTime(
                            trialStates,
                            detectabilityTimeS,
                            stateColumns.iasIndex
                    )
            );
            String detectabilityMethod = valueFromDetail(detectabilityDetail, "method");
            Boolean detectabilityOperationalProxy = parseNullableBoolean(
                    valueFromDetail(detectabilityDetail, "operational_proxy")
            );
            Double detectabilityThresholdPx = parseNullableDouble(
                    valueFromDetail(detectabilityDetail, "threshold_px")
            );
            Double detectabilityEstimatedSpanPx = parseNullableDouble(
                    valueFromDetail(detectabilityDetail, "estimated_span_px")
            );
            Double detectabilityScreenXPx = parseNullableDouble(
                    valueFromDetail(detectabilityDetail, "screen_x_px")
            );
            Double detectabilityScreenYPx = parseNullableDouble(
                    valueFromDetail(detectabilityDetail, "screen_y_px")
            );
            Double detectabilitySlantDistanceM = parseNullableDouble(
                    valueFromDetail(detectabilityDetail, "slant_distance_m")
            );
            Double detectabilityHorizontalDistanceM = parseNullableDouble(
                    valueFromDetail(detectabilityDetail, "horizontal_distance_m")
            );
            Double detectabilityRelativeBearingDeg = parseNullableDouble(
                    valueFromDetail(detectabilityDetail, "relative_bearing_deg")
            );
            Double detectabilityRelativeElevationDeg = parseNullableDouble(
                    valueFromDetail(detectabilityDetail, "relative_elevation_deg")
            );
            Double spawnLatitudeDeg = detailValueOrFallback(
                    spawnDetail,
                    "own_latitude_deg",
                    spawnState == null ? null : spawnState.latitudeDeg
            );
            Double spawnLongitudeDeg = detailValueOrFallback(
                    spawnDetail,
                    "own_longitude_deg",
                    spawnState == null ? null : spawnState.longitudeDeg
            );
            Double spawnElevationM = detailValueOrFallback(
                    spawnDetail,
                    "own_elevation_m",
                    spawnState == null ? null : spawnState.elevationM
            );
            Double spawnAglM = detailValueOrFallback(
                    spawnDetail,
                    "own_agl_m",
                    spawnState == null ? null : spawnState.aglM
            );
            Double spawnHeadingDeg = detailValueOrFallback(
                    spawnDetail,
                    "own_heading_deg",
                    spawnState == null ? null : spawnState.headingDeg
            );
            Double spawnPitchDeg = detailValueOrFallback(
                    spawnDetail,
                    "own_pitch_deg",
                    spawnState == null ? null : spawnState.pitchDeg
            );
            Double spawnRollDeg = detailValueOrFallback(
                    spawnDetail,
                    "own_roll_deg",
                    spawnState == null ? null : spawnState.rollDeg
            );
            Double spawnPRate = detailValueOrFallback(
                    spawnDetail,
                    "own_p_rate",
                    spawnState == null ? null : spawnState.pRate
            );
            Double spawnQRate = detailValueOrFallback(
                    spawnDetail,
                    "own_q_rate",
                    spawnState == null ? null : spawnState.qRate
            );
            Double spawnRRate = detailValueOrFallback(
                    spawnDetail,
                    "own_r_rate",
                    spawnState == null ? null : spawnState.rRate
            );
            Double spawnTasMps = detailValueOrFallback(
                    spawnDetail,
                    "own_tas_mps",
                    spawnState == null ? null : spawnState.tasMps
            );
            Double spawnVerticalSpeedMps = detailValueOrFallback(
                    spawnDetail,
                    "own_vertical_speed_mps",
                    spawnState == null ? null : spawnState.verticalSpeedMps
            );
            Double spawnPitchInput = detailValueOrFallback(
                    spawnDetail,
                    "own_pitch_input",
                    spawnState == null ? null : spawnState.pitchInput
            );
            Double spawnRollInput = detailValueOrFallback(
                    spawnDetail,
                    "own_roll_input",
                    spawnState == null ? null : spawnState.rollInput
            );
            Double spawnYawInput = detailValueOrFallback(
                    spawnDetail,
                    "own_yaw_input",
                    spawnState == null ? null : spawnState.yawInput
            );
            Double spawnThrottleInput = detailValueOrFallback(
                    spawnDetail,
                    "own_throttle_input",
                    spawnState == null ? null : spawnState.throttleInput
            );
            Double preSpawnMeanAbsSpeedErrorKias = meanAbsoluteErrorInWindow(
                    trialStates,
                    stateColumns.iasIndex,
                    targetSpeedKias,
                    taskAudioStartTimeS,
                    spawnTimeS
            );
            Double preSpawnWithin5KiasRate = withinToleranceRateInWindow(
                    trialStates,
                    stateColumns.iasIndex,
                    targetSpeedKias,
                    5.0,
                    taskAudioStartTimeS,
                    spawnTimeS
            );
            Double soundMasterRatio = parseNullableDouble(valueFromDetail(trialStartDetail, "sound_master"));
            Double soundInteriorRatio = parseNullableDouble(valueFromDetail(trialStartDetail, "sound_interior"));
            Double soundEngineRatio = parseNullableDouble(valueFromDetail(trialStartDetail, "sound_engine"));
            Double soundPropRatio = parseNullableDouble(valueFromDetail(trialStartDetail, "sound_prop"));
            Double soundEnviroRatio = parseNullableDouble(valueFromDetail(trialStartDetail, "sound_enviro"));
            Double soundRadioRatio = parseNullableDouble(valueFromDetail(trialStartDetail, "sound_radio"));
            String responseAnchor = valueFromDetail(responseStartDetail, "anchor");
            if (responseAnchor.isBlank()) {
                responseAnchor = valueFromDetail(responseBaselineDetail, "anchor");
            }
            String responseMethod = valueFromDetail(responseStartDetail, "method");
            if (responseMethod.isBlank()) {
                responseMethod = valueFromDetail(responseBaselineDetail, "method");
            }
            String responseTriggerAxis = valueFromDetail(responseStartDetail, "trigger_axis");
            Boolean responseBaselineValid = parseNullableBoolean(
                    valueFromDetail(responseBaselineDetail, "baseline_valid")
            );
            Boolean activeControlAtSpawn = parseNullableBoolean(
                    valueFromDetail(responseBaselineDetail, "active_control_at_spawn")
            );
            Boolean activeControlAtAnchor = parseNullableBoolean(
                    valueFromDetail(responseBaselineDetail, "active_control_at_anchor")
            );
            if (activeControlAtSpawn == null
                    && "INTRUDER_SPAWNED".equals(responseAnchor)) {
                activeControlAtSpawn = activeControlAtAnchor;
            }
            Boolean activeControlAtDetectability =
                    "INTRUDER_VISUALLY_DETECTABLE".equals(responseAnchor)
                            ? activeControlAtAnchor
                            : null;
            Boolean activeControlAtVisualOpportunity =
                    "INTRUDER_VISUAL_OPPORTUNITY_ONSET".equals(responseAnchor)
                            ? activeControlAtAnchor
                            : null;
            Integer responseBaselineSamples = parseNullableInteger(
                    valueFromDetail(responseBaselineDetail, "baseline_samples")
            );
            Double responseBaselineWindowS = parseNullableDouble(
                    valueFromDetail(responseBaselineDetail, "baseline_window_actual_s")
            );
            Double responsePersistenceS = parseNullableDouble(
                    valueFromDetail(responseStartDetail, "persistence_actual_s")
            );

            String incompleteReason = strictIncompleteReason(trialEvents, eventCounts);
            boolean clean = incompleteReason.isBlank();
            trials.add(new XPlaneTrialSummary(
                    trialId,
                    clean,
                    incompleteReason,
                    eventCountsToString(eventCounts),
                    trialStates.size(),
                    trialIntruders.size(),
                    valueFromDetail(spawnDetail, "approach"),
                    firstEventTime(trialEvents, "TRIAL_RESET"),
                    trialStartTimeS,
                    taskAudioStartTimeS,
                    taskAudioEndTimeS,
                    spawnTimeS,
                    visualOpportunityTimeS,
                    detectabilityTimeS,
                    plannedAudioToSpawnS,
                    audioSpawnTimingValid,
                    spawnGateMethod,
                    spawnTimeoutAudioToSpawnS,
                    speedStableBeforeSpawn,
                    speedErrorAtSpawnKias,
                    speedRateAtSpawnKiasS,
                    speedRateValidAtSpawn,
                    speedStabilityDurationS,
                    speedGatePassAudioElapsedS,
                    spawnWaitAfterCandidateS,
                    spawnForcedByTimeout,
                    targetSpeedKias,
                    iasAtAudioStartKias,
                    iasAtSpawnKias,
                    iasAtVisualOpportunityKias,
                    visualOpportunityMethod,
                    visualOpportunityOperationalProxy,
                    visualOpportunityRecognitionClaim,
                    visualOpportunityLightsWriteOk,
                    visualOpportunityMinimumSpanPx,
                    visualOpportunityEstimatedSpanPx,
                    visualOpportunityScreenXPx,
                    visualOpportunityScreenYPx,
                    visualOpportunitySlantDistanceM,
                    visualOpportunityHorizontalDistanceM,
                    visualOpportunityRelativeBearingDeg,
                    visualOpportunityRelativeElevationDeg,
                    iasAtDetectabilityKias,
                    detectabilityMethod,
                    detectabilityOperationalProxy,
                    detectabilityThresholdPx,
                    detectabilityEstimatedSpanPx,
                    detectabilityScreenXPx,
                    detectabilityScreenYPx,
                    detectabilitySlantDistanceM,
                    detectabilityHorizontalDistanceM,
                    detectabilityRelativeBearingDeg,
                    detectabilityRelativeElevationDeg,
                    spawnLatitudeDeg,
                    spawnLongitudeDeg,
                    spawnElevationM,
                    spawnAglM,
                    spawnHeadingDeg,
                    spawnPitchDeg,
                    spawnRollDeg,
                    spawnPRate,
                    spawnQRate,
                    spawnRRate,
                    spawnTasMps,
                    spawnVerticalSpeedMps,
                    spawnPitchInput,
                    spawnRollInput,
                    spawnYawInput,
                    spawnThrottleInput,
                    preSpawnMeanAbsSpeedErrorKias,
                    preSpawnWithin5KiasRate,
                    soundMasterRatio,
                    soundInteriorRatio,
                    soundEngineRatio,
                    soundPropRatio,
                    soundEnviroRatio,
                    soundRadioRatio,
                    responseAnchor,
                    responseMethod,
                    responseTriggerAxis,
                    responseBaselineValid,
                    activeControlAtSpawn,
                    activeControlAtAnchor,
                    activeControlAtDetectability,
                    activeControlAtVisualOpportunity,
                    responseBaselineSamples,
                    responseBaselineWindowS,
                    responsePersistenceS,
                    firstEventTime(trialEvents, "HAZARD_DETECTED"),
                    firstEventTime(trialEvents, "ADVISORY_SHOWN"),
                    firstEventTime(trialEvents, "PILOT_RESPONSE_START"),
                    firstEventTime(trialEvents, "HAZARD_CLEARED"),
                    firstEventTime(trialEvents, "ADVISORY_CLEARED"),
                    firstEventTime(trialEvents, "PILOT_RESPONSE_END"),
                    trialEndTimeS,
                    minHorizontalDistance,
                    minVerticalSeparation,
                    minDistanceSimTime,
                    maxAbs(trialStates, stateColumns.pitchInputIndex),
                    maxAbs(trialStates, stateColumns.rollInputIndex),
                    maxAbs(trialStates, stateColumns.yawInputIndex),
                    maxAbs(trialStates, stateColumns.throttleInputIndex),
                    firstControlAxisAtResponse(trialStates, advisoryStateSampleIndex, responseStartStateSampleIndex)
            ));
        }

        return trials;
    }

    private static List<String[]> readCsv(Path path) throws IOException {
        return readCsvTable(path).rows;
    }

    private static CsvTable readCsvTable(Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(path)) {
            return new CsvTable(new String[0], rows);
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        String[] header = new String[0];
        if (!lines.isEmpty()) {
            header = parseCsvLine(lines.get(0).trim());
        }
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                rows.add(parseCsvLine(line));
            }
        }
        return new CsvTable(header, rows);
    }

    private static String[] parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        values.add(current.toString());
        return values.toArray(new String[0]);
    }

    private static void printSummary(
            XPlaneSessionSummary summary,
            Path stateCsv,
            Path intruderCsv,
            Path eventCsv
    ) {
        System.out.println("Session analysis");
        System.out.println("Session ID       : " + summary.sessionId);
        System.out.println("State CSV        : " + stateCsv);
        System.out.println("Intruder CSV     : " + intruderCsv);
        System.out.println("Event CSV        : " + eventCsv);
        System.out.println("State samples    : " + summary.stateSampleCount);
        System.out.println("Intruder samples : " + summary.intruderSampleCount);
        System.out.println("State time range : " + formatNullable(summary.stateStartTimeS) + " -> " + formatNullable(summary.stateEndTimeS));
        System.out.println("Hazard count     : detect=" + summary.hazardDetectedCount + ", clear=" + summary.hazardClearedCount);
        System.out.println("Advisory count   : shown=" + summary.advisoryShownCount + ", cleared=" + summary.advisoryClearedCount);
        System.out.println("Response count   : start=" + summary.responseStartCount + ", end=" + summary.responseEndCount);
        System.out.println("Min horiz dist   : " + formatNullable(summary.minHorizontalDistance) + " at " + formatNullable(summary.minDistanceSimTimeS));
        System.out.println("Min vert sep     : " + formatNullable(summary.minVerticalSeparation));

        if (summary.firstHazardTimeS != null && summary.firstHazardClearTimeS != null) {
            System.out.println("Hazard window    : " + formatNullable(summary.firstHazardTimeS) + " -> " + formatNullable(summary.firstHazardClearTimeS)
                    + " (" + formatNullable(summary.firstHazardClearTimeS - summary.firstHazardTimeS) + " s)");
        }
        if (summary.firstAdvisoryTimeS != null && summary.firstResponseStartTimeS != null) {
            System.out.println("Advisory->Response Start : " + formatOrderedDelta(summary.firstAdvisoryTimeS, summary.firstResponseStartTimeS));
        }
        if (summary.firstHazardTimeS != null && summary.firstResponseStartTimeS != null) {
            System.out.println("Hazard->Response Start   : " + formatOrderedDelta(summary.firstHazardTimeS, summary.firstResponseStartTimeS));
        }
        if (summary.firstHazardTimeS != null && summary.firstAdvisoryTimeS != null) {
            System.out.println("Hazard->Advisory         : " + formatOrderedDelta(summary.firstHazardTimeS, summary.firstAdvisoryTimeS));
        }

        printTrialSummary(summary.trials);
    }

    private static void printTrialSummary(List<XPlaneTrialSummary> trials) {
        long cleanCount = trials.stream().filter(trial -> trial.clean).count();

        System.out.println();
        System.out.println("Trial analysis");
        System.out.println("Trials           : total=" + trials.size() + ", clean=" + cleanCount + ", incomplete=" + (trials.size() - cleanCount));

        for (XPlaneTrialSummary trial : trials) {
            System.out.println();
            System.out.println("Trial " + trial.trialId + " [" + (trial.clean ? "CLEAN" : "INCOMPLETE") + "]");
            if (!trial.clean) {
                System.out.println("Reason           : " + trial.incompleteReason);
            }
            System.out.println("Approach         : " + blankToUnknown(trial.approach));
            System.out.println("Event counts     : " + trial.eventCounts);
            System.out.println("Samples          : state=" + trial.stateSampleCount + ", intruder=" + trial.intruderSampleCount);
            System.out.println("Timeline         : reset=" + formatNullable(trial.resetTimeS)
                    + ", start=" + formatNullable(trial.startTimeS)
                    + ", audio_start=" + formatNullable(trial.taskAudioStartTimeS)
                    + ", audio_end=" + formatNullable(trial.taskAudioEndTimeS)
                    + ", spawn=" + formatNullable(trial.spawnTimeS)
                    + ", visual_opportunity=" + formatNullable(trial.visualOpportunityTimeS)
                    + ", detectable=" + formatNullable(trial.detectabilityTimeS)
                    + ", hazard=" + formatNullable(trial.hazardTimeS)
                    + ", advisory=" + formatNullable(trial.advisoryTimeS)
                    + ", response=" + formatNullable(trial.responseStartTimeS)
                    + ", trial_end=" + formatNullable(trial.trialEndTimeS));
            System.out.println("Min distance     : horiz=" + formatNullable(trial.minHorizontalDistance)
                    + ", vert=" + formatNullable(trial.minVerticalSeparation)
                    + ", at=" + formatNullable(trial.minDistanceSimTimeS));
            System.out.println("Sound ratios     : master=" + formatNullable(trial.soundMasterRatio)
                    + ", interior=" + formatNullable(trial.soundInteriorRatio)
                    + ", engine=" + formatNullable(trial.soundEngineRatio)
                    + ", prop=" + formatNullable(trial.soundPropRatio)
                    + ", enviro=" + formatNullable(trial.soundEnviroRatio)
                    + ", radio=" + formatNullable(trial.soundRadioRatio));
            System.out.println("Speed task       : target=" + formatNullable(trial.targetSpeedKias)
                    + " KIAS, at_audio=" + formatNullable(trial.iasAtAudioStartKias)
                    + ", at_spawn=" + formatNullable(trial.iasAtSpawnKias)
                    + ", at_visual_opportunity=" + formatNullable(trial.iasAtVisualOpportunityKias)
                    + ", at_detectable=" + formatNullable(trial.iasAtDetectabilityKias)
                    + ", pre_spawn_mae=" + formatNullable(trial.preSpawnMeanAbsSpeedErrorKias)
                    + ", within_5kt_rate=" + formatNullable(trial.preSpawnWithin5KiasRate));
            System.out.println("Speed spawn gate : method=" + blankToUnknown(trial.spawnGateMethod)
                    + ", stable=" + (trial.speedStableBeforeSpawn == null ? "n/a" : trial.speedStableBeforeSpawn)
                    + ", forced=" + (trial.spawnForcedByTimeout == null ? "n/a" : trial.spawnForcedByTimeout)
                    + ", timeout_s=" + formatNullable(trial.spawnTimeoutAudioToSpawnS)
                    + ", error_kias=" + formatNullable(trial.speedErrorAtSpawnKias)
                    + ", rate_kias_s=" + formatNullable(trial.speedRateAtSpawnKiasS)
                    + ", rate_valid=" + (trial.speedRateValidAtSpawn == null ? "n/a" : trial.speedRateValidAtSpawn)
                    + ", stable_duration_s=" + formatNullable(trial.speedStabilityDurationS)
                    + ", gate_pass_audio_s=" + formatNullable(trial.speedGatePassAudioElapsedS)
                    + ", wait_after_candidate_s=" + formatNullable(trial.spawnWaitAfterCandidateS));
            System.out.println("Detectability    : method=" + blankToUnknown(trial.detectabilityMethod)
                    + ", proxy=" + (trial.detectabilityOperationalProxy == null ? "n/a" : trial.detectabilityOperationalProxy)
                    + ", threshold_px=" + formatNullable(trial.detectabilityThresholdPx)
                    + ", span_px=" + formatNullable(trial.detectabilityEstimatedSpanPx)
                    + ", screen=" + formatNullable(trial.detectabilityScreenXPx)
                    + "/" + formatNullable(trial.detectabilityScreenYPx)
                    + ", slant_m=" + formatNullable(trial.detectabilitySlantDistanceM)
                    + ", horizontal_m=" + formatNullable(trial.detectabilityHorizontalDistanceM)
                    + ", bearing/elevation=" + formatNullable(trial.detectabilityRelativeBearingDeg)
                    + "/" + formatNullable(trial.detectabilityRelativeElevationDeg));
            System.out.println("Visual opportunity: method=" + blankToUnknown(trial.visualOpportunityMethod)
                    + ", proxy=" + (trial.visualOpportunityOperationalProxy == null ? "n/a" : trial.visualOpportunityOperationalProxy)
                    + ", recognition_claim=" + (trial.visualOpportunityRecognitionClaim == null ? "n/a" : trial.visualOpportunityRecognitionClaim)
                    + ", lights_ok=" + (trial.visualOpportunityLightsWriteOk == null ? "n/a" : trial.visualOpportunityLightsWriteOk)
                    + ", min/span_px=" + formatNullable(trial.visualOpportunityMinimumSpanPx)
                    + "/" + formatNullable(trial.visualOpportunityEstimatedSpanPx)
                    + ", screen=" + formatNullable(trial.visualOpportunityScreenXPx)
                    + "/" + formatNullable(trial.visualOpportunityScreenYPx)
                    + ", slant/horizontal_m=" + formatNullable(trial.visualOpportunitySlantDistanceM)
                    + "/" + formatNullable(trial.visualOpportunityHorizontalDistanceM)
                    + ", bearing/elevation=" + formatNullable(trial.visualOpportunityRelativeBearingDeg)
                    + "/" + formatNullable(trial.visualOpportunityRelativeElevationDeg));
            System.out.println("Spawn position   : lat=" + formatNullable(trial.spawnLatitudeDeg)
                    + ", lon=" + formatNullable(trial.spawnLongitudeDeg)
                    + ", elevation_m=" + formatNullable(trial.spawnElevationM)
                    + ", agl_m=" + formatNullable(trial.spawnAglM));
            System.out.println("Spawn attitude   : heading=" + formatNullable(trial.spawnHeadingDeg)
                    + ", pitch=" + formatNullable(trial.spawnPitchDeg)
                    + ", roll=" + formatNullable(trial.spawnRollDeg)
                    + ", p/q/r=" + formatNullable(trial.spawnPRate)
                    + "/" + formatNullable(trial.spawnQRate)
                    + "/" + formatNullable(trial.spawnRRate)
                    + ", tas_mps=" + formatNullable(trial.spawnTasMps)
                    + ", vs_mps=" + formatNullable(trial.spawnVerticalSpeedMps));
            System.out.println("Spawn controls   : pitch=" + formatNullable(trial.spawnPitchInput)
                    + ", roll=" + formatNullable(trial.spawnRollInput)
                    + ", yaw=" + formatNullable(trial.spawnYawInput)
                    + ", throttle=" + formatNullable(trial.spawnThrottleInput));
            System.out.println("Response baseline: anchor=" + blankToUnknown(trial.responseAnchor)
                    + ", method=" + blankToUnknown(trial.responseMethod)
                    + ", valid=" + (trial.responseBaselineValid == null ? "n/a" : trial.responseBaselineValid)
                    + ", samples=" + (trial.responseBaselineSamples == null ? "n/a" : trial.responseBaselineSamples)
                    + ", window=" + formatNullable(trial.responseBaselineWindowS)
                    + ", active_at_spawn=" + (trial.activeControlAtSpawn == null ? "n/a" : trial.activeControlAtSpawn)
                    + ", active_at_anchor=" + (trial.activeControlAtAnchor == null ? "n/a" : trial.activeControlAtAnchor)
                    + ", active_at_detectable=" + (trial.activeControlAtDetectability == null ? "n/a" : trial.activeControlAtDetectability)
                    + ", active_at_visual_opportunity=" + (trial.activeControlAtVisualOpportunity == null ? "n/a" : trial.activeControlAtVisualOpportunity)
                    + ", trigger_axis=" + blankToUnknown(trial.responseTriggerAxis));
            System.out.println("Max abs input    : pitch=" + formatNullable(trial.maxAbsPitchInput)
                    + ", roll=" + formatNullable(trial.maxAbsRollInput)
                    + ", yaw=" + formatNullable(trial.maxAbsYawInput)
                    + ", throttle=" + formatNullable(trial.maxAbsThrottleInput));
            System.out.println("First response axis : " + trial.firstControlAxis);
            System.out.println("Latencies        : start->audio=" + formatDelta(trial.startTimeS, trial.taskAudioStartTimeS)
                    + ", audio_duration=" + formatDelta(trial.taskAudioStartTimeS, trial.taskAudioEndTimeS)
                    + ", planned_audio->spawn=" + formatNullable(trial.plannedAudioToSpawnS)
                    + ", audio->spawn=" + formatDelta(trial.taskAudioStartTimeS, trial.spawnTimeS)
                    + ", timing_valid=" + (trial.audioSpawnTimingValid == null ? "n/a" : trial.audioSpawnTimingValid)
                    + ", spawn->visual_opportunity=" + formatDelta(trial.spawnTimeS, trial.visualOpportunityTimeS)
                    + ", visual_opportunity->response=" + formatOrderedDelta(trial.visualOpportunityTimeS, trial.responseStartTimeS)
                    + ", audio->detectable=" + formatDelta(trial.taskAudioStartTimeS, trial.detectabilityTimeS)
                    + ", spawn->detectable=" + formatDelta(trial.spawnTimeS, trial.detectabilityTimeS)
                    + ", detectable->response=" + formatOrderedDelta(trial.detectabilityTimeS, trial.responseStartTimeS)
                    + ", response_vs_detectable=" + responseVsDetectabilityStatus(trial.detectabilityTimeS, trial.responseStartTimeS)
                    + ", spawn->response=" + formatDelta(trial.spawnTimeS, trial.responseStartTimeS)
                    + ", spawn->hazard=" + formatDelta(trial.spawnTimeS, trial.hazardTimeS)
                    + ", advisory->response=" + formatDelta(trial.advisoryTimeS, trial.responseStartTimeS)
                    + ", hazard_window=" + formatDelta(trial.hazardTimeS, trial.hazardClearTimeS)
                    + ", response_duration=" + formatDelta(trial.responseStartTimeS, trial.responseEndTimeS));
        }
    }

    private static Map<Integer, List<String[]>> groupByTrialId(List<String[]> rows, int trialIdIndex) {
        Map<Integer, List<String[]>> byTrial = new LinkedHashMap<>();
        for (String[] row : rows) {
            Integer trialId = parseNullableInteger(valueAt(row, trialIdIndex));
            if (trialId == null) {
                continue;
            }
            byTrial.computeIfAbsent(trialId, ignored -> new ArrayList<>()).add(row);
        }
        return byTrial;
    }

    private static List<String[]> rowsWithinTimeWindow(
            List<String[]> rows,
            int simTimeIndex,
            Double startTimeS,
            Double endTimeS
    ) {
        if (startTimeS == null && endTimeS == null) {
            return rows;
        }

        List<String[]> boundedRows = new ArrayList<>();
        for (String[] row : rows) {
            Double simTimeS = parseNullableDouble(valueAt(row, simTimeIndex));
            if (simTimeS == null) {
                continue;
            }
            if (startTimeS != null && simTimeS < startTimeS) {
                continue;
            }
            if (endTimeS != null && simTimeS > endTimeS) {
                continue;
            }
            boundedRows.add(row);
        }
        return boundedRows;
    }

    private static Map<String, Integer> eventCounts(List<String[]> eventRows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String[] row : eventRows) {
            String eventType = valueAt(row, 3);
            counts.put(eventType, counts.getOrDefault(eventType, 0) + 1);
        }
        return counts;
    }

    private static boolean isCleanTrial(Map<String, Integer> eventCounts) {
        for (String eventType : CLEAN_TRIAL_EVENTS) {
            if (eventCounts.getOrDefault(eventType, 0) != 1) {
                return false;
            }
        }
        return true;
    }

    private static String strictIncompleteReason(List<String[]> eventRows, Map<String, Integer> eventCounts) {
        String visualOpportunityReason = visualOpportunityIncompleteReason(eventRows, eventCounts);
        if (!visualOpportunityReason.isBlank()) {
            return visualOpportunityReason;
        }
        if (eventCounts.getOrDefault("PILOT_RESPONSE_BASELINE", 0) > 0) {
            return spawnResponseIncompleteReason(eventRows, eventCounts);
        }

        for (String eventType : CLEAN_TRIAL_EVENTS) {
            int count = eventCounts.getOrDefault(eventType, 0);
            if (count == 0) {
                return "missing " + eventType;
            }
            if (count > 1) {
                return "duplicate " + eventType + " count=" + count;
            }
        }

        boolean taskAudioRequired = requiresTaskAudio(eventRows, eventCounts);
        if (taskAudioRequired) {
            for (String eventType : TASK_AUDIO_EVENTS) {
                int count = eventCounts.getOrDefault(eventType, 0);
                if (count == 0) {
                    return "missing " + eventType;
                }
                if (count > 1) {
                    return "duplicate " + eventType + " count=" + count;
                }
            }
        }

        int searchStartIndex = 0;
        String previousEventType = "";
        for (String expectedEventType : CLEAN_TRIAL_EVENTS) {
            int foundIndex = indexOfEventAtOrAfter(eventRows, expectedEventType, searchStartIndex);
            if (foundIndex < 0) {
                return expectedEventType + " occurred before " + previousEventType;
            }
            searchStartIndex = foundIndex + 1;
            previousEventType = expectedEventType;
        }

        if (taskAudioRequired) {
            int trialStartIndex = indexOfEventAtOrAfter(eventRows, "TRIAL_START", 0);
            int audioStartIndex = indexOfEventAtOrAfter(eventRows, "TASK_COMMAND_AUDIO_START", trialStartIndex + 1);
            if (audioStartIndex < 0) {
                return "TASK_COMMAND_AUDIO_START occurred before TRIAL_START";
            }
            int audioEndIndex = indexOfEventAtOrAfter(eventRows, "TASK_COMMAND_AUDIO_END", audioStartIndex + 1);
            if (audioEndIndex < 0) {
                return "TASK_COMMAND_AUDIO_END occurred before TASK_COMMAND_AUDIO_START";
            }
            int trialEndIndex = indexOfEventAtOrAfter(eventRows, "TRIAL_END", audioEndIndex + 1);
            if (trialEndIndex < 0) {
                return "TRIAL_END occurred before TASK_COMMAND_AUDIO_END";
            }
        }

        String protocol = valueFromDetail(firstEventDetail(eventRows, "TRIAL_START"), "protocol");
        if (isAudioAnchoredSpawnProtocol(protocol)) {
            List<String> audioAnchoredSpawnOrder = List.of(
                    "TRIAL_START",
                    "TASK_COMMAND_AUDIO_START",
                    "SCENARIO_SELECTED",
                    "INTRUDER_SPAWNED",
                    "TRIAL_END"
            );
            int sequenceStartIndex = 0;
            String previous = "";
            for (String expected : audioAnchoredSpawnOrder) {
                int foundIndex = indexOfEventAtOrAfter(eventRows, expected, sequenceStartIndex);
                if (foundIndex < 0) {
                    return expected + " occurred before " + previous;
                }
                sequenceStartIndex = foundIndex + 1;
                previous = expected;
            }
        }

        if (requiresVisualDetectability(eventRows)) {
            int detectabilityCount = eventCounts.getOrDefault("INTRUDER_VISUALLY_DETECTABLE", 0);
            if (detectabilityCount == 0) {
                return "missing INTRUDER_VISUALLY_DETECTABLE";
            }
            if (detectabilityCount > 1) {
                return "duplicate INTRUDER_VISUALLY_DETECTABLE count=" + detectabilityCount;
            }
            String visualOrderedReason = orderedSequenceReason(
                    eventRows,
                    List.of(
                            "INTRUDER_SPAWNED",
                            "INTRUDER_VISUALLY_DETECTABLE",
                            "TRIAL_END"
                    )
            );
            if (!visualOrderedReason.isBlank()) {
                return visualOrderedReason;
            }
        }

        return "";
    }

    private static String spawnResponseIncompleteReason(
            List<String[]> eventRows,
            Map<String, Integer> eventCounts
    ) {
        String visualOpportunityReason = visualOpportunityIncompleteReason(eventRows, eventCounts);
        if (!visualOpportunityReason.isBlank()) {
            return visualOpportunityReason;
        }
        for (String eventType : SPAWN_RESPONSE_RECORDING_EVENTS) {
            int count = eventCounts.getOrDefault(eventType, 0);
            if (count == 0) {
                return "missing " + eventType;
            }
            if (count > 1) {
                return "duplicate " + eventType + " count=" + count;
            }
        }

        boolean taskAudioRequired = requiresTaskAudio(eventRows, eventCounts);
        if (taskAudioRequired) {
            for (String eventType : TASK_AUDIO_EVENTS) {
                int count = eventCounts.getOrDefault(eventType, 0);
                if (count == 0) {
                    return "missing " + eventType;
                }
                if (count > 1) {
                    return "duplicate " + eventType + " count=" + count;
                }
            }
        }

        String orderedReason = orderedSequenceReason(eventRows, SPAWN_RESPONSE_RECORDING_EVENTS);
        if (!orderedReason.isBlank()) {
            return orderedReason;
        }

        if (taskAudioRequired) {
            orderedReason = orderedSequenceReason(
                    eventRows,
                    List.of(
                            "TRIAL_START",
                            "TASK_COMMAND_AUDIO_START",
                            "TASK_COMMAND_AUDIO_END",
                            "TRIAL_END"
                    )
            );
            if (!orderedReason.isBlank()) {
                return orderedReason;
            }
        }

        String protocol = valueFromDetail(firstEventDetail(eventRows, "TRIAL_START"), "protocol");
        if (isAudioAnchoredSpawnProtocol(protocol)) {
            orderedReason = orderedSequenceReason(
                    eventRows,
                    List.of(
                            "TRIAL_START",
                            "TASK_COMMAND_AUDIO_START",
                            "SCENARIO_SELECTED",
                            "INTRUDER_SPAWNED",
                            "TRIAL_END"
                    )
            );
            if (!orderedReason.isBlank()) {
                return orderedReason;
            }
        }

        if (requiresVisualDetectability(eventRows)) {
            int detectabilityCount = eventCounts.getOrDefault("INTRUDER_VISUALLY_DETECTABLE", 0);
            if (detectabilityCount == 0) {
                return "missing INTRUDER_VISUALLY_DETECTABLE";
            }
            if (detectabilityCount > 1) {
                return "duplicate INTRUDER_VISUALLY_DETECTABLE count=" + detectabilityCount;
            }
            orderedReason = orderedSequenceReason(
                    eventRows,
                    List.of(
                            "INTRUDER_SPAWNED",
                            "INTRUDER_VISUALLY_DETECTABLE",
                            "TRIAL_END"
                    )
            );
            if (!orderedReason.isBlank()) {
                return orderedReason;
            }
        }

        String baselineValid = valueFromDetail(
                firstEventDetail(eventRows, "PILOT_RESPONSE_BASELINE"),
                "baseline_valid"
        );
        if (!"true".equalsIgnoreCase(baselineValid)) {
            return "response baseline invalid";
        }

        String responseLowerBoundEvent = requiresVisualOpportunity(eventRows)
                ? "INTRUDER_VISUAL_OPPORTUNITY_ONSET"
                : "INTRUDER_SPAWNED";
        String optionalReason = optionalEventPairReason(
                eventRows,
                eventCounts,
                "PILOT_RESPONSE_START",
                "PILOT_RESPONSE_END",
                responseLowerBoundEvent,
                "TRIAL_END"
        );
        if (!optionalReason.isBlank()) {
            return optionalReason;
        }
        optionalReason = optionalEventPairReason(
                eventRows,
                eventCounts,
                "HAZARD_DETECTED",
                "HAZARD_CLEARED",
                "INTRUDER_SPAWNED",
                "TRIAL_END"
        );
        if (!optionalReason.isBlank()) {
            return optionalReason;
        }
        return optionalEventPairReason(
                eventRows,
                eventCounts,
                "ADVISORY_SHOWN",
                "ADVISORY_CLEARED",
                "INTRUDER_SPAWNED",
                "TRIAL_END"
        );
    }

    private static String orderedSequenceReason(List<String[]> eventRows, List<String> eventTypes) {
        int searchStartIndex = 0;
        String previous = "";
        for (String expected : eventTypes) {
            int foundIndex = indexOfEventAtOrAfter(eventRows, expected, searchStartIndex);
            if (foundIndex < 0) {
                return expected + " occurred before " + previous;
            }
            searchStartIndex = foundIndex + 1;
            previous = expected;
        }
        return "";
    }

    private static String optionalEventPairReason(
            List<String[]> eventRows,
            Map<String, Integer> eventCounts,
            String startEvent,
            String endEvent,
            String lowerBoundEvent,
            String upperBoundEvent
    ) {
        int startCount = eventCounts.getOrDefault(startEvent, 0);
        int endCount = eventCounts.getOrDefault(endEvent, 0);
        if (startCount == 0 && endCount == 0) {
            return "";
        }
        if (startCount != 1) {
            return startCount == 0
                    ? "missing " + startEvent
                    : "duplicate " + startEvent + " count=" + startCount;
        }
        if (endCount != 1) {
            return endCount == 0
                    ? "missing " + endEvent
                    : "duplicate " + endEvent + " count=" + endCount;
        }

        Double lowerBoundTime = firstEventTime(eventRows, lowerBoundEvent);
        Double startTime = firstEventTime(eventRows, startEvent);
        Double endTime = firstEventTime(eventRows, endEvent);
        Double upperBoundTime = firstEventTime(eventRows, upperBoundEvent);
        if (lowerBoundTime == null || startTime == null || endTime == null || upperBoundTime == null) {
            return "missing event time for " + startEvent + "/" + endEvent;
        }
        if (startTime < lowerBoundTime) {
            return startEvent + " occurred before " + lowerBoundEvent;
        }
        if (endTime < startTime) {
            return endEvent + " occurred before " + startEvent;
        }
        if (upperBoundTime < endTime) {
            return upperBoundEvent + " occurred before " + endEvent;
        }
        return "";
    }

    private static boolean requiresTaskAudio(List<String[]> eventRows, Map<String, Integer> eventCounts) {
        if (eventCounts.getOrDefault("TASK_COMMAND_AUDIO_START", 0) > 0
                || eventCounts.getOrDefault("TASK_COMMAND_AUDIO_END", 0) > 0) {
            return true;
        }
        String trialStartDetail = firstEventDetail(eventRows, "TRIAL_START");
        String protocol = valueFromDetail(trialStartDetail, "protocol");
        return protocol.startsWith("audio_task_");
    }

    private static boolean requiresVisualDetectability(List<String[]> eventRows) {
        String trialStartDetail = firstEventDetail(eventRows, "TRIAL_START");
        return "audio_task_v32".equals(valueFromDetail(trialStartDetail, "protocol"));
    }

    private static boolean requiresVisualOpportunity(List<String[]> eventRows) {
        String trialStartDetail = firstEventDetail(eventRows, "TRIAL_START");
        String protocol = valueFromDetail(trialStartDetail, "protocol");
        return "audio_task_v33".equals(protocol)
                || "audio_task_v34".equals(protocol)
                || "audio_task_v35".equals(protocol)
                || "audio_task_v36".equals(protocol)
                || "audio_task_v37".equals(protocol)
                || "audio_task_v38".equals(protocol)
                || "audio_task_v39".equals(protocol);
    }

    private static String visualOpportunityIncompleteReason(
            List<String[]> eventRows,
            Map<String, Integer> eventCounts
    ) {
        if (!requiresVisualOpportunity(eventRows)) {
            return "";
        }
        int count = eventCounts.getOrDefault("INTRUDER_VISUAL_OPPORTUNITY_ONSET", 0);
        if (count == 0) {
            return "missing INTRUDER_VISUAL_OPPORTUNITY_ONSET";
        }
        if (count > 1) {
            return "duplicate INTRUDER_VISUAL_OPPORTUNITY_ONSET count=" + count;
        }
        return orderedSequenceReason(
                eventRows,
                List.of(
                        "INTRUDER_SPAWNED",
                        "INTRUDER_VISUAL_OPPORTUNITY_ONSET",
                        "TRIAL_END"
                )
        );
    }

    static Boolean audioSpawnTimingValid(
            String protocol,
            Double plannedAudioToSpawnS,
            Double taskAudioStartTimeS,
            Double spawnTimeS,
            Double spawnTimeoutAudioToSpawnS,
            Boolean speedStableBeforeSpawn,
            Boolean spawnForcedByTimeout,
            Double speedErrorAtSpawnKias,
            Double speedRateAtSpawnKiasS,
            Boolean speedRateValidAtSpawn,
            Double speedStabilityDurationS
    ) {
        if (!isAudioAnchoredSpawnProtocol(protocol)) {
            return null;
        }
        if (plannedAudioToSpawnS == null || taskAudioStartTimeS == null || spawnTimeS == null) {
            return false;
        }
        double actualAudioToSpawnS = spawnTimeS - taskAudioStartTimeS;
        if ("audio_task_v35".equals(protocol)
                || "audio_task_v36".equals(protocol)
                || "audio_task_v37".equals(protocol)
                || "audio_task_v38".equals(protocol)
                || "audio_task_v39".equals(protocol)) {
            if (spawnTimeoutAudioToSpawnS == null) {
                return false;
            }
            double timeoutS = spawnTimeoutAudioToSpawnS;
            boolean commonTimingValid = plannedAudioToSpawnS >= 6.0
                    && plannedAudioToSpawnS <= 10.0
                    && timeoutS >= 13.9
                    && timeoutS <= 14.1
                    && actualAudioToSpawnS >= plannedAudioToSpawnS - 0.1
                    && actualAudioToSpawnS <= timeoutS + 0.1;
            if (!commonTimingValid
                    || speedStableBeforeSpawn == null
                    || spawnForcedByTimeout == null) {
                return false;
            }
            if (spawnForcedByTimeout) {
                return !speedStableBeforeSpawn && actualAudioToSpawnS >= timeoutS - 0.1;
            }
            boolean targetBandDwellValid = speedStableBeforeSpawn
                    && speedErrorAtSpawnKias != null
                    && Math.abs(speedErrorAtSpawnKias) <= 5.05
                    && speedStabilityDurationS != null
                    && speedStabilityDurationS >= 1.49;
            if ("audio_task_v36".equals(protocol)
                    || "audio_task_v37".equals(protocol)
                    || "audio_task_v38".equals(protocol)
                    || "audio_task_v39".equals(protocol)) {
                return targetBandDwellValid;
            }
            return targetBandDwellValid
                    && Boolean.TRUE.equals(speedRateValidAtSpawn)
                    && speedRateAtSpawnKiasS != null
                    && Math.abs(speedRateAtSpawnKiasS) <= 1.05;
        }
        return plannedAudioToSpawnS >= 3.0
                && plannedAudioToSpawnS <= 10.0
                && actualAudioToSpawnS >= 3.0
                && actualAudioToSpawnS <= 10.1;
    }

    private static boolean isAudioAnchoredSpawnProtocol(String protocol) {
        return "audio_task_v27".equals(protocol)
                || "audio_task_v28".equals(protocol)
                || "audio_task_v29".equals(protocol)
                || "audio_task_v31".equals(protocol)
                || "audio_task_v32".equals(protocol)
                || "audio_task_v33".equals(protocol)
                || "audio_task_v34".equals(protocol)
                || "audio_task_v35".equals(protocol)
                || "audio_task_v36".equals(protocol)
                || "audio_task_v37".equals(protocol)
                || "audio_task_v38".equals(protocol)
                || "audio_task_v39".equals(protocol);
    }

    private static int indexOfEventAtOrAfter(List<String[]> eventRows, String eventType, int startIndex) {
        for (int i = startIndex; i < eventRows.size(); i++) {
            if (eventType.equals(valueAt(eventRows.get(i), 3))) {
                return i;
            }
        }
        return -1;
    }

    private static Double firstEventTime(List<String[]> eventRows, String eventType) {
        for (String[] row : eventRows) {
            if (eventType.equals(valueAt(row, 3))) {
                return parseNullableDouble(valueAt(row, 9));
            }
        }
        return null;
    }

    private static String firstEventDetail(List<String[]> eventRows, String eventType) {
        for (String[] row : eventRows) {
            if (eventType.equals(valueAt(row, 3))) {
                return valueAt(row, 5);
            }
        }
        return "";
    }

    private static Integer firstEventStateSampleIndex(List<String[]> eventRows, String eventType) {
        for (String[] row : eventRows) {
            if (eventType.equals(valueAt(row, 3))) {
                return parseNullableInteger(valueAt(row, 7));
            }
        }
        return null;
    }

    private static String eventCountsToString(Map<String, Integer> eventCounts) {
        if (eventCounts.isEmpty()) {
            return "none";
        }

        List<String> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : eventCounts.entrySet()) {
            values.add(entry.getKey() + "=" + entry.getValue());
        }
        return String.join(", ", values);
    }

    private static String valueFromDetail(String detail, String key) {
        if (detail == null || detail.isBlank()) {
            return "";
        }

        String prefix = key + "=";
        String[] parts = detail.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith(prefix)) {
                return trimmed.substring(prefix.length());
            }
        }
        return "";
    }

    private static Double maxAbs(List<String[]> rows, int columnIndex) {
        Double maxValue = null;
        for (String[] row : rows) {
            Double value = parseNullableDouble(valueAt(row, columnIndex));
            if (value == null) {
                continue;
            }
            double abs = Math.abs(value);
            if (maxValue == null || abs > maxValue) {
                maxValue = abs;
            }
        }
        return maxValue;
    }

    private static Double nearestStateValueAtTime(
            List<String[]> rows,
            Double targetTimeS,
            int valueColumnIndex
    ) {
        if (targetTimeS == null) {
            return null;
        }

        Double nearestValue = null;
        double nearestDeltaS = Double.POSITIVE_INFINITY;
        for (String[] row : rows) {
            Double simTimeS = parseNullableDouble(valueAt(row, 3));
            Double value = parseNullableDouble(valueAt(row, valueColumnIndex));
            if (simTimeS == null || value == null) {
                continue;
            }
            double deltaS = Math.abs(simTimeS - targetTimeS);
            if (deltaS < nearestDeltaS) {
                nearestDeltaS = deltaS;
                nearestValue = value;
            }
        }
        return nearestValue;
    }

    private static StateSnapshot nearestStateSnapshotAtTime(
            List<String[]> rows,
            Double targetTimeS,
            StateColumns columns
    ) {
        if (targetTimeS == null) {
            return null;
        }

        String[] nearestRow = null;
        double nearestDeltaS = Double.POSITIVE_INFINITY;
        for (String[] row : rows) {
            Double simTimeS = parseNullableDouble(valueAt(row, columns.simTimeIndex));
            if (simTimeS == null) {
                continue;
            }
            double deltaS = Math.abs(simTimeS - targetTimeS);
            if (deltaS < nearestDeltaS) {
                nearestDeltaS = deltaS;
                nearestRow = row;
            }
        }
        return nearestRow == null ? null : StateSnapshot.from(nearestRow, columns);
    }

    private static Double detailValueOrFallback(String detail, String key, Double fallback) {
        Double detailValue = parseNullableDouble(valueFromDetail(detail, key));
        return detailValue == null ? fallback : detailValue;
    }

    private static Double meanAbsoluteErrorInWindow(
            List<String[]> rows,
            int valueColumnIndex,
            Double targetValue,
            Double startTimeS,
            Double endTimeS
    ) {
        if (targetValue == null || startTimeS == null || endTimeS == null || endTimeS < startTimeS) {
            return null;
        }

        double errorSum = 0.0;
        int sampleCount = 0;
        for (String[] row : rows) {
            Double simTimeS = parseNullableDouble(valueAt(row, 3));
            Double value = parseNullableDouble(valueAt(row, valueColumnIndex));
            if (simTimeS == null || value == null || simTimeS < startTimeS || simTimeS > endTimeS) {
                continue;
            }
            errorSum += Math.abs(value - targetValue);
            sampleCount++;
        }
        return sampleCount == 0 ? null : errorSum / sampleCount;
    }

    private static Double withinToleranceRateInWindow(
            List<String[]> rows,
            int valueColumnIndex,
            Double targetValue,
            double tolerance,
            Double startTimeS,
            Double endTimeS
    ) {
        if (targetValue == null || startTimeS == null || endTimeS == null || endTimeS < startTimeS) {
            return null;
        }

        int validSampleCount = 0;
        int withinToleranceCount = 0;
        for (String[] row : rows) {
            Double simTimeS = parseNullableDouble(valueAt(row, 3));
            Double value = parseNullableDouble(valueAt(row, valueColumnIndex));
            if (simTimeS == null || value == null || simTimeS < startTimeS || simTimeS > endTimeS) {
                continue;
            }
            validSampleCount++;
            if (Math.abs(value - targetValue) <= tolerance) {
                withinToleranceCount++;
            }
        }
        return validSampleCount == 0 ? null : (double) withinToleranceCount / validSampleCount;
    }

    private static String firstControlAxisAtResponse(
            List<String[]> stateRows,
            Integer advisoryStateSampleIndex,
            Integer responseStateSampleIndex
    ) {
        if (advisoryStateSampleIndex == null || responseStateSampleIndex == null) {
            return "n/a";
        }

        String[] baseline = firstStateAtOrAfter(stateRows, advisoryStateSampleIndex);
        String[] response = firstStateAtOrAfter(stateRows, responseStateSampleIndex);
        if (baseline == null || response == null) {
            return "n/a";
        }

        Double baselinePitch = parseNullableDouble(valueAt(baseline, 23));
        Double baselineRoll = parseNullableDouble(valueAt(baseline, 24));
        Double baselineYaw = parseNullableDouble(valueAt(baseline, 25));
        Double baselineThrottle = parseNullableDouble(valueAt(baseline, 26));
        Double responsePitch = parseNullableDouble(valueAt(response, 23));
        Double responseRoll = parseNullableDouble(valueAt(response, 24));
        Double responseYaw = parseNullableDouble(valueAt(response, 25));
        Double responseThrottle = parseNullableDouble(valueAt(response, 26));
        if (baselinePitch == null || baselineRoll == null || baselineYaw == null || baselineThrottle == null
                || responsePitch == null || responseRoll == null || responseYaw == null || responseThrottle == null) {
            return "n/a";
        }

        double pitch = Math.abs(responsePitch - baselinePitch);
        double roll = Math.abs(responseRoll - baselineRoll);
        double yaw = Math.abs(responseYaw - baselineYaw);
        double throttle = Math.abs(responseThrottle - baselineThrottle);
        double max = Math.max(Math.max(pitch, roll), Math.max(yaw, throttle));

        if (max == pitch) {
            return "pitch";
        }
        if (max == roll) {
            return "roll";
        }
        if (max == yaw) {
            return "yaw";
        }
        return "throttle";
    }

    private static String[] firstStateAtOrAfter(List<String[]> stateRows, int targetSampleIndex) {
        for (String[] row : stateRows) {
            Integer sampleIndex = parseNullableInteger(valueAt(row, 2));
            if (sampleIndex != null && sampleIndex >= targetSampleIndex) {
                return row;
            }
        }
        return null;
    }

    private static String valueAt(String[] row, int index) {
        return index < row.length ? row[index] : "";
    }

    private static double parseDouble(String value) {
        return Double.parseDouble(value);
    }

    private static Double parseNullableDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Double.parseDouble(value);
    }

    private static Integer parseNullableInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean parseNullableBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return null;
    }

    private static String formatNullable(Double value) {
        return value == null || value.isNaN() ? "n/a" : String.format("%.3f", value);
    }

    private static String formatDelta(Double start, Double end) {
        if (start == null || end == null) {
            return "n/a";
        }
        return formatNullable(end - start) + " s";
    }

    private static String formatOrderedDelta(Double start, Double end) {
        if (start == null || end == null || end < start) {
            return "n/a";
        }
        return formatNullable(end - start) + " s";
    }

    private static String responseVsDetectabilityStatus(Double detectabilityTimeS, Double responseTimeS) {
        if (detectabilityTimeS == null) {
            return "NO_DETECTABILITY_EVENT";
        }
        if (responseTimeS == null) {
            return "NO_RESPONSE_EVENT";
        }
        if (responseTimeS < detectabilityTimeS) {
            return "RESPONSE_BEFORE_DETECTABILITY";
        }
        return "POST_DETECTABILITY_RESPONSE";
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static int columnIndex(String[] header, String columnName, int fallbackIndex) {
        for (int i = 0; i < header.length; i++) {
            if (columnName.equals(header[i])) {
                return i;
            }
        }
        return fallbackIndex;
    }

    private static class CsvTable {
        final String[] header;
        final List<String[]> rows;

        private CsvTable(String[] header, List<String[]> rows) {
            this.header = header;
            this.rows = rows;
        }
    }

    private static class StateColumns {
        final int simTimeIndex;
        final int latitudeIndex;
        final int longitudeIndex;
        final int elevationIndex;
        final int aglIndex;
        final int headingIndex;
        final int pitchIndex;
        final int rollIndex;
        final int pRateIndex;
        final int qRateIndex;
        final int rRateIndex;
        final int iasIndex;
        final int tasIndex;
        final int verticalSpeedIndex;
        final int pitchInputIndex;
        final int rollInputIndex;
        final int yawInputIndex;
        final int throttleInputIndex;

        private StateColumns(
                int simTimeIndex,
                int latitudeIndex,
                int longitudeIndex,
                int elevationIndex,
                int aglIndex,
                int headingIndex,
                int pitchIndex,
                int rollIndex,
                int pRateIndex,
                int qRateIndex,
                int rRateIndex,
                int iasIndex,
                int tasIndex,
                int verticalSpeedIndex,
                int pitchInputIndex,
                int rollInputIndex,
                int yawInputIndex,
                int throttleInputIndex
        ) {
            this.simTimeIndex = simTimeIndex;
            this.latitudeIndex = latitudeIndex;
            this.longitudeIndex = longitudeIndex;
            this.elevationIndex = elevationIndex;
            this.aglIndex = aglIndex;
            this.headingIndex = headingIndex;
            this.pitchIndex = pitchIndex;
            this.rollIndex = rollIndex;
            this.pRateIndex = pRateIndex;
            this.qRateIndex = qRateIndex;
            this.rRateIndex = rRateIndex;
            this.iasIndex = iasIndex;
            this.tasIndex = tasIndex;
            this.verticalSpeedIndex = verticalSpeedIndex;
            this.pitchInputIndex = pitchInputIndex;
            this.rollInputIndex = rollInputIndex;
            this.yawInputIndex = yawInputIndex;
            this.throttleInputIndex = throttleInputIndex;
        }

        static StateColumns from(CsvTable table) {
            return new StateColumns(
                    columnIndex(table.header, "sim_time_s", 3),
                    columnIndex(table.header, "latitude_deg", 7),
                    columnIndex(table.header, "longitude_deg", 8),
                    columnIndex(table.header, "elevation_m", 9),
                    columnIndex(table.header, "y_agl_m", 10),
                    columnIndex(table.header, "heading_deg", 14),
                    columnIndex(table.header, "pitch_deg", 15),
                    columnIndex(table.header, "roll_deg", 16),
                    columnIndex(table.header, "p_rate", 17),
                    columnIndex(table.header, "q_rate", 18),
                    columnIndex(table.header, "r_rate", 19),
                    columnIndex(table.header, "ias_mps", 20),
                    columnIndex(table.header, "tas_mps", 21),
                    columnIndex(table.header, "vertical_speed_mps", 22),
                    columnIndex(table.header, "pitch_input", 23),
                    columnIndex(table.header, "roll_input", 24),
                    columnIndex(table.header, "yaw_input", 25),
                    columnIndex(table.header, "throttle_input", 26)
            );
        }
    }

    private static class StateSnapshot {
        final Double latitudeDeg;
        final Double longitudeDeg;
        final Double elevationM;
        final Double aglM;
        final Double headingDeg;
        final Double pitchDeg;
        final Double rollDeg;
        final Double pRate;
        final Double qRate;
        final Double rRate;
        final Double tasMps;
        final Double verticalSpeedMps;
        final Double pitchInput;
        final Double rollInput;
        final Double yawInput;
        final Double throttleInput;

        private StateSnapshot(
                Double latitudeDeg,
                Double longitudeDeg,
                Double elevationM,
                Double aglM,
                Double headingDeg,
                Double pitchDeg,
                Double rollDeg,
                Double pRate,
                Double qRate,
                Double rRate,
                Double tasMps,
                Double verticalSpeedMps,
                Double pitchInput,
                Double rollInput,
                Double yawInput,
                Double throttleInput
        ) {
            this.latitudeDeg = latitudeDeg;
            this.longitudeDeg = longitudeDeg;
            this.elevationM = elevationM;
            this.aglM = aglM;
            this.headingDeg = headingDeg;
            this.pitchDeg = pitchDeg;
            this.rollDeg = rollDeg;
            this.pRate = pRate;
            this.qRate = qRate;
            this.rRate = rRate;
            this.tasMps = tasMps;
            this.verticalSpeedMps = verticalSpeedMps;
            this.pitchInput = pitchInput;
            this.rollInput = rollInput;
            this.yawInput = yawInput;
            this.throttleInput = throttleInput;
        }

        static StateSnapshot from(String[] row, StateColumns columns) {
            return new StateSnapshot(
                    parseNullableDouble(valueAt(row, columns.latitudeIndex)),
                    parseNullableDouble(valueAt(row, columns.longitudeIndex)),
                    parseNullableDouble(valueAt(row, columns.elevationIndex)),
                    parseNullableDouble(valueAt(row, columns.aglIndex)),
                    parseNullableDouble(valueAt(row, columns.headingIndex)),
                    parseNullableDouble(valueAt(row, columns.pitchIndex)),
                    parseNullableDouble(valueAt(row, columns.rollIndex)),
                    parseNullableDouble(valueAt(row, columns.pRateIndex)),
                    parseNullableDouble(valueAt(row, columns.qRateIndex)),
                    parseNullableDouble(valueAt(row, columns.rRateIndex)),
                    parseNullableDouble(valueAt(row, columns.tasIndex)),
                    parseNullableDouble(valueAt(row, columns.verticalSpeedIndex)),
                    parseNullableDouble(valueAt(row, columns.pitchInputIndex)),
                    parseNullableDouble(valueAt(row, columns.rollInputIndex)),
                    parseNullableDouble(valueAt(row, columns.yawInputIndex)),
                    parseNullableDouble(valueAt(row, columns.throttleInputIndex))
            );
        }
    }

    private static class IntruderColumns {
        final int simTimeIndex;
        final int horizontalDistanceIndex;
        final int verticalSeparationIndex;

        private IntruderColumns(int simTimeIndex, int horizontalDistanceIndex, int verticalSeparationIndex) {
            this.simTimeIndex = simTimeIndex;
            this.horizontalDistanceIndex = horizontalDistanceIndex;
            this.verticalSeparationIndex = verticalSeparationIndex;
        }

        static IntruderColumns from(CsvTable table) {
            return new IntruderColumns(
                    columnIndex(table.header, "sim_time_s", 3),
                    columnIndex(table.header, "horizontal_distance", 10),
                    columnIndex(table.header, "vertical_separation", 11)
            );
        }
    }

    public static class XPlaneSessionSummary {
        public final String sessionId;
        public final int stateSampleCount;
        public final int intruderSampleCount;
        public final Double stateStartTimeS;
        public final Double stateEndTimeS;
        public final int hazardDetectedCount;
        public final int hazardClearedCount;
        public final int advisoryShownCount;
        public final int advisoryClearedCount;
        public final int responseStartCount;
        public final int responseEndCount;
        public final Double firstHazardTimeS;
        public final Double firstHazardClearTimeS;
        public final Double firstAdvisoryTimeS;
        public final Double firstAdvisoryClearTimeS;
        public final Double firstResponseStartTimeS;
        public final Double firstResponseEndTimeS;
        public final Double minHorizontalDistance;
        public final Double minVerticalSeparation;
        public final Double minDistanceSimTimeS;
        public final List<XPlaneTrialSummary> trials;

        public XPlaneSessionSummary(
                String sessionId,
                int stateSampleCount,
                int intruderSampleCount,
                Double stateStartTimeS,
                Double stateEndTimeS,
                int hazardDetectedCount,
                int hazardClearedCount,
                int advisoryShownCount,
                int advisoryClearedCount,
                int responseStartCount,
                int responseEndCount,
                Double firstHazardTimeS,
                Double firstHazardClearTimeS,
                Double firstAdvisoryTimeS,
                Double firstAdvisoryClearTimeS,
                Double firstResponseStartTimeS,
                Double firstResponseEndTimeS,
                Double minHorizontalDistance,
                Double minVerticalSeparation,
                Double minDistanceSimTimeS,
                List<XPlaneTrialSummary> trials
        ) {
            this.sessionId = sessionId;
            this.stateSampleCount = stateSampleCount;
            this.intruderSampleCount = intruderSampleCount;
            this.stateStartTimeS = stateStartTimeS;
            this.stateEndTimeS = stateEndTimeS;
            this.hazardDetectedCount = hazardDetectedCount;
            this.hazardClearedCount = hazardClearedCount;
            this.advisoryShownCount = advisoryShownCount;
            this.advisoryClearedCount = advisoryClearedCount;
            this.responseStartCount = responseStartCount;
            this.responseEndCount = responseEndCount;
            this.firstHazardTimeS = firstHazardTimeS;
            this.firstHazardClearTimeS = firstHazardClearTimeS;
            this.firstAdvisoryTimeS = firstAdvisoryTimeS;
            this.firstAdvisoryClearTimeS = firstAdvisoryClearTimeS;
            this.firstResponseStartTimeS = firstResponseStartTimeS;
            this.firstResponseEndTimeS = firstResponseEndTimeS;
            this.minHorizontalDistance = minHorizontalDistance;
            this.minVerticalSeparation = minVerticalSeparation;
            this.minDistanceSimTimeS = minDistanceSimTimeS;
            this.trials = trials;
        }
    }

    public static class XPlaneTrialSummary {
        public final int trialId;
        public final boolean clean;
        public final String incompleteReason;
        public final String eventCounts;
        public final int stateSampleCount;
        public final int intruderSampleCount;
        public final String approach;
        public final Double resetTimeS;
        public final Double startTimeS;
        public final Double taskAudioStartTimeS;
        public final Double taskAudioEndTimeS;
        public final Double spawnTimeS;
        public final Double visualOpportunityTimeS;
        public final Double detectabilityTimeS;
        public final Double plannedAudioToSpawnS;
        public final Boolean audioSpawnTimingValid;
        public final String spawnGateMethod;
        public final Double spawnTimeoutAudioToSpawnS;
        public final Boolean speedStableBeforeSpawn;
        public final Double speedErrorAtSpawnKias;
        public final Double speedRateAtSpawnKiasS;
        public final Boolean speedRateValidAtSpawn;
        public final Double speedStabilityDurationS;
        public final Double speedGatePassAudioElapsedS;
        public final Double spawnWaitAfterCandidateS;
        public final Boolean spawnForcedByTimeout;
        public final Double targetSpeedKias;
        public final Double iasAtAudioStartKias;
        public final Double iasAtSpawnKias;
        public final Double iasAtVisualOpportunityKias;
        public final String visualOpportunityMethod;
        public final Boolean visualOpportunityOperationalProxy;
        public final Boolean visualOpportunityRecognitionClaim;
        public final Boolean visualOpportunityLightsWriteOk;
        public final Double visualOpportunityMinimumSpanPx;
        public final Double visualOpportunityEstimatedSpanPx;
        public final Double visualOpportunityScreenXPx;
        public final Double visualOpportunityScreenYPx;
        public final Double visualOpportunitySlantDistanceM;
        public final Double visualOpportunityHorizontalDistanceM;
        public final Double visualOpportunityRelativeBearingDeg;
        public final Double visualOpportunityRelativeElevationDeg;
        public final Double iasAtDetectabilityKias;
        public final String detectabilityMethod;
        public final Boolean detectabilityOperationalProxy;
        public final Double detectabilityThresholdPx;
        public final Double detectabilityEstimatedSpanPx;
        public final Double detectabilityScreenXPx;
        public final Double detectabilityScreenYPx;
        public final Double detectabilitySlantDistanceM;
        public final Double detectabilityHorizontalDistanceM;
        public final Double detectabilityRelativeBearingDeg;
        public final Double detectabilityRelativeElevationDeg;
        public final Double spawnLatitudeDeg;
        public final Double spawnLongitudeDeg;
        public final Double spawnElevationM;
        public final Double spawnAglM;
        public final Double spawnHeadingDeg;
        public final Double spawnPitchDeg;
        public final Double spawnRollDeg;
        public final Double spawnPRate;
        public final Double spawnQRate;
        public final Double spawnRRate;
        public final Double spawnTasMps;
        public final Double spawnVerticalSpeedMps;
        public final Double spawnPitchInput;
        public final Double spawnRollInput;
        public final Double spawnYawInput;
        public final Double spawnThrottleInput;
        public final Double preSpawnMeanAbsSpeedErrorKias;
        public final Double preSpawnWithin5KiasRate;
        public final Double soundMasterRatio;
        public final Double soundInteriorRatio;
        public final Double soundEngineRatio;
        public final Double soundPropRatio;
        public final Double soundEnviroRatio;
        public final Double soundRadioRatio;
        public final String responseAnchor;
        public final String responseMethod;
        public final String responseTriggerAxis;
        public final Boolean responseBaselineValid;
        public final Boolean activeControlAtSpawn;
        public final Boolean activeControlAtAnchor;
        public final Boolean activeControlAtDetectability;
        public final Boolean activeControlAtVisualOpportunity;
        public final Integer responseBaselineSamples;
        public final Double responseBaselineWindowS;
        public final Double responsePersistenceS;
        public final Double hazardTimeS;
        public final Double advisoryTimeS;
        public final Double responseStartTimeS;
        public final Double hazardClearTimeS;
        public final Double advisoryClearTimeS;
        public final Double responseEndTimeS;
        public final Double trialEndTimeS;
        public final Double minHorizontalDistance;
        public final Double minVerticalSeparation;
        public final Double minDistanceSimTimeS;
        public final Double maxAbsPitchInput;
        public final Double maxAbsRollInput;
        public final Double maxAbsYawInput;
        public final Double maxAbsThrottleInput;
        public final String firstControlAxis;

        public XPlaneTrialSummary(
                int trialId,
                boolean clean,
                String incompleteReason,
                String eventCounts,
                int stateSampleCount,
                int intruderSampleCount,
                String approach,
                Double resetTimeS,
                Double startTimeS,
                Double taskAudioStartTimeS,
                Double taskAudioEndTimeS,
                Double spawnTimeS,
                Double visualOpportunityTimeS,
                Double detectabilityTimeS,
                Double plannedAudioToSpawnS,
                Boolean audioSpawnTimingValid,
                String spawnGateMethod,
                Double spawnTimeoutAudioToSpawnS,
                Boolean speedStableBeforeSpawn,
                Double speedErrorAtSpawnKias,
                Double speedRateAtSpawnKiasS,
                Boolean speedRateValidAtSpawn,
                Double speedStabilityDurationS,
                Double speedGatePassAudioElapsedS,
                Double spawnWaitAfterCandidateS,
                Boolean spawnForcedByTimeout,
                Double targetSpeedKias,
                Double iasAtAudioStartKias,
                Double iasAtSpawnKias,
                Double iasAtVisualOpportunityKias,
                String visualOpportunityMethod,
                Boolean visualOpportunityOperationalProxy,
                Boolean visualOpportunityRecognitionClaim,
                Boolean visualOpportunityLightsWriteOk,
                Double visualOpportunityMinimumSpanPx,
                Double visualOpportunityEstimatedSpanPx,
                Double visualOpportunityScreenXPx,
                Double visualOpportunityScreenYPx,
                Double visualOpportunitySlantDistanceM,
                Double visualOpportunityHorizontalDistanceM,
                Double visualOpportunityRelativeBearingDeg,
                Double visualOpportunityRelativeElevationDeg,
                Double iasAtDetectabilityKias,
                String detectabilityMethod,
                Boolean detectabilityOperationalProxy,
                Double detectabilityThresholdPx,
                Double detectabilityEstimatedSpanPx,
                Double detectabilityScreenXPx,
                Double detectabilityScreenYPx,
                Double detectabilitySlantDistanceM,
                Double detectabilityHorizontalDistanceM,
                Double detectabilityRelativeBearingDeg,
                Double detectabilityRelativeElevationDeg,
                Double spawnLatitudeDeg,
                Double spawnLongitudeDeg,
                Double spawnElevationM,
                Double spawnAglM,
                Double spawnHeadingDeg,
                Double spawnPitchDeg,
                Double spawnRollDeg,
                Double spawnPRate,
                Double spawnQRate,
                Double spawnRRate,
                Double spawnTasMps,
                Double spawnVerticalSpeedMps,
                Double spawnPitchInput,
                Double spawnRollInput,
                Double spawnYawInput,
                Double spawnThrottleInput,
                Double preSpawnMeanAbsSpeedErrorKias,
                Double preSpawnWithin5KiasRate,
                Double soundMasterRatio,
                Double soundInteriorRatio,
                Double soundEngineRatio,
                Double soundPropRatio,
                Double soundEnviroRatio,
                Double soundRadioRatio,
                String responseAnchor,
                String responseMethod,
                String responseTriggerAxis,
                Boolean responseBaselineValid,
                Boolean activeControlAtSpawn,
                Boolean activeControlAtAnchor,
                Boolean activeControlAtDetectability,
                Boolean activeControlAtVisualOpportunity,
                Integer responseBaselineSamples,
                Double responseBaselineWindowS,
                Double responsePersistenceS,
                Double hazardTimeS,
                Double advisoryTimeS,
                Double responseStartTimeS,
                Double hazardClearTimeS,
                Double advisoryClearTimeS,
                Double responseEndTimeS,
                Double trialEndTimeS,
                Double minHorizontalDistance,
                Double minVerticalSeparation,
                Double minDistanceSimTimeS,
                Double maxAbsPitchInput,
                Double maxAbsRollInput,
                Double maxAbsYawInput,
                Double maxAbsThrottleInput,
                String firstControlAxis
        ) {
            this.trialId = trialId;
            this.clean = clean;
            this.incompleteReason = incompleteReason;
            this.eventCounts = eventCounts;
            this.stateSampleCount = stateSampleCount;
            this.intruderSampleCount = intruderSampleCount;
            this.approach = approach;
            this.resetTimeS = resetTimeS;
            this.startTimeS = startTimeS;
            this.taskAudioStartTimeS = taskAudioStartTimeS;
            this.taskAudioEndTimeS = taskAudioEndTimeS;
            this.spawnTimeS = spawnTimeS;
            this.visualOpportunityTimeS = visualOpportunityTimeS;
            this.detectabilityTimeS = detectabilityTimeS;
            this.plannedAudioToSpawnS = plannedAudioToSpawnS;
            this.audioSpawnTimingValid = audioSpawnTimingValid;
            this.spawnGateMethod = spawnGateMethod;
            this.spawnTimeoutAudioToSpawnS = spawnTimeoutAudioToSpawnS;
            this.speedStableBeforeSpawn = speedStableBeforeSpawn;
            this.speedErrorAtSpawnKias = speedErrorAtSpawnKias;
            this.speedRateAtSpawnKiasS = speedRateAtSpawnKiasS;
            this.speedRateValidAtSpawn = speedRateValidAtSpawn;
            this.speedStabilityDurationS = speedStabilityDurationS;
            this.speedGatePassAudioElapsedS = speedGatePassAudioElapsedS;
            this.spawnWaitAfterCandidateS = spawnWaitAfterCandidateS;
            this.spawnForcedByTimeout = spawnForcedByTimeout;
            this.targetSpeedKias = targetSpeedKias;
            this.iasAtAudioStartKias = iasAtAudioStartKias;
            this.iasAtSpawnKias = iasAtSpawnKias;
            this.iasAtVisualOpportunityKias = iasAtVisualOpportunityKias;
            this.visualOpportunityMethod = visualOpportunityMethod;
            this.visualOpportunityOperationalProxy = visualOpportunityOperationalProxy;
            this.visualOpportunityRecognitionClaim = visualOpportunityRecognitionClaim;
            this.visualOpportunityLightsWriteOk = visualOpportunityLightsWriteOk;
            this.visualOpportunityMinimumSpanPx = visualOpportunityMinimumSpanPx;
            this.visualOpportunityEstimatedSpanPx = visualOpportunityEstimatedSpanPx;
            this.visualOpportunityScreenXPx = visualOpportunityScreenXPx;
            this.visualOpportunityScreenYPx = visualOpportunityScreenYPx;
            this.visualOpportunitySlantDistanceM = visualOpportunitySlantDistanceM;
            this.visualOpportunityHorizontalDistanceM = visualOpportunityHorizontalDistanceM;
            this.visualOpportunityRelativeBearingDeg = visualOpportunityRelativeBearingDeg;
            this.visualOpportunityRelativeElevationDeg = visualOpportunityRelativeElevationDeg;
            this.iasAtDetectabilityKias = iasAtDetectabilityKias;
            this.detectabilityMethod = detectabilityMethod;
            this.detectabilityOperationalProxy = detectabilityOperationalProxy;
            this.detectabilityThresholdPx = detectabilityThresholdPx;
            this.detectabilityEstimatedSpanPx = detectabilityEstimatedSpanPx;
            this.detectabilityScreenXPx = detectabilityScreenXPx;
            this.detectabilityScreenYPx = detectabilityScreenYPx;
            this.detectabilitySlantDistanceM = detectabilitySlantDistanceM;
            this.detectabilityHorizontalDistanceM = detectabilityHorizontalDistanceM;
            this.detectabilityRelativeBearingDeg = detectabilityRelativeBearingDeg;
            this.detectabilityRelativeElevationDeg = detectabilityRelativeElevationDeg;
            this.spawnLatitudeDeg = spawnLatitudeDeg;
            this.spawnLongitudeDeg = spawnLongitudeDeg;
            this.spawnElevationM = spawnElevationM;
            this.spawnAglM = spawnAglM;
            this.spawnHeadingDeg = spawnHeadingDeg;
            this.spawnPitchDeg = spawnPitchDeg;
            this.spawnRollDeg = spawnRollDeg;
            this.spawnPRate = spawnPRate;
            this.spawnQRate = spawnQRate;
            this.spawnRRate = spawnRRate;
            this.spawnTasMps = spawnTasMps;
            this.spawnVerticalSpeedMps = spawnVerticalSpeedMps;
            this.spawnPitchInput = spawnPitchInput;
            this.spawnRollInput = spawnRollInput;
            this.spawnYawInput = spawnYawInput;
            this.spawnThrottleInput = spawnThrottleInput;
            this.preSpawnMeanAbsSpeedErrorKias = preSpawnMeanAbsSpeedErrorKias;
            this.preSpawnWithin5KiasRate = preSpawnWithin5KiasRate;
            this.soundMasterRatio = soundMasterRatio;
            this.soundInteriorRatio = soundInteriorRatio;
            this.soundEngineRatio = soundEngineRatio;
            this.soundPropRatio = soundPropRatio;
            this.soundEnviroRatio = soundEnviroRatio;
            this.soundRadioRatio = soundRadioRatio;
            this.responseAnchor = responseAnchor;
            this.responseMethod = responseMethod;
            this.responseTriggerAxis = responseTriggerAxis;
            this.responseBaselineValid = responseBaselineValid;
            this.activeControlAtSpawn = activeControlAtSpawn;
            this.activeControlAtAnchor = activeControlAtAnchor;
            this.activeControlAtDetectability = activeControlAtDetectability;
            this.activeControlAtVisualOpportunity = activeControlAtVisualOpportunity;
            this.responseBaselineSamples = responseBaselineSamples;
            this.responseBaselineWindowS = responseBaselineWindowS;
            this.responsePersistenceS = responsePersistenceS;
            this.hazardTimeS = hazardTimeS;
            this.advisoryTimeS = advisoryTimeS;
            this.responseStartTimeS = responseStartTimeS;
            this.hazardClearTimeS = hazardClearTimeS;
            this.advisoryClearTimeS = advisoryClearTimeS;
            this.responseEndTimeS = responseEndTimeS;
            this.trialEndTimeS = trialEndTimeS;
            this.minHorizontalDistance = minHorizontalDistance;
            this.minVerticalSeparation = minVerticalSeparation;
            this.minDistanceSimTimeS = minDistanceSimTimeS;
            this.maxAbsPitchInput = maxAbsPitchInput;
            this.maxAbsRollInput = maxAbsRollInput;
            this.maxAbsYawInput = maxAbsYawInput;
            this.maxAbsThrottleInput = maxAbsThrottleInput;
            this.firstControlAxis = firstControlAxis;
        }
    }
}
