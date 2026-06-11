package com.example.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.GraphicsEnvironment;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.SwingUtilities;

public class XPlaneReceiverMain {
    private static final int DEFAULT_PORT = 9100;
    private static final Path DEFAULT_OUTPUT_DIR = Paths.get("logs", "xplane");
    private static final DateTimeFormatter SESSION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        String sessionId = "session_" + LocalDateTime.now().format(SESSION_TIME_FORMAT);
        Path stateOutputPath = DEFAULT_OUTPUT_DIR.resolve("xplane_" + sessionId + ".csv").toAbsolutePath();
        Path intruderOutputPath = DEFAULT_OUTPUT_DIR.resolve("xplane_" + sessionId + "_intruder.csv").toAbsolutePath();
        Path eventOutputPath = DEFAULT_OUTPUT_DIR.resolve("xplane_" + sessionId + "_events.csv").toAbsolutePath();
        String atcHost = null;
        int atcPort = 50000;
        String atcModuleId = "PLT_XP";
        String atcFlightId = "xplane01";
        String atcIntruderModuleType = "sdp";
        String atcIntruderModuleId = "SDP_XP";
        String atcIntruderFlightId = "intruder01";
        boolean atcIntruderEnabled = true;
        boolean atcIntruderFdtEnabled = false;
        boolean atcIntruderOnOwnshipLink = false;
        int atcSendEverySamples = 5;

        if (args.length >= 1) {
            port = Integer.parseInt(args[0]);
        }
        if (args.length >= 2) {
            stateOutputPath = Paths.get(args[1]).toAbsolutePath();
        }
        if (args.length >= 3) {
            sessionId = args[2];
        }
        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--atc-host" -> atcHost = args[++i];
                case "--atc-port" -> atcPort = Integer.parseInt(args[++i]);
                case "--atc-module" -> atcModuleId = args[++i];
                case "--atc-fid" -> atcFlightId = args[++i];
                case "--atc-intruder-source" -> atcIntruderModuleType = args[++i];
                case "--atc-intruder-module" -> atcIntruderModuleId = args[++i];
                case "--atc-intruder-fid" -> atcIntruderFlightId = args[++i];
                case "--atc-no-intruder" -> atcIntruderEnabled = false;
                case "--atc-intruder-fdt" -> atcIntruderFdtEnabled = true;
                case "--atc-intruder-on-ownship-link" -> atcIntruderOnOwnshipLink = true;
                case "--atc-every" -> atcSendEverySamples = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (args.length < 2) {
            stateOutputPath = DEFAULT_OUTPUT_DIR.resolve("xplane_" + sessionId + ".csv").toAbsolutePath();
        }
        intruderOutputPath = stateOutputPath.resolveSibling(stateOutputPath.getFileName().toString().replace(".csv", "_intruder.csv"));
        eventOutputPath = stateOutputPath.resolveSibling(stateOutputPath.getFileName().toString().replace(".csv", "_events.csv"));
        final String finalSessionId = sessionId;
        final Path finalStateOutputPath = stateOutputPath;
        final Path finalIntruderOutputPath = intruderOutputPath;
        final Path finalEventOutputPath = eventOutputPath;

        Files.createDirectories(finalStateOutputPath.getParent());

        System.out.println("Starting X-Plane UDP receiver");
        System.out.println("Session ID: " + finalSessionId);
        System.out.println("Port      : " + port);
        System.out.println("State CSV : " + finalStateOutputPath);
        System.out.println("Intruder CSV : " + finalIntruderOutputPath);
        System.out.println("Event CSV : " + finalEventOutputPath);
        if (atcHost != null) {
            System.out.println("ATC bridge: " + atcHost + ":" + atcPort
                    + " module=PLT#" + atcModuleId
                    + " fid=" + atcFlightId
                    + " every=" + atcSendEverySamples);
            if (atcIntruderEnabled) {
                System.out.println("ATC intruder bridge: " + atcHost + ":" + atcPort
                        + " module=" + atcIntruderModuleType.toUpperCase() + "#" + atcIntruderModuleId
                        + " fid=" + atcIntruderFlightId
                        + " every=" + atcSendEverySamples
                        + " fdt=" + atcIntruderFdtEnabled
                        + " ownshipLink=" + atcIntruderOnOwnshipLink);
            }
        }

        XPlaneExperimentLogger stateLogger = new XPlaneExperimentLogger(finalStateOutputPath);
        XPlaneIntruderLogger intruderLogger = new XPlaneIntruderLogger(finalIntruderOutputPath);
        XPlaneEventLogger eventLogger = new XPlaneEventLogger(finalEventOutputPath);
        XPlaneAutoEventDetector autoEventDetector = new XPlaneAutoEventDetector(finalSessionId, eventLogger);
        AtcServerBridgeClient atcOwnshipBridgeClient = null;
        AtcServerBridgeClient atcIntruderBridgeClient = null;
        if (atcHost != null) {
            atcOwnshipBridgeClient = new AtcServerBridgeClient(
                    atcHost,
                    atcPort,
                    "plt",
                    atcModuleId,
                    atcFlightId,
                    atcIntruderFlightId,
                    atcSendEverySamples,
                    atcIntruderOnOwnshipLink && atcIntruderFdtEnabled
            );
            atcOwnshipBridgeClient.connect();

            if (atcIntruderEnabled) {
                if (atcIntruderOnOwnshipLink) {
                    atcIntruderBridgeClient = atcOwnshipBridgeClient;
                } else {
                    atcIntruderBridgeClient = new AtcServerBridgeClient(
                            atcHost,
                            atcPort,
                            atcIntruderModuleType,
                            atcIntruderModuleId,
                            atcIntruderFlightId,
                            atcSendEverySamples,
                            atcIntruderFdtEnabled
                    );
                    atcIntruderBridgeClient.connect();
                }
            }
        }
        XPlaneStateReceiver receiver = new XPlaneStateReceiver(
                port,
                finalSessionId,
                stateLogger,
                intruderLogger,
                eventLogger,
                autoEventDetector,
                atcOwnshipBridgeClient,
                atcIntruderBridgeClient
        );
        final XPlaneEventControllerFrame[] controllerFrameRef = new XPlaneEventControllerFrame[1];

        eventLogger.log(finalSessionId, XPlaneEventType.SESSION_START, "receiver_main", "X-Plane UDP receiver session started");
        eventLogger.log(finalSessionId, XPlaneEventType.RECEIVER_START, "receiver_main", "Listening on UDP port " + port);

        if (!GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                XPlaneEventControllerFrame frame = new XPlaneEventControllerFrame(
                        finalSessionId,
                        finalStateOutputPath,
                        finalEventOutputPath,
                        eventLogger,
                        receiver::stopListening
                );
                controllerFrameRef[0] = frame;
                frame.setVisible(true);
            });
        } else {
            System.out.println("Headless environment detected. Event controller UI was not started.");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Stopping X-Plane UDP receiver");
            eventLogger.log(finalSessionId, XPlaneEventType.RECEIVER_STOP, "receiver_main", "Receiver shutdown hook invoked");
            receiver.stopListening();
            if (controllerFrameRef[0] != null) {
                controllerFrameRef[0].closeFrame();
            }
            try {
                eventLogger.log(finalSessionId, XPlaneEventType.SESSION_STOP, "receiver_main", "X-Plane UDP receiver session stopped");
                stateLogger.close();
                intruderLogger.close();
                eventLogger.close();
            } catch (Exception e) {
                System.out.println("Failed to close logger: " + e.getMessage());
            }
        }));

        receiver.start();
        receiver.join();
    }
}
