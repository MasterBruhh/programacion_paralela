package edu.pucmm.trafico.model;

import java.util.concurrent.atomic.AtomicLong;

public class Vehicle {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    
    private final long id;
    private final VehicleType type;
    private final StartPoint startPoint;
    private final Direction direction;
    private volatile double x;
    private volatile double y;
    private volatile double speed;
    private volatile boolean active;
    private volatile boolean waitingAtStop;
    private volatile long stopArrivalTime;
    
    public Vehicle(VehicleType type, StartPoint startPoint, Direction direction) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.type = type;
        this.startPoint = startPoint;
        this.direction = direction;
        this.x = startPoint.getX();
        this.y = startPoint.getY();
        this.speed = 30.0;
        this.active = true;
        this.waitingAtStop = false;
        this.stopArrivalTime = 0;
    }
    
    public synchronized void updatePosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
    }
    
    public synchronized void setWaitingAtStop(boolean waiting) {
        this.waitingAtStop = waiting;
        if (waiting) {
            this.stopArrivalTime = System.currentTimeMillis();
        }
    }
    
    public long getWaitTime() {
        if (!waitingAtStop) return 0;
        return System.currentTimeMillis() - stopArrivalTime;
    }
    
    public void deactivate() {
        this.active = false;
    }
    
    public long getId() {
        return id;
    }
    
    public VehicleType getType() {
        return type;
    }
    
    public StartPoint getStartPoint() {
        return startPoint;
    }
    
    public Direction getDirection() {
        return direction;
    }
    
    public double getX() {
        return x;
    }
    
    public double getY() {
        return y;
    }
    
    public double getSpeed() {
        return speed;
    }
    
    public void setSpeed(double speed) {
        this.speed = speed;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public boolean isWaitingAtStop() {
        return waitingAtStop;
    }
    
    @Override
    public String toString() {
        return String.format("Vehicle[id=%d, type=%s, start=%s, dir=%s, pos=(%.1f, %.1f), active=%s]",
                id, type, startPoint, direction, x, y, active);
    }
}