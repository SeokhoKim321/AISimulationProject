package com.example.ai;

import java.util.StringJoiner;

public class XPlaneStateSample {
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
    public final double yAglM;
    public final double vx;
    public final double vy;
    public final double vz;
    public final double headingDeg;
    public final double pitchDeg;
    public final double rollDeg;
    public final double pRate;
    public final double qRate;
    public final double rRate;
    public final double iasMps;
    public final double tasMps;
    public final double verticalSpeedMps;
    public final double pitchInput;
    public final double rollInput;
    public final double yawInput;
    public final double throttleInput;

    public XPlaneStateSample(
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
            double yAglM,
            double vx,
            double vy,
            double vz,
            double headingDeg,
            double pitchDeg,
            double rollDeg,
            double pRate,
            double qRate,
            double rRate,
            double iasMps,
            double tasMps,
            double verticalSpeedMps,
            double pitchInput,
            double rollInput,
            double yawInput,
            double throttleInput
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
        this.yAglM = yAglM;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.headingDeg = headingDeg;
        this.pitchDeg = pitchDeg;
        this.rollDeg = rollDeg;
        this.pRate = pRate;
        this.qRate = qRate;
        this.rRate = rRate;
        this.iasMps = iasMps;
        this.tasMps = tasMps;
        this.verticalSpeedMps = verticalSpeedMps;
        this.pitchInput = pitchInput;
        this.rollInput = rollInput;
        this.yawInput = yawInput;
        this.throttleInput = throttleInput;
    }

    public static XPlaneStateSample fromCsv(String message, long receivedTimestampMs) {
        String[] values = message.split(",");
        if (values.length < 22 || !"STATE".equalsIgnoreCase(values[0].trim())) {
            throw new IllegalArgumentException("Invalid X-Plane state message: " + message);
        }

        int offset = values.length >= 23 ? 1 : 0;
        int trialId = offset == 1 ? Integer.parseInt(values[1].trim()) : 0;
        int positionOffset = values.length >= 27 ? 4 : 0;

        double latitudeDeg = Double.NaN;
        double longitudeDeg = Double.NaN;
        double elevationM = Double.NaN;
        double yAglM = Double.NaN;

        if (positionOffset == 4) {
            latitudeDeg = Double.parseDouble(values[6 + offset].trim());
            longitudeDeg = Double.parseDouble(values[7 + offset].trim());
            elevationM = Double.parseDouble(values[8 + offset].trim());
            yAglM = Double.parseDouble(values[9 + offset].trim());
        }

        return new XPlaneStateSample(
                receivedTimestampMs,
                trialId,
                Integer.parseInt(values[1 + offset].trim()),
                Double.parseDouble(values[2 + offset].trim()),
                Double.parseDouble(values[3 + offset].trim()),
                Double.parseDouble(values[4 + offset].trim()),
                Double.parseDouble(values[5 + offset].trim()),
                latitudeDeg,
                longitudeDeg,
                elevationM,
                yAglM,
                Double.parseDouble(values[6 + offset + positionOffset].trim()),
                Double.parseDouble(values[7 + offset + positionOffset].trim()),
                Double.parseDouble(values[8 + offset + positionOffset].trim()),
                Double.parseDouble(values[9 + offset + positionOffset].trim()),
                Double.parseDouble(values[10 + offset + positionOffset].trim()),
                Double.parseDouble(values[11 + offset + positionOffset].trim()),
                Double.parseDouble(values[12 + offset + positionOffset].trim()),
                Double.parseDouble(values[13 + offset + positionOffset].trim()),
                Double.parseDouble(values[14 + offset + positionOffset].trim()),
                Double.parseDouble(values[15 + offset + positionOffset].trim()),
                Double.parseDouble(values[16 + offset + positionOffset].trim()),
                Double.parseDouble(values[17 + offset + positionOffset].trim()),
                Double.parseDouble(values[18 + offset + positionOffset].trim()),
                Double.parseDouble(values[19 + offset + positionOffset].trim()),
                Double.parseDouble(values[20 + offset + positionOffset].trim()),
                Double.parseDouble(values[21 + offset + positionOffset].trim())
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
        joiner.add(Double.toString(yAglM));
        joiner.add(Double.toString(vx));
        joiner.add(Double.toString(vy));
        joiner.add(Double.toString(vz));
        joiner.add(Double.toString(headingDeg));
        joiner.add(Double.toString(pitchDeg));
        joiner.add(Double.toString(rollDeg));
        joiner.add(Double.toString(pRate));
        joiner.add(Double.toString(qRate));
        joiner.add(Double.toString(rRate));
        joiner.add(Double.toString(iasMps));
        joiner.add(Double.toString(tasMps));
        joiner.add(Double.toString(verticalSpeedMps));
        joiner.add(Double.toString(pitchInput));
        joiner.add(Double.toString(rollInput));
        joiner.add(Double.toString(yawInput));
        joiner.add(Double.toString(throttleInput));
        return joiner.toString();
    }
}
