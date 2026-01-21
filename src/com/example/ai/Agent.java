// ==========================
// Agent.java
// ==========================

package com.example.ai;

import com.example.ai.memory.*;
import com.example.ai.tree.*;
import java.util.List;
import java.util.Random;

public class Agent { // 조종사 (Brain)
    private final WorkingMemory wm;   // 작업 기억(그래프 그려지는 활성도 저장소)
    private final Behavior behaviorTree; // 행동 트리 (판단 규칙 집합)
    private final Blackboard blackboard; // 칠판 ( 데이터 공유 저장소)
    private final Random random;
    // [중요] 조종사가 기억해야할 대상들. 계기판이라도 볼 수 있음.
    private final String[] cellNames = {"ClosestAircraft", "Fuel Level", "Altitude", "Obstacle"};

    private Aircraft aircraft; // 내가 조종하는 몸체

    // 생성자
    public Agent(Aircraft aircraft) {
        this.aircraft = aircraft;

        // 두뇌 부품 초기화
        this.wm = new WorkingMemory();
        for (String name : cellNames) {
            this.wm.addCell(new MemoryCell(name));
        }
        this.blackboard = new Blackboard();
        this.random = new Random();
        this.behaviorTree = createBehaviourTree();
    }


    // update 메소드 , 1초에 60번 호출되며, 조종사의 생각 흐름 담당
    public void update(Airspace airspace) {
        // 1. 인식(perceive): 항공기 위협
        Aircraft threat = perceiveThreat(airspace);



        // 2. [추가] 인식: 장애물 위협 측정
        double minObstacleDist = Double.MAX_VALUE;
        // 안전장치: 장애물 리스트가 null이 아닐 때만 계산

        if (airspace.getObstacles() != null) {
            for (Obstacle obs : airspace.getObstacles()) {
                double dist = obs.getDistance(aircraft.getX(), aircraft.getY());
                // 가장 가까운 놈만 기억한다.
                if (dist < minObstacleDist) minObstacleDist = dist;
            }
        }

        // 계산된 최단 거리 블랙보드에 기록
        this.blackboard.set("closestObstacleDistance", minObstacleDist);

        // 3. 인지 필터 (위에서 수정한 getNextFocus 실행)
        // 이제 여기서 blackboard의 closestObstacleDistance를 읽어서 확률을 조작함
        String focusTarget = getNextFocus();

        // 4. 작업 기억 업데이트(선택된 대상만 활성도 올라감)
        this.wm.update(focusTarget, 0.016); // 0.016은 약 1프레임(60fps)
        // =================================================================
        // [★수정된 부분] Step 5: 변수 선언을 먼저 해야 함!
        // =================================================================

        // 1) 먼저 변수(double threatActivation)를 만들어서 값을 저장합니다.
        double threatActivation = getActivationLevel("ClosestAircraft");

        // 5. 블랙보드 기록 (그래프용 데이터)
        // 적기(ClosestAircraft)에 대한 활성도가 뚝 떨어지는지 확인하는 핵심 지표
        this.blackboard.set("threatActivation", getActivationLevel("ClosestAircraft"));
        // =================================================================
        // [★ 핵심] 지각적 맹 (Perceptual Blindness) 판정
        // 기억이 0.2(20%) 미만으로 떨어지면, 센서가 탐지했어도 "못 본 척" 한다.
        // =================================================================
        if (threatActivation < 0.2) {
            // 뇌가 정보를 차단해버림
            this.blackboard.set("closestAircraftDistance", Double.MAX_VALUE);
            threat = null; // 위협 객체를 null로 만듦 -> 회피 로직 작동 안 함
        }
        // =================================================================

        // 6. 의사결정(행동 트리 실행)
        // threatActivation이 낮으면 '위험'을 인지 못하고 Failure 반환
        this.blackboard.set("current_state", this.aircraft.getTacticalState());
        this.behaviorTree.update(this.blackboard);

        // 7. 명령(물리적 이동)
        String decision = (String) this.blackboard.get("tactical_state");
        if (decision == null) decision = "Cruising";
        aircraft.setTacticalState(decision);
        calculateAndCommand(decision, threat);
    }

