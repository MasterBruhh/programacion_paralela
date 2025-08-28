package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.core.*;
import edu.pucmm.trafico.model.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.shape.Circle;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.util.Duration;
import javafx.scene.shape.CubicCurveTo;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Tarea del vehículo con respeto a semáforos y salida con animación.
 * Incluye lógica completa de prioridad para vehículos de emergencia.
 */
public class VehicleTask implements Runnable {
    private static final Logger logger = Logger.getLogger(VehicleTask.class.getName());

    private enum State {
        SPAWNING,
        APPROACHING,
        WAITING_AT_LIGHT,
        CROSSING,
        POST_TURN,
        EXITING,
        YIELDING_TO_EMERGENCY  // Nuevo estado para ceder paso a emergencia
    }

    private static final double HIGHWAY_SPEED = 5.0;
    private static final double STREET_SPEED = 3.0;
    private static final double STOP_DISTANCE = 50.0;
    private static final double HIGHWAY_INTERSECTION_BUFFER = 100.0;
    private static final int[] VALID_INTERSECTIONS = {70, 390, 690, 1010};
    private static final double POST_TURN_DISTANCE = 250.0;
    private static final double STREET_LANE_CENTER_WEST = 20.0;
    private static final double STREET_LANE_CENTER_EAST = 60.0;
    private static final double EMERGENCY_DETECTION_RADIUS = 300.0; // Radio de detección de emergencias

    private final Vehicle vehicle;
    private final VehicleLifecycleManager lifecycleManager;
    private final AtomicBoolean active;
    private final CollisionDetector collisionDetector;
    private final Random random = new Random();

    private volatile double postTurnTargetX;
    private volatile double postTurnTargetY;
    private volatile double postTurnSpeed = 3.0;
    
    private volatile double savedX; // Para guardar posición al ceder paso
    private volatile double savedY;

    private int stuckCounter = 0;
    private static final int MAX_STUCK_COUNT = 20;

    private volatile State currentState = State.SPAWNING;
    private volatile State previousState = null; // Para recordar estado antes de ceder paso
    private volatile boolean isAnimating = false;
    private volatile boolean hasCrossedLastLight = false;

    public VehicleTask(Vehicle vehicle, VehicleLifecycleManager lifecycleManager) {
        this.vehicle = vehicle;
        this.lifecycleManager = lifecycleManager;
        this.active = new AtomicBoolean(true);
        this.collisionDetector = new CollisionDetector();
    }

