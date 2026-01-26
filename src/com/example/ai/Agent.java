package com.example.ai;

import com.example.ai.memory.*;
import com.example.ai.tree.*;
import java.util.List;
import java.util.Random;

public class Agent {
    private final WorkingMemory wm;
    private final Behavior behaviorTree;
    private final Blackboard blackboard;
    private final Random random;

    private final String[] cellNames = {"ClosestAircraft", "Fuel Level", "Altitude", "Obstacle"};
    private Aircraft aircraft;

    public Agent(Aircraft aircraft) {
        this.aircraft = aircraft;
        // 두뇌 부품 초기화 (기존 클래스 재사용)
        this.wm = new WorkingMemory();
        for (String name : cellNames) {
            this.wm.addCell(new MemoryCell(name));
        }
        this.blackboard = new Blackboard();
        this.random = new Random();
        this.behaviorTree = createBehaviourTree();
    }

    public void update(Airspace airspace) {
        // 1. 인식 (Perception)
        Aircraft threat = perceiveThreat(airspace);

        // 장애물 거리 측정
        double minObstacleDist = Double.MAX_VALUE;
        if (airspace.getObstacles() != null) {
            for (Obstacle obs : airspace.getObstacles()) {
                double dist = obs.getDistance(aircraft.getX(), aircraft.getY());
                if (dist < minObstacleDist) minObstacleDist = dist;
            }
        }
        this.blackboard.set("closestObstacleDistance", minObstacleDist);

        // 2. 주의 집중 (Attention)
        String focusTarget = getNextFocus();
        this.wm.update(focusTarget, 0.016); // 1프레임 시간

        // 3. 인지적 맹 (Tunneling) 판정
        double threatActivation = getActivationLevel("ClosestAircraft");
        this.blackboard.set("threatActivation", threatActivation);

        // [중요] 활성도가 낮으면 물리적으로 보여도(threat != null) 못 본 척함
        if (threatActivation < 0.2) {
            this.blackboard.set("closestAircraftDistance", Double.MAX_VALUE);
            threat = null;
        }

        // 4. 의사결정 (Behavior Tree)
        this.blackboard.set("current_state", this.aircraft.getTacticalState());
        this.behaviorTree.update(this.blackboard);

        // 5. 명령 하달 (Action)
        String decision = (String) this.blackboard.get("tactical_state");
        if (decision == null) decision = "Cruising";

        // [복귀 로직] Evade 상태여도 적기가 내 앞(시야각)에 없으면 즉시 복귀
        if ("Evade".equals(decision)) {
            if (threat == null || !isThreatInFront(threat)) {
                decision = "Cruising";
                this.blackboard.set("tactical_state", "Cruising");
            }
        }

        aircraft.setTacticalState(decision);
        calculateAndCommand(decision, threat);
    }

    // --- [명령 계산] 순수 수학 좌표계 사용 ---
    private void calculateAndCommand(String state, Aircraft threat) {
        double targetX, targetY;

        if ("Evade".equals(state) && threat != null) {
            // 회피: 오른쪽으로 꺾어서 도망
            double[] evadeCoords = calculateRightTurn();
            targetX = evadeCoords[0];
            targetY = evadeCoords[1];
        } else {
            // 순항: 원래 목적지로
            targetX = aircraft.getDestX();
            targetY = aircraft.getDestY();
        }
        aircraft.setCommandTarget(targetX, targetY);
    }

    // --- [핵심] 우측 회피 좌표 계산 (Standard Math) ---
    private double[] calculateRightTurn() {
        // 내 현재 각도 (Radian)
        double currentRad = Math.toRadians(aircraft.getAngle());

        // 오른쪽 90도 = 시계 방향 90도 = 수학적으로 -90도
        double avoidRad = currentRad - Math.toRadians(90);

        // 현재 위치에서 해당 방향으로 300m 앞 찍기
        double tx = aircraft.getX() + Math.cos(avoidRad) * 300.0;
        double ty = aircraft.getY() + Math.sin(avoidRad) * 300.0;

        return new double[]{tx, ty};
    }

