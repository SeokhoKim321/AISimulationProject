package com.example.ai;

import java.util.ArrayList;
import java.util.List;

public class Airspace {
    // 모든 항공기를 하나의 리스트로 관리
    private List<Aircraft> allAircraft = new ArrayList<>();

    public void addAgent(Aircraft aircraft) {
        allAircraft.add(aircraft);
    }

    // Agent가 호출할 수 있도록 전체 항공기 목록 제공
    public List<Aircraft> getAllAircraft() {
        return allAircraft;
    }

    // 환경 업데이트 (현재는 하는 일 없음)
    public void update(int time) {
        // (적기 이동, 격추 등 전투 로직 모두 삭제)
    }
}