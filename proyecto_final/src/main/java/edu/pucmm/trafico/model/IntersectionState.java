package edu.pucmm.trafico.model;

public class IntersectionState {
    private final double stopX;
    private final double stopY;
    private volatile Vehicle currentVehicle;
    
    public IntersectionState(double stopX, double stopY) {
        this.stopX = stopX;
        this.stopY = stopY;
        this.currentVehicle = null;
    }
    
    public synchronized boolean tryAcquire(Vehicle vehicle) {
        if (currentVehicle == null) {
            currentVehicle = vehicle;
            return true;
        }
        return false;
    }
    
    public synchronized void release() {
        currentVehicle = null;
    }
    
    public synchronized boolean isOccupied() {
        return currentVehicle != null;
    }
    
    public double getStopX() {
        return stopX;
    }
    
    public double getStopY() {
        return stopY;
    }
}