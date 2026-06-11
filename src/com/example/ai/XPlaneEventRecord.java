package com.example.ai;

import java.util.StringJoiner;

public class XPlaneEventRecord {
    public final long recordedTimestampMs;
    public final String sessionId;
    public final int trialId;
    public final XPlaneEventType eventType;
    public final String source;
    public final String detail;
    public final Integer xplaneEventIndex;
    public final Integer xplaneStateSampleIndex;
    public final Integer xplaneIntruderSampleIndex;
    public final Double xplaneSimTimeS;

    public XPlaneEventRecord(
            long recordedTimestampMs,
            String sessionId,
            int trialId,
            XPlaneEventType eventType,
            String source,
            String detail,
            Integer xplaneEventIndex,
            Integer xplaneStateSampleIndex,
            Integer xplaneIntruderSampleIndex,
            Double xplaneSimTimeS
    ) {
        this.recordedTimestampMs = recordedTimestampMs;
        this.sessionId = sessionId;
        this.trialId = trialId;
        this.eventType = eventType;
        this.source = source;
        this.detail = detail;
        this.xplaneEventIndex = xplaneEventIndex;
        this.xplaneStateSampleIndex = xplaneStateSampleIndex;
        this.xplaneIntruderSampleIndex = xplaneIntruderSampleIndex;
        this.xplaneSimTimeS = xplaneSimTimeS;
    }

    public static XPlaneEventRecord fromUdpMessage(String message, long receivedTimestampMs, String sessionId) {
        String[] values = message.split(",");
        if (values.length < 4 || !"EVENT".equalsIgnoreCase(values[0].trim())) {
            throw new IllegalArgumentException("Invalid X-Plane event message: " + message);
        }

        boolean hasTrialId = values.length >= 5 && isInteger(values[1].trim());
        int trialId = hasTrialId ? Integer.parseInt(values[1].trim()) : 0;
        int eventIndexPosition = hasTrialId ? 2 : 1;
        int simTimePosition = hasTrialId ? 3 : 2;
        int eventNamePosition = hasTrialId ? 4 : 3;

        String eventName = values[eventNamePosition].trim();
        String detail = eventName;
        if (values.length > eventNamePosition + 1) {
            StringJoiner detailJoiner = new StringJoiner(",");
            for (int i = eventNamePosition + 1; i < values.length; i++) {
                detailJoiner.add(values[i].trim());
            }
            detail = detailJoiner.toString();
        }

        return new XPlaneEventRecord(
                receivedTimestampMs,
                sessionId,
                trialId,
                XPlaneEventType.valueOf(eventName),
                "xplane_udp",
                detail,
                Integer.parseInt(values[eventIndexPosition].trim()),
                null,
                null,
                Double.parseDouble(values[simTimePosition].trim())
        );
    }

    public String toCsv() {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(Long.toString(recordedTimestampMs));
        joiner.add(escape(sessionId));
        joiner.add(Integer.toString(trialId));
        joiner.add(eventType.name());
        joiner.add(escape(source));
        joiner.add(escape(detail));
        joiner.add(xplaneEventIndex == null ? "" : Integer.toString(xplaneEventIndex));
        joiner.add(xplaneStateSampleIndex == null ? "" : Integer.toString(xplaneStateSampleIndex));
        joiner.add(xplaneIntruderSampleIndex == null ? "" : Integer.toString(xplaneIntruderSampleIndex));
        joiner.add(xplaneSimTimeS == null ? "" : Double.toString(xplaneSimTimeS));
        return joiner.toString();
    }

    private static boolean isInteger(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (i == 0 && (c == '-' || c == '+')) {
                continue;
            }
            if (!Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
