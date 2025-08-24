package edu.pucmm.trafico.model;

import javafx.scene.paint.Color;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Modelo completo de vehículo con soporte para autopista y todos los tipos requeridos.
 * Implementa el ciclo de vida completo y características específicas por tipo.
 */
public class Vehicle {
    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);
    
    // Identificación y tipo
    private final long id;
    private final VehicleType type;
    private final StartPoint startPoint;
    private final Direction direction;
    private final boolean isHighwayVehicle;
    private final HighwayLane highwayLane;
    
    // Estado de posición y movimiento
    private volatile double x;
    private volatile double y;
    private volatile double speed;
    private volatile double maxSpeed;
    private volatile double acceleration;
    private volatile boolean active;
    
    // Estado de espera y cruce
    private volatile boolean waitingAtLight;
    private volatile boolean inIntersection;
    private volatile boolean atTrafficLight;
    private volatile long waitStartTime;
    
    // Estado específico de autopista
    private volatile int currentLane;
    private volatile boolean isChangingLane;
    private volatile int targetExit;
    private volatile boolean emergencyYielding;
    
    // Visual
    private final Color color;
    private final double size;

    private volatile int targetIntersection = -1; // -1 significa que no girará
    
    // Constructor para vehículos de calle
    public Vehicle(VehicleType type, StartPoint startPoint, Direction direction) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.type = type;
        this.startPoint = startPoint;
        this.direction = direction;
        this.isHighwayVehicle = false;
        this.highwayLane = null;
        this.x = startPoint.getX();
        this.y = startPoint.getY();
        
        // Configurar características por tipo
        VehicleCharacteristics characteristics = configureVehicleCharacteristics();
        this.color = characteristics.color();
        this.size = characteristics.size();
        
        this.active = true;
        this.waitingAtLight = false;
        this.inIntersection = false;
        this.atTrafficLight = false;
        this.waitStartTime = 0;
        this.currentLane = 0;
        this.isChangingLane = false;
        this.targetExit = -1;
        this.emergencyYielding = false;
    }
    
    // Constructor para vehículos de autopista
    public Vehicle(VehicleType type, HighwayLane lane, Direction direction) {
        this.id = ID_GENERATOR.incrementAndGet();
        this.type = type;
        this.highwayLane = lane;
        this.direction = direction;
        this.isHighwayVehicle = true;
        this.startPoint = null;
        this.x = lane.getStartX();
        this.y = lane.getStartY();
        this.targetExit = -1;
        
        // Validar restricciones de carril - Corregir para dar libertad en contexto de menú
        // Si es carril izquierdo y la dirección no es LEFT o U_TURN, cambiar a STRAIGHT
        if (lane.isLeftLane() && direction != Direction.LEFT && direction != Direction.U_TURN) {
            // Reportar en consola pero permitir
            System.out.println("Aviso: Carril izquierdo usado para dirección " + direction + 
                ". Se recomienda usar LEFT o U_TURN para este carril.");
        }
        
        // Configurar características por tipo
        VehicleCharacteristics characteristics = configureVehicleCharacteristics();
        this.color = characteristics.color();
        this.size = characteristics.size();
        
        this.active = true;
        this.waitingAtLight = false;
        this.inIntersection = false;
        this.atTrafficLight = false;
        this.waitStartTime = 0;
        this.currentLane = lane.getLaneNumber();
        this.isChangingLane = false;
        this.emergencyYielding = false;
    }
    
    /**
     * Configura las características del vehículo según su tipo
     */
    private VehicleCharacteristics configureVehicleCharacteristics() {
        return switch (type) {
            case EMERGENCY -> {
                this.maxSpeed = isHighwayVehicle ? 120.0 : 70.0;
                this.speed = maxSpeed * 0.8;
                this.acceleration = 5.0;
                yield new VehicleCharacteristics(Color.RED, 12.0);
            }
            case PUBLIC_TRANSPORT -> {
                this.maxSpeed = isHighwayVehicle ? 80.0 : 50.0;
                this.speed = maxSpeed * 0.7;
                this.acceleration = 2.0;
                yield new VehicleCharacteristics(Color.ORANGE, 18.0);
            }
            case HEAVY -> {
                this.maxSpeed = isHighwayVehicle ? 70.0 : 40.0;
                this.speed = maxSpeed * 0.6;
                this.acceleration = 1.5;
                yield new VehicleCharacteristics(Color.DARKGRAY, 20.0);
            }
            default -> { // NORMAL
                this.maxSpeed = isHighwayVehicle ? 100.0 : 60.0;
                this.speed = maxSpeed * 0.7;
                this.acceleration = 3.0;
                yield new VehicleCharacteristics(
                    isHighwayVehicle ? Color.DARKBLUE : Color.BLUE, 
                    10.0
                );
            }
        };
    }
    
    /**
     * Record para características visuales del vehículo
     */
    private record VehicleCharacteristics(Color color, double size) {}
    
    /**
     * Actualiza la posición del vehículo de forma thread-safe
     */
    public synchronized void updatePosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
    }
    
    /**
     * Establece si el vehículo está esperando en un semáforo
     */
    public synchronized void setWaitingAtLight(boolean waiting) {
        this.waitingAtLight = waiting;
        if (waiting && waitStartTime == 0) {
            this.waitStartTime = System.currentTimeMillis();
        } else if (!waiting) {
            this.waitStartTime = 0;
        }
    }
    
    /**
     * Establece si el vehículo está en un semáforo
     */
    public void setAtTrafficLight(boolean atLight) {
        this.atTrafficLight = atLight;
    }
    
    /**
     * Inicia un cambio de carril
     */
    public void startLaneChange(int newLane) {
        if (isHighwayVehicle && newLane >= 1 && newLane <= 3) {
            this.isChangingLane = true;
            this.currentLane = newLane;
        }
    }
    
    /**
     * Completa el cambio de carril
     */
    public void completeLaneChange() {
        this.isChangingLane = false;
    }
    
    /**
     * Ajusta la velocidad del vehículo
     */
    public void adjustSpeed(double targetSpeed) {
        if (targetSpeed > maxSpeed) {
            targetSpeed = maxSpeed;
        }
        
        double speedDiff = targetSpeed - speed;
        if (Math.abs(speedDiff) < 0.1) {
            speed = targetSpeed;
        } else if (speedDiff > 0) {
            speed += Math.min(acceleration, speedDiff);
        } else {
            speed += Math.max(-acceleration * 2, speedDiff); // Frenado más rápido
        }
        
        if (speed < 0) speed = 0;
    }
    
    /**
     * Calcula si debe ceder paso a un vehículo de emergencia
     */
    public boolean shouldYieldToEmergency(Vehicle emergencyVehicle) {
        if (!isHighwayVehicle || type == VehicleType.EMERGENCY) {
            return false;
        }
        
        if (!emergencyVehicle.isHighwayVehicle || 
            emergencyVehicle.getType() != VehicleType.EMERGENCY) {
            return false;
        }
        
        // Mismo carril y misma dirección
        if (currentLane == emergencyVehicle.getCurrentLane()) {
            boolean sameDirection = (highwayLane.isWestbound() == 
                                   emergencyVehicle.getHighwayLane().isWestbound());
            
            if (sameDirection) {
                // Verificar si la emergencia está detrás
                double distance = Math.abs(y - emergencyVehicle.getY());
                return distance < 200;
            }
        }
        
        return false;
    }
    
    /**
     * Obtiene el tiempo de espera en milisegundos
     */
    public long getWaitTime() {
        if (!waitingAtLight) return 0;
        return System.currentTimeMillis() - waitStartTime;
    }
    
    /**
     * Desactiva el vehículo al final de su ciclo de vida
     */
    public void deactivate() {
        this.active = false;
    }
    
    // Getters
    public long getId() { return id; }
    public VehicleType getType() { return type; }
    public StartPoint getStartPoint() { return startPoint; }
    public Direction getDirection() { return direction; }
    public boolean isHighwayVehicle() { return isHighwayVehicle; }
    public HighwayLane getHighwayLane() { return highwayLane; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getSpeed() { return speed; }
    public double getMaxSpeed() { return maxSpeed; }
    public double getAcceleration() { return acceleration; }
    public boolean isActive() { return active; }
    public boolean isWaitingAtLight() { return waitingAtLight; }
    public boolean isInIntersection() { return inIntersection; }
    public boolean isAtTrafficLight() { return atTrafficLight; }
    public int getCurrentLane() { return currentLane; }
    public boolean isChangingLane() { return isChangingLane; }
    public int getTargetExit() { return targetExit; }
    public Color getColor() { return color; }
    public double getSize() { return size; }
    public boolean isEmergencyYielding() { return emergencyYielding; }
    
    // Setters
    public void setSpeed(double speed) { this.speed = Math.min(speed, maxSpeed); }
    public void setInIntersection(boolean inIntersection) { this.inIntersection = inIntersection; }
    public void setTargetExit(int targetExit) { this.targetExit = targetExit; }
    public void setEmergencyYielding(boolean yielding) { this.emergencyYielding = yielding; }
    // Y añadir estos métodos getter/setter
    public int getTargetIntersection() {
        return targetIntersection;
    }

    public void setTargetIntersection(int targetIntersection) {
        this.targetIntersection = targetIntersection;
    }
    @Override
    public String toString() {
        if (isHighwayVehicle) {
            return String.format("Vehicle[id=%d, type=%s, lane=%s, dir=%s, exit=%d, pos=(%.1f,%.1f)]",
                    id, type, highwayLane, direction, targetExit, x, y);
        } else {
            return String.format("Vehicle[id=%d, type=%s, start=%s, dir=%s, pos=(%.1f,%.1f)]",
                    id, type, startPoint, direction, x, y);
        }
    }
}