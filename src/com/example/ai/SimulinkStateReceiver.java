package com.example.ai;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class SimulinkStateReceiver extends Thread {
    private final Map<Integer, Aircraft> aircraftById;
    private final int port;
    private volatile boolean running = true;
    private DatagramSocket socket;

    public SimulinkStateReceiver(Map<Integer, Aircraft> aircraftById, int port) {
        this.aircraftById = aircraftById;
        this.port = port;
        setName("simulink-state-receiver");
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            socket = new DatagramSocket(port);
            byte[] receiveData = new byte[2048];
            System.out.println("Waiting for Simulink state on UDP port " + port);

            while (running) {
                DatagramPacket packet = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                applyMessage(message);
            }
        } catch (Exception e) {
            if (running) {
                System.out.println("State receiver stopped unexpectedly.");
                e.printStackTrace();
            }
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public void stopListening() {
        running = false;
        if (socket != null) {
            socket.close();
        }
    }

    private void applyMessage(String message) {
        String[] values = message.split(",");
        if (values.length >= 11 && "STATE".equalsIgnoreCase(values[0])) {
            int id = Integer.parseInt(values[1].trim());
            Aircraft aircraft = aircraftById.get(id);
            if (aircraft == null) {
                return;
            }

            aircraft.updateStateFromSimulator(
                    Double.parseDouble(values[2].trim()),
                    Double.parseDouble(values[3].trim()),
                    Double.parseDouble(values[4].trim()),
                    Double.parseDouble(values[5].trim()),
                    Double.parseDouble(values[6].trim()),
                    Double.parseDouble(values[7].trim()),
                    Double.parseDouble(values[8].trim()),
                    Double.parseDouble(values[9].trim()),
                    Double.parseDouble(values[10].trim())
            );
            return;
        }

        if (values.length >= 6 && "STATE".equalsIgnoreCase(values[0])) {
            int id = Integer.parseInt(values[1].trim());
            Aircraft aircraft = aircraftById.get(id);
            if (aircraft == null) {
                return;
            }

            aircraft.setPitch(Double.parseDouble(values[2].trim()));
            aircraft.setRoll(Double.parseDouble(values[3].trim()));
            aircraft.setYaw(Double.parseDouble(values[4].trim()));
            aircraft.setIndicatedAirspeed(Double.parseDouble(values[5].trim()));
            if (values.length >= 7) {
                aircraft.setEngineRPM(Double.parseDouble(values[6].trim()));
            }
            return;
        }

        if (values.length >= 5) {
            for (Aircraft aircraft : aircraftById.values()) {
                aircraft.setPitch(Double.parseDouble(values[0].trim()));
                aircraft.setRoll(Double.parseDouble(values[1].trim()));
                aircraft.setYaw(Double.parseDouble(values[2].trim()));
                aircraft.setIndicatedAirspeed(Double.parseDouble(values[3].trim()));
                aircraft.setEngineRPM(Double.parseDouble(values[4].trim()));
                break;
            }
        }
    }
}
