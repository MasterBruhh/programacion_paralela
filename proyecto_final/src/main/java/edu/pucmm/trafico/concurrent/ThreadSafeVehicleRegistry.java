package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Collection;
import java.util.Queue;
import java.util.List;
import java.util.ArrayList;

public class ThreadSafeVehicleRegistry {
    private final ConcurrentHashMap<Long, Vehicle> activeVehicles;
    private final ConcurrentHashMap<String, Queue<Vehicle>> laneQueues;
    
    public ThreadSafeVehicleRegistry() {
        this.activeVehicles = new ConcurrentHashMap<>();
        this.laneQueues = new ConcurrentHashMap<>();
        
        laneQueues.put("NORTH", new ConcurrentLinkedQueue<>());
        laneQueues.put("SOUTH", new ConcurrentLinkedQueue<>());
        laneQueues.put("EAST", new ConcurrentLinkedQueue<>());
        laneQueues.put("WEST", new ConcurrentLinkedQueue<>());
        
        for (HighwayLane lane : HighwayLane.values()) {
            laneQueues.put(lane.name(), new ConcurrentLinkedQueue<>());
        }
    }
    
    public void registerVehicle(Vehicle vehicle) {
        activeVehicles.put(vehicle.getId(), vehicle);
        
        if (vehicle.isHighwayVehicle()) {
            laneQueues.get(vehicle.getHighwayLane().name()).offer(vehicle);
        } else {
            laneQueues.get(vehicle.getStartPoint().name()).offer(vehicle);
        }
    }
    
    public void unregisterVehicle(Vehicle vehicle) {
        activeVehicles.remove(vehicle.getId());
        
        if (vehicle.isHighwayVehicle()) {
            laneQueues.get(vehicle.getHighwayLane().name()).remove(vehicle);
        } else {
            laneQueues.get(vehicle.getStartPoint().name()).remove(vehicle);
        }
    }
    
    public Vehicle getVehicle(long id) {
        return activeVehicles.get(id);
    }
    
    public Collection<Vehicle> getAllVehicles() {
        return activeVehicles.values();
    }
    
    public List<Vehicle> getEmergencyVehicles() {
        List<Vehicle> emergencyVehicles = new ArrayList<>();
        for (Vehicle vehicle : activeVehicles.values()) {
            if (vehicle.getType() == VehicleType.EMERGENCY) {
                emergencyVehicles.add(vehicle);
            }
        }
        return emergencyVehicles;
    }
    
    public int getActiveVehicleCount() {
        return activeVehicles.size();
    }
}