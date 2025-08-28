package edu.pucmm.trafico.core;

import edu.pucmm.trafico.model.StartPoint;
import edu.pucmm.trafico.model.VehicleType;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public class IntersectionController {
    private static final Logger logger = Logger.getLogger(IntersectionController.class.getName());
    
    // Modos de operación
    public enum CrossingMode {
        FIFO,      // Primero en llegar, primero en cruzar
        ROTATIVE,  // Rotación por carriles (Norte->Oeste->Sur->Este)
        EMERGENCY  // Protocolo de emergencia activo
    }
    
    // Estado de cada carril
    private class LaneState {
        final Queue<VehicleInfo> waitingQueue = new ConcurrentLinkedQueue<>();
        final AtomicInteger vehicleCount = new AtomicInteger(0);
        final AtomicBoolean hasVehiclesWaiting = new AtomicBoolean(false);
        final AtomicReference<VehicleInfo> vehicleAtStop = new AtomicReference<>(null);
        volatile VehicleInfo currentCrossing = null;
    }
    
    // Información de vehículo
    private static class VehicleInfo {
        final String id;
        final VehicleType type;
        final long arrivalTime;
        final StartPoint lane;
        volatile boolean atStop = false;
        volatile long stopArrivalTime = 0;
        
        VehicleInfo(String id, VehicleType type, StartPoint lane) {
            this.id = id;
            this.type = type;
            this.lane = lane;
            this.arrivalTime = System.currentTimeMillis();
        }
    }
    
    // Estado del controlador
    private final Map<StartPoint, LaneState> laneStates = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<VehicleInfo> globalQueue;
    private final Semaphore intersectionSemaphore = new Semaphore(1, true);
    private final AtomicReference<CrossingMode> currentMode = new AtomicReference<>(CrossingMode.FIFO);
    private final AtomicReference<StartPoint> currentRotationLane = new AtomicReference<>(StartPoint.NORTH_L);
    private final AtomicInteger congestedLanes = new AtomicInteger(0);
    private final Object turnLock = new Object();
    
    // Variables para el protocolo de emergencia
    private final AtomicReference<StartPoint> emergencyLane = new AtomicReference<>(null);
    private final AtomicBoolean emergencyProtocolActive = new AtomicBoolean(false);
    private final AtomicReference<String> currentEmergencyVehicleId = new AtomicReference<>(null);
    
    // Umbral de congestión
    private static final int CONGESTION_THRESHOLD = 3;
    
    // Orden de rotación
    private static final StartPoint[] ROTATION_ORDER = {
        StartPoint.NORTH_L, StartPoint.WEST, StartPoint.SOUTH_L,StartPoint.SOUTH_D, StartPoint.EAST, StartPoint.NORTH_D
    };
    
    public IntersectionController() {
        // Inicializar estados de carriles
        for (StartPoint point : StartPoint.values()) {
            laneStates.put(point, new LaneState());
        }
        
        // Cola global con prioridad para vehículos de emergencia y orden de llegada
        this.globalQueue = new PriorityBlockingQueue<>(100, (v1, v2) -> {
            // Prioridad 1: Vehículos de emergencia
            if (v1.type == VehicleType.EMERGENCY && v2.type != VehicleType.EMERGENCY) return -1;
            if (v1.type != VehicleType.EMERGENCY && v2.type == VehicleType.EMERGENCY) return 1;
            
            // Prioridad 2: Vehículos en el pare tienen prioridad sobre los que están en cola
            if (v1.atStop && !v2.atStop) return -1;
            if (!v1.atStop && v2.atStop) return 1;
            
            // Prioridad 3: Tiempo de llegada al pare (si ambos están en el pare)
            if (v1.atStop && v2.atStop) {
                return Long.compare(v1.stopArrivalTime, v2.stopArrivalTime);
            }
            
            // Prioridad 4: Tiempo de llegada general (FIFO)
            return Long.compare(v1.arrivalTime, v2.arrivalTime);
        });
        
        // Monitor de congestión
        startCongestionMonitor();
        
        logger.info("IntersectionController inicializado en modo FIFO");
    }
    
    /**
     * Registra un vehículo que llegó a la cola del carril
     */
    public void registerVehicleAtStop(StartPoint lane, String vehicleId, VehicleType type) {
        VehicleInfo vehicleInfo = new VehicleInfo(vehicleId, type, lane);
        LaneState state = laneStates.get(lane);
        
        state.waitingQueue.offer(vehicleInfo);
        state.vehicleCount.incrementAndGet();
        state.hasVehiclesWaiting.set(true);
        
        // Verificar si es un vehículo de emergencia para activar protocolo
        if (type == VehicleType.EMERGENCY && !emergencyProtocolActive.get()) {
            activateEmergencyProtocol(lane, vehicleId);
        }
        
        // Agregar a la cola global para modo FIFO
        if (currentMode.get() == CrossingMode.FIFO) {
            globalQueue.offer(vehicleInfo);
        }
        
        logger.info("Vehículo " + vehicleId + " (" + type + ") registrado en " + lane + 
                   " - Posición en cola: " + state.waitingQueue.size());
        
        checkCongestion();
    }
    
    /**
     * Activa el protocolo de emergencia para un carril específico
     */
    private void activateEmergencyProtocol(StartPoint lane, String vehicleId) {
        synchronized (turnLock) {
            if (emergencyProtocolActive.get()) {
                // Ya hay un protocolo de emergencia activo
                return;
            }
            
            emergencyLane.set(lane);
            currentEmergencyVehicleId.set(vehicleId);
            emergencyProtocolActive.set(true);
            
            // Cambiar el modo a emergencia
            CrossingMode previousMode = currentMode.getAndSet(CrossingMode.EMERGENCY);
            
            logger.warning("¡PROTOCOLO DE EMERGENCIA ACTIVADO! Vehículo " + vehicleId + 
                         " en carril " + lane + " tiene prioridad absoluta.");
            logger.info("Modo anterior: " + previousMode + " -> Nuevo modo: " + CrossingMode.EMERGENCY);
        }
    }
    
    /**
     * Notifica que un vehículo llegó a la línea de pare
     */
    public void vehicleReachedStop(StartPoint lane, String vehicleId) {
        LaneState state = laneStates.get(lane);
        
        // Buscar el vehículo en la cola
        for (VehicleInfo info : state.waitingQueue) {
            if (info.id.equals(vehicleId)) {
                info.atStop = true;
                info.stopArrivalTime = System.currentTimeMillis();
                state.vehicleAtStop.set(info);
                
                // Actualizar en la cola global si está en modo FIFO
                if (currentMode.get() == CrossingMode.FIFO) {
                    // Re-ordenar la cola global
                    globalQueue.remove(info);
                    globalQueue.offer(info);
                }
                
                logger.info("Vehículo " + vehicleId + " llegó al PARE en " + lane);
                break;
            }
        }
    }
    
    /**
     * Solicita turno para cruzar la intersección
     */
    public boolean requestCrossingTurn(StartPoint lane, String vehicleId) {
        LaneState state = laneStates.get(lane);
        
        // Verificar si es el primero en su carril
        VehicleInfo first = state.waitingQueue.peek();
        if (first == null || !first.id.equals(vehicleId)) {
            return false;
        }
        
        // Verificar si está en el pare
        if (!first.atStop) {
            return false;
        }
        
        synchronized (turnLock) {
            try {
                // Verificar protocolo de emergencia
                if (currentMode.get() == CrossingMode.EMERGENCY) {
                    // Si hay emergencia en otro carril, este vehículo debe esperar
                    if (lane != emergencyLane.get()) {
                        logger.info("Vehículo " + vehicleId + " debe esperar por protocolo de emergencia activo en " + emergencyLane.get());
                        return false;
                    }
                    
                    // Si el vehículo es el de emergencia, verificar si es su turno
                    if (vehicleId.equals(currentEmergencyVehicleId.get())) {
                        logger.warning("Vehículo de emergencia " + vehicleId + " solicita cruzar.");
                    } else {
                        logger.info("Vehículo " + vehicleId + " en carril con emergencia activa solicita cruzar.");
                    }
                }
                
                // Intentar adquirir el semáforo
                boolean acquired = intersectionSemaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    return false;
                }
                
                // Verificar según el modo actual
                boolean canCross = false;
                
                if (currentMode.get() == CrossingMode.EMERGENCY) {
                    // En modo emergencia, solo los vehículos del carril prioritario pueden pasar
                    canCross = (lane == emergencyLane.get());
                    
                    // Si es el vehículo de emergencia, verificar si ya cruzó
                    if (vehicleId.equals(currentEmergencyVehicleId.get())) {
                        logger.warning("Vehículo de emergencia " + vehicleId + " obtendrá turno para cruzar.");
                    }
                } else if (currentMode.get() == CrossingMode.FIFO) {
                    // En modo FIFO, verificar si es el siguiente en la cola global
                    VehicleInfo globalNext = findNextInGlobalQueue();
                    canCross = globalNext != null && globalNext.id.equals(vehicleId);
                    
                    if (canCross) {
                        globalQueue.remove(globalNext); // Remover de la cola global
                    }
                } else {
                    // En modo ROTATIVO, verificar si es el turno de su carril
                    canCross = (lane == currentRotationLane.get()) && hasVehiclesWaitingAt(lane);
                }
                
                if (canCross) {
                    // Marcar como cruzando
                    state.currentCrossing = state.waitingQueue.poll();
                    state.vehicleCount.decrementAndGet();
                    state.vehicleAtStop.set(null);
                    
                    // Si es el vehículo de emergencia, marcarlo para desactivar el protocolo al finalizar
                    if (currentMode.get() == CrossingMode.EMERGENCY && 
                        vehicleId.equals(currentEmergencyVehicleId.get())) {
                        logger.warning("Vehículo de emergencia " + vehicleId + " comenzando a cruzar.");
                    }
                    
                    logger.info("Turno otorgado a " + vehicleId + " desde " + lane + 
                               " (Modo: " + currentMode.get() + ")");
                    return true;
                } else {
                    // No es su turno, liberar semáforo
                    intersectionSemaphore.release();
                    return false;
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
    
    /**
     * Busca el siguiente vehículo en la cola global que esté en el pare
     */
    private VehicleInfo findNextInGlobalQueue() {
        // Buscar el primer vehículo que esté realmente en el pare
        Iterator<VehicleInfo> iterator = globalQueue.iterator();
        while (iterator.hasNext()) {
            VehicleInfo info = iterator.next();
            if (info.atStop) {
                return info;
            }
        }
        return null;
    }
    
    /**
     * Notifica que un vehículo completó el cruce
     */
    public void notifyCrossingComplete(StartPoint lane, String vehicleId) {
        synchronized (turnLock) {
            LaneState state = laneStates.get(lane);
            
            if (state.currentCrossing != null && state.currentCrossing.id.equals(vehicleId)) {
                state.currentCrossing = null;
                
                // Verificar si es el vehículo de emergencia para desactivar el protocolo
                if (vehicleId.equals(currentEmergencyVehicleId.get())) {
                    deactivateEmergencyProtocol(vehicleId);
                }
                
                // Actualizar estado del carril
                if (state.waitingQueue.isEmpty()) {
                    state.hasVehiclesWaiting.set(false);
                }
                
                // En modo rotativo, avanzar al siguiente carril
                if (currentMode.get() == CrossingMode.ROTATIVE) {
                    advanceRotation();
                }
                
                // Liberar semáforo
                intersectionSemaphore.release();
                
                logger.info("Vehículo " + vehicleId + " completó el cruce desde " + lane);
                
                checkCongestion();
            }
        }
    }
    
    /**
     * Desactiva el protocolo de emergencia cuando el vehículo de emergencia ha cruzado
     */
    private void deactivateEmergencyProtocol(String vehicleId) {
        if (emergencyProtocolActive.get() && vehicleId.equals(currentEmergencyVehicleId.get())) {
            logger.warning("¡PROTOCOLO DE EMERGENCIA DESACTIVADO! Vehículo " + vehicleId + " ha cruzado la intersección.");
            
            // Restablecer variables de emergencia
            emergencyProtocolActive.set(false);
            currentEmergencyVehicleId.set(null);
            emergencyLane.set(null);
            
            // Restablecer el modo anterior según las condiciones de congestión actuales
            if (congestedLanes.get() >= CONGESTION_THRESHOLD) {
                switchToRotativeMode();
                logger.info("Volviendo a modo ROTATIVO después de emergencia debido a congestión.");
            } else {
                switchToFIFOMode();
                logger.info("Volviendo a modo FIFO después de emergencia.");
            }
            
            // Notificar a todos los hilos para que revisen si pueden cruzar ahora
            turnLock.notifyAll();
        }
    }
    
    /**
     * Obtiene la posición de un vehículo en la cola de su carril
     */
    public int getQueuePosition(StartPoint lane, String vehicleId) {
        LaneState state = laneStates.get(lane);
        int position = 0;
        
        for (VehicleInfo info : state.waitingQueue) {
            if (info.id.equals(vehicleId)) {
                return position;
            }
            position++;
        }
        
        return -1; // No encontrado
    }
    
    /**
     * Verifica el nivel de congestión y cambia de modo si es necesario
     */
    private void checkCongestion() {
        int lanesWithVehicles = 0;
        int vehiclesAtStop = 0;
        
        for (LaneState state : laneStates.values()) {
            if (state.hasVehiclesWaiting.get()) {
                lanesWithVehicles++;
                if (state.vehicleAtStop.get() != null) {
                    vehiclesAtStop++;
                }
            }
        }
        
        congestedLanes.set(lanesWithVehicles);
        
        // Cambiar a modo rotativo si hay congestión
        if (vehiclesAtStop >= CONGESTION_THRESHOLD && currentMode.get() == CrossingMode.FIFO) {
            switchToRotativeMode();
        }
        // Volver a modo FIFO si la congestión disminuye
        else if (vehiclesAtStop < 2 && currentMode.get() == CrossingMode.ROTATIVE) {
            switchToFIFOMode();
        }
    }
    
    /**
     * Cambia a modo rotativo
     */
    private void switchToRotativeMode() {
        synchronized (turnLock) {
            currentMode.set(CrossingMode.ROTATIVE);
            
            // Encontrar el primer carril con vehículos esperando en el pare
            for (StartPoint lane : ROTATION_ORDER) {
                if (laneStates.get(lane).vehicleAtStop.get() != null) {
                    currentRotationLane.set(lane);
                    break;
                }
            }
            
            logger.warning("MODO CAMBIADO A ROTATIVO debido a congestión - Iniciando con " + currentRotationLane.get());
        }
    }
    
    /**
     * Cambia a modo FIFO
     */
    private void switchToFIFOMode() {
        synchronized (turnLock) {
            currentMode.set(CrossingMode.FIFO);
            
            // Reconstruir la cola global con todos los vehículos esperando
            globalQueue.clear();
            List<VehicleInfo> allWaiting = new ArrayList<>();
            
            for (LaneState state : laneStates.values()) {
                allWaiting.addAll(state.waitingQueue);
            }
            
            // Ordenar por prioridad y tiempo
            allWaiting.sort((v1, v2) -> {
                // Emergencias primero
                if (v1.type == VehicleType.EMERGENCY && v2.type != VehicleType.EMERGENCY) return -1;
                if (v1.type != VehicleType.EMERGENCY && v2.type == VehicleType.EMERGENCY) return 1;
                
                // Los que están en el pare primero
                if (v1.atStop && !v2.atStop) return -1;
                if (!v1.atStop && v2.atStop) return 1;
                
                // Por tiempo
                if (v1.atStop && v2.atStop) {
                    return Long.compare(v1.stopArrivalTime, v2.stopArrivalTime);
                }
                return Long.compare(v1.arrivalTime, v2.arrivalTime);
            });
            
            globalQueue.addAll(allWaiting);
            
            logger.info("MODO CAMBIADO A FIFO - " + globalQueue.size() + " vehículos en cola global");
        }
    }
    
    /**
     * Avanza la rotación al siguiente carril
     */
    private void advanceRotation() {
        int currentIndex = Arrays.asList(ROTATION_ORDER).indexOf(currentRotationLane.get());
        int attempts = 0;
        
        // Buscar el siguiente carril con vehículos esperando en el pare
        while (attempts < ROTATION_ORDER.length) {
            currentIndex = (currentIndex + 1) % ROTATION_ORDER.length;
            StartPoint nextLane = ROTATION_ORDER[currentIndex];
            
            if (hasVehiclesWaitingAt(nextLane)) {
                currentRotationLane.set(nextLane);
                logger.info("Rotación avanzada a " + currentRotationLane.get());
                return;
            }
            
            attempts++;
        }
        
        // Si no hay más carriles con vehículos, mantener el actual
        logger.info("No hay más carriles con vehículos esperando en el pare");
    }
    
    /**
     * Verifica si hay vehículos esperando en el pare en un carril específico
     */
    private boolean hasVehiclesWaitingAt(StartPoint lane) {
        LaneState state = laneStates.get(lane);
        return state.vehicleAtStop.get() != null;
    }
    
    /**
     * Monitor de congestión que se ejecuta periódicamente
     */
    private void startCongestionMonitor() {
        ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("IntersectionCongestionMonitor");
            return t;
        });
        
        monitor.scheduleAtFixedRate(() -> {
            try {
                checkCongestion();
                
                // Log estado actual
                if (logger.isLoggable(java.util.logging.Level.FINE)) {
                    StringBuilder status = new StringBuilder();
                    status.append("Estado Intersección - Modo: ").append(currentMode.get());
                    status.append(", Carriles con vehículos: ").append(congestedLanes.get());
                    
                    if (currentMode.get() == CrossingMode.ROTATIVE) {
                        status.append(", Turno actual: ").append(currentRotationLane.get());
                    }
                    
                    for (Map.Entry<StartPoint, LaneState> entry : laneStates.entrySet()) {
                        LaneState state = entry.getValue();
                        if (state.hasVehiclesWaiting.get()) {
                            status.append("\n  ").append(entry.getKey())
                                  .append(": ").append(state.vehicleCount.get())
                                  .append(" esperando");
                            if (state.vehicleAtStop.get() != null) {
                                status.append(" (").append(state.vehicleAtStop.get().id).append(" en PARE)");
                            }
                        }
                    }
                    
                    logger.fine(status.toString());
                }
                
            } catch (Exception e) {
                logger.warning("Error en monitor de congestión: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Obtiene el modo actual de operación
     */
    public CrossingMode getCurrentMode() {
        return currentMode.get();
    }
    
    /**
     * Obtiene estadísticas de la intersección
     */
    public IntersectionStats getStats() {
        Map<StartPoint, Integer> waitingByLane = new HashMap<>();
        Map<StartPoint, String> vehiclesAtStop = new HashMap<>();
        int totalWaiting = 0;
        
        for (Map.Entry<StartPoint, LaneState> entry : laneStates.entrySet()) {
            LaneState state = entry.getValue();
            int count = state.vehicleCount.get();
            waitingByLane.put(entry.getKey(), count);
            totalWaiting += count;
            
            VehicleInfo atStop = state.vehicleAtStop.get();
            if (atStop != null) {
                vehiclesAtStop.put(entry.getKey(), atStop.id);
            }
        }
        
        // Verificar si hay emergencia activa para incluir en estadísticas
        if (currentMode.get() == CrossingMode.EMERGENCY && emergencyProtocolActive.get()) {
            return new IntersectionStats(
                currentMode.get(), 
                totalWaiting, 
                waitingByLane, 
                vehiclesAtStop,
                congestedLanes.get(),
                null, // En emergencia, no hay carril rotativo activo
                true,
                emergencyLane.get(),
                currentEmergencyVehicleId.get()
            );
        } else {
            return new IntersectionStats(
                currentMode.get(), 
                totalWaiting, 
                waitingByLane, 
                vehiclesAtStop,
                congestedLanes.get(),
                currentMode.get() == CrossingMode.ROTATIVE ? currentRotationLane.get() : null
            );
        }
    }
    
    /**
     * Clase para estadísticas de la intersección
     */
    public static class IntersectionStats {
        public final CrossingMode mode;
        public final int totalWaiting;
        public final Map<StartPoint, Integer> waitingByLane;
        public final Map<StartPoint, String> vehiclesAtStop;
        public final int congestedLanes;
        public final StartPoint currentRotationLane;
        public final boolean emergencyActive;
        public final StartPoint emergencyLane;
        public final String emergencyVehicleId;
        
        IntersectionStats(CrossingMode mode, int totalWaiting, 
                         Map<StartPoint, Integer> waitingByLane,
                         Map<StartPoint, String> vehiclesAtStop,
                         int congestedLanes, StartPoint currentRotationLane) {
            this(mode, totalWaiting, waitingByLane, vehiclesAtStop, 
                congestedLanes, currentRotationLane, false, null, null);
        }
        
        IntersectionStats(CrossingMode mode, int totalWaiting, 
                         Map<StartPoint, Integer> waitingByLane,
                         Map<StartPoint, String> vehiclesAtStop,
                         int congestedLanes, StartPoint currentRotationLane,
                         boolean emergencyActive, StartPoint emergencyLane,
                         String emergencyVehicleId) {
            this.mode = mode;
            this.totalWaiting = totalWaiting;
            this.waitingByLane = Collections.unmodifiableMap(waitingByLane);
            this.vehiclesAtStop = Collections.unmodifiableMap(vehiclesAtStop);
            this.congestedLanes = congestedLanes;
            this.currentRotationLane = currentRotationLane;
            this.emergencyActive = emergencyActive;
            this.emergencyLane = emergencyLane;
            this.emergencyVehicleId = emergencyVehicleId;
        }
    }
}