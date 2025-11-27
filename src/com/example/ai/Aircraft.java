package com.example.ai;


public class Aircraft { // 항공기 정보
    private double x, y;     // 항공기의 현재 X,Y 좌표, double 타입은 100.5처럼 소수점이 있는 실수를 담겠다는 뜻
    private double speed = 4.0;  // 항공기의 기본 비행속도
    private double angle = 0.0;  // 항공기가 바라보는 방향의 각도
    private final String team; // final : 생성자에서 딱 한 번만 값이 할당될 수 있으며, 그 이후에는 바꿀수 없다


    // 비행 목표(FMS)
    private double destX, destY;

    // 충돌 회피(TCAS)
    private Aircraft closestAircraft;  // 이 항공기에 가장 가깝게 감지된 '다른 항공기' 객체, Aircraft : 내가 만든 타입

    // 인지모델
    private String tacticalState;


    // 생성자 수정
    public Aircraft(double startX, double startY, String team, double destX, double destY) {
        // new Aircraft()로 항공기를 만들 때 호출되는 코드. 최소 5개의 정보가 필요함
        // new Aircraft(...)로 객체를 만들 때 단 한 번 실행되는 '초기 설정' 메소드
        this.x = startX;
        this.y = startY;
        this.team = team;
        this.destX = destX;
        this.destY = destY;
        this.tacticalState = "Cruising";  // 항공기의 초기상태를 'Cruising"으로 설정

        // [수정 1] 생성되자마자 목적지를 바라보도록 초기 각도 설정
        double dx = destX - startX;
        double dy = destY - startY;
        this.angle = Math.toDegrees(Math.atan2(dy,dx)) + 90;


    }

    public double getX() {
        return x;
    } // SimulationGUI가 이 항공기의 현재 X좌표를 읽을 수 있게 해주는 '공개' 메소드

    // return x : 이 객체 자신의 비공개 변수 x의 값을 반환(return) 이것을 'Getter'라고 부름
    public double getY() {
        return y;
    } // SimulationGUI가 이 항공기의 현재 Y좌표를 읽을 수 있게 해주는 '공개' 메소드

    public double getAngle() {
        return angle;
    } // SimulationGUI가 이 항공기의 현재 각도를 읽을 수 있게 해주는 '공개' 메소드

    public String getTeam() {
        return this.team;
    }// SimulationGUI가 이 항공기의 소속 팀을 읽을 수 있게 해주는 '공개' 메소드

    public double getDestX() {
        return this.destX;
    }

    public double getDestY() {
        return this.destY;
    }

    public String getTacticalState() { // SimulationGUI가 이 항공기의 현재 상태(예: "Evade")를 읽을 수 있게 해주는 '공개' 메소드
        return this.tacticalState; // tacticalState 변수에 저장된 문자열을 반환
    }


    // [설정] 항공기 기동 성능 상수

    // --- [Aircraft.java 하단부 메소드 전체 교체] ---

    private static final double MAX_TURN_RATE = 5.0; // 선회 민첩성

    public void executeMovement() {
        // 1. 도착 판정 : 목적지까지 남은 거리 계산
        double distToDest = Math.sqrt(Math.pow(destX - x, 2) + Math.pow(destY - y, 2));

        // 2. 도착 확인 : 만약 도착지까지 남은 거리가 10.0 미만이라면?
        if (distToDest < 10.0) return;

        // 3. 상태에 따른 행동 : Agent가 내려준 명령(tacticalState)에 따라 행동을 결정함
        switch (this.tacticalState) {
            case "Evade":
                // 4. 회피 조건 확인 :
                // 회피 대상(closestAircraft)이 존재하고,
                // 그 대상이 내 진행 방향 앞쪽(45도 이내)에 있을 때만 회피 실행
                if (this.closestAircraft != null && isThreatInFront(this.closestAircraft)) {
                    // 5. 스마트 회피 실행 : 정면인지 교차인지 판단해서 최적의 회피 수행
                    smartAvoid(this.closestAircraft);

                } else {
                    // 위협이 사라졌거나, 내 뒤로 지나갔다면 원래 목적지로 비행
                    steerTo(this.destX, this.destY);
                }
                break;

            case "Avoid-Cruise":
            case "Cruising":
            default:
                // 그 외의 상태(순항, 주의 등) 에서는 목적지를 향해 비행
                steerTo(this.destX, this.destY);
                break;
        }
        // 6. 실제 이동 : 위에서 결정된 각도 방향으로 기체를 전진
        moveForward();
    }

    // [핵심 알고리즘] 상황에 따른 최적 회피
    private void smartAvoid(Aircraft threat) {
        // 1. 상대방과의 각도 차이 계산 : 내 진행방향과 상대 진행방향의 차이(angleDiff)를 구함
        double angleDiff = Math.abs(this.angle - threat.getAngle());
        if (angleDiff > 180) angleDiff = 360 - angleDiff;  // 각도 차이를 0 ~ 180도로 정규화

        // 2. 상황 판단 : 각도 차이가 160도 이상이면 '정면 대치(Head-on)'로 판단
        if (angleDiff > 160) {
            // [상황 A: 정면] 무조건 서로의 '오른쪽'으로 피하는 국제 표준을 따름
            turnRightForcefully(threat);
        } else {
            // [상황 B: 교차] 상대방의 '꼬리 뒤쪽' 공간으로 파고듦 (절대 따라가지 않음)
            aimBehindThreat(threat);
        }
    }

