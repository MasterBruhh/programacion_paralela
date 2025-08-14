package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.core.*;
import edu.pucmm.trafico.model.*;
import javafx.application.Platform;
import javafx.animation.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Path;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.QuadCurveTo;
import javafx.util.Duration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class VehicleTask implements Runnable {
    private static final Logger logger = Logger.getLogger(VehicleTask.class.getName());
    
    private final Vehicle vehicle;
    private final VehicleLifecycleManager lifecycleManager;
    private final CollisionDetector collisionDetector;
    private final AtomicBoolean active;
    
    private enum State {
        APPROACHING,
        WAITING_AT_LIGHT,
        CROSSING,
        CRUISING,
        CHANGING_LANE,
        EXITING
    }
    
    private State currentState;
    private volatile boolean isYieldingToEmergency = false;
    
    public VehicleTask(Vehicle vehicle, VehicleLifecycleManager lifecycleManager) {
        this.vehicle = vehicle;
        this.lifecycleManager = lifecycleManager;
        this.collisionDetector = new CollisionDetector();
        this.active = new AtomicBoolean(true);
        this.currentState = vehicle.isHighwayVehicle() ? State.CRUISING : State.APPROACHING;
    }
    
    @Override
    public void run() {
        try {
            while (active.get() && vehicle.isActive()) {
                if (vehicle.getType() != VehicleType.EMERGENCY && shouldYieldToEmergency()) {
                    handleYieldingToEmergency();
                    continue;
                }
                
                switch (currentState) {
                    case CRUISING -> handleHighwayCruising();
                    case CHANGING_LANE -> handleLaneChange();
                    case APPROACHING -> handleApproaching();
                    case WAITING_AT_LIGHT -> handleWaitingAtLight();
                    case CROSSING -> handleCrossing();
                    case EXITING -> {
                        handleExit();
                        return;
                    }
                }
                
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Vehículo " + vehicle.getId() + " interrumpido");
        } finally {
            active.set(false);
        }
    }
    
    private boolean shouldYieldToEmergency() {
        if (!vehicle.isHighwayVehicle()) return false;
        
        for (Vehicle emergency : lifecycleManager.getRegistry().getEmergencyVehicles()) {
            if (!emergency.isHighwayVehicle() || !emergency.isActive()) continue;
            
            if (emergency.getCurrentLane() == vehicle.getCurrentLane()) {
                boolean sameDirection = (vehicle.getHighwayLane().isNorthbound() == 
                                       emergency.getHighwayLane().isNorthbound());
                
                if (sameDirection) {
                    boolean emergencyBehind = vehicle.getHighwayLane().isNorthbound() ?
                        emergency.getY() < vehicle.getY() :
                        emergency.getY() > vehicle.getY();
                    
                    if (emergencyBehind && Math.abs(emergency.getY() - vehicle.getY()) < 200) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    private void handleYieldingToEmergency() throws InterruptedException {
        isYieldingToEmergency = true;
        
        int currentLane = vehicle.getCurrentLane();
        int targetLane = -1;
        
        if (currentLane == 1 || currentLane == 2) {
            targetLane = currentLane + 1;
        } else if (currentLane == 3) {
            vehicle.setSpeed(vehicle.getSpeed() * 0.3);
        }
        
        if (targetLane != -1 && targetLane <= 3) {
            vehicle.setCurrentLane(targetLane);
            currentState = State.CHANGING_LANE;
            return;
        }
        
        vehicle.setSpeed(20.0);
        
        if (!shouldYieldToEmergency()) {
            isYieldingToEmergency = false;
            vehicle.setSpeed(vehicle.getType() == VehicleType.EMERGENCY ? 80.0 : 65.0);
            currentState = State.CRUISING;
        }
    }
    
    private void handleHighwayCruising() throws InterruptedException {
        if (vehicle.getCurrentLane() == 1 && vehicle.getDirection() != Direction.LEFT && 
            vehicle.getDirection() != Direction.U_TURN) {
            logger.warning("Vehículo " + vehicle.getId() + " en carril izquierdo sin maniobra LEFT/U_TURN");
            vehicle.setCurrentLane(2);
            currentState = State.CHANGING_LANE;
            return;
        }
        
        CollisionDetector.VehicleAheadInfo aheadInfo = collisionDetector.detectVehicleAhead(
            vehicle, lifecycleManager.getRegistry().getAllVehicles());
        
        if (aheadInfo != null) {
            double safeSpeed = collisionDetector.calculateSafeSpeed(
                vehicle.getSpeed(), aheadInfo.getDistance(), true);
            vehicle.setSpeed(safeSpeed);
            
            if (safeSpeed == 0) {
                Thread.sleep(500);
                return;
            }
        } else {
            vehicle.setSpeed(vehicle.getType() == VehicleType.EMERGENCY ? 80.0 : 65.0);
        }
        
        moveHighwayVehicle();
        
        if (isNearIntersection()) {
            currentState = State.WAITING_AT_LIGHT;
        } else if (isAtEndOfHighway()) {
            currentState = State.EXITING;
        }
    }
    
    private void handleLaneChange() throws InterruptedException {
        moveHighwayVehicle();
        
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node != null) {
                double newX = vehicle.getHighwayLane().getStartX() + 
                             (vehicle.getCurrentLane() - 1) * 30;
                
                TranslateTransition transition = new TranslateTransition(
                    Duration.seconds(1.0), node);
                transition.setToX(newX - node.getCenterX());
                transition.setOnFinished(e -> {
                    node.setCenterX(newX);
                    node.setTranslateX(0);
                    vehicle.updatePosition(newX, vehicle.getY());
                });
                transition.play();
            }
        });
        
        Thread.sleep(1000);
        vehicle.setChangingLane(false);
        currentState = isYieldingToEmergency ? State.CRUISING : State.CRUISING;
    }
    
    private void handleApproaching() throws InterruptedException {
        double[] stopCoords = vehicle.getStartPoint().getStopCoordinates();
        double distance = collisionDetector.calculateDistance(
            vehicle.getX(), vehicle.getY(), stopCoords[0], stopCoords[1]);
        
        if (distance < 50) {
            vehicle.setWaitingAtLight(true);
            currentState = State.WAITING_AT_LIGHT;
        } else {
            moveTowardsPosition(stopCoords[0], stopCoords[1]);
        }
    }
    
    private void handleWaitingAtLight() throws InterruptedException {
        boolean canCross = false;
        
        if (vehicle.isHighwayVehicle()) {
            canCross = true;
        } else {
            String groupName = getTrafficLightGroup();
            TrafficLightState lightState = lifecycleManager.getTrafficLightState(groupName);
            
            if (vehicle.getType() == VehicleType.EMERGENCY) {
                lifecycleManager.activateEmergencyProtocol(groupName);
                canCross = lightState == TrafficLightState.GREEN || lightState == TrafficLightState.YELLOW;
            } else {
                canCross = lightState == TrafficLightState.GREEN;
            }
        }
        
        if (canCross) {
            IntersectionSemaphore semaphore = lifecycleManager.getIntersectionSemaphore();
            boolean acquired = semaphore.tryAcquire(vehicle, 100, TimeUnit.MILLISECONDS);
            
            if (acquired) {
                vehicle.setWaitingAtLight(false);
                vehicle.setInIntersection(true);
                currentState = State.CROSSING;
                performCrossing();
            }
        }
        
        Thread.sleep(100);
    }
    
    private void handleCrossing() throws InterruptedException {
        Thread.sleep(100);
    }
    
    private void handleExit() {
        active.set(false);
        Platform.runLater(() -> {
            lifecycleManager.removeVehicle(vehicle);
        });
    }
    
    private void performCrossing() {
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;
            
            Animation crossingAnimation = createCrossingAnimation(node);
            crossingAnimation.setOnFinished(e -> {
                vehicle.setInIntersection(false);
                lifecycleManager.getIntersectionSemaphore().release();
                
                if (vehicle.getType() == VehicleType.EMERGENCY && !vehicle.isHighwayVehicle()) {
                    lifecycleManager.deactivateEmergencyProtocol();
                }
                
                currentState = State.EXITING;
            });
            crossingAnimation.play();
        });
    }
    
    private Animation createCrossingAnimation(Circle node) {
        Path path = new Path();
        double startX = node.getCenterX();
        double startY = node.getCenterY();
        
        double[] exitCoords;
        if (vehicle.isHighwayVehicle()) {
            exitCoords = calculateHighwayExit();
        } else {
            exitCoords = vehicle.getStartPoint().getExitCoordinates(vehicle.getDirection());
        }
        
        double endX = exitCoords[0];
        double endY = exitCoords[1];
        
        switch (vehicle.getDirection()) {
            case STRAIGHT -> {
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    new LineTo(endX, endY)
                );
            }
            case LEFT -> {
                double controlX = 430;
                double controlY = 295;
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    new QuadCurveTo(controlX - 50, controlY, endX, endY)
                );
            }
            case RIGHT -> {
                double controlX = 430;
                double controlY = 295;
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    new QuadCurveTo(controlX + 50, controlY, endX, endY)
                );
            }
            case U_TURN -> {
                double controlX = startX;
                double controlY = startY + (vehicle.isHighwayVehicle() ? 150 : 100);
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    new QuadCurveTo(controlX, controlY, endX, endY)
                );
            }
        }
        
        PathTransition transition = new PathTransition(Duration.seconds(3), path, node);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        
        transition.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            vehicle.updatePosition(node.getCenterX() + node.getTranslateX(), 
                                 node.getCenterY() + node.getTranslateY());
        });
        
        return transition;
    }
    
    private double[] calculateHighwayExit() {
        switch (vehicle.getDirection()) {
            case LEFT -> {
                return new double[]{0, 295};
            }
            case RIGHT -> {
                return new double[]{1160, 295};
            }
            case U_TURN -> {
                if (vehicle.getHighwayLane().isNorthbound()) {
                    return new double[]{vehicle.getX() + 90, 768};
                } else {
                    return new double[]{vehicle.getX() - 90, 0};
                }
            }
            default -> {
                if (vehicle.getHighwayLane().isNorthbound()) {
                    return new double[]{vehicle.getX(), 768};
                } else {
                    return new double[]{vehicle.getX(), 0};
                }
            }
        }
    }
    
    private void moveHighwayVehicle() {
        double newY = vehicle.getY();
        double speed = vehicle.getSpeed() / 10;
        
        if (vehicle.getHighwayLane().isNorthbound()) {
            newY += speed;
        } else {
            newY -= speed;
        }
        
        if (collisionDetector.canMove(vehicle, vehicle.getX(), newY,
                lifecycleManager.getRegistry().getAllVehicles())) {
            updateVehiclePosition(vehicle.getX(), newY);
        }
    }
    
    private void moveTowardsPosition(double targetX, double targetY) {
        double dx = targetX - vehicle.getX();
        double dy = targetY - vehicle.getY();
        double distance = Math.sqrt(dx * dx + dy * dy);
        
        if (distance < 2) return;
        
        double speed = Math.min(vehicle.getSpeed() / 10, distance);
        double newX = vehicle.getX() + (dx / distance) * speed;
        double newY = vehicle.getY() + (dy / distance) * speed;
        
        if (collisionDetector.canMove(vehicle, newX, newY,
                lifecycleManager.getRegistry().getAllVehicles())) {
            updateVehiclePosition(newX, newY);
        }
    }
    
    private void updateVehiclePosition(double x, double y) {
        vehicle.updatePosition(x, y);
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node != null) {
                node.setCenterX(x);
                node.setCenterY(y);
            }
        });
    }
    
    private boolean isNearIntersection() {
        if (!vehicle.isHighwayVehicle()) return false;
        
        double y = vehicle.getY();
        double[] intersectionYs = {260, 460, 660};
        
        for (int i = 0; i < intersectionYs.length; i++) {
            if (Math.abs(y - intersectionYs[i]) < 100 && vehicle.getTargetExit() == i) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean isAtEndOfHighway() {
        if (!vehicle.isHighwayVehicle()) return false;
        
        double y = vehicle.getY();
        if (vehicle.getHighwayLane().isNorthbound()) {
            return y > 750;
        } else {
            return y < 20;
        }
    }
    
    private String getTrafficLightGroup() {
        if (vehicle.isHighwayVehicle()) {
            return vehicle.getHighwayLane().isNorthbound() ? "ABAJO" : "ARRIBA";
        } else {
            return switch (vehicle.getStartPoint()) {
                case NORTH, SOUTH -> "CalleIzq";
                case EAST, WEST -> "CalleDer";
            };
        }
    }
}