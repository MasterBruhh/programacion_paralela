package edu.pucmm.trafico.view;

import edu.pucmm.trafico.core.SimulationEngine;
import edu.pucmm.trafico.model.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.concurrent.ThreadLocalRandom;

public class TrafficSimulationController {

    @FXML
    private Pane lienzo;

    private SimulationEngine simulationEngine;
    private StartPoint selectedStartPoint = StartPoint.NORTH;
    private VehicleType selectedVehicleType = VehicleType.NORMAL;
    private Direction selectedDirection = Direction.STRAIGHT;
    private Label configLabel;

    @FXML
    public void initialize() {
        drawRoads();
        simulationEngine = new SimulationEngine(lienzo);
        createConfigDisplay();

        // Iniciar la simulación automáticamente
        simulationEngine.start();
    }

    private void createConfigDisplay() {
        configLabel = new Label(getConfigText());
        configLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        configLabel.setTextFill(Color.WHITE);
        configLabel.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); " +
                "-fx-padding: 10; -fx-background-radius: 5;");
        configLabel.setLayoutX(10);
        configLabel.setLayoutY(10);

        lienzo.getChildren().add(configLabel);
    }

    private String getConfigText() {
        return "Configuración: " +
                selectedStartPoint + " | " +
                selectedVehicleType + " | " +
                selectedDirection;
    }

    @FXML
    public void handleConfiguracion(ActionEvent event) {
        String option = ((MenuItem) event.getSource()).getId();

        switch (option) {
            case "Arriba":
                selectedStartPoint = StartPoint.NORTH;
                break;
            case "Abajo":
                selectedStartPoint = StartPoint.SOUTH;
                break;
            case "Izquierda":
                selectedStartPoint = StartPoint.WEST;
                break;
            case "Derecha":
                selectedStartPoint = StartPoint.EAST;
                break;
            case "Normal":
                selectedVehicleType = VehicleType.NORMAL;
                break;
            case "Emergencia":
                selectedVehicleType = VehicleType.EMERGENCY;
                break;
            case "Recto":
                selectedDirection = Direction.STRAIGHT;
                break;
            case "VIzquierda":
                selectedDirection = Direction.LEFT;
                break;
            case "VDerecha":
                selectedDirection = Direction.RIGHT;
                break;
            case "U":
                selectedDirection = Direction.U_TURN;
                break;
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

    // Crear lote de 15 vehículos aleatorios con 20% EMERGENCY y 80% NORMAL
    @FXML
    public void crearLoteVehiculos() {
        final int cantidad = 15;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        StringBuilder resumen = new StringBuilder();
        resumen.append("Se crearon ").append(cantidad).append(" vehículos:\n\n");

        for (int i = 0; i < cantidad; i++) {
            VehicleType tipo = pickVehicleType(rnd); // ponderado: 20% emergencia, 80% normal
            StartPoint inicio = pickRandom(StartPoint.values(), rnd);
            Direction direccion = pickRandom(Direction.values(), rnd);

            Vehicle v = new Vehicle(VehicleType.NORMAL, inicio, direccion);
            simulationEngine.addVehicle(v);

            resumen.append(String.format("#%d -> Tipo: %s | Inicio: %s | Dirección: %s%n",
                    v.getId(), tipo, inicio, direccion));
        }

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Lote de Vehículos");
        alert.setHeaderText("Creación de Lote Aleatorio");
        alert.setContentText(resumen.toString());
        alert.showAndWait();
    }

    // 20% EMERGENCY, 80% NORMAL
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
        System.exit(0);
    }

    private void drawRoads() {
        Rectangle horizontalRoad = new Rectangle(0, 250, 800, 90);
        horizontalRoad.setFill(Color.GRAY);

        Rectangle verticalRoad = new Rectangle(355, 0, 90, 600);
        verticalRoad.setFill(Color.GRAY);

        lienzo.getChildren().addAll(horizontalRoad, verticalRoad);

        drawYellowLines();

        addDirectionMarkers();
    }

    private void drawYellowLines() {
        double lineLength = 30.0;
        double lineWidth = 5.0;
        double spacing = 20.0;

        double horizontalY = 292.0;
        for (double x = 10; x < 800; x += (lineLength + spacing)) {
            if (x > 310 && x < 460) {
                continue;
            }
            Rectangle line = new Rectangle(x, horizontalY, lineLength, lineWidth);
            line.setFill(Color.YELLOW);
            lienzo.getChildren().add(line);
        }

        double verticalX = 395.0;
        for (double y = 10; y < 600; y += (lineLength + spacing)) {
            if (y > 210 && y < 360) {
                continue;
            }
            Rectangle line = new Rectangle(verticalX, y, lineWidth, lineLength);
            line.setFill(Color.YELLOW);
            lienzo.getChildren().add(line);
        }
    }

    private void addDirectionMarkers() {

        // Norte
        Label northLabel = new Label("NORTE");
        northLabel.setTextFill(Color.WHITE);
        northLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        northLabel.setLayoutX(380);
        northLabel.setLayoutY(30);

        // Sur
        Label southLabel = new Label("SUR");
        southLabel.setTextFill(Color.WHITE);
        southLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        southLabel.setLayoutX(385);
        southLabel.setLayoutY(560);

        // Este
        Label eastLabel = new Label("ESTE");
        eastLabel.setTextFill(Color.WHITE);
        eastLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        eastLabel.setLayoutX(740);
        eastLabel.setLayoutY(285);

        // Oeste
        Label westLabel = new Label("OESTE");
        westLabel.setTextFill(Color.WHITE);
        westLabel.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        westLabel.setLayoutX(20);
        westLabel.setLayoutY(285);

        lienzo.getChildren().addAll(northLabel, southLabel, eastLabel, westLabel);
    }
}