    // --- [인식] 위협 찾기 ---
    private Aircraft perceiveThreat(Airspace airspace) {
        List<Aircraft> allAircraft = airspace.getAllAircraft();
        Aircraft closest = null;
        double minDist = Double.MAX_VALUE;

        for (Aircraft other : allAircraft) {
            if (other == this.aircraft) continue;
            double dist = Math.sqrt(Math.pow(aircraft.getX() - other.getX(), 2) +
                    Math.pow(aircraft.getY() - other.getY(), 2));
            if (dist < minDist) {
                minDist = dist;
                closest = other;
            }
        }

        // 블랙보드 업데이트
        double reportDist = (closest != null) ? minDist : Double.MAX_VALUE;
        this.blackboard.set("closestAircraftDistance", reportDist);

        // 내 상태도 기록
        // (주의: aircraft에는 tacticalState 변수가 사라졌으므로, Agent가 기억하는 값을 써야 함.
        //  여기서는 생략하거나 blackboard의 tactical_state를 재활용)

        return closest;
    }


    // =========================================================
    // [핵심 논문 로직] 주의(Attention) 모델
    // 빌딩이 가까우면 시선이 빌딩에 쏠려, 적기를 못 보게 만듦
    // =========================================================
    private String getNextFocus() {
        double distToObstacle = getClosestObstacleDistance();
        // update()에서 넣어준 '진짜 거리'를 가져옴
        Object distObj = this.blackboard.get("closestAircraftDistance");
        double distToThreat = (distObj != null) ? (Double) distObj : Double.MAX_VALUE;

        double roll = random.nextDouble();

        // 1. [긴급 경보] 250px 초근접 (생존 본능)
        // 도심이든 개활지든 당장 다른 항공기와 부딪치게 생겼으면 이것부터 봄
        if (distToThreat < 250.0) {
            if (roll < 0.60) return "ClosestAircraft";
            else if (roll < 0.90) return "Obstacle";
            else return "Altitude";
        }

        // 2. [도심 협곡 로직] 장애물이 있을 때만 작동
        else if (distToObstacle < 300.0) { // 터널링
            if (roll < 0.85) return "Obstacle";
            else return "Altitude";
        }
        else if (distToObstacle < 500.0) { // 주의 분산
            if (roll < 0.40) return "Obstacle";
            else if (roll < 0.70) return "ClosestAircraft"; // 가끔 봄
            else return "Altitude";
        }

        // 3. [개활지 로직] 장애물 있는 상황과 없는 상황 비교를 위함
        else {
            // 장애물이 없으면 시야가 트여 있으므로 1000px 밖에서 미리 발견해야 함
            if (distToThreat < 1000.0) {
                // 미리 발견 -> 기억 1.0 충전 -> 400px 행동 트리 발동 -> 안전 거리(Safe) 확보
                if (roll < 0.95) return "ClosestAircraft";
                else return "Altitude";
            }

            // 적기가 아주 멀리 있을 때 (평시 비행)
            if (roll < 0.25) return "ClosestAircraft";
            else if (roll < 0.50) return "Obstacle";
            else if (roll < 0.75) return "Altitude";
            else return "Fuel Level";
        }
    }

    // [보조 메소드] 가장 가까운 장애물 거리 계산
    private double getClosestObstacleDistance() {
        // 수정: Blackboard 클래스에는 getOrDefault가 없으므로 get()으로 가져온 뒤 null 체크를 합니다.
        Object val = this.blackboard.get("closestObstacleDistance");

        if (val != null) {
            return (Double) val;
        } else {
            // 값이 없으면(아직 측정 안 됨) 아주 먼 거리로 취급
            return Double.MAX_VALUE;
        }
    }



    // --- [행동 계산] 결정된 상태에 따라 좌표 계산 ---
    private void calculateAndCommand(String state, Aircraft threat) {

        // [추가] 몸체(Aircraft)에게 현재 상태 이름표를 붙여줍니다. (GUI 표시용)
        aircraft.setTacticalState(state);


        double targetX, targetY;

        switch (state) {
            case "Evade":
                // 위협이 있고 내 앞에 있다면 -> 스마트 회피 좌표 계산
                if (threat != null && isThreatInFront(threat)) {
                    double[] evadeCoords = calculateSmartAvoid(threat);
                    targetX = evadeCoords[0];
                    targetY = evadeCoords[1];
                } else {
                    // 위협이 없거나 지나갔으면 -> 원래 목적지로
                    targetX = aircraft.getDestX();
                    targetY = aircraft.getDestY();
                }
                break;

            case "Avoid-Cruise":
                // (이전의 gentleAvoid 로직을 여기에 구현 가능, 지금은 생략하고 목적지로)
                targetX = aircraft.getDestX();
                targetY = aircraft.getDestY();
                break;

            case "Cruising":
            default:
                targetX = aircraft.getDestX();
                targetY = aircraft.getDestY();
                break;
        }

        // 4. 몸체에 최종 명령 전달
        aircraft.setCommandTarget(targetX, targetY);
    }

