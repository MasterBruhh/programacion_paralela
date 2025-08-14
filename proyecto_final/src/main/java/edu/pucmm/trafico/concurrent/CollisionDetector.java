package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.*;
import java.util.Collection;
import java.util.logging.Logger;

public class CollisionDetector {
    private static final Logger logger = Logger.getLogger(CollisionDetector.class.getName());
    
    private static final double VEHICLE_RADIUS = 15.0;
    private static final double SAFETY_MARGIN = 10.0;
    private static final double HIGHWAY_SAFETY_DISTANCE = 60.0;
    private static final double STREET_SAFETY_DISTANCE = 40.0;
    private static final double LANE_WIDTH = 30.0;
    
    public boolean canMove(Vehicle vehicle, double nextX, double nextY, Collection<Vehicle> activeVehicles) {
        if (vehicle.isChangingLane()) {
            return canChangeLane(vehicle, nextX, nextY, activeVehicles);
        }
        
        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId()) continue;
            
            double distance = calculateDistance(nextX, nextY, other.getX(), other.getY());
            double minDistance = vehicle.isHighwayVehicle() ? HIGHWAY_SAFETY_DISTANCE : STREET_SAFETY_DISTANCE;
            
            if (distance < minDistance) {
                if (areInSameLane(vehicle, other) && isVehicleAhead(vehicle, other, nextX, nextY)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private boolean canChangeLane(Vehicle vehicle, double nextX, double nextY, Collection<Vehicle> activeVehicles) {
        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId()) continue;
            if (!other.isHighwayVehicle()) continue;
            
            double distance = calculateDistance(nextX, nextY, other.getX(), other.getY());
            
            if (distance < HIGHWAY_SAFETY_DISTANCE * 1.5) {
                return false;
            }
        }
        
        return true;
    }
    
    public VehicleAheadInfo detectVehicleAhead(Vehicle vehicle, Collection<Vehicle> activeVehicles) {
        Vehicle closestVehicle = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (Vehicle other : activeVehicles) {
            if (other.getId() == vehicle.getId()) continue;
            if (!areInSameLane(vehicle, other)) continue;
            
            if (isVehicleAhead(vehicle, other, vehicle.getX(), vehicle.getY())) {
                double distance = calculateDistance(vehicle.getX(), vehicle.getY(), other.getX(), other.getY());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestVehicle = other;
                }
            }
        }
        
        return closestVehicle != null ? new VehicleAheadInfo(closestVehicle, closestDistance) : null;
    }
    
    private boolean areInSameLane(Vehicle v1, Vehicle v2) {
        if (v1.isHighwayVehicle() && v2.isHighwayVehicle()) {
            return v1.getCurrentLane() == v2.getCurrentLane() &&
                   ((v1.getHighwayLane().isNorthbound() && v2.getHighwayLane().isNorthbound()) ||
                    (v1.getHighwayLane().isSouthbound() && v2.getHighwayLane().isSouthbound()));
        } else if (!v1.isHighwayVehicle() && !v2.isHighwayVehicle()) {
            return v1.getStartPoint() == v2.getStartPoint();
        }
        return false;
    }
    
    private boolean isVehicleAhead(Vehicle current, Vehicle other, double currentX, double currentY) {
        if (current.isHighwayVehicle() && other.isHighwayVehicle()) {
            if (current.getHighwayLane().isNorthbound()) {
                return other.getY() > currentY;
            } else {
                return other.getY() < currentY;
            }
        } else if (!current.isHighwayVehicle() && !other.isHighwayVehicle()) {
            return switch (current.getStartPoint()) {
                case NORTH -> other.getY() > currentY;
                case SOUTH -> other.getY() < currentY;
                case EAST -> other.getX() < currentX;
                case WEST -> other.getX() > currentX;
            };
        }
        return false;
    }
    
    public double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
    
    public double calculateSafeSpeed(double currentSpeed, double distanceAhead, boolean isHighway) {
        double minDistance = isHighway ? HIGHWAY_SAFETY_DISTANCE : STREET_SAFETY_DISTANCE;
        
        if (distanceAhead < minDistance) {
            return 0;
        } else if (distanceAhead < minDistance * 2) {
            double factor = (distanceAhead - minDistance) / minDistance;
            return currentSpeed * factor;
        }
        
        return currentSpeed;
    }
    
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