    @Override
    public void run() {
        try {
            performSpawnAnimation();
            currentState = State.APPROACHING;

            while (active.get() && vehicle.isActive()) {
                // Verificar emergencias constantemente
                if (vehicle.getType() != VehicleType.EMERGENCY) {
                    checkForEmergencyVehicles();
                }

                switch (currentState) {
                    case APPROACHING -> handleApproaching();
                    case WAITING_AT_LIGHT -> handleWaitingAtLight();
                    case CROSSING -> { Thread.sleep(50); }
                    case POST_TURN -> handlePostTurnMovement();
                    case YIELDING_TO_EMERGENCY -> handleYieldingToEmergency();
                    case EXITING -> { handleExit(); return; }
                    default -> Thread.sleep(50);
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Vehículo " + vehicle.getId() + " interrumpido");
        }
    }

    /**
     * Verifica si hay vehículos de emergencia cerca y cede el paso si es necesario
     */
    private void checkForEmergencyVehicles() {
        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        
        for (Vehicle other : allVehicles) {
            if (other.getType() != VehicleType.EMERGENCY || !other.isActive()) continue;
            
            double distance = collisionDetector.calculateDistance(
                vehicle.getX(), vehicle.getY(), other.getX(), other.getY()
            );
            
            // Si hay una emergencia cerca
            if (distance < EMERGENCY_DETECTION_RADIUS) {
                // Verificar si están en la misma trayectoria o carril
                if (areInSamePath(other) || willPathsCross(other)) {
                    // Verificar si la emergencia está detrás o se aproxima
                    if (isEmergencyApproaching(other)) {
                        // Ceder el paso
                        if (currentState != State.YIELDING_TO_EMERGENCY) {
                            logger.info("Vehículo " + vehicle.getId() + 
                                      " cediendo paso a emergencia " + other.getId());
                            previousState = currentState;
                            currentState = State.YIELDING_TO_EMERGENCY;
                            vehicle.setEmergencyYielding(true);
                            
                            // Si el vehículo de emergencia está en el mismo carril de autopista
                            if (vehicle.isHighwayVehicle() && other.isHighwayVehicle()) {
                                if (vehicle.getCurrentLane() == other.getCurrentLane() &&
                                    vehicle.getHighwayLane().isWestbound() == other.getHighwayLane().isWestbound()) {
                                    // Notificar al lifecycle manager para protocolo de emergencia
                                    String group = vehicle.getHighwayLane().isWestbound() ? "ARRIBA" : "ABAJO";
                                    lifecycleManager.activateEmergencyProtocol(group);
                                }
                            }
                        }
                        return;
                    }
                }
            }
        }
        
        // Si estábamos cediendo pero ya no hay emergencias cerca, volver al estado anterior
        if (currentState == State.YIELDING_TO_EMERGENCY && !hasEmergencyNearby()) {
            logger.info("Vehículo " + vehicle.getId() + " reanudando tras emergencia");
            vehicle.setEmergencyYielding(false);
            currentState = previousState != null ? previousState : State.APPROACHING;
            previousState = null;
            
            // Desactivar protocolo de emergencia si aplica
            if (vehicle.isHighwayVehicle()) {
                lifecycleManager.deactivateEmergencyProtocol();
            }
        }
    }

    /**
     * Verifica si el vehículo está en la misma trayectoria que la emergencia
     */
    private boolean areInSamePath(Vehicle emergency) {
        // Ambos en autopista
        if (vehicle.isHighwayVehicle() && emergency.isHighwayVehicle()) {
            // Mismo carril y misma dirección
            return vehicle.getCurrentLane() == emergency.getCurrentLane() &&
                   vehicle.getHighwayLane().isWestbound() == emergency.getHighwayLane().isWestbound();
        }
        
        // Ambos en calle
        if (!vehicle.isHighwayVehicle() && !emergency.isHighwayVehicle()) {
            // Mismo punto de inicio
            StartPoint vsp = vehicle.getStartPoint();
            StartPoint esp = emergency.getStartPoint();
            
            // Considerar calles del mismo tipo (NORTH_L y NORTH_D son paralelas pero diferentes)
            if (vsp == esp) return true;
            
            // Verificar si están en calles que se cruzan
            boolean vIsNorthSouth = (vsp == StartPoint.NORTH_L || vsp == StartPoint.NORTH_D ||
                                    vsp == StartPoint.SOUTH_L || vsp == StartPoint.SOUTH_D);
            boolean eIsNorthSouth = (esp == StartPoint.NORTH_L || esp == StartPoint.NORTH_D ||
                                    esp == StartPoint.SOUTH_L || esp == StartPoint.SOUTH_D);
            
            return vIsNorthSouth == eIsNorthSouth;
        }
        
        return false;
    }

    /**
     * Verifica si las trayectorias se cruzarán
     */
    private boolean willPathsCross(Vehicle emergency) {
        // Si uno está en autopista y otro en calle, verificar intersección
        if (vehicle.isHighwayVehicle() != emergency.isHighwayVehicle()) {
            // Verificar si están cerca de una intersección
            for (int x : VALID_INTERSECTIONS) {
                double distToIntersection = Math.abs(vehicle.getX() - x);
                double emergDistToIntersection = Math.abs(emergency.getX() - x);
                
                if (distToIntersection < 150 && emergDistToIntersection < 150) {
                    return true; // Ambos cerca de la misma intersección
                }
            }
        }
        
        return false;
    }

    /**
     * Verifica si la emergencia se está aproximando
     */
    private boolean isEmergencyApproaching(Vehicle emergency) {
        if (vehicle.isHighwayVehicle() && emergency.isHighwayVehicle()) {
            boolean westbound = vehicle.getHighwayLane().isWestbound();
            if (westbound) {
                // Va hacia el oeste, emergencia está detrás si X es mayor
                return emergency.getX() > vehicle.getX();
            } else {
                // Va hacia el este, emergencia está detrás si X es menor
                return emergency.getX() < vehicle.getX();
            }
        }
        
        if (!vehicle.isHighwayVehicle() && !emergency.isHighwayVehicle()) {
            StartPoint sp = vehicle.getStartPoint();
            
            // Verificar según dirección
            return switch (sp) {
                case NORTH_L, NORTH_D -> emergency.getY() < vehicle.getY();
                case SOUTH_L, SOUTH_D -> emergency.getY() > vehicle.getY();
                case EAST -> emergency.getX() > vehicle.getX();
                case WEST -> emergency.getX() < vehicle.getX();
            };
        }
        
        return false;
    }

    /**
     * Verifica si hay emergencias cerca
     */
    private boolean hasEmergencyNearby() {
        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        
        for (Vehicle other : allVehicles) {
            if (other.getType() != VehicleType.EMERGENCY || !other.isActive()) continue;
            
            double distance = collisionDetector.calculateDistance(
                vehicle.getX(), vehicle.getY(), other.getX(), other.getY()
            );
            
            if (distance < EMERGENCY_DETECTION_RADIUS * 1.5) { // Margen adicional
                if (areInSamePath(other) || willPathsCross(other)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Maneja el estado de ceder paso a emergencia
     */
    private void handleYieldingToEmergency() {
        // Si es autopista, moverse al lado si es posible
        if (vehicle.isHighwayVehicle()) {
            // Intentar cambiar de carril si no lo ha hecho
            if (!vehicle.isChangingLane() && vehicle.getCurrentLane() != 3) {
                // Cambiar al carril derecho si es posible
                int targetLane = Math.min(vehicle.getCurrentLane() + 1, 3);
                if (canChangeLane(targetLane)) {
                    vehicle.startLaneChange(targetLane);
                    performLaneChange(targetLane);
                    return;
                }
            }
            
            // Si no puede cambiar de carril, reducir velocidad al mínimo
            vehicle.setSpeed(HIGHWAY_SPEED * 0.2);
            
            // Si está en un semáforo, esperar aunque esté verde
            return;
        }
        
        // Para vehículos de calle, detenerse completamente
        vehicle.setSpeed(0);
        
        // Mantener posición actual
        updateVehiclePosition(vehicle.getX(), vehicle.getY());
    }

    /**
     * Verifica si puede cambiar de carril
     */
    private boolean canChangeLane(int targetLane) {
        if (!vehicle.isHighwayVehicle() || targetLane < 1 || targetLane > 3) {
            return false;
        }
        
        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        double laneChangeY = vehicle.getHighwayLane().getTargetLane(targetLane).getStartY();
        
        // Verificar si hay espacio en el carril objetivo
        for (Vehicle other : allVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;
            if (!other.isHighwayVehicle()) continue;
            
            // Si está en el carril objetivo
            if (Math.abs(other.getY() - laneChangeY) < 20) {
                double distance = Math.abs(other.getX() - vehicle.getX());
                if (distance < 100) { // No hay suficiente espacio
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Realiza un cambio de carril
     */
    private void performLaneChange(int targetLane) {
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;
            
            double targetY = vehicle.getHighwayLane().getTargetLane(targetLane).getStartY();
            
            TranslateTransition laneChange = new TranslateTransition(Duration.seconds(1), node);
            laneChange.setToY(targetY - vehicle.getY());
            laneChange.setOnFinished(e -> {
                vehicle.updatePosition(vehicle.getX(), targetY);
                vehicle.completeLaneChange();
            });
            laneChange.play();
        });
    }

    private void handlePostTurnMovement() {
        // Si es emergencia, ignorar semáforos en post-turn también
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            // Mover directamente sin verificar semáforos
            moveTowardsTarget();
            return;
        }
        
        // Calcular dirección hacia el punto objetivo
        double dx = postTurnTargetX - vehicle.getX();
        double dy = postTurnTargetY - vehicle.getY();
        double distance = Math.hypot(dx, dy);

        if (distance < 5.0) {
            currentState = State.EXITING;
            return;
        }

        // Para vehículos normales, respetar semáforos como antes
        if (!vehicle.isHighwayVehicle()) {
            AvenueContext avenue = detectAvenueContext(vehicle.getY());
            if (avenue != null) {
                boolean westbound = avenue.isUpper;
                String group = westbound ? "ARRIBA" : "ABAJO";
                TrafficLightState light = lifecycleManager.getTrafficLightState(group);

                for (int i = 0; i < VALID_INTERSECTIONS.length; i++) {
                    int sx = VALID_INTERSECTIONS[i];
                    double leftEdge = sx;
                    double rightEdge = sx + 80;
                    double stopX = westbound ? leftEdge + 95 : rightEdge - 95;

                    double currX = vehicle.getX();
                    double nextX = currX + (dx / distance) * Math.min(postTurnSpeed, distance);

                    boolean approaching = westbound ? 
                            (currX > stopX && nextX <= stopX + STOP_DISTANCE) :
                            (currX < stopX && nextX >= stopX - STOP_DISTANCE);
                            
                    if (!approaching) continue;

                    boolean isSecondIntersection = (i == 1);
                    boolean allowRightOnRed = !isSecondIntersection && 
                                             vehicle.getDirection() == Direction.RIGHT &&
                                             light == TrafficLightState.RED &&
                                             isSafeToRightTurnOnRed(sx);
                                             
                    boolean allowed = (light == TrafficLightState.GREEN) ||
                            (light == TrafficLightState.YELLOW && vehicle.getType() == VehicleType.EMERGENCY) ||
                            allowRightOnRed;

                    if (!allowed) {
                        updateVehiclePosition(stopX, vehicle.getY());
                        vehicle.setWaitingAtLight(true);
                        return;
                    }
                }
                vehicle.setWaitingAtLight(false);
            }
        }

        moveTowardsTarget();
    }

    private void moveTowardsTarget() {
        double dx = postTurnTargetX - vehicle.getX();
        double dy = postTurnTargetY - vehicle.getY();
        double distance = Math.hypot(dx, dy);
        
        double step = Math.min(postTurnSpeed, distance);
        double newX = vehicle.getX() + (dx / distance) * step;
        double newY = vehicle.getY() + (dy / distance) * step;

        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        
        // Si es emergencia, ignorar colisiones parcialmente
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            // Solo verificar colisiones críticas
            for (Vehicle other : allVehicles) {
                if (other.getId() == vehicle.getId() || !other.isActive()) continue;
                double dist = collisionDetector.calculateDistance(newX, newY, other.getX(), other.getY());
                if (dist < 20) { // Solo evitar colisiones muy cercanas
                    return;
                }
            }
            updateVehiclePosition(newX, newY);
            return;
        }

        // Para vehículos normales, verificación completa
        if (!collisionDetector.canMove(vehicle, newX, newY, allVehicles)) {
            stuckCounter++;

            if (stuckCounter > MAX_STUCK_COUNT) {
                double jitterX = (random.nextDouble() - 0.5) * 3.0;
                double jitterY = (random.nextDouble() - 0.5) * 3.0;
                updateVehiclePosition(vehicle.getX() + jitterX, vehicle.getY() + jitterY);
                stuckCounter = 0;
                return;
            }

            step *= 0.5;
            newX = vehicle.getX() + (dx / distance) * step;
            newY = vehicle.getY() + (dy / distance) * step;

            if (!collisionDetector.canMove(vehicle, newX, newY, allVehicles)) {
                return;
            }
        }

        stuckCounter = 0;
        updateVehiclePosition(newX, newY);
    }

    private void performSpawnAnimation() throws InterruptedException {
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node != null) {
                node.setCenterX(vehicle.getX());
                node.setCenterY(vehicle.getY());
                FadeTransition fadeIn = new FadeTransition(Duration.seconds(0.5), node);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.setOnFinished(e -> isAnimating = false);
                isAnimating = true;
                fadeIn.play();
            }
        });
        while (isAnimating && active.get()) Thread.sleep(30);
    }

    private void handleApproaching() {
        if (vehicle.isHighwayVehicle()) {
            moveHighwayVehicle();
        } else {
            moveStreetVehicleTowardsStop();
        }
    }

    private void moveStreetVehicleTowardsStop() {
        double[] stop = vehicle.getStartPoint().getStopCoordinates();
        double dx = stop[0] - vehicle.getX();
        double dy = stop[1] - vehicle.getY();
        double dist = Math.hypot(dx, dy);

        if (dist < 3) {
            vehicle.setWaitingAtLight(true);
            currentState = State.WAITING_AT_LIGHT;
            return;
        }

        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        
        CollisionDetector.VehicleAheadInfo ahead = collisionDetector.detectVehicleAhead(vehicle, allVehicles);
        if (ahead != null && ahead.getDistance() < 60) {
            if (ahead.getVehicle().isWaitingAtLight()) {
                stuckCounter = 0;
                return;
            }
            
            double slowFactor = Math.max(0.3, ahead.getDistance() / 60.0);
            double slowStep = Math.min(STREET_SPEED * slowFactor, dist);
            double nx = vehicle.getX() + (dx / dist) * slowStep;
            double ny = vehicle.getY() + (dy / dist) * slowStep;
            
            if (collisionDetector.canMove(vehicle, nx, ny, allVehicles)) {
                updateVehiclePosition(nx, ny);
                stuckCounter = 0;
                return;
            }
        }
        
        double step = Math.min(STREET_SPEED, dist);
        double nx = vehicle.getX() + (dx / dist) * step;
        double ny = vehicle.getY() + (dy / dist) * step;
        
        if (!collisionDetector.canMove(vehicle, nx, ny, allVehicles)) {
            stuckCounter++;

            if (stuckCounter > MAX_STUCK_COUNT) {
                double jitterX = (random.nextDouble() - 0.5) * 2.0;
                double jitterY = (random.nextDouble() - 0.5) * 2.0;
                updateVehiclePosition(vehicle.getX() + jitterX, vehicle.getY() + jitterY);
                stuckCounter = 0;
                return;
            }

            step = step * 0.5;
            nx = vehicle.getX() + (dx / dist) * step;
            ny = vehicle.getY() + (dy / dist) * step;

            if (!collisionDetector.canMove(vehicle, nx, ny, allVehicles)) {
                return;
            }
        }

        stuckCounter = 0;
        updateVehiclePosition(nx, ny);
    }

    private void handleWaitingAtLight() throws InterruptedException {
        String group = getTrafficLightGroup();
        TrafficLightState light = lifecycleManager.getTrafficLightState(group);

        // EMERGENCIAS SIEMPRE PUEDEN PASAR
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            logger.info("Vehículo de emergencia " + vehicle.getId() + " ignorando semáforo en " + group);
            
            IntersectionSemaphore sem = lifecycleManager.getIntersectionSemaphore();
            boolean acquired = sem != null && sem.tryAcquire(vehicle, 100, TimeUnit.MILLISECONDS);
            if (acquired) {
                vehicle.setInIntersection(true);
                vehicle.setWaitingAtLight(false);
                currentState = State.CROSSING;
                
                // Notificar protocolo de emergencia
                lifecycleManager.activateEmergencyProtocol(group);
                
                if (vehicle.isHighwayVehicle()) {
                    performHighwayTurn();
                } else {
                    performCrossing();
                }
                return;
            }
            Thread.sleep(50); // Reintentar rápidamente
            return;
        }

        // Para vehículos normales, lógica estándar
        boolean canCross;
        if (vehicle.isHighwayVehicle()) {
            canCross = (light == TrafficLightState.GREEN);

            if (!canCross && vehicle.getDirection() == Direction.RIGHT && light == TrafficLightState.RED) {
                int idx = Math.min(Math.max(vehicle.getTargetIntersection(), 0), VALID_INTERSECTIONS.length - 1);
                if (idx == 0) {
                    int targetX = VALID_INTERSECTIONS[idx];
                    if (isSafeToRightTurnOnRed(targetX)) {
                        IntersectionSemaphore sem = lifecycleManager.getIntersectionSemaphore();
                        boolean acquired = sem != null && sem.tryAcquire(vehicle, 100, TimeUnit.MILLISECONDS);
                        if (acquired) {
                            vehicle.setInIntersection(true);
                            vehicle.setWaitingAtLight(false);
                            currentState = State.CROSSING;
                            performHighwayTurn();
                            return;
                        }
                    }
                }
            }
            
            if (canCross) {
                vehicle.setWaitingAtLight(false);
                currentState = State.APPROACHING;
            } else {
                Thread.sleep(100);
            }
            return;
        } else {
            canCross = (light == TrafficLightState.GREEN);

            if (!canCross && vehicle.getDirection() == Direction.RIGHT && light == TrafficLightState.RED) {
                StartPoint sp = vehicle.getStartPoint();
                int streetIntersectionX = getIntersectionXForStreet(sp);
                boolean isFirstIntersection = isFirstIntersectionForStreetVehicle(sp, streetIntersectionX);
                
                if (isFirstIntersection && isSafeToRightTurnOnRed(streetIntersectionX)) {
                    IntersectionSemaphore sem = lifecycleManager.getIntersectionSemaphore();
                    boolean acquired = sem != null && sem.tryAcquire(vehicle, 100, TimeUnit.MILLISECONDS);
                    if (acquired) {
                        vehicle.setInIntersection(true);
                        vehicle.setWaitingAtLight(false);
                        currentState = State.CROSSING;
                        performCrossing();
                        return;
                    }
                }
            }
        }

        if (canCross) {
            Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
            boolean hasSpace = true;

            for (Vehicle other : allVehicles) {
                if (other.getId() == vehicle.getId() || !other.isActive()) continue;
                if (other.isInIntersection()) {
                    double distance = collisionDetector.calculateDistance(
                            vehicle.getX(), vehicle.getY(), other.getX(), other.getY());
                    if (distance < 60) {
                        hasSpace = false;
                        break;
                    }
                }
            }

            if (!hasSpace) {
                Thread.sleep(100);
                return;
            }

            IntersectionSemaphore sem = lifecycleManager.getIntersectionSemaphore();
            boolean acquired = sem != null && sem.tryAcquire(vehicle, 100, TimeUnit.MILLISECONDS);
            if (acquired) {
                vehicle.setInIntersection(true);
                vehicle.setWaitingAtLight(false);
                currentState = State.CROSSING;
                performCrossing();
            } else {
                Thread.sleep(100);
            }
        } else {
            Thread.sleep(120);
        }
    }

    private void performCrossing() {
        vehicle.setWaitingAtLight(false);
        
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;

            double startX = node.getCenterX();
            double startY = node.getCenterY();
            double[] exit = vehicle.getStartPoint().getExitCoordinates(vehicle.getDirection());

            javafx.scene.shape.Path path = buildCrossingPath(
                    vehicle.getStartPoint(),
                    vehicle.getDirection(),
                    startX, startY,
                    exit[0], exit[1]
            );

            // Velocidad más rápida para emergencias
            double duration = vehicle.getType() == VehicleType.EMERGENCY ? 1.5 : 2.5;
            
            PathTransition t = new PathTransition(Duration.seconds(duration), path, node);
            t.setInterpolator(Interpolator.EASE_BOTH);
            t.currentTimeProperty().addListener((obs, ot, nt) -> {
                double x = node.getCenterX() + node.getTranslateX();
                double y = node.getCenterY() + node.getTranslateY();
                vehicle.updatePosition(x, y);
            });
            t.setOnFinished(e -> {
                vehicle.setInIntersection(false);
                IntersectionSemaphore sem = lifecycleManager.getIntersectionSemaphore();
                if (sem != null) sem.release();
                
                // Si era emergencia, desactivar protocolo
                if (vehicle.getType() == VehicleType.EMERGENCY) {
                    lifecycleManager.deactivateEmergencyProtocol();
                }

                double dx = exit[0] - startX;
                double dy = exit[1] - startY;
                double length = Math.hypot(dx, dy);

                if (length > 0) {
                    dx = dx / length;
                    dy = dy / length;
                    
                    AvenueContext avenue = detectAvenueContext(exit[1]);
                    if (avenue != null && vehicle.getDirection() == Direction.RIGHT) {
                        double laneY;
                        if (avenue.isUpper) {
                            laneY = 293.33;
                        } else {
                            laneY = 393.33;
                        }
                        
                        postTurnTargetX = exit[0] + dx * POST_TURN_DISTANCE;
                        postTurnTargetY = laneY;
                    } else if (avenue != null && vehicle.getDirection() == Direction.LEFT) {
                        double laneY;
                        if (avenue.isUpper) {
                            laneY = 226.67;
                        } else {
                            laneY = 326.67;
                        }
                        
                        postTurnTargetX = exit[0] + dx * POST_TURN_DISTANCE;
                        postTurnTargetY = laneY;
                    } else {
                        postTurnTargetX = exit[0] + dx * POST_TURN_DISTANCE;
                        postTurnTargetY = exit[1] + dy * POST_TURN_DISTANCE;
                    }
                    
                    currentState = State.POST_TURN;
                } else {
                    currentState = State.EXITING;
                }
            });
            t.play();
        });
    }

