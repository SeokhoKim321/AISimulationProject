package com.example.utils;

public class LambertProjection {

    // [설정값]
    private double refLat;   // 기준 위도( 예 : 인하대 37.45)
    private double refLon;   // 기준 경도 ( 예 : 인하대 126.65)

    // LCC 상수 (대한민국 / 한반도 기준 표준 위선)
    // 보통 30도와 60도, 또는 38도 등을 사용

    private static final double STD_PARALLEL_1 = Math.toRadians(30.0);
    private static final double STD_PARALLEL_2 = Math.toRadians(60.0);
}
