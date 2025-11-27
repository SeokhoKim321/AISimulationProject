package com.example.ai;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import java.util.Objects;


public class SimulationGUI extends Application { // 이 클래스가 JavaFX 그래픽 창을 띄울 수 있는 특별한 "애플리케이션" 임을 선언
    private Airspace airspace;// 시뮬레이션의 '공역' 개체를 담을 변수
    private List<Aircraft> allAircrafts = new ArrayList<>();  // 시뮬레이션에 등장하는 모든'항공기' 객체들을 담아둘 리스트
    private List<ImageView> allViews = new ArrayList<>(); // 항공기를 화면에 그리기 위한 '전투기 이미지' 객체들을 담아둘 리스트
    private List<Agent> allAgents = new ArrayList<>(); // [새로 추가]

    private Pane root; // '도화지'를 멤버 변수로 승격(리셋시 필요)
    private AnimationTimer timer; // '타이머'를 멤버 변수로 승격(시작 / 정지시 필요)
    private HBox buttonBox; // 버튼들을 담을 상자

    private int simulationTime = 0; // 시뮬레이션이 시작된 후 몇 프레임이 지났는지 세는 '프레임 카운터'
//    private final int totalDuration = 2000;  // 이 시뮬레이션이 총 20초동안 실행될 것임을 설정

    @Override
    public void start(Stage primaryStage) throws Exception { // main 메소드로부터 launch 명령을 받으면, JavaTX가 실제로 프로그램을 시작하는 지점
        // primaryStage는 화면에 나타나는 '윈도우창' 그자체
        primaryStage.setTitle("Civil Aircraft Simulation"); // 윈도우 창의 상단 제목 표시줄에 "Civil Aircraft Simulation"이라는 글자를 설정

        // root(도화지)를 멤버 변수로 초기화
        root = new Pane();

        // 버튼 생성
        Button startButton = new Button("시작");
        Button stopButton = new Button("정지");
        Button resetButton = new Button("리셋");

        // 버튼들을 HBox(가로 상자)에 담기
        buttonBox = new HBox(10, startButton, stopButton, resetButton); // 10은 버튼 사이의 간격
        buttonBox.setLayoutX(10);// 버튼 상자의 X 위치
        buttonBox.setLayoutY(10);// 버튼 상자의 Y 위치


        this.timer = new AnimationTimer() { // 실시간 시뮬레이션의 심장. 게임처럼 매끄러운 움직임을 만들기 위해, 1초에 약 60번씩 반복 실행되는
            // 특수 타이머를 생성
            private long lastUpdate = 0; // 타이머가 너무 빨리 실행되는 것을 방지하기 위해, 마지막으로 업데이트한 시간을 기록하는 변수
            @Override
            public void handle(long now) { // AnimationTimer가 1초에 약 60번씩 실제로 호출하는 코드 블록. now는 현재 시스템 시간을 나노초 단위로 전달받음
                if (now - lastUpdate >= 16_000_000) { // "만약 마지막 업데이트 이후 16,000,000나노초 이상이 지났다면" 코드 실행 / 약 60 FPS
                    int currentSecond = (int) (simulationTime / 60.0); // 현재 프레임 카운터(simulationTime)를 60으로 나누어,
                    // 시뮬레이션이 시작된지 몇'초가 지났는지 계산

                    airspace.update(currentSecond); // '공역' 객체에게 현재 '초'를 알려주어, 공역의 상태를 업데이트하도록 지시

                    // 핵심 수정 부분
                    // 생각 단계 : 모든 두뇌(agent)가 먼저 생각하고 '몸체에 명령을 내림
                    for (Agent agent : allAgents) {
                        agent.update(airspace);
                    }

                    // 모든 몸체가 움직임
                    for (Aircraft aircraft : allAircrafts) {  // allAircrafts 리스트에 있는 모든 항공기 객체를 하나씩 꺼내어 반복
                        aircraft.executeMovement(); //  각 항공기에게 '방금 결정한 행동'에 맞춰 당신의 X,Y좌표와 각도를 '실제로 움직여라고 지시
                    }

                    updateUI(); // 아래쪽에 정의된 updateUI 메소드를 호출하여, "모든 항공기의 바뀐 위치와 각도를 실제 '화면'에 다시 그리라고 지시
                    simulationTime++; // 프레임 카운터 1증가
//                }else{
//                    timer.stop(); // 150초가 되면 자동 정지
//                    System.out.println("시뮬레이션 종료!");
                }
                lastUpdate = now; // '마지막 업데이트 시간'을 '현재 시간'으로 갱신
            }
        };
        // --- [새로 추가] 버튼 이벤트 핸들링 ---
        startButton.setOnAction(e -> {
            timer.start(); // 타이머 시작
        });

        stopButton.setOnAction(e -> {
            timer.stop(); // 타이머 정지
        });

        resetButton.setOnAction(e -> {
            timer.stop(); // 1. 타이머 정지
            simulationTime = 0; // 2. 시간 리셋
            initializeSimulation(root); // 3. 시뮬레이션 초기화 (모든 것 다시 그리기)
        });

        // [수정] 도화지에 버튼 상자를 먼저 추가
        root.getChildren().add(buttonBox);

        // [수정] 시뮬레이션을 처음 한 번 초기화 (항공기 배치)
        initializeSimulation(root);

        timer.start(); //위에서 설계한 AnimationTimer를 시작시킴. 이 순간부터 handle 메소드가 1초에 60번씩 돌기 시작함

        Scene scene = new Scene(root, 1400, 1000); // 우리가 만든 '도화지'를 '가로 1400, 세로 800' 크기의 장면으로 만듬
        primaryStage.setScene(scene); // '윈도우 창'에 방금 만든 '장면'을 끼워넣음
        primaryStage.show(); // 최종적으로 윈도우 창을 사용자 화면에 보여줌
    }

