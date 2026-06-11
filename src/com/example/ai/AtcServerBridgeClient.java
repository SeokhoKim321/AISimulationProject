package com.example.ai;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AtcServerBridgeClient implements Closeable {
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    private final String host;
    private final int port;
    private final String moduleType;
    private final String moduleId;
    private final String flightId;
    private final String intruderFlightId;
    private final int sendEverySamples;
    private final boolean sendIntruderFdt;

    private Socket socket;
    private PrintWriter writer;
    private int stateSamplesSeen;
    private int intruderSamplesSeen;
    private boolean connected;

    public AtcServerBridgeClient(
            String host,
            int port,
            String moduleType,
            String moduleId,
            String flightId,
            int sendEverySamples
    ) {
        this(host, port, moduleType, moduleId, flightId, sendEverySamples, false);
    }

    public AtcServerBridgeClient(
            String host,
            int port,
            String moduleType,
            String moduleId,
            String flightId,
            int sendEverySamples,
            boolean sendIntruderFdt
    ) {
        this(host, port, moduleType, moduleId, flightId, flightId, sendEverySamples, sendIntruderFdt);
    }

    public AtcServerBridgeClient(
            String host,
            int port,
            String moduleType,
            String moduleId,
            String flightId,
            String intruderFlightId,
            int sendEverySamples,
            boolean sendIntruderFdt
    ) {
        this.host = host;
        this.port = port;
        this.moduleType = moduleType.toLowerCase();
        this.moduleId = moduleId.toUpperCase();
        this.flightId = flightId.toLowerCase();
        this.intruderFlightId = intruderFlightId.toLowerCase();
        this.sendEverySamples = Math.max(1, sendEverySamples);
        this.sendIntruderFdt = sendIntruderFdt;
    }

    public void connect() throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
        writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true
        );

        String registerMessage = "src:" + moduleType + "#" + moduleId + ">dst:svr>typ:reg";
        writer.println(registerMessage);
        String response = reader.readLine();
        String expected = "src:svr>dst:" + moduleType + "#" + moduleId + ">typ:grt";
        if (response == null || !response.equalsIgnoreCase(expected)) {
            throw new IllegalStateException("Unexpected ATC registration response: " + response);
        }

        connected = true;
        System.out.println("ATC bridge registered as " + moduleType + "#" + moduleId);
    }

    public void sendState(XPlaneStateSample sample) {
        if (!connected || writer == null) {
            return;
        }
        stateSamplesSeen++;
        if (stateSamplesSeen % sendEverySamples != 0) {
            return;
        }
        if (Double.isNaN(sample.latitudeDeg) || Double.isNaN(sample.longitudeDeg)) {
            return;
        }

        writer.println(formatFdt(sample));
        if (writer.checkError()) {
            connected = false;
            System.out.println("ATC bridge send failed. Future ATC sends will be skipped.");
        }
    }

    public void sendIntruder(XPlaneIntruderSample sample) {
        if (!connected || writer == null) {
            return;
        }
        intruderSamplesSeen++;
        if (intruderSamplesSeen % sendEverySamples != 0) {
            return;
        }
        if (Double.isNaN(sample.latitudeDeg) || Double.isNaN(sample.longitudeDeg)) {
            return;
        }

        writer.println(formatAds(sample));
        if (sendIntruderFdt) {
            writer.println(formatIntruderFdt(sample));
        }
        if (writer.checkError()) {
            connected = false;
            System.out.println("ATC bridge send failed. Future ATC sends will be skipped.");
        }
    }

    private String formatFdt(XPlaneStateSample sample) {
        double altitudeFt = sample.elevationM * 3.280839895;
        double speedKnot = sample.tasMps * 1.943844492;
        double verticalRateFpm = sample.verticalSpeedMps * 196.850394;

        return "src:" + moduleType + "#" + moduleId
                + ">dst:svr"
                + ">typ:fdt"
                + "&fid:" + flightId
                + ">lat:" + format(sample.latitudeDeg, 6) + "@deg"
                + ">lon:" + format(sample.longitudeDeg, 6) + "@deg"
                + ">alt:" + format(altitudeFt, 0) + "@ft"
                + ">spd:" + format(speedKnot, 0) + "@knot"
                + ">vrt:" + format(verticalRateFpm, 0) + "@ft/min"
                + ">hdg:" + format(sample.headingDeg, 0) + "@deg"
                + ">sqk:1200"
                + ">wpt:0"
                + ">crt:" + format(sample.simTimeS, 0);
    }

    private String formatAds(XPlaneIntruderSample sample) {
        return formatIntruderTrack(sample, "ads");
    }

    private String formatIntruderFdt(XPlaneIntruderSample sample) {
        return formatIntruderTrack(sample, "fdt");
    }

    private String formatIntruderTrack(XPlaneIntruderSample sample, String dataType) {
        double altitudeFt = sample.elevationM * 3.280839895;
        double horizontalSpeedMps = Math.sqrt(sample.vx * sample.vx + sample.vz * sample.vz);
        double speedKnot = horizontalSpeedMps * 1.943844492;
        double verticalRateFpm = sample.vy * 196.850394;
        double headingDeg = headingFromVelocity(sample.vx, sample.vz);

        return "src:" + moduleType + "#" + moduleId
                + ">dst:svr"
                + ">typ:" + dataType
                + "&fid:" + intruderFlightId
                + ">lat:" + format(sample.latitudeDeg, 6) + "@deg"
                + ">lon:" + format(sample.longitudeDeg, 6) + "@deg"
                + ">alt:" + format(altitudeFt, 0) + "@ft"
                + ">spd:" + format(speedKnot, 0) + "@knot"
                + ">vrt:" + format(verticalRateFpm, 0) + "@ft/min"
                + ">hdg:" + format(headingDeg, 0) + "@deg"
                + ">sqk:1200"
                + ">wpt:0"
                + ">crt:" + format(sample.simTimeS, 0);
    }

    private static double headingFromVelocity(double vx, double vz) {
        if (Math.sqrt(vx * vx + vz * vz) < 0.1) {
            return 0.0;
        }
        double heading = Math.toDegrees(Math.atan2(vx, -vz));
        if (heading < 0.0) {
            heading += 360.0;
        }
        return heading;
    }

    private static String format(double value, int digits) {
        return String.format(java.util.Locale.US, "%." + digits + "f", value);
    }

    @Override
    public void close() {
        connected = false;
        if (writer != null) {
            writer.println("src:" + moduleType + "#" + moduleId + ">dst:svr>typ:cls");
            writer.flush();
            writer.close();
            writer = null;
        }
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
                // Best-effort shutdown.
            }
            socket = null;
        }
    }
}
