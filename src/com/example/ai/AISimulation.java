package com.example.ai;

import java.util.ArrayList;
import java.util.List;

public class AISimulation {

    // 실험 설정
    private static final int TOTAL_RUNS = 100;
    private static final int MAX_FRAMES = 2500;
    private static final double CRASH_THRESHOLD = 30.0;
    private static final double NMAC_THRESHOLD = 100.0;

    // [New] 결과를 저장할 성적표 클래스 (Inner Class)
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

        // 1. [실험 A] 개활지 실행 및 결과 저장 (출력 X)
        System.out.print("[1/2] 개활지 시뮬레이션 진행 중: ");
        SimulationResult resultOpen = runBatchSimulation(false);
        System.out.println(" 완료!");

        // 2. [실험 B] 도심 실행 및 결과 저장 (출력 X)
        System.out.print("[2/2] 도심 협곡 시뮬레이션 진행 중: ");
        SimulationResult resultUrban = runBatchSimulation(true);
        System.out.println(" 완료!");

        // 3. [최종 리포트] 저장된 두 결과를 한 번에 출력 (비교가 쉬워짐)
        printFinalReport(resultOpen, resultUrban);
    }

    // 반환 타입을 void -> SimulationResult로 변경
    private static SimulationResult runBatchSimulation(boolean isUrban) {
        int crashCount = 0;
        int nmacCount = 0;
        int safeCount = 0;
        double totalMinDist = 0;

        for (int run = 1; run <= TOTAL_RUNS; run++) {
            Airspace airspace = new Airspace();
            List<Aircraft> allAircrafts = new ArrayList<>();
            List<Agent> allAgents = new ArrayList<>();

            if (isUrban) {
                airspace.addObstacle(new Obstacle(600, 200, 200, 150));
                airspace.addObstacle(new Obstacle(600, 450, 200, 150));
            }

            Aircraft a1 = new Aircraft(50.0, 400.0, "blue", 1350.0, 400.0);
            Agent ag1 = new Agent(a1);

            Aircraft a2 = new Aircraft(1350.0, 440.0, "red", 50.0, 360.0);
            Agent ag2 = new Agent(a2);

            allAircrafts.add(a1); allAircrafts.add(a2);
            allAgents.add(ag1);   allAgents.add(ag2);
            airspace.addAgent(a1); airspace.addAgent(a2);

            double minDistInThisRun = Double.MAX_VALUE;

            for (int t = 0; t < MAX_FRAMES; t++) {
                airspace.update(t);
                for (Agent agent : allAgents) agent.update(airspace);
                for (Aircraft aircraft : allAircrafts) aircraft.executeMovement();

                double dist = getDistance(a1, a2);
                if (dist < minDistInThisRun) minDistInThisRun = dist;
                if (dist < CRASH_THRESHOLD) break;
            }

            if (minDistInThisRun < CRASH_THRESHOLD) crashCount++;
            else if (minDistInThisRun < NMAC_THRESHOLD) nmacCount++;
            else safeCount++;

            totalMinDist += minDistInThisRun;

            // 진행률 표시 (점 찍기)
            if (run % 10 == 0) System.out.print(".");
        }

        // 결과 객체 생성 및 반환
        return new SimulationResult(
                isUrban ? "도심 협곡 (빌딩 O)" : "개활지 (장애물 X)",
                crashCount, nmacCount, safeCount, (totalMinDist / TOTAL_RUNS)
        );
    }

    // 최종 결과 출력용 메서드
    private static void printFinalReport(SimulationResult r1, SimulationResult r2) {
        System.out.println("\n\n");
        System.out.println("================================================================");
        System.out.println("                  [최종 시뮬레이션 비교 리포트]                  ");
        System.out.println("================================================================");

        // 헤더 출력
        System.out.printf("%-20s | %-10s | %-10s | %-10s | %-10s\n",
                "환경(Environment)", "충돌(Crash)", "준사고(NMAC)", "안전(Safe)", "평균거리(px)");
        System.out.println("----------------------------------------------------------------");

        // 개활지 결과 출력
        System.out.printf("%-20s | %3d회(%3d%%) | %3d회(%3d%%) | %3d회(%3d%%) | %8.2f\n",
                r1.environment,
                r1.crashCount, r1.crashCount,
                r1.nmacCount, r1.nmacCount,
                r1.safeCount, r1.safeCount,
                r1.avgMinDist);

        // 도심 결과 출력
        System.out.printf("%-20s | %3d회(%3d%%) | %3d회(%3d%%) | %3d회(%3d%%) | %8.2f\n",
                r2.environment,
                r2.crashCount, r2.crashCount,
                r2.nmacCount, r2.nmacCount,
                r2.safeCount, r2.safeCount,
                r2.avgMinDist);

        System.out.println("================================================================");
        System.out.println("개활지에서는 '안전' 비율이 높고, 도심에서는 '준사고' 비율이 높아야 정상");
    }

    private static double getDistance(Aircraft a1, Aircraft a2) {
        return Math.sqrt(Math.pow(a1.getX() - a2.getX(), 2) + Math.pow(a1.getY() - a2.getY(), 2));
    }
}