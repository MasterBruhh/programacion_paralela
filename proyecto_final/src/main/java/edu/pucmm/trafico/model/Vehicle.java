package edu.pucmm.trafico.model;

import javafx.scene.paint.Color;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Random;

public class Vehicle {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    private static final Random random = new Random();
    
    private final long id;
    private final VehicleType type;
    private final StartPoint startPoint;
    private final Direction direction;
    private final boolean isHighwayVehicle;
    private final HighwayLane highwayLane;
    
    private volatile double x;
    private volatile double y;
    private volatile double speed;
    private volatile boolean active;
    private volatile boolean waitingAtLight;
    private volatile boolean inIntersection;
    private volatile long waitStartTime;
    private volatile int currentLane;
    private volatile boolean isChangingLane;
    private volatile int targetExit;
    private final Color color;
    
    public Vehicle(VehicleType type, StartPoint startPoint, Direction direction) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.type = type;
        this.startPoint = startPoint;
        this.direction = direction;
        this.isHighwayVehicle = false;
        this.highwayLane = null;
        this.x = startPoint.getX();
        this.y = startPoint.getY();
        this.speed = calculateInitialSpeed(type);
        this.active = true;
        this.waitingAtLight = false;
        this.inIntersection = false;
        this.waitStartTime = 0;
        this.currentLane = 0;
        this.isChangingLane = false;
        this.targetExit = 0;
        this.color = generateColor(type);
    }
    
    public Vehicle(VehicleType type, HighwayLane lane, Direction direction) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.type = type;
        this.highwayLane = lane;
        this.direction = direction;
        this.isHighwayVehicle = true;
        this.startPoint = null;
        this.x = lane.getStartX();
        this.y = lane.getStartY();
        this.speed = calculateInitialSpeed(type);
        this.active = true;
        this.waitingAtLight = false;
        this.inIntersection = false;
        this.waitStartTime = 0;
        this.currentLane = lane.getLaneNumber();
        this.isChangingLane = false;
        this.targetExit = random.nextInt(3);
        this.color = generateColor(type);
        
        if (lane.isLeftLane() && direction != Direction.LEFT && direction != Direction.U_TURN) {
            throw new IllegalArgumentException("Carril izquierdo solo para giros a la izquierda o vuelta en U");
        }
    }
    
    private double calculateInitialSpeed(VehicleType type) {
        return switch (type) {
            case EMERGENCY -> isHighwayVehicle ? 80.0 : 50.0;
            case PUBLIC_TRANSPORT -> isHighwayVehicle ? 60.0 : 35.0;
            case HEAVY -> isHighwayVehicle ? 50.0 : 30.0;
            default -> isHighwayVehicle ? 65.0 : 40.0;
        };
    }
    
    private Color generateColor(VehicleType type) {
        switch (type) {
            case EMERGENCY -> {
                return Color.RED;
            }
            case PUBLIC_TRANSPORT -> {
                return Color.ORANGE;
            }
            case HEAVY -> {
                return Color.DARKGRAY;
            }
            default -> {
                if (isHighwayVehicle) {
                    return Color.DARKBLUE;
                } else {
                    return Color.BLUE;
                }
            }
        }
    }
    
    public synchronized void updatePosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
    }
    
    public synchronized void setWaitingAtLight(boolean waiting) {
        this.waitingAtLight = waiting;
        if (waiting && waitStartTime == 0) {
            this.waitStartTime = System.currentTimeMillis();
        } else if (!waiting) {
            this.waitStartTime = 0;
        }
    }
    
    public void setInIntersection(boolean inIntersection) {
        this.inIntersection = inIntersection;
    }
    
    public void setCurrentLane(int lane) {
        this.currentLane = lane;
    }
    
    public void setChangingLane(boolean changingLane) {
        this.isChangingLane = changingLane;
    }
    
    public void setTargetExit(int targetExit) {
        this.targetExit = targetExit;
    }
    
    public long getWaitTime() {
        if (!waitingAtLight) return 0;
        return System.currentTimeMillis() - waitStartTime;
    }
    
    public void deactivate() {
        this.active = false;
    }
    
    public long getId() { return id; }
    public VehicleType getType() { return type; }
    public StartPoint getStartPoint() { return startPoint; }
    public Direction getDirection() { return direction; }
    public boolean isHighwayVehicle() { return isHighwayVehicle; }
    public HighwayLane getHighwayLane() { return highwayLane; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getSpeed() { return speed; }
    public boolean isActive() { return active; }
    public boolean isWaitingAtLight() { return waitingAtLight; }
    public boolean isInIntersection() { return inIntersection; }
    public int getCurrentLane() { return currentLane; }
    public boolean isChangingLane() { return isChangingLane; }
    public int getTargetExit() { return targetExit; }
    public Color getColor() { return color; }
    public void setSpeed(double speed) { this.speed = speed; }
}