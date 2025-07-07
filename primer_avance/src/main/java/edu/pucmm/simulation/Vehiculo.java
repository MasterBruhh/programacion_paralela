package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;
import edu.pucmm.controller.SimulationController;

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
        ACERCANDOSE_A_COLA,
        EN_COLA,
        EN_PARE_OBLIGATORIO,  // NUEVA FASE: detenido en el stop sign
        CRUZANDO,
        GIRANDO,
        GIRA_DERECHA,
        GIRA_IZQUIERDA,
        AVANZA_DENTRO_CRUCE,
        GIRA_IZQUIERDA_2,
        DETENIDO
    }
    FaseMovimiento faseMovimiento = FaseMovimiento.AVANZANDO; // package-private para tests

    private boolean enCola = false;
    private CruceManager.DireccionCruce direccionCola = null;
    private boolean cruceOtorgado = false; // nuevo flag para trackear si tiene permiso de cruce
    private long tiempoCruceOtorgado = 0; // timestamp cuando se otorgó el permiso
    private boolean posicionColaEstabilizada = false; // flag para indicar si ya está en posición de cola estable
    
    // Variables para el PARE OBLIGATORIO
    private boolean enPareObligatorio = false;
    private long tiempoInicioParada = 0;
    private static final long TIEMPO_PARE_MINIMO = 3000; // 3 segundos en milisegundos

    protected final String id;
    protected final TipoVehiculo tipo;
    protected final long timestampCreacion; // Timestamp cuando se creó el vehículo
    protected volatile double posX;
    protected volatile double posY;
    protected volatile double velocidad;
    protected volatile Direccion direccion;
    protected volatile PuntoSalida puntoSalida;

    protected final AtomicBoolean running = new AtomicBoolean(false);
    protected final AtomicBoolean paused = new AtomicBoolean(false);
    protected final ISimulationModel simulationModel;

    protected Vehiculo(String id, TipoVehiculo tipo, double posX, double posY,
                       double velocidad, Direccion direccion, ISimulationModel simulationModel, 
                       PuntoSalida puntoSalida, long timestampCreacion) {
        this.id = id;
        this.tipo = tipo;
        this.timestampCreacion = timestampCreacion;
        this.posX = posX;
        this.posY = posY;
        this.velocidad = velocidad;
        this.direccion = direccion;
        this.simulationModel = simulationModel;
        this.puntoSalida = puntoSalida;
    }

    public boolean setRunning(boolean running) {
        return running;
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
        // 1. verificar si está cerca de intersección y manejar cruce/cola
        try {
            manejarInterseccionesYCola();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("vehículo " + id + " interrumpido durante manejo de intersecciones");
            return;
        }

        // 2. detectar vehículos adelante y ajustar velocidad (frenado preventivo)
        ajustarVelocidadPreventiva();

        // 3. calcular movimiento
        MovimientoInfo nextMovement;
        if (faseMovimiento == FaseMovimiento.EN_COLA) {
            // si está en cola, moverse hacia su posición de espera
            nextMovement = calcularMovimientoHaciaPosicionCola();
        } else if (faseMovimiento == FaseMovimiento.EN_PARE_OBLIGATORIO) {
            // PARE OBLIGATORIO: vehículo completamente detenido
            nextMovement = new MovimientoInfo(posX, posY, 0); // NO se mueve
        } else {
            // movimiento normal
            nextMovement = calcularMovimiento();
        }

        // 4. interactuar con entorno
        boolean puedeMover = puedeRealizarMovimiento(nextMovement);

        // Si el vehículo está en cola pero no puede moverse (otro vehículo por delante),
        // estabilizar su posición para evitar recalculos constantes que producen jitter.
        if (!puedeMover && faseMovimiento == FaseMovimiento.EN_COLA) {
            posicionColaEstabilizada = true;
            nextMovement = new MovimientoInfo(posX, posY, 0);
            puedeMover = true; // aplicar asignación exacta y publicar estado
        }
        
        // También estabilizar si ya está muy cerca de su posición de cola asignada
        if (faseMovimiento == FaseMovimiento.EN_COLA && nextMovement.distancia() > 0 && nextMovement.distancia() < 2.0) {
            posicionColaEstabilizada = true;
            nextMovement = new MovimientoInfo(nextMovement.nextX(), nextMovement.nextY(), 0);
        }

        if (puedeMover) {
            // 5. aplicar movimiento
            aplicarMovimiento(nextMovement);

            // 6. publicar estado
            publishState();
        }

        // 7. lógica específica del tipo de vehículo
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

        // Si está cruzando, usar la lógica normal de movimiento pero asegurar que inicie el giro
        if (faseMovimiento == FaseMovimiento.CRUZANDO) {
            // Resetear a la fase de giro correspondiente según la dirección
            if (direccion != Direccion.recto) {
                // Determinar si debe empezar a girar basándose en la posición actual
                switch (puntoSalida) {
                    case ABAJO -> {
                        if (direccion == Direccion.derecha && posY <= 320) {
                            faseMovimiento = FaseMovimiento.GIRA_DERECHA;
                        } else if (direccion == Direccion.izquierda && posY <= 270) {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        } else if (direccion == Direccion.vuelta_u && posY <= 320) {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    case ARRIBA -> {
                        if (direccion == Direccion.derecha && posY >= 270) {
                            faseMovimiento = FaseMovimiento.GIRA_DERECHA;
                        } else if (direccion == Direccion.izquierda && posY >= 320) {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        } else if (direccion == Direccion.vuelta_u && posY >= 270) {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    case IZQUIERDA -> {
                        if (direccion == Direccion.derecha && posX >= 375) {
                            faseMovimiento = FaseMovimiento.GIRA_DERECHA;
                        } else if (direccion == Direccion.izquierda && posX >= 420) {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        } else if (direccion == Direccion.vuelta_u && posX >= 375) {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                    case DERECHA -> {
                        if (direccion == Direccion.derecha && posX <= 420) {
                            faseMovimiento = FaseMovimiento.GIRA_DERECHA;
                        } else if (direccion == Direccion.izquierda && posX <= 375) {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        } else if (direccion == Direccion.vuelta_u && posX <= 420) {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA;
                        }
                    }
                }
            }
            // Si es recto, mantener CRUZANDO que funcionará como AVANZANDO
        }

        // Calcular movimiento basado en dirección de salida
        switch (puntoSalida) {
            case ABAJO -> {
                if (direccion == Direccion.recto) {
                    nextY -= distancia;
                } else if (direccion == Direccion.derecha) {
                    // Fase de giro solo cuando está en CRUZANDO o GIRA_DERECHA, de lo contrario sigue recto
                    if (faseMovimiento == FaseMovimiento.GIRA_DERECHA) {
                        nextX += distancia;
                    } else {
                        nextY -= distancia; // Sigue recto hasta llegar al stop
                    }
                } else if (direccion == Direccion.izquierda) {
                    // Fase de giro solo cuando está en CRUZANDO o GIRA_IZQUIERDA
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        nextX -= distancia;
                    } else {
                        nextY -= distancia; // Sigue recto hasta llegar al stop
                    }
                } else if (direccion == Direccion.vuelta_u) {
                    // Fase de giro solo cuando está en CRUZANDO o fases de giro
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        if (posX > 375) {
                            nextX -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA_2;
                        }
                    } else if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2) {
                        nextY += distancia;
                    } else {
                        nextY -= distancia; // Sigue recto hasta llegar al stop
                    }
                }
            }
            case ARRIBA -> {
                if (direccion == Direccion.recto) {
                    nextY += distancia;
                } else if (direccion == Direccion.derecha) {
                    // Fase de giro solo cuando está en CRUZANDO o GIRA_DERECHA
                    if (faseMovimiento == FaseMovimiento.GIRA_DERECHA) {
                        nextX -= distancia;
                    } else {
                        nextY += distancia; // Sigue recto hasta llegar al stop
                    }
                } else if (direccion == Direccion.izquierda) {
                    // Fase de giro solo cuando está en CRUZANDO o GIRA_IZQUIERDA
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        nextX += distancia;
                    } else {
                        nextY += distancia; // Sigue recto hasta llegar al stop
                    }
                } else if (direccion == Direccion.vuelta_u) {
                    // Fase de giro solo cuando está en CRUZANDO o fases de giro
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        if (posX < 420) {
                            nextX += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA_2;
                        }
                    } else if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2) {
                        nextY -= distancia;
                    } else {
                        nextY += distancia; // Sigue recto hasta llegar al stop
                    }
                }
            }
            case IZQUIERDA -> {
                if (direccion == Direccion.recto) {
                    nextX += distancia;
                } else if (direccion == Direccion.derecha) {
                    // Fase de giro solo cuando está en CRUZANDO o GIRA_DERECHA
                    if (faseMovimiento == FaseMovimiento.GIRA_DERECHA) {
                        nextY += distancia;
                    } else {
                        nextX += distancia; // Sigue recto hasta llegar al stop
                    }
                } else if (direccion == Direccion.izquierda) {
                    // Fase de giro solo cuando está en CRUZANDO o GIRA_IZQUIERDA
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        nextY -= distancia;
                    } else {
                        nextX += distancia; // Sigue recto hasta llegar al stop
                    }
                } else if (direccion == Direccion.vuelta_u) {
                    // Fase de giro solo cuando está en CRUZANDO o fases de giro
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        if (posY > 270) {
                            nextY -= distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA_2;
                        }
                    } else if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2) {
                        nextX -= distancia;
                    } else {
                        nextX += distancia; // Sigue recto hasta llegar al stop
                    }
                }
            }
            case DERECHA -> {
                if (direccion == Direccion.recto) {
                    nextX -= distancia;
                } else if (direccion == Direccion.derecha) {
                    // Fase de giro solo cuando está en CRUZANDO o GIRA_DERECHA
                    if (faseMovimiento == FaseMovimiento.GIRA_DERECHA) {
                        nextY -= distancia;
                    } else {
                        nextX -= distancia; // Sigue recto hasta llegar al stop
                    }
                } else if (direccion == Direccion.izquierda) {
                    // Fase de giro solo cuando está en CRUZANDO o GIRA_IZQUIERDA
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        nextY += distancia;
                    } else {
                        nextX -= distancia; // Sigue recto hasta llegar al stop
                    }
                } else if (direccion == Direccion.vuelta_u) {
                    // Fase de giro solo cuando está en CRUZANDO o fases de giro
                    if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA) {
                        if (posY < 320) {
                            nextY += distancia;
                        } else {
                            faseMovimiento = FaseMovimiento.GIRA_IZQUIERDA_2;
                        }
                    } else if (faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2) {
                        nextX += distancia;
                    } else {
                        nextX -= distancia; // Sigue recto hasta llegar al stop
                    }
                }
            }
        }

        return new MovimientoInfo(nextX, nextY, distancia);
    }

    /**
     * Calcula el movimiento hacia la posición asignada en la cola.
     */
    private MovimientoInfo calcularMovimientoHaciaPosicionCola() {
        // Si ya está en posición estable, verificar si la cola cambió recientemente
        if (posicionColaEstabilizada) {
            if (simulationModel instanceof CruceSimulationModel cruceModel && direccionCola != null) {
                // Si la cola cambió recientemente, permitir un reposicionamiento
                if (cruceModel.getCruceManager().hayCambioRecienteEnCola(direccionCola)) {
                    posicionColaEstabilizada = false; // Permitir un reposicionamiento
                    logger.fine("Permitiendo reposicionamiento de " + id + " por cambio reciente en cola");
                }
            }
            
            // Si sigue estabilizado, mantener posición actual
            if (posicionColaEstabilizada) {
                return new MovimientoInfo(posX, posY, 0);
            }
        }
        
        if (!(simulationModel instanceof CruceSimulationModel cruceModel) || direccionCola == null) {
            return new MovimientoInfo(posX, posY, 0);
        }
        
        double[] posicionObjetivo = cruceModel.getCruceManager()
            .obtenerPosicionEspera(id, direccionCola);
        
        if (posicionObjetivo == null) {
            return new MovimientoInfo(posX, posY, 0);
        }
        
        double targetX = posicionObjetivo[0];
        double targetY = posicionObjetivo[1];
        
        // calcular dirección hacia la posición objetivo
        double deltaX = targetX - posX;
        double deltaY = targetY - posY;
        double distancia = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        
        // Aumentar el umbral a 5.0 para reducir micro-ajustes y almacenar posición final
        if (distancia < 5.0) {
            // Ya está suficientemente cerca - asignar EXACTAMENTE la posición objetivo
            // y marcar como estabilizado para evitar futuras recalculaciones
            posicionColaEstabilizada = true;
            return new MovimientoInfo(targetX, targetY, 0);
        }
        
        // normalizar y aplicar velocidad reducida para posicionamiento preciso
        double velocidadPosicionamiento = Math.min(velocidad * 0.3, 5.0);
        double deltaTime = TICK_INTERVAL_MS / 1000.0;
        double movimiento = velocidadPosicionamiento * deltaTime;
        
        double nextX = posX + (deltaX / distancia) * movimiento;
        double nextY = posY + (deltaY / distancia) * movimiento;
        
        return new MovimientoInfo(nextX, nextY, movimiento);
    }
    
    /**
     * Aplica el movimiento calculado.
     */
    protected void aplicarMovimiento(MovimientoInfo movimiento) {
        // Si está en cola y la distancia es cero, asignar posición exacta
        if ((faseMovimiento == FaseMovimiento.EN_COLA || faseMovimiento == FaseMovimiento.ACERCANDOSE_A_COLA) 
                && movimiento.distancia() == 0) {
            // Asignación precisa para eliminar el jitter por completo
            this.posX = movimiento.nextX();
            this.posY = movimiento.nextY();
            return;
        }
        
        // Comportamiento normal para otros casos
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
     * Maneja la interacción con intersecciones y colas.
     */
    private void manejarInterseccionesYCola() throws InterruptedException {
        if (simulationModel instanceof CruceSimulationModel cruceModel) {
            // determinar intersección basada en el punto de salida del vehículo
            // para evitar que tome una dirección incorrecta al estar lejos del cruce
            CruceManager.DireccionCruce direccionCercana = switch (puntoSalida) {
                case ARRIBA -> CruceManager.DireccionCruce.NORTE;
                case ABAJO -> CruceManager.DireccionCruce.SUR;
                case IZQUIERDA -> CruceManager.DireccionCruce.OESTE;
                case DERECHA -> CruceManager.DireccionCruce.ESTE;
            };
            
            // verificar si está cerca de una intersección
            if (simulationModel.estaCercaDeInterseccion(id, posX, posY)) {
                
                // IMPORTANTE: Solo unirse a una cola si no está ya en proceso de cruce o giro
                // Evitar que se una a otra cola durante giros (vuelta en U, etc.)
                if (!enCola && !cruceOtorgado && !estaEnProcesoDeGiro()) {
                    if (tipo == TipoVehiculo.emergencia) {
                        // Vehículo de emergencia: prioridad absoluta
                        cruceModel.getCruceManager().agregarVehiculoEmergenciaACola(id, timestampCreacion, direccionCercana);
                        logger.warning("🚨 EMERGENCIA: " + id + " con prioridad absoluta en " + direccionCercana);
                    } else {
                        // Vehículo normal: orden por creación
                        cruceModel.getCruceManager().agregarVehiculoACola(id, timestampCreacion, direccionCercana);
                    }
                    
                    enCola = true;
                    direccionCola = direccionCercana;
                    faseMovimiento = FaseMovimiento.EN_COLA;
                    posicionColaEstabilizada = false; // Permitir posicionamiento inicial en la cola
                    logger.info("vehículo " + id + " (creado en t=" + timestampCreacion + ") se unió a la cola de " + direccionCercana);
                }
                
                // si está en cola y es el primero, puede solicitar cruce
                if (enCola && !cruceOtorgado && cruceModel.getCruceManager().puedesolicitarCruce(id, direccionCola)) {
                    // al convertirnos en el primero de la cola, permitir que el vehículo avance
                    posicionColaEstabilizada = false;
                    // verificar si llegó a la línea de parada
                    if (estaEnLineaDeParada()) {
                        
                        // PARE OBLIGATORIO: El vehículo DEBE detenerse completamente
                        if (!enPareObligatorio) {
                            // Iniciar el PARE OBLIGATORIO
                            logger.info("🛑 vehículo " + id + " llegó al STOP - iniciando PARE OBLIGATORIO");
                            enPareObligatorio = true;
                            tiempoInicioParada = System.currentTimeMillis();
                            faseMovimiento = FaseMovimiento.EN_PARE_OBLIGATORIO;
                            return; // NO proceder hasta completar el tiempo
                        }
                        
                        // OBLIGAR a todos los vehículos a esperar al menos 3 segundos en el STOP
                        long tiempoParada = System.currentTimeMillis() - tiempoInicioParada;
                        if (tiempoParada < TIEMPO_PARE_MINIMO) {
                            // Aún no ha completado el tiempo mínimo de parada
                            logger.fine("⏸️ vehículo " + id + " en PARE OBLIGATORIO - tiempo: " + 
                                      tiempoParada + "ms / " + TIEMPO_PARE_MINIMO + "ms");
                            faseMovimiento = FaseMovimiento.EN_PARE_OBLIGATORIO;
                            return; // NO permitir avance
                        }
                        
                        // Solo después del tiempo mínimo, verificar si puede proceder
                        logger.info("✓ vehículo " + id + " completó tiempo de PARE OBLIGATORIO");
                        
                        // Verificar si es el siguiente según orden global
                        boolean esElSiguiente = cruceModel.getCruceManager().getCoordinadorGlobal().puedeProcedeSegunOrdenGlobal(id);
                        if (!esElSiguiente) {
                            // No es su turno según orden global, debe seguir esperando
                            logger.info("⏸️ vehículo " + id + " está en PARE pero debe esperar su turno global");
                            
                            // IMPORTANTE: Verificar si el cruce está actualmente ocupado
                            if (cruceModel.getCruceManager().getCoordinadorGlobal().estaCruceOcupado()) {
                                logger.info("⏸️ vehículo " + id + " espera porque el cruce está ocupado por otro vehículo");
                            } else {
                                // El cruce no está ocupado, verificar quién es el siguiente
                                var siguiente = cruceModel.getCruceManager().getCoordinadorGlobal().getSiguienteEnOrden();
                                if (siguiente != null) {
                                    logger.info("⏸️ vehículo " + id + " espera, el siguiente en orden es: " + siguiente.vehiculoId());
                                }
                            }
                            
                            // Intentar nuevamente en el próximo tick
                            faseMovimiento = FaseMovimiento.EN_PARE_OBLIGATORIO;
                            return;
                        }
                        
                        // Verificar si hay vehículos de emergencia que tienen prioridad
                        if (tipo != TipoVehiculo.emergencia && hayVehiculoEmergenciaAcercandose()) {
                            logger.info("🚨 vehículo " + id + " cede paso a vehículo de EMERGENCIA");
                            faseMovimiento = FaseMovimiento.EN_PARE_OBLIGATORIO;
                            return;
                        }
                        
                        // Verificar otras condiciones para avanzar
                        if (puedeProcedeRestpestandoTodasLasLineas()) {
                            logger.info("✅ vehículo " + id + " puede proceder después del pare, solicitando cruce");
                            
                            try {
                                simulationModel.solicitarCruceInterseccion(id, tipo, posX, posY);
                                
                                // Cambiar a estado de cruce - permite movimiento normal
                                faseMovimiento = FaseMovimiento.CRUZANDO;
                                enCola = false;
                                enPareObligatorio = false;
                                cruceOtorgado = true;
                                tiempoCruceOtorgado = System.currentTimeMillis();
                                direccionCola = null;
                                posicionColaEstabilizada = false; // Reset estabilización al salir de la cola
                                logger.info("🚶 vehículo " + id + " inicia cruce de intersección");
                            } catch (InterruptedException e) {
                                // Si hay interrupción durante la solicitud, mantener en pare
                                logger.warning("⚠️ vehículo " + id + " interrumpido durante solicitud de cruce");
                                faseMovimiento = FaseMovimiento.EN_PARE_OBLIGATORIO;
                                Thread.currentThread().interrupt();
                            }
                        } else {
                            logger.info("⏸️ vehículo " + id + " esperando después del pare obligatorio - condiciones no seguras");
                            // Mantener en el pare hasta que sea seguro proceder
                            faseMovimiento = FaseMovimiento.EN_PARE_OBLIGATORIO;
                        }
                    } else {
                        // Aún no ha llegado al pare, continuar acercándose
                        faseMovimiento = FaseMovimiento.ACERCANDOSE_A_COLA;
                    }
                }
            }
            
            // verificar si debe liberar el cruce (ya no está en el área del cruce)
            if (cruceOtorgado && !simulationModel.estaCercaDeInterseccion(id, posX, posY)) {
                // verificar que haya pasado suficiente tiempo para asegurar que cruzó completamente
                long tiempoEnCruce = System.currentTimeMillis() - tiempoCruceOtorgado;
                if (tiempoEnCruce > 500) { // mínimo 500ms para cruzar
                    cruceModel.liberarCruceInterseccion(id, posX, posY);
                    cruceOtorgado = false;
                    faseMovimiento = FaseMovimiento.AVANZANDO; // volver a movimiento normal
                    logger.info("🏁 vehículo " + id + " terminó de cruzar, liberando intersección");


                    // Eliminar el vehículo del modelo y detener su hilo
                    if (simulationModel instanceof CruceSimulationModel cruceSim) {
                        cruceSim.eliminarVehiculo(id);
                    } else {
                        simulationModel.eliminarVehiculo(id);
                    }
                    SimulationController.getVehiculosActivos().remove(this);
                    running.set(false);
                    return;
                }
            }
        }
    }
    
    /**
     * Verifica si el vehículo puede proceder después del pare obligatorio.
     * Respeta todas las condiciones de seguridad y prioridad.
     */
    private boolean puedeProcedeRestpestandoTodasLasLineas() {
        if (!(simulationModel instanceof CruceSimulationModel cruceModel)) {
            return true;
        }
        
        // 1. Verificar que es el primero en el orden global
        if (!cruceModel.getCruceManager().getCoordinadorGlobal().puedeProcedeSegunOrdenGlobal(id)) {
            logger.fine("vehículo " + id + " no puede proceder - no es el siguiente según orden global");
            return false;
        }
        
        // 2. Verificar que no hay otro vehículo cruzando
        if (cruceModel.getCruceManager().getCoordinadorGlobal().estaCruceOcupado()) {
            String vehiculoCruzando = cruceModel.getCruceManager().getCoordinadorGlobal().getVehiculoCruzandoActualmente();
            if (vehiculoCruzando != null && !vehiculoCruzando.equals(id)) {
                logger.fine("vehículo " + id + " no puede proceder - cruce ocupado por " + vehiculoCruzando);
                return false;
            }
        }
        
        // 3. Calcular próxima posición de movimiento
        MovimientoInfo proximoMovimiento = calcularMovimiento();
        
        // 4. Verificar que no haya colisiones en la próxima posición
        if (!cruceModel.getColisionDetector().puedeMoverse(id, proximoMovimiento.nextX(), proximoMovimiento.nextY(),
                cruceModel.getSimulationModel().getVehiculos().values())) {
            logger.fine("vehículo " + id + " no puede proceder - riesgo de colisión");
            return false;
        }
        
        // 5. Verificar que no haya vehículos en la dirección de movimiento dentro del cruce
        if (!verificarCruceDespejado(proximoMovimiento)) {
            logger.fine("vehículo " + id + " no puede proceder - cruce no despejado");
            return false;
        }
        
        // 6. Para vehículos normales, verificar si hay emergencias en el sistema
        if (tipo == TipoVehiculo.normal) {
            // Verificar si hay emergencia en el cruce
            if (hayVehiculoEmergenciaEnElCruce()) {
                logger.fine("vehículo " + id + " esperando - vehículo de emergencia en el cruce");
                return false;
            }
            
            // Verificar si hay emergencia acercándose desde cualquier dirección
            if (hayVehiculoEmergenciaAcercandose()) {
                logger.warning("🚨 vehículo " + id + " esperando - vehículo de emergencia acercándose");
                return false;
            }
        }
        
        logger.fine("vehículo " + id + " todas las condiciones verificadas - puede proceder");
        return true;
    }
    
    /**
     * Verifica que el área del cruce esté despejada en la dirección de movimiento.
     */
    private boolean verificarCruceDespejado(MovimientoInfo proximoMovimiento) {
        if (!(simulationModel instanceof CruceSimulationModel cruceModel)) {
            return true;
        }
        
        // Definir área del cruce
        double cruceMinX = 355;  // límite izquierdo del cruce
        double cruceMaxX = 445;  // límite derecho del cruce
        double cruceMinY = 250;  // límite superior del cruce
        double cruceMaxY = 340;  // límite inferior del cruce
        
        // Verificar si hay vehículos en el área de cruce que podrían colisionar
        for (VehiculoState otroVehiculo : cruceModel.getSimulationModel().getVehiculos().values()) {
            if (otroVehiculo.id().equals(id)) continue;
            
            // Si el otro vehículo está en el área del cruce
            if (otroVehiculo.posX() >= cruceMinX && otroVehiculo.posX() <= cruceMaxX &&
                otroVehiculo.posY() >= cruceMinY && otroVehiculo.posY() <= cruceMaxY) {
                
                // Calcular si podrían colisionar considerando el movimiento
                double distanciaAlOtro = cruceModel.getColisionDetector()
                    .calcularDistancia(proximoMovimiento.nextX(), proximoMovimiento.nextY(),
                                     otroVehiculo.posX(), otroVehiculo.posY());
                
                if (distanciaAlOtro < 30.0) { // zona de seguridad en el cruce
                    logger.fine("vehículo " + id + " detectó otro vehículo en el cruce: " + otroVehiculo.id());
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Verifica si hay un vehículo de emergencia actualmente en el cruce.
     */
    private boolean hayVehiculoEmergenciaEnElCruce() {
        if (!(simulationModel instanceof CruceSimulationModel cruceModel)) {
            return false;
        }
        
        double cruceMinX = 355;
        double cruceMaxX = 445;
        double cruceMinY = 250;
        double cruceMaxY = 340;
        
        return cruceModel.getSimulationModel().getVehiculos().values().stream()
            .anyMatch(vehiculo -> 
                vehiculo.tipo() == TipoVehiculo.emergencia &&
                vehiculo.posX() >= cruceMinX && vehiculo.posX() <= cruceMaxX &&
                vehiculo.posY() >= cruceMinY && vehiculo.posY() <= cruceMaxY
            );
    }
    
    /**
     * Verifica si hay un vehículo de emergencia acercándose desde cualquier dirección.
     * Esto incluye vehículos de emergencia en cualquier cola o aproximándose al cruce.
     */
    private boolean hayVehiculoEmergenciaAcercandose() {
        if (!(simulationModel instanceof CruceSimulationModel cruceModel)) {
            return false;
        }
        
        // Definir zona amplia de detección alrededor del cruce
        double zonaDeteccionMinX = 200;  // más amplio que el cruce
        double zonaDeteccionMaxX = 600;
        double zonaDeteccionMinY = 100;
        double zonaDeteccionMaxY = 500;
        
        // Buscar vehículos de emergencia en la zona amplia o en las colas
        boolean hayEmergencia = cruceModel.getSimulationModel().getVehiculos().values().stream()
            .anyMatch(vehiculo -> {
                if (vehiculo.tipo() != TipoVehiculo.emergencia) {
                    return false;
                }
                
                // Si la emergencia es este mismo vehículo, ignorar
                if (vehiculo.id().equals(id)) {
                    return false;
                }
                
                // Verificar si está en la zona de detección
                boolean enZonaDeteccion = vehiculo.posX() >= zonaDeteccionMinX && 
                                         vehiculo.posX() <= zonaDeteccionMaxX &&
                                         vehiculo.posY() >= zonaDeteccionMinY && 
                                         vehiculo.posY() <= zonaDeteccionMaxY;
                
                if (enZonaDeteccion) {
                    logger.info("🚨 Detectado vehículo de emergencia " + vehiculo.id() + 
                               " en zona de aproximación: (" + 
                               String.format("%.1f", vehiculo.posX()) + ", " + 
                               String.format("%.1f", vehiculo.posY()) + ")");
                    return true;
                }
                
                return false;
            });
        
        // También verificar si hay emergencias registradas en el coordinador global
        if (!hayEmergencia && cruceModel.getCruceManager() != null) {
            var siguiente = cruceModel.getCruceManager().getCoordinadorGlobal().getSiguienteEnOrden();
            if (siguiente != null && siguiente.tipo() == TipoVehiculo.emergencia) {
                logger.info("🚨 Vehículo de emergencia " + siguiente.vehiculoId() + 
                           " registrado como siguiente en orden global");
                hayEmergencia = true;
            }
        }
        
        return hayEmergencia;
    }

    /**
     * Verifica si el vehículo puede realizar el movimiento propuesto.
     */
    protected boolean puedeRealizarMovimiento(MovimientoInfo movimiento) {
        // Si está en cola o acercándose a la cola, permitir movimiento hacia la posición asignada
        if (faseMovimiento == FaseMovimiento.EN_COLA || faseMovimiento == FaseMovimiento.ACERCANDOSE_A_COLA) {
            return simulationModel.puedeAvanzar(id, movimiento.nextX(), movimiento.nextY());
        }
        
        // Si está en PARE OBLIGATORIO, SIEMPRE permitir (no se mueve, distancia = 0)
        if (faseMovimiento == FaseMovimiento.EN_PARE_OBLIGATORIO) {
            return true; // El vehículo está detenido, no hay movimiento que verificar
        }
        
        // Si está cruzando con permiso, permitir el movimiento (ya verificado previamente)
        if (faseMovimiento == FaseMovimiento.CRUZANDO && cruceOtorgado) {
            // Solo verificar colisiones básicas, no restricciones de intersección
            return simulationModel.puedeAvanzar(id, movimiento.nextX(), movimiento.nextY());
        }
        
        // Si está en cualquiera de las fases de giro, permitir movimiento
        if (faseMovimiento == FaseMovimiento.GIRA_DERECHA || 
            faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA ||
            faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2 ||
            faseMovimiento == FaseMovimiento.AVANZA_DENTRO_CRUCE) {
            return simulationModel.puedeAvanzar(id, movimiento.nextX(), movimiento.nextY());
        }
        
        // Para otros casos, usar la verificación estándar
        return simulationModel.puedeAvanzar(id, movimiento.nextX(), movimiento.nextY());
    }

    /**
     * Verifica si el vehículo está en la línea de parada.
     */
    private boolean estaEnLineaDeParada() {
        if (direccionCola == null) return false;
        double tolerancia = 15.0; // Tolerancia para detección de la línea de parada
        
        // Determinar la dirección adecuada según punto de salida
        CruceManager.DireccionCruce direccionStop;
        
        // Mapeo de PuntoSalida a DireccionCruce correspondiente para el stop
        switch (puntoSalida) {
            case ARRIBA:
                direccionStop = CruceManager.DireccionCruce.NORTE;
                return Math.abs(posY - direccionStop.posY) < tolerancia;
                
            case ABAJO:
                direccionStop = CruceManager.DireccionCruce.SUR;
                return Math.abs(posY - direccionStop.posY) < tolerancia;
                
            case IZQUIERDA:
                direccionStop = CruceManager.DireccionCruce.OESTE;
                return Math.abs(posX - direccionStop.posX) < tolerancia;
                
            case DERECHA:
                direccionStop = CruceManager.DireccionCruce.ESTE;
                return Math.abs(posX - direccionStop.posX) < tolerancia;
                
            default:
                return false;
        }
    }

    /**
     * Verifica si el vehículo está en proceso de giro o cruce.
     * Evita que se una a otra cola durante maniobras.
     */
    private boolean estaEnProcesoDeGiro() {
        return faseMovimiento == FaseMovimiento.EN_PARE_OBLIGATORIO ||
               faseMovimiento == FaseMovimiento.CRUZANDO ||
               faseMovimiento == FaseMovimiento.GIRA_DERECHA ||
               faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA ||
               faseMovimiento == FaseMovimiento.GIRA_IZQUIERDA_2 ||
               faseMovimiento == FaseMovimiento.AVANZA_DENTRO_CRUCE ||
               faseMovimiento == FaseMovimiento.GIRANDO;
    }

    /**
     * Ajusta la velocidad preventivamente si detecta un vehículo adelante.
     */
    private void ajustarVelocidadPreventiva() {
        if (simulationModel instanceof CruceSimulationModel cruceModel) {
            // calcular dirección de movimiento
            double dirX = 0, dirY = 0;
            switch (puntoSalida) {
                case ARRIBA -> dirY = 1;  // moviéndose hacia abajo
                case ABAJO -> dirY = -1;  // moviéndose hacia arriba
                case IZQUIERDA -> dirX = 1;  // moviéndose hacia la derecha
                case DERECHA -> dirX = -1;  // moviéndose hacia la izquierda
            }
            
            // detectar vehículo adelante
            var vehiculoAdelante = cruceModel.getColisionDetector()
                .detectarVehiculoAdelante(id, posX, posY, dirX, dirY, 
                                        cruceModel.getSimulationModel().getVehiculos().values());
            
            if (vehiculoAdelante.isPresent()) {
                double distancia = cruceModel.getColisionDetector()
                    .calcularDistancia(posX, posY, 
                                     vehiculoAdelante.get().posX(), 
                                     vehiculoAdelante.get().posY());
                
                // Distancia de seguridad deseada (píxeles antes del vehículo adelante)
                double DISTANCIA_SEGURIDAD_DESEADA = 25.0; // 25 píxeles de separación
                double DISTANCIA_FRENADO_GRADUAL = 40.0;   // distancia para empezar a frenar gradualmente
                
                // ajustar velocidad basada en la distancia para mantener separación
                if (distancia <= DISTANCIA_SEGURIDAD_DESEADA) {
                    // muy cerca - detenerse completamente para mantener distancia
                    this.velocidad = 0.0;
                    logger.fine("vehículo " + id + " detenido para mantener distancia de seguridad (" + 
                              String.format("%.1f", distancia) + " píxeles)");
                } else if (distancia < DISTANCIA_SEGURIDAD_DESEADA + 10.0) {
                    // cerca de la distancia ideal - velocidad muy baja para ajuste fino
                    this.velocidad = Math.min(this.velocidad, 3.0);
                    logger.fine("vehículo " + id + " velocidad muy reducida para ajuste de distancia");
                } else if (distancia < DISTANCIA_FRENADO_GRADUAL) {
                    // en rango de frenado gradual - reducir velocidad proporcionalmente
                    double factorReduccion = (distancia - DISTANCIA_SEGURIDAD_DESEADA) / 
                                            (DISTANCIA_FRENADO_GRADUAL - DISTANCIA_SEGURIDAD_DESEADA);
                    this.velocidad = Math.min(this.velocidad, getVelocidadNormal() * factorReduccion);
                    logger.fine("vehículo " + id + " frenado gradual a " + 
                              String.format("%.1f", velocidad) + " (distancia: " + 
                              String.format("%.1f", distancia) + ")");
                } else {
                    // distancia segura - restaurar velocidad normal gradualmente
                    if (this.velocidad < getVelocidadNormal()) {
                        this.velocidad = Math.min(this.velocidad + 2.0, getVelocidadNormal());
                    }
                }
            } else {
                // camino libre - restaurar velocidad normal gradualmente
                if (this.velocidad < getVelocidadNormal()) {
                    this.velocidad = Math.min(this.velocidad + 2.0, getVelocidadNormal());
                }
            }
        }
    }
    
    /**
     * Obtiene la velocidad normal del vehículo según su tipo.
     */
    private double getVelocidadNormal() {
        return 30.0; // velocidad unificada para todos los vehículos
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
    public long getTimestampCreacion() { return timestampCreacion; }
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