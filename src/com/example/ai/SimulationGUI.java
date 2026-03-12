// ==========================
// SimulationGUI.java
// ==========================

package com.example.ai;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label; // [추가] 텍스트 표시용
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle; // [변경] 사각형 -> 원
import javafx.scene.shape.Line;   // [추가] 선 그리는 거
import javafx.scene.text.Font;    // [추가] 폰트
import javafx.scene.text.Text;    // [추가] 텍스트
import javafx.stage.Stage;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.example.utils.CoordinateConverter;
import com.example.utils.LambertProjection;

public class SimulationGUI extends Application {
    private Airspace airspace;
    private List<Aircraft> allAircrafts = new ArrayList<>();
    private List<ImageView> allViews = new ArrayList<>();
    private List<Text> allLabels = new ArrayList<>(); // [추가] 고도 표시용 텍스트
    private List<Agent> allAgents = new ArrayList<>();

    private Pane simulationPane;
    private AnimationTimer timer;
    private HBox buttonBox;

    private LambertProjection projector;
    private Map<String, XYChart.Series<Number, Number>> seriesMap = new HashMap<>();

    private double timeSeconds = 0.0;
    private int simulationTime = 0;

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Civil Aircraft Simulation (Logic: Meter / View: Pixel)");

        // 1. 레이아웃
        BorderPane mainLayout = new BorderPane();
        simulationPane = new Pane();
        simulationPane.setPrefSize(1400, 600);
        mainLayout.setCenter(simulationPane);

        // 2. 버튼
        Button startButton = new Button("시작");
        Button stopButton = new Button("정지");
        Button resetButton = new Button("리셋");
        buttonBox = new HBox(10, startButton, stopButton, resetButton);
        buttonBox.setStyle("-fx-padding: 10; -fx-background-color: #ddd;");
        mainLayout.setTop(buttonBox);

        // 3. 그래프 패널
        FlowPane chartsPane = createChartsPanel();
        mainLayout.setBottom(chartsPane);

