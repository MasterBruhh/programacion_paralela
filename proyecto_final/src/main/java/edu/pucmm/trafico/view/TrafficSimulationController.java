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
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;

public class TrafficSimulationController {

    @FXML
    private Pane lienzo;

    private SimulationEngine simulationEngine;
    // Usar un carril por defecto válido del norte
    private StartPoint selectedStartPoint = StartPoint.NORTH_L;
    private VehicleType selectedVehicleType = VehicleType.NORMAL;
    private Direction selectedDirection = Direction.STRAIGHT;
    // Añadida variable para la intersección seleccionada
    private int selectedIntersection = 0; // 0 = primera (X=390), 1 = segunda (X=690)
    private Label configLabel;

    private final TrafficLightGroup CalleIzqGroup = new TrafficLightGroup("CalleIzq");
    private final TrafficLightGroup CalleDerGroup = new TrafficLightGroup("CalleDer");
    private final TrafficLightGroup arribaGroup = new TrafficLightGroup("ARRIBA");
    private final TrafficLightGroup abajoGroup = new TrafficLightGroup("ABAJO");
    private TrafficLightController lightController;

    private Map<String, TrafficLightGroup> trafficLightGroups;

    // Array con nombres descriptivos de las intersecciones
    private final String[] interseccionesDescripcion = {"primera (X=390)", "segunda (X=690)"};

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
        String config = "Configuración: " + selectedStartPoint + " | " + selectedVehicleType + " | " + selectedDirection;

        // Añadir información de la intersección seleccionada si es relevante
        if ((selectedStartPoint == StartPoint.EAST || selectedStartPoint == StartPoint.WEST) &&
                selectedDirection != Direction.STRAIGHT) {
            config += " | Intersección: " + (selectedIntersection == 0 ? "Primera" : "Segunda");
        }

