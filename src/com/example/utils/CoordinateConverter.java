package com.example.utils;

/**
 * [화면 로직] 미터(World) <-> 픽셀(Screen) 변환 도구
 * - 오직 SimulationGUI(View)에서만 사용해야 함.
 * - Agent(Logic) 내부에서는 절대 사용 금지!
 */
public class CoordinateConverter {

    // 축척 설정
    // 0.5 px/m -> 1미터를 0.5픽셀로 그림 (2미터가 1픽셀)
    // 예: 400m 거리 -> 화면상 200px 거리
    public static final double PIXELS_PER_METER = 0.5;

    // 미터 -> 픽셀 (그릴 때)
    public static double toPx(double meters) {
        return meters * PIXELS_PER_METER;
    }

    // 픽셀 -> 미터 (마우스 입력 받을 때)
    public static double toMeters(double pixels) {
        return pixels / PIXELS_PER_METER;
    }
}