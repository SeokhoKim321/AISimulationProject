// ==========================
// Aircraft.java
// ==========================

package com.example.ai;

public class Aircraft {
    // 1. 물리 속성 (단위: 미터, 초, 도)
    private double x, y;  // East, North
    private double speed; // m/s
    private double angle; // 0=East, 90=North (CCW)

    // [추가] 3차원 물리 속성
    private double z;  // 고도(Altitude, m)
    private double verticalSpeed; // 수직속도 (m/s)

    // 2. 항법 속성
    private double destX, destY;       // 최종 목적지
    private double destZ ;   // 최종 목적지 고도
    private double commandX, commandY; // 현재 가야 할 목표점(경유지)
    private double commandZ;   // 현재 가야할 목표 고도


    // 선회 능력 (초당 30도 회전)
    private static final double MAX_TURN_RATE = 8.1;
    // [추가] 성능 제한 상수 (UAM 특성 반영)
    private static final double MAX_CLIMB_RATE = 10.0;    // 최대 상승률 (10m/s)
    private static final double MAX_DESCENT_RATE = 5.0;   // 최대 하강률 (5m/s)

    private String tacticalState = "Cruising"; // 상태 정보
    private String team = "blue";



    // 생성자
    public Aircraft(double x, double y, double z, double speed, double angle) {
        this.x = x;
        this.y = y;
        this.z = z;  // 초기 고도 설정
        this.speed = speed;
        this.angle = angle; // 입력받은 각도 그대로 사용

        // 초기 목표는 내 앞 1km 지점
        double rad = Math.toRadians(angle);
        this.commandX = x + Math.cos(rad) * 1000;
        this.commandY = y + Math.sin(rad) * 1000;
        this.commandZ = z;  //  초기 목표 고도는 현재 고도
    }

    // [핵심] 물리 엔진 업데이트 (dt: 경과 시간, 초 단위)
    public void executeMovement(double dt) {
        // 수평 기동
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

        // 수직 기동
        double altDiff = commandZ - z;

        // 오차가 크면 최대 속도로, 작으면 비례해서 줄임 (부드러운 도착)
        // 여기서는 간단하게 최대 상승/하강률로 제한하는 로직 적용

        if (Math.abs(altDiff) < 0.1) {
            verticalSpeed = 0; // 목표 도달 시 정지
            z = commandZ;      // 미세 오차 보정
        } else if (altDiff > 0) {
            // 상승 필요: 오차가 크면 MAX_CLIMB, 작으면 오차만큼만
            verticalSpeed = Math.min(MAX_CLIMB_RATE, altDiff / dt);
        } else {
            // 하강 필요: 오차가 크면 MAX_DESCENT, 작으면 오차만큼만
            verticalSpeed = Math.max(-MAX_DESCENT_RATE, altDiff / dt);
        }

        // 고도 업데이트
        z += verticalSpeed * dt;

        // [중요] 지면 충돌 방지 (Ground Clamping)
        if (z < 0) {
            z = 0;
            verticalSpeed = 0;
        }

    }

    // [추가] 3D 목표 설정
    public void setCommandTarget(double x, double y, double z) {
        this.commandX = x;
        this.commandY = y;
        this.commandZ = z;
    }

    // 명령 수신 2D 목표 설정(고도 유지)
    public void setCommandTarget(double x, double y) {
        this.commandX = x;
        this.commandY = y;
        // commandZ는 변경하지 않음(현재 목표 고도 유지)
    }

    // [추가] 고도 변경 명령만 따로 줄 때
    public void setTargetAltitude(double z) {
        this.commandZ = z;
    }

    // 최종 목적지 설정 (Agent가 참고용)
    public void setDestination(double x, double y) {
        this.destX = x;
        this.destY = y;
    }

    // Getters & Setters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getAngle() { return angle; }
    public double getDestX() { return destX; }
    public double getDestY() { return destY; }
    public String getTacticalState() { return tacticalState; }
    public void setTacticalState(String s) { this.tacticalState = s; }
    public String getTeam() { return team; }
    public void setTeam(String team) { this.team = team; }
}