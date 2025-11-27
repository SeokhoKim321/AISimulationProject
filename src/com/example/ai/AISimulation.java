package com.example.ai;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class AISimulation {

    // (참고: 이 파일은 이제 SimulationGUI와 별도로, 데이터만 뽑기 위한 파일입니다)

    public static void main(String[] args) throws IOException {
        // 1. 환경과 항공기 목록 생성
        Airspace airspace = new Airspace();

        // 2. '몸체' 와 '두뇌'를 담을 리스트 각각 생성
        List<Aircraft> allAircrafts = new ArrayList<>();
        List<Agent> allAgents = new ArrayList<>();

        // 3. 항공기(몸체)와 조종사(두뇌)를 생성하고 연결

        // 항공기 1(몸체) 생성 및 목적지 설정
        Aircraft aircraft1 = new Aircraft(100.0, 100.0, "blue", 900.0, 700.0);

        //항공기 1(두뇌) 생성 및 aircraft1과 연결
        Agent agent1 = new Agent(aircraft1);

        // 항공기 2(몸체) 생성 및 목적지 설정
        Aircraft aircraft2 = new Aircraft(900.0, 700.0, "red", 100.0, 100.0);
        //항공기 2(두뇌) 생성 및 aircraft과 연결
        Agent agent2 = new Agent(aircraft2);

        // 4. 각 리스트에 추가
        allAircrafts.add(aircraft1);
        allAircrafts.add(aircraft2);

        allAgents.add(agent1);
        allAgents.add(agent2);

        // 5. 공역에 '몸체'들을 등록
        airspace.addAgent(aircraft1);
        airspace.addAgent(aircraft2);

        int totalDuration = 100;

        try (PrintWriter writer = new PrintWriter(new FileWriter("simulation_results_civilian.csv"))) {

            // --- [수정된 부분 2: 헤더 이름 변경] ---
            writer.println("Time," +
                    "Aircraft1_Closest,Aircraft1_Fuel,Aircraft1_Altitude,Aircraft1_State," +
                    "Aircraft2_Closest,Aircraft2_Fuel,Aircraft2_Altitude,Aircraft2_State");

            // 6. 메인 루프 실행
            for (int t = 0; t < totalDuration; t++) {
                airspace.update(t); // 환경 업데이트

                // A. 모든 '두뇌'가 먼저 생각함
                for (Agent agent : allAgents){
                    agent.update(airspace);
                }
                // B. (선택사항) 모든 '몸체'가 움직임
                // 파일 저장만 할 경우 이 부분은 주석 처리해도 됨)
                for (Aircraft aircraft : allAircrafts){
                    aircraft.executeMovement();
                }

                // 7. 결과 기록
                writer.printf("%d,%.4f,%.4f,%.4f,%s,%.4f,%.4f,%.4f,%s\n",
                        t,
                        // --- [핵심 수정] ---
                        // 활성도(기억)는 '두뇌'(Agent)에서 가져옴
                        allAgents.get(0).getActivationLevel("ClosestAircraft"),
                        allAgents.get(0).getActivationLevel("Fuel Level"),
                        allAgents.get(0).getActivationLevel("Altitude"),
                        // 전술 상태는 '몸체'(Aircraft)에서 가져옴
                        allAircrafts.get(0).getTacticalState(),

                        allAgents.get(1).getActivationLevel("ClosestAircraft"),
                        allAgents.get(1).getActivationLevel("Fuel Level"),
                        allAgents.get(1).getActivationLevel("Altitude"),
                        allAircrafts.get(1).getTacticalState()
                );
            }
        }
        System.out.println("민항기 데이터 시뮬레이션 완료! 결과가 simulation_results_civilian.csv 파일에 저장되었습니다.");
    }
}