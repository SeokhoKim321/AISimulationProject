package com.example.ai.tree;

public class IsConflictDetected extends Behavior {
    private final double safetyBubble; // 충돌을 피해야 할 최소 안전 거리

    public IsConflictDetected(String name, double range) {
        super(name);
        this.safetyBubble = range;
    }

    @Override
    public Status update(Blackboard blackboard) {
        Object distanceObj = blackboard.get("closestAircraftDistance");
        if (distanceObj == null) {
            return Status.FAILURE; // 주변에 다른 항공기 없음
        }

        double distance = (Double) distanceObj;
        if (distance < this.safetyBubble) {
            return Status.SUCCESS; // 충돌 위험 감지!
        } else {
            return Status.FAILURE; // 안전함
        }
    }
}