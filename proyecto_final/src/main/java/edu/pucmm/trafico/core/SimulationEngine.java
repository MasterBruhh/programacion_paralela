package edu.pucmm.trafico.core;

import edu.pucmm.trafico.model.*;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class SimulationEngine {
    private static final Logger logger = Logger.getLogger(SimulationEngine.class.getName());
    
    private final VehicleLifecycleManager lifecycleManager;
    private final Pane canvas;
    private final AtomicBoolean running;
    private AnimationTimer animationTimer;
    
    public SimulationEngine(Pane canvas, Map<String, TrafficLightGroup> trafficLightGroups) {
        this.lifecycleManager = new VehicleLifecycleManager(trafficLightGroups);
        this.canvas = canvas;
        this.running = new AtomicBoolean(false);
    }
    
    public void start() {
        if (running.compareAndSet(false, true)) {
            animationTimer = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    updateVisualization();
                }
            };
            animationTimer.start();
            
            logger.info("Simulación iniciada");
        }
    }
    
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (animationTimer != null) {
                animationTimer.stop();
            }
            
            lifecycleManager.shutdown();
            logger.info("Simulación detenida");
        }
    }
    
    public void addVehicle(Vehicle vehicle) {
        Circle visualNode = createVehicleNode(vehicle);
        
        lifecycleManager.addVehicle(vehicle, visualNode);
        
        Platform.runLater(() -> {
            canvas.getChildren().add(visualNode);
            
            visualNode.setOpacity(0);
            javafx.animation.FadeTransition fadeIn = 
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(500), visualNode);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
    }
    
    private Circle createVehicleNode(Vehicle vehicle) {
        Circle circle = new Circle(12);
        circle.setFill(vehicle.getColor());
        circle.setCenterX(vehicle.getX());
        circle.setCenterY(vehicle.getY());
        
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            circle.setStroke(Color.YELLOW);
            circle.setStrokeWidth(2);
            
            javafx.scene.effect.Glow glow = new javafx.scene.effect.Glow();
            glow.setLevel(0.7);
            circle.setEffect(glow);
        } else if (vehicle.isHighwayVehicle()) {
            circle.setStroke(Color.WHITE);
            circle.setStrokeWidth(0.5);
        }
        
        return circle;
    }
    
    private void updateVisualization() {
        // La actualización visual se maneja en VehicleTask
    }
    
    public boolean isRunning() {
        return running.get();
    }
}