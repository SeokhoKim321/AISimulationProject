package com.example.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class XPlaneBatchAnalysisMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java com.example.ai.XPlaneBatchAnalysisMain <logs_dir_or_state_csv> [state_csv...]");
            return;
        }

        List<Path> stateCsvPaths = resolveStateCsvPaths(args);
        if (stateCsvPaths.isEmpty()) {
            System.out.println("No state CSV files found.");
            return;
        }

        List<SessionResult> sessionResults = new ArrayList<>();
        for (Path stateCsv : stateCsvPaths) {
            SessionPaths paths = SessionPaths.fromStateCsv(stateCsv);
            XPlaneSessionAnalysisMain.XPlaneSessionSummary summary =
                    XPlaneSessionAnalysisMain.analyze(paths.stateCsv, paths.intruderCsv, paths.eventCsv);
            sessionResults.add(new SessionResult(paths, summary));
        }

        List<CleanTrialResult> cleanTrialResults = printBatchSummary(sessionResults);
        ExportPaths exportPaths = writeCsvExports(sessionResults, cleanTrialResults, determineOutputDir(args, stateCsvPaths));
        System.out.println();
        System.out.println("CSV exports");
        System.out.println("  Sessions       : " + exportPaths.sessionsCsv);
        System.out.println("  Trials         : " + exportPaths.trialsCsv);
        System.out.println("  Clean trials   : " + exportPaths.cleanTrialsCsv);
    }

    private static List<Path> resolveStateCsvPaths(String[] args) throws IOException {
        List<Path> stateCsvPaths = new ArrayList<>();
        for (String arg : args) {
            Path path = Paths.get(arg).toAbsolutePath();
            if (Files.isDirectory(path)) {
                try (Stream<Path> files = Files.list(path)) {
                    files.filter(Files::isRegularFile)
                            .filter(XPlaneBatchAnalysisMain::isCompatibleStateCsv)
                            .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                            .forEach(stateCsvPaths::add);
                }
            } else if (isCompatibleStateCsv(path)) {
                stateCsvPaths.add(path);
            }
        }
        return stateCsvPaths;
    }

    private static boolean isStateCsv(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith(".csv")
                && !fileName.endsWith("_intruder.csv")
                && !fileName.endsWith("_events.csv")
                && !fileName.contains("_batch_analysis_");
    }

    private static boolean isCompatibleStateCsv(Path path) {
        if (!isStateCsv(path)) {
            return false;
        }
        try {
            String header = Files.readString(path);
            int lineBreak = header.indexOf('\n');
            if (lineBreak >= 0) {
                header = header.substring(0, lineBreak);
            }
            return header.contains("trial_id")
                    && header.contains("pitch_input")
                    && header.contains("roll_input")
                    && header.contains("yaw_input")
                    && header.contains("throttle_input");
        } catch (IOException ignored) {
            return false;
        }
    }

    private static Path determineOutputDir(String[] args, List<Path> stateCsvPaths) {
        Path firstArg = Paths.get(args[0]).toAbsolutePath();
        if (Files.isDirectory(firstArg)) {
            return firstArg;
        }
        return stateCsvPaths.get(0).toAbsolutePath().getParent();
    }

    private static List<CleanTrialResult> printBatchSummary(List<SessionResult> sessionResults) {
        int totalSessions = sessionResults.size();
        int totalTrials = 0;
        int cleanTrials = 0;
        int incompleteTrials = 0;

        List<CleanTrialResult> cleanTrialResults = new ArrayList<>();

        System.out.println("Batch analysis");
        System.out.println("Sessions         : " + totalSessions);
        System.out.println();

        for (SessionResult result : sessionResults) {
            XPlaneSessionAnalysisMain.XPlaneSessionSummary summary = result.summary;
            int sessionTotal = summary.trials.size();
            int sessionClean = countClean(summary.trials);
            int sessionIncomplete = sessionTotal - sessionClean;

            totalTrials += sessionTotal;
            cleanTrials += sessionClean;
            incompleteTrials += sessionIncomplete;

            System.out.println(summary.sessionId);
            System.out.println("  State CSV      : " + result.paths.stateCsv);
            System.out.println("  Trials         : total=" + sessionTotal
                    + ", clean=" + sessionClean
                    + ", incomplete=" + sessionIncomplete
                    + ", clean_rate=" + formatPercent(sessionClean, sessionTotal));
            System.out.println("  Events         : hazard=" + summary.hazardDetectedCount
                    + "/" + summary.hazardClearedCount
                    + ", advisory=" + summary.advisoryShownCount
                    + "/" + summary.advisoryClearedCount
                    + ", response=" + summary.responseStartCount
                    + "/" + summary.responseEndCount);
            System.out.println("  Min distance   : horiz=" + formatNullable(summary.minHorizontalDistance)
                    + ", vert=" + formatNullable(summary.minVerticalSeparation));

            for (XPlaneSessionAnalysisMain.XPlaneTrialSummary trial : summary.trials) {
                if (trial.clean) {
                    cleanTrialResults.add(new CleanTrialResult(summary.sessionId, trial));
                }
            }
        }

        System.out.println();
        System.out.println("Combined");
        System.out.println("  Trials         : total=" + totalTrials
                + ", clean=" + cleanTrials
                + ", incomplete=" + incompleteTrials
                + ", clean_rate=" + formatPercent(cleanTrials, totalTrials));
        System.out.println("  Clean averages : spawn->hazard=" + formatAverage(cleanTrialResults, Metric.SPAWN_TO_HAZARD)
                + ", advisory->response=" + formatAverage(cleanTrialResults, Metric.ADVISORY_TO_RESPONSE)
                + ", hazard_window=" + formatAverage(cleanTrialResults, Metric.HAZARD_WINDOW));
        System.out.println("  Clean min avg  : horiz=" + formatAverage(cleanTrialResults, Metric.MIN_HORIZONTAL)
                + ", vert=" + formatAverage(cleanTrialResults, Metric.MIN_VERTICAL));

        printCleanTrialTable(cleanTrialResults);
        return cleanTrialResults;
    }

    private static ExportPaths writeCsvExports(
            List<SessionResult> sessionResults,
            List<CleanTrialResult> cleanTrialResults,
            Path outputDir
    ) throws IOException {
        Files.createDirectories(outputDir);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path sessionsCsv = outputDir.resolve("xplane_batch_analysis_" + timestamp + "_sessions.csv");
        Path trialsCsv = outputDir.resolve("xplane_batch_analysis_" + timestamp + "_trials.csv");
        Path cleanTrialsCsv = outputDir.resolve("xplane_batch_analysis_" + timestamp + "_clean_trials.csv");

        writeSessionSummaryCsv(sessionResults, sessionsCsv);
        writeTrialSummaryCsv(sessionResults, trialsCsv, false);
        writeCleanTrialSummaryCsv(cleanTrialResults, cleanTrialsCsv);

        return new ExportPaths(sessionsCsv, trialsCsv, cleanTrialsCsv);
    }

    private static void writeSessionSummaryCsv(List<SessionResult> sessionResults, Path outputPath) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",",
                "session_id",
                "state_csv",
                "total_trials",
                "clean_trials",
                "incomplete_trials",
                "clean_rate_pct",
                "hazard_detected",
                "hazard_cleared",
                "advisory_shown",
                "advisory_cleared",
                "response_start",
                "response_end",
                "min_horizontal_m",
                "min_vertical_m",
                "state_samples",
                "intruder_samples",
                "state_start_time_s",
                "state_end_time_s"));

        for (SessionResult result : sessionResults) {
            XPlaneSessionAnalysisMain.XPlaneSessionSummary summary = result.summary;
            int totalTrials = summary.trials.size();
            int cleanTrials = countClean(summary.trials);
            int incompleteTrials = totalTrials - cleanTrials;
            lines.add(csvLine(
                    summary.sessionId,
                    result.paths.stateCsv.toString(),
                    totalTrials,
                    cleanTrials,
                    incompleteTrials,
                    percentValue(cleanTrials, totalTrials),
                    summary.hazardDetectedCount,
                    summary.hazardClearedCount,
                    summary.advisoryShownCount,
                    summary.advisoryClearedCount,
                    summary.responseStartCount,
                    summary.responseEndCount,
                    summary.minHorizontalDistance,
                    summary.minVerticalSeparation,
                    summary.stateSampleCount,
                    summary.intruderSampleCount,
                    summary.stateStartTimeS,
                    summary.stateEndTimeS));
        }

        Files.write(outputPath, lines, StandardCharsets.UTF_8);
    }

    private static void writeTrialSummaryCsv(
            List<SessionResult> sessionResults,
            Path outputPath,
            boolean cleanOnly
    ) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(trialHeader());
        for (SessionResult result : sessionResults) {
            for (XPlaneSessionAnalysisMain.XPlaneTrialSummary trial : result.summary.trials) {
                if (!cleanOnly || trial.clean) {
                    lines.add(trialCsvLine(result.summary.sessionId, trial));
                }
            }
        }
        Files.write(outputPath, lines, StandardCharsets.UTF_8);
    }

    private static void writeCleanTrialSummaryCsv(List<CleanTrialResult> cleanTrialResults, Path outputPath) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(trialHeader());
        for (CleanTrialResult result : cleanTrialResults) {
            lines.add(trialCsvLine(result.sessionId, result.trial));
        }
        Files.write(outputPath, lines, StandardCharsets.UTF_8);
    }

    private static String trialHeader() {
        return String.join(",",
                "session_id",
                "trial_id",
                "clean",
                "incomplete_reason",
                "approach",
                "state_samples",
                "intruder_samples",
                "reset_time_s",
                "start_time_s",
                "task_audio_start_time_s",
                "task_audio_end_time_s",
                "spawn_time_s",
                "visual_opportunity_time_s",
                "detectability_time_s",
                "hazard_time_s",
                "advisory_time_s",
                "response_start_time_s",
                "hazard_clear_time_s",
                "advisory_clear_time_s",
                "response_end_time_s",
                "trial_end_time_s",
                "start_to_task_audio_s",
                "task_audio_duration_s",
                "planned_task_audio_to_spawn_s",
                "task_audio_start_to_spawn_s",
                "task_audio_spawn_timing_valid",
                "spawn_gate_method",
                "spawn_timeout_audio_to_spawn_s",
                "speed_stable_before_spawn",
                "speed_error_at_spawn_kias",
                "speed_rate_at_spawn_kias_s",
                "speed_rate_valid_at_spawn",
                "speed_stability_duration_s",
                "speed_gate_pass_audio_elapsed_s",
                "spawn_wait_after_candidate_s",
                "spawn_forced_by_timeout",
                "target_speed_kias",
                "ias_at_task_audio_start_kias",
                "ias_at_spawn_kias",
                "ias_at_visual_opportunity_kias",
                "visual_opportunity_method",
                "visual_opportunity_operational_proxy",
                "visual_opportunity_recognition_claim",
                "visual_opportunity_lights_write_ok",
                "visual_opportunity_minimum_span_px",
                "visual_opportunity_estimated_span_px",
                "visual_opportunity_screen_x_px",
                "visual_opportunity_screen_y_px",
                "visual_opportunity_slant_distance_m",
                "visual_opportunity_horizontal_distance_m",
                "visual_opportunity_relative_bearing_deg",
                "visual_opportunity_relative_elevation_deg",
                "ias_at_detectability_kias",
                "detectability_method",
                "detectability_operational_proxy",
                "detectability_threshold_px",
                "detectability_estimated_span_px",
                "detectability_screen_x_px",
                "detectability_screen_y_px",
                "detectability_slant_distance_m",
                "detectability_horizontal_distance_m",
                "detectability_relative_bearing_deg",
                "detectability_relative_elevation_deg",
                "spawn_latitude_deg",
                "spawn_longitude_deg",
                "spawn_elevation_m",
                "spawn_agl_m",
                "spawn_heading_deg",
                "spawn_pitch_deg",
                "spawn_roll_deg",
                "spawn_p_rate",
                "spawn_q_rate",
                "spawn_r_rate",
                "spawn_tas_mps",
                "spawn_vertical_speed_mps",
                "spawn_pitch_input",
                "spawn_roll_input",
                "spawn_yaw_input",
                "spawn_throttle_input",
                "pre_spawn_mean_abs_speed_error_kias",
                "pre_spawn_within_5kias_rate",
                "sound_master_ratio",
                "sound_interior_ratio",
                "sound_engine_ratio",
                "sound_prop_ratio",
                "sound_enviro_ratio",
                "sound_radio_ratio",
                "response_anchor",
                "response_method",
                "response_baseline_valid",
                "response_baseline_samples",
                "response_baseline_window_s",
                "active_control_at_spawn",
                "active_control_at_anchor",
                "active_control_at_detectability",
                "active_control_at_visual_opportunity",
                "response_trigger_axis",
                "response_persistence_s",
                "spawn_to_visual_opportunity_s",
                "visual_opportunity_to_response_s",
                "task_audio_start_to_detectability_s",
                "spawn_to_detectability_s",
                "detectability_to_response_s",
                "spawn_to_response_s",
                "spawn_to_hazard_s",
                "advisory_to_response_s",
                "hazard_window_s",
                "response_duration_s",
                "min_horizontal_m",
                "min_vertical_m",
                "min_distance_time_s",
                "max_pitch_input",
                "max_roll_input",
                "max_yaw_input",
                "max_throttle_input",
                "first_response_axis",
                "event_counts");
    }

    private static String trialCsvLine(String sessionId, XPlaneSessionAnalysisMain.XPlaneTrialSummary trial) {
        return csvLine(
                sessionId,
                trial.trialId,
                trial.clean,
                trial.incompleteReason,
                blankToUnknown(trial.approach),
                trial.stateSampleCount,
                trial.intruderSampleCount,
                trial.resetTimeS,
                trial.startTimeS,
                trial.taskAudioStartTimeS,
                trial.taskAudioEndTimeS,
                trial.spawnTimeS,
                trial.visualOpportunityTimeS,
                trial.detectabilityTimeS,
                trial.hazardTimeS,
                trial.advisoryTimeS,
                trial.responseStartTimeS,
                trial.hazardClearTimeS,
                trial.advisoryClearTimeS,
                trial.responseEndTimeS,
                trial.trialEndTimeS,
                deltaValue(trial.startTimeS, trial.taskAudioStartTimeS),
                deltaValue(trial.taskAudioStartTimeS, trial.taskAudioEndTimeS),
                trial.plannedAudioToSpawnS,
                deltaValue(trial.taskAudioStartTimeS, trial.spawnTimeS),
                trial.audioSpawnTimingValid,
                trial.spawnGateMethod,
                trial.spawnTimeoutAudioToSpawnS,
                trial.speedStableBeforeSpawn,
                trial.speedErrorAtSpawnKias,
                trial.speedRateAtSpawnKiasS,
                trial.speedRateValidAtSpawn,
                trial.speedStabilityDurationS,
                trial.speedGatePassAudioElapsedS,
                trial.spawnWaitAfterCandidateS,
                trial.spawnForcedByTimeout,
                trial.targetSpeedKias,
                trial.iasAtAudioStartKias,
                trial.iasAtSpawnKias,
                trial.iasAtVisualOpportunityKias,
                trial.visualOpportunityMethod,
                trial.visualOpportunityOperationalProxy,
                trial.visualOpportunityRecognitionClaim,
                trial.visualOpportunityLightsWriteOk,
                trial.visualOpportunityMinimumSpanPx,
                trial.visualOpportunityEstimatedSpanPx,
                trial.visualOpportunityScreenXPx,
                trial.visualOpportunityScreenYPx,
                trial.visualOpportunitySlantDistanceM,
                trial.visualOpportunityHorizontalDistanceM,
                trial.visualOpportunityRelativeBearingDeg,
                trial.visualOpportunityRelativeElevationDeg,
                trial.iasAtDetectabilityKias,
                trial.detectabilityMethod,
                trial.detectabilityOperationalProxy,
                trial.detectabilityThresholdPx,
                trial.detectabilityEstimatedSpanPx,
                trial.detectabilityScreenXPx,
                trial.detectabilityScreenYPx,
                trial.detectabilitySlantDistanceM,
                trial.detectabilityHorizontalDistanceM,
                trial.detectabilityRelativeBearingDeg,
                trial.detectabilityRelativeElevationDeg,
                trial.spawnLatitudeDeg,
                trial.spawnLongitudeDeg,
                trial.spawnElevationM,
                trial.spawnAglM,
                trial.spawnHeadingDeg,
                trial.spawnPitchDeg,
                trial.spawnRollDeg,
                trial.spawnPRate,
                trial.spawnQRate,
                trial.spawnRRate,
                trial.spawnTasMps,
                trial.spawnVerticalSpeedMps,
                trial.spawnPitchInput,
                trial.spawnRollInput,
                trial.spawnYawInput,
                trial.spawnThrottleInput,
                trial.preSpawnMeanAbsSpeedErrorKias,
                trial.preSpawnWithin5KiasRate,
                trial.soundMasterRatio,
                trial.soundInteriorRatio,
                trial.soundEngineRatio,
                trial.soundPropRatio,
                trial.soundEnviroRatio,
                trial.soundRadioRatio,
                trial.responseAnchor,
                trial.responseMethod,
                trial.responseBaselineValid,
                trial.responseBaselineSamples,
                trial.responseBaselineWindowS,
                trial.activeControlAtSpawn,
                trial.activeControlAtAnchor,
                trial.activeControlAtDetectability,
                trial.activeControlAtVisualOpportunity,
                trial.responseTriggerAxis,
                trial.responsePersistenceS,
                deltaValue(trial.spawnTimeS, trial.visualOpportunityTimeS),
                orderedDeltaValue(trial.visualOpportunityTimeS, trial.responseStartTimeS),
                deltaValue(trial.taskAudioStartTimeS, trial.detectabilityTimeS),
                deltaValue(trial.spawnTimeS, trial.detectabilityTimeS),
                orderedDeltaValue(trial.detectabilityTimeS, trial.responseStartTimeS),
                deltaValue(trial.spawnTimeS, trial.responseStartTimeS),
                deltaValue(trial.spawnTimeS, trial.hazardTimeS),
                deltaValue(trial.advisoryTimeS, trial.responseStartTimeS),
                deltaValue(trial.hazardTimeS, trial.hazardClearTimeS),
                deltaValue(trial.responseStartTimeS, trial.responseEndTimeS),
                trial.minHorizontalDistance,
                trial.minVerticalSeparation,
                trial.minDistanceSimTimeS,
                trial.maxAbsPitchInput,
                trial.maxAbsRollInput,
                trial.maxAbsYawInput,
                trial.maxAbsThrottleInput,
                trial.firstControlAxis,
                trial.eventCounts);
    }

    private static void printCleanTrialTable(List<CleanTrialResult> cleanTrialResults) {
        System.out.println();
        System.out.println("Clean trial table");
        if (cleanTrialResults.isEmpty()) {
            System.out.println("  none");
            return;
        }

        System.out.println("  session_id,trial_id,approach,min_horizontal_m,min_vertical_m,spawn_to_hazard_s,advisory_to_response_s,max_pitch,max_roll,first_axis");
        for (CleanTrialResult result : cleanTrialResults) {
            XPlaneSessionAnalysisMain.XPlaneTrialSummary trial = result.trial;
            System.out.println("  " + result.sessionId
                    + "," + trial.trialId
                    + "," + blankToUnknown(trial.approach)
                    + "," + formatNullable(trial.minHorizontalDistance)
                    + "," + formatNullable(trial.minVerticalSeparation)
                    + "," + formatDeltaValue(trial.spawnTimeS, trial.hazardTimeS)
                    + "," + formatDeltaValue(trial.advisoryTimeS, trial.responseStartTimeS)
                    + "," + formatNullable(trial.maxAbsPitchInput)
                    + "," + formatNullable(trial.maxAbsRollInput)
                    + "," + trial.firstControlAxis);
        }
    }

    private static int countClean(List<XPlaneSessionAnalysisMain.XPlaneTrialSummary> trials) {
        int count = 0;
        for (XPlaneSessionAnalysisMain.XPlaneTrialSummary trial : trials) {
            if (trial.clean) {
                count++;
            }
        }
        return count;
    }

    private static String formatAverage(List<CleanTrialResult> results, Metric metric) {
        double sum = 0.0;
        int count = 0;
        for (CleanTrialResult result : results) {
            Double value = metric.value(result.trial);
            if (value != null && !value.isNaN()) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? "n/a" : String.format("%.3f", sum / count);
    }

    private static String formatDeltaValue(Double start, Double end) {
        if (start == null || end == null) {
            return "n/a";
        }
        return formatNullable(end - start);
    }

    private static Double deltaValue(Double start, Double end) {
        if (start == null || end == null) {
            return null;
        }
        return end - start;
    }

    private static Double orderedDeltaValue(Double start, Double end) {
        if (start == null || end == null || end < start) {
            return null;
        }
        return end - start;
    }

    private static String formatPercent(int numerator, int denominator) {
        if (denominator == 0) {
            return "n/a";
        }
        return String.format("%.1f%%", numerator * 100.0 / denominator);
    }

    private static Double percentValue(int numerator, int denominator) {
        if (denominator == 0) {
            return null;
        }
        return numerator * 100.0 / denominator;
    }

    private static String formatNullable(Double value) {
        return value == null || value.isNaN() ? "n/a" : String.format("%.3f", value);
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String csvLine(Object... values) {
        List<String> columns = new ArrayList<>();
        for (Object value : values) {
            columns.add(csvValue(value));
        }
        return String.join(",", columns);
    }

    private static String csvValue(Object value) {
        if (value == null) {
            return "";
        }

        String text;
        if (value instanceof Double doubleValue) {
            text = formatNullable(doubleValue);
        } else {
            text = value.toString();
        }

        boolean needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (needsQuotes) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private enum Metric {
        SPAWN_TO_HAZARD {
            @Override
            Double value(XPlaneSessionAnalysisMain.XPlaneTrialSummary trial) {
                return delta(trial.spawnTimeS, trial.hazardTimeS);
            }
        },
        ADVISORY_TO_RESPONSE {
            @Override
            Double value(XPlaneSessionAnalysisMain.XPlaneTrialSummary trial) {
                return delta(trial.advisoryTimeS, trial.responseStartTimeS);
            }
        },
        HAZARD_WINDOW {
            @Override
            Double value(XPlaneSessionAnalysisMain.XPlaneTrialSummary trial) {
                return delta(trial.hazardTimeS, trial.hazardClearTimeS);
            }
        },
        MIN_HORIZONTAL {
            @Override
            Double value(XPlaneSessionAnalysisMain.XPlaneTrialSummary trial) {
                return trial.minHorizontalDistance;
            }
        },
        MIN_VERTICAL {
            @Override
            Double value(XPlaneSessionAnalysisMain.XPlaneTrialSummary trial) {
                return trial.minVerticalSeparation;
            }
        };

        abstract Double value(XPlaneSessionAnalysisMain.XPlaneTrialSummary trial);

        static Double delta(Double start, Double end) {
            if (start == null || end == null) {
                return null;
            }
            return end - start;
        }
    }

    private static class SessionPaths {
        final Path stateCsv;
        final Path intruderCsv;
        final Path eventCsv;

        private SessionPaths(Path stateCsv, Path intruderCsv, Path eventCsv) {
            this.stateCsv = stateCsv;
            this.intruderCsv = intruderCsv;
            this.eventCsv = eventCsv;
        }

        static SessionPaths fromStateCsv(Path stateCsv) {
            Path absoluteStateCsv = stateCsv.toAbsolutePath();
            String stateFileName = absoluteStateCsv.getFileName().toString();
            Path sessionDir = absoluteStateCsv.getParent();
            Path intruderCsv = sessionDir.resolve(stateFileName.replace(".csv", "_intruder.csv"));
            Path eventCsv = sessionDir.resolve(stateFileName.replace(".csv", "_events.csv"));
            return new SessionPaths(absoluteStateCsv, intruderCsv, eventCsv);
        }
    }

    private static class SessionResult {
        final SessionPaths paths;
        final XPlaneSessionAnalysisMain.XPlaneSessionSummary summary;

        private SessionResult(SessionPaths paths, XPlaneSessionAnalysisMain.XPlaneSessionSummary summary) {
            this.paths = paths;
            this.summary = summary;
        }
    }

    private static class ExportPaths {
        final Path sessionsCsv;
        final Path trialsCsv;
        final Path cleanTrialsCsv;

        private ExportPaths(Path sessionsCsv, Path trialsCsv, Path cleanTrialsCsv) {
            this.sessionsCsv = sessionsCsv;
            this.trialsCsv = trialsCsv;
            this.cleanTrialsCsv = cleanTrialsCsv;
        }
    }

    private static class CleanTrialResult {
        final String sessionId;
        final XPlaneSessionAnalysisMain.XPlaneTrialSummary trial;

        private CleanTrialResult(String sessionId, XPlaneSessionAnalysisMain.XPlaneTrialSummary trial) {
            this.sessionId = sessionId;
            this.trial = trial;
        }
    }
}
