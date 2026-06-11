package com.example.ai;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class XPlaneIntruderLogger implements AutoCloseable {
    private final BufferedWriter writer;

    public XPlaneIntruderLogger(Path outputPath) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true));
        if (!Files.exists(outputPath) || Files.size(outputPath) == 0) {
            writer.write("received_timestamp_ms,trial_id,sample_index,sim_time_s,intruder_x,intruder_y,intruder_z,intruder_latitude_deg,intruder_longitude_deg,intruder_elevation_m,intruder_vx,intruder_vy,intruder_vz,horizontal_distance,vertical_separation,ownship_x,ownship_y,ownship_z");
            writer.newLine();
            writer.flush();
        }
    }

    public synchronized void log(XPlaneIntruderSample sample) {
        try {
            writer.write(sample.toCsv());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.out.println("Failed to log X-Plane intruder sample: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