    // --- [판단 로직] 시야각 확인 ---
    private boolean isThreatInFront(Aircraft threat) {
        double dx = threat.getX() - aircraft.getX();
        double dy = threat.getY() - aircraft.getY();
        double angleToThreat = Math.toDegrees(Math.atan2(dy, dx)) + 90;

        double diff = Math.abs(angleToThreat - aircraft.getAngle());
        while (diff > 180) diff -= 360;

        return Math.abs(diff) < 45.0;
    }

    // --- [전술 계산] 스마트 회피 좌표 계산 ---
    private double[] calculateSmartAvoid(Aircraft threat) {
        double angleDiff = Math.abs(aircraft.getAngle() - threat.getAngle());
        if (angleDiff > 180) angleDiff = 360 - angleDiff;

        if (angleDiff > 160) {
            return calculateRightTurn(threat); // 정면 대치
        } else {
            return calculateTailAim(threat);   // 교차 상황
        }
    }

    // [계산 A] 우측 회피 좌표
    private double[] calculateRightTurn(Aircraft threat) {
        double myRad = Math.toRadians(aircraft.getAngle() - 90);
        double dx = Math.cos(myRad);
        double dy = Math.sin(myRad);

        // 오른쪽 90도 방향 200px 앞
        double tx = aircraft.getX() + dy * 200;
        double ty = aircraft.getY() - dx * 200;
        return new double[]{tx, ty};
    }

    // [계산 B] 꼬리 물기 좌표
    private double[] calculateTailAim(Aircraft threat) {
        double threatRad = Math.toRadians(threat.getAngle() - 90);
        double tVx = Math.cos(threatRad);
        double tVy = Math.sin(threatRad);

        // 상대방 뒤쪽 150px 지점
        double tx = threat.getX() - (tVx * 150);
        double ty = threat.getY() - (tVy * 150);
        return new double[]{tx, ty};
    }

    // --- [행동 트리] ---
    private Behavior createBehaviourTree() {
        Selector root = new Selector("Root");
        Selector evasionLogic = new Selector("Evasion Logic");

        Sequence stopEvasion = new Sequence("Stop");
        stopEvasion.addChildren(
                new IsInState("Evading?", "Evade"),
                new IsNot(new IsConflictDetected("Safe?", 500.0)),
                new SetTacticalState("Cruise", "Cruising")
        );

        Sequence startEvasion = new Sequence("Start");
        startEvasion.addChildren(
                new IsConflictDetected("Danger?", 400.0),
                new SetTacticalState("Evade", "Evade")
        );

        Sequence stayEvasion = new Sequence("Stay");
        stayEvasion.addChildren(new IsInState("Still Evading?", "Evade"));

        evasionLogic.addChildren(stopEvasion, startEvasion, stayEvasion);
        root.addChildren(evasionLogic, new SetTacticalState("Default", "Cruising"));
        return root;
    }

    // (getActivationLevel 등 기타 메소드는 그대로 유지)
    public double getActivationLevel(String cellName) {
        return this.wm.cells.get(cellName).getActivationLevel();
    }


    // 헬퍼 메소드: 가장 가까운 항공기 찾기
    private Aircraft findClosestAircraft(List<Aircraft> allAircraft) {
        Aircraft closest = null;
        double minDistance = Double.MAX_VALUE;
        for (Aircraft other : allAircraft) {
            if (other == this.aircraft) continue; // 자신의 몸체는 제외
            double dx = this.aircraft.getX() - other.getX();
            double dy = this.aircraft.getY() - other.getY();
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance < minDistance) {
                minDistance = distance;
                closest = other;
            }
        }
        return closest;
    }


}