        // 4. 타이머 (게임 루프)
        this.timer = new AnimationTimer() {
            private long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 16_000_000) { // 약 60FPS
                    // A. 논리 업데이트 (순수 미터/수학 계산)
                    // (화면 크기나 픽셀과는 전혀 상관없이 돌아감)
                    int currentSecond = (int) (simulationTime / 60.0);
                    airspace.update(currentSecond);

                    for (Agent agent : allAgents) agent.update(airspace);
                    for (Aircraft aircraft : allAircrafts) aircraft.executeMovement(0.016); // dt = 0.016초

                    // B. 화면 업데이트 (미터 -> 픽셀 변환 후 그리기)
                    updateUI();
                    updateCharts();

                    simulationTime++;
                    timeSeconds += 0.016;
                    lastUpdate = now;
                }
            }
        };

        // 버튼 이벤트
        startButton.setOnAction(e -> timer.start());
        stopButton.setOnAction(e -> timer.stop());
        resetButton.setOnAction(e -> {
            timer.stop();
            simulationTime = 0;
            timeSeconds = 0.0;
            initializeSimulation();
            clearCharts();
        });

        // 초기화 및 실행
        initializeSimulation();
        Scene scene = new Scene(mainLayout, 1400, 900);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // --- [초기화] 시나리오 설정 ---
    private void initializeSimulation() {
        allAircrafts.clear();
        allAgents.clear();
        allViews.clear();
        allLabels.clear(); // [추가] 라벨 초기화
        simulationPane.getChildren().clear();

        airspace = new Airspace();
        projector = new LambertProjection(37.4500, 126.6530, 30.0, 60.0);

        // 이 변수는 항공기들이 다니는 항로의 위도
        double fixedLat = 37.4500;

        // 이 변수는 장애물의 위도
        double obstacleLat = 37.4510;     // 숫자를 올리면 북쪽으로 이동함
        Point2D.Double obsPos = projector.project(obstacleLat, 126.6620); // 숫자를 올리면 동쪽으로 이동함

        // 장애물 생성(여기 부분을 블러처리하면 장애물 없을 때 비교 가능)
        Obstacle centerBuilding = new Obstacle(obsPos.x - 100.0, obsPos.y - 75.0, 100.0, 150.0);
        airspace.addObstacle(centerBuilding);
        drawObstacle(centerBuilding);

        // 2. 파란 비행기 (서 -> 동)
        // 장애물보다 서쪽 600m 지점
        Point2D.Double start1 = projector.project(fixedLat, 126.6480);
        Point2D.Double dest1  = projector.project(fixedLat, 126.6800);

        // 속도 40m/s, 각도 0도(동쪽)  고도(z) 150 추가
        Aircraft a1 = new Aircraft(start1.x, start1.y, 150.0, 40.0, 0.0);
        a1.setDestination(dest1.x, dest1.y); // Agent 참고용 최종 목적지
        a1.setCommandTarget(dest1.x, dest1.y); // 초기 명령
        a1.setTeam("blue");
        Agent ag1 = new Agent(a1);

        // 3. 빨간 비행기 (동 -> 서)
        // 장애물보다 동쪽 600m 지점
        Point2D.Double start2 = projector.project(fixedLat, 126.6740);
        Point2D.Double dest2  = projector.project(fixedLat, 126.6400);

        // 속도 40m/s, 각도 180도(서쪽)  고도 (z) 150 추가
        Aircraft a2 = new Aircraft(start2.x, start2.y, 150.0, 40.0, 180.0);
        a2.setDestination(dest2.x, dest2.y);
        a2.setCommandTarget(dest2.x, dest2.y);
        a2.setTeam("red");
        Agent ag2 = new Agent(a2);

        // 등록
        allAircrafts.add(a1); allAircrafts.add(a2);
        allAgents.add(ag1);   allAgents.add(ag2);
        airspace.addAgent(a1); airspace.addAgent(a2);

        // 이미지 생성
        createAircraftViews();

        // [이곳에 코드 추가] 비행기들이 이동하기 전, 초기 위치를 화면 바닥에 고정으로 그립니다.
        drawInitialPositions(a1, a2, centerBuilding);

        // 시작 전 위치 정렬 (한 번 갱신)
        updateUI();
    }

    // --- [View] 비행기 이미지 생성 ---
    private void createAircraftViews() {
        try {
            Image blueImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/blue_jet_2.png")));
            Image redImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/red_jet_2.png")));

            for (int i = 0; i < allAircrafts.size(); i++) {
                // 1. 이미지
                ImageView view = new ImageView(i == 0 ? blueImg : redImg);
                view.setFitWidth(80);  // 삼각형크기
                view.setFitHeight(80); // 삼각형크기
                allViews.add(view);
                simulationPane.getChildren().add(view);
                // 2. 텍스트 라벨(고도 표시용) [ 추가]
                Text label = new Text("Alt: 0m");
                label.setFont(new Font(20));
                label.setFill(Color.BLACK);
                // [추가] 가로축(X)으로 1.2배 늘리기
                label.setScaleX(1.2);

                allLabels.add(label);
                simulationPane.getChildren().add(label);

            }
        } catch (Exception e) { e.printStackTrace(); }
    }

