package edu.pucmm.trafico.core;

import edu.pucmm.trafico.model.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.effect.DropShadow;
import javafx.util.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Motor de simulación simplificado enfocado en la creación y posicionamiento inicial.
 */
public class SimulationEngine {
    private static final Logger logger = Logger.getLogger(SimulationEngine.class.getName());
    
    private final VehicleLifecycleManager lifecycleManager;
    private final Pane canvas;
    private final AtomicBoolean running;
    
    public SimulationEngine(Pane canvas, Map<String, TrafficLightGroup> trafficLightGroups) {
        this.lifecycleManager = new VehicleLifecycleManager(trafficLightGroups);
        this.canvas = canvas;
        this.running = new AtomicBoolean(false);
    }
    
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Simulación iniciada - Fase de creación y movimiento inicial");
        }
    }
    
    public void stop() {
        if (running.compareAndSet(true, false)) {
            lifecycleManager.shutdown();
            logger.info("Simulación detenida");
        }
    }
    
    public void addVehicle(Vehicle vehicle) {
        // Crear nodo visual con posicionamiento preciso
        Circle visualNode = createVehicleNode(vehicle);
        
        // Log detallado de creación
        logger.info(String.format("Creando vehículo #%d - Tipo: %s, Posición: (%.1f, %.1f), %s",
                vehicle.getId(),
                vehicle.getType(),
                vehicle.getX(),
                vehicle.getY(),
                vehicle.isHighwayVehicle() ? "Autopista - " + vehicle.getHighwayLane() : "Calle - " + vehicle.getStartPoint()
        ));
        
        // Registrar en el lifecycle manager
        lifecycleManager.addVehicle(vehicle, visualNode);
        
        // Agregar al canvas con animación de entrada
        Platform.runLater(() -> {
            // Posicionar exactamente según las coordenadas del vehículo
            visualNode.setCenterX(vehicle.getX());
            visualNode.setCenterY(vehicle.getY());
            
            canvas.getChildren().add(visualNode);
            
            // Animación de entrada mejorada
            createEntryAnimation(visualNode, vehicle);
        });
    }
    
    private Circle createVehicleNode(Vehicle vehicle) {
        Circle circle = new Circle(vehicle.getSize());
        
        // Color según tipo de vehículo
        circle.setFill(vehicle.getColor());
        
        // Efectos visuales según el tipo
        switch (vehicle.getType()) {
            case EMERGENCY -> {
                // Efecto de brillo para emergencias
                circle.setStroke(Color.YELLOW);
                circle.setStrokeWidth(3);
                
                DropShadow glow = new DropShadow();
                glow.setColor(Color.YELLOW);
                glow.setRadius(15);
                glow.setSpread(0.3);
                circle.setEffect(glow);
                
                // Animación de pulso para emergencias
                createPulseAnimation(circle);
            }
            case PUBLIC_TRANSPORT -> {
                circle.setStroke(Color.WHITE);
                circle.setStrokeWidth(2);
                circle.getStrokeDashArray().addAll(5.0, 5.0);
            }
            case HEAVY -> {
                circle.setStroke(Color.BLACK);
                circle.setStrokeWidth(2);
                
                DropShadow shadow = new DropShadow();
                shadow.setRadius(5.0);
                shadow.setOffsetX(2.0);
                shadow.setOffsetY(2.0);
                circle.setEffect(shadow);
            }
            default -> {
                // Vehículos normales con borde sutil
                if (vehicle.isHighwayVehicle()) {
                    circle.setStroke(Color.LIGHTGRAY);
                    circle.setStrokeWidth(1);
                } else {
                    circle.setStroke(Color.DARKGRAY);
                    circle.setStrokeWidth(1);
                }
            }
        }
        
        // Agregar identificador visual para debugging
        circle.setId("vehicle-" + vehicle.getId());
        
        return circle;
    }
    
    /**
     * Crea animación de entrada para el vehículo
     */
    private void createEntryAnimation(Circle node, Vehicle vehicle) {
        // Fade in
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(0.5), node);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        
        // Scale up suave
        ScaleTransition scaleIn = new ScaleTransition(Duration.seconds(0.5), node);
        scaleIn.setFromX(0.5);
        scaleIn.setFromY(0.5);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);
        
        // Combinar animaciones
        ParallelTransition entryAnimation = new ParallelTransition(fadeIn, scaleIn);
        entryAnimation.setInterpolator(Interpolator.EASE_OUT);
        
        // Log cuando termine la animación
        entryAnimation.setOnFinished(e -> {
            logger.fine("Animación de entrada completada para vehículo " + vehicle.getId());
        });
        
        entryAnimation.play();
    }
    
    /**
     * Crea animación de pulso para vehículos de emergencia
     */
    private void createPulseAnimation(Circle node) {
        ScaleTransition pulse = new ScaleTransition(Duration.seconds(1), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.2);
        pulse.setToY(1.2);
        pulse.setCycleCount(Timeline.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.play();
    }
    
    public boolean isRunning() {
        return running.get();
    }
}