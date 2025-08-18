package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.core.*;
import edu.pucmm.trafico.model.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.scene.shape.Circle;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.util.Duration;

import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Tarea del vehículo con respeto a semáforos y salida con animación.
 */
public class VehicleTask implements Runnable {
    private static final Logger logger = Logger.getLogger(VehicleTask.class.getName());

    private enum State {
        SPAWNING,
        APPROACHING,
        WAITING_AT_LIGHT,
        CROSSING,
        EXITING
    }

    private static final double HIGHWAY_SPEED = 5.0;    // px/frame
    private static final double STREET_SPEED = 3.0;     // px/frame
    private static final double STOP_DISTANCE = 25.0;   // px - Reducido para detener más cerca
    private static final double HIGHWAY_INTERSECTION_BUFFER = 85.0; // px para que no paren en mitad de autopista

    private final Vehicle vehicle;
    private final VehicleLifecycleManager lifecycleManager;
    private final AtomicBoolean active;
    private final CollisionDetector collisionDetector;

    private volatile State currentState = State.SPAWNING;
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
                switch (currentState) {
                    case APPROACHING -> handleApproaching();
                    case WAITING_AT_LIGHT -> handleWaitingAtLight();
                    case CROSSING -> { /* animación en hilo FX */ Thread.sleep(50); }
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

    private void moveHighwayVehicle() {
        double currentX = vehicle.getX();
        double y = vehicle.getY();
        boolean westbound = vehicle.getHighwayLane().isWestbound();
        double newX = westbound ? currentX - HIGHWAY_SPEED : currentX + HIGHWAY_SPEED;
        
        // Fin de autopista -> salir
        if (newX < -20 || newX > 1180) {
            currentState = State.EXITING;
            return;
        }
        
        // Verificar colisiones
        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        if (!collisionDetector.canMove(vehicle, newX, y, allVehicles)) {
            // Si no podemos movernos por colisión, intentar con un movimiento más corto
            double halfSpeed = HIGHWAY_SPEED * 0.5;
            double saferX = westbound ? currentX - halfSpeed : currentX + halfSpeed;
            
            if (collisionDetector.canMove(vehicle, saferX, y, allVehicles)) {
                // Podemos movernos más lento
                newX = saferX;
            } else {
                // Mejor quedarnos quietos
                return;
            }
        }

        // Detectar si hay un vehículo adelante y ajustar velocidad
        CollisionDetector.VehicleAheadInfo ahead = collisionDetector.detectVehicleAhead(vehicle, allVehicles);
        if (ahead != null && ahead.getDistance() < 80) {
            // Reducir más la velocidad cuanto más cerca
            double slowFactor = Math.max(0.3, ahead.getDistance() / 80.0);
            newX = westbound ? 
                   currentX - (HIGHWAY_SPEED * slowFactor) : 
                   currentX + (HIGHWAY_SPEED * slowFactor);
        }

        // Intersecciones verticales en X: 70, 390, 690, 1010 (ancho calle 80)
        int[] streets = {70, 390, 690, 1010};
        String group = getTrafficLightGroup();
        TrafficLightState light = lifecycleManager.getTrafficLightState(group);

        // Si el vehículo ya ha cruzado la última intersección, no debería detenerse más
        if (hasCrossedLastLight) {
            updateVehiclePosition(newX, y);
            return;
        }

        // Calcular borde de pare según dirección
        for (int i = 0; i < streets.length; i++) {
            int sx = streets[i];
            double leftEdge = sx;
            double rightEdge = sx + 80;
            double stopX = westbound ? rightEdge - 5 : leftEdge + 5; // 5px antes del cruce (más cerca)
            
            // Solo considerar la intersección hacia la que nos movemos
            boolean approachingThis = westbound ? 
                (currentX > stopX && newX <= stopX + STOP_DISTANCE) :
                (currentX < stopX && newX >= stopX - STOP_DISTANCE);
            
            if (!approachingThis) continue;

            // Si luz roja (o amarilla para no emergencias), detenerse en stopX
            boolean allowed = (light == TrafficLightState.GREEN) ||
                             (light == TrafficLightState.YELLOW && vehicle.getType() == VehicleType.EMERGENCY);
            
            if (!allowed) {
                double clampX = westbound ? Math.max(newX, stopX) : Math.min(newX, stopX);
                updateVehiclePosition(clampX, y);
                if (Math.abs(clampX - stopX) < 1.0) {
                    vehicle.setWaitingAtLight(true);
                    currentState = State.WAITING_AT_LIGHT;
                }
                return; // No avanzar más si estamos esperando
            }
            
            // Marcar que ha pasado la última intersección
            if (i == streets.length - 1) {
                // Si estamos suficientemente lejos de la intersección después de cruzar
                boolean pastIntersection = westbound ? 
                    (currentX < leftEdge - HIGHWAY_INTERSECTION_BUFFER) : 
                    (currentX > rightEdge + HIGHWAY_INTERSECTION_BUFFER);
                    
                if (pastIntersection) {
                    hasCrossedLastLight = true;
                }
            }
            
            break;
        }

        updateVehiclePosition(newX, y);
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

        // Verificar colisiones
        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        double step = Math.min(STREET_SPEED, dist);
        double nx = vehicle.getX() + (dx / dist) * step;
        double ny = vehicle.getY() + (dy / dist) * step;
        
        if (!collisionDetector.canMove(vehicle, nx, ny, allVehicles)) {
            // Si detectamos colisión, intentar movernos más lento
            step = step * 0.5;
            nx = vehicle.getX() + (dx / dist) * step;
            ny = vehicle.getY() + (dy / dist) * step;
            
            if (!collisionDetector.canMove(vehicle, nx, ny, allVehicles)) {
                // Si aún no podemos movernos, esperar
                return;
            }
        }

        updateVehiclePosition(nx, ny);
    }

    private void handleWaitingAtLight() throws InterruptedException {
        // Determinar grupo de semáforo
        String group = getTrafficLightGroup();
        TrafficLightState light = lifecycleManager.getTrafficLightState(group);

        boolean canCross;
        if (vehicle.isHighwayVehicle()) {
            // En autopista, respetar verde; emergencia puede pasar en amarillo
            canCross = (light == TrafficLightState.GREEN) ||
                       (light == TrafficLightState.YELLOW && vehicle.getType() == VehicleType.EMERGENCY);
            if (canCross) {
                vehicle.setWaitingAtLight(false);
                currentState = State.APPROACHING; // Reanudar avance
            } else {
                Thread.sleep(100);
            }
            return;
        } else {
            canCross = (vehicle.getType() == VehicleType.EMERGENCY)
                    ? (light == TrafficLightState.GREEN || light == TrafficLightState.YELLOW)
                    : (light == TrafficLightState.GREEN);
        }

        if (canCross) {
            // Verificar si hay espacio suficiente en la intersección
            Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
            boolean hasSpace = true;
            
            for (Vehicle other : allVehicles) {
                if (other.getId() == vehicle.getId() || !other.isActive()) continue;
                if (other.isInIntersection()) {
                    // Si hay otro vehículo en la intersección, verificar la distancia
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
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;

            double startX = node.getCenterX();
            double startY = node.getCenterY();
            double[] exit = vehicle.getStartPoint().getExitCoordinates(vehicle.getDirection());

            Path path = new Path();
            path.getElements().addAll(
                    new MoveTo(startX, startY),
                    new LineTo(exit[0], exit[1])
            );

            PathTransition t = new PathTransition(Duration.seconds(2.5), path, node);
            t.setInterpolator(Interpolator.EASE_BOTH);
            t.currentTimeProperty().addListener((obs, ot, nt) -> {
                double x = node.getCenterX() + node.getTranslateX();
                double y = node.getCenterY() + node.getTranslateY();
                vehicle.updatePosition(x, y);
            });
            t.setOnFinished(e -> {
                // Liberar intersección si aplica
                vehicle.setInIntersection(false);
                IntersectionSemaphore sem = lifecycleManager.getIntersectionSemaphore();
                if (sem != null) sem.release();
                currentState = State.EXITING;
            });
            t.play();
        });
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
            return vehicle.getHighwayLane().isWestbound() ? "ARRIBA" : "ABAJO";
        } else {
            return switch (vehicle.getStartPoint()) {
                case NORTH -> "CalleIzq"; // Calle izquierda (norte->sur)
                case SOUTH -> "CalleDer"; // Calle derecha (sur->norte)
                case EAST, WEST -> "CalleDer"; // No usado para calles, valor por defecto
            };
        }
    }
}