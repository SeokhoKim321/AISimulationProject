package com.example.ai;

import com.example.ai.memory.*;
import com.example.ai.tree.*;
import java.util.List;
import java.util.Random;

public class Agent { // 조종사(두뇌) 클래스 public은 '공개' 접근 제어자 어떤 클래스에서도 이 설계도를 보고 객체를 생성할 수 있음

    // 1. '두뇌' 부품들
    private final WorkingMemory wm;  // private : '비공개' 접근 제어자, 이 wm 변수는 이 Agent 클래스 내부에서만 접근할 수 있음
    // SimulationGUI가 agent1.wm 처럼 직접 건드리는 것을 막아 정보를 보호함
    // final : '최종' 키워드 이 wm변수는 생성자에서 딱 한 번만 초기화 할 수 있으며, 그 우히에는 다른 WorkingMemory 객체로 절대 교체할수 없음
    private final Behavior behaviorTree;
    private final Blackboard blackboard;
    private final Random random;
    private final String[] cellNames = {"ClosestAircraft", "Fuel Level", "Altitude"};
    // WorkingMemory가 관리할 기억 세포들의 이름 목록을 '문자열 배열'로 미리 정의함

    // 2. 자신이 조종할 '몸체(항공기)'
    private Aircraft aircraft;

    // 3. 생성자 (두뇌가 생성될 때, 자신이 조종할 몸체를 전달받음)
    public Agent(Aircraft aircraft){   // new Agent(...)라고 주문하면 실행되는 "조립 설명서"
        this.aircraft = aircraft; // 몸체 연결
        // 재료로 받은 비행기(aircraft)를 "내 주머니(this.aircraft)"에 넣어서 연결

        // 두뇌 부품 초기화
        this.wm = new WorkingMemory();
        for (String name : cellNames){
            this.wm.addCell(new MemoryCell(name));
        }
        this.blackboard = new Blackboard();
        this.random = new Random();
        this.behaviorTree = createBehaviourTree();
    }

    // 4. '생각하고 명령' 하는 메인 메소드
    public void update(Airspace airspace){  // void : 행동만 하는 역할
        // 1. 인식(perceive)
        perceiveAirspace(airspace);

        // 2. 의사결정(decide)
        this.blackboard.set("current_state", this.aircraft.getTacticalState());  // 몸체의 현재 상태를 메모장에 적용
        this.behaviorTree.update(this.blackboard);

        // 3. 명령(command)
        String newState = (String) this.blackboard.get("tactical_state");
        // (String) : 메모장에서 꺼낸 건 그냥 '물건'아럿, "이건 글자야"라고 꼬리표를 붙여주는 것(형변환)
        this.aircraft.setTacticalState(newState); // 몸체에 "다음 상태는 evade야!" 라고 명령
    }

    // 5. '인식' 메소드
    private void perceiveAirspace(Airspace airspace) {   // 확률적 감지( 적 발견 / 위협 발견)
        // 공역에서 모든 항공기 목록을 가져옴
        List<Aircraft> allAircraft = airspace.getAllAircraft();


        // 가장 가까운 항공기 찾기(TCAS역할)
        Aircraft closestAircraft = findClosestAircraft(allAircraft); // 헬퍼 메소드

        // '두뇌'가 인식한 타겟을 '몸체'에 전달함
        this.aircraft.setClosestAircraft(closestAircraft);

        if (closestAircraft != null) { // 자신의 '몸체' 위치와 '타겟'위치를 기준으로 거리 계산
            double dx = this.aircraft.getX() - closestAircraft.getX(); // 그 항공기까지의 x축 거리를 계산
            double dy = this.aircraft.getY() - closestAircraft.getY(); // 그 항공기까지의 y축 거리를 계산
            double distance = Math.sqrt(dx * dx + dy * dy);   // 그 항공기까지의 직선 거리를 계산
            this.blackboard.set("closestAircraftDistance", distance); // 계산된 '거리'값을 '메모장'에 closestAircraftDistacne 라는 이름으로 저장
        } else { // 만약 주변에 다른 항공기가 아무도 없다면
            this.blackboard.set("closestAircraftDistance", Double.MAX_VALUE);  // '메모장'에 '무한대' 값을 적어둬서, 절대로 충돌 위험이 없다고 알림
        }
    }

    public double getActivationLevel(String cellName) {
        return this.wm.cells.get(cellName).getActivationLevel();
    }

    // 6. '헬퍼' 메소드들, 거리 재기 도우미
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


    // 7. '의사결정 지도' (행동 트리)
    private Behavior createBehaviourTree() {
        Selector root = new Selector("Root"); // 1순위 규칙만 실행

        // 1순위 로직: 회피 시작, 유지, 또는 중단 결정
        Selector evasionLogic = new Selector("Evasion Logic");

        // [규칙 1a] 회피 중단 규칙 (더 '먼' 거리 : 100)
        // "만약 내가 'Evade' 상태이고, AND 거리가 100보다 멀어졌다면, 'Cruising' 상태로 복귀하라."
        Sequence stopEvasionSequence = new Sequence("Stop Evasion Sequence");
        stopEvasionSequence.addChildren(
                new IsInState("Am I Evading?", "Evade"),
                new IsNot(new IsConflictDetected("Is Danger Close (150)?", 150)), // 100.0보다 멀어지면 중단
                new SetTacticalState("Set Cruise", "Cruising")
        );

        // [규칙 1b] 회피 시작 규칙(더 '가까운' 거리 : 70.0)
        // "만약 거리가 70.0보다 가깝다면, 'Evade' 상태를 설정하라."
        Sequence startEvasionSequence = new Sequence("Start Evasion");
        startEvasionSequence.addChildren(
                new IsConflictDetected("Is Danger Close (100)?", 100), // 70.0보다 가까워지면 시작
                new SetTacticalState("Set Evade", "Evade")
        );

        // [규칙 1c] 회피 상태 '유지' 규칙
        // (이 규칙은 1a와 1b가 모두 실패했을 때 'Evade' 상태 : 즉 거리가 70.0 ~ 100.0 사이일 때)
        Sequence stayInEvasionState = new Sequence("Stay in Evasion");
        stayInEvasionState.addChildren(
                new IsInState("Am I Still Evading?", "Evade")
                // 아무것도 안 하지만, IsInState가 성공하면 이 Sequence도 성공합니다.
        );

        // 2순위 로직: 항로 비행 (FMS) - 기본값
        SetTacticalState followFlightPathAction = new SetTacticalState("Set Cruise", "Cruising");

        // 1순위 회피 로직들을 조립 (순서 중요!)
        evasionLogic.addChildren(
                stopEvasionSequence,        // 1a. 중단할 것인가?
                startEvasionSequence,       // 1b. 시작할 것인가?
                stayInEvasionState          // 1c. (둘 다 아니면) 현재 상태를 유지할 것인가?
        );

        // 최상위 트리에 1순위(회피 로직), 2순위(순항)를 추가
        root.addChildren(evasionLogic, followFlightPathAction);
        return root;
    }
}
