// ==========================
// Obstacle.java
// ==========================

package com.example.ai;

public class Obstacle {
    // 모든 단위는 '미터(m)'입니다.
    // 위치는 사각형의 '정중앙(Center)' 기준입니다.
    private double x, y;

    private double radius; //

    private double height;

    // 생성자 (장애물을 처음 만들 때 쓰는 설계도)
    public Obstacle(double x, double y, double radius, double height) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.height = height; // 건물 높이 설정
    }

    // [핵심] 충돌 감지 함수
    public boolean isColliding(Aircraft ac) {

        // 1단계: 고도(Z축) 확인
        // 비행기(ac)의 고도가 내 키(height)보다 높으면 부딪힐 일이 없습니다.
        // (안전 마진 10m를 더해서, 건물 옥상보다 10m 더 높게 날아야 안전하다고 판단)
        if (ac.getZ() > (this.height + 10)) {
            return false; // 충돌 아님 (통과!)
        }

        // 2단계: 수평 거리 확인 (기존 거리 계산 로직)
        // 비행기가 건물 옆으로 지나가는지 확인합니다.

        // 피타고라스 정리를 이용해 중심점 간의 거리 계산
        double dx = this.x - ac.getX();
        double dy = this.y - ac.getY();
        // Math.hypot(dx, dy)는 Math.sqrt(dx*dx + dy*dy)와 같습니다. (빗변 길이)
        double dist = Math.hypot(dx, dy);

        // 3단계: 최종 판단
        // 거리가 (내 반지름 + 비행기 안전거리 30m)보다 가까우면 충돌
        if (dist < (this.radius + 30)) {
            return true; // 충돌!
        }

        return false; // 안전함
    }

    public double getDistance(double targetX, double targetY) {
        // 피타고라스 정리로 거리 계산 (Math.hypot = sqrt(dx^2 + dy^2))
        return Math.hypot(this.x - targetX, this.y - targetY);
    }

    // Getters
    public double getX() { return x; }
    public double getY() { return y; }
    public double getRadius() { return radius; }
    public double getHeight() { return height; }
}