package edu.pucmm.trafico.core;

import edu.pucmm.trafico.concurrent.*;
import edu.pucmm.trafico.model.*;
import javafx.scene.shape.Circle;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.util.Duration;
import java.util.concurrent.*;
import java.util.Map;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * Gestor del ciclo de vida de vehículos con soporte para protocolos de emergencia.
 * Coordina las prioridades de emergencia y gestiona los semáforos según sea necesario.
 */
public class VehicleLifecycleManager {
    private static final Logger logger = Logger.getLogger(VehicleLifecycleManager.class.getName());

    private final ExecutorService executorService;
    private final ThreadSafeVehicleRegistry registry;
    private final IntersectionSemaphore intersectionSemaphore;
    private final ConcurrentHashMap<Long, Future<?>> vehicleTasks;
    private final ConcurrentHashMap<Long, Circle> vehicleNodes;
    private final Map<String, TrafficLightGroup> trafficLightGroups;
    
    // Control de protocolo de emergencia
    private final ConcurrentHashMap<String, EmergencyProtocol> emergencyProtocols;
    private final Object emergencyLock = new Object();

    /**
     * Clase interna para manejar protocolos de emergencia por grupo de semáforos
     */
    private static class EmergencyProtocol {
        final String groupName;
        final long emergencyVehicleId;
        final long startTime;
        volatile boolean active;
        TrafficLightState previousState;

        EmergencyProtocol(String groupName, long emergencyVehicleId) {
            this.groupName = groupName;
            this.emergencyVehicleId = emergencyVehicleId;
            this.startTime = System.currentTimeMillis();
            this.active = true;
        }
    }

    public VehicleLifecycleManager(Map<String, TrafficLightGroup> trafficLightGroups) {
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
        this.emergencyProtocols = new ConcurrentHashMap<>();

        logger.info("VehicleLifecycleManager inicializado con soporte de emergencia");
    }

    /**
     * Agrega un vehículo al sistema y lanza su tarea de movimiento
     */
    public void addVehicle(Vehicle vehicle, Circle visualNode) {
        // Registrar vehículo
        registry.registerVehicle(vehicle);
        vehicleNodes.put(vehicle.getId(), visualNode);

        // Log especial para emergencias
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            logger.warning(String.format("⚠️ EMERGENCIA: Vehículo #%d entrando al sistema", 
                vehicle.getId()));
            
            // Notificar a todos los vehículos activos sobre la emergencia
            notifyEmergencyApproaching(vehicle);
        }

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
     * Notifica a todos los vehículos activos sobre una emergencia aproximándose
     */
    private void notifyEmergencyApproaching(Vehicle emergency) {
        Collection<Vehicle> activeVehicles = registry.snapshotAll();
        
        for (Vehicle vehicle : activeVehicles) {
            if (vehicle.getId() == emergency.getId()) continue;
            if (vehicle.getType() == VehicleType.EMERGENCY) continue;
            
            // Calcular distancia
            double distance = Math.hypot(
                vehicle.getX() - emergency.getX(),
                vehicle.getY() - emergency.getY()
            );
            
            // Si está cerca, marcar para ceder
            if (distance < 500) {
                logger.fine("Vehículo " + vehicle.getId() + 
                          " notificado de emergencia " + emergency.getId());
            }
        }
    }