        return config;
    }

    @FXML
    public void handleConfiguracion(ActionEvent event) {
        String option = ((MenuItem) event.getSource()).getId();

        switch (option) {
            // Calles verticales: mapear explícitamente a L/D
            case "Arriba Izquierda" -> selectedStartPoint = StartPoint.NORTH_L;
            case "Arriba Derecha"  -> selectedStartPoint = StartPoint.NORTH_D;
            case "Abajo Izquierda" -> selectedStartPoint = StartPoint.SOUTH_L;
            case "Abajo Derecha"   -> selectedStartPoint = StartPoint.SOUTH_D;
            // Entradas de autopista
            case "Izquierda"       -> selectedStartPoint = StartPoint.WEST;
            case "Derecha"         -> selectedStartPoint = StartPoint.EAST;
            // Tipo de vehículo
            case "Normal"          -> selectedVehicleType = VehicleType.NORMAL;
            case "Emergencia"      -> selectedVehicleType = VehicleType.EMERGENCY;
            case "Transporte_Publico" -> selectedVehicleType = VehicleType.PUBLIC_TRANSPORT;
            case "Pesado"          -> selectedVehicleType = VehicleType.HEAVY;
            // Dirección
            case "Recto"           -> selectedDirection = Direction.STRAIGHT;
            case "VIzquierda"      -> selectedDirection = Direction.LEFT;
            case "VDerecha"        -> selectedDirection = Direction.RIGHT;
            case "U"               -> selectedDirection = Direction.U_TURN;
            // Selección de intersección (nuevas opciones)
            case "PrimeraInterseccion" -> selectedIntersection = 0;
            case "SegundaInterseccion" -> selectedIntersection = 1;
            default -> { /* ignorar */ }
        }

        configLabel.setText(getConfigText());
    }

    @FXML
    public void crearVehiculo() {
        Vehicle vehicle;

        // Autopista si es Este u Oeste
        if (selectedStartPoint == StartPoint.EAST || selectedStartPoint == StartPoint.WEST) {
            boolean goingWest = (selectedStartPoint == StartPoint.WEST);
            HighwayLane lane = HighwayLane.getRandomLane(goingWest, selectedDirection);

            vehicle = new Vehicle(selectedVehicleType, lane, selectedDirection);

            // Si no es STRAIGHT y la dirección implica un giro, asignar intersección objetivo
            if (selectedDirection != Direction.STRAIGHT) {
                // Usar la intersección seleccionada por el usuario
                vehicle.setTargetIntersection(selectedIntersection);
                System.out.println("Vehículo girará en la " + interseccionesDescripcion[selectedIntersection]);
            } else {
                // Para movimientos rectos, mantener la configuración original
                vehicle.setTargetExit(new Random().nextInt(4));
            }
        } else {
            // Calles verticales: NORTH_L/NORTH_D/SOUTH_L/SOUTH_D
            vehicle = new Vehicle(selectedVehicleType, selectedStartPoint, selectedDirection);
        }

        simulationEngine.addVehicle(vehicle);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Vehículo Creado");
        alert.setHeaderText("Vehículo #" + vehicle.getId() + " creado exitosamente");

        String content;
        if (vehicle.isHighwayVehicle() && selectedDirection != Direction.STRAIGHT) {
            content = String.format(
                    "Tipo: %s%nPunto de salida: %s%nDirección: %s%nPosición inicial: (%.0f, %.0f)%n" +
                            "Es vehículo de autopista: Sí%nGirará en la %s intersección",
                    selectedVehicleType,
                    selectedStartPoint,
                    selectedDirection,
                    vehicle.getX(),
                    vehicle.getY(),
                    interseccionesDescripcion[vehicle.getTargetIntersection()]
            );
        } else {
            content = String.format(
                    "Tipo: %s%nPunto de salida: %s%nDirección: %s%nPosición inicial: (%.0f, %.0f)%nEs vehículo de autopista: %s",
                    selectedVehicleType,
                    selectedStartPoint,
                    selectedDirection,
                    vehicle.getX(),
                    vehicle.getY(),
                    vehicle.isHighwayVehicle() ? "Sí" : "No"
            );
        }

        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    public void crearLoteVehiculos() {
        final int cantidad = 15;
        Random rnd = new Random();

        StringBuilder resumen = new StringBuilder();
        resumen.append("Se crearon ").append(cantidad).append(" vehículos:\n\n");

        int autopista = 0;
        int calle = 0;

        for (int i = 0; i < cantidad; i++) {
            if (rnd.nextDouble() < 0.7) {
                // Autopista
                boolean goingWest = rnd.nextBoolean();
                Direction dir = pickRandomDirection(rnd);
                VehicleType tipo = pickVehicleType(rnd);

                HighwayLane lane = HighwayLane.getRandomLane(goingWest, dir);

                Vehicle v = new Vehicle(tipo, lane, dir);

                // Si no es recto, asignar una intersección para girar
                if (dir != Direction.STRAIGHT) {
                    // Para lotes aleatorios, seguimos seleccionando intersección aleatoria
                    v.setTargetIntersection(rnd.nextInt(2));
                    resumen.append(String.format("#%d -> AUTOPISTA | %s | Carril %s | Dir: %s | Girará en intersección %d%n",
                            v.getId(), tipo.getDescription(), lane.getDescription(),
                            dir.getDescription(), v.getTargetIntersection() + 1));
                } else {
                    // Para movimientos rectos, usar la lógica de salida existente
                    v.setTargetExit(rnd.nextInt(4));
                    resumen.append(String.format("#%d -> AUTOPISTA | %s | Carril %s | Dir: %s | Salida: %d%n",
                            v.getId(), tipo.getDescription(), lane.getDescription(),
                            dir.getDescription(), v.getTargetExit()));
                }

                simulationEngine.addVehicle(v);
                autopista++;
            } else {
                // Calle: elegir entre L/D de norte o sur
                VehicleType tipo = pickVehicleType(rnd);
                boolean north = rnd.nextBoolean();
                StartPoint inicio = north
                        ? (rnd.nextBoolean() ? StartPoint.NORTH_L : StartPoint.NORTH_D)
                        : (rnd.nextBoolean() ? StartPoint.SOUTH_L : StartPoint.SOUTH_D);
                Direction direccion = pickRandomDirection(rnd);

                Vehicle v = new Vehicle(tipo, inicio, direccion);
                simulationEngine.addVehicle(v);

                calle++;
                resumen.append(String.format("#%d -> CALLE | %s | Desde %s | Dir: %s%n",
                        v.getId(), tipo.getDescription(), inicio.getDescription(),
                        direccion.getDescription()));
            }
            try {
                Thread.sleep(1000); // 1000 ms = 1 segundo
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        resumen.append(String.format("\nTotal: %d de autopista, %d de calle", autopista, calle));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Lote de Vehículos");
        alert.setHeaderText("Creación de Lote Aleatorio");
        alert.setContentText(resumen.toString());
        alert.showAndWait();
    }

    private VehicleType pickVehicleType(Random rnd) {
        double p = rnd.nextDouble();
        if (p < 0.05) return VehicleType.EMERGENCY;
        else if (p < 0.15) return VehicleType.PUBLIC_TRANSPORT;
        else if (p < 0.25) return VehicleType.HEAVY;
        else return VehicleType.NORMAL;
    }

    private Direction pickRandomDirection(Random rnd) {
        double p = rnd.nextDouble();
        if (p < 0.60) return Direction.STRAIGHT;
        else if (p < 0.80) return Direction.RIGHT;
        else if (p < 0.95) return Direction.LEFT;
        else return Direction.U_TURN;
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

        // Dibujar calles verticales
        double[] verticalXs = {70, 390, 690, 1010};
        for (int i = 0; i < verticalXs.length; i++) {
            double x = verticalXs[i];
            Rectangle calle = new Rectangle(x, topMargin, streetWidth, streetLength);
            calle.setFill(Color.GRAY);
            lienzo.getChildren().add(calle);

            // Línea central amarilla para calles con semáforos
            if (i == 1 || i == 2) {
                double xLinea = x + streetWidth / 2;
                drawSegmentedLine(xLinea, 0, xLinea, avenueY1, Color.YELLOW, 2, 30, 25);
                drawSegmentedLine(xLinea, avenueY2 + avenueWidth, xLinea, height, Color.YELLOW, 2, 30, 25);
            }
        }

        // Dibujar avenidas
        Rectangle avenidaSup = new Rectangle(leftMargin, avenueY1, avenueLength, avenueWidth);
        avenidaSup.setFill(Color.DIMGRAY);
        lienzo.getChildren().add(avenidaSup);
        Rectangle avenidaInf = new Rectangle(leftMargin, avenueY2, avenueLength, avenueWidth);
        avenidaInf.setFill(Color.DIMGRAY);
        lienzo.getChildren().add(avenidaInf);

        // Línea divisoria entre avenidas
        drawDivisionLineWithIntersections(
                leftMargin, avenueY1 + avenueWidth, avenueLength,
                verticalXs, streetWidth, Color.DARKSLATEGRAY, 2
        );

        // Líneas de carriles en las avenidas
        for (int i = 1; i < 3; i++) {
            double yLineaSup = avenueY1 + i * (avenueWidth / 3.0);
            drawSegmentedLineWithIntersections(leftMargin, yLineaSup, avenueLength, verticalXs, streetWidth, Color.WHITE, 2, 30, 25);

            double yLineaInf = avenueY2 + i * (avenueWidth / 3.0);
            drawSegmentedLineWithIntersections(leftMargin, yLineaInf, avenueLength, verticalXs, streetWidth, Color.WHITE, 2, 30, 25);
        }

        // Crear semáforos
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

        // Etiquetas de avenidas
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

        // Indicadores de carriles de autopista para debug
        drawLaneIndicators();

        // Agregar etiquetas para las intersecciones donde se puede girar
        Label inter1 = new Label("1");
        inter1.setTextFill(Color.WHITE);
        inter1.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        inter1.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-padding: 4; -fx-background-radius: 10;");
        inter1.setLayoutX(430);
        inter1.setLayoutY(180);

        Label inter2 = new Label("2");
        inter2.setTextFill(Color.WHITE);
        inter2.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        inter2.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); -fx-padding: 4; -fx-background-radius: 10;");
        inter2.setLayoutX(730);
        inter2.setLayoutY(180);

        lienzo.getChildren().addAll(inter1, inter2);
    }

    private void drawLaneIndicators() {
        for (HighwayLane lane : new HighwayLane[]{HighwayLane.WEST_LEFT, HighwayLane.WEST_CENTER, HighwayLane.WEST_RIGHT}) {
            Circle indicator = new Circle(50, lane.getStartY(), 3, Color.YELLOW);
            lienzo.getChildren().add(indicator);
        }
        for (HighwayLane lane : new HighwayLane[]{HighwayLane.EAST_LEFT, HighwayLane.EAST_CENTER, HighwayLane.EAST_RIGHT}) {
            Circle indicator = new Circle(1110, lane.getStartY(), 3, Color.YELLOW);
            lienzo.getChildren().add(indicator);
        }
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
            exclusionRanges[i - 1][0] = verticalXs[i];
            exclusionRanges[i - 1][1] = verticalXs[i] + streetWidth;
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
            double segEnd = ranges[i + 1];
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