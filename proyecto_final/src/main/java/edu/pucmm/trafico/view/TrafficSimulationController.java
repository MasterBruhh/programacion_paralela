package edu.pucmm.trafico.view;

import edu.pucmm.trafico.core.*;
import edu.pucmm.trafico.model.*;
import edu.pucmm.trafico.concurrent.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Rotate;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TrafficSimulationController {

    @FXML
    private Pane lienzo;

    private SimulationEngine simulationEngine;
    private StartPoint selectedStartPoint = StartPoint.NORTH;
    private VehicleType selectedVehicleType = VehicleType.NORMAL;
    private Direction selectedDirection = Direction.STRAIGHT;
    private Label configLabel;

    private TrafficLightGroup CalleIzqGroup = new TrafficLightGroup("CalleIzq");
    private TrafficLightGroup CalleDerGroup = new TrafficLightGroup("CalleDer");
    private TrafficLightGroup arribaGroup = new TrafficLightGroup("ARRIBA");
    private TrafficLightGroup abajoGroup = new TrafficLightGroup("ABAJO");
    private TrafficLightController lightController;
    
    private Map<String, TrafficLightGroup> trafficLightGroups;

    @FXML
    public void initialize() {
        drawRoadsWithLanes();
        
        trafficLightGroups = new HashMap<>();
        trafficLightGroups.put("CalleIzq", CalleIzqGroup);
        trafficLightGroups.put("CalleDer", CalleDerGroup);
        trafficLightGroups.put("ARRIBA", arribaGroup);
        trafficLightGroups.put("ABAJO", abajoGroup);
        
        simulationEngine = new SimulationEngine(lienzo, trafficLightGroups);
        createConfigDisplay();

        lightController = new TrafficLightController(List.of(CalleIzqGroup, CalleDerGroup, arribaGroup, abajoGroup));
        lightController.start();

        simulationEngine.start();
    }

    private void createConfigDisplay() {
        configLabel = new Label(getConfigText());
        configLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        configLabel.setTextFill(Color.WHITE);
        configLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-padding: 10; -fx-background-radius: 5;");
        configLabel.setLayoutX(10);
        configLabel.setLayoutY(10);

        lienzo.getChildren().add(configLabel);
    }

    private String getConfigText() {
        return "Configuración: " + selectedStartPoint + " | " + selectedVehicleType + " | " + selectedDirection;
    }

    @FXML
    public void handleConfiguracion(ActionEvent event) {
        String option = ((MenuItem) event.getSource()).getId();

        switch (option) {
            case "Arriba" -> selectedStartPoint = StartPoint.NORTH;
            case "Abajo" -> selectedStartPoint = StartPoint.SOUTH;
            case "Izquierda" -> selectedStartPoint = StartPoint.WEST;
            case "Derecha" -> selectedStartPoint = StartPoint.EAST;
            case "Normal" -> selectedVehicleType = VehicleType.NORMAL;
            case "Emergencia" -> selectedVehicleType = VehicleType.EMERGENCY;
            case "Recto" -> selectedDirection = Direction.STRAIGHT;
            case "VIzquierda" -> selectedDirection = Direction.LEFT;
            case "VDerecha" -> selectedDirection = Direction.RIGHT;
            case "U" -> selectedDirection = Direction.U_TURN;
        }

        configLabel.setText(getConfigText());
    }

    @FXML
    public void crearVehiculo() {
        Vehicle vehicle = new Vehicle(selectedVehicleType, selectedStartPoint, selectedDirection);
        simulationEngine.addVehicle(vehicle);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Vehículo Creado");
        alert.setHeaderText("Vehículo #" + vehicle.getId() + " creado exitosamente");

        String content = String.format(
                "Tipo: %s\n" +
                "Punto de salida: %s\n" +
                "Dirección: %s\n" +
                "Posición inicial: (%.0f, %.0f)",
                selectedVehicleType,
                selectedStartPoint,
                selectedDirection,
                vehicle.getX(),
                vehicle.getY()
        );

        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void crearLoteVehiculos() {
        final int cantidad = 15;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        StringBuilder resumen = new StringBuilder();
        resumen.append("Se crearon ").append(cantidad).append(" vehículos:\n\n");

        for (int i = 0; i < cantidad; i++) {
            if (rnd.nextDouble() < 0.7) {
                boolean goingNorth = rnd.nextBoolean();
                Direction dir = Direction.values()[rnd.nextInt(4)];
                VehicleType tipo = pickVehicleType(rnd);
                
                HighwayLaneManager laneManager = new HighwayLaneManager();
                HighwayLane lane = laneManager.assignLane(dir, goingNorth);
                
                try {
                    Vehicle v = new Vehicle(tipo, lane, dir);
                    v.setTargetExit(rnd.nextInt(3));
                    simulationEngine.addVehicle(v);
                    resumen.append(String.format("#%d -> AUTOPISTA | %s | %s | %s%n",
                            v.getId(), tipo, lane, dir));
                } catch (IllegalArgumentException e) {
                    i--;
                }
            } else {
                VehicleType tipo = pickVehicleType(rnd);
                StartPoint inicio = pickRandom(StartPoint.values(), rnd);
                Direction direccion = pickRandom(Direction.values(), rnd);

                Vehicle v = new Vehicle(tipo, inicio, direccion);
                simulationEngine.addVehicle(v);

                resumen.append(String.format("#%d -> CALLE | %s | %s | %s%n",
                        v.getId(), tipo, inicio, direccion));
            }
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Lote de Vehículos");
        alert.setHeaderText("Creación de Lote Aleatorio");
        alert.setContentText(resumen.toString());
        alert.showAndWait();
    }

    private VehicleType pickVehicleType(ThreadLocalRandom rnd) {
        double p = rnd.nextDouble();
        return (p < 0.20) ? VehicleType.EMERGENCY : VehicleType.NORMAL;
    }

    private <T> T pickRandom(T[] values, ThreadLocalRandom rnd) {
        return values[rnd.nextInt(values.length)];
    }

    @FXML
    public void handleExit() {
        simulationEngine.stop();
        if (lightController != null) {
            lightController.stop();
        }
        System.exit(0);
    }

    private void drawRoadsWithLanes() {
        lienzo.getChildren().clear();

        double width = 1160, height = 768;
        double leftMargin = 0, topMargin = 0;
        double avenueWidth = 100, avenueLength = 1160;
        double streetWidth = 80, streetLength = 768;
        double avenueY1 = 210;
        double avenueY2 = avenueY1 + avenueWidth;

        double[] verticalXs = {70, 390, 690, 1010};
        for (int i = 0; i < verticalXs.length; i++) {
            double x = verticalXs[i];
            Rectangle calle = new Rectangle(x, topMargin, streetWidth, streetLength);
            calle.setFill(Color.GRAY);
            lienzo.getChildren().add(calle);

            if (i == 1 || i == 2) {
                double xLinea = x + streetWidth / 2;
                drawSegmentedLine(xLinea, 0, xLinea, avenueY1, Color.YELLOW, 2, 30, 25);
                drawSegmentedLine(xLinea, avenueY2 + avenueWidth, xLinea, height, Color.YELLOW, 2, 30, 25);
            }
        }

        Rectangle avenidaSup = new Rectangle(leftMargin, avenueY1, avenueLength, avenueWidth);
        avenidaSup.setFill(Color.DIMGRAY);
        lienzo.getChildren().add(avenidaSup);
        Rectangle avenidaInf = new Rectangle(leftMargin, avenueY2, avenueLength, avenueWidth);
        avenidaInf.setFill(Color.DIMGRAY);
        lienzo.getChildren().add(avenidaInf);

        drawDivisionLineWithIntersections(
                leftMargin, avenueY1 + avenueWidth, avenueLength,
                verticalXs, streetWidth, Color.DARKSLATEGRAY, 2
        );

        for (int i = 1; i < 3; i++) {
            double yLineaSup = avenueY1 + i * (avenueWidth / 3.0);
            drawSegmentedLineWithIntersections(leftMargin, yLineaSup, avenueLength, verticalXs, streetWidth, Color.WHITE, 2, 30, 25);

            double yLineaInf = avenueY2 + i * (avenueWidth / 3.0);
            drawSegmentedLineWithIntersections(leftMargin, yLineaInf, avenueLength, verticalXs, streetWidth, Color.WHITE, 2, 30, 25);
        }

        TrafficLight semaforoA1 = new TrafficLight(385, 310, 180, "CI1");
        TrafficLight semaforoA2 = new TrafficLight(690, 310, 180, "CI2");

        CalleIzqGroup.addTrafficLight(semaforoA1);
        CalleIzqGroup.addTrafficLight(semaforoA2);

        TrafficLight semaforoA3 = new TrafficLight(465, 310, 0, "CD1");
        TrafficLight semaforoA4 = new TrafficLight(770, 310, 0, "CD2");
        CalleDerGroup.addTrafficLight(semaforoA3);
        CalleDerGroup.addTrafficLight(semaforoA4);

        TrafficLight semaforoUp1 = new TrafficLight(360, 210, 270, "UP1");
        TrafficLight semaforoUp2 = new TrafficLight(660, 210, 270, "UP2");
        arribaGroup.addTrafficLight(semaforoUp1);
        arribaGroup.addTrafficLight(semaforoUp2);

        TrafficLight semaforoDown1 = new TrafficLight(500, 410, 90, "DOWN1");
        TrafficLight semaforoDown2 = new TrafficLight(800, 410, 90, "DOWN2");
        abajoGroup.addTrafficLight(semaforoDown1);
        abajoGroup.addTrafficLight(semaforoDown2);

        lienzo.getChildren().addAll(
                semaforoA1.getNode(), semaforoA2.getNode(), semaforoA3.getNode(), semaforoA4.getNode(),
                semaforoUp1.getNode(), semaforoUp2.getNode(),
                semaforoDown1.getNode(), semaforoDown2.getNode()
        );

        Label avenida1 = new Label("AVENIDA SUPERIOR (←)");
        avenida1.setTextFill(Color.WHITE);
        avenida1.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        avenida1.setLayoutX(width / 2 - 90);
        avenida1.setLayoutY(avenueY1 - 27);

        Label avenida2 = new Label("AVENIDA INFERIOR (→)");
        avenida2.setTextFill(Color.WHITE);
        avenida2.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        avenida2.setLayoutX(width / 2 - 90);
        avenida2.setLayoutY(avenueY2 + avenueWidth + 7);

        lienzo.getChildren().addAll(avenida1, avenida2);
    }

    private void drawSegmentedLineWithIntersections(
            double startX, double y, double totalLength,
            double[] verticalXs, double streetWidth,
            Color color, double width, double segmentLength, double gapLength
    ) {
        double x = startX;
        double endX = startX + totalLength;

        double[][] exclusionRanges = new double[2][2];
        for (int i = 1; i <= 2; i++) {
            exclusionRanges[i-1][0] = verticalXs[i];
            exclusionRanges[i-1][1] = verticalXs[i] + streetWidth;
        }
        double[] ranges = new double[]{
                x, exclusionRanges[0][0],
                exclusionRanges[0][1], exclusionRanges[1][0],
                exclusionRanges[1][1], endX
        };
        for (int i = 0; i < ranges.length; i += 2) {
            double segStart = ranges[i];
            double segEnd = ranges[i + 1];
            double current = segStart;
            while (current + segmentLength <= segEnd) {
                Line line = new Line(current, y, current + segmentLength, y);
                line.setStroke(color);
                line.setStrokeWidth(width);
                lienzo.getChildren().add(line);
                current += segmentLength + gapLength;
            }
        }
    }

    private void drawDivisionLineWithIntersections(
            double startX, double y, double totalLength,
            double[] verticalXs, double streetWidth,
            Color color, double width
    ) {
        double x = startX;
        double endX = startX + totalLength;

        double leftInter1 = verticalXs[1];
        double rightInter1 = verticalXs[1] + streetWidth;
        double leftInter2 = verticalXs[2];
        double rightInter2 = verticalXs[2] + streetWidth;

        double[] ranges = new double[]{
                x, leftInter1,
                rightInter1, leftInter2,
                rightInter2, endX
        };
        for (int i = 0; i < ranges.length; i += 2) {
            double segStart = ranges[i];
            double segEnd = ranges[i+1];
            if (segEnd > segStart) {
                Line line = new Line(segStart, y, segEnd, y);
                line.setStroke(color);
                line.setStrokeWidth(width);
                lienzo.getChildren().add(line);
            }
        }
    }

    private void drawSegmentedLine(double x1, double y1, double x2, double y2, Color color, double width, double segmentLength, double gapLength) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double length = Math.hypot(dx, dy);
        double segments = Math.floor(length / (segmentLength + gapLength));
        double angle = Math.atan2(dy, dx);

        double currentX = x1;
        double currentY = y1;

        for (int i = 0; i < segments; i++) {
            double nextX = currentX + segmentLength * Math.cos(angle);
            double nextY = currentY + segmentLength * Math.sin(angle);
            Line line = new Line(currentX, currentY, nextX, nextY);
            line.setStroke(color);
            line.setStrokeWidth(width);
            lienzo.getChildren().add(line);

            currentX = nextX + gapLength * Math.cos(angle);
            currentY = nextY + gapLength * Math.sin(angle);
        }
    }
}