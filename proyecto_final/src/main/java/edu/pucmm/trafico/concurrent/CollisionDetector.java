package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.*;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Sistema avanzado de detección de colisiones para vehículos de calle y autopista.
 * Implementa algoritmos de predicción y prevención de colisiones.
 */
public class CollisionDetector {
    private static final Logger logger = Logger.getLogger(CollisionDetector.class.getName());

    // Configuración de distancias
    private static final double VEHICLE_RADIUS = 15.0;
    private static final double SAFETY_MARGIN = 10.0;
    private static final double STREET_MIN_DISTANCE = 40.0;
    private static final double HIGHWAY_MIN_DISTANCE = 60.0;
    private static final double EMERGENCY_MIN_DISTANCE = 80.0;
    private static final double LANE_WIDTH = 30.0;
    private static final double PREDICTION_TIME = 2.0; // segundos

    /**
     * Verifica si un vehículo puede moverse a una posición sin colisionar
     */
    public boolean canMove(Vehicle vehicle, double nextX, double nextY,
                           Collection<Vehicle> activeVehicles) {

        // Verificación especial para cambios de carril
        if (vehicle.isChangingLane()) {
            return canChangeLaneSafely(vehicle, nextX, nextY, activeVehicles);
        }

        // Verificación normal de colisiones
        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;

            // Calcular distancia mínima requerida
            double minDistance = calculateMinDistance(vehicle, other);

            // Verificar colisión actual
            double distance = calculateDistance(nextX, nextY, other.getX(), other.getY());
            if (distance < minDistance) {
                // Verificar si están en trayectorias que se cruzan
                if (willCollide(vehicle, nextX, nextY, other)) {
                    logger.fine("Colisión detectada: Vehículo " + vehicle.getId() +
                            " con " + other.getId() + " a distancia " + distance);
                    return false;
                }
            }

            // Predicción de colisión futura
            if (vehicle.isHighwayVehicle() && other.isHighwayVehicle()) {
                if (predictHighwayCollision(vehicle, nextX, nextY, other)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Detecta vehículos adelante en el mismo carril o trayectoria
     */
    public VehicleAheadInfo detectVehicleAhead(Vehicle vehicle,
                                               Collection<Vehicle> activeVehicles) {
        Vehicle closestVehicle = null;
        double closestDistance = Double.MAX_VALUE;

        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;

            // Verificar si están en la misma trayectoria
            if (areInSamePath(vehicle, other)) {
                // Verificar si está adelante
                if (isVehicleAhead(vehicle, other)) {
                    double distance = calculateDistance(
                            vehicle.getX(), vehicle.getY(),
                            other.getX(), other.getY()
                    );

                    if (distance < closestDistance) {
                        closestDistance = distance;
                        closestVehicle = other;
                    }
                }
            }
        }

        return closestVehicle != null ?
                new VehicleAheadInfo(closestVehicle, closestDistance) : null;
    }

    /**
     * Detecta todos los vehículos en un radio específico
     */
    public List<Vehicle> detectNearbyVehicles(Vehicle vehicle, double radius,
                                              Collection<Vehicle> activeVehicles) {
        List<Vehicle> nearby = new ArrayList<>();

        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;

            double distance = calculateDistance(
                    vehicle.getX(), vehicle.getY(),
                    other.getX(), other.getY()
            );

            if (distance <= radius) {
                nearby.add(other);
            }
        }

        return nearby;
    }

    /**
     * Calcula la velocidad segura basada en el entorno
     */
    public double calculateSafeSpeed(double currentSpeed, double distanceAhead,
                                     boolean isHighway) {
        double minDistance = isHighway ? HIGHWAY_MIN_DISTANCE : STREET_MIN_DISTANCE;
        double stopDistance = minDistance * 0.5;

        if (distanceAhead < stopDistance) {
            return 0; // Detenerse completamente
        } else if (distanceAhead < minDistance) {
            // Reducir velocidad proporcionalmente
            double factor = (distanceAhead - stopDistance) / (minDistance - stopDistance);
            return currentSpeed * factor * 0.5; // Factor adicional de seguridad
        } else if (distanceAhead < minDistance * 1.5) {
            // Reducción suave
            return currentSpeed * 0.8;
        }

        return currentSpeed;
    }

    /**
     * Verifica si es seguro cambiar de carril
     */
    private boolean canChangeLaneSafely(Vehicle vehicle, double nextX, double nextY,
                                        Collection<Vehicle> activeVehicles) {
        if (!vehicle.isHighwayVehicle()) return true;

        // Zona de seguridad para cambio de carril
        double safeZoneFront = HIGHWAY_MIN_DISTANCE;
        double safeZoneBack = HIGHWAY_MIN_DISTANCE * 0.7;
        double safeZoneSide = LANE_WIDTH;

        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;
            if (!other.isHighwayVehicle()) continue;

            // Calcular posición relativa
            double dx = Math.abs(nextX - other.getX());
            double dy = other.getY() - nextY;

            // Verificar si está en la zona de peligro
            if (dx < safeZoneSide) {
                boolean isBehind = vehicle.getHighwayLane().isNorthbound() ?
                        dy < 0 : dy > 0;

                double longitudinalDistance = Math.abs(dy);
                double requiredDistance = isBehind ? safeZoneBack : safeZoneFront;

                if (longitudinalDistance < requiredDistance) {
                    // Considerar velocidades relativas
                    if (other.getSpeed() > vehicle.getSpeed() && isBehind) {
                        requiredDistance *= 1.5; // Más espacio si viene más rápido
                    }

                    if (longitudinalDistance < requiredDistance) {
                        logger.fine("Cambio de carril bloqueado: Vehículo " +
                                other.getId() + " muy cerca");
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Predice colisiones futuras en autopista (movimiento horizontal)
     */
    private boolean predictHighwayCollision(Vehicle vehicle, double nextX, double nextY,
                                            Vehicle other) {
        // Solo predecir si están en carriles adyacentes o el mismo
        int laneDiff = Math.abs(vehicle.getCurrentLane() - other.getCurrentLane());
        if (laneDiff > 1) return false;

        // Calcular posiciones futuras en X (movimiento horizontal)
        double vehicleFutureX = nextX;
        double otherFutureX = other.getX();

        // Determinar dirección de movimiento
        if (vehicle.getHighwayLane().isWestbound()) {
            vehicleFutureX = nextX - (vehicle.getSpeed() / 10) * PREDICTION_TIME * 10;
        } else {
            vehicleFutureX = nextX + (vehicle.getSpeed() / 10) * PREDICTION_TIME * 10;
        }

        if (other.isHighwayVehicle()) {
            if (other.getHighwayLane().isWestbound()) {
                otherFutureX = other.getX() - (other.getSpeed() / 10) * PREDICTION_TIME * 10;
            } else {
                otherFutureX = other.getX() + (other.getSpeed() / 10) * PREDICTION_TIME * 10;
            }
        }

        // Verificar si habrá superposición (mantener Y constante en autopista)
        double futureDistance = calculateDistance(vehicleFutureX, nextY,
                otherFutureX, other.getY());

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
            return true; // La distancia ya fue verificada
        }

        return false;
    }

    /**
     * Verifica colisiones en intersección
     */
    private boolean checkIntersectionCollision(Vehicle v1, double x1, double y1, Vehicle v2) {
        // Zona de intersección aproximada
        double intersectionCenterX = 430;
        double intersectionCenterY = 295;
        double intersectionRadius = 100;

        // Verificar si ambos están en la zona de intersección
        boolean v1InIntersection = calculateDistance(x1, y1,
                intersectionCenterX, intersectionCenterY) < intersectionRadius;
        boolean v2InIntersection = calculateDistance(v2.getX(), v2.getY(),
                intersectionCenterX, intersectionCenterY) < intersectionRadius;

        if (v1InIntersection && v2InIntersection) {
            // Verificar si sus trayectorias se cruzan
            return doTrajectoriesIntersect(v1, v2);
        }

        return false;
    }

    /**
     * Verifica si las trayectorias de dos vehículos se cruzan
     */
    private boolean doTrajectoriesIntersect(Vehicle v1, Vehicle v2) {
        if (v1.getStartPoint() == null || v2.getStartPoint() == null) return false;

        StartPoint sp1 = v1.getStartPoint();
        StartPoint sp2 = v2.getStartPoint();
        Direction d1 = v1.getDirection();
        Direction d2 = v2.getDirection();

        // Norte/Sur recto vs Este/Oeste recto (soporte para NORTH_L/NORTH_D/SOUTH_L/SOUTH_D)
        boolean v1NS = isNorthEntry(sp1) || isSouthEntry(sp1);
        boolean v2NS = isNorthEntry(sp2) || isSouthEntry(sp2);
        boolean v1EW = sp1 == StartPoint.EAST || sp1 == StartPoint.WEST;
        boolean v2EW = sp2 == StartPoint.EAST || sp2 == StartPoint.WEST;

        if (((v1NS && v2EW) || (v2NS && v1EW)) &&
                d1 == Direction.STRAIGHT && d2 == Direction.STRAIGHT) {
            return true;
        }

        // TODO: Implementar lógica más detallada para giros (LEFT/RIGHT/U_TURN) considerando carriles

        return false;
    }

    /**
     * Calcula la distancia mínima requerida entre dos vehículos
     */
    private double calculateMinDistance(Vehicle v1, Vehicle v2) {
        double baseDistance = v1.isHighwayVehicle() ?
                HIGHWAY_MIN_DISTANCE : STREET_MIN_DISTANCE;

        // Ajustar por tipo de vehículo
        if (v1.getType() == VehicleType.EMERGENCY || v2.getType() == VehicleType.EMERGENCY) {
            baseDistance = EMERGENCY_MIN_DISTANCE;
        } else if (v1.getType() == VehicleType.HEAVY || v2.getType() == VehicleType.HEAVY) {
            baseDistance *= 1.3;
        }

        // Ajustar por velocidad
        double speedFactor = 1.0 + (Math.max(v1.getSpeed(), v2.getSpeed()) / 100.0) * 0.5;

        return baseDistance * speedFactor;
    }

    /**
     * Verifica si dos vehículos están en la misma trayectoria
     */
    private boolean areInSamePath(Vehicle v1, Vehicle v2) {
        // Vehículos de autopista
        if (v1.isHighwayVehicle() && v2.isHighwayVehicle()) {
            // Mismo carril y misma dirección
            return v1.getCurrentLane() == v2.getCurrentLane() &&
                    (v1.getHighwayLane().isNorthbound() == v2.getHighwayLane().isNorthbound());
        }

        // Vehículos de calle
        if (!v1.isHighwayVehicle() && !v2.isHighwayVehicle()) {
            // Mismo punto de inicio = mismo carril (NORTH_L != NORTH_D)
            return v1.getStartPoint() == v2.getStartPoint();
        }

        return false;
    }

    /**
     * Verifica si un vehículo está adelante de otro
     */
    private boolean isVehicleAhead(Vehicle current, Vehicle other) {
        if (current.isHighwayVehicle() && other.isHighwayVehicle()) {
            // En autopista, considerar dirección
            if (current.getHighwayLane().isNorthbound()) {
                return other.getY() > current.getY();
            } else {
                return other.getY() < current.getY();
            }
        }

        if (!current.isHighwayVehicle() && !other.isHighwayVehicle()) {
            // En calles, considerar punto de inicio (soporte L/D)
            return switch (current.getStartPoint()) {
                case NORTH_L, NORTH_D -> other.getY() > current.getY();
                case SOUTH_L, SOUTH_D -> other.getY() < current.getY();
                case EAST -> other.getX() < current.getX();
                case WEST -> other.getX() > current.getX();
            };
        }

        return false;
    }

    /**
     * Calcula la distancia euclidiana entre dos puntos
     */
    public double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    // Helpers para categorías de StartPoint en calles
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

        public VehicleAheadInfo(Vehicle vehicle, double distance) {
            this.vehicle = vehicle;
            this.distance = distance;
        }

        public Vehicle getVehicle() {
            return vehicle;
        }

        public double getDistance() {
            return distance;
        }
    }
}