    private javafx.scene.shape.Path buildCrossingPath(StartPoint sp, Direction dir,
                                                      double startX, double startY,
                                                      double endX, double endY) {
        javafx.scene.shape.Path path = new javafx.scene.shape.Path();
        path.getElements().add(new javafx.scene.shape.MoveTo(startX, startY));

        if (dir == Direction.STRAIGHT) {
            path.getElements().add(new javafx.scene.shape.LineTo(endX, endY));
            return path;
        }

        double[] vin = incomingUnitVector(sp);
        double[] vout = outgoingUnitVector(sp, dir);

        double radius = 60.0;

        double cp1x = startX + vin[0] * radius;
        double cp1y = startY + vin[1] * radius;
        double cp2x = endX   - vout[0] * radius;
        double cp2y = endY   - vout[1] * radius;

        if (dir == Direction.U_TURN) {
            double[] n = new double[]{ -vin[1], vin[0] };
            double bump = radius * 1.5;
            cp1x += n[0] * bump;
            cp1y += n[1] * bump;
            cp2x -= n[0] * bump;
            cp2y -= n[1] * bump;
        }

        path.getElements().add(new javafx.scene.shape.CubicCurveTo(
                cp1x, cp1y, cp2x, cp2y, endX, endY
        ));
        return path;
    }

