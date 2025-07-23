package edu.pucmm.trafico.core;

import edu.pucmm.trafico.concurrent.IntersectionSemaphore;
import edu.pucmm.trafico.concurrent.ThreadSafeVehicleRegistry;
import edu.pucmm.trafico.concurrent.VehicleTask;
import edu.pucmm.trafico.model.Vehicle;
import edu.pucmm.trafico.model.StartPoint;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PathTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.util.Duration;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

public class VehicleLifecycleManager {
    private static final Logger logger = Logger.getLogger(VehicleLifecycleManager.class.getName());
    private static final double QUEUE_SPACING = 30.0; // Aumentado para mejor espaciado
    private static final double INITIAL_OFFSET = 50.0; // Distancia inicial desde el punto de spawn
    
    private final ExecutorService executorService;
    private final ThreadSafeVehicleRegistry registry;
    private final IntersectionSemaphore intersectionSemaphore;
    private final IntersectionController intersectionController;
    private final ConcurrentHashMap<Long, Future<?>> vehicleTasks;
    private final ConcurrentHashMap<Long, Circle> vehicleNodes;
    
    // Nuevo: mapa para rastrear las posiciones finales reales de los vehículos
    private final ConcurrentHashMap<Long, double[]> vehicleFinalPositions;
    
    public VehicleLifecycleManager() {
        this.executorService = Executors.newCachedThreadPool();
        this.registry = new ThreadSafeVehicleRegistry();
        this.intersectionSemaphore = new IntersectionSemaphore();
        this.intersectionController = new IntersectionController();
        this.vehicleTasks = new ConcurrentHashMap<>();
        this.vehicleNodes = new ConcurrentHashMap<>();
        this.vehicleFinalPositions = new ConcurrentHashMap<>();
    }
    
    /**
     * Calcula la posición inicial correcta para un vehículo basándose en la cola existente.
     * Implementación mejorada basada en el código viejo para evitar sobreposiciones.
     */
    private double[] calculateInitialPosition(Vehicle vehicle) {
        StartPoint startPoint = vehicle.getStartPoint();
        
        // Contar vehículos existentes en el mismo carril
        int queueCount = 0;
        for (Vehicle existingVehicle : registry.getAllVehicles()) {
            if (existingVehicle.getStartPoint() == startPoint && existingVehicle.isActive()) {
                queueCount++;
            }
        }
        
        // Obtener coordenadas base del punto de partida
        double baseX = startPoint.getX();
        double baseY = startPoint.getY();
        
        // Calcular offset basado en la posición en cola con mayor espaciado
        double offsetDistance = INITIAL_OFFSET + (queueCount * QUEUE_SPACING);
        
        // Ajustar posición según la dirección del carril
        switch (startPoint) {
            case NORTH:
                // Los vehículos vienen desde arriba, se apilan hacia arriba
                return new double[]{baseX, baseY - offsetDistance};
            case SOUTH:
                // Los vehículos vienen desde abajo, se apilan hacia abajo
                return new double[]{baseX, baseY + offsetDistance};
            case EAST:
                // Los vehículos vienen desde la derecha, se apilan hacia la derecha
                return new double[]{baseX + offsetDistance, baseY};
            case WEST:
                // Los vehículos vienen desde la izquierda, se apilan hacia la izquierda
                return new double[]{baseX - offsetDistance, baseY};
            default:
                return new double[]{baseX, baseY};
        }
    }
    
    public void addVehicle(Vehicle vehicle, Circle visualNode) {
        // Calcular posición inicial correcta ANTES de registrar el vehículo
        double[] initialPos = calculateInitialPosition(vehicle);
        
        // Actualizar la posición del vehículo a la posición inicial calculada
        vehicle.updatePosition(initialPos[0], initialPos[1]);
        
        // Actualizar la posición visual del nodo ANTES de agregarlo a la escena
        Platform.runLater(() -> {
            visualNode.setCenterX(initialPos[0]);
            visualNode.setCenterY(initialPos[1]);
        });
        
        // Ahora registrar el vehículo
        registry.registerVehicle(vehicle);
        vehicleNodes.put(vehicle.getId(), visualNode);
        
        VehicleTask task = new VehicleTask(vehicle, this);
        Future<?> future = executorService.submit(task);
        vehicleTasks.put(vehicle.getId(), future);
    }
    
    public void removeVehicle(Vehicle vehicle) {
        vehicle.deactivate();
        
        Future<?> task = vehicleTasks.remove(vehicle.getId());
        if (task != null) {
            task.cancel(true);
        }
        
        // Guardar la posición final actual del vehículo antes de eliminarlo
        vehicleFinalPositions.put(vehicle.getId(), new double[]{vehicle.getX(), vehicle.getY()});
        
        Platform.runLater(() -> {
            Circle node = vehicleNodes.remove(vehicle.getId());
            if (node != null && node.getParent() != null) {
                performExitAnimation(node, vehicle);
            }
        });
        
        registry.unregisterVehicle(vehicle);
    }
    
