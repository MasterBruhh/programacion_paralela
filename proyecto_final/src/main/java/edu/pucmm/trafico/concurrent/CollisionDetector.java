package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.*;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Sistema avanzado de detección de colisiones para vehículos de calle y autopista.
 * Implementa algoritmos de predicción y prevención de colisiones.
 * Incluye lógica especial para vehículos de emergencia.
 */
public class CollisionDetector {
    private static final Logger logger = Logger.getLogger(CollisionDetector.class.getName());

    // Configuración de distancias
    private static final double VEHICLE_RADIUS = 15.0;
    private static final double SAFETY_MARGIN = 10.0;
    private static final double STREET_MIN_DISTANCE = 40.0;
    private static final double HIGHWAY_MIN_DISTANCE = 60.0;
    private static final double EMERGENCY_MIN_DISTANCE = 20.0; // Emergencias pueden estar más cerca
    private static final double EMERGENCY_YIELD_DISTANCE = 100.0; // Distancia para ceder paso a emergencia
    private static final double LANE_WIDTH = 30.0;
    private static final double PREDICTION_TIME = 2.0; // segundos

    /**
     * Verifica si un vehículo puede moverse a una posición sin colisionar
     * Incluye lógica especial para vehículos de emergencia
     */
    public boolean canMove(Vehicle vehicle, double nextX, double nextY,
                           Collection<Vehicle> activeVehicles) {

        // Los vehículos de emergencia tienen reglas más flexibles
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            return canMoveEmergency(vehicle, nextX, nextY, activeVehicles);
        }

        // Verificación especial para cambios de carril
        if (vehicle.isChangingLane()) {
            return canChangeLaneSafely(vehicle, nextX, nextY, activeVehicles);
        }

        // Verificación normal de colisiones
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
                    // Si el vehículo está cediendo a emergencia, permitir pequeños ajustes
                    if (vehicle.isEmergencyYielding() && distance > VEHICLE_RADIUS) {
                        continue;
                    }
                    
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
     * Verificación de movimiento especial para vehículos de emergencia
     */
    private boolean canMoveEmergency(Vehicle emergency, double nextX, double nextY,
                                    Collection<Vehicle> activeVehicles) {
        // Las emergencias solo evitan colisiones directas
        for (Vehicle other : activeVehicles) {
            if (other.getId() == emergency.getId() || !other.isActive()) continue;

            double distance = calculateDistance(nextX, nextY, other.getX(), other.getY());
            
            // Solo evitar colisiones muy cercanas
            if (distance < EMERGENCY_MIN_DISTANCE) {
                // Si el otro vehículo está cediendo, permitir pasar más cerca
                if (other.isEmergencyYielding()) {
                    if (distance > VEHICLE_RADIUS) {
                        continue;
                    }
                }
                
                logger.fine("Emergencia " + emergency.getId() + 
                          " evitando colisión crítica con " + other.getId());
                return false;
            }
        }
        
        return true;
    }

