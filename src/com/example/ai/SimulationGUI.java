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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SimulationGUI extends Application {
    private Airspace airspace;
    private List<Aircraft> allAircrafts = new ArrayList<>();
    private List<ImageView> allViews = new ArrayList<>();
    private List<Agent> allAgents = new ArrayList<>();

    // [수정] 레이아웃 분리를 위해 Pane 이름 변경 (root -> simulationPane)
    private Pane simulationPane; // 비행기가 날아다니는 '중앙 도화지'
    private AnimationTimer timer;  // 1초에 60번 업데이트되는 타이머
    private HBox buttonBox; // 버튼들을 담는 상자

    // [신규] 그래프 관련 변수들
    // "어떤 데이터(이름)"가 "어떤 그래프 시리즈"에 대응되는지 저장하는 맵
    private Map<String, XYChart.Series<Number, Number>> seriesMap = new HashMap<>();

    private double timeSeconds = 0.0; // 그래프 X축용 시간(초)

    private int simulationTime = 0; // 시뮬레이션 내부 프레임 카운트

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("Civil Aircraft Simulation (Cognitive Monitor)");
        // 기능 : 화면 레이아웃을 잡고, timer(심장박동)를 작동시킴.

        // 1. 전체 화면 레이아웃 (BorderPane 사용)
        // 화면을 상단, 하단, 좌측, 우측, 중앙으로 나눌 수 있음
        BorderPane mainLayout = new BorderPane();

        // 2. 시뮬레이션 도화지 (중앙 배치)
        simulationPane = new Pane();
        // 시뮬레이션 화면 크기 설정 (그래프 공간 확보를 위해 높이 조정)
        simulationPane.setPrefSize(1400, 600);
        mainLayout.setCenter(simulationPane); // 중앙에 배치

        // 3. 버튼 상자 (상단 배치)
        Button startButton = new Button("시작");
        Button stopButton = new Button("정지");
        Button resetButton = new Button("리셋");
        buttonBox = new HBox(10, startButton, stopButton, resetButton);
        buttonBox.setStyle("-fx-padding: 10; -fx-background-color: #ddd;");
        mainLayout.setTop(buttonBox); // 상단에 배치

        // 4. [핵심] 그래프 패널 생성 (하단 배치)
        // createChartsPanel 메소드에서 그래프 패널 생성
        FlowPane chartsPane = createChartsPanel();
        mainLayout.setBottom(chartsPane); // 하단에 배치

        // 5. 타이머 설정
        this.timer = new AnimationTimer() {
            private long lastUpdate = 0;
            @Override
            public void handle(long now) {
                if (now - lastUpdate >= 16_000_000) {
                    int currentSecond = (int) (simulationTime / 60.0);
                    airspace.update(currentSecond);

                    // 생각 단계
                    for (Agent agent : allAgents) {
                        agent.update(airspace);
                    }

                    // 행동 단계
                    for (Aircraft aircraft : allAircrafts) {
                        aircraft.executeMovement();
                    }

                    updateUI();     // 비행기 화면 갱신
                    updateCharts(); // [추가] 그래프 데이터 갱신

                    simulationTime++;
                    timeSeconds += 0.016; // 약 60FPS 기준 시간 흐름 누적
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
            clearCharts(); // 그래프 데이터도 초기화
        });

        // 초기화 실행
        initializeSimulation();

        // 장면 생성 (전체 레이아웃인 mainLayout을 넣음)
        Scene scene = new Scene(mainLayout, 1400, 900); // 높이를 좀 더 늘림
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // --- [신규] 그래프 패널 생성 메소드 ---
    private FlowPane createChartsPanel() {
        // 그래프들을 가로로 흐르듯 배치하는 패널
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(10);
        flowPane.setVgap(10);
        flowPane.setStyle("-fx-padding: 10; -fx-background-color: #f4f4f4; -fx-border-color: #ccc; -fx-border-width: 1px 0 0 0;");
        flowPane.setPrefHeight(300); // 하단 패널 높이

        // 우리가 추적할 기억(MemoryCell)의 이름들
        // (Agent.java의 cellNames 배열과 일치해야 데이터가 나옵니다)
        String[] memoryNames = {"ClosestAircraft", "Fuel Level", "Altitude", "Obstacle"};

        for (String name : memoryNames) {
            // 1. X축 (시간)
            NumberAxis xAxis = new NumberAxis();
            xAxis.setLabel("Time (s)");
            xAxis.setAutoRanging(false); // 스크롤 효과를 위해 자동 범위 끔
            xAxis.setTickUnit(5);

            // 2. Y축 (활성도 0.0 ~ 1.0)
            // 논문 이미지처럼 0~1 사이 범위를 고정합니다.
            NumberAxis yAxis = new NumberAxis(0, 1.1, 0.25);
            yAxis.setLabel("Excitation");
            yAxis.setAutoRanging(false);

            // 3. 라인 차트 생성
            LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
            lineChart.setTitle(name); // 제목 설정 (예 : Altitude)
            lineChart.setCreateSymbols(false); // 점(Symbol)을 없애고 선만 그림 (성능 최적화 필수!)
            lineChart.setAnimated(false);      // 실시간 갱신 시 애니메이션 끄기 (성능 최적화)
            lineChart.setPrefSize(400, 250);   // 그래프 크기 지정

            // 4. 데이터 시리즈 생성
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("Agent 1 (Blue)");  // 범례 이름
            lineChart.getData().add(series); // 차트에 선 추가

            // 5. 나중에 데이터를 넣기 위해 '이름표'를 붙여서 저장
            seriesMap.put(name, series);

            // 패널에 차트 추가
            flowPane.getChildren().add(lineChart);
        }

        return flowPane; // 생성된 그래프 패널 반환
    }

    // --- [신규] 그래프 데이터 업데이트 메소드 ---
    private void updateCharts() {
        if (allAgents.isEmpty()) return;

        // 여기서는 '1번 항공기(Blue)'의 두뇌 상태만 모니터링합니다.
        Agent targetAgent = allAgents.get(0);

        // Map에 저장된 모든 그래프 시리즈를 순회하며 업데이트
        for (String name : seriesMap.keySet()) {
            XYChart.Series<Number, Number> series = seriesMap.get(name);

            // Agent에게서 해당 기억의 활성도(Activation Level)를 가져옴
            double activation = targetAgent.getActivationLevel(name);

            // 데이터 추가 (X: 시간, Y: 활성도)
            series.getData().add(new XYChart.Data<>(timeSeconds, activation));

            // 데이터가 너무 많이 쌓이면 메모리 부족 및 렉 발생 -> 오래된 데이터 삭제 (슬라이딩 윈도우)
            if (series.getData().size() > 500) { // 약 8초 분량 데이터 유지
                series.getData().remove(0);
            }

            // X축이 시간에 따라 흘러가도록 범위 조정 (현재 시간 기준 -10초 ~ 현재 시간)
            NumberAxis xAxis = (NumberAxis) series.getChart().getXAxis();
            xAxis.setLowerBound(Math.max(0, timeSeconds - 10));
            xAxis.setUpperBound(Math.max(10, timeSeconds));
        }
    }

    // 그래프 초기화
    private void clearCharts() {
        for (XYChart.Series<Number, Number> series : seriesMap.values()) {
            series.getData().clear();
        }
    }

    // 기존 초기화 메소드 (매개변수 제거하고 멤버변수 사용)
    private void initializeSimulation() {
        allAircrafts.clear();
        allAgents.clear();
        allViews.clear();

        simulationPane.getChildren().clear(); // 도화지 비우기

        airspace = new Airspace();

        // [도심 협곡 시나리오]  장애물 삭제시 여기부터
        // 파란 비행기 경로상에 빌딩 배치
        Obstacle b1 = new Obstacle(600, 200, 200, 150); // (x, y, w, h)
        Obstacle b2 = new Obstacle(600, 450, 200, 150);

        airspace.addObstacle(b1);
        airspace.addObstacle(b2);

        // [수정 코드] 그라데이션과 테두리 적용
        // 1. 빌딩 느낌의 그라데이션 페인트 생성 (좌상단은 밝은 회색, 우하단은 어두운 회색)
        javafx.scene.paint.Stop[] stops = new javafx.scene.paint.Stop[] {
                new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.web("#A9A9A9")), // 밝은 회색 (콘크리트색)
                new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.web("#696969"))  // 어두운 회색 (그림자)
        };
        // (시작X, 시작Y, 끝X, 끝Y, 비례여부, 반복방법, 색상정지점들)
        javafx.scene.paint.LinearGradient buildingPaint = new javafx.scene.paint.LinearGradient(
                0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.NO_CYCLE, stops
        );

        // 2. 사각형 생성 및 스타일 적용
        javafx.scene.shape.Rectangle rect1 = new javafx.scene.shape.Rectangle(b1.getX(), b1.getY(), b1.getWidth(), b1.getHeight());
        rect1.setFill(buildingPaint); // 그라데이션 채우기
        rect1.setStroke(javafx.scene.paint.Color.DARKSLATEGRAY); // 진한 테두리 추가
        rect1.setStrokeWidth(2); // 테두리 두께

        javafx.scene.shape.Rectangle rect2 = new javafx.scene.shape.Rectangle(b2.getX(), b2.getY(), b2.getWidth(), b2.getHeight());
        rect2.setFill(buildingPaint);
        rect2.setStroke(javafx.scene.paint.Color.DARKSLATEGRAY);
        rect2.setStrokeWidth(2);

        // 3. (선택 사항) 약간의 그림자 효과 추가
        javafx.scene.effect.DropShadow buildingShadow = new javafx.scene.effect.DropShadow(10, 5, 5, javafx.scene.paint.Color.BLACK);
        rect1.setEffect(buildingShadow);
        rect2.setEffect(buildingShadow);

        simulationPane.getChildren().addAll(rect1, rect2);

        // 장애물 삭제시 여기까지

        // 1번 항공기
        Aircraft aircraft1 = new Aircraft(50, 400, "blue", 1350, 400);
        Agent agent1 = new Agent(aircraft1);
        // 2번 항공기
        Aircraft aircraft2 = new Aircraft(1350, 440, "red",  50, 360);
        Agent agent2 = new Agent(aircraft2);
        // 3번 항공기
        Aircraft aircraft3 = new Aircraft(700, 100, "red", 700, 700);
        Agent agent3 = new Agent(aircraft3);





        allAircrafts.add(aircraft1);
        allAircrafts.add(aircraft2);
        allAircrafts.add(aircraft3);

        allAgents.add(agent1);
        allAgents.add(agent2);
        allAgents.add(agent3);

        airspace.addAgent(aircraft1);
        airspace.addAgent(aircraft2);
        airspace.addAgent(aircraft3);

        try {
            Image blueJetImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/blue_jet.png")));
            Image redJetImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/red_jet.png")));

            for (Aircraft aircraft : allAircrafts) {
                Image img = "blue".equals(aircraft.getTeam()) ? blueJetImage : redJetImage;
                ImageView view = new ImageView(img);
                view.setFitWidth(40);
                view.setFitHeight(40);
                allViews.add(view);

                Text destinationMark = new Text(aircraft.getDestX(), aircraft.getDestY(), "X");
                destinationMark.setFill(Color.GRAY);

                Line flightPath = new Line();
                flightPath.setStartX(aircraft.getX());
                flightPath.setStartY(aircraft.getY());
                flightPath.setEndX(aircraft.getDestX());
                flightPath.setEndY(aircraft.getDestY());
                flightPath.setStroke(Color.GRAY);
                flightPath.getStrokeDashArray().addAll(5.0, 5.0);

                simulationPane.getChildren().addAll(flightPath, destinationMark);
            }
            simulationPane.getChildren().addAll(allViews);
        } catch (NullPointerException e) {
            System.err.println("이미지 파일을 찾을 수 없습니다!");
        }
    }

    // 이 메소드를 찾아서 아래 내용으로 완전히 교체하세요.
    private void updateUI() {
        for (int i = 0; i < allAircrafts.size(); i++) {
            Aircraft aircraft = allAircrafts.get(i);
            ImageView view = allViews.get(i);

            // 1. 위치 및 각도 업데이트 (기본)
            view.setX(aircraft.getX() - view.getFitWidth() / 2);
            view.setY(aircraft.getY() - view.getFitHeight() / 2);
            view.setRotate(aircraft.getAngle());

            // --- [시각화 핵심 수정] 비행기 테두리 효과(Glow) 적용 ---

            // 적용할 효과를 담을 변수 (기본은 효과 없음)
            javafx.scene.effect.DropShadow effect = null;

            // A. [주인공 표시] 만약 이 비행기가 'Agent 1'(인덱스 0번)이라면?
            if (i == 0) {
                // 평소에도 밝은 하늘색(CYAN) 빛이 나도록 설정 (관찰 대상임을 표시)
                // 반경(radius)을 30으로 크게 줘서 눈에 확 띄게 합니다.
                effect = new javafx.scene.effect.DropShadow(30, javafx.scene.paint.Color.CYAN);
            }

            // B. [상태 표시] 만약 비행기가 '회피(Evade)' 상태라면?
            if ("Evade".equals(aircraft.getTacticalState())) {
                // 주인공이든 아니든, 회피 중이면 강렬한 노란색 경고등으로 덮어씁니다.
                effect = new javafx.scene.effect.DropShadow(30, javafx.scene.paint.Color.YELLOW);
            }

            // 최종 결정된 효과를 이미지에 적용
            view.setEffect(effect);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}