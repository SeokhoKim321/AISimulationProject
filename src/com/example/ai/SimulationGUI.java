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
        Aircraft a1 = new Aircraft(start1.x, start1.y, 150.0, 80.0, 0.0);
        a1.setDestination(dest1.x, dest1.y); // Agent 참고용 최종 목적지
        a1.setCommandTarget(dest1.x, dest1.y); // 초기 명령
        a1.setTeam("blue");
        Agent ag1 = new Agent(a1);

        // 3. 빨간 비행기 (동 -> 서)
        // 장애물보다 동쪽 600m 지점
        Point2D.Double start2 = projector.project(fixedLat, 126.6740);
        Point2D.Double dest2  = projector.project(fixedLat, 126.6400);

        // 속도 40m/s, 각도 180도(서쪽)  고도 (z) 150 추가
        Aircraft a2 = new Aircraft(start2.x, start2.y, 150.0, 80.0, 180.0);
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

        // 시작 전 위치 정렬 (한 번 갱신)
        updateUI();
    }

    // --- [View] 비행기 이미지 생성 ---
    private void createAircraftViews() {
        try {
            Image blueImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/blue_jet.png")));
            Image redImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/red_jet.png")));

            for (int i = 0; i < allAircrafts.size(); i++) {
                // 1. 이미지
                ImageView view = new ImageView(i == 0 ? blueImg : redImg);
                view.setFitWidth(40);
                view.setFitHeight(40);
                allViews.add(view);
                simulationPane.getChildren().add(view);
                // 2. 텍스트 라벨(고도 표시용) [ 추가]
                Text label = new Text("Alt: 0m");
                label.setFont(new Font(10));
                label.setFill(Color.BLACK);
                allLabels.add(label);
                simulationPane.getChildren().add(label);

            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- [View Logic] 장애물 그리기 (좌표 변환 적용) ---
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

        // [변경] Rectangle -> Circle
        Circle circle = new Circle(screenX, screenY, radiusPx);
        circle.setFill(Color.color(0.5, 0.5, 0.5, 0.5)); // 반투명 회색
        circle.setStroke(Color.BLACK);
        circle.setStrokeWidth(2);

        // (선택) 빌딩 높이 텍스트 표시
        Text heightText = new Text(screenX - 10, screenY, String.format("H: %.0fm", obs.getHeight()));

        simulationPane.getChildren().addAll(circle, heightText);
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
            t.setX(screenX + 20); // 비행기 약간 오른쪽에 표시
            t.setY(screenY - 20); // 비행기 약간 위쪽에 표시
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