package edu.pucmm.trafico.view;

import edu.pucmm.trafico.core.*;
import edu.pucmm.trafico.model.*;
import edu.pucmm.trafico.concurrent.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;

public class TrafficSimulationController {

    @FXML
    private Pane lienzo;

    private SimulationEngine simulationEngine;
    // Usar un carril por defecto válido del norte
    private StartPoint selectedStartPoint = StartPoint.NORTH_L;
    private VehicleType selectedVehicleType = VehicleType.NORMAL;
    private Direction selectedDirection = Direction.STRAIGHT;
    // Actualizado: ahora hay 4 intersecciones (0=x70, 1=x390, 2=x690, 3=x1010)
    private int selectedIntersection = 1; // Por defecto la primera intersección central (X=390)
    private Label configLabel;

    private final TrafficLightGroup CalleIzqGroup = new TrafficLightGroup("CalleIzq");
    private final TrafficLightGroup CalleDerGroup = new TrafficLightGroup("CalleDer");
    private final TrafficLightGroup arribaGroup = new TrafficLightGroup("ARRIBA");
    private final TrafficLightGroup abajoGroup = new TrafficLightGroup("ABAJO");
    private TrafficLightController lightController;


    private Map<String, TrafficLightGroup> trafficLightGroups;

    // Actualizado: Array con nombres descriptivos de las 4 intersecciones
    // Corresponde a VALID_INTERSECTIONS = {70, 390, 690, 1010} en VehicleTask
    private final String[] interseccionesDescripcion = {
            "lateral izquierda (X=70)",
            "primera central (X=390)",
            "segunda central (X=690)",
            "lateral derecha (X=1010)"
    };

    @FXML
    public void initialize() {
        Platform.runLater(() -> {
            // Asegurar que el MenuBar siempre esté al frente
            MenuBar menuBar = (MenuBar) lienzo.getScene().getRoot().lookup(".menu-bar");
            if (menuBar != null) {
                menuBar.toFront();
                menuBar.setMouseTransparent(false);
            }
        });

        drawRoadsWithLanes();
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
        configLabel.setLayoutY(25);

        lienzo.getChildren().add(configLabel);
    }

