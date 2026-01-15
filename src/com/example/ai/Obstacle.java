package com.example.ai;

import javafx.scene.shape.Rectangle;

public class Obstacle {
    private double x, y;
    private double width, height;

    public Obstacle(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // 충돌 체크 로직 (AABB 방식: 사각형끼리 겹치는지 확인)
    public boolean checkCollision(double agentX, double agentY, double agentSize) {
        return (agentX < x + width &&
                agentX + agentSize > x &&
                agentY < y + height &&
                agentY + agentSize > y);
    }

    // 거리 계산 (에이전트와 빌딩 중심점 간의 거리)
    public double getDistance(double agentX, double agentY) {
        double centerX = x + width / 2;
        double centerY = y + height / 2;
        return Math.sqrt(Math.pow(centerX - agentX, 2) + Math.pow(centerY - agentY, 2));
    }

    // Getter
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
}