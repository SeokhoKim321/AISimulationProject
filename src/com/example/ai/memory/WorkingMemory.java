// src/com/example/ai/memory/WorkingMemory.java

package com.example.ai.memory;

import java.util.HashMap;
import java.util.Map;

public class WorkingMemory {
    public Map<String, MemoryCell> cells = new HashMap<>();

    public void addCell(MemoryCell cell) {
        this.cells.put(cell.name, cell);
    }

    public void update(String attendedCellName, double timeStep) {
        // 1. 논문에 명시된 계수 값으로 변경
        double r_a = 7.489; // 주의 집중 시 활성화 속도
        double r_d = 0.5;   // 시간 경과 시 감쇠(잊히는) 속도

        for (MemoryCell cell : this.cells.values()) {
            if (cell.name.equals(attendedCellName)) {
                // 2. 활성도 상승: 논문의 공식(A = 1 - e^(-ra*t))을 적용
                // 이전 값에 더하는 대신, 새로운 활성도를 직접 계산합니다.
                // timeStep 동안 집중했을 때 도달하는 활성도를 의미합니다.
                cell.setActivationLevel(1.0 - Math.exp(-r_a * timeStep));
            } else {
                // 3. 활성도 하락: 기존 로직은 유지하되, 더 빠른 감쇠 계수(r_d)를 적용
                double decayFactor = Math.exp(-r_d * timeStep);
                cell.setActivationLevel(cell.getActivationLevel() * decayFactor);
            }
        }
    }
}