    private void initializeSimulation(Pane root) { // start 메소드에서 호출했던 '초기 설정' 전용 메소드
        // [수정] 리셋을 위해 리스트들을 모두 비움
        allAircrafts.clear();
        allAgents.clear();
        allViews.clear();

        // [수정] '도화지'를 비움 (버튼 상자 제외)
        root.getChildren().clear();
        root.getChildren().add(buttonBox); // 버튼 상자는 다시 추가

        // [수정] 공역도 새로 만듦
        airspace = new Airspace();

        // 1번 항공기: 왼쪽 위에서 오른쪽 아래로 비행
        Aircraft aircraft1 = new Aircraft(100, 100, "blue", 1200, 1000);
        Agent agent1 = new Agent(aircraft1); // 몸체를 두뇌에 연결
        // 2번 항공기: 오른쪽 아래에서 왼쪽 위로 비행
        Aircraft aircraft2 = new Aircraft(1300, 800, "red",  100, 200);
        Agent agent2 = new Agent(aircraft2);
        // 3번 항공기: 왼쪽 아래에서 오른쪽 위로 비행
        Aircraft aircraft3 = new Aircraft(100, 800, "red", 1300, 100);
        Agent agent3 = new Agent(aircraft3);
        allAircrafts.add(aircraft1);
        allAircrafts.add(aircraft2);
        allAircrafts.add(aircraft3);


        allAgents.add(agent1);
        allAgents.add(agent2);
        allAgents.add(agent3);

        airspace.addAgent(aircraft1);
        airspace.addAgent(aircraft2);
        airspace.addAgent(aircraft3); // '공역' 객체에게도 이 3대의 항공기가 현재 공역에 존재함을 알려줌

        try { // 이미지 파일을 불러오는 작업은 파일이 없을 경우 오류가 날 수 있으므로, try-catch 구문으로 감싸줌
            Image blueJetImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/blue_jet.png")));
            // resource 폴더에서 blue_jet.png 파일을 찾아' 이미지 ' 객체로 불러옴
            Image redJetImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/red_jet.png")));
            // resource 폴더에서 red_jet.png 파일을 찾아' 이미지 ' 객체로 불러옴

            for (Aircraft aircraft : allAircrafts) { // allAircrafts 리스트에 있는 모든 항공기를 하나씩 꺼내어 반복
                Image img = "blue".equals(aircraft.getTeam()) ? blueJetImage : redJetImage;
                // 항공기의 팀을 확인(aircraft.getTeam()) 하여 'blue'팀이면 blueJetImage를 'red'팀이면 redJetImage를 imag 변수에 선택하여 담음
                ImageView view = new ImageView(img); // 선택된 img를 화면에 표시할 수 있는 '이미지 뷰' 객체로 만듬
                view.setFitWidth(40);
                view.setFitHeight(40);
                // 이미지의 크기를 가로/세로 40픽셀로 조절
                allViews.add(view); // 생성된 ImageView 객체를 allViews 리스트에 추가

                // 새로 추가 (항로 및 목적지에 X표시 추가)
                // 1. 목적지에 "X" 표시 추가
                Text destinationMark = new Text(aircraft.getDestX(), aircraft.getDestY(), "X");
                destinationMark.setFill(Color.GRAY); // 회색으로 설정

                // 2. 항로(점선) 추가
                Line flightPath = new Line();
                flightPath.setStartX(aircraft.getX()); // 시작 X
                flightPath.setStartY(aircraft.getY()); // 시작 Y
                flightPath.setEndX(aircraft.getDestX()); // 목적지 X
                flightPath.setEndY(aircraft.getDestY()); // 목적지 Y
                flightPath.setStroke(Color.GRAY); // 회색으로 설정
                flightPath.getStrokeDashArray().addAll(5.0, 5.0); // 5픽셀 그리고, 5픽셀 띄우는 점선

                // ' 도화지'에 항로와 X 표시를 먼저 추가
                root.getChildren().addAll(flightPath, destinationMark);
            }
            root.getChildren().addAll(allViews); // allViews 리스트에 담긴 모든 ImagView 객체들을 '도화지'에 한꺼번에 추가하여 화면에 보이도록 함
        } catch (NullPointerException e) { // 만약 try 블록에서 resource 폴더에 이미지가 없어 NullPointerException(파일 없음 오류) 발생하면 catch 블록 실행
            System.err.println("이미지 파일을 찾을 수 없습니다!");
        }
    }

