package com.example.ai;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class XPlaneStateReceiver extends Thread {
    private final int port;
    private final String sessionId;
    private final XPlaneExperimentLogger stateLogger;
    private final XPlaneIntruderLogger intruderLogger;
    private final XPlaneEventLogger eventLogger;
    private final XPlaneAutoEventDetector autoEventDetector;
    private final AtcServerBridgeClient atcOwnshipBridgeClient;
    private final AtcServerBridgeClient atcIntruderBridgeClient;
    private volatile boolean running = true;
    private DatagramSocket socket;

    public XPlaneStateReceiver(
            int port,
            String sessionId,
            XPlaneExperimentLogger stateLogger,
            XPlaneIntruderLogger intruderLogger,
            XPlaneEventLogger eventLogger,
            XPlaneAutoEventDetector autoEventDetector
    ) {
        this(port, sessionId, stateLogger, intruderLogger, eventLogger, autoEventDetector, null, null);
    }

    public XPlaneStateReceiver(
            int port,
            String sessionId,
            XPlaneExperimentLogger stateLogger,
            XPlaneIntruderLogger intruderLogger,
            XPlaneEventLogger eventLogger,
            XPlaneAutoEventDetector autoEventDetector,
            AtcServerBridgeClient atcBridgeClient
    ) {
        this(port, sessionId, stateLogger, intruderLogger, eventLogger, autoEventDetector, atcBridgeClient, null);
    }

    public XPlaneStateReceiver(
            int port,
            String sessionId,
            XPlaneExperimentLogger stateLogger,
            XPlaneIntruderLogger intruderLogger,
            XPlaneEventLogger eventLogger,
            XPlaneAutoEventDetector autoEventDetector,
            AtcServerBridgeClient atcOwnshipBridgeClient,
            AtcServerBridgeClient atcIntruderBridgeClient
    ) {
        this.port = port;
        this.sessionId = sessionId;
        this.stateLogger = stateLogger;
        this.intruderLogger = intruderLogger;
        this.eventLogger = eventLogger;
        this.autoEventDetector = autoEventDetector;
        this.atcOwnshipBridgeClient = atcOwnshipBridgeClient;
        this.atcIntruderBridgeClient = atcIntruderBridgeClient;
        setName("xplane-state-receiver");
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            byte[] receiveData = new byte[4096];
            System.out.println("Waiting for X-Plane state on UDP port " + port);

            while (running) {
                DatagramPacket packet = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(packet);

                long receivedTimestampMs = System.currentTimeMillis();
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();

                try {
                    if (message.startsWith("STATE,")) {
                        XPlaneStateSample sample = XPlaneStateSample.fromCsv(message, receivedTimestampMs);
                        stateLogger.log(sample);
                        autoEventDetector.onStateSample(sample);
                        if (atcOwnshipBridgeClient != null) {
                            atcOwnshipBridgeClient.sendState(sample);
                        }
                    } else if (message.startsWith("INTRUDER,")) {
                        XPlaneIntruderSample intruderSample = XPlaneIntruderSample.fromCsv(message, receivedTimestampMs);
                        intruderLogger.log(intruderSample);
                        autoEventDetector.onIntruderSample(intruderSample);
                        if (atcIntruderBridgeClient != null) {
                            atcIntruderBridgeClient.sendIntruder(intruderSample);
                        }
                    } else if (message.startsWith("EVENT,")) {
                        XPlaneEventRecord eventRecord = XPlaneEventRecord.fromUdpMessage(message, receivedTimestampMs, sessionId);
                        eventLogger.log(eventRecord);
                        autoEventDetector.onEventRecord(eventRecord);
                    } else {
                        throw new IllegalArgumentException("Unknown packet prefix");
                    }
                } catch (Exception parseError) {
                    System.out.println("Failed to parse X-Plane packet: " + message);
                    System.out.println(parseError.getMessage());
                }
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("X-Plane receiver stopped unexpectedly.");
                e.printStackTrace();
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            if (atcOwnshipBridgeClient != null) {
                atcOwnshipBridgeClient.close();
            }
            if (atcIntruderBridgeClient != null && atcIntruderBridgeClient != atcOwnshipBridgeClient) {
                atcIntruderBridgeClient.close();
            }
        }
    }

    public void stopListening() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }
}
