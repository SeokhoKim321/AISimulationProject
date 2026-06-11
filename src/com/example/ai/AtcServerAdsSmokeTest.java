package com.example.ai;

public class AtcServerAdsSmokeTest {
    private static final String DEFAULT_HOST = "172.16.150.130";
    private static final int DEFAULT_PORT = 50000;
    private static final String DEFAULT_MODULE_ID = "SDP_XP_SMOKE";
    private static final String DEFAULT_FLIGHT_ID = "intruder01";

    public static void main(String[] args) throws Exception {
        String host = args.length >= 1 ? args[0] : DEFAULT_HOST;
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String moduleId = args.length >= 3 ? args[2] : DEFAULT_MODULE_ID;
        String flightId = args.length >= 4 ? args[3] : DEFAULT_FLIGHT_ID;

        XPlaneIntruderSample sample = new XPlaneIntruderSample(
                System.currentTimeMillis(),
                1,
                1,
                12.5,
                -38620.0,
                499.0,
                21120.0,
                37.309500,
                126.564200,
                652.0,
                -18.0,
                0.0,
                -24.0,
                250.0,
                5.0,
                -38747.859,
                499.319,
                21335.248
        );

        System.out.println("Starting ATC ADS smoke test");
        System.out.println("Host      : " + host);
        System.out.println("Port      : " + port);
        System.out.println("Module ID : " + moduleId);
        System.out.println("Flight ID : " + flightId);

        try (AtcServerBridgeClient bridgeClient = new AtcServerBridgeClient(
                host,
                port,
                "sdp",
                moduleId,
                flightId,
                1
        )) {
            bridgeClient.connect();
            bridgeClient.sendIntruder(sample);
            System.out.println("Sent one ADS packet from a synthetic intruder sample.");
            Thread.sleep(1_000L);
        }

        System.out.println("ATC ADS smoke test finished.");
    }
}
