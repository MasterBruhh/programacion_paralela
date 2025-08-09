package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.Vehicle;
import edu.pucmm.trafico.model.VehicleType;
import edu.pucmm.trafico.model.StartPoint;

import java.util.Collection;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Detector de colisiones mejorado con lógica de frenado preventivo.
 * Implementa algoritmo AABB (Axis-Aligned Bounding Box) con detección anticipada.
 */
public class CollisionDetector {
    private static final Logger logger = Logger.getLogger(CollisionDetector.class.getName());
    
    // Configuración de colisiones y distancias
    private static final double VEHICLE_RADIUS = 12.0; // Radio del vehículo visual
    private static final double SAFETY_MARGIN = 5.0; // Margen adicional de seguridad
    private static final double NORMAL_SAFETY_RADIUS = VEHICLE_RADIUS + SAFETY_MARGIN;
    private static final double EMERGENCY_SAFETY_RADIUS = VEHICLE_RADIUS + SAFETY_MARGIN;
    private static final double QUEUE_SAFETY_DISTANCE = 35.0; // Distancia en cola
    private static final double BRAKING_DISTANCE = 50.0; // Distancia para empezar a frenar
    private static final double ALIGNMENT_TOLERANCE = 15.0;
    
    /**
     * Verifica si un vehículo puede moverse a una posición sin colisionar.
     * Incluye lógica de prevención anticipada.
     * 
     * @param vehicleId ID del vehículo que se quiere mover
     * @param nextX nueva posición X propuesta
     * @param nextY nueva posición Y propuesta
     * @param activeVehicles lista de todos los vehículos activos
     * @return true si puede moverse sin colisión, false si hay riesgo
     */
    public boolean canMove(long vehicleId, double nextX, double nextY, Collection<Vehicle> activeVehicles) {
        // Buscar el vehículo que se quiere mover
        Vehicle movingVehicle = activeVehicles.stream()
                .filter(v -> v.getId() == vehicleId)
                .findFirst()
                .orElse(null);
        
        if (movingVehicle == null) {
            return true;
        }
        
        // Verificar colisión con todos los otros vehículos
        for (Vehicle otherVehicle : activeVehicles) {
            if (otherVehicle.getId() == vehicleId) {
                continue;
            }
            
            // Si no están en el mismo carril, usar detección normal
            if (otherVehicle.getStartPoint() != movingVehicle.getStartPoint()) {
                if (detectAABBCollision(nextX, nextY, NORMAL_SAFETY_RADIUS,
                                       otherVehicle.getX(), otherVehicle.getY(), 
                                       NORMAL_SAFETY_RADIUS)) {
                    return false;
                }
                continue;
            }
            
            // Para vehículos en el mismo carril, usar lógica especial
            if (areInSameLane(movingVehicle, otherVehicle)) {
                // Verificar si el otro vehículo está adelante
                if (isVehicleAhead(movingVehicle, otherVehicle)) {
                    double distance = calculateDistance(nextX, nextY, 
                                                      otherVehicle.getX(), otherVehicle.getY());
                    
                    // No permitir movimiento si estaría muy cerca
                    if (distance < QUEUE_SAFETY_DISTANCE) {
                        logger.fine("Vehículo " + vehicleId + " detenido - muy cerca de " + 
                                   otherVehicle.getId() + " (distancia: " + 
                                   String.format("%.1f", distance) + ")");
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Detecta si hay un vehículo adelante en el mismo carril y calcula la distancia segura.
     * 
     * @param vehicle vehículo actual
     * @param activeVehicles todos los vehículos activos
     * @return información sobre el vehículo adelante si existe
     */
    public VehicleAheadInfo detectVehicleAhead(Vehicle vehicle, Collection<Vehicle> activeVehicles) {
        Vehicle closestVehicle = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId()) continue;
            if (other.getStartPoint() != vehicle.getStartPoint()) continue;
            
            if (isVehicleAhead(vehicle, other)) {
                double distance = calculateDistance(vehicle.getX(), vehicle.getY(),
                                                  other.getX(), other.getY());
                
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestVehicle = other;
                }
            }
        }
        
        if (closestVehicle != null) {
            return new VehicleAheadInfo(closestVehicle, closestDistance);
        }
        
        return null;
    }
    
    /**
     * Calcula la velocidad segura basada en la distancia al vehículo de adelante.
     * 
     * @param currentSpeed velocidad actual
     * @param distanceAhead distancia al vehículo de adelante
     * @return velocidad segura recomendada
     */
    public double calculateSafeSpeed(double currentSpeed, double distanceAhead) {
        if (distanceAhead < QUEUE_SAFETY_DISTANCE) {
            return 0; // Detenerse completamente
        } else if (distanceAhead < BRAKING_DISTANCE) {
            // Reducir velocidad proporcionalmente
            double factor = (distanceAhead - QUEUE_SAFETY_DISTANCE) / 
                           (BRAKING_DISTANCE - QUEUE_SAFETY_DISTANCE);
            return currentSpeed * factor;
        }
        
        return currentSpeed; // Mantener velocidad
    }
    
    /**
     * Verifica si dos vehículos están en el mismo carril.
     */
    private boolean areInSameLane(Vehicle v1, Vehicle v2) {
        // Mismo punto de inicio = mismo carril
        return v1.getStartPoint() == v2.getStartPoint();
    }
    
    /**
     * Verifica si el vehículo 'other' está adelante del vehículo 'current'.
     */
    private boolean isVehicleAhead(Vehicle current, Vehicle other) {
        switch (current.getStartPoint()) {
            case NORTH:
                // Los vehículos del norte van hacia el sur (Y aumenta)
                return other.getY() > current.getY();
            case SOUTH:
                // Los vehículos del sur van hacia el norte (Y disminuye)
                return other.getY() < current.getY();
            case EAST:
                // Los vehículos del este van hacia el oeste (X disminuye)
                return other.getX() < current.getX();
            case WEST:
                // Los vehículos del oeste van hacia el este (X aumenta)
                return other.getX() > current.getX();
            default:
                return false;
        }
    }
    
    /**
     * Verifica si dos vehículos están en la misma línea (horizontal o vertical).
     */
    private boolean areInSameLine(Vehicle v1, Vehicle v2) {
        double deltaX = Math.abs(v1.getX() - v2.getX());
        double deltaY = Math.abs(v1.getY() - v2.getY());
        
        // Están en línea horizontal si tienen casi la misma Y
        if (deltaY < ALIGNMENT_TOLERANCE && deltaX < 100.0) {
            return true;
        }
        
        // Están en línea vertical si tienen casi la misma X
        if (deltaX < ALIGNMENT_TOLERANCE && deltaY < 100.0) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Detecta colisión usando algoritmo AABB.
     */
    private boolean detectAABBCollision(double x1, double y1, double radio1,
                                       double x2, double y2, double radio2) {
        
        // Calcular distancia entre centros
        double deltaX = Math.abs(x1 - x2);
        double deltaY = Math.abs(y1 - y2);
        
        // Verificar si los bounding boxes se superponen
        double combinedRadii = radio1 + radio2;
        
        return (deltaX < combinedRadii) && (deltaY < combinedRadii);
    }
    
    /**
     * Calcula la distancia euclidiana entre dos puntos.
     */
    public double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
    
    /**
     * Obtiene el radio de seguridad para un vehículo específico.
     */
    private double getSafetyRadius(Vehicle vehicle) {
        return (vehicle.getType() == VehicleType.EMERGENCY) ?
               EMERGENCY_SAFETY_RADIUS : NORMAL_SAFETY_RADIUS;
    }
    
    /**
     * Información sobre un vehículo detectado adelante.
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