    private double[] incomingUnitVector(StartPoint sp) {
        return switch (sp) {
            case NORTH_L, NORTH_D -> new double[]{ 0.0,  1.0 };
            case SOUTH_L, SOUTH_D -> new double[]{ 0.0, -1.0 };
            case EAST               -> new double[]{ -1.0, 0.0 };
            case WEST               -> new double[]{ 1.0,  0.0 };
        };
    }

    private double[] outgoingUnitVector(StartPoint sp, Direction dir) {
        double[] vin = incomingUnitVector(sp);

        return switch (dir) {
            case STRAIGHT -> vin;
            case U_TURN   -> new double[]{ -vin[0], -vin[1] };
            case LEFT     -> rotateLeft(vin);
            case RIGHT    -> rotateRight(vin);
        };
    }

    private double[] rotateLeft(double[] v) {
        return new double[]{ -v[1], v[0] };
    }

    private double[] rotateRight(double[] v) {
        return new double[]{ v[1], -v[0] };
    }

    private void handleExit() {
        active.set(false);
        Platform.runLater(() -> lifecycleManager.removeVehicle(vehicle));
    }

    private void updateVehiclePosition(double x, double y) {
        vehicle.updatePosition(x, y);
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node != null) {
                node.setCenterX(x);
                node.setCenterY(y);
            }
        });
    }

    private String getTrafficLightGroup() {
        if (vehicle.isHighwayVehicle()) {
            boolean westbound = vehicle.getHighwayLane().isWestbound();

            if (vehicle.getTargetIntersection() >= 0 && vehicle.getDirection() != Direction.STRAIGHT) {
                int idx = Math.min(vehicle.getTargetIntersection(), VALID_INTERSECTIONS.length - 1);

                return switch (idx) {
                    case 0 -> westbound ? "ARRIBA" : "ABAJO";
                    case 1 -> westbound ? "ARRIBA" : "ABAJO";
                    case 2 -> westbound ? "ARRIBA" : "ABAJO";
                    case 3 -> westbound ? "ARRIBA" : "ABAJO";
                    default -> westbound ? "ARRIBA" : "ABAJO";
                };
            }

            return westbound ? "ARRIBA" : "ABAJO";
        } else {
            StartPoint sp = vehicle.getStartPoint();
            return switch (sp) {
                case NORTH_L, NORTH_D -> "CalleIzq";
                case SOUTH_L, SOUTH_D -> "CalleDer";
                case EAST, WEST -> "CalleDer";
            };
        }
    }

    private void moveHighwayVehicle() {
        double currentX = vehicle.getX();
        double y = vehicle.getY();
        boolean westbound = vehicle.getHighwayLane().isWestbound();
        
        // Velocidad aumentada para emergencias
        double speed = vehicle.getType() == VehicleType.EMERGENCY ? 
                      HIGHWAY_SPEED * 1.5 : HIGHWAY_SPEED;
        
        double newX = westbound ? currentX - speed : currentX + speed;

        if (newX < -20 || newX > 1180) {
            currentState = State.EXITING;
            return;
        }

        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        
        // Emergencias no verifican colisiones de la misma manera
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            // Solo evitar colisiones críticas
            for (Vehicle other : allVehicles) {
                if (other.getId() == vehicle.getId() || !other.isActive()) continue;
                double dist = collisionDetector.calculateDistance(newX, y, other.getX(), other.getY());
                if (dist < 15) { // Solo colisiones muy cercanas
                    return; // No moverse si hay riesgo de colisión directa
                }
            }
            updateVehiclePosition(newX, y);
            checkForTurns(currentX, newX, westbound);
            return;
        }
        
        // Vehículos normales verifican colisiones completas
        CollisionDetector.VehicleAheadInfo ahead = collisionDetector.detectVehicleAhead(vehicle, allVehicles);
        if (ahead != null && ahead.getDistance() < 80) {
            if (ahead.getVehicle().isWaitingAtLight()) {
                stuckCounter = 0;
                return;
            }
            
            double slowFactor = Math.max(0.3, ahead.getDistance() / 80.0);
            newX = westbound ?
                   currentX - (HIGHWAY_SPEED * slowFactor) :
                   currentX + (HIGHWAY_SPEED * slowFactor);
        }
        
        if (!collisionDetector.canMove(vehicle, newX, y, allVehicles)) {
            stuckCounter++;

            if (stuckCounter > MAX_STUCK_COUNT) {
                double jitterX = (random.nextDouble() - 0.5) * 2.0;
                double jitterY = (random.nextDouble() - 0.5) * 2.0;
                updateVehiclePosition(vehicle.getX() + jitterX, vehicle.getY() + jitterY);
                stuckCounter = 0;
                return;
            }

            double halfSpeed = HIGHWAY_SPEED * 0.5;
            double saferX = westbound ? currentX - halfSpeed : currentX + halfSpeed;

            if (collisionDetector.canMove(vehicle, saferX, y, allVehicles)) {
                newX = saferX;
            } else {
                return;
            }
        }

        stuckCounter = 0;
        updateVehiclePosition(newX, y);
        checkForTurns(currentX, newX, westbound);
    }

    private void checkForTurns(double currentX, double newX, boolean westbound) {
        if (vehicle.getTargetIntersection() >= 0 && vehicle.getDirection() != Direction.STRAIGHT) {
            int targetIdx = Math.min(vehicle.getTargetIntersection(), VALID_INTERSECTIONS.length - 1);
            int targetX = VALID_INTERSECTIONS[targetIdx];

            boolean isInTurnZone = westbound ?
                    (currentX > targetX && currentX < targetX + 90) :
                    (currentX < targetX + 80 && currentX > targetX - 10);

            if (isInTurnZone) {
                // Emergencias siempre pueden girar
                if (vehicle.getType() == VehicleType.EMERGENCY) {
                    vehicle.setWaitingAtLight(false);
                    vehicle.setInIntersection(true);
                    performHighwayTurn();
                    return;
                }

                String group = getTrafficLightGroup();
                TrafficLightState light = lifecycleManager.getTrafficLightState(group);

                boolean canTurn = (light == TrafficLightState.GREEN);

                boolean isRightTurnOnRedAllowedHere =
                        vehicle.getDirection() == Direction.RIGHT && targetIdx == 0 && light == TrafficLightState.RED;

                if (canTurn || (isRightTurnOnRedAllowedHere && isSafeToRightTurnOnRed(targetX))) {
                    vehicle.setWaitingAtLight(false);
                    vehicle.setInIntersection(true);
                    performHighwayTurn();
                    return;
                } else {
                    double stopX = westbound ? targetX + 80 : targetX;
                    updateVehiclePosition(stopX, vehicle.getY());
                    vehicle.setWaitingAtLight(true);
                    currentState = State.WAITING_AT_LIGHT;
                    return;
                }
            }
        }

        if (hasCrossedLastLight) {
            return;
        }

        // Emergencias ignoran semáforos rectos
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            return;
        }

        String group = getTrafficLightGroup();
        TrafficLightState light = lifecycleManager.getTrafficLightState(group);

        for (int i = 0; i < VALID_INTERSECTIONS.length; i++) {
            int sx = VALID_INTERSECTIONS[i];
            double leftEdge = sx;
            double rightEdge = sx + 80;
            
            double stopX;
            if (westbound) {
                stopX = leftEdge + 95;
            } else {
                stopX = rightEdge - 95;
            }
            
            boolean approachingThis = westbound ?
                    (currentX > stopX && newX <= stopX + STOP_DISTANCE + 20) :
                    (currentX < stopX && newX >= stopX - STOP_DISTANCE - 20);
            
            if (!approachingThis) continue;

            boolean allowed = (light == TrafficLightState.GREEN);
            
            if (!allowed) {
                boolean isApproachingStopLine = westbound ?
                    (currentX > stopX && newX <= stopX) :
                    (currentX < stopX && newX >= stopX);

                if (isApproachingStopLine) {
                    updateVehiclePosition(stopX, vehicle.getY());
                    vehicle.setWaitingAtLight(true);
                    currentState = State.WAITING_AT_LIGHT;
                    return;
                }
                
                boolean betweenStopLineAndIntersection = westbound ?
                    (currentX <= stopX && currentX >= leftEdge) :
                    (currentX >= stopX && currentX <= rightEdge);
                
                if (betweenStopLineAndIntersection) {
                    updateVehiclePosition(stopX, vehicle.getY());
                    vehicle.setWaitingAtLight(true);
                    currentState = State.WAITING_AT_LIGHT;
                    return;
                }
                
                if ((westbound && newX > stopX) || (!westbound && newX < stopX)) {
                    return;
                }
                
                updateVehiclePosition(stopX, vehicle.getY());
                vehicle.setWaitingAtLight(true);
                currentState = State.WAITING_AT_LIGHT;
                return;
            }
            
            if (i == VALID_INTERSECTIONS.length - 1) {
                boolean pastIntersection = westbound ?
                    (currentX < leftEdge - HIGHWAY_INTERSECTION_BUFFER) :
                    (currentX > rightEdge + HIGHWAY_INTERSECTION_BUFFER);
                    
                if (pastIntersection) {
                    hasCrossedLastLight = true;
                }
            }
            
            break;
        }
    }

    private void performHighwayTurn() {
        vehicle.setWaitingAtLight(false);
        
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;

            double startX = node.getCenterX();
            double startY = node.getCenterY();

            double endX, endY;
            boolean westbound = vehicle.getHighwayLane().isWestbound();
            int targetIdx = Math.min(vehicle.getTargetIntersection(), VALID_INTERSECTIONS.length - 1);
            int intersectionX = VALID_INTERSECTIONS[targetIdx];

            switch (vehicle.getDirection()) {
                case LEFT:
                    if (westbound) {
                        endX = intersectionX + STREET_LANE_CENTER_WEST;
                        endY = 500;
                    } else {
                        endX = intersectionX + STREET_LANE_CENTER_EAST;
                        endY = 100;
                    }
                    break;
                case RIGHT:
                    if (westbound) {
                        endX = intersectionX + STREET_LANE_CENTER_EAST;
                        endY = 100;
                    } else {
                        endX = intersectionX + STREET_LANE_CENTER_WEST;
                        endY = 500;
                    }
                    break;
                case U_TURN:
                    endX = intersectionX + (westbound ? 120 : -120);
                    endY = westbound ? 360 : 260;
                    break;
                default:
                    endX = startX + 100;
                    endY = startY;
            }

            Path path = new Path();
            path.getElements().add(new MoveTo(startX, startY));

            double controlX1, controlY1, controlX2, controlY2;

            if (vehicle.getDirection() == Direction.LEFT) {
                controlX1 = startX + (westbound ? -40 : 40);
                controlY1 = startY;
                controlX2 = endX;
                controlY2 = endY - (westbound ? 60 : -60);
            } else if (vehicle.getDirection() == Direction.RIGHT) {
                controlX1 = startX + (westbound ? -40 : 40);
                controlY1 = startY;
                controlX2 = endX;
                controlY2 = endY + (westbound ? 60 : -60);
            } else {
                controlX1 = startX + (westbound ? -70 : 70);
                controlY1 = startY + (westbound ? 80 : -80);
                controlX2 = endX - (westbound ? 70 : -70);
                controlY2 = endY - (westbound ? 30 : -30);
            }

            path.getElements().add(new CubicCurveTo(
                    controlX1, controlY1, controlX2, controlY2, endX, endY));

            // Velocidad más rápida para emergencias
            double animationDuration = vehicle.getType() == VehicleType.EMERGENCY ?
                (vehicle.getDirection() == Direction.U_TURN ? 1.8 : 1.5) :
                (vehicle.getDirection() == Direction.U_TURN ? 2.5 : 2.0);
                
            PathTransition transition = new PathTransition(Duration.seconds(animationDuration), path, node);
            transition.setInterpolator(Interpolator.EASE_BOTH);

            transition.currentTimeProperty().addListener((obs, ot, nt) -> {
                double x = node.getCenterX() + node.getTranslateX();
                double y = node.getCenterY() + node.getTranslateY();
                vehicle.updatePosition(x, y);
            });

            transition.setOnFinished(e -> {
                vehicle.setInIntersection(false);
                IntersectionSemaphore sem = lifecycleManager.getIntersectionSemaphore();
                if (sem != null) sem.release();
                
                // Si era emergencia, desactivar protocolo
                if (vehicle.getType() == VehicleType.EMERGENCY) {
                    lifecycleManager.deactivateEmergencyProtocol();
                }

                Direction dir = vehicle.getDirection();

                if (dir == Direction.LEFT) {
                    if (westbound) {
                        postTurnTargetX = endX;
                        postTurnTargetY = endY + POST_TURN_DISTANCE;
                    } else {
                        postTurnTargetX = endX;
                        postTurnTargetY = endY - POST_TURN_DISTANCE;
                    }
                } else if (dir == Direction.RIGHT) {
                    if (westbound) {
                        postTurnTargetX = endX;
                        postTurnTargetY = endY - POST_TURN_DISTANCE;
                    } else {
                        postTurnTargetX = endX;
                        postTurnTargetY = endY + POST_TURN_DISTANCE;
                    }
                } else if (dir == Direction.U_TURN) {
                    if (westbound) {
                        postTurnTargetX = endX + POST_TURN_DISTANCE;
                        postTurnTargetY = endY;
                    } else {
                        postTurnTargetX = endX - POST_TURN_DISTANCE;
                        postTurnTargetY = endY;
                    }
                } else {
                    postTurnTargetX = endX + (westbound ? -POST_TURN_DISTANCE : POST_TURN_DISTANCE);
                    postTurnTargetY = endY;
                }

                currentState = State.POST_TURN;
            });

            transition.play();
        });
    }

    private boolean isSafeToRightTurnOnRed(int intersectionX) {
        double left = intersectionX - 10;
        double right = intersectionX + 90;
        double top = 210 - 20;
        double bottom = 310 + 120;
        
        if (!vehicle.isHighwayVehicle()) {
            StartPoint sp = vehicle.getStartPoint();
            
            if (sp == StartPoint.NORTH_L || sp == StartPoint.NORTH_D) {
                top = 210 - 20;
                bottom = 310;
            }
            
            if (sp == StartPoint.SOUTH_L || sp == StartPoint.SOUTH_D) {
                top = 310;
                bottom = 410 + 20;
            }
        }

        Collection<Vehicle> all = lifecycleManager.getAllActiveVehicles();
        for (Vehicle other : all) {
            if (!other.isActive() || other.getId() == vehicle.getId()) continue;

            double ox = other.getX();
            double oy = other.getY();

            boolean insideBox = (ox >= left && ox <= right && oy >= top && oy <= bottom);
            
            if (insideBox || other.isInIntersection()) {
                boolean isMovingAway = false;
                
                if (other.isHighwayVehicle()) {
                    boolean otherWestbound = other.getHighwayLane().isWestbound();
                    
                    if (otherWestbound && ox < intersectionX) {
                        isMovingAway = true;
                    }
                    
                    if (!otherWestbound && ox > intersectionX + 80) {
                        isMovingAway = true;
                    }
                }
                
                if (isMovingAway) {
                    double distance = Math.hypot(vehicle.getX() - ox, vehicle.getY() - oy);
                    if (distance > 150) {
                        continue;
                    }
                }
                
                return false;
            }
        }
        return true;
    }

    private static class AvenueContext {
        final boolean isUpper;
        AvenueContext(boolean isUpper) { this.isUpper = isUpper; }
    }

    private AvenueContext detectAvenueContext(double y) {
        if (y >= 210 && y <= 310) return new AvenueContext(true);
        if (y >= 310 && y <= 410) return new AvenueContext(false);
        return null;
    }

    private int getIntersectionXForStreet(StartPoint sp) {
        return switch (sp) {
            case NORTH_L, SOUTH_L -> 390;
            case NORTH_D, SOUTH_D -> 690;
            case EAST, WEST -> 390;
        };
    }
    
    private boolean isFirstIntersectionForStreetVehicle(StartPoint sp, int intersectionX) {
        if (sp == StartPoint.NORTH_L || sp == StartPoint.NORTH_D) {
            return true;
        }
        
        if (sp == StartPoint.SOUTH_L || sp == StartPoint.SOUTH_D) {
            return true;
        }
        
        return intersectionX == 390;
    }
}