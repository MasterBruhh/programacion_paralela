package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.*;
import java.util.concurrent.*;
import java.util.Comparator;

public class IntersectionSemaphore {
    private final Semaphore semaphore;
    private final PriorityBlockingQueue<Vehicle> waitingQueue;
    
    public IntersectionSemaphore() {
        this.semaphore = new Semaphore(1, true);
        this.waitingQueue = new PriorityBlockingQueue<>(100, 
            Comparator.comparing((Vehicle v) -> {
                if (v.isHighwayVehicle()) return 0;
                if (v.getType() == VehicleType.EMERGENCY) return 1;
                return 2;
            }).thenComparing(Vehicle::getId));
    }
    
    public boolean tryAcquire(Vehicle vehicle, long timeout, TimeUnit unit) throws InterruptedException {
        waitingQueue.offer(vehicle);
        
        if (!vehicle.isHighwayVehicle() && hasHighwayVehicleWaiting()) {
            return false;
        }
        
        if (waitingQueue.peek() != vehicle) {
            return false;
        }
        
        boolean acquired = semaphore.tryAcquire(timeout, unit);
        if (acquired) {
            waitingQueue.poll();
        }
        
        return acquired;
    }
    
    public void release() {
        semaphore.release();
    }
    
    private boolean hasHighwayVehicleWaiting() {
        return waitingQueue.stream().anyMatch(Vehicle::isHighwayVehicle);
    }
    
    public boolean hasWaitingVehicles() {
        return !waitingQueue.isEmpty();
    }
}