    /**
     * Detecta vehículos adelante en el mismo carril o trayectoria
     * Prioriza detección de vehículos de emergencia
     */
    public VehicleAheadInfo detectVehicleAhead(Vehicle vehicle,
                                               Collection<Vehicle> activeVehicles) {
        Vehicle closestVehicle = null;
        double closestDistance = Double.MAX_VALUE;
        boolean emergencyAhead = false;

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

                    // Priorizar vehículos de emergencia
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

            double distance = calculateDistance(
                    vehicle.getX(), vehicle.getY(),
                    other.getX(), other.getY()
            );

            if (distance <= radius) {
                emergencies.add(other);
            }
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
     * Da prioridad especial a vehículos de emergencia
     */
    public double calculateSafeSpeed(double currentSpeed, double distanceAhead,
                                     boolean isHighway, boolean emergencyAhead) {
        // Si hay emergencia adelante, detenerse antes
        if (emergencyAhead) {
            double stopDistance = EMERGENCY_YIELD_DISTANCE;
            if (distanceAhead < stopDistance) {
                return 0; // Detenerse para dar paso
            }
            return currentSpeed * 0.3; // Velocidad muy reducida
        }
        
        double minDistance = isHighway ? HIGHWAY_MIN_DISTANCE : STREET_MIN_DISTANCE;
        double stopDistance = minDistance * 0.5;

        if (distanceAhead < stopDistance) {
            return 0; // Detenerse completamente
        } else if (distanceAhead < minDistance) {
            // Reducir velocidad proporcionalmente
            double factor = (distanceAhead - stopDistance) / (minDistance - stopDistance);
            return currentSpeed * factor * 0.5;
        } else if (distanceAhead < minDistance * 1.5) {
            // Reducción suave
            return currentSpeed * 0.8;
        }

        return currentSpeed;
    }

    /**
     * Verifica si debe ceder paso a un vehículo de emergencia
     */
    public boolean shouldYieldToEmergency(Vehicle vehicle, Vehicle emergency) {
        if (vehicle.getType() == VehicleType.EMERGENCY) {
            return false; // Las emergencias no ceden a otras emergencias
        }

        if (emergency.getType() != VehicleType.EMERGENCY) {
            return false;
        }

        double distance = calculateDistance(
            vehicle.getX(), vehicle.getY(),
            emergency.getX(), emergency.getY()
        );

        // Si está muy cerca, ceder
        if (distance < EMERGENCY_YIELD_DISTANCE) {
            // Verificar si están en la misma trayectoria
            if (areInSamePath(vehicle, emergency)) {
                // Verificar si la emergencia está aproximándose
                if (isEmergencyApproaching(vehicle, emergency)) {
                    return true;
                }
            }
            
            // Verificar si sus trayectorias se cruzarán pronto
            if (willPathsCross(vehicle, emergency)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Verifica si una emergencia se está aproximando
     */
    private boolean isEmergencyApproaching(Vehicle vehicle, Vehicle emergency) {
        if (vehicle.isHighwayVehicle() && emergency.isHighwayVehicle()) {
            // En autopista, verificar dirección y posición relativa
            boolean sameDirection = vehicle.getHighwayLane().isWestbound() == 
                                  emergency.getHighwayLane().isWestbound();
            
            if (!sameDirection) return false;
            
            if (vehicle.getHighwayLane().isWestbound()) {
                // Va hacia el oeste, emergencia se aproxima si X es mayor
                return emergency.getX() > vehicle.getX();
            } else {
                // Va hacia el este, emergencia se aproxima si X es menor
                return emergency.getX() < vehicle.getX();
            }
        }

        if (!vehicle.isHighwayVehicle() && !emergency.isHighwayVehicle()) {
            // En calles, verificar según dirección
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

    /**
     * Verifica si las trayectorias de dos vehículos se cruzarán
     */
    private boolean willPathsCross(Vehicle v1, Vehicle v2) {
        // Si uno está en autopista y otro en calle
        if (v1.isHighwayVehicle() != v2.isHighwayVehicle()) {
            // Verificar si están cerca de una intersección
            double intersectionY1 = 260; // Centro de autopista superior
            double intersectionY2 = 360; // Centro de autopista inferior
            
            // Verificar proximidad a intersecciones
            if (Math.abs(v1.getY() - intersectionY1) < 100 || 
                Math.abs(v1.getY() - intersectionY2) < 100) {
                if (Math.abs(v2.getY() - intersectionY1) < 100 || 
                    Math.abs(v2.getY() - intersectionY2) < 100) {
                    // Ambos cerca de una intersección
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

        // Zona de seguridad para cambio de carril (reducida para emergencias)
        double safeZoneFront = vehicle.getType() == VehicleType.EMERGENCY ? 
                              EMERGENCY_MIN_DISTANCE : HIGHWAY_MIN_DISTANCE;
        double safeZoneBack = safeZoneFront * 0.7;
        double safeZoneSide = LANE_WIDTH;

        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId() || !other.isActive()) continue;
            if (!other.isHighwayVehicle()) continue;

            // Si el vehículo está cediendo a emergencia, dar más libertad
            if (vehicle.getType() == VehicleType.EMERGENCY && other.isEmergencyYielding()) {
                continue;
            }

            // Calcular posición relativa
            double dx = Math.abs(nextX - other.getX());
            double dy = other.getY() - nextY;

            // Verificar si está en la zona de peligro
            if (dx < safeZoneSide) {
                boolean isBehind = vehicle.getHighwayLane().isNorthbound() ?
                        dy < 0 : dy > 0;

                double longitudinalDistance = Math.abs(dy);
                double requiredDistance = isBehind ? safeZoneBack : safeZoneFront;

                if (other.getSpeed() > vehicle.getSpeed() && isBehind) {
                    requiredDistance *= 1.5;
                }

                if (longitudinalDistance < requiredDistance) {
                    logger.fine("Cambio de carril bloqueado: Vehículo " +
                                other.getId() + " muy cerca");
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
        // Las emergencias tienen predicción más agresiva
        if (vehicle.getType() == VehicleType.EMERGENCY || 
            other.getType() == VehicleType.EMERGENCY) {
            return false; // No predecir colisiones para emergencias
        }

        int laneDiff = Math.abs(vehicle.getCurrentLane() - other.getCurrentLane());

        if (laneDiff != 0 && !(vehicle.isChangingLane() || other.isChangingLane())) {
            return false;
        }

        double vehicleFutureX = nextX;
        double otherFutureX = other.getX();

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

        double futureDistance;
        if (laneDiff == 0) {
            futureDistance = Math.abs(vehicleFutureX - otherFutureX);
        } else {
            futureDistance = calculateDistance(vehicleFutureX, nextY, otherFutureX, other.getY());
        }

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

    /**
     * Verifica colisiones en intersección
     */
    private boolean checkIntersectionCollision(Vehicle v1, double x1, double y1, Vehicle v2) {
        // Si uno es emergencia, dar prioridad
        if (v1.getType() == VehicleType.EMERGENCY || v2.getType() == VehicleType.EMERGENCY) {
            return false; // No bloquear emergencias en intersección
        }

        double intersectionCenterX = 430;
        double intersectionCenterY = 295;
        double intersectionRadius = 100;

        boolean v1InIntersection = calculateDistance(x1, y1,
                intersectionCenterX, intersectionCenterY) < intersectionRadius;
        boolean v2InIntersection = calculateDistance(v2.getX(), v2.getY(),
                intersectionCenterX, intersectionCenterY) < intersectionRadius;

        if (v1InIntersection && v2InIntersection) {
            return doTrajectoriesIntersect(v1, v2);
        }

        return false;
    }

    /**
     * Verifica si las trayectorias de dos vehículos se cruzan
     */
    private boolean doTrajectoriesIntersect(Vehicle v1, Vehicle v2) {
        // Las emergencias tienen prioridad absoluta
        if (v1.getType() == VehicleType.EMERGENCY || v2.getType() == VehicleType.EMERGENCY) {
            return false;
        }

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
     * Calcula la distancia mínima requerida entre dos vehículos
     */
    private double calculateMinDistance(Vehicle v1, Vehicle v2) {
        // Si uno es emergencia, reducir distancia mínima
        if (v1.getType() == VehicleType.EMERGENCY || v2.getType() == VehicleType.EMERGENCY) {
            return EMERGENCY_MIN_DISTANCE;
        }

        double baseDistance = v1.isHighwayVehicle() ?
                HIGHWAY_MIN_DISTANCE : STREET_MIN_DISTANCE;

        // Ajustar por tipo de vehículo
        if (v1.getType() == VehicleType.HEAVY || v2.getType() == VehicleType.HEAVY) {
            baseDistance *= 1.3;
        } else if (v1.getType() == VehicleType.PUBLIC_TRANSPORT || 
                   v2.getType() == VehicleType.PUBLIC_TRANSPORT) {
            baseDistance *= 1.2;
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
            return v1.getCurrentLane() == v2.getCurrentLane() &&
                    (v1.getHighwayLane().isWestbound() == v2.getHighwayLane().isWestbound());
        }

        // Vehículos de calle
        if (!v1.isHighwayVehicle() && !v2.isHighwayVehicle()) {
            return v1.getStartPoint() == v2.getStartPoint();
        }

        return false;
    }

    /**
     * Verifica si un vehículo está adelante de otro
     */
    private boolean isVehicleAhead(Vehicle current, Vehicle other) {
        if (current.isHighwayVehicle() && other.isHighwayVehicle()) {
            boolean westbound = current.getHighwayLane().isWestbound();
            if (westbound) {
                return other.getX() < current.getX();
            } else {
                return other.getX() > current.getX();
            }
        }

        if (!current.isHighwayVehicle() && !other.isHighwayVehicle()) {
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

        public Vehicle getVehicle() {
            return vehicle;
        }

        public double getDistance() {
            return distance;
        }

        public boolean isEmergency() {
            return isEmergency;
        }
    }
}