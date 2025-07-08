package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

/**
 * Manager para una intersección individual con control FIFO y prioridad de emergencia.
 */
public class InterseccionManager {
    
    private static final Logger logger = Logger.getLogger(InterseccionManager.class.getName());
    
    private final String id;
    private final Semaphore cruce;
    private final PriorityBlockingQueue<VehiculoWaiting> colaEspera;
    private final AtomicInteger vehiculosProcessed = new AtomicInteger(0);
    // LA SIGUIENTE COLA ES REDUNDANTE Y CAUSA BUGS. SERÁ ELIMINADA.
    // private final Queue<String> vehiculosEnEspera = new ConcurrentLinkedQueue<>();
    
    // tiempos de cruce en milisegundos
    private static final long TIEMPO_CRUCE_NORMAL = 800;
    private static final long TIEMPO_CRUCE_EMERGENCIA = 400;
    
    public InterseccionManager(String id) {
        this.id = id;
        this.cruce = new Semaphore(1, true); // solo 1 vehículo, modo justo (FIFO)
        this.colaEspera = new PriorityBlockingQueue<>(100, (v1, v2) -> {
            // vehículos de emergencia tienen prioridad absoluta
            if (v1.tipo() == TipoVehiculo.emergencia && v2.tipo() != TipoVehiculo.emergencia) {
                return -1;
            }
            if (v1.tipo() != TipoVehiculo.emergencia && v2.tipo() == TipoVehiculo.emergencia) {
                return 1;
            }
            // mismo tipo: orden de llegada (timestamp)
            return Long.compare(v1.timestampLlegada(), v2.timestampLlegada());
        });
    }

    /**
     * Solicita permiso para cruzar la intersección.
     * Implementa el patrón acquire/release con manejo de prioridades.
     * Ahora es no-bloqueante para permitir que el vehículo ejecute su movimiento.
     * 
     * @param vehiculoId ID del vehículo que solicita cruzar
     * @param tipo tipo del vehículo (normal o emergencia)
     * @throws InterruptedException si el hilo es interrumpido mientras espera
     */
    public void solicitarCruce(String vehiculoId, TipoVehiculo tipo) throws InterruptedException {
        long timestampLlegada = System.currentTimeMillis();

        logger.info("🚗 vehículo " + vehiculoId + " (" + tipo + ") solicita cruzar intersección " + id);

        VehiculoWaiting vehiculoWaiting = new VehiculoWaiting(vehiculoId, tipo, timestampLlegada);
        colaEspera.offer(vehiculoWaiting);

        try {
            // Intentar adquirir el semáforo con un timeout corto
            boolean acquired = cruce.tryAcquire(300, TimeUnit.MILLISECONDS);
            
            if (!acquired) {
                logger.info("⏳ vehículo " + vehiculoId + " esperando por semáforo en intersección " + id);
                
                // Verificar si hay otro vehículo cruzando actualmente
                if (cruce.availablePermits() == 0) {
                    logger.info("⏸️ vehículo " + vehiculoId + " esperando a que se libere intersección " + id);
                }
                
                // Intentar adquirir con un timeout más largo pero no indefinido
                acquired = cruce.tryAcquire(2000, TimeUnit.MILLISECONDS);
                
                if (!acquired) {
                    logger.warning("⚠️ Timeout esperando semáforo para " + vehiculoId + " en " + id);
                    colaEspera.remove(vehiculoWaiting); // Remover de la cola para evitar bloqueos
                    return; // No bloquear indefinidamente
                }
            }
            
            // Verificar que este vehículo es el que debe proceder según la cola
            VehiculoWaiting proximo = colaEspera.peek();
            int intentos = 0;
            final int MAX_INTENTOS = 3;
            
            while (proximo != null && !proximo.vehiculoId().equals(vehiculoId) && intentos < MAX_INTENTOS) {
                // No es su turno según la cola local, liberar y reintentar
                cruce.release();
                logger.info("⏸️ vehículo " + vehiculoId + " cede el paso a " + proximo.vehiculoId() + " en intersección " + id);
                Thread.sleep(50);
                
                // Intentar adquirir nuevamente con timeout
                acquired = cruce.tryAcquire(500, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    logger.warning("⚠️ No se pudo readquirir semáforo para " + vehiculoId);
                    colaEspera.remove(vehiculoWaiting);
                    return; // No bloquear indefinidamente
                }
                
                proximo = colaEspera.peek();
                intentos++;
            }
            
            // Si después de los intentos sigue sin ser su turno, puede ser un problema de sincronización
            // En ese caso, permitimos que proceda para evitar deadlocks
            if (intentos >= MAX_INTENTOS) {
                logger.warning("⚠️ Posible problema de sincronización para " + vehiculoId + " en intersección " + id);
            }
            
            // Remover de la cola de espera
            colaEspera.remove(vehiculoWaiting);

            logger.info("✅ vehículo " + vehiculoId + " obtuvo permiso para cruzar intersección " + id);
            
            vehiculosProcessed.incrementAndGet();
            
        } catch (Exception e) {
            logger.warning("Error en solicitud de cruce para " + vehiculoId + ": " + e.getMessage());
            // Asegurar que se remueve de la cola en caso de error
            colaEspera.remove(vehiculoWaiting);
            // Asegurar que se libera el semáforo si lo tenía
            if (cruce.availablePermits() == 0) {
                try {
                    cruce.release();
                } catch (Exception ex) {
                    // Ignorar errores al liberar
                }
            }
            throw e;
        }
        // NO liberar el semáforo aquí - se liberará cuando el vehículo termine el cruce
    }
    