    // --- [핵심] 시야각 판단 (Standard Math) ---
    private boolean isThreatInFront(Aircraft threat) {
        double dx = threat.getX() - aircraft.getX();
        double dy = threat.getY() - aircraft.getY();

        // 1. 적기까지의 절대 각도 (atan2는 -PI ~ PI 반환)
        double angleToThreat = Math.toDegrees(Math.atan2(dy, dx));

        // 2. 내 헤딩과의 차이
        double diff = angleToThreat - aircraft.getAngle();

        // 3. 정규화 (-180 ~ 180)
        while (diff <= -180) diff += 360;
        while (diff > 180) diff -= 360;

        // 전방 90도 (좌우 90도) 이내면 "앞에 있다"고 판단
        return Math.abs(diff) < 90.0;
    }

    // --- [기타 보조 메소드들] ---
    private Aircraft perceiveThreat(Airspace airspace) {
        List<Aircraft> allAircraft = airspace.getAllAircraft();
        Aircraft closest = null;
        double minDist = Double.MAX_VALUE;
        for (Aircraft other : allAircraft) {
            if (other == this.aircraft) continue;
            // 유클리드 거리 (미터)
            double dist = Math.sqrt(Math.pow(aircraft.getX() - other.getX(), 2) +
                    Math.pow(aircraft.getY() - other.getY(), 2));
            if (dist < minDist) {
                minDist = dist;
                closest = other;
            }
        }
        this.blackboard.set("closestAircraftDistance", (closest != null) ? minDist : Double.MAX_VALUE);
        return closest;
    }

    private String getNextFocus() {
        double distToObstacle = getClosestObstacleDistance();
        Object distObj = this.blackboard.get("closestAircraftDistance");
        double distToThreat = (distObj != null) ? (Double) distObj : Double.MAX_VALUE;
        double roll = random.nextDouble();

        // [확률 조정] 논문 시나리오에 맞게 튜닝
        if (distToThreat < 200.0) { // 200m 이내 (긴급)
            if (roll < 0.90) return "ClosestAircraft"; // 90% 확률로 적기 확인
            else return "Obstacle";
        } else if (distToObstacle < 500.0) { // 장애물 근접 : 500m 이내 (터널링 유발)
            if (roll < 0.70) return "Obstacle";       // 70%는 장애물만 봄
            else if (roll < 0.85) return "ClosestAircraft";
            else return "Altitude";
        } else {
            if (distToThreat < 1000.0) {
                if (roll < 0.60) return "ClosestAircraft";
                else return "Obstacle";
            }
            return "Altitude";
        }
    }

    private double getClosestObstacleDistance() {
        Object val = this.blackboard.get("closestObstacleDistance");
        return (val != null) ? (Double) val : Double.MAX_VALUE;
    }

    public double getActivationLevel(String cellName) {
        return this.wm.cells.get(cellName).getActivationLevel();
    }

    // --- [행동 트리 구성] ---
    private Behavior createBehaviourTree() {
        Selector root = new Selector("Root");
        Selector evasionLogic = new Selector("Evasion Logic");

        Sequence stopEvasion = new Sequence("Stop");
        stopEvasion.addChildren(
                new IsInState("Evading?", "Evade"),
                // 안전 거리 600m 확보 시 복귀
                new IsNot(new IsConflictDetected("Safe?", 600.0)),
                new SetTacticalState("Cruise", "Cruising")
        );

        Sequence startEvasion = new Sequence("Start");
        startEvasion.addChildren(
                // 감지 거리 400m (UAM 속도 고려 시 적절)
                new IsConflictDetected("Danger?", 400.0),
                new SetTacticalState("Evade", "Evade")
        );

        Sequence stayEvasion = new Sequence("Stay");
        stayEvasion.addChildren(new IsInState("Still Evading?", "Evade"));

        evasionLogic.addChildren(stopEvasion, startEvasion, stayEvasion);
        root.addChildren(evasionLogic, new SetTacticalState("Default", "Cruising"));
        return root;
    }
}