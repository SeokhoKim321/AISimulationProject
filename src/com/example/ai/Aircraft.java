// ==========================
// Aircraft.java
// ==========================

package com.example.ai;

public class Aircraft {
    // 1. 물리 속성 (단위: 미터, 초, 도)
    private double x, y;
    private double speed; // m/s
    private double angle; // 0=East, 90=North (CCW)

    // 2. 항법 속성
    private double destX, destY;       // 최종 목적지
    private double commandX, commandY; // 현재 가야 할 목표점(경유지)

    private String tacticalState = "Cruising"; // 상태 정보
    private String team = "blue";

    // 선회 능력 (초당 30도 회전)
    private static final double MAX_TURN_RATE = 30.0;

    // 생성자
    public Aircraft(double x, double y, double speed, double angle) {
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.angle = angle; // 입력받은 각도 그대로 사용

        // 초기 목표는 내 앞 1km 지점
        double rad = Math.toRadians(angle);
        this.commandX = x + Math.cos(rad) * 1000;
        this.commandY = y + Math.sin(rad) * 1000;
    }

    // [핵심] 물리 엔진 업데이트 (dt: 경과 시간, 초 단위)
    public void executeMovement(double dt) {
        // 1. 목표 각도 계산 (atan2는 수학 표준 각도를 반환함)
        double dx = commandX - x;
        double dy = commandY - y;
        double targetAngle = Math.toDegrees(Math.atan2(dy, dx));

        // 2. 각도 차이 계산 (-180 ~ 180 정규화)
        double diff = targetAngle - angle;
        while (diff <= -180) diff += 360;
        while (diff > 180) diff -= 360;

        // 3. 부드러운 선회 (최대 회전각 제한)
        double maxTurnStep = MAX_TURN_RATE * dt;

        if (Math.abs(diff) < maxTurnStep) {
            angle = targetAngle; // 거의 다 왔으면 바로 고정
        } else {
            angle += (diff > 0) ? maxTurnStep : -maxTurnStep;
        }

        // 4. 전진 (삼각함수: 0도=cos1, sin0 = +x방향 = 동쪽)
        double rad = Math.toRadians(angle);
        x += Math.cos(rad) * speed * dt;
        y += Math.sin(rad) * speed * dt;
    }

    // 명령 수신
    public void setCommandTarget(double x, double y) {
        this.commandX = x;
        this.commandY = y;
    }

    // 최종 목적지 설정 (Agent가 참고용)
    public void setDestination(double x, double y) {
        this.destX = x;
        this.destY = y;
    }

    // Getters & Setters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getAngle() { return angle; }
    public double getDestX() { return destX; }
    public double getDestY() { return destY; }
    public String getTacticalState() { return tacticalState; }
    public void setTacticalState(String s) { this.tacticalState = s; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
}