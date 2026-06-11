package com.example.ai;

public class Aircraft {
    private int id;

    // World state in meters and degrees.
    private double x;
    private double y;
    private double z;
    private double speed;
    private double angle;
    private double verticalSpeed;
    private double vx;
    private double vy;
    private double vz;

    // Attitude / telemetry from Simulink.
    private double pitch;
    private double roll;
    private double yaw;
    private double indicatedAirspeed;
    private double engineRPM;

    // Mission / command state.
    private double destX;
    private double destY;
    private double destZ;
    private double commandX;
    private double commandY;
    private double commandZ;
    private double targetHeadingDeg;
    private double targetAltitudeM;
    private double targetSpeedMps;
    private boolean simulinkControlled;

    private static final double MAX_TURN_RATE = 8.1;
    private static final double MAX_CLIMB_RATE = 10.0;
    private static final double MAX_DESCENT_RATE = 5.0;

    private String tacticalState = "Cruising";
    private String team = "blue";

    public Aircraft(double x, double y, double z, double speed, double angle) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.speed = speed;
        this.angle = normalizeHeading(angle);
        this.yaw = this.angle;
        this.indicatedAirspeed = speed;
        this.targetSpeedMps = speed;
        this.targetAltitudeM = z;

        double rad = Math.toRadians(this.angle);
        this.commandX = x + Math.cos(rad) * 1000.0;
        this.commandY = y + Math.sin(rad) * 1000.0;
        this.commandZ = z;
        this.targetHeadingDeg = this.angle;
    }

    public void executeMovement(double dt) {
        if (simulinkControlled) {
            return;
        }

        double dx = commandX - x;
        double dy = commandY - y;
        double targetAngle = Math.toDegrees(Math.atan2(dy, dx));

        double diff = targetAngle - angle;
        while (diff <= -180) diff += 360;
        while (diff > 180) diff -= 360;

        double maxTurnStep = MAX_TURN_RATE * dt;
        if (Math.abs(diff) < maxTurnStep) {
            angle = targetAngle;
        } else {
            angle += (diff > 0) ? maxTurnStep : -maxTurnStep;
        }
        angle = normalizeHeading(angle);
        yaw = angle;

        double rad = Math.toRadians(angle);
        x += Math.cos(rad) * speed * dt;
        y += Math.sin(rad) * speed * dt;
        vx = Math.cos(rad) * speed;
        vy = Math.sin(rad) * speed;

        double altDiff = commandZ - z;
        if (Math.abs(altDiff) < 0.1) {
            verticalSpeed = 0.0;
            z = commandZ;
        } else if (altDiff > 0) {
            verticalSpeed = Math.min(MAX_CLIMB_RATE, altDiff / dt);
        } else {
            verticalSpeed = Math.max(-MAX_DESCENT_RATE, altDiff / dt);
        }

        z += verticalSpeed * dt;
        vz = verticalSpeed;
        if (z < 0) {
            z = 0;
            verticalSpeed = 0;
            vz = 0;
        }
    }

    public synchronized void updateStateFromSimulator(
            double x,
            double y,
            double z,
            double roll,
            double pitch,
            double yaw,
            double vx,
            double vy,
            double vz
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.roll = roll;
        this.pitch = pitch;
        this.yaw = normalizeHeading(yaw);
        this.angle = this.yaw;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.verticalSpeed = vz;
        this.speed = Math.sqrt((vx * vx) + (vy * vy));
        this.indicatedAirspeed = this.speed;
    }

    public synchronized void setCommandTarget(double x, double y, double z) {
        this.commandX = x;
        this.commandY = y;
        this.commandZ = z;
        refreshGuidanceTargets();
    }

    public synchronized void setCommandTarget(double x, double y) {
        this.commandX = x;
        this.commandY = y;
        refreshGuidanceTargets();
    }

    public synchronized void setTargetAltitude(double z) {
        this.commandZ = z;
        this.targetAltitudeM = z;
    }

    public synchronized void setDestination(double x, double y) {
        this.destX = x;
        this.destY = y;
    }

    public synchronized void setDestination(double x, double y, double z) {
        this.destX = x;
        this.destY = y;
        this.destZ = z;
    }

    public synchronized void setTargetHeadingDeg(double targetHeadingDeg) {
        this.targetHeadingDeg = normalizeHeading(targetHeadingDeg);
    }

    public synchronized void setTargetAltitudeM(double targetAltitudeM) {
        this.targetAltitudeM = targetAltitudeM;
        this.commandZ = targetAltitudeM;
    }

    public synchronized void setTargetSpeedMps(double targetSpeedMps) {
        this.targetSpeedMps = targetSpeedMps;
    }

    public synchronized void setPitch(double pitch) {
        this.pitch = pitch;
    }

    public synchronized void setRoll(double roll) {
        this.roll = roll;
    }

    public synchronized void setYaw(double yaw) {
        this.yaw = normalizeHeading(yaw);
        this.angle = this.yaw;
    }

    public synchronized void setIndicatedAirspeed(double indicatedAirspeed) {
        this.indicatedAirspeed = indicatedAirspeed;
    }

    public synchronized void setEngineRPM(double engineRPM) {
        this.engineRPM = engineRPM;
    }

    public synchronized int getId() {
        return id;
    }

    public synchronized void setId(int id) {
        this.id = id;
    }

    public synchronized double getX() {
        return x;
    }

    public synchronized double getY() {
        return y;
    }

    public synchronized double getZ() {
        return z;
    }

    public synchronized double getAngle() {
        return angle;
    }

    public synchronized double getPitch() {
        return pitch;
    }

    public synchronized double getRoll() {
        return roll;
    }

    public synchronized double getYaw() {
        return yaw;
    }

    public synchronized double getIndicatedAirspeed() {
        return indicatedAirspeed;
    }

    public synchronized double getEngineRPM() {
        return engineRPM;
    }

    public synchronized double getDestX() {
        return destX;
    }

    public synchronized double getDestY() {
        return destY;
    }

    public synchronized double getDestZ() {
        return destZ;
    }

    public synchronized double getCommandX() {
        return commandX;
    }

    public synchronized double getCommandY() {
        return commandY;
    }

    public synchronized double getCommandZ() {
        return commandZ;
    }

    public synchronized double getTargetHeadingDeg() {
        return targetHeadingDeg;
    }

    public synchronized double getTargetAltitudeM() {
        return targetAltitudeM;
    }

    public synchronized double getTargetSpeedMps() {
        return targetSpeedMps;
    }

    public synchronized double getVx() {
        return vx;
    }

    public synchronized double getVy() {
        return vy;
    }

    public synchronized double getVz() {
        return vz;
    }

    public synchronized String getTacticalState() {
        return tacticalState;
    }

    public synchronized void setTacticalState(String tacticalState) {
        this.tacticalState = tacticalState;
    }

    public synchronized String getTeam() {
        return team;
    }

    public synchronized void setTeam(String team) {
        this.team = team;
    }

    public synchronized boolean isSimulinkControlled() {
        return simulinkControlled;
    }

    public synchronized void setSimulinkControlled(boolean simulinkControlled) {
        this.simulinkControlled = simulinkControlled;
    }

    private synchronized void refreshGuidanceTargets() {
        double dx = commandX - x;
        double dy = commandY - y;
        if (Math.abs(dx) > 1e-9 || Math.abs(dy) > 1e-9) {
            this.targetHeadingDeg = normalizeHeading(Math.toDegrees(Math.atan2(dy, dx)));
        }
        this.targetAltitudeM = commandZ;
        if (targetSpeedMps <= 0.0) {
            this.targetSpeedMps = speed;
        }
    }

    private double normalizeHeading(double heading) {
        double normalized = heading % 360.0;
        if (normalized < 0) {
            normalized += 360.0;
        }
        return normalized;
    }
}