    private void updateUI() { // AnimationTimer가 매 프레임 호출하는 '화면 그리기' 전용 메소드
        for (int i = 0; i < allAircrafts.size(); i++) { // allAircrafts 리스트의 첫 번째부터 마지막까지 순서대로(i) 반복
            Aircraft aircraft = allAircrafts.get(i); // i 번째 '항공기' 객체를 가져옴
            ImageView view = allViews.get(i); // i 번째 '이미지 뷰'객체를 가져옴
            view.setX(aircraft.getX() - view.getFitWidth() / 2); // '이미지 뷰'의 X좌표를 '항공기의 X좌표로 설정함
            // 이미지 중심을 맞추기 위해 너비의 절반을 뺌
            view.setY(aircraft.getY() - view.getFitHeight() / 2);
            view.setRotate(aircraft.getAngle());

            // (충돌 회피 시각화를 위해 색상 변경 로직을 다시 추가 - 선택 사항)
            if ("Evade".equals(aircraft.getTacticalState())) { // 만약 '항공기'의 현재 상태가 'Eavde' 라면:
                view.setEffect(new javafx.scene.effect.DropShadow(20, javafx.scene.paint.Color.YELLOW));
                // 이미지에 노란색 '그림자 효과'를 추가하여 회피 중임을 시각적으로 강조
            } else {
                view.setEffect(null); // 그렇지 않으면 이미지에 적용된 모든 효과 제거
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    } // 자바 프로그램이 실행될 때 가장 먼저 호출되는 코드
    // JafaFX애플리케이션을 실행하라는 특수 명령어
}