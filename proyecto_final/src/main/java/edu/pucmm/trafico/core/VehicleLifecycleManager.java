package edu.pucmm.trafico.core;

import edu.pucmm.trafico.concurrent.*;
import edu.pucmm.trafico.model.*;
import javafx.scene.shape.Circle;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.util.Duration;
import java.util.concurrent.*;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Gestor simplificado del ciclo de vida de vehículos para la fase inicial.
 * Se enfoca en el registro y lanzamiento de tareas de movimiento.
 */
public class VehicleLifecycleManager {
    private static final Logger logger = Logger.getLogger(VehicleLifecycleManager.class.getName());
    
    private final ExecutorService executorService;
    private final ThreadSafeVehicleRegistry registry;
    private final IntersectionSemaphore intersectionSemaphore;
    private final ConcurrentHashMap<Long, Future<?>> vehicleTasks;
    private final ConcurrentHashMap<Long, Circle> vehicleNodes;
    private final Map<String, TrafficLightGroup> trafficLightGroups;
    
    public VehicleLifecycleManager(Map<String, TrafficLightGroup> trafficLightGroups) {
        // Pool de threads para manejar vehículos concurrentemente
        final java.util.concurrent.atomic.AtomicInteger threadCount = new java.util.concurrent.atomic.AtomicInteger(1);
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("Vehicle-" + threadCount.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        
    this.registry = new ThreadSafeVehicleRegistry();
    this.intersectionSemaphore = new IntersectionSemaphore();
        this.vehicleTasks = new ConcurrentHashMap<>();
        this.vehicleNodes = new ConcurrentHashMap<>();
        this.trafficLightGroups = trafficLightGroups;
        
        logger.info("VehicleLifecycleManager inicializado");
    }
    
    /**
     * Agrega un vehículo al sistema y lanza su tarea de movimiento
     */
    public void addVehicle(Vehicle vehicle, Circle visualNode) {
        // Registrar vehículo
        registry.registerVehicle(vehicle);
        vehicleNodes.put(vehicle.getId(), visualNode);
        
        // Log de registro
        logger.info(String.format("Registrando vehículo #%d [%s] en %s",
                vehicle.getId(),
                vehicle.getType(),
                vehicle.isHighwayVehicle() ? 
                    "Autopista - " + vehicle.getHighwayLane().getDescription() :
                    "Calle - " + vehicle.getStartPoint().getDescription()
        ));
        
        // Crear y lanzar tarea de movimiento
        VehicleTask task = new VehicleTask(vehicle, this);
        Future<?> future = executorService.submit(task);
        vehicleTasks.put(vehicle.getId(), future);
        
        logger.info("Tarea de movimiento lanzada para vehículo " + vehicle.getId());
    }
    
    /**
     * Obtiene el nodo visual de un vehículo
     */
    public Circle getVehicleNode(long vehicleId) {
        return vehicleNodes.get(vehicleId);
    }
    
    /**
     * Obtiene todos los vehículos activos (placeholder para colisiones futuras)
     */
    public java.util.Collection<Vehicle> getAllActiveVehicles() {
        return registry.getAllVehicles();
    }
    
    /**
     * Placeholder para obtener estado de semáforo (implementar en fase posterior)
     */
    public TrafficLightState getTrafficLightState(String groupName) {
        TrafficLightGroup group = trafficLightGroups.get(groupName);
        return group != null ? group.getState() : TrafficLightState.RED;
    }
    
    /**
     * Placeholder para semáforo de intersección (implementar en fase posterior)
     */
    public IntersectionSemaphore getIntersectionSemaphore() { return intersectionSemaphore; }
    
    /**
     * Placeholder para protocolo de emergencia (implementar en fase posterior)
     */
    public void activateEmergencyProtocol(String groupName) {
        logger.info("Protocolo de emergencia será implementado en fase posterior");
    }
    
    public void deactivateEmergencyProtocol() {
        logger.info("Desactivación de protocolo de emergencia será implementada en fase posterior");
    }
    
    /**
     * Elimina el vehículo de la escena con animación y limpia recursos
     */
    public void removeVehicle(Vehicle vehicle) {
        Future<?> task = vehicleTasks.remove(vehicle.getId());
        if (task != null) {
            task.cancel(true);
        }
        
        registry.unregisterVehicle(vehicle);
        vehicle.deactivate();
        
        Platform.runLater(() -> {
            Circle node = vehicleNodes.remove(vehicle.getId());
            if (node != null && node.getParent() != null) {
                performExitAnimation(node);
            }
        });
    }
    
    private void performExitAnimation(Circle node) {
        FadeTransition fade = new FadeTransition(Duration.seconds(1.0), node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        
        ScaleTransition scale = new ScaleTransition(Duration.seconds(1.0), node);
        scale.setToX(0.2);
        scale.setToY(0.2);
        
        ParallelTransition exit = new ParallelTransition(fade, scale);
        exit.setOnFinished(e -> {
            if (node.getParent() != null) {
                ((javafx.scene.layout.Pane) node.getParent()).getChildren().remove(node);
            }
        });
        exit.play();
    }
    
    /**
     * Detiene todas las tareas y libera recursos
     */
    public void shutdown() {
        logger.info("Deteniendo todas las tareas de vehículos...");
        
        // Cancelar todas las tareas
        vehicleTasks.values().forEach(task -> task.cancel(true));
        
        // Detener el executor
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("VehicleLifecycleManager detenido");
    }
    
    /**
     * Obtiene estadísticas básicas del sistema
     */
    public String getSystemStats() {
        int totalVehicles = registry.getActiveVehicleCount();
        int emergencyVehicles = registry.getEmergencyVehicles().size();
        
        return String.format("Vehículos activos: %d (Emergencias: %d)",
                totalVehicles, emergencyVehicles);
    }
}