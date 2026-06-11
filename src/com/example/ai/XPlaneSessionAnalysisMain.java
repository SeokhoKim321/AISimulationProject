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
            "FINAL_APPROACH_GATE_ENTERED",
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
        List<String[]> intruderRows = readCsv(intruderCsv);
        List<String[]> stateRows = readCsv(stateCsv);

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

        double minHorizontalDistance = Double.NaN;
        double minVerticalSeparation = Double.NaN;
        Double minDistanceSimTime = null;

        for (String[] row : intruderRows) {
            double horizontalDistance = parseDouble(valueAt(row, 10));
            double verticalSeparation = parseDouble(valueAt(row, 11));
            double simTime = parseDouble(valueAt(row, 3));

            if (Double.isNaN(minHorizontalDistance) || horizontalDistance < minHorizontalDistance) {
                minHorizontalDistance = horizontalDistance;
                minVerticalSeparation = verticalSeparation;
                minDistanceSimTime = simTime;
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
                analyzeTrials(stateRows, intruderRows, eventRows)
        );
    }

    private static List<XPlaneTrialSummary> analyzeTrials(
            List<String[]> stateRows,
            List<String[]> intruderRows,
            List<String[]> eventRows
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

            List<String[]> trialStates = statesByTrial.getOrDefault(trialId, List.of());
            List<String[]> trialIntruders = intrudersByTrial.getOrDefault(trialId, List.of());
            List<String[]> trialEvents = eventsByTrial.getOrDefault(trialId, List.of());
            Map<String, Integer> eventCounts = eventCounts(trialEvents);

            double minHorizontalDistance = Double.NaN;
            double minVerticalSeparation = Double.NaN;
            Double minDistanceSimTime = null;
            for (String[] row : trialIntruders) {
                double horizontalDistance = parseDouble(valueAt(row, 10));
                double verticalSeparation = parseDouble(valueAt(row, 11));
                double simTime = parseDouble(valueAt(row, 3));

                if (Double.isNaN(minHorizontalDistance) || horizontalDistance < minHorizontalDistance) {
                    minHorizontalDistance = horizontalDistance;
                    minVerticalSeparation = verticalSeparation;
                    minDistanceSimTime = simTime;
                }
            }

            String spawnDetail = firstEventDetail(trialEvents, "INTRUDER_SPAWNED");
            Integer advisoryStateSampleIndex = firstEventStateSampleIndex(trialEvents, "ADVISORY_SHOWN");
            Integer responseStartStateSampleIndex = firstEventStateSampleIndex(trialEvents, "PILOT_RESPONSE_START");

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
                    firstEventTime(trialEvents, "TRIAL_START"),
                    firstEventTime(trialEvents, "INTRUDER_SPAWNED"),
                    firstEventTime(trialEvents, "HAZARD_DETECTED"),
                    firstEventTime(trialEvents, "ADVISORY_SHOWN"),
                    firstEventTime(trialEvents, "PILOT_RESPONSE_START"),
                    firstEventTime(trialEvents, "HAZARD_CLEARED"),
                    firstEventTime(trialEvents, "ADVISORY_CLEARED"),
                    firstEventTime(trialEvents, "PILOT_RESPONSE_END"),
                    firstEventTime(trialEvents, "TRIAL_END"),
                    minHorizontalDistance,
                    minVerticalSeparation,
                    minDistanceSimTime,
                    maxAbs(trialStates, 23),
                    maxAbs(trialStates, 24),
                    maxAbs(trialStates, 25),
                    maxAbs(trialStates, 26),
                    firstControlAxisAtResponse(trialStates, advisoryStateSampleIndex, responseStartStateSampleIndex)
            ));
        }

        return trials;
    }

    private static List<String[]> readCsv(Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        if (!Files.exists(path)) {
            return rows;
        }

        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                rows.add(parseCsvLine(line));
            }
        }
        return rows;
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
            System.out.println("Advisory->Response Start : " + formatNullable(summary.firstResponseStartTimeS - summary.firstAdvisoryTimeS) + " s");
        }
        if (summary.firstHazardTimeS != null && summary.firstResponseStartTimeS != null) {
            System.out.println("Hazard->Response Start   : " + formatNullable(summary.firstResponseStartTimeS - summary.firstHazardTimeS) + " s");
        }
        if (summary.firstHazardTimeS != null && summary.firstAdvisoryTimeS != null) {
            System.out.println("Hazard->Advisory         : " + formatNullable(summary.firstAdvisoryTimeS - summary.firstHazardTimeS) + " s");
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
                    + ", spawn=" + formatNullable(trial.spawnTimeS)
                    + ", hazard=" + formatNullable(trial.hazardTimeS)
                    + ", advisory=" + formatNullable(trial.advisoryTimeS)
                    + ", response=" + formatNullable(trial.responseStartTimeS)
                    + ", trial_end=" + formatNullable(trial.trialEndTimeS));
            System.out.println("Min distance     : horiz=" + formatNullable(trial.minHorizontalDistance)
                    + ", vert=" + formatNullable(trial.minVerticalSeparation)
                    + ", at=" + formatNullable(trial.minDistanceSimTimeS));
            System.out.println("Max abs input    : pitch=" + formatNullable(trial.maxAbsPitchInput)
                    + ", roll=" + formatNullable(trial.maxAbsRollInput)
                    + ", yaw=" + formatNullable(trial.maxAbsYawInput)
                    + ", throttle=" + formatNullable(trial.maxAbsThrottleInput));
            System.out.println("First response axis : " + trial.firstControlAxis);
            System.out.println("Latencies        : spawn->hazard=" + formatDelta(trial.spawnTimeS, trial.hazardTimeS)
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
        for (String eventType : CLEAN_TRIAL_EVENTS) {
            int count = eventCounts.getOrDefault(eventType, 0);
            if (count == 0) {
                return "missing " + eventType;
            }
            if (count > 1) {
                return "duplicate " + eventType + " count=" + count;
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

        return "";
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

    private static String formatNullable(Double value) {
        return value == null || value.isNaN() ? "n/a" : String.format("%.3f", value);
    }

    private static String formatDelta(Double start, Double end) {
        if (start == null || end == null) {
            return "n/a";
        }
        return formatNullable(end - start) + " s";
    }

    private static String blankToUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
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
        public final Double spawnTimeS;
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
                Double spawnTimeS,
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
            this.spawnTimeS = spawnTimeS;
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
