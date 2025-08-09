package edu.pucmm.trafico.core;

import edu.pucmm.trafico.model.Vehicle;
import edu.pucmm.trafico.model.StartPoint;
import edu.pucmm.trafico.concurrent.CollisionDetector;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class SimulationEngine {
    private static final Logger logger = Logger.getLogger(SimulationEngine.class.getName());
    
    private final VehicleLifecycleManager lifecycleManager;
    private final Pane canvas;
    private final AtomicBoolean running;
    private final CollisionDetector collisionDetector;
    private AnimationTimer animationTimer;
    private ScheduledExecutorService statsExecutor;
    private VBox statsDisplay;
    private Label modeLabel;
    private Label statusLabel;
    private Label lanesLabel;
    
    // Constante para velocidad de movimiento en cola
    private static final double QUEUE_MOVEMENT_SPEED = 20.0; // píxeles por segundo
    
    public SimulationEngine(Pane canvas) {
        this.lifecycleManager = new VehicleLifecycleManager();
        this.canvas = canvas;
        this.running = new AtomicBoolean(false);
        this.collisionDetector = new CollisionDetector();
        
        // Crear panel de estadísticas
        createStatsDisplay();
    }
    
    private void createStatsDisplay() {
        statsDisplay = new VBox(5);
        statsDisplay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.9); " +
                             "-fx-background-radius: 10; " +
                             "-fx-border-color: darkblue; " +
                             "-fx-border-radius: 10; " +
                             "-fx-border-width: 2;");
        statsDisplay.setPadding(new Insets(15));
        
        // Etiqueta de modo
        modeLabel = new Label("Modo: FIFO");
        modeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        modeLabel.setTextFill(Color.DARKBLUE);
        
        // Etiqueta de estado
        statusLabel = new Label("Total esperando: 0");
        statusLabel.setFont(Font.font("Arial", 14));
        statusLabel.setTextFill(Color.DARKGREEN);
        
        // Etiqueta de carriles
        lanesLabel = new Label("Carriles: ");
        lanesLabel.setFont(Font.font("Arial", 12));
        lanesLabel.setTextFill(Color.BLACK);
        lanesLabel.setWrapText(true);
        lanesLabel.setMaxWidth(250);
        
        statsDisplay.getChildren().addAll(modeLabel, statusLabel, lanesLabel);
        
        // Posicionar en la esquina superior derecha
        statsDisplay.setLayoutX(520);
        statsDisplay.setLayoutY(10);
        
        Platform.runLater(() -> canvas.getChildren().add(statsDisplay));
    }
    
    public void start() {
        if (running.compareAndSet(false, true)) {
            animationTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    updateVisualization();
                    applyBrakingLogic();
                }
            };
            animationTimer.start();
            
            // Iniciar actualizador de estadísticas
            startStatsUpdater();
            
            logger.info("Simulación iniciada");
        }
    }
    
    private void applyBrakingLogic() {
        for (Vehicle vehicle : lifecycleManager.getAllActiveVehicles()) {
            // Detectar vehículo adelante
            CollisionDetector.VehicleAheadInfo aheadInfo = 
                collisionDetector.detectVehicleAhead(vehicle, lifecycleManager.getAllActiveVehicles());
            
            if (aheadInfo != null) {
                double distance = aheadInfo.getDistance();
                double currentSpeed = vehicle.getSpeed();
                
                // Calcular velocidad segura
                double safeSpeed = collisionDetector.calculateSafeSpeed(currentSpeed, distance);
                
                if (safeSpeed < currentSpeed) {
                    vehicle.setSpeed(safeSpeed);
                    
                    if (safeSpeed == 0) {
                        // Detener completamente el vehículo visualmente
                        Platform.runLater(() -> {
                            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
                            if (node != null) {
                                // Detener cualquier animación en curso
                                node.setTranslateX(0);
                                node.setTranslateY(0);
                            }
                        });
                    }
                }
            } else {
                // No hay vehículo adelante, restaurar velocidad normal
                vehicle.setSpeed(30.0);
            }
        }
    }
    
    private void startStatsUpdater() {
        statsExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("StatsUpdater");
            return t;
        });
        
        statsExecutor.scheduleAtFixedRate(() -> {
            IntersectionController.IntersectionStats stats = 
                lifecycleManager.getIntersectionController().getStats();
            
            Platform.runLater(() -> {
                // Actualizar modo con color
                String modeText = "Modo: " + (stats.mode == IntersectionController.CrossingMode.FIFO ? 
                                 "FIFO (Orden de llegada)" : "ROTATIVO (Por carriles)");
                modeLabel.setText(modeText);
                
                if (stats.mode == IntersectionController.CrossingMode.ROTATIVE) {
                    modeLabel.setTextFill(Color.DARKRED);
                    if (stats.currentRotationLane != null) {
                        modeText += " - Turno: " + stats.currentRotationLane;
                        modeLabel.setText(modeText);
                    }
                } else {
                    modeLabel.setTextFill(Color.DARKBLUE);
                }
                
                // Actualizar total
                statusLabel.setText("Total esperando: " + stats.totalWaiting);
                if (stats.totalWaiting == 0) {
                    statusLabel.setTextFill(Color.DARKGREEN);
                } else if (stats.totalWaiting < 4) {
                    statusLabel.setTextFill(Color.DARKORANGE);
                } else {
                    statusLabel.setTextFill(Color.DARKRED);
                }
                
                // Actualizar detalles por carril
                StringBuilder lanes = new StringBuilder("Carriles:\n");
                for (StartPoint point : StartPoint.values()) {
                    int count = stats.waitingByLane.get(point);
                    String vehicleAtStop = stats.vehiclesAtStop.get(point);
                    
                    if (count > 0 || vehicleAtStop != null) {
                        lanes.append("  ").append(point).append(": ")
                             .append(count).append(" esperando");
                        
                        if (vehicleAtStop != null) {
                            lanes.append(" (").append(vehicleAtStop).append(" en PARE)");
                        }
                        lanes.append("\n");
                    }
                }
                
                if (stats.congestedLanes >= 3) {
                    lanes.append("\n⚠️ CONGESTIÓN DETECTADA");
                }
                
                lanesLabel.setText(lanes.toString());
            });
        }, 0, 1, TimeUnit.SECONDS);
    }
    
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (animationTimer != null) {
                animationTimer.stop();
            }
            if (statsExecutor != null) {
                statsExecutor.shutdown();
            }
            lifecycleManager.shutdown();
            
            logger.info("Simulación detenida");
        }
    }
    
    public void addVehicle(Vehicle vehicle) {
        Circle visualNode = createVehicleNode(vehicle);
        
        // El VehicleLifecycleManager ahora calculará la posición inicial correcta
        // antes de agregar el nodo visual a la escena
        lifecycleManager.addVehicle(vehicle, visualNode);
        
        // Agregar el nodo visual a la escena después de que se haya calculado
        // la posición inicial correcta
        Platform.runLater(() -> {
            canvas.getChildren().add(visualNode);
            
            // Aplicar animación de entrada suave
            visualNode.setOpacity(0);
            javafx.animation.FadeTransition fadeIn = 
                new javafx.animation.FadeTransition(Duration.millis(300), visualNode);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        
        logger.info("Vehículo agregado: " + vehicle);
    }
    
    private Circle createVehicleNode(Vehicle vehicle) {
        Circle circle = new Circle(10);
        circle.setFill(vehicle.getType() == edu.pucmm.trafico.model.VehicleType.EMERGENCY ? 
                       Color.RED : Color.BLUE);
        circle.setCenterX(vehicle.getX());
        circle.setCenterY(vehicle.getY());
        
        // Agregar efecto visual para vehículos de emergencia
        if (vehicle.getType() == edu.pucmm.trafico.model.VehicleType.EMERGENCY) {
            circle.setStroke(Color.YELLOW);
            circle.setStrokeWidth(2);
        }
        
        // Agregar tooltip con información del vehículo
        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(
            "ID: " + vehicle.getId() + "\n" +
            "Tipo: " + vehicle.getType() + "\n" +
            "Desde: " + vehicle.getStartPoint() + "\n" +
            "Dirección: " + vehicle.getDirection()
        );
        javafx.scene.control.Tooltip.install(circle, tooltip);
        
        return circle;
    }
    
    private void updateVisualization() {
        for (Vehicle vehicle : lifecycleManager.getRegistry().getAllVehicles()) {
            lifecycleManager.updateVehiclePosition(vehicle);
        }
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public VehicleLifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }
}