// ==========================
// IsConflictDetected.java
// ==========================

package com.example.ai.tree;

import com.sun.source.tree.UsesTree;

public class IsConflictDetected extends Behavior {
    private final double safetyBubble; // 충돌을 피해야 할 최소 안전 거리
    private final double activationThreshold = 0.7; // 인지 임계값 (이 값보다 커야 '보인다'고 판단)

    public IsConflictDetected(String name, double range) {
        super(name);
        this.safetyBubble = range;
    }

    @Override
    public Status update(Blackboard blackboard) {
        // 1. 물리적 거리 가져오기
        Object distanceObj = blackboard.get("closestAircraftDistance");
        if (distanceObj == null) {
            return Status.FAILURE; // 주변에 다른 항공기 없음
        }
        double distance = (Double) distanceObj;

        // 2. [핵심] 인지적 활성도 가져오기
        Object activationObj = blackboard.get("threatActivation");
        double activation = (activationObj != null) ? (Double) activationObj : 0.0;

        // --- [디버깅 코드 추가] ---
        // 거리가 가까울 때(예: 150 이내) 활성도가 얼마인지 콘솔에 출력합니다.
        if (distance < 150.0) {
            System.out.println("거리: " + (int)distance + " / 활성도: " + String.format("%.4f", activation) + " / 기준: " + activationThreshold);
        }
        // -----------------------

        // 3. 판단 : 거리가 가깝고 내 기억이 선명해야(Activation > 0.5) " 위협을 감지함
        if (distance < this.safetyBubble) {
            if (activation > this.activationThreshold) {
                return Status.SUCCESS; // "위험해"! 인지 성공
            } else {
                // 거리는 가깝지만, 딴짓하느라 못봄
                System.out.println("위험하지만 인지하지 못함! (활성도: " + String.format("%.2f", activation) + ")");
                return Status.FAILURE;

            }
        }

        return Status.FAILURE; // 안전 거리 밖임
    }
}