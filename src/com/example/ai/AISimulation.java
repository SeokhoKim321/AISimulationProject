package com.example.ai;

import com.example.utils.LambertProjection;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class AISimulation {

    // 실험 설정 상수
    private static final int TOTAL_RUNS = 100;
    private static final int MAX_FRAMES = 2500;
    private static final double CRASH_THRESHOLD = 30.0;     // 충돌 판정 거리 (30m)
    private static final double NMAC_THRESHOLD = 100.0;     // 준사고 판정 거리 (100m)

    // 결과 저장용 클래스
    static class SimulationResult {
        String environment;
        int crashCount;
        int nmacCount;
        int safeCount;
        double avgMinDist;

        public SimulationResult(String env, int crash, int nmac, int safe, double dist) {
            this.environment = env;
            this.crashCount = crash;
            this.nmacCount = nmac;
            this.safeCount = safe;
            this.avgMinDist = dist;
        }
    }

    public static void main(String[] args) {
        System.out.println(">>> 시뮬레이션 데이터 수집 중... (잠시만 기다려주세요)");

        // 1. [실험 A] 개활지 실행
        System.out.print("[1/2] 개활지 시뮬레이션 진행 중: ");
        SimulationResult resultOpen = runBatchSimulation(false);
        System.out.println(" 완료!");

        // 2. [실험 B] 도심 실행
        System.out.print("[2/2] 도심 협곡 시뮬레이션 진행 중: ");
        SimulationResult resultUrban = runBatchSimulation(true);
        System.out.println(" 완료!");

        // 3. 결과 리포트 출력
        printFinalReport(resultOpen, resultUrban);
    }

    private static SimulationResult runBatchSimulation(boolean isUrban) {
        int crashCount = 0;
        int nmacCount = 0;
        int safeCount = 0;
        double totalMinDist = 0;

        for (int run = 1; run <= TOTAL_RUNS; run++) {
            Airspace airspace = new Airspace();
            List<Aircraft> allAircrafts = new ArrayList<>();
            List<Agent> allAgents = new ArrayList<>();

            // 1. 장애물 배치 (도심일 경우)
            if (isUrban) {
                // (미터 단위 좌표로 장애물 생성)
                airspace.addObstacle(new Obstacle(600, 200, 200, 150));
                airspace.addObstacle(new Obstacle(600, 450, 200, 150));
            }

            // 2. 항공기 생성 (여기가 수정된 부분!)
            // [오류 해결] 옛날 생성자 대신 새 생성자(x, y, speed, angle) 사용

            // 파란 비행기 (서 -> 동)
            Aircraft a1 = new Aircraft(50.0, 400.0, 40.0, 0.0);
            a1.setDestination(1350.0, 400.0);
            a1.setCommandTarget(1350.0, 400.0);
            a1.setTeam("blue");
            Agent ag1 = new Agent(a1);

            // 빨간 비행기 (동 -> 서)
            Aircraft a2 = new Aircraft(1350.0, 440.0, 40.0, 180.0);
            a2.setDestination(50.0, 440.0);
            a2.setCommandTarget(50.0, 440.0);
            a2.setTeam("red");
            Agent ag2 = new Agent(a2);

            // 리스트 등록
            allAircrafts.add(a1); allAircrafts.add(a2);
            allAgents.add(ag1);   allAgents.add(ag2);
            airspace.addAgent(a1); airspace.addAgent(a2);

            // 3. 시뮬레이션 루프 (프레임 단위 실행)
            double minDistInThisRun = Double.MAX_VALUE;

            for (int t = 0; t < MAX_FRAMES; t++) {
                // 업데이트
                airspace.update(0.016);
                for (Agent agent : allAgents) agent.update(airspace);
                for (Aircraft aircraft : allAircrafts) aircraft.executeMovement(0.016);

                // 최소 거리 측정
                double dist = getDistance(a1, a2);
                if (dist < minDistInThisRun) minDistInThisRun = dist;

                // 충돌 시 조기 종료
                if (dist < CRASH_THRESHOLD) break;
            }

            // 4. 결과 기록
            if (minDistInThisRun < CRASH_THRESHOLD) crashCount++;
            else if (minDistInThisRun < NMAC_THRESHOLD) nmacCount++;
            else safeCount++;

            totalMinDist += minDistInThisRun;

            // 진행률 표시
            if (run % 10 == 0) System.out.print(".");
        }

        return new SimulationResult(
                isUrban ? "도심 협곡 (빌딩 O)" : "개활지 (장애물 X)",
                crashCount, nmacCount, safeCount, (totalMinDist / TOTAL_RUNS)
        );
    }

    private static double getDistance(Aircraft a1, Aircraft a2) {
        return Math.sqrt(Math.pow(a1.getX() - a2.getX(), 2) + Math.pow(a1.getY() - a2.getY(), 2));
    }

    private static void printFinalReport(SimulationResult r1, SimulationResult r2) {
        System.out.println("\n\n");
        System.out.println("================================================================");
        System.out.println("                  [최종 시뮬레이션 비교 리포트]                  ");
        System.out.println("================================================================");
        System.out.printf("%-20s | %-10s | %-10s | %-10s | %-10s\n",
                "환경(Environment)", "충돌(Crash)", "준사고(NMAC)", "안전(Safe)", "평균최소거리(m)");
        System.out.println("----------------------------------------------------------------");

        printRow(r1);
        printRow(r2);

        System.out.println("================================================================");
    }

    private static void printRow(SimulationResult r) {
        System.out.printf("%-20s | %3d회(%3d%%) | %3d회(%3d%%) | %3d회(%3d%%) | %8.2fm\n",
                r.environment,
                r.crashCount, r.crashCount,
                r.nmacCount, r.nmacCount,
                r.safeCount, r.safeCount,
                r.avgMinDist);
    }
}