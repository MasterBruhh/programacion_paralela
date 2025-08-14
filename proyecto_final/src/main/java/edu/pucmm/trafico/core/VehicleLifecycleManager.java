package edu.pucmm.trafico.core;

import edu.pucmm.trafico.concurrent.*;
import edu.pucmm.trafico.model.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.shape.Circle;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import java.util.concurrent.*;
import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;

public class VehicleLifecycleManager {
    private static final Logger logger = Logger.getLogger(VehicleLifecycleManager.class.getName());
    
    private final ExecutorService executorService;
    private final ThreadSafeVehicleRegistry registry;
    private final IntersectionSemaphore intersectionSemaphore;
    private final HighwayLaneManager laneManager;
    private final ConcurrentHashMap<Long, Future<?>> vehicleTasks;
    private final ConcurrentHashMap<Long, Circle> vehicleNodes;
    private final Map<String, TrafficLightGroup> trafficLightGroups;
    
    private volatile boolean emergencyProtocolActive = false;
    private String emergencyGroup = null;
    
    public VehicleLifecycleManager(Map<String, TrafficLightGroup> trafficLightGroups) {
        this.executorService = Executors.newCachedThreadPool();
        this.registry = new ThreadSafeVehicleRegistry();
        this.intersectionSemaphore = new IntersectionSemaphore();
        this.laneManager = new HighwayLaneManager();
        this.vehicleTasks = new ConcurrentHashMap<>();
        this.vehicleNodes = new ConcurrentHashMap<>();
        this.trafficLightGroups = trafficLightGroups;
    }
    
    public void addVehicle(Vehicle vehicle, Circle visualNode) {
        registry.registerVehicle(vehicle);
        vehicleNodes.put(vehicle.getId(), visualNode);
        
        if (vehicle.isHighwayVehicle()) {
            laneManager.registerVehicle(vehicle);
        }
        
        VehicleTask task = new VehicleTask(vehicle, this);
        Future<?> future = executorService.submit(task);
        vehicleTasks.put(vehicle.getId(), future);
        
        logger.info("Vehículo agregado: " + vehicle);
    }
    
    public void removeVehicle(Vehicle vehicle) {
        Platform.runLater(() -> {
            Circle node = vehicleNodes.remove(vehicle.getId());
            if (node != null && node.getParent() != null) {
                performExitAnimation(node, vehicle);
            }
        });
        
        Future<?> task = vehicleTasks.remove(vehicle.getId());
        if (task != null) {
            task.cancel(true);
        }
        
        registry.unregisterVehicle(vehicle);
        
        if (vehicle.isHighwayVehicle()) {
            laneManager.unregisterVehicle(vehicle);
        }
        
        vehicle.deactivate();
    }
    
    private void performExitAnimation(Circle node, Vehicle vehicle) {
        FadeTransition fade = new FadeTransition(Duration.seconds(1.5), node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        
        ScaleTransition scale = new ScaleTransition(Duration.seconds(1.5), node);
        scale.setToX(0.1);
        scale.setToY(0.1);
        
        ParallelTransition exit = new ParallelTransition(fade, scale);
        exit.setOnFinished(e -> {
            if (node.getParent() != null) {
                ((javafx.scene.layout.Pane) node.getParent()).getChildren().remove(node);
            }
        });
        exit.play();
    }
    
    public void activateEmergencyProtocol(String groupName) {
        if (emergencyProtocolActive) return;
        
        emergencyProtocolActive = true;
        emergencyGroup = groupName;
        
        for (Map.Entry<String, TrafficLightGroup> entry : trafficLightGroups.entrySet()) {
            if (!entry.getKey().equals("HIGHWAY")) {
                if (entry.getKey().equals(groupName)) {
                    entry.getValue().setState(TrafficLightState.GREEN);
                } else {
                    entry.getValue().setState(TrafficLightState.RED);
                }
            }
        }
        
        logger.warning("Protocolo de emergencia activado para grupo: " + groupName);
    }
    
    public void deactivateEmergencyProtocol() {
        if (!emergencyProtocolActive) return;
        
        emergencyProtocolActive = false;
        emergencyGroup = null;
        
        logger.info("Protocolo de emergencia desactivado");
    }
    
    public TrafficLightState getTrafficLightState(String groupName) {
        TrafficLightGroup group = trafficLightGroups.get(groupName);
        return group != null ? group.getState() : TrafficLightState.RED;
    }
    
    public void shutdown() {
        executorService.shutdownNow();
    }
    
    public ThreadSafeVehicleRegistry getRegistry() {
        return registry;
    }
    
    public IntersectionSemaphore getIntersectionSemaphore() {
        return intersectionSemaphore;
    }
    
    public HighwayLaneManager getLaneManager() {
        return laneManager;
    }
    
    public Circle getVehicleNode(long vehicleId) {
        return vehicleNodes.get(vehicleId);
    }
    
    public Collection<Vehicle> getAllActiveVehicles() {
        return registry.getAllVehicles();
    }
}