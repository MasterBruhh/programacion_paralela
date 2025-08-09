package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.model.Vehicle;
import edu.pucmm.trafico.model.VehicleType;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.Comparator;

public class IntersectionSemaphore {
    private final Semaphore semaphore;
    private final PriorityBlockingQueue<Vehicle> waitingQueue;
    
    public IntersectionSemaphore() {
        this.semaphore = new Semaphore(1, true);
        this.waitingQueue = new PriorityBlockingQueue<>(100, 
            Comparator.comparing((Vehicle v) -> v.getType() == VehicleType.EMERGENCY ? 0 : 1)
                      .thenComparing(Vehicle::getId));
    }
    
    public boolean tryAcquire(Vehicle vehicle, long timeout, TimeUnit unit) throws InterruptedException {
        waitingQueue.offer(vehicle);
        
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
    
    public boolean hasWaitingVehicles() {
        return !waitingQueue.isEmpty();
    }
}