package com.example.ai;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class XPlaneEventLogger implements AutoCloseable {
    private final BufferedWriter writer;

    public XPlaneEventLogger(Path outputPath) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true));
        if (!Files.exists(outputPath) || Files.size(outputPath) == 0) {
            writer.write("recorded_timestamp_ms,session_id,trial_id,event_type,source,detail,xplane_event_index,xplane_state_sample_index,xplane_intruder_sample_index,xplane_sim_time_s");
            writer.newLine();
            writer.flush();
        }
    }

    public synchronized void log(String sessionId, XPlaneEventType eventType, String source, String detail) {
        try {
            XPlaneEventRecord record = new XPlaneEventRecord(
                    System.currentTimeMillis(),
                    sessionId,
                    0,
                    eventType,
                    source,
                    detail,
                    null,
                    null,
                    null,
                    null
            );
            writer.write(record.toCsv());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.out.println("Failed to log X-Plane event: " + e.getMessage());
        }
    }

    public synchronized void log(XPlaneEventRecord record) {
        try {
            writer.write(record.toCsv());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.out.println("Failed to log X-Plane event: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
