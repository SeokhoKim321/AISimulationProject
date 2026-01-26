// ==========================
// Airspace.java
// ==========================

package com.example.ai;

import java.util.ArrayList;
import java.util.List;

public class Airspace {
    private List<Aircraft> allAircraft = new ArrayList<>();
    private List<Obstacle> obstacles = new ArrayList<>();

    public void addAgent(Aircraft a) {
        allAircraft.add(a);
    }

    public void addObstacle(Obstacle o) {
        obstacles.add(o);
    }

    public List<Aircraft> getAllAircraft() {
        return allAircraft;
    }

    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    public void update(double dt) {
        // 여기에 나중에 물리 충돌 검사 등을 넣을 수 있음
        // 지금은 비워둠
    }
}