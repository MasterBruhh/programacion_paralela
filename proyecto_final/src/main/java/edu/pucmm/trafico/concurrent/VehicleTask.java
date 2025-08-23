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
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Line;
import java.util.Collection;
import java.util.Random;
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
        POST_TURN,  // Estado para movimiento post-giro
        EXITING
    }

    private static final double HIGHWAY_SPEED = 5.0;    // px/frame
    private static final double STREET_SPEED = 3.0;     // px/frame
    private static final double STOP_DISTANCE = 25.0;   // px - Reducido para detener más cerca
    private static final double HIGHWAY_INTERSECTION_BUFFER = 85.0; // px para que no paren en mitad de autopista
    private static final int[] VALID_INTERSECTIONS = {390, 690}; // X-coordenadas de intersecciones válidas
    private static final double POST_TURN_DISTANCE = 200.0; // Aumentado de 150 a 200 para mayor distancia después del giro

    private final Vehicle vehicle;
    private final VehicleLifecycleManager lifecycleManager;
    private final AtomicBoolean active;
    private final CollisionDetector collisionDetector;
    private final Random random = new Random(); // Para movimientos aleatorios

    // Variables para el control del movimiento post-giro
    private volatile double postTurnTargetX;
    private volatile double postTurnTargetY;
    private volatile double postTurnSpeed = 3.0; // px/frame

    // Contador para detectar cuando un vehículo está bloqueado
    private int stuckCounter = 0;
    private static final int MAX_STUCK_COUNT = 20;

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
                    case POST_TURN -> handlePostTurnMovement();
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

    // Método para manejar el movimiento después del giro
    private void handlePostTurnMovement() {
        // Calcular dirección hacia el punto objetivo
        double dx = postTurnTargetX - vehicle.getX();
        double dy = postTurnTargetY - vehicle.getY();
        double distance = Math.hypot(dx, dy);

        // Si ya llegamos al punto objetivo, cambiar a EXITING
        if (distance < 5.0) {
            currentState = State.EXITING;
            return;
        }

        // Mover hacia el punto objetivo
        double step = Math.min(postTurnSpeed, distance);
        double newX = vehicle.getX() + (dx / distance) * step;
        double newY = vehicle.getY() + (dy / distance) * step;

        // Verificar colisiones
        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        if (!collisionDetector.canMove(vehicle, newX, newY, allVehicles)) {
            stuckCounter++;

            // Si estamos bloqueados por demasiado tiempo, aplicar un pequeño movimiento aleatorio
            if (stuckCounter > MAX_STUCK_COUNT) {
                double jitterX = (random.nextDouble() - 0.5) * 3.0;
                double jitterY = (random.nextDouble() - 0.5) * 3.0;
                updateVehiclePosition(vehicle.getX() + jitterX, vehicle.getY() + jitterY);
                stuckCounter = 0;
                return;
            }

            // Intentar con velocidad reducida
            step *= 0.5;
            newX = vehicle.getX() + (dx / distance) * step;
            newY = vehicle.getY() + (dy / distance) * step;

            if (!collisionDetector.canMove(vehicle, newX, newY, allVehicles)) {
                // Si aún no podemos movernos, esperar
                return;
            }
        }

        stuckCounter = 0; // Resetear contador si nos movemos con éxito
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

        // Verificar colisiones
        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();
        double step = Math.min(STREET_SPEED, dist);
        double nx = vehicle.getX() + (dx / dist) * step;
        double ny = vehicle.getY() + (dy / dist) * step;

        if (!collisionDetector.canMove(vehicle, nx, ny, allVehicles)) {
            stuckCounter++;

            // Si estamos bloqueados por demasiado tiempo, aplicar un pequeño movimiento aleatorio
            if (stuckCounter > MAX_STUCK_COUNT) {
                double jitterX = (random.nextDouble() - 0.5) * 3.0;
                double jitterY = (random.nextDouble() - 0.5) * 3.0;
                updateVehiclePosition(vehicle.getX() + jitterX, vehicle.getY() + jitterY);
                stuckCounter = 0;
                return;
            }

            // Intentar con velocidad reducida
            step = step * 0.5;
            nx = vehicle.getX() + (dx / dist) * step;
            ny = vehicle.getY() + (dy / dist) * step;

            if (!collisionDetector.canMove(vehicle, nx, ny, allVehicles)) {
                // Si aún no podemos movernos, esperar
                return;
            }
        }

        stuckCounter = 0; // Resetear contador si nos movemos con éxito
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

            // Construir path con curva si aplica
            javafx.scene.shape.Path path = buildCrossingPath(
                    vehicle.getStartPoint(),
                    vehicle.getDirection(),
                    startX, startY,
                    exit[0], exit[1]
            );

            PathTransition t = new PathTransition(Duration.seconds(2.5), path, node);
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

                // Calcular punto objetivo para movimiento post-giro
                double dx = exit[0] - startX;
                double dy = exit[1] - startY;
                double length = Math.hypot(dx, dy);

                if (length > 0) {
                    // Normalizar y extender en la misma dirección
                    dx = dx / length;
                    dy = dy / length;
                    postTurnTargetX = exit[0] + dx * POST_TURN_DISTANCE;
                    postTurnTargetY = exit[1] + dy * POST_TURN_DISTANCE;
                    currentState = State.POST_TURN;
                } else {
                    currentState = State.EXITING;
                }
            });
            t.play();
        });
    }

    /**
     * Construye el Path de cruce. Para STRAIGHT usa línea recta; para LEFT/RIGHT/U_TURN usa curvas Bezier.
     */
    private javafx.scene.shape.Path buildCrossingPath(StartPoint sp, Direction dir,
                                                      double startX, double startY,
                                                      double endX, double endY) {
        javafx.scene.shape.Path path = new javafx.scene.shape.Path();
        path.getElements().add(new javafx.scene.shape.MoveTo(startX, startY));

        // Recto: no hace falta curva
        if (dir == Direction.STRAIGHT) {
            path.getElements().add(new javafx.scene.shape.LineTo(endX, endY));
            return path;
        }

        // Vectores unitarios de entrada/salida
        double[] vin = incomingUnitVector(sp);
        double[] vout = outgoingUnitVector(sp, dir);

        // Radio/holgura de giro (ajustable) - Aumentado para curvas más amplias
        double radius = 60.0;  // Aumentado de 55 a 60

        // Puntos de control básicos
        double cp1x = startX + vin[0] * radius;
        double cp1y = startY + vin[1] * radius;
        double cp2x = endX   - vout[0] * radius;
        double cp2y = endY   - vout[1] * radius;

        if (dir == Direction.U_TURN) {
            // Para U-TURN levantamos la curva hacia el interior con un desplazamiento perpendicular
            double[] n = new double[]{ -vin[1], vin[0] }; // perpendicular (giro 90°)
            double bump = radius * 1.5;  // Aumentado de 0.8 a 1.5 para curvas U más amplias
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

    /**
     * Dirección de entrada (unitaria) según el StartPoint.
     */
    private double[] incomingUnitVector(StartPoint sp) {
        return switch (sp) {
            case NORTH_L, NORTH_D -> new double[]{ 0.0,  1.0 }; // viniendo desde arriba hacia abajo
            case SOUTH_L, SOUTH_D -> new double[]{ 0.0, -1.0 }; // viniendo desde abajo hacia arriba
            case EAST               -> new double[]{ -1.0, 0.0 }; // viniendo desde la derecha hacia la izquierda
            case WEST               -> new double[]{ 1.0,  0.0 }; // viniendo desde la izquierda hacia la derecha
        };
    }

    /**
     * Dirección de salida (unitaria) después del giro según StartPoint y Direction.
     */
    private double[] outgoingUnitVector(StartPoint sp, Direction dir) {
        // Base: vector de entrada
        double[] vin = incomingUnitVector(sp);

        // Rotaciones: LEFT = -90°, RIGHT = +90°, STRAIGHT = 0°, U_TURN = 180°
        return switch (dir) {
            case STRAIGHT -> vin;
            case U_TURN   -> new double[]{ -vin[0], -vin[1] };
            case LEFT     -> rotateLeft(vin);
            case RIGHT    -> rotateRight(vin);
        };
    }

    private double[] rotateLeft(double[] v) {
        // (x, y) -> (-y, x)
        return new double[]{ -v[1], v[0] };
    }

    private double[] rotateRight(double[] v) {
        // (x, y) -> (y, -x)
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
            return vehicle.getHighwayLane().isWestbound() ? "ARRIBA" : "ABAJO";
        } else {
            StartPoint sp = vehicle.getStartPoint();
            return switch (sp) {
                case NORTH_L, NORTH_D -> "CalleIzq"; // Carriles norte -> sur (calle izquierda)
                case SOUTH_L, SOUTH_D -> "CalleDer"; // Carriles sur -> norte (calle derecha)
                case EAST, WEST -> "CalleDer";       // Valor por defecto para no-autopista
            };
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
            stuckCounter++;

            // Si estamos bloqueados por demasiado tiempo, aplicar un pequeño movimiento aleatorio
            if (stuckCounter > MAX_STUCK_COUNT) {
                double jitterX = (random.nextDouble() - 0.5) * 3.0;
                double jitterY = (random.nextDouble() - 0.5) * 3.0;
                updateVehiclePosition(vehicle.getX() + jitterX, vehicle.getY() + jitterY);
                stuckCounter = 0;
                return;
            }

            // Intentar con velocidad reducida
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

        stuckCounter = 0; // Resetear contador si nos movemos con éxito

        // Detectar si hay un vehículo adelante y ajustar velocidad
        CollisionDetector.VehicleAheadInfo ahead = collisionDetector.detectVehicleAhead(vehicle, allVehicles);
        if (ahead != null && ahead.getDistance() < 80) {
            // Reducir más la velocidad cuanto más cerca
            double slowFactor = Math.max(0.3, ahead.getDistance() / 80.0);
            newX = westbound ?
                    currentX - (HIGHWAY_SPEED * slowFactor) :
                    currentX + (HIGHWAY_SPEED * slowFactor);
        }

        // NUEVA LÓGICA: Verificar si debe girar en alguna intersección
        if (vehicle.getTargetIntersection() >= 0 &&
                vehicle.getDirection() != Direction.STRAIGHT) {

            // Obtener la coordenada X de la intersección objetivo
            int targetX = VALID_INTERSECTIONS[vehicle.getTargetIntersection()];

            // Calcular si estamos en la zona de giro
            boolean isInTurnZone = westbound ?
                    (currentX > targetX && currentX < targetX + 90) :
                    (currentX < targetX + 80 && currentX > targetX - 10);

            if (isInTurnZone) {
                // Verificar semáforo
                String group = getTrafficLightGroup();
                TrafficLightState light = lifecycleManager.getTrafficLightState(group);

                boolean canTurn = (light == TrafficLightState.GREEN) ||
                        (light == TrafficLightState.YELLOW &&
                                vehicle.getType() == VehicleType.EMERGENCY);

                if (canTurn) {
                    vehicle.setInIntersection(true);
                    performHighwayTurn();
                    return;
                } else {
                    // Detenerse en la intersección hasta que cambie el semáforo
                    double stopX = westbound ? targetX + 80 : targetX;
                    updateVehiclePosition(stopX, y);
                    vehicle.setWaitingAtLight(true);
                    currentState = State.WAITING_AT_LIGHT;
                    return;
                }
            }
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

    private void performHighwayTurn() {
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;

            // Coordenadas actuales
            double startX = node.getCenterX();
            double startY = node.getCenterY();

            // Calcular coordenadas de destino según dirección
            double endX, endY;
            boolean westbound = vehicle.getHighwayLane().isWestbound();
            int intersectionX = VALID_INTERSECTIONS[vehicle.getTargetIntersection()];

            switch (vehicle.getDirection()) {
                case LEFT:
                    // Si va hacia oeste (arriba), gira hacia el sur
                    // Si va hacia este (abajo), gira hacia el norte
                    endX = intersectionX + 40; // 40px a la derecha del centro de la intersección
                    endY = westbound ? 500 : 100; // Hacia abajo o arriba según carril
                    break;
                case RIGHT:
                    // Si va hacia oeste (arriba), gira hacia el norte
                    // Si va hacia este (abajo), gira hacia el sur
                    endX = intersectionX + 40;
                    endY = westbound ? 100 : 500;
                    break;
                case U_TURN:
                    // Mejorado: puntos finales más precisos para U-turns
                    endX = intersectionX + (westbound ? 120 : -120); // Más lejos en X para curva más amplia
                    endY = westbound ? 360 : 260; // Y del carril contrario
                    break;
                default:
                    // Nunca debería llegar aquí
                    endX = startX + 100;
                    endY = startY;
            }

            // Crear curva de giro
            Path path = new Path();
            path.getElements().add(new MoveTo(startX, startY));

            // Puntos de control para curvas Bezier - Mejorados para curvas más naturales
            double controlX1, controlY1, controlX2, controlY2;

            if (vehicle.getDirection() == Direction.LEFT) {
                controlX1 = startX + (westbound ? -40 : 40);  // Aumentado de 30 a 40
                controlY1 = startY;
                controlX2 = endX;
                controlY2 = endY - (westbound ? 60 : -60);  // Aumentado de 50 a 60
            } else if (vehicle.getDirection() == Direction.RIGHT) {
                controlX1 = startX + (westbound ? -40 : 40);  // Aumentado de 30 a 40
                controlY1 = startY;
                controlX2 = endX;
                controlY2 = endY + (westbound ? 60 : -60);  // Aumentado de 50 a 60
            } else { // U_TURN - Mejoras significativas
                // Puntos de control más alejados para curva más amplia
                controlX1 = startX + (westbound ? -70 : 70);  // Aumentado de 50 a 70
                controlY1 = startY + (westbound ? 80 : -80);  // Aumentado de 50 a 80
                controlX2 = endX - (westbound ? 70 : -70);    // Aumentado de 50 a 70
                controlY2 = endY - (westbound ? 30 : -30);    // Nuevo ajuste para suavizar
            }

            path.getElements().add(new CubicCurveTo(
                    controlX1, controlY1, controlX2, controlY2, endX, endY));

            // Animar giro - Aumentado tiempo para U-turns
            double animationDuration = (vehicle.getDirection() == Direction.U_TURN) ? 2.5 : 2.0;
            PathTransition transition = new PathTransition(Duration.seconds(animationDuration), path, node);
            transition.setInterpolator(Interpolator.EASE_BOTH);

            // Actualizar posición del modelo durante la animación
            transition.currentTimeProperty().addListener((obs, ot, nt) -> {
                double x = node.getCenterX() + node.getTranslateX();
                double y = node.getCenterY() + node.getTranslateY();
                vehicle.updatePosition(x, y);
            });

            // Modificar para calcular un punto adicional en la misma dirección después del giro
            transition.setOnFinished(e -> {
                // Liberar semáforo y actualizar estado
                vehicle.setInIntersection(false);
                IntersectionSemaphore sem = lifecycleManager.getIntersectionSemaphore();
                if (sem != null) sem.release();

                // Calcular puntos de destino después del giro
                Direction dir = vehicle.getDirection();

                if (dir == Direction.LEFT) {
                    if (westbound) {
                        // Giro hacia el sur desde avenida superior
                        postTurnTargetX = endX;
                        postTurnTargetY = endY + POST_TURN_DISTANCE;
                    } else {
                        // Giro hacia el norte desde avenida inferior
                        postTurnTargetX = endX;
                        postTurnTargetY = endY - POST_TURN_DISTANCE;
                    }
                } else if (dir == Direction.RIGHT) {
                    if (westbound) {
                        // Giro hacia el norte desde avenida superior
                        postTurnTargetX = endX;
                        postTurnTargetY = endY - POST_TURN_DISTANCE;
                    } else {
                        // Giro hacia el sur desde avenida inferior
                        postTurnTargetX = endX;
                        postTurnTargetY = endY + POST_TURN_DISTANCE;
                    }
                } else if (dir == Direction.U_TURN) {
                    if (westbound) {
                        // U-turn a la avenida inferior (hacia el este) - Mayor distancia X
                        postTurnTargetX = endX + POST_TURN_DISTANCE;
                        postTurnTargetY = endY;
                    } else {
                        // U-turn a la avenida superior (hacia el oeste) - Mayor distancia X
                        postTurnTargetX = endX - POST_TURN_DISTANCE;
                        postTurnTargetY = endY;
                    }
                } else {
                    // Caso por defecto (nunca debería llegar aquí)
                    postTurnTargetX = endX + (westbound ? -POST_TURN_DISTANCE : POST_TURN_DISTANCE);
                    postTurnTargetY = endY;
                }

                // Cambiar al estado POST_TURN en lugar de directamente a EXITING
                currentState = State.POST_TURN;
            });

            transition.play();
        });
    }
}