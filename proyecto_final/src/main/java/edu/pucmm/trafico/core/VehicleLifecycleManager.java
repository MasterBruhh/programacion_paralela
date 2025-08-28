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
import java.util.Set;
import java.util.HashSet;
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
        final Set<Long> emergencyVehicleIds;  // Conjunto de IDs de vehículos de emergencia
        final long startTime;
        volatile boolean active;
        // Mantener referencia al estado real en el momento de activación (solo informativo)
        final TrafficLightState snapshotState;

        EmergencyProtocol(String groupName, long emergencyVehicleId, TrafficLightState snapshotState) {
            this.groupName = groupName;
            this.emergencyVehicleIds = new HashSet<>();
            this.emergencyVehicleIds.add(emergencyVehicleId);
            this.startTime = System.currentTimeMillis();
            this.active = true;
            this.snapshotState = snapshotState;
        }
        
        void addEmergencyVehicle(long vehicleId) {
            emergencyVehicleIds.add(vehicleId);
        }
        
        void removeEmergencyVehicle(long vehicleId) {
            emergencyVehicleIds.remove(vehicleId);
        }
        
        int getEmergencyCount() {
            return emergencyVehicleIds.size();
        }
        
        boolean isEmpty() {
            return emergencyVehicleIds.isEmpty();
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
    /**
     * Agrega un vehículo al sistema y lanza su tarea de movimiento
     */
    public void addVehicle(Vehicle vehicle, Circle visualNode) {
        // Registrar vehículo
        registry.registerVehicle(vehicle);

        // ← AGREGAR ESTAS LÍNEAS PARA DESHABILITAR EVENTOS DE MOUSE
        visualNode.setMouseTransparent(true);      // No capturar eventos de mouse
        visualNode.setFocusTraversable(false);     // No recibir foco
        visualNode.setPickOnBounds(false);         // Solo detectar en el área visible del círculo

        vehicleNodes.put(vehicle.getId(), visualNode);

        // Log especial para emergencias
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            logger.warning(String.format("EMERGENCIA: Vehículo #%d entrando al sistema", 
                vehicle.getId()));
            
            // Notificar a todos los vehículos activos sobre la emergencia
            notifyEmergencyApproaching(vehicle);

            // Activar protocolo inmediatamente para su grupo correspondiente
            String groupName = determineGroupNameForVehicle(vehicle);
            if (groupName != null) {
                activateEmergencyProtocol(groupName, vehicle.getId());
            }
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

            // Si ya existe un protocolo activo, añadir esta emergencia al grupo
            if (existing != null && existing.active) {
                existing.emergencyVehicleIds.add(emergencyId);
                logger.warning("Emergencia añadida #" + emergencyId + " al grupo " + 
                              groupName + " (total=" + existing.getEmergencyCount() + ")");
                return;
            }

            // Si no existe protocolo activo, crear uno nuevo
            TrafficLightGroup group = trafficLightGroups.get(groupName);
            TrafficLightState snapshot = group != null ? group.getState() : TrafficLightState.RED;
            EmergencyProtocol protocol = new EmergencyProtocol(groupName, emergencyId, snapshot);
            emergencyProtocols.put(groupName, protocol);

            logger.warning("PROTOCOLO DE EMERGENCIA ACTIVADO para grupo " + groupName +
                " - Emergencias=[" + emergencyId + "]" + (group != null ? " (Estado previo: " + snapshot + ")" : ""));

            // Programar timeout de seguridad
            scheduleEmergencyTimeout(groupName, 30000);
        }
    }

    /**
     * Variante explícita para activar protocolo con ID de vehículo ya conocido.
     */
    public void activateEmergencyProtocol(String groupName, long emergencyVehicleId) {
        synchronized (emergencyLock) {
            EmergencyProtocol existing = emergencyProtocols.get(groupName);
            
            // Si ya existe un protocolo activo, añadir esta emergencia al grupo
            if (existing != null && existing.active) {
                existing.emergencyVehicleIds.add(emergencyVehicleId);
                logger.warning("Emergencia añadida #" + emergencyVehicleId + " al grupo " + 
                              groupName + " (total=" + existing.getEmergencyCount() + ")");
                return;
            }

            TrafficLightGroup group = trafficLightGroups.get(groupName);
            TrafficLightState snapshot = group != null ? group.getState() : TrafficLightState.RED;
            EmergencyProtocol protocol = new EmergencyProtocol(groupName, emergencyVehicleId, snapshot);
            emergencyProtocols.put(groupName, protocol);

            logger.warning("PROTOCOLO DE EMERGENCIA ACTIVADO para grupo " + groupName +
                " - Emergencias=[" + emergencyVehicleId + "]" + (group != null ? " (Estado previo: " + snapshot + ")" : ""));

            scheduleEmergencyTimeout(groupName, 30000);
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
        
        // No se modifican físicamente los semáforos durante el protocolo (virtualización),
        // por lo que solo se registra el evento.
        Platform.runLater(() -> logger.warning("PROTOCOLO DE EMERGENCIA DESACTIVADO para grupo " + groupName +
            " (Estado original: " + protocol.snapshotState + ")"));

        emergencyProtocols.remove(groupName);
        
        long duration = System.currentTimeMillis() - protocol.startTime;
        logger.info("Protocolo de emergencia duró " + (duration/1000.0) + " segundos");
    }
    
    /**
     * Elimina una emergencia específica de un protocolo activo
     */
    private void removeEmergencyFromProtocol(String groupName, long emergencyId) {
        synchronized (emergencyLock) {
            EmergencyProtocol protocol = emergencyProtocols.get(groupName);
            if (protocol != null && protocol.active) {
                protocol.emergencyVehicleIds.remove(emergencyId);
                
                if (protocol.isEmpty()) {
                    logger.warning("Última emergencia del grupo " + groupName + " salió - desactivando protocolo");
                    deactivateProtocol(protocol);
                } else {
                    logger.warning("Vehículo de emergencia " + emergencyId + 
                                 " saliendo del grupo " + groupName + 
                                 " (quedan=" + protocol.getEmergencyCount() + ")");
                }
            }
        }
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
        synchronized (emergencyLock) {
            // Recolectar protocolos activos
            boolean anyActive = false;
            boolean thisActive = false;
            for (EmergencyProtocol p : emergencyProtocols.values()) {
                if (p.active) {
                    anyActive = true;
                    if (p.groupName.equals(groupName)) thisActive = true;
                }
            }

            if (anyActive) {
                if (thisActive) {
                    // Virtualizar verde para permitir que TODOS los vehículos del mismo grupo avancen
                    return TrafficLightState.GREEN;
                } else {
                    // Bloquear todos los demás grupos con rojo, independientemente de su estado real
                    return TrafficLightState.RED;
                }
            }
        }

        TrafficLightGroup group = trafficLightGroups.get(groupName);
        return group != null ? group.getState() : TrafficLightState.RED;
    }

    /**
     * Versión con conciencia de vehículo: restringe la prioridad a SOLO el carril del vehículo de emergencia
     * y a los vehículos que están delante en ese mismo carril. Los demás carriles del mismo grupo se detienen.
     */
    public TrafficLightState getTrafficLightStateFor(Vehicle vehicle, String groupName) {
        synchronized (emergencyLock) {
            // Si no hay protocolos activos, devolver estado normal
            if (emergencyProtocols.isEmpty()) {
                return getTrafficLightState(groupName); // reutiliza lógica normal
            }

            // ¿Hay protocolo para este grupo?
            EmergencyProtocol protocol = emergencyProtocols.get(groupName);
            
            // Si el vehículo es una emergencia, SIEMPRE VE VERDE sin importar cuántas haya
            if (vehicle.getType() == VehicleType.EMERGENCY) {
                return TrafficLightState.GREEN;
            }

            if (protocol != null && protocol.active) {
                // Buscar todas las emergencias activas en el grupo actual
                Collection<Vehicle> allVehicles = getAllActiveVehicles();
                boolean anyEmergencyAhead = false;
                
                for (Vehicle v : allVehicles) {
                    if (v.getType() != VehicleType.EMERGENCY || !v.isActive()) continue;
                    
                    // Verificar si esta emergencia está en nuestro mismo grupo (misma dirección)
                    if (!groupName.equals(determineGroupNameForVehicle(v))) continue;
                    
                    // Caso avenidas: permitir que CUALQUIER vehículo por delante (en cualquier carril del mismo grupo/dirección)
                    // reciba verde virtual para despejar la trayectoria; los que están detrás permanecen en rojo.
                    if (v.isHighwayVehicle() && vehicle.isHighwayVehicle()) {
                        boolean westbound = v.getHighwayLane().isWestbound();
                        boolean vehicleAhead = westbound ? (vehicle.getX() < v.getX())
                                                        : (vehicle.getX() > v.getX());
                        if (vehicleAhead) {
                            anyEmergencyAhead = true;
                            break;
                        }
                    }
                }
                
                if (anyEmergencyAhead) {
                    return TrafficLightState.GREEN; // Avanza para despejar (aunque sea otro carril)
                }
                
                // Vehículos detrás o en calles esperan
                return TrafficLightState.RED;
            }

            // Si el protocolo es para otro grupo: bloqueo completo
            // Verificar si hay emergencias activas en cualquier otro grupo
            for (EmergencyProtocol p : emergencyProtocols.values()) {
                if (p.active && !p.groupName.equals(groupName)) {
                    // Si hay emergencias en otro grupo (ej. en ABAJO), este grupo (ej. ARRIBA) debe bloquearse
                    return TrafficLightState.RED;
                }
            }
        }
        // Sin interferencia: estado normal real
        TrafficLightGroup group = trafficLightGroups.get(groupName);
        return group != null ? group.getState() : TrafficLightState.RED;
    }

    /* La función findVehicleById ya no es necesaria */

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

        // Si era emergencia, actualizar protocolo activo
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            String groupName = determineGroupNameForVehicle(vehicle);
            if (groupName != null) {
                removeEmergencyFromProtocol(groupName, vehicle.getId());
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
     * Determina el nombre de grupo de semáforo asociado a un vehículo (para protocolo de emergencia).
     */
    private String determineGroupNameForVehicle(Vehicle v) {
        if (v.isHighwayVehicle()) {
            // Avenidas superiores (westbound) = ARRIBA, inferiores (eastbound) = ABAJO
            return v.getHighwayLane().isWestbound() ? "ARRIBA" : "ABAJO";
        } else if (v.getStartPoint() != null) {
            return switch (v.getStartPoint()) {
                case NORTH_L, NORTH_D -> "CalleIzq"; // Grupo para calle izquierda (norte -> sur)
                case SOUTH_L, SOUTH_D -> "CalleDer"; // Grupo para calle derecha (sur -> norte)
                case EAST, WEST -> "CalleDer";       // Reusar grupo (según diseño existente)
            };
        }
        return null;
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