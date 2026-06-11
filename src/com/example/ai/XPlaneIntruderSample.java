package com.example.ai;

import java.util.StringJoiner;

public class XPlaneIntruderSample {
    public final long receivedTimestampMs;
    public final int trialId;
    public final int sampleIndex;
    public final double simTimeS;
    public final double x;
    public final double y;
    public final double z;
    public final double latitudeDeg;
    public final double longitudeDeg;
    public final double elevationM;
    public final double vx;
    public final double vy;
    public final double vz;
    public final double horizontalDistance;
    public final double verticalSeparation;
    public final double ownshipX;
    public final double ownshipY;
    public final double ownshipZ;

    public XPlaneIntruderSample(
            long receivedTimestampMs,
            int trialId,
            int sampleIndex,
            double simTimeS,
            double x,
            double y,
            double z,
            double latitudeDeg,
            double longitudeDeg,
            double elevationM,
            double vx,
            double vy,
            double vz,
            double horizontalDistance,
            double verticalSeparation,
            double ownshipX,
            double ownshipY,
            double ownshipZ
    ) {
        this.receivedTimestampMs = receivedTimestampMs;
        this.trialId = trialId;
        this.sampleIndex = sampleIndex;
        this.simTimeS = simTimeS;
        this.x = x;
        this.y = y;
        this.z = z;
        this.latitudeDeg = latitudeDeg;
        this.longitudeDeg = longitudeDeg;
        this.elevationM = elevationM;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.horizontalDistance = horizontalDistance;
        this.verticalSeparation = verticalSeparation;
        this.ownshipX = ownshipX;
        this.ownshipY = ownshipY;
        this.ownshipZ = ownshipZ;
    }

    public static XPlaneIntruderSample fromCsv(String message, long receivedTimestampMs) {
        String[] values = message.split(",");
        if (values.length < 14 || !"INTRUDER".equalsIgnoreCase(values[0].trim())) {
            throw new IllegalArgumentException("Invalid X-Plane intruder message: " + message);
        }

        boolean hasGeo = values.length == 17 || values.length >= 18;
        boolean hasTrialId = values.length == 15 || values.length >= 18;
        int offset = hasTrialId ? 1 : 0;
        int trialId = hasTrialId ? Integer.parseInt(values[1].trim()) : 0;
        int index = 1 + offset;

        int sampleIndex = Integer.parseInt(values[index++].trim());
        double simTimeS = Double.parseDouble(values[index++].trim());
        double x = Double.parseDouble(values[index++].trim());
        double y = Double.parseDouble(values[index++].trim());
        double z = Double.parseDouble(values[index++].trim());
        double latitudeDeg = Double.NaN;
        double longitudeDeg = Double.NaN;
        double elevationM = Double.NaN;
        if (hasGeo) {
            latitudeDeg = Double.parseDouble(values[index++].trim());
            longitudeDeg = Double.parseDouble(values[index++].trim());
            elevationM = Double.parseDouble(values[index++].trim());
        }

        return new XPlaneIntruderSample(
                receivedTimestampMs,
                trialId,
                sampleIndex,
                simTimeS,
                x,
                y,
                z,
                latitudeDeg,
                longitudeDeg,
                elevationM,
                Double.parseDouble(values[index++].trim()),
                Double.parseDouble(values[index++].trim()),
                Double.parseDouble(values[index++].trim()),
                Double.parseDouble(values[index++].trim()),
                Double.parseDouble(values[index++].trim()),
                Double.parseDouble(values[index++].trim()),
                Double.parseDouble(values[index++].trim()),
                Double.parseDouble(values[index].trim())
        );
    }

    public String toCsv() {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(Long.toString(receivedTimestampMs));
        joiner.add(Integer.toString(trialId));
        joiner.add(Integer.toString(sampleIndex));
        joiner.add(Double.toString(simTimeS));
        joiner.add(Double.toString(x));
        joiner.add(Double.toString(y));
        joiner.add(Double.toString(z));
        joiner.add(Double.toString(latitudeDeg));
        joiner.add(Double.toString(longitudeDeg));
        joiner.add(Double.toString(elevationM));
        joiner.add(Double.toString(vx));
        joiner.add(Double.toString(vy));
        joiner.add(Double.toString(vz));
        joiner.add(Double.toString(horizontalDistance));
        joiner.add(Double.toString(verticalSeparation));
        joiner.add(Double.toString(ownshipX));
        joiner.add(Double.toString(ownshipY));
        joiner.add(Double.toString(ownshipZ));
        return joiner.toString();
    }
}
