package edu.pucmm.trafico.concurrent;

import edu.pucmm.trafico.core.VehicleLifecycleManager;
import edu.pucmm.trafico.model.*;
import javafx.application.Platform;
import javafx.animation.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Path;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.QuadCurveTo;
import javafx.util.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class VehicleTask implements Runnable {
    private static final Logger logger = Logger.getLogger(VehicleTask.class.getName());
    
    // Constantes de simulación
    private static final double STOP_LINE_THRESHOLD = 10.0;
    private static final double QUEUE_SPACING = 30.0;
    private static final double SAFETY_DISTANCE = 25.0;
    private static final long STOP_WAIT_TIME = 3000; // 3 segundos en el pare
    private static final double INTERSECTION_CENTER_X = 400.0;
    private static final double INTERSECTION_CENTER_Y = 295.0;
    private static final double TURN_RADIUS = 50.0; // Radio más amplio para giros suaves
    private static final double RIGHT_TURN_RADIUS = 20.0;
    
    // Estados del vehículo
    private enum VehicleState {
        APPROACHING,      // Acercándose a la intersección
        IN_QUEUE,        // En cola detrás de otros vehículos
        AT_STOP,         // En la línea de pare
        WAITING_TURN,    // Esperando su turno para cruzar
        CROSSING,        // Cruzando la intersección
        COMPLETED        // Cruce completado
    }
    
    private final Vehicle vehicle;
    private final VehicleLifecycleManager lifecycleManager;
    private final AtomicBoolean isActive = new AtomicBoolean(true);
    private VehicleState currentState = VehicleState.APPROACHING;
    private long stopArrivalTime = 0;
    private volatile Animation currentAnimation = null;
    
    public VehicleTask(Vehicle vehicle, VehicleLifecycleManager lifecycleManager) {
        this.vehicle = vehicle;
        this.lifecycleManager = lifecycleManager;
    }
    
    @Override
    public void run() {
        try {
            while (isActive.get() && vehicle.isActive()) {
                switch (currentState) {
                    case APPROACHING:
                        approachIntersection();
                        break;
                    case IN_QUEUE:
                        waitInQueue();
                        break;
                    case AT_STOP:
                        waitAtStop();
                        break;
                    case WAITING_TURN:
                        waitForTurn();
                        break;
                    case CROSSING:
                        // El cruce se maneja con animaciones
                        Thread.sleep(100);
                        break;
                    case COMPLETED:
                        exitScene();
                        return;
                }
                
                Thread.sleep(50); // Control de ciclo
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("Vehículo " + vehicle.getId() + " interrumpido");
        } finally {
            cleanup();
        }
    }
    
    private void approachIntersection() throws InterruptedException {
        // Registrar en el controlador de intersección
        lifecycleManager.getIntersectionController()
            .registerVehicleAtStop(vehicle.getStartPoint(), String.valueOf(vehicle.getId()), vehicle.getType());
        
        // Mover hacia la intersección con detección de colisiones
        moveTowardsStopWithCollisionDetection();
        
        // Verificar posición en la cola
        int queuePosition = lifecycleManager.getIntersectionController()
            .getQueuePosition(vehicle.getStartPoint(), String.valueOf(vehicle.getId()));
        
        if (queuePosition == 0) {
            // Es el primero, ir directo al pare
            currentState = VehicleState.AT_STOP;
        } else {
            // Hay vehículos adelante, unirse a la cola
            currentState = VehicleState.IN_QUEUE;
        }
    }
    
    private void waitInQueue() throws InterruptedException {
        int queuePosition = lifecycleManager.getIntersectionController()
            .getQueuePosition(vehicle.getStartPoint(), String.valueOf(vehicle.getId()));
        
        if (queuePosition == 0) {
            // Ahora es el primero, avanzar al pare
            currentState = VehicleState.AT_STOP;
            moveToStop();
        } else {
            // Mantener posición en la cola con detección de colisiones
            maintainQueuePositionWithCollisionDetection(queuePosition);
        }
    }
    
    private void waitAtStop() throws InterruptedException {
        if (stopArrivalTime == 0) {
            stopArrivalTime = System.currentTimeMillis();
            vehicle.setWaitingAtStop(true);
            
            // Notificar al controlador que llegó al pare
            lifecycleManager.getIntersectionController()
                .vehicleReachedStop(vehicle.getStartPoint(), String.valueOf(vehicle.getId()));
            
            logger.info("Vehículo " + vehicle.getId() + " llegó al PARE");
        }
        
        // Esperar tiempo mínimo en el pare
        long elapsedTime = System.currentTimeMillis() - stopArrivalTime;
        if (elapsedTime < STOP_WAIT_TIME) {
            Thread.sleep(100);
            return;
        }
        
        // Tiempo de espera completado, cambiar a esperando turno
        currentState = VehicleState.WAITING_TURN;
    }
    
    private void waitForTurn() throws InterruptedException {
        // Solicitar turno para cruzar
        boolean turnGranted = lifecycleManager.getIntersectionController()
            .requestCrossingTurn(vehicle.getStartPoint(), String.valueOf(vehicle.getId()));
        
        if (turnGranted) {
            logger.info("Vehículo " + vehicle.getId() + " obtuvo turno para cruzar");
            currentState = VehicleState.CROSSING;
            vehicle.setWaitingAtStop(false);
            performCrossing();
        } else {
            // Seguir esperando
            Thread.sleep(100);
        }
    }
    
    private void performCrossing() {
        Platform.runLater(() -> {
            Circle visualNode = lifecycleManager.getVehicleNode(vehicle.getId());
            if (visualNode == null) return;
            
            // Crear animación según la dirección
            Animation crossingAnimation = createCrossingAnimation(visualNode);
            currentAnimation = crossingAnimation;
            
            crossingAnimation.setOnFinished(e -> {
                // Posición final basada en coordenadas de salida exactas
                double[] exitCoords = vehicle.getStartPoint().getExitCoordinates(vehicle.getDirection());
                double finalX = exitCoords[0];
                double finalY = exitCoords[1];
                visualNode.setCenterX(finalX);
                visualNode.setCenterY(finalY);
                visualNode.setTranslateX(0);
                visualNode.setTranslateY(0);
                vehicle.updatePosition(finalX, finalY);

                // Actualizar inmediatamente la posición en el lifecycle manager
                lifecycleManager.updateVehiclePosition(vehicle);

                // Ahora que la posición está sincronizada, cambiar estado y notificar
                currentState = VehicleState.COMPLETED;
                lifecycleManager.getIntersectionController()
                    .notifyCrossingComplete(vehicle.getStartPoint(), String.valueOf(vehicle.getId()));
                
                // Esperar un pequeño momento para asegurar que la posición se actualice
                Platform.runLater(() -> {
                    // Verificación final de posición
                    if (Math.abs(vehicle.getX() - finalX) > 0.1 || 
                        Math.abs(vehicle.getY() - finalY) > 0.1) {
                        logger.warning("Corrigiendo desajuste de posición final para vehículo " + 
                                    vehicle.getId());
                        vehicle.updatePosition(finalX, finalY);
                        lifecycleManager.updateVehiclePosition(vehicle);
                    }
                    // Ahora sí podemos proceder con la limpieza
                    exitScene();
                });
            });
            
            crossingAnimation.play();
        });
    }
    
    private Animation createCrossingAnimation(Circle node) {
        switch (vehicle.getDirection()) {
            case STRAIGHT:
                return createStraightAnimation(node);
            case LEFT:
                return createLeftTurnAnimation(node);
            case RIGHT:
                return createRightTurnAnimation(node);
            case U_TURN:
                return createUTurnAnimation(node);
            default:
                return createStraightAnimation(node);
        }
    }
    
    /**
     * Crea una animación de movimiento recto mejorada con velocidad y trayectoria realistas.
     * Incluye actualización continua de la posición del modelo.
     */
    private Animation createStraightAnimation(Circle node) {
        Path path = new Path();
        double startX = node.getCenterX();
        double startY = node.getCenterY();
        
        // Obtener las coordenadas de salida basadas en el carril correcto
        double[] exitCoords = vehicle.getStartPoint().getExitCoordinates(Direction.STRAIGHT);
        double endX = exitCoords[0];
        double endY = exitCoords[1];
        
        // Crear una trayectoria recta pero con puntos intermedios para mejor seguimiento
        path.getElements().add(new MoveTo(startX, startY));
        
        // Agregar puntos intermedios para un seguimiento más preciso
        switch (vehicle.getStartPoint()) {
            case NORTH, SOUTH -> {
                // Movimiento vertical: agregar punto intermedio en Y
                double midY = (startY + endY) / 2;
                path.getElements().add(new LineTo(startX, midY));
            }
            case EAST, WEST -> {
                // Movimiento horizontal: agregar punto intermedio en X
                double midX = (startX + endX) / 2;
                path.getElements().add(new LineTo(midX, startY));
            }
        }
        
        // Punto final exacto
        path.getElements().add(new LineTo(endX, endY));
        
        // Guardar las coordenadas finales en el vehículo
        vehicle.updatePosition(endX, endY);
        
        // Usar PathTransition en lugar de Timeline para mejor control
        PathTransition transition = new PathTransition(Duration.seconds(4), path, node);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        
        // Actualizar posición del vehículo durante la animación para mantener coherencia
        transition.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime.toSeconds() > oldTime.toSeconds()) {
                // Actualizar posición en el modelo basada en la posición visual
                Platform.runLater(() -> {
                    vehicle.updatePosition(node.getCenterX(), node.getCenterY());
                });
            }
        });
        
        return transition;
    }
    
    /**
     * Crea una animación de giro a la izquierda más realista y precisa,
     * basada en el código de referencia del viejo sistema.
     */
    private Animation createLeftTurnAnimation(Circle node) {
        Path path = new Path();
        double startX = node.getCenterX();
        double startY = node.getCenterY();
        
        // Obtener las coordenadas de salida para este giro específico
        double[] exitCoords = vehicle.getStartPoint().getExitCoordinates(Direction.LEFT);
        double endX = exitCoords[0];
        double endY = exitCoords[1];
        
        // Usar curvas cuadráticas para crear giros más naturales
        switch (vehicle.getStartPoint()) {
            case NORTH -> {
                // Norte hacia Oeste (giro a la izquierda)
                double controlX = INTERSECTION_CENTER_X - 20;
                double controlY = INTERSECTION_CENTER_Y + 20;
                
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    // Avanzar hasta el punto de inicio del giro
                    new LineTo(startX, INTERSECTION_CENTER_Y),
                    // Realizar un giro suave con curva cuadrática
                    new QuadCurveTo(
                        controlX, controlY,
                        INTERSECTION_CENTER_X + TURN_RADIUS, endY
                    ),
                    // Salir hacia la dirección correcta con alineación precisa
                    new LineTo(endX, endY)
                );
            }
            case SOUTH -> {
                // Sur hacia Este (giro a la izquierda)
                double controlX = INTERSECTION_CENTER_X + 20;
                double controlY = INTERSECTION_CENTER_Y - 20;
                
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    // Avanzar hasta el punto de inicio del giro
                    new LineTo(startX, INTERSECTION_CENTER_Y),
                    // Realizar un giro suave con curva cuadrática
                    new QuadCurveTo(
                        controlX, controlY,
                        INTERSECTION_CENTER_X - TURN_RADIUS, endY
                    ),
                    // Salir hacia la dirección correcta con alineación precisa
                    new LineTo(endX, endY)
                );
            }
            case EAST -> {
                // Este hacia Sur (giro a la izquierda)
                double controlX = INTERSECTION_CENTER_X - 20;
                double controlY = INTERSECTION_CENTER_Y + 20;

                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    // Avanzar hasta el punto de inicio del giro
                    new LineTo(INTERSECTION_CENTER_X, startY),
                    // Realizar un giro suave con curva cuadrática
                    new QuadCurveTo(
                        controlX, controlY,
                        endX, INTERSECTION_CENTER_Y + TURN_RADIUS
                    ),
                    // Salir hacia la dirección correcta con alineación precisa
                    new LineTo(endX, endY)
                );
            }
            case WEST -> {
                // Oeste hacia Norte (giro a la izquierda)
                double controlX = INTERSECTION_CENTER_X + 20;
                double controlY = INTERSECTION_CENTER_Y - 20;

                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    // Avanzar hasta el punto de inicio del giro
                    new LineTo(INTERSECTION_CENTER_X, startY),
                    // Realizar un giro suave con curva cuadrática
                    new QuadCurveTo(
                        controlX, controlY,
                        endX, INTERSECTION_CENTER_Y - TURN_RADIUS
                    ),
                    // Salir hacia la dirección correcta con alineación precisa
                    new LineTo(endX, endY)
                );
            }
        }
        
        // Guardar las coordenadas finales en el vehículo
        vehicle.updatePosition(endX, endY);
        
        // Duración más larga (5s) para un giro más realista
        PathTransition transition = new PathTransition(Duration.seconds(5), path, node);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        
        // Actualizar posición del vehículo durante la animación para mantener coherencia
        transition.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime.toSeconds() > oldTime.toSeconds()) {
                // Actualizar posición en el modelo basada en la posición visual
                Platform.runLater(() -> {
                    vehicle.updatePosition(node.getCenterX(), node.getCenterY());
                });
            }
        });
        
        return transition;
    }
    
    /**
     * Crea una animación de giro a la derecha mejorada con trayectorias más naturales.
     * Implementa lógica del código viejo con mejoras visuales.
     */
    private Animation createRightTurnAnimation(Circle node) {
        Path path = new Path();
        double startX = node.getCenterX();
        double startY = node.getCenterY();
        
        // Obtener las coordenadas de salida para este giro específico
        double[] exitCoords = vehicle.getStartPoint().getExitCoordinates(Direction.RIGHT);
        double endX = exitCoords[0];
        double endY = exitCoords[1];
        
        switch (vehicle.getStartPoint()) {
            case NORTH -> {
                // Norte hacia Este (giro a la derecha)
                // Usar control points precisos basados en el código viejo
                double controlX = INTERSECTION_CENTER_X + 15;
                double controlY = INTERSECTION_CENTER_Y - 15;
                
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    // Avanzar hasta el punto de inicio del giro
                    new LineTo(startX, INTERSECTION_CENTER_Y - RIGHT_TURN_RADIUS),
                    // Realizar un giro suave con curva cuadrática bien ajustada
                    new QuadCurveTo(
                        controlX, controlY,
                        RIGHT_TURN_RADIUS, endY
                    ),
                    // Salir hacia la dirección correcta con alineación precisa
                    new LineTo(endX, endY)
                );
            }
            case SOUTH -> {
                // Sur hacia Oeste (giro a la derecha)
                double controlX = INTERSECTION_CENTER_X - 15;
                double controlY = INTERSECTION_CENTER_Y + 15;
                
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    // Avanzar hasta el punto de inicio del giro
                    new LineTo(startX, INTERSECTION_CENTER_Y + RIGHT_TURN_RADIUS),
                    // Realizar un giro suave con curva cuadrática bien ajustada
                    new QuadCurveTo(
                        controlX, controlY,
                        INTERSECTION_CENTER_X + RIGHT_TURN_RADIUS, endY
                    ),
                    // Salir hacia la dirección correcta con alineación precisa
                    new LineTo(endX, endY)
                );
            }
            case EAST -> {
                // Este hacia Norte (giro a la derecha)
                double controlX = INTERSECTION_CENTER_X + 15;
                double controlY = INTERSECTION_CENTER_Y - 15;
                
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    // Avanzar hasta el punto de inicio del giro
                    new LineTo(INTERSECTION_CENTER_X + RIGHT_TURN_RADIUS, startY),
                    // Realizar un giro suave con curva cuadrática bien ajustada
                    new QuadCurveTo(
                        controlX, controlY,
                        endX, INTERSECTION_CENTER_Y - RIGHT_TURN_RADIUS
                    ),
                    // Salir hacia la dirección correcta con alineación precisa
                    new LineTo(endX, endY)
                );
            }
            case WEST -> {
                // Oeste hacia Sur (giro a la derecha)
                double controlX = INTERSECTION_CENTER_X - 15;
                double controlY = INTERSECTION_CENTER_Y + 15;
                
                path.getElements().addAll(
                    new MoveTo(startX, startY),
                    // Avanzar hasta el punto de inicio del giro
                    new LineTo(INTERSECTION_CENTER_X - RIGHT_TURN_RADIUS, startY),
                    // Realizar un giro suave con curva cuadrática bien ajustada
                    new QuadCurveTo(
                        controlX, controlY,
                        endX, INTERSECTION_CENTER_Y + RIGHT_TURN_RADIUS
                    ),
                    // Salir hacia la dirección correcta con alineación precisa
                    new LineTo(endX, endY)
                );
            }
        }
        
        // Guardar las coordenadas finales en el vehículo
        vehicle.updatePosition(endX, endY);
        
        // Duración más larga para un giro más realista (3.5 segundos)
        PathTransition transition = new PathTransition(Duration.seconds(3.5), path, node);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        
        // Actualizar posición del vehículo durante la animación para mantener coherencia
        transition.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime.toSeconds() > oldTime.toSeconds()) {
                // Actualizar posición en el modelo basada en la posición visual
                Platform.runLater(() -> {
                    vehicle.updatePosition(node.getCenterX(), node.getCenterY());
                });
            }
        });
        
        return transition;
    }
    
    /**
     * Crea una animación de vuelta en U mejorada con trayectoria altamente precisa.
     * Esta implementación está basada en el análisis del código viejo pero con mejoras visuales.
     */
    private Animation createUTurnAnimation(Circle node) {
        Path path = new Path();
        double startX = node.getCenterX();
        double startY = node.getCenterY();
        double uTurnRadius = 45.0;

        // Obtener las coordenadas de salida para este giro específico
        double[] exitCoords = vehicle.getStartPoint().getExitCoordinates(Direction.U_TURN);
        double endX = exitCoords[0];
        double endY = exitCoords[1];

        switch (vehicle.getStartPoint()) {
            case NORTH -> {
                final double r = uTurnRadius;
                final double cx = INTERSECTION_CENTER_X;
                final double cy = INTERSECTION_CENTER_Y;

                path.getElements().addAll(
                        new MoveTo(startX, startY),

                        // Acercarse al punto de inicio del giro (parte inferior de la curva)
                        new LineTo(startX, cy - r),

                        // Primer tramo del giro: hacia la izquierda y arriba (pequeño arco, sweep=false)
                        new ArcTo(
                                r, r, 0,
                                cx, cy,
                                false, false // small-arc, sentido inverso al que tenías
                        ),

                        // Segundo tramo del giro: hacia el carril de retorno (pequeño arco, sweep=false)
                        new ArcTo(
                                r, r, 0,
                                endX, cy - r,
                                false, false
                        ),

                        // Alinear con el carril de salida (sur)
                        new LineTo(endX, endY)
                );
            }
            case SOUTH -> {
                // Vuelta en U desde Sur (regresa hacia Sur)
                final double r = uTurnRadius;
                final double cx = INTERSECTION_CENTER_X;
                final double cy = INTERSECTION_CENTER_Y;

                path.getElements().addAll(
                        new MoveTo(startX, startY),

                        // Acercarse al punto de inicio del giro (parte inferior de la curva)
                        new LineTo(startX, cy + r),

                        // Primer tramo del giro: hacia la izquierda y arriba (pequeño arco, sweep=false)
                        new ArcTo(
                                r, r, 0,
                                cx, cy,
                                false, false // small-arc, sentido inverso al que tenías
                        ),

                        // Segundo tramo del giro: hacia el carril de retorno (pequeño arco, sweep=false)
                        new ArcTo(
                                r, r, 0,
                                endX, cy + r,
                                false, false
                        ),

                        // Alinear con el carril de salida (sur)
                        new LineTo(endX, endY)
                );
            }
            case EAST -> {
                final double r = uTurnRadius;
                final double cx = INTERSECTION_CENTER_X;
                final double cy = INTERSECTION_CENTER_Y;

                path.getElements().addAll(
                        new MoveTo(startX, startY),

                        // Acercarse al punto de inicio del giro (parte inferior de la curva)
                        new LineTo(cx+r, startY),

                        // Primer tramo del giro: hacia la izquierda y arriba (pequeño arco, sweep=false)
                        new ArcTo(
                                r, r, 0,
                                cx, cy,
                                false, false // small-arc, sentido inverso al que tenías
                        ),

                        // Segundo tramo del giro: hacia el carril de retorno (pequeño arco, sweep=false)
                        new ArcTo(
                                r, r, 0,
                                cx+r, endY,
                                false, false
                        ),

                        // Alinear con el carril de salida (sur)
                        new LineTo(endX, endY)
                );
            }
            case WEST -> {
                // Vuelta en U desde Sur (regresa hacia Sur)
                final double r = uTurnRadius;
                final double cx = INTERSECTION_CENTER_X;
                final double cy = INTERSECTION_CENTER_Y;

                path.getElements().addAll(
                        new MoveTo(startX, startY),

                        // Acercarse al punto de inicio del giro (parte inferior de la curva)
                        new LineTo(cx-r, startY),

                        // Primer tramo del giro: hacia la izquierda y arriba (pequeño arco, sweep=false)
                        new ArcTo(
                                r, r, 0,
                                cx, cy,
                                false, false // small-arc, sentido inverso al que tenías
                        ),

                        // Segundo tramo del giro: hacia el carril de retorno (pequeño arco, sweep=false)
                        new ArcTo(
                                r, r, 0,
                                cx-r, endY,
                                false, false
                        ),

                        // Alinear con el carril de salida (sur)
                        new LineTo(endX, endY)
                );
            }
        }

        // Guardar las coordenadas finales en el vehículo
        vehicle.updatePosition(endX, endY);

        // Duración más larga (6s) para un giro más realista y visible
        PathTransition transition = new PathTransition(Duration.seconds(6), path, node);
        transition.setInterpolator(Interpolator.EASE_BOTH);

        // Actualizar posición del vehículo durante la animación para mantener coherencia
        transition.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (newTime.toSeconds() > oldTime.toSeconds()) {
                // Actualizar posición en el modelo basada en la posición visual
                Platform.runLater(() -> {
                    vehicle.updatePosition(node.getCenterX(), node.getCenterY());
                });
            }
        });

        return transition;
    }
    
    // Detector de colisiones avanzado para evitar sobreposición
    private final CollisionDetector collisionDetector = new CollisionDetector();
    private boolean isFirstMove = true; // Flag para identificar primer movimiento
    
    private void moveTowardsStopWithCollisionDetection() {
        // Si es el primer movimiento, posicionar directamente sin animación
        if (isFirstMove) {
            double[] stopCoords = getStopCoordinates();
            double targetX = stopCoords[0];
            double targetY = stopCoords[1];
            
            // Calcular posición exacta en la cola sin sobreposiciones
            CollisionDetector.VehicleAheadInfo vehicleAhead = 
                collisionDetector.detectVehicleAhead(vehicle, lifecycleManager.getAllActiveVehicles());
            
            if (vehicleAhead != null) {
                // Si hay un vehículo adelante, calcular posición exacta de cola
                switch (vehicle.getStartPoint()) {
                    case NORTH -> targetY = vehicleAhead.getVehicle().getY() - QUEUE_SPACING;
                    case SOUTH -> targetY = vehicleAhead.getVehicle().getY() + QUEUE_SPACING;
                    case EAST -> targetX = vehicleAhead.getVehicle().getX() + QUEUE_SPACING;
                    case WEST -> targetX = vehicleAhead.getVehicle().getX() - QUEUE_SPACING;
                }
                logger.info("Vehículo " + vehicle.getId() + 
                           " posicionado en cola detrás de " + vehicleAhead.getVehicle().getId());
            }
            
            // Actualizar posición del vehículo directamente
            updateVehiclePosition(targetX, targetY);
            isFirstMove = false;
            return;
        }
        
        // Para movimientos posteriores, usar animación suave
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;
            
            Timeline moveTimeline = new Timeline();
            double[] stopCoords = getStopCoordinates();
            
            // Calculamos las coordenadas finales considerando colisiones
            double[] targetPosition = calculateTargetPositionWithCollisionAvoidance(stopCoords);
            final double targetX = targetPosition[0];
            final double targetY = targetPosition[1];
            
            // Animación fluida con interpolación precisa
            KeyFrame moveFrame = new KeyFrame(Duration.seconds(1.5),
                new KeyValue(node.centerXProperty(), targetX, Interpolator.EASE_OUT),
                new KeyValue(node.centerYProperty(), targetY, Interpolator.EASE_OUT)
            );
            
            moveTimeline.getKeyFrames().add(moveFrame);
            moveTimeline.setOnFinished(e -> {
                // Actualizar modelo al finalizar para mayor precisión
                vehicle.updatePosition(targetX, targetY);
            });
            moveTimeline.play();
            
            // Actualizar la posición en el modelo inmediatamente
            vehicle.updatePosition(targetX, targetY);
        });
        
        // Esperar a que termine la animación
        try {
            Thread.sleep(1500); // Duración reducida para mayor fluidez
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Calcula la posición objetivo considerando la detección de colisiones.
     * Implementa lógica sofisticada para prevenir sobreposiciones.
     */
    private double[] calculateTargetPositionWithCollisionAvoidance(double[] baseCoords) {
        double targetX = baseCoords[0];
        double targetY = baseCoords[1];
        
        // Obtener información precisa del vehículo adelante
        CollisionDetector.VehicleAheadInfo vehicleAhead = 
            collisionDetector.detectVehicleAhead(vehicle, lifecycleManager.getAllActiveVehicles());
        
        if (vehicleAhead != null) {
            // Ajustar posición basándose en el vehículo adelante
            double safeDistance = QUEUE_SPACING;
            Vehicle ahead = vehicleAhead.getVehicle();
            
            switch (vehicle.getStartPoint()) {
                case NORTH -> targetY = Math.min(targetY, ahead.getY() - safeDistance);
                case SOUTH -> targetY = Math.max(targetY, ahead.getY() + safeDistance);
                case EAST -> targetX = Math.max(targetX, ahead.getX() + safeDistance);
                case WEST -> targetX = Math.min(targetX, ahead.getX() - safeDistance);
            }
        }
        
        return new double[]{targetX, targetY};
    }

    private void moveToStop() {
        double[] stopCoords = getStopCoordinates();
        // Animación suave al avanzar al stop en lugar de teletransportarse
        updateVehiclePositionSmooth(stopCoords[0], stopCoords[1]);
    }
    
    /**
     * Mantiene la posición correcta en la cola con prevención de colisiones avanzada.
     * Implementación basada en el código viejo con mejoras de fluidez visual.
     */
    private void maintainQueuePositionWithCollisionDetection(int position) {
        double[] stopCoords = getStopCoordinates();
        double queueOffset = position * QUEUE_SPACING;
        
        // Calcular posición teórica basada en la posición en la cola
        double targetX = stopCoords[0];
        double targetY = stopCoords[1];
        
        switch (vehicle.getStartPoint()) {
            case NORTH -> targetY -= queueOffset;
            case SOUTH -> targetY += queueOffset;
            case EAST -> targetX += queueOffset;
            case WEST -> targetX -= queueOffset;
        }
        
        // Detectar el vehículo adelante usando el detector de colisiones avanzado
        CollisionDetector.VehicleAheadInfo aheadInfo = 
            collisionDetector.detectVehicleAhead(vehicle, lifecycleManager.getAllActiveVehicles());
        
        // Si hay un vehículo adelante, ajustar la posición para evitar colisiones
        if (aheadInfo != null) {
            Vehicle ahead = aheadInfo.getVehicle();
            double distance = aheadInfo.getDistance();
            
            // Si está demasiado cerca, ajustar posición para mantener distancia segura
            if (distance < QUEUE_SPACING) {
                switch (vehicle.getStartPoint()) {
                    case NORTH -> targetY = ahead.getY() - QUEUE_SPACING;
                    case SOUTH -> targetY = ahead.getY() + QUEUE_SPACING;
                    case EAST -> targetX = ahead.getX() + QUEUE_SPACING;
                    case WEST -> targetX = ahead.getX() - QUEUE_SPACING;
                }
                
                logger.fine("Vehículo " + vehicle.getId() + " ajustado en cola - distancia al vehículo " + 
                          ahead.getId() + ": " + String.format("%.1f", distance));
            }
            // Si la distancia es menor que el umbral de seguridad pero mayor que QUEUE_SPACING,
            // mantener posición actual para evitar movimientos innecesarios (estabilización)
            else if (distance < SAFETY_DISTANCE) {
                return;
            }
        }
        
        // Aplicar una animación más suave si el movimiento es significativo
        double currentX = vehicle.getX();
        double currentY = vehicle.getY();
        double moveDistance = collisionDetector.calculateDistance(currentX, currentY, targetX, targetY);
        
        // Solo mover si la distancia es significativa para evitar micro-oscilaciones
        if (moveDistance > 2.0) {
            updateVehiclePositionSmooth(targetX, targetY);
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
    
    private void updateVehiclePositionSmooth(double targetX, double targetY) {
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node == null) return;
            
            double currentX = node.getCenterX();
            double currentY = node.getCenterY();
            
            // Calcular distancia al objetivo
            double distance = Math.sqrt(
                Math.pow(targetX - currentX, 2) + 
                Math.pow(targetY - currentY, 2)
            );
            
            // Si ya está muy cerca, no hacer animación
            if (distance < 2.0) {
                node.setCenterX(targetX);
                node.setCenterY(targetY);
                vehicle.updatePosition(targetX, targetY);
                return;
            }
            
            // Animación suave hacia la posición objetivo
            Timeline smoothMove = new Timeline();
            KeyFrame moveFrame = new KeyFrame(Duration.millis(500),
                new KeyValue(node.centerXProperty(), targetX, Interpolator.EASE_BOTH),
                new KeyValue(node.centerYProperty(), targetY, Interpolator.EASE_BOTH)
            );
            
            smoothMove.getKeyFrames().add(moveFrame);
            smoothMove.setOnFinished(e -> {
                vehicle.updatePosition(targetX, targetY);
            });
            smoothMove.play();
        });
    }
    
    private double[] getStopCoordinates() {
        // Usar las coordenadas de pare definidas en StartPoint
        return vehicle.getStartPoint().getStopCoordinates();
    }
    
    private void exitScene() {
        // Asegurar una última vez que estamos usando la posición actual correcta
        Platform.runLater(() -> {
            Circle node = lifecycleManager.getVehicleNode(vehicle.getId());
            if (node != null) {
                // Sincronizar una última vez la posición del modelo con la visual
                vehicle.updatePosition(node.getCenterX(), node.getCenterY());
                // Actualizar en el lifecycle manager para que use esta posición final
                lifecycleManager.updateVehiclePosition(vehicle);
                // Ahora sí podemos remover el vehículo, que usará la posición correcta
                lifecycleManager.removeVehicle(vehicle);
            }
        });
        isActive.set(false);
    }
    
    private void cleanup() {
        if (currentAnimation != null) {
            Platform.runLater(() -> {
                currentAnimation.stop();
            });
        }
    }
}