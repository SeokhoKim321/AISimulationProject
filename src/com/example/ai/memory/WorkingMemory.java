// ==========================
// WorkingMemory.java
// ==========================

package com.example.ai.memory;

import java.util.HashMap;
import java.util.Map;

public class WorkingMemory {
    public Map<String, MemoryCell> cells = new HashMap<>(); // 셀의 이름을 키로, MemoryCell 객체를 값으로 하는 맵을 생성

    public void addCell(MemoryCell cell) {
        this.cells.put(cell.name, cell);
    }

    public void update(String attendedCellName, double timeStep) {
        // 1. 논문에 명시된 계수 값으로 변경
        double r_a = 7.489; // 주의 집중 시 활성화 속도
        double r_d = 5.0;   // 시간 경과 시 감쇠(잊히는) 속도

        for (MemoryCell cell : this.cells.values()) {

            // [ 핵심 ] 현재 기억 상태를 먼저 가져옴.
            double currentLevvel = cell.getActivationLevel();

            if (cell.name.equals(attendedCellName)) {
                // 1. 활성도 상승(누적)
                // "남은 공간(1.0 - currentLevel)"에 비례하여 활성도가 증가하도록 수정
                // 이렇게 해야 1.0을 넘지 않으면서 부드럽게 올라감
                double increase = (1.0 - currentLevvel) * (1.0 - Math.exp(-r_a * timeStep));
                cell.setActivationLevel(currentLevvel + increase);
            } else {
                // 2. 활성도 하락(누적)
                // 기존 값에서 일정 비율만큼 깎아내림
                double decayFactor = Math.exp(-r_d * timeStep);
                cell.setActivationLevel(currentLevvel * decayFactor);
            }
        }
    }
}