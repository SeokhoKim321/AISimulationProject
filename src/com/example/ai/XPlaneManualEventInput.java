package com.example.ai;

import java.util.Locale;
import java.util.Scanner;

public class XPlaneManualEventInput extends Thread {
    private final String sessionId;
    private final XPlaneEventLogger eventLogger;
    private final XPlaneStateReceiver receiver;

    public XPlaneManualEventInput(String sessionId, XPlaneEventLogger eventLogger, XPlaneStateReceiver receiver) {
        this.sessionId = sessionId;
        this.eventLogger = eventLogger;
        this.receiver = receiver;
        setName("xplane-manual-event-input");
        setDaemon(true);
    }

    @Override
    public void run() {
        printHelp();

        try (Scanner scanner = new Scanner(System.in)) {
            while (receiver.isAlive()) {
                if (!scanner.hasNextLine()) {
                    break;
                }

                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (handleCommand(line)) {
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("Manual event input stopped: " + e.getMessage());
        }
    }

    private boolean handleCommand(String line) {
        String lower = line.toLowerCase(Locale.ROOT);

        switch (lower) {
            case "help":
                printHelp();
                return false;
            case "marker":
                log(XPlaneEventType.SCENARIO_MARKER, "Manual scenario marker");
                return false;
            case "hazard_on":
                log(XPlaneEventType.HAZARD_DETECTED, "Manual hazard start");
                return false;
            case "hazard_off":
                log(XPlaneEventType.HAZARD_CLEARED, "Manual hazard cleared");
                return false;
            case "advisory_on":
                log(XPlaneEventType.ADVISORY_SHOWN, "Manual advisory shown");
                return false;
            case "advisory_off":
                log(XPlaneEventType.ADVISORY_CLEARED, "Manual advisory cleared");
                return false;
            case "response_on":
                log(XPlaneEventType.PILOT_RESPONSE_START, "Manual pilot response start");
                return false;
            case "response_off":
                log(XPlaneEventType.PILOT_RESPONSE_END, "Manual pilot response end");
                return false;
            case "quit":
                log(XPlaneEventType.MANUAL_NOTE, "Manual quit command");
                receiver.stopListening();
                return true;
            default:
                if (lower.startsWith("note ")) {
                    log(XPlaneEventType.MANUAL_NOTE, line.substring(5).trim());
                    return false;
                }
                System.out.println("Unknown command: " + line);
                printHelp();
                return false;
        }
    }

    private void log(XPlaneEventType eventType, String detail) {
        eventLogger.log(sessionId, eventType, "manual_input", detail);
        System.out.println("Event logged: " + eventType + " | " + detail);
    }

    private static void printHelp() {
        System.out.println("Manual event commands:");
        System.out.println("  marker");
        System.out.println("  hazard_on / hazard_off");
        System.out.println("  advisory_on / advisory_off");
        System.out.println("  response_on / response_off");
        System.out.println("  note <text>");
        System.out.println("  help");
        System.out.println("  quit");
    }
}
