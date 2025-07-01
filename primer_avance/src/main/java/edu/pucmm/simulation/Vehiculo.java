package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Clase abstracta que representa un vehículo como agente independiente.
 * Cada vehículo es un Runnable que ejecuta su propio ciclo de simulación.
 */
public abstract class Vehiculo implements Runnable {
    
    private static final Logger logger = Logger.getLogger(Vehiculo.class.getName());
    private static final int TICK_INTERVAL_MS = 16; // ~60 FPS
    
    protected final String id;
    protected final TipoVehiculo tipo;
    protected volatile double posX;
    protected volatile double posY;
    protected volatile double velocidad;
    protected volatile Direccion direccion;
    
    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean paused = new AtomicBoolean(false);
    protected final ISimulationModel simulationModel;
    
    protected Vehiculo(String id, TipoVehiculo tipo, double posX, double posY, 
                      double velocidad, Direccion direccion, ISimulationModel simulationModel) {
        this.id = id;
        this.tipo = tipo;
        this.posX = posX;
        this.posY = posY;
        this.velocidad = velocidad;
        this.direccion = direccion;
        this.simulationModel = simulationModel;
    }
    
    @Override
    public final void run() {
        running.set(true);
        logger.info("iniciando agente vehículo: " + id + " (" + tipo + ")");
        
        try {
            while (running.get()) {
                if (!paused.get()) {
                    // ciclo de simulación por tick
                    executeTick();
                }
                
                Thread.sleep(TICK_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.info("agente vehículo interrumpido: " + id);
        } finally {
            logger.info("finalizando agente vehículo: " + id);
        }
    }
    
    /**
     * Ejecuta un tick de simulación del agente.
     */
    private void executeTick() {
        // 1. calcular movimiento
        MovimientoInfo nextMovement = calcularMovimiento();
        
        // 2. interactuar con entorno
        if (puedeRealizarMovimiento(nextMovement)) {
            // 3. aplicar movimiento
            aplicarMovimiento(nextMovement);
            
            // 4. publicar estado
            publishState();
        }
        
        // lógica específica del tipo de vehículo
        executeTypeSpecificLogic();
    }
    
    /**
     * Calcula el próximo movimiento basándose en velocidad y dirección.
     */
    protected MovimientoInfo calcularMovimiento() {
        double deltaTime = TICK_INTERVAL_MS / 1000.0; // convertir a segundos
        double distancia = velocidad * deltaTime;
        
        // calcular nueva posición basándose en la dirección
        double nextX = posX;
        double nextY = posY;
        
        switch (direccion) {
            case derecha -> nextX += distancia;
            case recto -> nextY -= distancia; // hacia arriba
            case izquierda -> nextX -= distancia;
            case vuelta_u -> nextY += distancia; // hacia abajo
        }
        
        return new MovimientoInfo(nextX, nextY, distancia);
    }
    
    /**
     * Verifica si el vehículo puede realizar el movimiento propuesto.
     */
    protected boolean puedeRealizarMovimiento(MovimientoInfo movimiento) {
        return simulationModel.puedeAvanzar(id, movimiento.nextX(), movimiento.nextY());
    }
    
    /**
     * Aplica el movimiento calculado.
     */
    protected void aplicarMovimiento(MovimientoInfo movimiento) {
        this.posX = movimiento.nextX();
        this.posY = movimiento.nextY();
    }
    
    /**
     * Publica el estado actual al modelo de simulación.
     */
    protected void publishState() {
        VehiculoState estado = new VehiculoState(id, posX, posY, tipo);
        simulationModel.publishState(estado);
    }
    
    /**
     * Lógica específica del tipo de vehículo (template method).
     */
    protected abstract void executeTypeSpecificLogic();
    
    // métodos de control
    public void pause() {
        paused.set(true);
        logger.info("pausando vehículo: " + id);
    }
    
    public void resume() {
        paused.set(false);
        logger.info("reanudando vehículo: " + id);
    }
    
    public void stop() {
        running.set(false);
        logger.info("deteniendo vehículo: " + id);
    }
    
    // getters
    public String getId() { return id; }
    public TipoVehiculo getTipo() { return tipo; }
    public double getPosX() { return posX; }
    public double getPosY() { return posY; }
    public double getVelocidad() { return velocidad; }
    public Direccion getDireccion() { return direccion; }
    public boolean isRunning() { return running.get(); }
    public boolean isPaused() { return paused.get(); }
    
    /**
     * Record para encapsular información de movimiento.
     */
    protected record MovimientoInfo(double nextX, double nextY, double distancia) {}
} 