// --- [View Logic] 장애물 그리기 (좌표 변환 및 치수선 적용) ---
    private void drawObstacle(Obstacle obs) {
        // 화면 중심점
        double centerX = simulationPane.getPrefWidth() / 2.0;
        double centerY = simulationPane.getPrefHeight() / 2.0;

        // 1. 크기 변환 (미터 -> 픽셀)
        double radiusPx = CoordinateConverter.toPx(obs.getRadius());

        // 2. 위치 변환 (미터 -> 픽셀)
        double px = CoordinateConverter.toPx(obs.getX());
        double py = CoordinateConverter.toPx(obs.getY());

        // 3. 화면 좌표계
        double screenX = centerX + px;
        double screenY = centerY - py;

        // 장애물 원형 (반투명)
        Circle circle = new Circle(screenX, screenY, radiusPx);
        circle.setFill(Color.color(0.5, 0.5, 0.5, 0.5)); // 반투명 회색
        circle.setStroke(Color.BLACK);
        circle.setStrokeWidth(2);

        // [추가 1] 원의 정중앙을 표시하는 작은 점
        Circle centerDot = new Circle(screenX, screenY, 3, Color.BLACK);

        // [추가 2] 반경(R)을 나타내는 치수선 (중심에서 오른쪽 테두리까지)
        Line radiusLine = new Line(screenX, screenY, screenX + radiusPx, screenY);
        radiusLine.setStroke(Color.BLACK);
        radiusLine.getStrokeDashArray().addAll(5d, 5d); // 선을 점선(Dash)으로 만듦

        // [추가 3] 반경 텍스트 (R: 100m) - 선의 중간 살짝 위에 배치
        Text radiusText = new Text(screenX + (radiusPx / 2.0) - 30, screenY - 10, String.format("R: %.0fm", obs.getRadius()));
        radiusText.setFont(new Font(20));
        radiusText.setFill(Color.BLUE);
        radiusText.setScaleX(1.2); // [추가] 가로로 1.2배 늘림

        // [수정] 높이 텍스트 (H: 150m) - 중앙점 아래에 배치
        Text heightText = new Text(screenX - 35, screenY + 25, String.format("H: %.0fm", obs.getHeight()));
        heightText.setFont(new Font(20));
        heightText.setFill(Color.RED);
        heightText.setScaleX(1.2); // [추가] 가로로 1.2배 늘림

        // 화면에 모든 요소를 한 번에 추가
        simulationPane.getChildren().addAll(circle, centerDot, radiusLine, radiusText, heightText);
    }

    // --- [View Logic] 초기 위치 및 거리선 고정 그리기 ---
    private void drawInitialPositions(Aircraft a1, Aircraft a2, Obstacle obs) {
        double centerX = simulationPane.getPrefWidth() / 2.0;
        double centerY = simulationPane.getPrefHeight() / 2.0;

        // 1. 장애물 화면 좌표 변환
        double obsPx = CoordinateConverter.toPx(obs.getX());
        double obsPy = CoordinateConverter.toPx(obs.getY());
        double screenObsX = centerX + obsPx;
        double screenObsY = centerY - obsPy;

        // 2. 파란 비행기(A1) 초기 화면 좌표 변환
        double a1Px = CoordinateConverter.toPx(a1.getX());
        double a1Py = CoordinateConverter.toPx(a1.getY());
        double screenA1X = centerX + a1Px;
        double screenA1Y = centerY - a1Py;

        // 3. 빨간 비행기(A2) 초기 화면 좌표 변환
        double a2Px = CoordinateConverter.toPx(a2.getX());
        double a2Py = CoordinateConverter.toPx(a2.getY());
        double screenA2X = centerX + a2Px;
        double screenA2Y = centerY - a2Py;

        // 4. 장애물과 각 비행기 사이의 실제 물리적 거리(미터) 계산
        double rawDist1 = Math.sqrt(Math.pow(a1.getX() - obs.getX(), 2) + Math.pow(a1.getY() - obs.getY(), 2));
        double rawDist2 = Math.sqrt(Math.pow(a2.getX() - obs.getX(), 2) + Math.pow(a2.getY() - obs.getY(), 2));

        // [수정] 거리를 100m 단위로 반올림하여 대칭적이고 깔끔한 숫자로 만듭니다.
        double cleanDist1 = Math.round(rawDist1 / 100.0) * 100.0;
        double cleanDist2 = Math.round(rawDist2 / 100.0) * 100.0;

        // 5. 파란 비행기(A1) 시각물 생성
        Line line1 = new Line(screenA1X, screenA1Y, screenObsX, screenObsY);
        line1.setStroke(Color.GRAY);
        line1.getStrokeDashArray().addAll(5d, 5d);

        Circle marker1 = new Circle(screenA1X, screenA1Y, 10, Color.TRANSPARENT);
        marker1.setStroke(Color.BLUE);

        // [수정] 텍스트를 "Start"로 변경하고, 마커의 왼쪽(-60) 아래(+45)로 넉넉히 이동시킵니다.
        Text text1 = new Text(screenA1X - 10, screenA1Y + 65, String.format("Start: %.0fm", cleanDist1));
        text1.setFont(new Font(20));
        text1.setFill(Color.BLUE);
        text1.setScaleX(1.2); // [추가] 가로로 1.2배 늘림

        // 6. 빨간 비행기(A2) 시각물 생성
        Line line2 = new Line(screenA2X, screenA2Y, screenObsX, screenObsY);
        line2.setStroke(Color.GRAY);
        line2.getStrokeDashArray().addAll(5d, 5d);

        Circle marker2 = new Circle(screenA2X, screenA2Y, 10, Color.TRANSPARENT);
        marker2.setStroke(Color.RED);

        // [수정] 텍스트를 "Start"로 변경하고, 마커의 오른쪽(+10) 아래(+45)로 이동시킵니다.
        Text text2 = new Text(screenA2X - 20, screenA2Y + 65, String.format("Start: %.0fm", cleanDist2));
        text2.setFont(new Font(20));
        text2.setFill(Color.RED);
        text2.setScaleX(1.2); // [추가] 가로로 1.2배 늘림

        // 7. 위에서 만든 모든 선, 점, 글자를 화면 도화지(simulationPane)에 붙임
        simulationPane.getChildren().addAll(line1, line2, marker1, marker2, text1, text2);
    }

    // --- [View Logic] 비행기 갱신 (핵심 변환 로직) ---
    private void updateUI() {
        double centerX = simulationPane.getPrefWidth() / 2.0;
        double centerY = simulationPane.getPrefHeight() / 2.0;

        for (int i = 0; i < allAircrafts.size(); i++) {
            Aircraft a = allAircrafts.get(i);
            ImageView v = allViews.get(i);
            Text t = allLabels.get(i); // 라벨 가져오기

            // 1. 미터 -> 픽셀 스케일링
            double px = CoordinateConverter.toPx(a.getX());
            double py = CoordinateConverter.toPx(a.getY());

            double screenX = centerX + px;
            double screenY = centerY - py;

            // 2. 이미지 이동 및 회전
            v.setX(screenX - (v.getFitWidth() / 2));
            v.setY(screenY - (v.getFitHeight() / 2));
            v.setRotate(90 - a.getAngle());

            // 3. 라벨 이동 및 텍스트 갱신 [추가]
            t.setX(screenX + 40); // 비행기 약간 오른쪽에 표시
            t.setY(screenY - 60); // 비행기 약간 위쪽에 표시
            // 고도를 텍스트로 보여줌 (3D 확인용)
            t.setText(String.format("Alt: %.0fm", a.getZ()));

            // 효과 (상태에 따라 테두리 색상 변경)
            if ("Evade".equals(a.getTacticalState())) {
                v.setEffect(new javafx.scene.effect.DropShadow(30, Color.RED));
            } else {
                v.setEffect(null);
            }
        }
    }

    // --- 차트 생성 및 갱신 (기존 코드 유지) ---
    private FlowPane createChartsPanel() {
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(10); flowPane.setVgap(10);
        flowPane.setStyle("-fx-padding: 10; -fx-background-color: #f4f4f4;");
        flowPane.setPrefHeight(300);

        String[] memoryNames = {"ClosestAircraft", "Fuel Level", "Altitude", "Obstacle"};
        for (String name : memoryNames) {
            NumberAxis xAxis = new NumberAxis(); xAxis.setLabel("Time (s)"); xAxis.setTickUnit(5); xAxis.setAutoRanging(false);
            NumberAxis yAxis = new NumberAxis(0, 1.1, 0.25); yAxis.setLabel("Excitation"); yAxis.setAutoRanging(false);
            LineChart<Number, Number> lc = new LineChart<>(xAxis, yAxis);
            lc.setTitle(name); lc.setCreateSymbols(false); lc.setAnimated(false); lc.setPrefSize(400, 250);
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("Agent 1");
            lc.getData().add(series);
            seriesMap.put(name, series);
            flowPane.getChildren().add(lc);
        }
        return flowPane;
    }

    private void updateCharts() {
        if (allAgents.isEmpty()) return;
        Agent target = allAgents.get(0);
        for (String name : seriesMap.keySet()) {
            XYChart.Series<Number, Number> series = seriesMap.get(name);
            series.getData().add(new XYChart.Data<>(timeSeconds, target.getActivationLevel(name)));
            if (series.getData().size() > 300) series.getData().remove(0);
            NumberAxis xAxis = (NumberAxis) series.getChart().getXAxis();
            xAxis.setLowerBound(Math.max(0, timeSeconds - 10));
            xAxis.setUpperBound(Math.max(10, timeSeconds));
        }
    }

    private void clearCharts() {
        for (XYChart.Series<Number, Number> s : seriesMap.values()) s.getData().clear();
    }

    public static void main(String[] args) {
        launch(args);
    }
}