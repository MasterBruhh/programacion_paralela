package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.Vehicle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Collection;
import java.util.Queue;

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
    }
    
    public void registerVehicle(Vehicle vehicle) {
        activeVehicles.put(vehicle.getId(), vehicle);
        String lane = vehicle.getStartPoint().name();
        laneQueues.get(lane).offer(vehicle);
    }
    
    public void unregisterVehicle(Vehicle vehicle) {
        activeVehicles.remove(vehicle.getId());
        String lane = vehicle.getStartPoint().name();
        laneQueues.get(lane).remove(vehicle);
    }
    
    public Vehicle getVehicle(long id) {
        return activeVehicles.get(id);
    }
    
    public Collection<Vehicle> getAllVehicles() {
        return activeVehicles.values();
    }
    
    public Queue<Vehicle> getLaneQueue(String lane) {
        return laneQueues.get(lane);
    }
    
    public boolean isFirstInLane(Vehicle vehicle) {
        String lane = vehicle.getStartPoint().name();
        Queue<Vehicle> queue = laneQueues.get(lane);
        return queue.peek() == vehicle;
    }
}