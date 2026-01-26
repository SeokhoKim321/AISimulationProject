// ==========================
// Obstacle.java
// ==========================

package com.example.ai;

public class Obstacle {
    // 모든 단위는 '미터(m)'입니다.
    // 위치는 사각형의 '정중앙(Center)' 기준입니다.
    private double x, y;
    private double width, height;

    public Obstacle(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // 에이전트와의 거리 계산 (유클리드 거리)
    public double getDistance(double targetX, double targetY) {
        // 이미 x, y가 중심점이므로 바로 계산
        return Math.sqrt(Math.pow(x - targetX, 2) + Math.pow(y - targetY, 2));
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
}