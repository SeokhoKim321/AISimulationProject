// ==========================
// Aircraft.java
// ==========================

package com.example.ai;

public class Aircraft { // 항공기 정보 (Body)
    private double x, y;
    private double speed = 2.0;
    private double angle = 0.0;
    private final String team;

    // 최종 목적지 (임무)
    private double destX, destY;


    // [두뇌로부터 받는 현재 명령] (매 순간 변하는 목표 지점)
    private double commandX, commandY;

    // [추가] GUI 표시용 상태 변수
    private String tacticalState = "Cruising";


    // [설정] 기동 성능
    private static final double MAX_TURN_RATE = 5.0;

    public Aircraft(double startX, double startY, String team, double destX, double destY) {
        this.x = startX;
        this.y = startY;
        this.team = team;
        this.destX = destX;
        this.destY = destY;

        // 초기 명령은 목적지로 설정
        this.commandX = destX;
        this.commandY = destY;

        // 초기 각도 설정
        double dx = destX - startX;
        double dy = destY - startY;
        this.angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
    }

    // --- [실행] 두뇌가 정해준 command 좌표로 기동 ---
    public void executeMovement() {
        // 도착 판정 (최종 목적지 기준)
        double distToDest = Math.sqrt(Math.pow(destX - x, 2) + Math.pow(destY - y, 2));
        if (distToDest < 10.0) return;

        // [물리 엔진] 두뇌가 찍어준 'command' 지점을 향해 조향
        steerTo(this.commandX, this.commandY);
        moveForward();
    }

    // --- [명령 수신] 두뇌가 호출하는 메소드 ---
    public void setCommandTarget(double targetX, double targetY) {
        this.commandX = targetX;
        this.commandY = targetY;
    }

    // --- [물리 엔진 로직] ---
    private void steerTo(double targetX, double targetY) {
        double dx = targetX - this.x;
        double dy = targetY - this.y;
        double desiredAngle = Math.toDegrees(Math.atan2(dy, dx)) + 90;

        double diff = desiredAngle - this.angle;
        while (diff <= -180) diff += 360;
        while (diff > 180) diff -= 360;

        if (Math.abs(diff) < MAX_TURN_RATE) {
            this.angle = desiredAngle;
        } else {
            this.angle += (diff > 0) ? MAX_TURN_RATE : -MAX_TURN_RATE;
        }
    }

    private void moveForward() {
        double radian = Math.toRadians(this.angle - 90);
        this.x += Math.cos(radian) * this.speed;
        this.y += Math.sin(radian) * this.speed;
    }

    // [추가] GUI가 상태를 읽을 수 있게 해주는 Getter
    public String getTacticalState() {
        return this.tacticalState;
    }

    // [추가] Agent(두뇌)가 상태를 알려줄 때 사용하는 Setter
    public void setTacticalState(String state) {
        this.tacticalState = state;
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public String getTeam() { return team; }
    public double getDestX() { return destX; }
    public double getDestY() { return destY; }
}