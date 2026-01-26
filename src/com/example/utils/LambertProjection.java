package com.example.utils;

import java.awt.geom.Point2D;

/**
 * [지리 로직] WGS84 위경도 <-> 미터(XY) 변환기
 * - LCC (Lambert Conformal Conic) 투영법 사용
 * - 대한민국/동북아시아 표준 위선 적용
 * - 출력 좌표계: Standard Math (East=+X, North=+Y)
 */
public class LambertProjection {

    // WGS84 타원체 상수
    private static final double WGS84_A = 6378137.0;            // 장반경
    private static final double WGS84_F = 1.0 / 298.257223563;  // 편평률
    private static final double WGS84_E = Math.sqrt(2 * WGS84_F - WGS84_F * WGS84_F); // 제1이심률

    // 투영 설정 변수
    private final double n;       // 원추 상수
    private final double F;       // 스케일 계수
    private final double rho0;    // 원점의 반경
    private final double lambda0; // 기준 경도 (Rad)

    /**
     * @param originLat 기준 위도 (시뮬레이션 월드 원점 (0,0)이 될 위도)
     * @param originLon 기준 경도 (시뮬레이션 월드 원점 (0,0)이 될 경도)
     * @param stdParallel1 표준 위선 1 (보통 30.0)
     * @param stdParallel2 표준 위선 2 (보통 60.0)
     */
    public LambertProjection(double originLat, double originLon, double stdParallel1, double stdParallel2) {
        double phi0 = Math.toRadians(originLat);
        this.lambda0 = Math.toRadians(originLon);
        double phi1 = Math.toRadians(stdParallel1);
        double phi2 = Math.toRadians(stdParallel2);

        double m1 = calculateM(phi1);
        double m2 = calculateM(phi2);
        double t0 = calculateT(phi0);
        double t1 = calculateT(phi1);
        double t2 = calculateT(phi2);

        this.n = (Math.log(m1) - Math.log(m2)) / (Math.log(t1) - Math.log(t2));
        this.F = m1 / (n * Math.pow(t1, n));
        this.rho0 = WGS84_A * F * Math.pow(t0, n);
    }

    // [GPS -> Meter]
    public Point2D.Double project(double lat, double lon) {
        double phi = Math.toRadians(lat);
        double lambda = Math.toRadians(lon);

        double t = calculateT(phi);
        double rho = WGS84_A * F * Math.pow(t, n);
        double theta = n * (lambda - lambda0);

        // 수학적 좌표계 변환 (북쪽이 +Y가 되도록 rho0 - y 사용)
        double x = rho * Math.sin(theta);
        double y = rho0 - (rho * Math.cos(theta));

        return new Point2D.Double(x, y);
    }

    // [Meter -> GPS] (마우스 클릭용)
    public Point2D.Double inverse(double x, double y) {
        double rho = Math.signum(n) * Math.sqrt(x * x + Math.pow(rho0 - y, 2));
        double theta = Math.atan2(x, rho0 - y);
        double t = Math.pow(rho / (WGS84_A * F), 1.0 / n);

        double phi = (Math.PI / 2.0) - (2.0 * Math.atan(t));
        for (int i = 0; i < 5; i++) {
            double esinPhi = WGS84_E * Math.sin(phi);
            phi = (Math.PI / 2.0) - 2.0 * Math.atan(t * Math.pow((1.0 - esinPhi) / (1.0 + esinPhi), WGS84_E / 2.0));
        }

        return new Point2D.Double(Math.toDegrees(phi), Math.toDegrees(lambda0 + (theta / n)));
    }

    private double calculateM(double phi) {
        double sinPhi = Math.sin(phi);
        return Math.cos(phi) / Math.sqrt(1.0 - WGS84_E * WGS84_E * sinPhi * sinPhi);
    }

    private double calculateT(double phi) {
        double esinPhi = WGS84_E * Math.sin(phi);
        return Math.tan(Math.PI / 4.0 - phi / 2.0) / Math.pow((1.0 - esinPhi) / (1.0 + esinPhi), WGS84_E / 2.0);
    }
}