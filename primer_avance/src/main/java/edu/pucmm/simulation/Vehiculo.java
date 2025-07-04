package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
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
    public enum FaseMovimiento {
        AVANZANDO,
        GIRANDO,
        GIRA_DERECHA,
        GIRA_IZQUIERDA,
        AVANZA_DENTRO_CRUCE,
        GIRA_IZQUIERDA_2 }
    protected FaseMovimiento faseMovimiento = FaseMovimiento.AVANZANDO;

    protected final String id;
    protected final TipoVehiculo tipo;
    protected volatile double posX;
    protected volatile double posY;
    protected volatile double velocidad;
    protected volatile Direccion direccion;
    protected volatile PuntoSalida puntoSalida;

    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean paused = new AtomicBoolean(false);
    protected final ISimulationModel simulationModel;

    protected Vehiculo(String id, TipoVehiculo tipo, double posX, double posY,
                       double velocidad, Direccion direccion, ISimulationModel simulationModel, PuntoSalida puntoSalida) {
        this.id = id;
        this.tipo = tipo;
        this.posX = posX;
        this.posY = posY;
        this.velocidad = velocidad;
        this.direccion = direccion;
        this.simulationModel = simulationModel;
        this.puntoSalida = puntoSalida;
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
        // 1. verificar si está cerca de intersección y manejar cruce
        try {
            manejarIntersecciones();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("vehículo " + id + " interrumpido durante manejo de intersecciones");
            return;
        }

        // 2. calcular movimiento normal
        MovimientoInfo nextMovement = calcularMovimiento();

        // 3. interactuar con entorno
        if (puedeRealizarMovimiento(nextMovement)) {
            // 4. aplicar movimiento
            aplicarMovimiento(nextMovement);

            // 5. publicar estado
            publishState();
        }

        // 6. lógica específica del tipo de vehículo
        executeTypeSpecificLogic();
    }

    /**
     * Calcula el próximo movimiento basándose en velocidad y dirección.
     * Ahora la dirección es relativa al punto de salida.
     */
    protected MovimientoInfo calcularMovimiento() {
        double deltaTime = TICK_INTERVAL_MS / 1000.0;
        double distancia = velocidad * deltaTime;
        double nextX = posX;
        double nextY = posY;

        switch (puntoSalida) {
            case ABAJO -> {
                if (direccion == Direccion.recto) {
                    nextY -= distancia;
                } else if (direccion == Direccion.derecha) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posY > 320) {
                            nextY -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_DERECHA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_DERECHA) {
                        nextX += distancia;
                    }
                } else if (direccion == Direccion.izquierda) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posY > 270) {
                            nextY -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        nextX -= distancia;
                    }
                } else if (direccion == Direccion.vuelta_u) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posY > 320) {
                            nextY -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        if (posX > 375) {
                            nextX -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA_2;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2) {
                        nextY += distancia;
                    }
                }
            }
            case ARRIBA -> {
                if (direccion == Direccion.recto) {
                    nextY += distancia;
                } else if (direccion == Direccion.derecha) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posY < 270) {
                            nextY += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_DERECHA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_DERECHA) {
                        nextX -= distancia;
                    }
                } else if (direccion == Direccion.izquierda) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posY < 320) {
                            nextY += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        nextX += distancia;
                    }
                } else if (direccion == Direccion.vuelta_u) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posY < 270) {
                            nextY += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        if (posX < 420) {
                            nextX += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA_2;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2) {
                        nextY -= distancia;
                    }
                }
            }
            case IZQUIERDA -> {
                if (direccion == Direccion.recto) {
                    nextX += distancia;
                } else if (direccion == Direccion.derecha) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posX < 375) {
                            nextX += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_DERECHA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_DERECHA) {
                        nextY += distancia;
                    }
                } else if (direccion == Direccion.izquierda) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posX < 420) {
                            nextX += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        nextY -= distancia;
                    }
                } else if (direccion == Direccion.vuelta_u) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posX < 375) {
                            nextX += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        if (posY > 270) {
                            nextY -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA_2;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2) {
                        nextX -= distancia;
                    }
                }
            }
            case DERECHA -> {
                if (direccion == Direccion.recto) {
                    nextX -= distancia;
                } else if (direccion == Direccion.derecha) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posX > 420) {
                            nextX -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_DERECHA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_DERECHA) {
                        nextY -= distancia;
                    }
                } else if (direccion == Direccion.izquierda) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posX > 375) {
                            nextX -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        nextY += distancia;
                    }
                } else if (direccion == Direccion.vuelta_u) {
                    if (faseMovimiento == FaseMovimiento.AVANZANDO) {
                        if (posX > 420) {
                            nextX -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        if (posY < 320) {
                            nextY += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA_2;
                        }
                    }
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2) {
                        nextX += distancia;
                    }
                }
            }
        }

        return new MovimientoInfo(nextX, nextY, distancia);
    }

    /**
     * Verifica si el vehículo puede realizar el movimiento propuesto.
     */
    protected boolean puedeRealizarMovimiento(MovimientoInfo movimiento) {
        if (faseMovimiento == FaseMovimiento.AVANZANDO && cruzariaLineaDeParada(movimiento.nextX(), movimiento.nextY())) {
            return simulationModel.esPrimerEnFila(id, movimiento.nextX(), movimiento.nextY());
        }
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
     * Maneja la interacción con intersecciones cuando el vehículo está cerca.
     */
    private void manejarIntersecciones() throws InterruptedException {
        // verificar si está cerca de una intersección
        if (simulationModel.estaCercaDeInterseccion(id, posX, posY)) {
            // solicitar cruzar la intersección
            simulationModel.solicitarCruceInterseccion(id, tipo, posX, posY);
        }
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
    public PuntoSalida getPuntoSalida() {
        return puntoSalida;
    }


    /**
     * Record para encapsular información de movimiento.
     */
    protected record MovimientoInfo(double nextX, double nextY, double distancia) {}

    boolean cruzariaLineaDeParada(double nextX, double nextY) {
        double centroX = 400;
        double centroY = 295;
        double halfIntersection = 45; // half of 90px intersection width
        double buffer = 10; // adjust for vehicle size

        switch (puntoSalida) {
            case ARRIBA:
                return nextY >= (centroY - halfIntersection - buffer);
            case ABAJO:
                return nextY <= (centroY + halfIntersection + buffer);
            case IZQUIERDA:
                return nextX >= (centroX - halfIntersection - buffer);
            case DERECHA:
                return nextX <= (centroX + halfIntersection + buffer);
            default:
                return false;
        }
    }



}