    /**
     * Activa el protocolo de emergencia para un grupo de semáforos
     */
    public void activateEmergencyProtocol(String groupName) {
        synchronized (emergencyLock) {
            // Verificar si ya hay un protocolo activo para este grupo
            EmergencyProtocol existing = emergencyProtocols.get(groupName);
            if (existing != null && existing.active) {
                logger.fine("Protocolo de emergencia ya activo para grupo " + groupName);
                return;
            }

            // Buscar el vehículo de emergencia activo
            long emergencyId = -1;
            for (Vehicle v : registry.snapshotAll()) {
                if (v.getType() == VehicleType.EMERGENCY && v.isActive()) {
                    emergencyId = v.getId();
                    break;
                }
            }

            if (emergencyId == -1) {
                logger.warning("No se encontró vehículo de emergencia activo");
                return;
            }

            // Crear nuevo protocolo
            EmergencyProtocol protocol = new EmergencyProtocol(groupName, emergencyId);
            
            // Guardar el estado actual del semáforo
            TrafficLightGroup group = trafficLightGroups.get(groupName);
            if (group != null) {
                protocol.previousState = group.getState();
                
                // Forzar verde para el grupo de emergencia
                Platform.runLater(() -> {
                    // Poner todos los demás grupos en rojo
                    for (Map.Entry<String, TrafficLightGroup> entry : trafficLightGroups.entrySet()) {
                        if (!entry.getKey().equals(groupName)) {
                            entry.getValue().setState(TrafficLightState.RED);
                        }
                    }
                    // Poner el grupo de emergencia en verde
                    group.setState(TrafficLightState.GREEN);
                });
                
                logger.warning("🚨 PROTOCOLO DE EMERGENCIA ACTIVADO para grupo " + groupName + 
                             " - Vehículo #" + emergencyId);
            }
            
            emergencyProtocols.put(groupName, protocol);
            
            // Programar timeout por si acaso
            scheduleEmergencyTimeout(groupName, 30000); // 30 segundos máximo
        }
    }

    /**
     * Desactiva el protocolo de emergencia
     */
    public void deactivateEmergencyProtocol() {
        synchronized (emergencyLock) {
            // Buscar protocolos activos
            for (Map.Entry<String, EmergencyProtocol> entry : emergencyProtocols.entrySet()) {
                EmergencyProtocol protocol = entry.getValue();
                if (protocol.active) {
                    deactivateProtocol(protocol);
                }
            }
        }
    }

    /**
     * Desactiva un protocolo específico
     */
    private void deactivateProtocol(EmergencyProtocol protocol) {
        if (!protocol.active) return;
        
        protocol.active = false;
        String groupName = protocol.groupName;
        
        // Restaurar estado anterior del semáforo
        TrafficLightGroup group = trafficLightGroups.get(groupName);
        if (group != null && protocol.previousState != null) {
            Platform.runLater(() -> {
                // Restaurar el ciclo normal de semáforos
                logger.warning("✅ PROTOCOLO DE EMERGENCIA DESACTIVADO para grupo " + groupName);
            });
        }
        
        emergencyProtocols.remove(groupName);
        
        long duration = System.currentTimeMillis() - protocol.startTime;
        logger.info("Protocolo de emergencia duró " + (duration/1000.0) + " segundos");
    }

