package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class HighwayLaneManager {
    private static final Logger logger = Logger.getLogger(HighwayLaneManager.class.getName());
    
    private final ConcurrentHashMap<HighwayLane, ConcurrentLinkedQueue<Vehicle>> laneQueues;
    private final ConcurrentHashMap<HighwayLane, Semaphore> laneSemaphores;
    
    public HighwayLaneManager() {
        this.laneQueues = new ConcurrentHashMap<>();
        this.laneSemaphores = new ConcurrentHashMap<>();
        
        for (HighwayLane lane : HighwayLane.values()) {
            laneQueues.put(lane, new ConcurrentLinkedQueue<>());
            laneSemaphores.put(lane, new Semaphore(10, true));
        }
    }
    
    public HighwayLane assignLane(Direction direction, boolean goingNorth) {
        if (goingNorth) {
            return switch (direction) {
                case LEFT, U_TURN -> HighwayLane.NORTH_LEFT;
                case RIGHT -> HighwayLane.NORTH_RIGHT;
                default -> HighwayLane.NORTH_CENTER;
            };
        } else {
            return switch (direction) {
                case LEFT, U_TURN -> HighwayLane.SOUTH_LEFT;
                case RIGHT -> HighwayLane.SOUTH_RIGHT;
                default -> HighwayLane.SOUTH_CENTER;
            };
        }
    }
    
    public void registerVehicle(Vehicle vehicle) {
        if (!vehicle.isHighwayVehicle()) return;
        
        HighwayLane lane = vehicle.getHighwayLane();
        laneQueues.get(lane).offer(vehicle);
        
        logger.info("Vehículo " + vehicle.getId() + " registrado en carril " + lane);
    }
    
    public void unregisterVehicle(Vehicle vehicle) {
        if (!vehicle.isHighwayVehicle()) return;
        
        HighwayLane lane = vehicle.getHighwayLane();
        laneQueues.get(lane).remove(vehicle);
    }
    
    public void updateVehicleLane(Vehicle vehicle, int oldLane, int newLane) {
        logger.info("Vehículo " + vehicle.getId() + " cambió de carril " + oldLane + " a " + newLane);
    }
}