    /**
     * Libera el permiso de cruce cuando el vehículo termina de cruzar.
     * 
     * @param vehiculoId ID del vehículo que termina de cruzar
     */
    public void liberarCruce(String vehiculoId) {
        try {
            // Verificar si el semáforo ya está liberado
            if (cruce.availablePermits() == 0) {
                cruce.release();
                logger.info("🔓 Semáforo liberado en intersección " + id + " por vehículo " + vehiculoId);
            } else {
                logger.info("ℹ️ Semáforo ya estaba liberado en intersección " + id);
            }
            
            logger.info("🏁 vehículo " + vehiculoId + " liberó intersección " + id + 
                       " (total procesados: " + vehiculosProcessed.get() + ")");
                       
            // Verificar si hay más vehículos esperando
            VehiculoWaiting siguiente = colaEspera.peek();
            if (siguiente != null) {
                logger.info("📢 Siguiente vehículo en cola local: " + siguiente.vehiculoId() + 
                           " (llegó en t=" + siguiente.timestampLlegada() + ")");
            }
        } catch (Exception e) {
            logger.warning("Error al liberar cruce para " + vehiculoId + ": " + e.getMessage());
        }
    }
    
    /**
     * Verifica si la intersección está disponible para cruzar.
     */
    public boolean estaDisponible() {
        return cruce.availablePermits() > 0;
    }
    
    /**
     * Obtiene el número de vehículos esperando en la cola.
     */
    public int getVehiculosEnEspera() {
        return colaEspera.size();
    }
    
    /**
     * Obtiene el total de vehículos que han cruzado esta intersección.
     */
    public int getVehiculosProcessed() {
        return vehiculosProcessed.get();
    }
    
    /**
     * Obtiene información de estado de la intersección.
     */
    public EstadoInterseccion getEstado() {
        return new EstadoInterseccion(
            id,
            estaDisponible(),
            getVehiculosEnEspera(),
            getVehiculosProcessed()
        );
    }
    
    public String getId() {
        return id;
    }

    /*
    public boolean esPrimerEnFila(String vehiculoId) {
        return vehiculosEnEspera.peek() != null && vehiculosEnEspera.peek().equals(vehiculoId);
    }
    */

    /**
     * Record para representar un vehículo esperando en la cola.
     */
    private record VehiculoWaiting(
        String vehiculoId,
        TipoVehiculo tipo,
        long timestampLlegada
    ) {}
    
    /**
     * Record para representar el estado de una intersección.
     */
    public record EstadoInterseccion(
        String id,
        boolean disponible,
        int vehiculosEnEspera,
        int vehiculosProcessed
    ) {}

    /*
    public void removerVehiculoEnEspera(String vehiculoId) {
        vehiculosEnEspera.remove(vehiculoId);
    }
    */

    /**
     * Notifica a todos los vehículos esperando que deben verificar su estado
     * ya que podría haber cambiado su orden o disponibilidad para cruzar.
     * SIEMPRE notifica, incluso cuando el cruce ya está libre.
     */
    public void notificarVehiculosEsperando() {
        logger.info("📢 Notificando vehículos en espera en intersección " + id);
        
        try {
            // Si el semáforo ya está disponible, adquirirlo primero para luego liberarlo
            boolean adquirido = false;
            
            if (cruce.availablePermits() > 0) {
                try {
                    // Adquirir el permiso brevemente
                    cruce.acquire();
                    adquirido = true;
                    logger.info("🔒 Intersección " + id + " bloqueada temporalmente para notificación");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            
            // Siempre liberar una vez (ya sea porque ya estaba bloqueado o porque lo acabamos de adquirir)
            cruce.release();
            logger.info("🔔 Semáforo liberado en intersección " + id + " - vehículos notificados");
            
            // Esperar un breve momento para permitir que los threads despierten
            Thread.sleep(50);
            
            // Si habíamos adquirido el permiso, volver a adquirirlo para mantener el estado anterior
            if (adquirido) {
                try {
                    cruce.acquire();
                    logger.info("🔐 Intersección " + id + " restaurada a su estado original");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            logger.warning("Error en notificación de vehículos: " + e.getMessage());
        }
    }

} 