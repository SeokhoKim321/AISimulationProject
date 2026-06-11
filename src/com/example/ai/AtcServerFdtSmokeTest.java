package com.example.ai;

public class AtcServerFdtSmokeTest {
    private static final String DEFAULT_HOST = "172.16.150.130";
    private static final int DEFAULT_PORT = 50000;
    private static final String DEFAULT_MODULE_ID = "PLT_XP_SMOKE";
    private static final String DEFAULT_FLIGHT_ID = "xplane01";

    public static void main(String[] args) throws Exception {
        String host = args.length >= 1 ? args[0] : DEFAULT_HOST;
        int port = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String moduleId = args.length >= 3 ? args[2] : DEFAULT_MODULE_ID;
        String flightId = args.length >= 4 ? args[3] : DEFAULT_FLIGHT_ID;

        XPlaneStateSample sample = new XPlaneStateSample(
                System.currentTimeMillis(),
                1,
                1,
                12.5,
                -38747.859,
                499.319,
                21335.248,
                37.307553,
                126.562424,
                652.687,
                651.743,
                -19.427,
                -1.182,
                -27.518,
                324.763,
                0.277,
                0.061,
                0.251,
                1.493,
                0.085,
                -13.804,
                33.705,
                -0.036,
                0.004,
                0.004,
                0.004,
                0.502
        );

        System.out.println("Starting ATC FDT smoke test");
        System.out.println("Host      : " + host);
        System.out.println("Port      : " + port);
        System.out.println("Module ID : " + moduleId);
        System.out.println("Flight ID : " + flightId);

        try (AtcServerBridgeClient bridgeClient = new AtcServerBridgeClient(
                host,
                port,
                "plt",
                moduleId,
                flightId,
                1
        )) {
            bridgeClient.connect();
            bridgeClient.sendState(sample);
            System.out.println("Sent one FDT packet from a synthetic X-Plane state sample.");
            Thread.sleep(1_000L);
        }

        System.out.println("ATC FDT smoke test finished.");
    }
}