    /**
     * Realiza la animación de salida de un vehículo.
     * Implementación mejorada que asegura el uso de la posición final correcta.
     */
    private void performExitAnimation(Circle node, Vehicle vehicle) {
        // Coordenadas actuales reales del vehículo
        double currentX = vehicle.getX();
        double currentY = vehicle.getY();
        
        // Asegurar que el nodo visual está en la misma posición que el modelo
        node.setCenterX(currentX);
        node.setCenterY(currentY);
        
        logger.fine("Vehículo " + vehicle.getId() + " saliendo desde posición (" + 
                   String.format("%.1f", currentX) + ", " + 
                   String.format("%.1f", currentY) + ")");
        
        // Calcular el punto de salida extendido en la dirección actual
        double targetX = currentX;
        double targetY = currentY;
        double distanciaExtraSalida = 150.0; // Distancia adicional para salir de escena
        
        // Determinar dirección de salida basada en el camino actual completado
        switch (vehicle.getDirection()) {
            case STRAIGHT:
                // Continuar en la misma dirección
                switch (vehicle.getStartPoint()) {
                    case NORTH -> targetY += distanciaExtraSalida; // Hacia abajo
                    case SOUTH -> targetY -= distanciaExtraSalida; // Hacia arriba
                    case EAST -> targetX -= distanciaExtraSalida; // Hacia la izquierda
                    case WEST -> targetX += distanciaExtraSalida; // Hacia la derecha
                }
                break;
            case LEFT:
                // Salir por la izquierda desde la perspectiva del vehículo
                switch (vehicle.getStartPoint()) {
                    case NORTH -> targetX -= distanciaExtraSalida; // Hacia la izquierda (oeste)
                    case SOUTH -> targetX += distanciaExtraSalida; // Hacia la derecha (este)
                    case EAST -> targetY += distanciaExtraSalida; // Hacia abajo (sur)
                    case WEST -> targetY -= distanciaExtraSalida; // Hacia arriba (norte)
                }
                break;
            case RIGHT:
                // Salir por la derecha desde la perspectiva del vehículo
                switch (vehicle.getStartPoint()) {
                    case NORTH -> targetX += distanciaExtraSalida; // Hacia la derecha (este)
                    case SOUTH -> targetX -= distanciaExtraSalida; // Hacia la izquierda (oeste)
                    case EAST -> targetY -= distanciaExtraSalida; // Hacia arriba (norte)
                    case WEST -> targetY += distanciaExtraSalida; // Hacia abajo (sur)
                }
                break;
            case U_TURN:
                // Volver por donde vino
                switch (vehicle.getStartPoint()) {
                    case NORTH -> targetY -= distanciaExtraSalida; // Hacia arriba
                    case SOUTH -> targetY += distanciaExtraSalida; // Hacia abajo
                    case EAST -> targetX += distanciaExtraSalida; // Hacia la derecha
                    case WEST -> targetX -= distanciaExtraSalida; // Hacia la izquierda
                }
                break;
        }
        
        // Crear una animación de desvanecimiento y movimiento suave desde la posición final actual
        Path exitPath = new Path();
        exitPath.getElements().addAll(
            new MoveTo(currentX, currentY),
            new LineTo(targetX, targetY)
        );
        
        PathTransition moveOut = new PathTransition(Duration.millis(2000), exitPath, node);
        moveOut.setInterpolator(Interpolator.EASE_OUT);
        
        FadeTransition fade = new FadeTransition(Duration.millis(2000), node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);
        
        ParallelTransition animation = new ParallelTransition(moveOut, fade);
        animation.setOnFinished(e -> {
            if (node.getParent() != null) {
                ((javafx.scene.layout.Pane) node.getParent()).getChildren().remove(node);
            }
            vehicleFinalPositions.remove(vehicle.getId());
            logger.fine("Vehículo " + vehicle.getId() + " removido de la escena");
        });
        
        animation.play();
    }
    
    public void updateVehiclePosition(Vehicle vehicle) {
        // Guardar la posición actual como posición final potencial
        vehicleFinalPositions.put(vehicle.getId(), new double[]{vehicle.getX(), vehicle.getY()});
        
        Platform.runLater(() -> {
            Circle node = vehicleNodes.get(vehicle.getId());
            if (node != null) {
                node.setCenterX(vehicle.getX());
                node.setCenterY(vehicle.getY());
            }
        });
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
    
    public IntersectionController getIntersectionController() {
        return intersectionController;
    }
    
    public Circle getVehicleNode(long vehicleId) {
        return vehicleNodes.get(vehicleId);
    }
    
    /**
     * Obtiene todos los vehículos activos en la simulación
     * @return Una colección con todos los vehículos activos
     */
    public Collection<Vehicle> getAllActiveVehicles() {
        return registry.getAllVehicles();
    }
}