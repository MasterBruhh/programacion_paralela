package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.*;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Sistema avanzado de detección de colisiones para vehículos de calle y autopista.
 * Implementa algoritmos de predicción y prevención de colisiones y lógica de prioridad
 * y cedencia para vehículos de emergencia. Además incorpora un mecanismo de "desatasco"
 * (reducción temporal del radio de colisión) para vehículos que llevan tiempo bloqueados.
 */
public class CollisionDetector {
    private static final Logger logger = Logger.getLogger(CollisionDetector.class.getName());

    // Geometría y tiempo de predicción
    private static final double VEHICLE_RADIUS = 15.0;
    private static final double SAFETY_MARGIN = 10.0;
    private static final double LANE_WIDTH = 30.0;
    private static final double PREDICTION_TIME = 2.0; // segundos
    private static final double AHEAD_DETECTION_ANGLE = 30.0; // grados

    // Distancias mínimas y umbrales (valores ajustados y coherentes entre ramas)
    private static final double AHEAD_MAX_LATERAL_DISTANCE = 25.0; // lateral para detectar "ahead"
    private static final double STREET_MIN_DISTANCE = 30.0;
    private static final double HIGHWAY_MIN_DISTANCE = 45.0;
    private static final double EMERGENCY_MIN_DISTANCE = 20.0; // Emergencias pueden acercarse más
    private static final double EMERGENCY_YIELD_DISTANCE = 100.0; // Distancia para ceder paso a emergencia
    private static final int STUCK_THRESHOLD = 6; // umbral para considerar 'atascado'

    // Mapa para seguimiento de vehículos bloqueados
    private final Map<Long, Integer> stuckVehicles = new HashMap<>();

    /**
     * Verifica si un vehículo puede moverse a una posición sin colisionar
     * Incluye lógica especial para vehículos de emergencia y para vehículos "atascados".
     */
    public boolean canMove(Vehicle vehicle, double nextX, double nextY,
                           Collection<Vehicle> activeVehicles) {

        // 1) Si es emergencia, aplicar reglas especiales (puede pasar con más libertad
        //    pero seguirá evitando colisiones críticas).
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            return canMoveEmergency(vehicle, nextX, nextY, activeVehicles);
        }

        // 2) Si está atascado (contador alto), permitir movimiento con radio reducido
        if (isVehicleStuck(vehicle)) {
            return canMoveWithReducedCollisionRadius(vehicle, nextX, nextY, activeVehicles);
        }

        // 3) Si está cambiando de carril, verificar cambio seguro
        if (vehicle.isChangingLane()) {
            return canChangeLaneSafely(vehicle, nextX, nextY, activeVehicles);
        }

        // 4) Verificación normal de colisiones
        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;

            // Si el otro es emergencia y están en misma trayectoria, dar más espacio
            if (other.getType() == VehicleType.EMERGENCY) {
                if (areInSamePath(vehicle, other)) {
                    double distance = calculateDistance(nextX, nextY, other.getX(), other.getY());
                    if (distance < EMERGENCY_YIELD_DISTANCE) {
                        logger.fine("Vehículo " + vehicle.getId() +
                                " cediendo espacio a emergencia " + other.getId());
                        return false; // No moverse para dar espacio
                    }
                }
            }

            // Si ambos son de autopista y NO hay cambio de carril, ignorar vehículos de otros carriles
            if (vehicle.isHighwayVehicle() && other.isHighwayVehicle() && !vehicle.isChangingLane()) {
                if (vehicle.getCurrentLane() != other.getCurrentLane()) {
                    continue;
                }
            }

            // Calcular distancia mínima requerida
            double minDistance = calculateMinDistance(vehicle, other);

            // Verificar colisión actual
            double distance = calculateDistance(nextX, nextY, other.getX(), other.getY());
            if (distance < minDistance) {
                // Verificar si están en trayectorias que se cruzan
                if (willCollide(vehicle, nextX, nextY, other)) {
                    // Incrementar contador de bloqueo (siempre que no sea emergencia)
                    incrementStuckCounter(vehicle);
                    logger.fine("Colisión detectada: Vehículo " + vehicle.getId() +
                            " con " + other.getId() + " a distancia " + distance);
                    return false;
                }
            }

