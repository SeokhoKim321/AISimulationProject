package com.example.ai;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;

public class XPlaneExperimentLogger implements AutoCloseable {
    private final BufferedWriter writer;

    public XPlaneExperimentLogger(Path outputPath) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(outputPath.toFile(), true));
        if (!Files.exists(outputPath) || Files.size(outputPath) == 0) {
            writer.write("received_timestamp_ms,trial_id,sample_index,sim_time_s,x,y,z,latitude_deg,longitude_deg,elevation_m,y_agl_m,vx,vy,vz,heading_deg,pitch_deg,roll_deg,p_rate,q_rate,r_rate,ias_mps,tas_mps,vertical_speed_mps,pitch_input,roll_input,yaw_input,throttle_input");
            writer.newLine();
            writer.flush();
        }
    }

    public synchronized void log(XPlaneStateSample sample) {
        try {
            writer.write(sample.toCsv());
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            System.out.println("Failed to log X-Plane sample: " + e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
