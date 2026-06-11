package com.example.ai;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class SimulinkCommandSender implements AutoCloseable {
    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port;

    public SimulinkCommandSender(String host, int port) throws Exception {
        this.socket = new DatagramSocket();
        this.address = InetAddress.getByName(host);
        this.port = port;
    }

    public void sendCommand(Aircraft aircraft) {
        String payload = String.format(
                "CMD,%d,%.3f,%.3f,%.3f,%s",
                aircraft.getId(),
                aircraft.getTargetHeadingDeg(),
                aircraft.getTargetAltitudeM(),
                aircraft.getTargetSpeedMps(),
                aircraft.getTacticalState()
        );
        send(payload);
    }

    public void send(String payload) {
        try {
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);
        } catch (Exception e) {
            System.out.println("Failed to send command to Simulink: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        socket.close();
    }
}
