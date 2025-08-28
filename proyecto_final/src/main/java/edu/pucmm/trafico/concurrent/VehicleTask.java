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
    private static final double STOP_DISTANCE = 50.0;   // px - Distancia de detección antes del semáforo (aumentada significativamente)
    private static final double HIGHWAY_INTERSECTION_BUFFER = 100.0; // px para que no paren en mitad de autopista
    private static final int[] VALID_INTERSECTIONS = {70, 390, 690, 1010}; // Todas las 4 intersecciones
    private static final double POST_TURN_DISTANCE = 250.0; // Mayor distancia después del giro para movimiento más natural
    // Geometría de calles verticales (coincide con drawRoadsWithLanes en el controlador)
    // Centros de carril de calle respecto al borde izquierdo de la calle
    private static final double STREET_LANE_CENTER_WEST = 20.0; // Para tráfico hacia el SUR (lado izquierdo de la calle)
    private static final double STREET_LANE_CENTER_EAST = 60.0; // Para tráfico hacia el NORTE (lado derecho de la calle)

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

    // --- Mejora: Reincorporación tras U-TURN ---
    // Cuando un vehículo de autopista realiza una vuelta en U debe reincorporarse a la avenida
    // opuesta y seguir respetando los semáforos como si circulara normalmente en el nuevo sentido.
    // Para no modificar la lógica existente del modelo (los campos del Vehicle son finales),
    // añadimos banderas internas en la tarea para interpretar un nuevo sentido efectivo.
    private volatile boolean reincorporatedAfterUTurn = false;      // Indica que ya completó U-TURN y sigue circulando
    private volatile boolean uturnWestboundAfter = false;           // Sentido efectivo tras la vuelta (true = oeste, false = este)

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

        // Antes de movernos: si somos vehículo de calle que ya se incorporó a una AVENIDA,
        // respetar los semáforos de esa avenida como lo hacen los de autopista.
        if (!vehicle.isHighwayVehicle()) {
            AvenueContext avenue = detectAvenueContext(vehicle.getY());
            if (avenue != null) {
                boolean westbound = avenue.isUpper; // avenida superior se mueve hacia el oeste

                // Determinar próximo punto de parada de semáforo
                String group = westbound ? "ARRIBA" : "ABAJO";
                TrafficLightState light = lifecycleManager.getTrafficLightState(group);

                // Revisar ambas intersecciones válidas
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

                    // Si es la segunda intersección, siempre respetar el semáforo
                    // Si es la primera, podemos permitir giro a la derecha en rojo con precaución
                    boolean isSecondIntersection = (i == 1);
                    boolean allowRightOnRed = !isSecondIntersection &&
                            vehicle.getDirection() == Direction.RIGHT &&
                            light == TrafficLightState.RED &&
                            isSafeToRightTurnOnRed(sx);

                    boolean allowed = (light == TrafficLightState.GREEN) ||
                            (light == TrafficLightState.YELLOW && vehicle.getType() == VehicleType.EMERGENCY) ||
                            allowRightOnRed;

                    if (!allowed) {
                        // Detenerse exactamente en stopX
                        updateVehiclePosition(stopX, vehicle.getY());
                        vehicle.setWaitingAtLight(true);
                        // Permanecer en POST_TURN; reintentará en el próximo tick
                        return;
                    }
                }
                // Si no hay luz que detenga, limpiar estado de espera
                vehicle.setWaitingAtLight(false);
            }
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

        // Verificar si hay vehículos adelante
        CollisionDetector.VehicleAheadInfo ahead = collisionDetector.detectVehicleAhead(vehicle, allVehicles);
        if (ahead != null && ahead.getDistance() < 60) {
            // Si hay un vehículo adelante que está detenido, no nos movemos
            if (ahead.getVehicle().isWaitingAtLight()) {
                stuckCounter = 0; // Resetear contador si es una detención normal
                return; // No avanzar si el vehículo adelante está esperando
            }

            // Reducir velocidad proporcional a la distancia
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

        // Movimiento normal
        double step = Math.min(STREET_SPEED, dist);
        double nx = vehicle.getX() + (dx / dist) * step;
        double ny = vehicle.getY() + (dy / dist) * step;

        if (!collisionDetector.canMove(vehicle, nx, ny, allVehicles)) {
            stuckCounter++;

            if (stuckCounter > MAX_STUCK_COUNT) {
                // Si estamos bloqueados demasiado tiempo, pequeño movimiento aleatorio
                double jitterX = (random.nextDouble() - 0.5) * 2.0;
                double jitterY = (random.nextDouble() - 0.5) * 2.0;
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
        // Obtener estado contextual (por vehículo) para respetar protocolo de emergencia por carril
        TrafficLightState light = lifecycleManager.getTrafficLightStateFor(vehicle, group);

        boolean canCross;
        // Si es emergencia, SIEMPRE puede cruzar sin importar el semáforo
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            canCross = true;
        } else if (vehicle.isHighwayVehicle()) {
            // En autopista, sólo verde permite avanzar a vehículos normales
            canCross = (light == TrafficLightState.GREEN);

            if (canCross) {
                vehicle.setWaitingAtLight(false);
                currentState = State.APPROACHING; // Reanudar avance
            } else {
                Thread.sleep(100); // Esperar si el semáforo sigue en rojo
            }
            return;
        } else {
            // Para calle, vehículos normales sólo con verde
            canCross = (light == TrafficLightState.GREEN);

            // Regla adicional para CALLE: permitir giro a la derecha en rojo con precaución (si es seguro)
            if (!canCross && vehicle.getDirection() == Direction.RIGHT && light == TrafficLightState.RED) {
                // Determinar si es la primera intersección (en caso de calles, solo hay 2 intersecciones)
                StartPoint sp = vehicle.getStartPoint();
                int streetIntersectionX = getIntersectionXForStreet(sp);

                // Verificar si es la primera intersección para ese vehículo
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
        // Asegurarnos de que el vehículo ya no está en estado de espera
        vehicle.setWaitingAtLight(false);

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

                    // Si el vehículo gira hacia una avenida, asegurarnos de que se incorpore a un carril específico
                    AvenueContext avenue = detectAvenueContext(exit[1]);
                    if (avenue != null && vehicle.getDirection() == Direction.RIGHT) {
                        // Si gira a la derecha hacia una avenida, debe incorporarse al carril derecho
                        double laneY;
                        if (avenue.isUpper) {
                            // Avenida superior (westbound) - carril derecho Y=293.33
                            laneY = 293.33;
                        } else {
                            // Avenida inferior (eastbound) - carril derecho Y=393.33
                            laneY = 393.33;
                        }

                        // Ajustar el target Y para incorporarse al carril correcto
                        // Mantenemos la dirección en X pero ajustamos Y para terminar en el carril adecuado
                        postTurnTargetX = exit[0] + dx * POST_TURN_DISTANCE;
                        postTurnTargetY = laneY;
                    } else if (avenue != null && vehicle.getDirection() == Direction.LEFT) {
                        // Si gira a la izquierda hacia una avenida, debe incorporarse al carril izquierdo
                        double laneY;
                        if (avenue.isUpper) {
                            // Avenida superior (westbound) - carril izquierdo Y=226.67
                            laneY = 226.67;
                        } else {
                            // Avenida inferior (eastbound) - carril izquierdo Y=326.67
                            laneY = 326.67;
                        }

                        // Ajustar el target Y para incorporarse al carril correcto
                        postTurnTargetX = exit[0] + dx * POST_TURN_DISTANCE;
                        postTurnTargetY = laneY;
                    } else {
                        // Para otros casos, mantener la dirección de salida
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
            boolean westbound = effectiveWestbound();

            // Si el vehículo tiene una intersección objetivo específica para giros
            if (vehicle.getTargetIntersection() >= 0 && vehicle.getDirection() != Direction.STRAIGHT) {
                int idx = Math.min(vehicle.getTargetIntersection(), VALID_INTERSECTIONS.length - 1);

                return switch (idx) {
                    case 0 -> westbound ? "ARRIBA" : "ABAJO";  // Intersección lateral izquierda (X=70)
                    case 1 -> westbound ? "ARRIBA" : "ABAJO";  // Primera intersección central (X=390)
                    case 2 -> westbound ? "ARRIBA" : "ABAJO";  // Segunda intersección central (X=690)
                    case 3 -> westbound ? "ARRIBA" : "ABAJO";  // Intersección lateral derecha (X=1010)
                    default -> westbound ? "ARRIBA" : "ABAJO";
                };
            }

            // Para movimiento recto, usar el grupo estándar
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
    boolean westbound = effectiveWestbound();
        double newX = westbound ? currentX - HIGHWAY_SPEED : currentX + HIGHWAY_SPEED;

        // Fin de autopista -> salir
        if (newX < -20 || newX > 1180) {
            currentState = State.EXITING;
            return;
        }

        // Verificar colisiones
        Collection<Vehicle> allVehicles = lifecycleManager.getAllActiveVehicles();

        // Detectar si hay un vehículo adelante y ajustar velocidad
        CollisionDetector.VehicleAheadInfo ahead = collisionDetector.detectVehicleAhead(vehicle, allVehicles);
        if (ahead != null && ahead.getDistance() < 80) {
            // Si hay un vehículo adelante que está detenido, no nos movemos
            if (ahead.getVehicle().isWaitingAtLight()) {
                stuckCounter = 0; // Resetear contador si es una detención normal
                return; // No avanzar si el vehículo adelante está esperando en semáforo
            }

            // Reducir velocidad proporcional a la distancia
            double slowFactor = Math.max(0.3, ahead.getDistance() / 80.0);
            newX = westbound ?
                    currentX - (HIGHWAY_SPEED * slowFactor) :
                    currentX + (HIGHWAY_SPEED * slowFactor);
        }

        // Verificar si podemos movernos a la nueva posición
        if (!collisionDetector.canMove(vehicle, newX, y, allVehicles)) {
            stuckCounter++;

            if (stuckCounter > MAX_STUCK_COUNT) {
                // Si estamos bloqueados demasiado tiempo, pequeño movimiento aleatorio
                double jitterX = (random.nextDouble() - 0.5) * 2.0;
                double jitterY = (random.nextDouble() - 0.5) * 2.0;
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

        // VERIFICACIÓN DE GIRO: Si el vehículo tiene un targetIntersection,
        // comprobamos si está en la zona de giro
        if (vehicle.getTargetIntersection() >= 0 &&
                vehicle.getDirection() != Direction.STRAIGHT) {

            // Obtener la coordenada X de la intersección objetivo
            // Asegurar que solo usamos las intersecciones principales (índice dentro de rango)
            int targetIdx = Math.min(vehicle.getTargetIntersection(), VALID_INTERSECTIONS.length - 1);
            int targetX = VALID_INTERSECTIONS[targetIdx];

            // Calcular si estamos en la zona de giro
            boolean isInTurnZone = westbound ?
                    (currentX > targetX && currentX < targetX + 90) :
                    (currentX < targetX + 80 && currentX > targetX - 10);

            if (isInTurnZone) {
                // Verificar semáforo - usar estado contextual
                String group = getTrafficLightGroup();
                TrafficLightState light = lifecycleManager.getTrafficLightStateFor(vehicle, group);
                
                boolean canTurn;
                if (vehicle.getType() == VehicleType.EMERGENCY) {
                    canTurn = true; // Las emergencias siempre pueden girar
                } else {
                    canTurn = (light == TrafficLightState.GREEN); // Vehículos normales sólo en verde
                }

                // Regla solicitada: giro a la derecha en rojo con precaución SOLO en la primera intersección
                boolean isRightTurnOnRedAllowedHere =
                        vehicle.getDirection() == Direction.RIGHT && targetIdx == 0 && light == TrafficLightState.RED;

                if (canTurn || (isRightTurnOnRedAllowedHere && isSafeToRightTurnOnRed(targetX))) {
                    vehicle.setWaitingAtLight(false);
                    vehicle.setInIntersection(true);
                    performHighwayTurn();
                    return;
                } else {
                    // Si es giro a la derecha en la segunda intersección (idx=1) o no es seguro, respetar el semáforo
                    double stopX = westbound ? targetX + 80 : targetX;
                    updateVehiclePosition(stopX, y);
                    vehicle.setWaitingAtLight(true);
                    currentState = State.WAITING_AT_LIGHT;
                    return;
                }
            }
        }

        // Si el vehículo ya ha cruzado los semáforos importantes, no debe detenerse más
        if (hasCrossedLastLight) {
            updateVehiclePosition(newX, y);
            return;
        }

        // Posiciones de los semáforos (SOLO las dos intersecciones principales tienen semáforos)
        // Usamos VALID_INTERSECTIONS que ya ha sido actualizado para incluir solo las intersecciones principales
        String group = getTrafficLightGroup();
    // Estado contextual (puede diferir de getTrafficLightState normal bajo emergencia)
    TrafficLightState light = lifecycleManager.getTrafficLightStateFor(vehicle, group);

        // Verificar si estamos acercándonos a un semáforo
        for (int i = 0; i < VALID_INTERSECTIONS.length; i++) {
            int sx = VALID_INTERSECTIONS[i];
            double leftEdge = sx;
            double rightEdge = sx + 80;

            // Calcular posición de pare basada en la ubicación real de las luces
            // Parar ANTES de la intersección (como se muestra con las líneas verdes en la imagen)
            double stopX;
            if (westbound) {
                // Avenida superior (hacia oeste): parar bastante antes de la intersección (izquierda)
                stopX = leftEdge + 95;  // Incrementado para parar más lejos de la intersección
            } else {
                // Avenida inferior (hacia este): parar bastante antes de la intersección (derecha)
                stopX = rightEdge - 95;  // Incrementado para parar más lejos de la intersección
            }

            // Solo considerar la intersección hacia la que nos movemos
            // Aumentamos la distancia de detección para que comience a reducir la velocidad antes
            boolean approachingThis = westbound ?
                    (currentX > stopX && newX <= stopX + STOP_DISTANCE + 20) :
                    (currentX < stopX && newX >= stopX - STOP_DISTANCE - 20);

            if (!approachingThis) continue;

            // Emergencias ignoran semáforo siempre
            boolean allowed;
            if (vehicle.getType() == VehicleType.EMERGENCY) {
                allowed = true; // Las emergencias siempre tienen permitido pasar
            } else {
                allowed = (light == TrafficLightState.GREEN); // Normales solo con verde
            }

            if (!allowed) {
                // Detectar si estamos aproximándonos a la línea de pare (verde)
                boolean isApproachingStopLine = westbound ?
                        (currentX > stopX && newX <= stopX) :
                        (currentX < stopX && newX >= stopX);

                // Si estamos por pasar la línea de pare verde, detenernos exactamente allí
                if (isApproachingStopLine) {
                    updateVehiclePosition(stopX, y);
                    vehicle.setWaitingAtLight(true);
                    currentState = State.WAITING_AT_LIGHT;
                    return; // No avanzar más si estamos esperando
                }

                // Si ya pasamos la línea de pare pero aún no llegamos a la intersección real
                boolean betweenStopLineAndIntersection = westbound ?
                        (currentX <= stopX && currentX >= leftEdge) :
                        (currentX >= stopX && currentX <= rightEdge);

                if (betweenStopLineAndIntersection) {
                    // Si estamos entre la línea de pare y la intersección, retroceder a la línea
                    updateVehiclePosition(stopX, y);
                    vehicle.setWaitingAtLight(true);
                    currentState = State.WAITING_AT_LIGHT;
                    return;
                }

                // Si no estamos en rango de detención pero el semáforo está rojo, seguir moviendo hasta la línea
                if ((westbound && newX > stopX) || (!westbound && newX < stopX)) {
                    updateVehiclePosition(newX, y);
                    return;
                }

                // Si ya estamos en posición de detención o más atrás
                updateVehiclePosition(stopX, y);
                vehicle.setWaitingAtLight(true);
                currentState = State.WAITING_AT_LIGHT;
                return; // No avanzar más si estamos esperando
            }

            // Marcar que ha pasado la última intersección (solo para la segunda)
            if (i == VALID_INTERSECTIONS.length - 1) {
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
        // Asegurarnos de que el vehículo ya no está en estado de espera
        vehicle.setWaitingAtLight(false);

        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;

            // Coordenadas actuales
            double startX = node.getCenterX();
            double startY = node.getCenterY();

            // Calcular coordenadas de destino según dirección
            double endX, endY;
            boolean westbound = vehicle.getHighwayLane().isWestbound();
            // Asegurar que solo usamos intersecciones válidas
            int targetIdx = Math.min(vehicle.getTargetIntersection(), VALID_INTERSECTIONS.length - 1);
            int intersectionX = VALID_INTERSECTIONS[targetIdx];

            switch (vehicle.getDirection()) {
                case LEFT:
                    // Si va hacia oeste (arriba), gira hacia el SUR; si va hacia este (abajo), gira hacia el NORTE
                    // Ajustar X al centro del carril correspondiente de la calle (no sobre la línea amarilla)
                    if (westbound) {
                        // Tráfico hacia el SUR usa el lado OESTE de la calle
                        endX = intersectionX + STREET_LANE_CENTER_WEST;
                        endY = 500;
                    } else {
                        // Tráfico hacia el NORTE usa el lado ESTE de la calle
                        endX = intersectionX + STREET_LANE_CENTER_EAST;
                        endY = 100;
                    }
                    break;
                case RIGHT:
                    // Si va hacia oeste (arriba), gira hacia el NORTE; si va hacia este (abajo), gira hacia el SUR
                    // Ajustar X al centro del carril correcto
                    if (westbound) {
                        // NORTE => lado ESTE
                        endX = intersectionX + STREET_LANE_CENTER_EAST;
                        endY = 100;
                    } else {
                        // SUR => lado OESTE
                        endX = intersectionX + STREET_LANE_CENTER_WEST;
                        endY = 500;
                    }
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
                        // SUR desde avenida superior - mantener X en centro de carril OESTE de la calle
                        postTurnTargetX = endX;
                        postTurnTargetY = endY + POST_TURN_DISTANCE;
                    } else {
                        // NORTE desde avenida inferior - mantener X en centro de carril ESTE de la calle
                        postTurnTargetX = endX;
                        postTurnTargetY = endY - POST_TURN_DISTANCE;
                    }
                } else if (dir == Direction.RIGHT) {
                    if (westbound) {
                        // NORTE desde avenida superior - centro de carril ESTE
                        postTurnTargetX = endX;
                        postTurnTargetY = endY - POST_TURN_DISTANCE;
                    } else {
                        // SUR desde avenida inferior - centro de carril OESTE
                        postTurnTargetX = endX;
                        postTurnTargetY = endY + POST_TURN_DISTANCE;
                    }
                } else if (dir == Direction.U_TURN) {
                    if (westbound) {
                        // Venía hacia el oeste (avenida superior). Tras U-TURN pasa a avenida inferior hacia el ESTE
                        // Nuevo sentido efectivo: este (westbound=false)
                        uturnWestboundAfter = false;
                    } else {
                        // Venía hacia el este (avenida inferior). Tras U-TURN pasa a avenida superior hacia el OESTE
                        // Nuevo sentido efectivo: oeste (westbound=true)
                        uturnWestboundAfter = true;
                    }
                    reincorporatedAfterUTurn = true;
                    // Ajustar una pequeña proyección inicial para evitar quedarse dentro de la zona de giro
                    postTurnTargetX = endX + (uturnWestboundAfter ? -POST_TURN_DISTANCE : POST_TURN_DISTANCE);
                    postTurnTargetY = endY;
                } else {
                    // Caso por defecto (nunca debería llegar aquí)
                    postTurnTargetX = endX + (westbound ? -POST_TURN_DISTANCE : POST_TURN_DISTANCE);
                    postTurnTargetY = endY;
                }

                // Si es U-TURN queremos reincorporarnos y continuar el ciclo normal (APPROACHING)
                if (dir == Direction.U_TURN) {
                    // Colocar el vehículo exactamente en el punto final del giro antes de reanudar
                    vehicle.updatePosition(endX, endY);
                    // Reiniciar banderas de semáforos para nuevo sentido
                    hasCrossedLastLight = false;
                    currentState = State.APPROACHING; // Volver a movimiento normal respetando semáforos
                } else {
                    // Mantener comportamiento anterior para otros giros
                    currentState = State.POST_TURN;
                }
            });

            transition.play();
        });
    }

    /**
     * Determina el sentido efectivo (westbound/este-oeste) después de una vuelta en U.
     * No altera la lógica original del vehículo; sólo añade una interpretación adicional
     * para permitir que el ciclo de movimiento y semáforos continúe tras el U-TURN.
     */
    private boolean effectiveWestbound() {
        if (reincorporatedAfterUTurn) {
            return uturnWestboundAfter;
        }
        return vehicle.getHighwayLane().isWestbound();
    }

    // Verifica si es seguro realizar un giro a la derecha en rojo en la intersección indicada (por X)
    // Se considera seguro si no hay vehículos cruzando en la caja de intersección cercana a ese X
    private boolean isSafeToRightTurnOnRed(int intersectionX) {
        // Definir una caja de seguridad alrededor de la intersección
        double left = intersectionX - 10;
        double right = intersectionX + 90; // coincide con ancho estimado de intersección
        double top = 210 - 20;   // borde superior de la avenida superior con margen
        double bottom = 310 + 120; // incluye avenida inferior con margen

        // Para vehículos de calle, ajustar la caja según el StartPoint
        if (!vehicle.isHighwayVehicle()) {
            StartPoint sp = vehicle.getStartPoint();

            // Para vehículos que vienen del norte (NORTH_L, NORTH_D),
            // verificar tráfico en la avenida superior
            if (sp == StartPoint.NORTH_L || sp == StartPoint.NORTH_D) {
                top = 210 - 20;
                bottom = 310; // Solo vigilar la avenida superior
            }

            // Para vehículos que vienen del sur (SOUTH_L, SOUTH_D),
            // verificar tráfico en la avenida inferior
            if (sp == StartPoint.SOUTH_L || sp == StartPoint.SOUTH_D) {
                top = 310;
                bottom = 410 + 20; // Solo vigilar la avenida inferior
            }
        }

        Collection<Vehicle> all = lifecycleManager.getAllActiveVehicles();
        for (Vehicle other : all) {
            if (!other.isActive() || other.getId() == vehicle.getId()) continue;

            double ox = other.getX();
            double oy = other.getY();

            boolean insideBox = (ox >= left && ox <= right && oy >= top && oy <= bottom);

            // Si un vehículo está marcado en intersección o se encuentra dentro del área crítica, no es seguro
            if (insideBox || other.isInIntersection()) {
                // Mejorado: verificamos la dirección relativa para ser más precisos
                // Por ejemplo, si el otro vehículo se está alejando, podría ser seguro

                // Calcular si el otro vehículo se está alejando o acercando a la intersección
                boolean isMovingAway = false;

                if (other.isHighwayVehicle()) {
                    boolean otherWestbound = other.getHighwayLane().isWestbound();

                    // Si está en avenida superior (westbound) y ya pasó la intersección, se está alejando
                    if (otherWestbound && ox < intersectionX) {
                        isMovingAway = true;
                    }

                    // Si está en avenida inferior (eastbound) y ya pasó la intersección, se está alejando
                    if (!otherWestbound && ox > intersectionX + 80) {
                        isMovingAway = true;
                    }
                }

                // Si el otro vehículo se está alejando, podríamos considerar seguro
                // Pero para ser conservadores, solo para vehículos que están muy lejos
                if (isMovingAway) {
                    double distance = Math.hypot(vehicle.getX() - ox, vehicle.getY() - oy);
                    if (distance > 150) {
                        continue; // Suficientemente lejos y alejándose, ignorar
                    }
                }

                // Para mantenerlo simple y conservador, si está en la caja es inseguro
                return false;
            }
        }
        // Sin vehículos cruzando o en caja, consideramos seguro con precaución
        return true;
    }

    // Pequeño descriptor para saber si estamos en una avenida y cuál
    private static class AvenueContext {
        final boolean isUpper; // true = avenida superior (y ~210..310), false = inferior (y ~310..410)
        AvenueContext(boolean isUpper) { this.isUpper = isUpper; }
    }

    // Detecta si Y pertenece a alguna avenida; retorna cuál, o null si no está en avenida
    private AvenueContext detectAvenueContext(double y) {
        // Rango aproximado basado en drawRoadsWithLanes: upper [210,310], lower [310,410]
        if (y >= 210 && y <= 310) return new AvenueContext(true);
        if (y >= 310 && y <= 410) return new AvenueContext(false);
        return null;
    }

    // Obtiene la X de la intersección (calle vertical) a la que llega un vehículo de calle en la primera intersección
    private int getIntersectionXForStreet(StartPoint sp) {
        // Dos calles principales: izquierda en ~390, derecha en ~690
        return switch (sp) {
            case NORTH_L, SOUTH_L -> 390; // Calle izquierda
            case NORTH_D, SOUTH_D -> 690; // Calle derecha
            case EAST, WEST -> 390;       // No aplica, valor por defecto
        };
    }

    // Determina si la intersección es la primera para un vehículo de calle
    private boolean isFirstIntersectionForStreetVehicle(StartPoint sp, int intersectionX) {
        // Para vehículos que vienen del norte (NORTH_L, NORTH_D), la primera intersección
        // es la avenida superior
        if (sp == StartPoint.NORTH_L || sp == StartPoint.NORTH_D) {
            return true; // Siempre es la primera intersección (solo cruzan una avenida)
        }

        // Para vehículos que vienen del sur (SOUTH_L, SOUTH_D), la primera intersección
        // es la avenida inferior
        if (sp == StartPoint.SOUTH_L || sp == StartPoint.SOUTH_D) {
            return true; // Siempre es la primera intersección (solo cruzan una avenida)
        }

        // Para vehículos EAST y WEST (que no deberían llegar aquí), consideramos primera
        // la intersección 390 (más cercana al oeste)
        return intersectionX == 390;
    }
}