            // Predicción de colisión futura en autopista
            if (vehicle.isHighwayVehicle() && other.isHighwayVehicle()) {
                if (predictHighwayCollision(vehicle, nextX, nextY, other)) {
                    incrementStuckCounter(vehicle);
                    return false;
                }
            }
        }

        // Si llegamos aquí, puede moverse: resetear contador de bloqueo
        resetStuckCounter(vehicle);
        return true;
    }

    /**
     * Verifica movimiento con radio de colisión reducido para "desatascar"
     */
    private boolean canMoveWithReducedCollisionRadius(Vehicle vehicle, double nextX, double nextY,
                                                      Collection<Vehicle> activeVehicles) {
        Integer stuckCount = stuckVehicles.get(vehicle.getId());
        double reductionFactor = 0.5; // valor por defecto

        if (stuckCount != null) {
            if (stuckCount > 15) reductionFactor = 0.2;
            else if (stuckCount > 10) reductionFactor = 0.3;
            else reductionFactor = 0.5;
        }

        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;

            double reducedDistance = calculateMinDistance(vehicle, other) * reductionFactor;
            double distance = calculateDistance(nextX, nextY, other.getX(), other.getY());
            if (distance < reducedDistance) {
                return false;
            }
        }

        return true;
    }

    /**
     * Verificación de movimiento especial para vehículos de emergencia
     */
    private boolean canMoveEmergency(Vehicle emergency, double nextX, double nextY,
                                     Collection<Vehicle> activeVehicles) {
        // Las emergencias evitan colisiones directas pero tienen reglas más flexibles
        for (Vehicle other : activeVehicles) {
            if (other.getId() == emergency.getId() || !other.isActive()) continue;

            double distance = calculateDistance(nextX, nextY, other.getX(), other.getY());

            // Evitar colisiones muy cercanas
            if (distance < EMERGENCY_MIN_DISTANCE) {
                // Si el otro vehículo está cediendo, permitir una cercanía razonable
                if (other.isEmergencyYielding() && distance > VEHICLE_RADIUS) {
                    continue;
                }

                logger.fine("Emergencia " + emergency.getId() +
                        " evitando colisión crítica con " + other.getId());
                return false;
            }
        }

        return true;
    }

    /**
     * Verifica si un vehículo está atascado (según contador)
     */
    private boolean isVehicleStuck(Vehicle vehicle) {
        Integer stuckCount = stuckVehicles.get(vehicle.getId());
        return stuckCount != null && stuckCount > STUCK_THRESHOLD;
    }

    private void incrementStuckCounter(Vehicle vehicle) {
        int count = stuckVehicles.getOrDefault(vehicle.getId(), 0);
        stuckVehicles.put(vehicle.getId(), count + 1);
    }

    private void resetStuckCounter(Vehicle vehicle) {
        stuckVehicles.put(vehicle.getId(), 0);
    }

    /**
     * Detecta vehículos adelante en el mismo carril o trayectoria.
     * Prioriza detección por proximidad y aplica umbrales laterales dependiendo del tipo.
     */
    public VehicleAheadInfo detectVehicleAhead(Vehicle vehicle,
                                               Collection<Vehicle> activeVehicles) {
        Vehicle closestVehicle = null;
        double closestDistance = Double.MAX_VALUE;
        boolean emergencyAhead = false;

        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;

            if (!areInSamePath(vehicle, other)) continue;

            if (!isVehicleAhead(vehicle, other)) continue;

            double distance = calculateDistance(vehicle.getX(), vehicle.getY(), other.getX(), other.getY());

            // Priorizar emergencias
            if (other.getType() == VehicleType.EMERGENCY) {
                if (!emergencyAhead || distance < closestDistance) {
                    closestDistance = distance;
                    closestVehicle = other;
                    emergencyAhead = true;
                }
            } else if (!emergencyAhead && distance < closestDistance) {
                closestDistance = distance;
                closestVehicle = other;
            }
        }

        return closestVehicle != null ?
                new VehicleAheadInfo(closestVehicle, closestDistance, emergencyAhead) : null;
    }

    /**
     * Detecta vehículos de emergencia en un radio específico
     */
    public List<Vehicle> detectEmergencyVehicles(Vehicle vehicle, double radius,
                                                 Collection<Vehicle> activeVehicles) {
        List<Vehicle> emergencies = new ArrayList<>();
        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;
            if (other.getType() != VehicleType.EMERGENCY) continue;

            double distance = calculateDistance(vehicle.getX(), vehicle.getY(), other.getX(), other.getY());
            if (distance <= radius) emergencies.add(other);
        }
        return emergencies;
    }

    /**
     * Detecta todos los vehículos en un radio específico
     */
    public List<Vehicle> detectNearbyVehicles(Vehicle vehicle, double radius,
                                              Collection<Vehicle> activeVehicles) {
        List<Vehicle> nearby = new ArrayList<>();
        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;

            double distance = calculateDistance(vehicle.getX(), vehicle.getY(), other.getX(), other.getY());
            if (distance <= radius) nearby.add(other);
        }
        return nearby;
    }

    /**
     * Calcula la velocidad segura basada en el entorno.
     * Si hay emergencia adelante, da preferencia a detenerse/ceder.
     */
    public double calculateSafeSpeed(double currentSpeed, double distanceAhead,
                                     boolean isHighway, boolean emergencyAhead) {

        if (emergencyAhead) {
            double stopDistance = EMERGENCY_YIELD_DISTANCE;
            if (distanceAhead < stopDistance) return 0.0;
            return currentSpeed * 0.3;
        }

        double minDistance = isHighway ? HIGHWAY_MIN_DISTANCE : STREET_MIN_DISTANCE;
        double stopDistance = minDistance * 0.5;
        double slowDistance = minDistance * 2.0;

        if (distanceAhead < stopDistance) {
            return 0.0;
        } else if (distanceAhead < minDistance) {
            double ratio = (distanceAhead - stopDistance) / (minDistance - stopDistance);
            double smoothFactor = Math.pow(Math.max(0.0, ratio), 1.5);
            return currentSpeed * smoothFactor * 0.7;
        } else if (distanceAhead < slowDistance) {
            double ratio = (distanceAhead - minDistance) / (slowDistance - minDistance);
            double speedFactor = 0.8 + (0.2 * ratio);
            return currentSpeed * speedFactor;
        }

        return currentSpeed;
    }

    /**
     * Comprueba si debe ceder paso a un vehículo de emergencia (método de apoyo).
     */
    public boolean shouldYieldToEmergency(Vehicle vehicle, Vehicle emergency) {
        if (vehicle.getType() == VehicleType.EMERGENCY) return false;
        if (emergency.getType() != VehicleType.EMERGENCY) return false;

        double distance = calculateDistance(vehicle.getX(), vehicle.getY(), emergency.getX(), emergency.getY());
        if (distance < EMERGENCY_YIELD_DISTANCE) {
            if (areInSamePath(vehicle, emergency) && isEmergencyApproaching(vehicle, emergency)) return true;
            if (willPathsCross(vehicle, emergency)) return true;
        }
        return false;
    }

    private boolean isEmergencyApproaching(Vehicle vehicle, Vehicle emergency) {
        if (vehicle.isHighwayVehicle() && emergency.isHighwayVehicle()) {
            boolean sameDirection = vehicle.getHighwayLane().isWestbound() == emergency.getHighwayLane().isWestbound();
            if (!sameDirection) return false;
            if (vehicle.getHighwayLane().isWestbound()) return emergency.getX() > vehicle.getX();
            else return emergency.getX() < vehicle.getX();
        }

        if (!vehicle.isHighwayVehicle() && !emergency.isHighwayVehicle()) {
            StartPoint vsp = vehicle.getStartPoint();
            return switch (vsp) {
                case NORTH_L, NORTH_D -> emergency.getY() < vehicle.getY();
                case SOUTH_L, SOUTH_D -> emergency.getY() > vehicle.getY();
                case EAST -> emergency.getX() > vehicle.getX();
                case WEST -> emergency.getX() < vehicle.getX();
            };
        }

        return false;
    }

    private boolean willPathsCross(Vehicle v1, Vehicle v2) {
        if (v1.isHighwayVehicle() != v2.isHighwayVehicle()) {
            double intersectionY1 = 260;
            double intersectionY2 = 360;
            if (Math.abs(v1.getY() - intersectionY1) < 100 || Math.abs(v1.getY() - intersectionY2) < 100) {
                if (Math.abs(v2.getY() - intersectionY1) < 100 || Math.abs(v2.getY() - intersectionY2) < 100) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifica si es seguro cambiar de carril
     */
    private boolean canChangeLaneSafely(Vehicle vehicle, double nextX, double nextY,
                                        Collection<Vehicle> activeVehicles) {
        if (!vehicle.isHighwayVehicle()) return true;

        double safeZoneFront = vehicle.getType() == VehicleType.EMERGENCY ?
                EMERGENCY_MIN_DISTANCE : HIGHWAY_MIN_DISTANCE;
        double safeZoneBack = safeZoneFront * 0.7;
        double safeZoneSide = LANE_WIDTH;

        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;
            if (!other.isHighwayVehicle()) continue;

            if (vehicle.getType() == VehicleType.EMERGENCY && other.isEmergencyYielding()) continue;

            double dx = Math.abs(nextX - other.getX());
            double dy = other.getY() - nextY;

            if (dx < safeZoneSide) {
                boolean isBehind = vehicle.getHighwayLane().isNorthbound() ? dy < 0 : dy > 0;
                double longitudinalDistance = Math.abs(dy);
                double requiredDistance = isBehind ? safeZoneBack : safeZoneFront;

                if (other.getSpeed() > vehicle.getSpeed() && isBehind) requiredDistance *= 1.5;

                if (longitudinalDistance < requiredDistance) {
                    logger.fine("Cambio de carril bloqueado: Vehículo " + other.getId() + " muy cerca");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Predice colisiones futuras en autopista
     */
    private boolean predictHighwayCollision(Vehicle vehicle, double nextX, double nextY,
                                            Vehicle other) {
        if (vehicle.getType() == VehicleType.EMERGENCY || other.getType() == VehicleType.EMERGENCY) {
            return false; // evitar predicción restrictiva para emergencias
        }

        int laneDiff = Math.abs(vehicle.getCurrentLane() - other.getCurrentLane());
        if (laneDiff != 0 && !(vehicle.isChangingLane() || other.isChangingLane())) return false;

        double vehicleFutureX;
        if (vehicle.getHighwayLane().isWestbound()) {
            vehicleFutureX = nextX - (vehicle.getSpeed() / 10) * PREDICTION_TIME * 10;
        } else {
            vehicleFutureX = nextX + (vehicle.getSpeed() / 10) * PREDICTION_TIME * 10;
        }

        double otherFutureX = other.getX();
        if (other.isHighwayVehicle()) {
            if (other.getHighwayLane().isWestbound()) otherFutureX = other.getX() - (other.getSpeed() / 10) * PREDICTION_TIME * 10;
            else otherFutureX = other.getX() + (other.getSpeed() / 10) * PREDICTION_TIME * 10;
        }

        double futureDistance;
        if (laneDiff == 0) futureDistance = Math.abs(vehicleFutureX - otherFutureX);
        else futureDistance = calculateDistance(vehicleFutureX, nextY, otherFutureX, other.getY());

        double minSafeDistance = HIGHWAY_MIN_DISTANCE * 0.8;
        return futureDistance < minSafeDistance;
    }

    /**
     * Verifica si dos vehículos colisionarán
     */
    private boolean willCollide(Vehicle v1, double x1, double y1, Vehicle v2) {
        // Si están en intersección, verificar trayectorias
        if (v1.isInIntersection() || v2.isInIntersection()) {
            return checkIntersectionCollision(v1, x1, y1, v2);
        }

        // Para vehículos en el mismo carril
        if (areInSamePath(v1, v2)) {
            return true;
        }

        return false;
    }

    private boolean checkIntersectionCollision(Vehicle v1, double x1, double y1, Vehicle v2) {
        if (v1.getType() == VehicleType.EMERGENCY || v2.getType() == VehicleType.EMERGENCY) {
            return false; // Prioridad a emergencias dentro de intersecciones
        }

        double intersectionCenterX = 430;
        double intersectionCenterY = 295;
        double intersectionRadius = 100;

        boolean v1InIntersection = calculateDistance(x1, y1, intersectionCenterX, intersectionCenterY) < intersectionRadius;
        boolean v2InIntersection = calculateDistance(v2.getX(), v2.getY(), intersectionCenterX, intersectionCenterY) < intersectionRadius;

        if (v1InIntersection && v2InIntersection) return doTrajectoriesIntersect(v1, v2);
        return false;
    }

    private boolean doTrajectoriesIntersect(Vehicle v1, Vehicle v2) {
        if (v1.getType() == VehicleType.EMERGENCY || v2.getType() == VehicleType.EMERGENCY) return false;
        if (v1.getStartPoint() == null || v2.getStartPoint() == null) return false;

        StartPoint sp1 = v1.getStartPoint();
        StartPoint sp2 = v2.getStartPoint();
        Direction d1 = v1.getDirection();
        Direction d2 = v2.getDirection();

        boolean v1NS = isNorthEntry(sp1) || isSouthEntry(sp1);
        boolean v2NS = isNorthEntry(sp2) || isSouthEntry(sp2);
        boolean v1EW = sp1 == StartPoint.EAST || sp1 == StartPoint.WEST;
        boolean v2EW = sp2 == StartPoint.EAST || sp2 == StartPoint.WEST;

        if (((v1NS && v2EW) || (v2NS && v1EW)) &&
                d1 == Direction.STRAIGHT && d2 == Direction.STRAIGHT) {
            return true;
        }
        return false;
    }

    /**
     * Calcula la distancia mínima requerida entre dos vehículos (ajustada por tipo y velocidad)
     */
    private double calculateMinDistance(Vehicle v1, Vehicle v2) {
        double baseDistance = v1.isHighwayVehicle() ? HIGHWAY_MIN_DISTANCE : STREET_MIN_DISTANCE;

        if (v1.getType() == VehicleType.EMERGENCY || v2.getType() == VehicleType.EMERGENCY) {
            baseDistance = EMERGENCY_MIN_DISTANCE;
        } else if (v1.getType() == VehicleType.HEAVY || v2.getType() == VehicleType.HEAVY) {
            baseDistance *= 1.1;
        } else if (v1.getType() == VehicleType.PUBLIC_TRANSPORT || v2.getType() == VehicleType.PUBLIC_TRANSPORT) {
            baseDistance *= 1.05;
        }

        double speedFactor = 1.0 + (Math.max(v1.getSpeed(), v2.getSpeed()) / 100.0) * 0.15;
        return baseDistance * speedFactor;
    }

    private double getVehicleSizeFactor(Vehicle vehicle) {
        switch (vehicle.getType()) {
            case NORMAL: return 1.0;
            case EMERGENCY: return 1.2;
            case PUBLIC_TRANSPORT: return 1.3;
            case HEAVY: return 1.4;
            default: return 1.0;
        }
    }

    /**
     * Verifica si dos vehículos están en la misma trayectoria.
     */
    private boolean areInSamePath(Vehicle v1, Vehicle v2) {
        if (v1.isHighwayVehicle() && v2.isHighwayVehicle()) {
            boolean sameDirection = v1.getHighwayLane().isWestbound() == v2.getHighwayLane().isWestbound();
            boolean sameLane = v1.getCurrentLane() == v2.getCurrentLane();
            return sameLane && sameDirection;
        }

        if (!v1.isHighwayVehicle() && !v2.isHighwayVehicle()) {
            boolean sameStreet = v1.getStartPoint() == v2.getStartPoint();
            double lateralThreshold = AHEAD_MAX_LATERAL_DISTANCE;
            if (v1.getType() == VehicleType.HEAVY || v1.getType() == VehicleType.PUBLIC_TRANSPORT ||
                    v2.getType() == VehicleType.HEAVY || v2.getType() == VehicleType.PUBLIC_TRANSPORT) {
                lateralThreshold *= 1.5;
            }

            if (isVerticalStreet(v1.getStartPoint())) {
                double xDiff = Math.abs(v1.getX() - v2.getX());
                return sameStreet && xDiff < lateralThreshold;
            } else {
                double yDiff = Math.abs(v1.getY() - v2.getY());
                return sameStreet && yDiff < lateralThreshold;
            }
        }

        return false;
    }

    /**
     * Verifica si un vehículo está adelante de otro.
     */
    private boolean isVehicleAhead(Vehicle current, Vehicle other) {
        if (current.isHighwayVehicle() && other.isHighwayVehicle()) {
            boolean westbound = current.getHighwayLane().isWestbound();
            double lateralThreshold = AHEAD_MAX_LATERAL_DISTANCE;
            if (current.getType() == VehicleType.HEAVY || current.getType() == VehicleType.PUBLIC_TRANSPORT ||
                    other.getType() == VehicleType.HEAVY || other.getType() == VehicleType.PUBLIC_TRANSPORT) {
                lateralThreshold *= 1.5;
            }

            if (westbound) {
                return other.getX() < current.getX() && Math.abs(other.getY() - current.getY()) < lateralThreshold;
            } else {
                return other.getX() > current.getX() && Math.abs(other.getY() - current.getY()) < lateralThreshold;
            }
        }

        if (!current.isHighwayVehicle() && !other.isHighwayVehicle()) {
            double lateralThreshold = AHEAD_MAX_LATERAL_DISTANCE;
            if (current.getType() == VehicleType.HEAVY || current.getType() == VehicleType.PUBLIC_TRANSPORT ||
                    other.getType() == VehicleType.HEAVY || other.getType() == VehicleType.PUBLIC_TRANSPORT) {
                lateralThreshold *= 1.5;
            }

            if (isVerticalStreet(current.getStartPoint())) {
                double xDiff = Math.abs(current.getX() - other.getX());
                boolean isAligned = xDiff < lateralThreshold;
                if (isNorthEntry(current.getStartPoint())) {
                    return other.getY() > current.getY() && isAligned;
                } else {
                    return other.getY() < current.getY() && isAligned;
                }
            } else {
                double yDiff = Math.abs(current.getY() - other.getY());
                boolean isAligned = yDiff < lateralThreshold;
                if (current.getStartPoint() == StartPoint.EAST) {
                    return other.getX() < current.getX() && isAligned;
                } else {
                    return other.getX() > current.getX() && isAligned;
                }
            }
        }

        return false;
    }

    private boolean isVerticalStreet(StartPoint sp) {
        return isNorthEntry(sp) || isSouthEntry(sp);
    }

    public double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private boolean isNorthEntry(StartPoint sp) {
        return sp == StartPoint.NORTH_L || sp == StartPoint.NORTH_D;
    }

    private boolean isSouthEntry(StartPoint sp) {
        return sp == StartPoint.SOUTH_L || sp == StartPoint.SOUTH_D;
    }

    /**
     * Información sobre un vehículo detectado adelante
     */
    public static class VehicleAheadInfo {
        private final Vehicle vehicle;
        private final double distance;
        private final boolean isEmergency;

        public VehicleAheadInfo(Vehicle vehicle, double distance) {
            this(vehicle, distance, false);
        }

        public VehicleAheadInfo(Vehicle vehicle, double distance, boolean isEmergency) {
            this.vehicle = vehicle;
            this.distance = distance;
            this.isEmergency = isEmergency;
        }

        public Vehicle getVehicle() { return vehicle; }
        public double getDistance() { return distance; }
        public boolean isEmergency() { return isEmergency; }
    }
}