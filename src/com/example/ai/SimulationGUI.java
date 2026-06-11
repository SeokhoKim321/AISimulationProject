package com.example.ai;

import com.example.utils.CoordinateConverter;
import com.example.utils.LambertProjection;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class SimulationGUI extends Application {
    private static final boolean USE_SIMULINK_BRIDGE = true;
    private static final String SIMULINK_HOST = "127.0.0.1";
    private static final int SIMULINK_COMMAND_PORT = 9091;
    private static final int SIMULINK_STATE_PORT = 9090;

    private Airspace airspace;
    private final List<Aircraft> allAircrafts = new ArrayList<>();
    private final List<ImageView> allViews = new ArrayList<>();
    private final List<Text> allLabels = new ArrayList<>();
    private final List<Agent> allAgents = new ArrayList<>();
    private final Map<Integer, Aircraft> aircraftById = new HashMap<>();

    private Pane simulationPane;
    private AnimationTimer timer;
    private HBox buttonBox;

    private LambertProjection projector;
    private final Map<String, XYChart.Series<Number, Number>> seriesMap = new HashMap<>();
    private SimulinkStateReceiver stateReceiver;
    private SimulinkCommandSender commandSender;

    private double timeSeconds = 0.0;
    private int simulationTime = 0;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Civil Aircraft Simulation (Logic: Meter / View: Pixel)");

        BorderPane mainLayout = new BorderPane();
        simulationPane = new Pane();
        simulationPane.setPrefSize(1400, 600);
        mainLayout.setCenter(simulationPane);

        Button startButton = new Button("Start");
        Button stopButton = new Button("Stop");
        Button resetButton = new Button("Reset");
        buttonBox = new HBox(10, startButton, stopButton, resetButton);
        buttonBox.setStyle("-fx-padding: 10; -fx-background-color: #ddd;");
        mainLayout.setTop(buttonBox);

        FlowPane chartsPane = createChartsPanel();
        mainLayout.setBottom(chartsPane);

        this.timer = new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (now - lastUpdate < 16_000_000) {
                    return;
                }

                int currentSecond = (int) (simulationTime / 60.0);
                airspace.update(currentSecond);

                for (Agent agent : allAgents) {
                    agent.update(airspace);
                }

                if (USE_SIMULINK_BRIDGE) {
                    sendCommandsToSimulink();
                } else {
                    for (Aircraft aircraft : allAircrafts) {
                        aircraft.executeMovement(0.016);
                    }
                }

                updateUI();
                updateCharts();

                simulationTime++;
                timeSeconds += 0.016;
                lastUpdate = now;
            }
        };

        startButton.setOnAction(e -> timer.start());
        stopButton.setOnAction(e -> timer.stop());
        resetButton.setOnAction(e -> {
            timer.stop();
            simulationTime = 0;
            timeSeconds = 0.0;
            initializeSimulation();
            clearCharts();
        });

        initializeSimulation();
        Scene scene = new Scene(mainLayout, 1400, 900);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void initializeSimulation() {
        shutdownSimulinkBridge();

        allAircrafts.clear();
        allAgents.clear();
        allViews.clear();
        allLabels.clear();
        aircraftById.clear();
        simulationPane.getChildren().clear();

        airspace = new Airspace();
        projector = new LambertProjection(37.4500, 126.6530, 30.0, 60.0);

        double fixedLat = 37.4500;
        double obstacleLat = 37.4510;
        Point2D.Double obsPos = projector.project(obstacleLat, 126.6620);

        Obstacle centerBuilding = new Obstacle(obsPos.x - 100.0, obsPos.y - 75.0, 100.0, 150.0);
        airspace.addObstacle(centerBuilding);
        drawObstacle(centerBuilding);

        Point2D.Double start1 = projector.project(fixedLat, 126.6480);
        Point2D.Double dest1 = projector.project(fixedLat, 126.6800);

        Aircraft a1 = new Aircraft(start1.x, start1.y, 150.0, 40.0, 0.0);
        a1.setId(1);
        a1.setDestination(dest1.x, dest1.y, 150.0);
        a1.setCommandTarget(dest1.x, dest1.y, 150.0);
        a1.setTeam("blue");
        a1.setSimulinkControlled(USE_SIMULINK_BRIDGE);
        Agent ag1 = new Agent(a1);

        Point2D.Double start2 = projector.project(fixedLat, 126.6740);
        Point2D.Double dest2 = projector.project(fixedLat, 126.6400);

        Aircraft a2 = new Aircraft(start2.x, start2.y, 150.0, 40.0, 180.0);
        a2.setId(2);
        a2.setDestination(dest2.x, dest2.y, 150.0);
        a2.setCommandTarget(dest2.x, dest2.y, 150.0);
        a2.setTeam("red");
        a2.setSimulinkControlled(USE_SIMULINK_BRIDGE);
        Agent ag2 = new Agent(a2);

        allAircrafts.add(a1);
        allAircrafts.add(a2);
        allAgents.add(ag1);
        allAgents.add(ag2);
        airspace.addAgent(a1);
        airspace.addAgent(a2);
        aircraftById.put(a1.getId(), a1);
        aircraftById.put(a2.getId(), a2);

        if (USE_SIMULINK_BRIDGE) {
            initializeSimulinkBridge();
        }

        createAircraftViews();
        drawInitialPositions(a1, a2, centerBuilding);
        updateUI();
    }

    private void createAircraftViews() {
        try {
            Image blueImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/blue_jet_2.png")));
            Image redImg = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/red_jet_2.png")));

            for (int i = 0; i < allAircrafts.size(); i++) {
                ImageView view = new ImageView(i == 0 ? blueImg : redImg);
                view.setFitWidth(40);
                view.setFitHeight(40);
                allViews.add(view);
                simulationPane.getChildren().add(view);

                Text label = new Text("Alt: 0m");
                label.setFont(new Font(20));
                label.setFill(Color.BLACK);
                label.setScaleX(1.2);
                allLabels.add(label);
                simulationPane.getChildren().add(label);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawObstacle(Obstacle obs) {
        double centerX = simulationPane.getPrefWidth() / 2.0;
        double centerY = simulationPane.getPrefHeight() / 2.0;
        double radiusPx = CoordinateConverter.toPx(obs.getRadius());
        double px = CoordinateConverter.toPx(obs.getX());
        double py = CoordinateConverter.toPx(obs.getY());
        double screenX = centerX + px;
        double screenY = centerY - py;

        Circle circle = new Circle(screenX, screenY, radiusPx);
        circle.setFill(Color.color(0.5, 0.5, 0.5, 0.5));
        circle.setStroke(Color.BLACK);
        circle.setStrokeWidth(2);

        Circle centerDot = new Circle(screenX, screenY, 3, Color.BLACK);
        Line radiusLine = new Line(screenX, screenY, screenX + radiusPx, screenY);
        radiusLine.setStroke(Color.BLACK);
        radiusLine.getStrokeDashArray().addAll(5d, 5d);

        Text radiusText = new Text(screenX + (radiusPx / 2.0) - 30, screenY - 10, String.format("R: %.0fm", obs.getRadius()));
        radiusText.setFont(new Font(20));
        radiusText.setFill(Color.BLUE);
        radiusText.setScaleX(1.2);

        Text heightText = new Text(screenX - 35, screenY + 25, String.format("H: %.0fm", obs.getHeight()));
        heightText.setFont(new Font(20));
        heightText.setFill(Color.RED);
        heightText.setScaleX(1.2);

        simulationPane.getChildren().addAll(circle, centerDot, radiusLine, radiusText, heightText);
    }

    private void drawInitialPositions(Aircraft a1, Aircraft a2, Obstacle obs) {
        double centerX = simulationPane.getPrefWidth() / 2.0;
        double centerY = simulationPane.getPrefHeight() / 2.0;

        double obsPx = CoordinateConverter.toPx(obs.getX());
        double obsPy = CoordinateConverter.toPx(obs.getY());
        double screenObsX = centerX + obsPx;
        double screenObsY = centerY - obsPy;

        double a1Px = CoordinateConverter.toPx(a1.getX());
        double a1Py = CoordinateConverter.toPx(a1.getY());
        double screenA1X = centerX + a1Px;
        double screenA1Y = centerY - a1Py;

        double a2Px = CoordinateConverter.toPx(a2.getX());
        double a2Py = CoordinateConverter.toPx(a2.getY());
        double screenA2X = centerX + a2Px;
        double screenA2Y = centerY - a2Py;

        double rawDist1 = Math.sqrt(Math.pow(a1.getX() - obs.getX(), 2) + Math.pow(a1.getY() - obs.getY(), 2));
        double rawDist2 = Math.sqrt(Math.pow(a2.getX() - obs.getX(), 2) + Math.pow(a2.getY() - obs.getY(), 2));
        double cleanDist1 = Math.round(rawDist1 / 100.0) * 100.0;
        double cleanDist2 = Math.round(rawDist2 / 100.0) * 100.0;

        Line line1 = new Line(screenA1X, screenA1Y, screenObsX, screenObsY);
        line1.setStroke(Color.GRAY);
        line1.getStrokeDashArray().addAll(5d, 5d);

        Circle marker1 = new Circle(screenA1X, screenA1Y, 10, Color.TRANSPARENT);
        marker1.setStroke(Color.BLUE);

        Text text1 = new Text(screenA1X - 10, screenA1Y + 65, String.format("Start: %.0fm", cleanDist1));
        text1.setFont(new Font(20));
        text1.setFill(Color.BLUE);
        text1.setScaleX(1.2);

        Line line2 = new Line(screenA2X, screenA2Y, screenObsX, screenObsY);
        line2.setStroke(Color.GRAY);
        line2.getStrokeDashArray().addAll(5d, 5d);

        Circle marker2 = new Circle(screenA2X, screenA2Y, 10, Color.TRANSPARENT);
        marker2.setStroke(Color.RED);

        Text text2 = new Text(screenA2X - 20, screenA2Y + 65, String.format("Start: %.0fm", cleanDist2));
        text2.setFont(new Font(20));
        text2.setFill(Color.RED);
        text2.setScaleX(1.2);

        simulationPane.getChildren().addAll(line1, line2, marker1, marker2, text1, text2);
    }

    private void updateUI() {
        double centerX = simulationPane.getPrefWidth() / 2.0;
        double centerY = simulationPane.getPrefHeight() / 2.0;

        for (int i = 0; i < allAircrafts.size(); i++) {
            Aircraft aircraft = allAircrafts.get(i);
            ImageView view = allViews.get(i);
            Text label = allLabels.get(i);

            double px = CoordinateConverter.toPx(aircraft.getX());
            double py = CoordinateConverter.toPx(aircraft.getY());
            double screenX = centerX + px;
            double screenY = centerY - py;

            view.setX(screenX - (view.getFitWidth() / 2));
            view.setY(screenY - (view.getFitHeight() / 2));
            view.setRotate(90 - aircraft.getAngle());

            label.setX(screenX + 40);
            label.setY(screenY - 60);
            label.setText(String.format(
                    "Alt: %.0fm%nRoll: %.1f%nPitch: %.1f",
                    aircraft.getZ(),
                    aircraft.getRoll(),
                    aircraft.getPitch()
            ));

            if ("Evade".equals(aircraft.getTacticalState())) {
                view.setEffect(new javafx.scene.effect.DropShadow(30, Color.RED));
            } else {
                view.setEffect(null);
            }
        }
    }

    private FlowPane createChartsPanel() {
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(10);
        flowPane.setVgap(10);
        flowPane.setStyle("-fx-padding: 10; -fx-background-color: #f4f4f4;");
        flowPane.setPrefHeight(300);

        String[] memoryNames = {"ClosestAircraft", "Fuel Level", "Altitude", "Obstacle"};
        for (String name : memoryNames) {
            NumberAxis xAxis = new NumberAxis();
            xAxis.setLabel("Time (s)");
            xAxis.setTickUnit(5);
            xAxis.setAutoRanging(false);

            NumberAxis yAxis = new NumberAxis(0, 1.1, 0.25);
            yAxis.setLabel("Excitation");
            yAxis.setAutoRanging(false);

            LineChart<Number, Number> lc = new LineChart<>(xAxis, yAxis);
            lc.setTitle(name);
            lc.setCreateSymbols(false);
            lc.setAnimated(false);
            lc.setPrefSize(400, 250);

            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("Agent 1");
            lc.getData().add(series);
            seriesMap.put(name, series);
            flowPane.getChildren().add(lc);
        }

        return flowPane;
    }

    private void updateCharts() {
        if (allAgents.isEmpty()) {
            return;
        }

        Agent target = allAgents.get(0);
        for (String name : seriesMap.keySet()) {
            XYChart.Series<Number, Number> series = seriesMap.get(name);
            series.getData().add(new XYChart.Data<>(timeSeconds, target.getActivationLevel(name)));
            NumberAxis xAxis = (NumberAxis) series.getChart().getXAxis();
            xAxis.setLowerBound(0);
            xAxis.setUpperBound(Math.max(10, timeSeconds));
        }
    }

    private void clearCharts() {
        for (XYChart.Series<Number, Number> series : seriesMap.values()) {
            series.getData().clear();
        }
    }

    private void initializeSimulinkBridge() {
        try {
            commandSender = new SimulinkCommandSender(SIMULINK_HOST, SIMULINK_COMMAND_PORT);
            stateReceiver = new SimulinkStateReceiver(aircraftById, SIMULINK_STATE_PORT);
            stateReceiver.start();
        } catch (Exception e) {
            System.out.println("Failed to initialize Simulink bridge.");
            e.printStackTrace();
        }
    }

    private void shutdownSimulinkBridge() {
        if (stateReceiver != null) {
            stateReceiver.stopListening();
            stateReceiver = null;
        }
        if (commandSender != null) {
            commandSender.close();
            commandSender = null;
        }
    }

    private void sendCommandsToSimulink() {
        if (commandSender == null) {
            return;
        }
        for (Aircraft aircraft : allAircrafts) {
            commandSender.sendCommand(aircraft);
        }
    }

    @Override
    public void stop() {
        shutdownSimulinkBridge();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
