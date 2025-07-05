package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Manager para una intersección individual con control FIFO y prioridad de emergencia.
 */
public class InterseccionManager {
    
    private static final Logger logger = Logger.getLogger(InterseccionManager.class.getName());
    
    private final String id;
    private final Semaphore cruce;
    private final PriorityBlockingQueue<VehiculoWaiting> colaEspera;
    private final AtomicInteger vehiculosProcessed = new AtomicInteger(0);
    private final Queue<String> vehiculosEnEspera = new ConcurrentLinkedQueue<>();
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

        // Add to vehiculosEnEspera if not already present
        if (!vehiculosEnEspera.contains(vehiculoId)) {
            vehiculosEnEspera.offer(vehiculoId);
        }

        try {
            cruce.acquire();
            VehiculoWaiting proximo = colaEspera.peek();
            while (proximo != null && !proximo.vehiculoId().equals(vehiculoId)) {
                cruce.release();
                Thread.sleep(50);
                cruce.acquire();
                proximo = colaEspera.peek();
            }
            colaEspera.remove(vehiculoWaiting);

            logger.info("✅ vehículo " + vehiculoId + " inicia cruce de intersección " + id);

            long tiempoCruce = (tipo == TipoVehiculo.emergencia) ?
                    TIEMPO_CRUCE_EMERGENCIA : TIEMPO_CRUCE_NORMAL;
            Thread.sleep(tiempoCruce);

            vehiculosProcessed.incrementAndGet();
            logger.info("🏁 vehículo " + vehiculoId + " completó cruce de intersección " + id +
                    " (total procesados: " + vehiculosProcessed.get() + ")");

        } finally {
            cruce.release();
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

    public boolean esPrimerEnFila(String vehiculoId) {
        return vehiculosEnEspera.peek() != null && vehiculosEnEspera.peek().equals(vehiculoId);
    }

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

    public void removerVehiculoEnEspera(String vehiculoId) {
        vehiculosEnEspera.remove(vehiculoId);
    }

} 