    private String getConfigText() {
        String config = "Configuración: " + selectedStartPoint + " | " + selectedVehicleType + " | " + selectedDirection;

        // Añadir información de la intersección seleccionada si es relevante
        if ((selectedStartPoint == StartPoint.EAST || selectedStartPoint == StartPoint.WEST) &&
                selectedDirection != Direction.STRAIGHT) {
            config += " | Intersección: " + interseccionesDescripcion[selectedIntersection];
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
            // Actualizado: Selección de intersección (4 opciones)
            case "InterseccionLateralIzq" -> selectedIntersection = 0;  // X=70
            case "PrimeraInterseccion" -> selectedIntersection = 1;     // X=390
            case "SegundaInterseccion" -> selectedIntersection = 2;     // X=690
            case "InterseccionLateralDer" -> selectedIntersection = 3;  // X=1010
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
                            "Es vehículo de autopista: Sí%nGirará en la intersección %s",
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

        // Mostrar diálogo indicando que el proceso está iniciando
        Alert startAlert = new Alert(Alert.AlertType.INFORMATION);
        startAlert.setTitle("Creación de vehículos");
        startAlert.setHeaderText("Creando " + cantidad + " vehículos");
        startAlert.setContentText("Se crearán los vehículos con espaciado adecuado para evitar congestión.");

        // Crear en un hilo separado para no bloquear la UI
        new Thread(() -> {
            try {
                // Contador de vehículos por carril para control de límites
                Map<HighwayLane, Integer> highwayLaneCounts = new HashMap<>();
                Map<StartPoint, Integer> streetLaneCounts = new HashMap<>();

                // Inicializar contadores
                for (HighwayLane lane : HighwayLane.values()) {
                    highwayLaneCounts.put(lane, 0);
                }
                for (StartPoint sp : new StartPoint[]{StartPoint.NORTH_L, StartPoint.NORTH_D,
                        StartPoint.SOUTH_L, StartPoint.SOUTH_D}) {
                    streetLaneCounts.put(sp, 0);
                }

                // Obtener vehículos activos para contar los existentes
                Collection<Vehicle> existingVehicles = simulationEngine.getActiveVehicles();
                for (Vehicle v : existingVehicles) {
                    if (v.isHighwayVehicle()) {
                        HighwayLane lane = v.getHighwayLane();
                        highwayLaneCounts.put(lane, highwayLaneCounts.getOrDefault(lane, 0) + 1);
                    } else if (v.getStartPoint() != null) {
                        StartPoint sp = v.getStartPoint();
                        streetLaneCounts.put(sp, streetLaneCounts.getOrDefault(sp, 0) + 1);
                    }
                }

                StringBuilder resumen = new StringBuilder();
                resumen.append("Vehículos creados:\n\n");

                int autopista = 0;
                int calle = 0;
                int creados = 0;
                int intentos = 0;
                int maxIntentos = 50; // Límite para evitar bucles infinitos

                // Crear vehículos con espaciado adecuado
                while (creados < cantidad && intentos < maxIntentos) {
                    intentos++;

                    try {
                        Vehicle v = null;
                        boolean vehiculoViable = false;

                        // Intentar autopista (70%) o calle (30%)
                        if (rnd.nextDouble() < 0.7) {
                            // AUTOPISTA
                            boolean goingWest = rnd.nextBoolean();

                            // Para evitar congestión, favorecer dirección STRAIGHT
                            Direction dir;
                            double dirProb = rnd.nextDouble();
                            if (dirProb < 0.75) {
                                dir = Direction.STRAIGHT; // 75% probabilidad
                            } else if (dirProb < 0.9) {
                                dir = Direction.RIGHT;    // 15% probabilidad
                            } else if (dirProb < 0.98) {
                                dir = Direction.LEFT;     // 8% probabilidad
                            } else {
                                dir = Direction.U_TURN;   // 2% probabilidad
                            }

                            VehicleType tipo = pickVehicleType(rnd);

                            // NUEVO: Filtrar carriles según la dirección del giro
                            List<HighwayLane> availableLanes = new ArrayList<>();

                            // Para cada dirección de la autopista (oeste/este), hay 3 carriles: LEFT, CENTER, RIGHT
                            if (dir == Direction.RIGHT) {
                                // Si va a doblar a la derecha, solo usar carril derecho
                                HighwayLane rightLane = goingWest ? HighwayLane.WEST_RIGHT : HighwayLane.EAST_RIGHT;
                                if (highwayLaneCounts.get(rightLane) < 4) {
                                    availableLanes.add(rightLane);
                                }
                            }
                            else if (dir == Direction.LEFT || dir == Direction.U_TURN) {
                                // Si va a doblar a la izquierda o en U, solo usar carril izquierdo
                                HighwayLane leftLane = goingWest ? HighwayLane.WEST_LEFT : HighwayLane.EAST_LEFT;
                                if (highwayLaneCounts.get(leftLane) < 4) {
                                    availableLanes.add(leftLane);
                                }
                            }
                            else {
                                // STRAIGHT: puede usar cualquier carril
                                for (HighwayLane lane : HighwayLane.values()) {
                                    if (lane.isWestbound() == goingWest && highwayLaneCounts.get(lane) < 4) {
                                        availableLanes.add(lane);
                                    }
                                }
                            }

                            if (availableLanes.isEmpty()) {
                                continue; // No hay carriles disponibles, intentar otro tipo
                            }

                            // Seleccionar carril aleatorio disponible
                            HighwayLane lane = availableLanes.get(rnd.nextInt(availableLanes.size()));

                            v = new Vehicle(tipo, lane, dir);

                            // Si no es recto, asignar una intersección para girar
                            if (dir != Direction.STRAIGHT) {
                                v.setTargetIntersection(rnd.nextInt(4));
                                resumen.append(String.format("#%d -> AUTOPISTA | %s | Carril %s | Dir: %s | Girará en %s%n",
                                        v.getId(), tipo.getDescription(), lane.getDescription(),
                                        dir.getDescription(), interseccionesDescripcion[v.getTargetIntersection()]));
                            } else {
                                v.setTargetExit(rnd.nextInt(4));
                                resumen.append(String.format("#%d -> AUTOPISTA | %s | Carril %s | Dir: %s | Salida: %d%n",
                                        v.getId(), tipo.getDescription(), lane.getDescription(),
                                        dir.getDescription(), v.getTargetExit()));
                            }

                            // Actualizar contador de carril
                            highwayLaneCounts.put(lane, highwayLaneCounts.get(lane) + 1);
                            vehiculoViable = true;
                            autopista++;

                        } else {
                            // CALLE
                            VehicleType tipo = pickVehicleType(rnd);

                            // Seleccionar un punto de inicio que no esté lleno
                            List<StartPoint> availableStartPoints = new ArrayList<>();
                            for (StartPoint sp : new StartPoint[]{StartPoint.NORTH_L, StartPoint.NORTH_D,
                                    StartPoint.SOUTH_L, StartPoint.SOUTH_D}) {
                                if (streetLaneCounts.get(sp) < 3) {
                                    availableStartPoints.add(sp);
                                }
                            }

                            if (availableStartPoints.isEmpty()) {
                                continue; // No hay calles disponibles, intentar otro tipo
                            }

                            // Para evitar congestión, favorecer dirección STRAIGHT en calles también
                            Direction direccion;
                            double dirProb = rnd.nextDouble();
                            if (dirProb < 0.8) {
                                direccion = Direction.STRAIGHT; // 80% probabilidad
                            } else if (dirProb < 0.95) {
                                direccion = Direction.RIGHT;    // 15% probabilidad
                            } else {
                                direccion = Direction.LEFT;     // 5% probabilidad
                            }

                            StartPoint inicio = availableStartPoints.get(rnd.nextInt(availableStartPoints.size()));
                            v = new Vehicle(tipo, inicio, direccion);

                            // Actualizar contador de carril
                            streetLaneCounts.put(inicio, streetLaneCounts.get(inicio) + 1);
                            vehiculoViable = true;
                            calle++;

                            resumen.append(String.format("#%d -> CALLE | %s | Desde %s | Dir: %s%n",
                                    v.getId(), tipo.getDescription(), inicio.getDescription(),
                                    direccion.getDescription()));
                        }

                        if (vehiculoViable) {
                            // CREAR EL VEHÍCULO EN LA SIMULACIÓN
                            final Vehicle finalV = v;
                            Platform.runLater(() -> simulationEngine.addVehicle(finalV));
                            creados++;

                            // ESPERAR ANTES DE CREAR EL SIGUIENTE VEHÍCULO
                            // Espaciado entre 0.5 y 1.5 segundos para mejor fluidez
                            double variableDelay = 0.5 + rnd.nextDouble();
                            Thread.sleep((long)(variableDelay * 1000));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                resumen.append(String.format("\nTotal: %d de autopista, %d de calle", autopista, calle));

                // Mostrar resumen en la UI
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Lote de Vehículos");
                    alert.setHeaderText("Creación de Lote Completada");
                    alert.setContentText(resumen.toString());
                    alert.showAndWait();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        startAlert.showAndWait();
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
        // Actualizado: Todas las intersecciones ahora permiten giros
        int[] mainIntersections = {0, 1, 2, 3}; // Todos los índices corresponden a intersecciones válidas

        for (int i = 0; i < verticalXs.length; i++) {
            double x = verticalXs[i];
            Rectangle calle = new Rectangle(x, topMargin, streetWidth, streetLength);
            calle.setFill(Color.GRAY);
            lienzo.getChildren().add(calle);

            // Línea central amarilla para todas las calles (ahora todas tienen semáforos)
            double xLinea = x + streetWidth / 2;
            drawSegmentedLine(xLinea, 0, xLinea, avenueY1, Color.YELLOW, 2, 30, 25);
            drawSegmentedLine(xLinea, avenueY2 + avenueWidth, xLinea, height, Color.YELLOW, 2, 30, 25);
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

        TrafficLight semaforoUp0 = new TrafficLight(45, 210, 270, "UP0");
        TrafficLight semaforoUp1 = new TrafficLight(360, 210, 270, "UP1");
        TrafficLight semaforoUp2 = new TrafficLight(660, 210, 270, "UP2");
        arribaGroup.addTrafficLight(semaforoUp1);
        arribaGroup.addTrafficLight(semaforoUp2);
        arribaGroup.addTrafficLight(semaforoUp0);

        TrafficLight semaforoDown1 = new TrafficLight(500, 410, 90, "DOWN1");
        TrafficLight semaforoDown2 = new TrafficLight(800, 410, 90, "DOWN2");
        TrafficLight semaforoDown0 = new TrafficLight(1115, 410, 90, "DOWN0");
        abajoGroup.addTrafficLight(semaforoDown1);
        abajoGroup.addTrafficLight(semaforoDown2);
        abajoGroup.addTrafficLight(semaforoDown0);

        lienzo.getChildren().addAll(
                semaforoA1.getNode(), semaforoA2.getNode(), semaforoA3.getNode(), semaforoA4.getNode(),
                semaforoUp1.getNode(), semaforoUp2.getNode(),
                semaforoDown1.getNode(), semaforoDown2.getNode(),semaforoDown0.getNode(),semaforoUp0.getNode()
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

        // Actualizado: Agregar etiquetas para las 4 intersecciones donde se puede girar
        Label inter0 = new Label("0");
        inter0.setTextFill(Color.WHITE);
        inter0.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        inter0.setStyle("-fx-background-color: rgba(255, 0, 0, 0.7); -fx-padding: 4; -fx-background-radius: 10;");
        inter0.setLayoutX(110);
        inter0.setLayoutY(180);

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

        Label inter3 = new Label("3");
        inter3.setTextFill(Color.WHITE);
        inter3.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        inter3.setStyle("-fx-background-color: rgba(255, 0, 0, 0.7); -fx-padding: 4; -fx-background-radius: 10;");
        inter3.setLayoutX(1050);
        inter3.setLayoutY(180);

        lienzo.getChildren().addAll(inter0, inter1, inter2, inter3);
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

        // Actualizado: Ahora excluir las 4 intersecciones
        double[][] exclusionRanges = new double[4][2];
        for (int i = 0; i < 4; i++) {
            exclusionRanges[i][0] = verticalXs[i];
            exclusionRanges[i][1] = verticalXs[i] + streetWidth;
        }

        double[] ranges = new double[]{
                x, exclusionRanges[0][0],
                exclusionRanges[0][1], exclusionRanges[1][0],
                exclusionRanges[1][1], exclusionRanges[2][0],
                exclusionRanges[2][1], exclusionRanges[3][0],
                exclusionRanges[3][1], endX
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

        // Actualizado: Excluir las 4 intersecciones
        double leftInter0 = verticalXs[0];
        double rightInter0 = verticalXs[0] + streetWidth;
        double leftInter1 = verticalXs[1];
        double rightInter1 = verticalXs[1] + streetWidth;
        double leftInter2 = verticalXs[2];
        double rightInter2 = verticalXs[2] + streetWidth;
        double leftInter3 = verticalXs[3];
        double rightInter3 = verticalXs[3] + streetWidth;

        double[] ranges = new double[]{
                x, leftInter0,
                rightInter0, leftInter1,
                rightInter1, leftInter2,
                rightInter2, leftInter3,
                rightInter3, endX
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