    // [A] 정면 대치 시: 내 진행방향 기준 오른쪽 90도 방향으로 이탈
    private void turnRightForcefully(Aircraft threat) {
        // 1. 내 진행방향 벡터 계산
        // angle 은 12시 기준이므로 -90을 해줘야 수학적 0도(3시) 기준이 됨
        double myRad = Math.toRadians(this.angle - 90);
        double dx = Math.cos(myRad); // 내 진행 방향의 X 성분
        double dy = Math.sin(myRad); // 내 진행 방향의 Y 성분

        // 2. 오른쪽 직교 벡터 생성:
        // 벡터(dx, dy)의 오른쪽 90도 벡터는 (dy, - dx). 수학 공식임.
        // 이 방향으로 200픽셀 떨어진 곳에 가상의 목표 지점(targetX, targetY)을 찍음
        double targetX = this.x + dy * 200;
        double targetY = this.y - dx * 200;

        // 3. 그 가상의 목표 지점을 향해 선회함
        steerTo(targetX, targetY);
    }

    // [B] 교차 시: 상대방의 꼬리 뒤쪽 좌표를 목표로 설정
    private void aimBehindThreat(Aircraft threat) {
        // 1. 상대방의 속도 벡터 (진행 방향) 계산:
        // 상대방의 각도(angle)를 이용해 X, Y 속도 성분(tVx, tVy)을 구함
        double threatRad = Math.toRadians(threat.getAngle() - 90);
        double tVx = Math.cos(threatRad);
        double tVy = Math.sin(threatRad);

        // 2. 상대방 현재 위치에서 '뒤쪽'으로 150픽셀 떨어진 지점 계산 ("고스트 타겟")
        // (상대방이 앞으로 가고 있으니, 그 뒤쪽은 안전 공간임)
        double ghostX = threat.getX() - (tVx * 150);
        double ghostY = threat.getY() - (tVy * 150);

        // 3. 그 안전 공간을 향해 비행
        steerTo(ghostX, ghostY);
    }

    // [판단] 시야각 좁히기 (좌우 45도)
    private boolean isThreatInFront(Aircraft threat) {
        // 1. 상대방을 향하는 벡터(dx, dy) 계산
        double dx = threat.getX() - this.x;
        double dy = threat.getY() - this.y;

        // 2. 상대방이 있는 절대 각도(angleToThreat) 계산
        double angleToThreat = Math.toDegrees(Math.atan2(dy, dx)) + 90;

        // 3. 내 진행방향(this.angle)과 상대방 방향의 차이(diff) 계산
        double diff = Math.abs(angleToThreat - this.angle);
        while (diff > 180) diff -= 360; // 각도 정규화(- 180 ~ 180)

        // 4. 각도 차이가 45도 미만(좌우 45도, 총 90도 시야각) 일 때만 'true' 반환
        // 즉, 내 시야 밖으로 벗어난 적은 무시
        return Math.abs(diff) < 45.0;
    }

    // [물리] 조향 및 이동
    private void steerTo(double targetX, double targetY) {
        // 1. 목표 지점까지의 벡터 및 각도 계산
        double dx = targetX - this.x;
        double dy = targetY - this.y;
        double desiredAngle = Math.toDegrees(Math.atan2(dy, dx)) + 90;

        // 2. 현재 각도와 목표 각도의 차이 계산 및 정규화

        double diff = desiredAngle - this.angle;
        while (diff <= -180) diff += 360;
        while (diff > 180) diff -= 360;

        // 3. 선회율 제한 적용
        // 한 번에 확 꺽지 않고, 최대 5도씩만 회전

        if (Math.abs(diff) < MAX_TURN_RATE) {
            this.angle = desiredAngle;
        } else {
            this.angle += (diff > 0) ? MAX_TURN_RATE : -MAX_TURN_RATE;
        }
    }

    private void moveForward() {
        // 1. 현재 설정된 각도(this.angle)를 기준으로 이동할 X,Y 거리 계산
        // 삼각함수 cos, sin을 사용하여 대각선 이동 거리를 구함
        double radian = Math.toRadians(this.angle - 90);

        // 2. 현재 위치에 속도(speed)만큼 더해서 좌표 계산
        this.x += Math.cos(radian) * this.speed;
        this.y += Math.sin(radian) * this.speed;
    }

    // Agent(두뇌)가 상태를 설정할 때 사용하는 메소드
    public void setTacticalState(String state) {
        this.tacticalState = state;
    }

    // Agent(두뇌)가 감지된 타겟 정보를 넘겨줄 때 사용하는 메소드
    public void setClosestAircraft(Aircraft target) {
        this.closestAircraft = target;
    }
}