    /**
     * Programa un timeout para el protocolo de emergencia
     */
    private void scheduleEmergencyTimeout(String groupName, long timeoutMs) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(() -> {
            synchronized (emergencyLock) {
                EmergencyProtocol protocol = emergencyProtocols.get(groupName);
                if (protocol != null && protocol.active) {
                    logger.warning("Timeout de protocolo de emergencia para grupo " + groupName);
                    deactivateProtocol(protocol);
                }
            }
            scheduler.shutdown();
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Verifica si hay un protocolo de emergencia activo para un grupo
     */
    public boolean hasEmergencyProtocol(String groupName) {
        synchronized (emergencyLock) {
            EmergencyProtocol protocol = emergencyProtocols.get(groupName);
            return protocol != null && protocol.active;
        }
    }

    /**
     * Obtiene el nodo visual de un vehículo
     */
    public Circle getVehicleNode(long vehicleId) {
        return vehicleNodes.get(vehicleId);
    }

    /**
     * Obtiene todos los vehículos activos
     */
    public Collection<Vehicle> getAllActiveVehicles() {
        return registry.snapshotAll();
    }

    /**
     * Estado actual del semáforo de un grupo
     * Considera protocolos de emergencia activos
     */
    public TrafficLightState getTrafficLightState(String groupName) {
        // Si hay protocolo de emergencia activo, devolver el estado forzado
        synchronized (emergencyLock) {
            EmergencyProtocol protocol = emergencyProtocols.get(groupName);
            if (protocol != null && protocol.active) {
                return TrafficLightState.GREEN; // Siempre verde durante emergencia
            }
            
            // Si hay emergencia en otro grupo, este debe estar en rojo
            for (EmergencyProtocol p : emergencyProtocols.values()) {
                if (p.active && !p.groupName.equals(groupName)) {
                    return TrafficLightState.RED;
                }
            }
        }
        
        // Estado normal del semáforo
        TrafficLightGroup group = trafficLightGroups.get(groupName);
        return group != null ? group.getState() : TrafficLightState.RED;
    }

    /**
     * Semáforo de intersección
     */
    public IntersectionSemaphore getIntersectionSemaphore() { 
        return intersectionSemaphore; 
    }

    /**
     * Elimina el vehículo de la escena con animación y limpia recursos
     */
    public void removeVehicle(Vehicle vehicle) {
        Future<?> task = vehicleTasks.remove(vehicle.getId());
        if (task != null) {
            task.cancel(true);
        }

        // Si era emergencia, verificar protocolos activos
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            synchronized (emergencyLock) {
                for (EmergencyProtocol protocol : emergencyProtocols.values()) {
                    if (protocol.emergencyVehicleId == vehicle.getId() && protocol.active) {
                        logger.warning("Vehículo de emergencia " + vehicle.getId() + 
                                     " saliendo - desactivando protocolo");
                        deactivateProtocol(protocol);
                    }
                }
            }
        }

        registry.unregisterVehicle(vehicle);
        vehicle.deactivate();

        Platform.runLater(() -> {
            Circle node = vehicleNodes.remove(vehicle.getId());
            if (node != null && node.getParent() != null) {
                performExitAnimation(node, vehicle);
            }
        });
    }

    /**
     * Animación de salida mejorada para emergencias
     */
    private void performExitAnimation(Circle node, Vehicle vehicle) {
        FadeTransition fade = new FadeTransition(Duration.seconds(1.0), node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        ScaleTransition scale = new ScaleTransition(Duration.seconds(1.0), node);
        scale.setToX(0.2);
        scale.setToY(0.2);

        // Si es emergencia, agregar efecto especial
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            // Parpadeo rápido antes de desaparecer
            Timeline flash = new Timeline(
                new KeyFrame(Duration.seconds(0.1), 
                    new KeyValue(node.opacityProperty(), 0.3)),
                new KeyFrame(Duration.seconds(0.2), 
                    new KeyValue(node.opacityProperty(), 1.0))
            );
            flash.setCycleCount(3);
            flash.setOnFinished(e -> {
                ParallelTransition exit = new ParallelTransition(fade, scale);
                exit.setOnFinished(e2 -> {
                    if (node.getParent() != null) {
                        ((javafx.scene.layout.Pane) node.getParent()).getChildren().remove(node);
                    }
                });
                exit.play();
            });
            flash.play();
        } else {
            ParallelTransition exit = new ParallelTransition(fade, scale);
            exit.setOnFinished(e -> {
                if (node.getParent() != null) {
                    ((javafx.scene.layout.Pane) node.getParent()).getChildren().remove(node);
                }
            });
            exit.play();
        }
    }

    /**
     * Detiene todas las tareas y libera recursos
     */
    public void shutdown() {
        logger.info("Deteniendo todas las tareas de vehículos...");

        // Desactivar todos los protocolos de emergencia
        synchronized (emergencyLock) {
            for (EmergencyProtocol protocol : emergencyProtocols.values()) {
                if (protocol.active) {
                    deactivateProtocol(protocol);
                }
            }
        }

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
     * Obtiene estadísticas del sistema incluyendo emergencias
     */
    public String getSystemStats() {
        Collection<Vehicle> active = registry.snapshotAll();
        int totalVehicles = active.size();
        int emergencyVehicles = 0;
        int vehiclesYielding = 0;
        
        for (Vehicle v : active) {
            if (v.getType() == VehicleType.EMERGENCY) {
                emergencyVehicles++;
            }
            if (v.isEmergencyYielding()) {
                vehiclesYielding++;
            }
        }

        int activeProtocols = 0;
        synchronized (emergencyLock) {
            for (EmergencyProtocol p : emergencyProtocols.values()) {
                if (p.active) activeProtocols++;
            }
        }

        return String.format("Vehículos: %d | Emergencias: %d | Cediendo: %d | Protocolos activos: %d",
                totalVehicles, emergencyVehicles, vehiclesYielding, activeProtocols);
    }
}