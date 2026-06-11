package com.example.ai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class AtcServerConnectionTest {
    private static final String DEFAULT_HOST = "172.16.150.130";
    private static final int DEFAULT_PORT = 50000;
    private static final String DEFAULT_MODULE_ID = "PLT_XP";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    public static void main(String[] args) throws Exception {
        String host = args.length >= 1 ? args[0] : DEFAULT_HOST;
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String moduleId = args.length >= 3 ? args[2] : DEFAULT_MODULE_ID;

        String registerMessage = "src:plt#" + moduleId + ">dst:svr>typ:reg";

        System.out.println("Connecting to ATC Simulator Server");
        System.out.println("Host      : " + host);
        System.out.println("Port      : " + port);
        System.out.println("Module ID : " + moduleId);
        System.out.println("Send      : " + registerMessage);

        try (Socket socket = new Socket();
             ) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            System.out.println("Connected  : " + socket.getLocalSocketAddress() + " -> " + socket.getRemoteSocketAddress());

            try (
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
            ) {

            writer.println(registerMessage);
            System.out.println("Waiting for registration response, timeout " + READ_TIMEOUT_MS + " ms.");

            String response = reader.readLine();
            System.out.println("Receive   : " + response);

            if (response == null) {
                throw new IllegalStateException("ATC server closed the connection without a response.");
            }
            if (!response.equalsIgnoreCase("src:svr>dst:plt#" + moduleId + ">typ:grt")) {
                throw new IllegalStateException("Unexpected ATC server response: " + response);
            }

            System.out.println("Registration succeeded. Keeping connection open for 10 seconds.");
            Thread.sleep(10_000L);

            String closeMessage = "src:plt#" + moduleId + ">dst:svr>typ:cls";
            System.out.println("Send      : " + closeMessage);
            writer.println(closeMessage);
            }
        }

        System.out.println("Connection test finished.");
    }
}
