package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.concurrent.TimeUnit;

/**
 * Coordinador global que asegura el orden de creación de vehículos en todo el cruce.
 * Garantiza que el primer vehículo creado tenga prioridad absoluta sobre cualquier
 * vehículo creado después, independientemente de la dirección de entrada.
 */
public class CruceCoordinador {
    
    private static final Logger logger = Logger.getLogger(CruceCoordinador.class.getName());
    
    private final String cruceId;
    private final Semaphore cruceSemaforo;
    private final PriorityBlockingQueue<VehiculoEnOrdenGlobal> colaGlobal;
    private final ConcurrentHashMap<String, VehiculoEnOrdenGlobal> vehiculosRegistrados;
    private final AtomicBoolean cruceOcupado = new AtomicBoolean(false);
    private String vehiculoActualmenteCruzando = null;
    
    public CruceCoordinador(String cruceId) {
        this.cruceId = cruceId;
        this.cruceSemaforo = new Semaphore(1, true); // Solo 1 vehículo puede cruzar a la vez, modo justo
        this.colaGlobal = new PriorityBlockingQueue<>(100, (v1, v2) -> {
            // 1. Vehículos de emergencia siempre tienen prioridad
            if (v1.tipo() == TipoVehiculo.emergencia && v2.tipo() != TipoVehiculo.emergencia) {
                return -1;
            }
            if (v1.tipo() != TipoVehiculo.emergencia && v2.tipo() == TipoVehiculo.emergencia) {
                return 1;
            }
            
            // 2. Entre vehículos del mismo tipo, orden estricto por timestamp de creación
            return Long.compare(v1.timestampCreacion(), v2.timestampCreacion());
        });
        this.vehiculosRegistrados = new ConcurrentHashMap<>();
        
        logger.info("✅ CruceCoordinador inicializado para cruce: " + cruceId);
    }
    
    /**
     * Registra un vehículo en el orden global del cruce.
     * 
     * @param vehiculoId ID del vehículo
     * @param timestampCreacion timestamp cuando se creó el vehículo
     * @param tipo tipo del vehículo
     * @param direccionEntrada dirección desde la que entra
     */
    public void registrarVehiculo(String vehiculoId, long timestampCreacion, 
                                 TipoVehiculo tipo, CruceManager.DireccionCruce direccionEntrada) {
        
        VehiculoEnOrdenGlobal vehiculo = new VehiculoEnOrdenGlobal(
            vehiculoId, timestampCreacion, tipo, direccionEntrada, System.currentTimeMillis()
        );
        
        vehiculosRegistrados.put(vehiculoId, vehiculo);
        colaGlobal.offer(vehiculo);
        
        logger.info("📝 Vehículo " + vehiculoId + " registrado en orden global " +
                   "(creado en t=" + timestampCreacion + ", tipo=" + tipo + ", desde=" + direccionEntrada + ")");
    }
    
    /**
     * Verifica si un vehículo puede proceder a cruzar según el orden global.
     * 
     * @param vehiculoId ID del vehículo que quiere cruzar
     * @return true si es su turno según el orden global
     */
    public boolean puedeProcedeSegunOrdenGlobal(String vehiculoId) {
        VehiculoEnOrdenGlobal siguienteEnOrden = colaGlobal.peek();
        
        if (siguienteEnOrden == null) {
            return false; // No hay nadie en la cola
        }
        
        boolean esSuTurno = siguienteEnOrden.vehiculoId().equals(vehiculoId);
        
        if (esSuTurno) {
            logger.info("✅ Vehículo " + vehiculoId + " es el siguiente en orden global");
        } else {
            logger.fine("⏸️ Vehículo " + vehiculoId + " debe esperar. Siguiente en orden: " + 
                       siguienteEnOrden.vehiculoId() + " (creado en t=" + siguienteEnOrden.timestampCreacion() + ")");
        }
        
        return esSuTurno;
    }
    
    /**
     * Solicita permiso para cruzar el cruce completo.
     * Solo el vehículo con el timestamp de creación más antiguo puede proceder.
     * 
     * @param vehiculoId ID del vehículo
     * @param tipo tipo del vehículo
     * @throws InterruptedException si el hilo es interrumpido
     */
    public void solicitarCruceGlobal(String vehiculoId, TipoVehiculo tipo) throws InterruptedException {
        logger.info("🚦 Vehículo " + vehiculoId + " solicita cruce en orden global");
        
        // Verificar si es su turno en el orden global
        if (!puedeProcedeSegunOrdenGlobal(vehiculoId)) {
            logger.info("⏸️ Vehículo " + vehiculoId + " debe esperar su turno en orden global");
            return; // No es su turno, debe esperar
        }
        
        try {
            // Adquirir el semáforo del cruce con un timeout para evitar bloqueos indefinidos
            boolean acquired = cruceSemaforo.tryAcquire(500, TimeUnit.MILLISECONDS);
            
            if (!acquired) {
                logger.info("⏳ Vehículo " + vehiculoId + " esperando por semáforo global");
                cruceSemaforo.acquire(); // Esperar indefinidamente si no se pudo adquirir en el timeout
            }
            
            // Verificar nuevamente que sigue siendo su turno (por si cambió mientras esperaba)
            if (!puedeProcedeSegunOrdenGlobal(vehiculoId)) {
                logger.warning("⚠️ Vehículo " + vehiculoId + " perdió su turno mientras esperaba");
                cruceSemaforo.release(); // Liberar el semáforo ya que no es su turno
                return;
            }
            
            // Remover de la cola global ya que va a cruzar
            VehiculoEnOrdenGlobal vehiculo = colaGlobal.poll();
            if (vehiculo != null && vehiculo.vehiculoId().equals(vehiculoId)) {
                cruceOcupado.set(true);
                vehiculoActualmenteCruzando = vehiculoId;
                
                logger.info("✅ Vehículo " + vehiculoId + " obtuvo permiso de cruce global " +
                           "(orden de creación respetado)");
            } else {
                logger.warning("⚠️ Error en orden global para vehículo " + vehiculoId);
                cruceSemaforo.release(); // Liberar el semáforo en caso de error
            }
            
        } catch (Exception e) {
            logger.warning("Error en solicitud de cruce global para " + vehiculoId + ": " + e.getMessage());
            cruceSemaforo.release(); // Liberar en caso de error
            throw e;
        }
        // NO liberar el semáforo aquí - se liberará cuando termine de cruzar
    }
    
    /**
     * Libera el permiso de cruce global cuando el vehículo termina de cruzar.
     * 
     * @param vehiculoId ID del vehículo que termina de cruzar
     */
    public void liberarCruceGlobal(String vehiculoId) {
        if (!vehiculoId.equals(vehiculoActualmenteCruzando)) {
            logger.warning("⚠️ Vehículo " + vehiculoId + " intenta liberar cruce pero no es el que está cruzando");
            return;
        }
        
        try {
            // Guardar info del siguiente antes de eliminar el actual
            VehiculoEnOrdenGlobal siguiente = colaGlobal.peek();
            
            // Eliminar el vehículo del registro
            vehiculosRegistrados.remove(vehiculoId);
            cruceOcupado.set(false);
            vehiculoActualmenteCruzando = null;
            
            // Liberar el semáforo para permitir que el siguiente proceda
            if (cruceSemaforo.availablePermits() == 0) {
                cruceSemaforo.release();
                logger.info("🔓 Semáforo global liberado - disponible para siguiente vehículo");
            }
            
            logger.info("🏁 Vehículo " + vehiculoId + " liberó cruce global");
            
            // Notificar al siguiente vehículo en la cola
            if (siguiente != null) {
                logger.info("📋 Siguiente en orden global: " + siguiente.vehiculoId() + 
                           " (creado en t=" + siguiente.timestampCreacion() + ")");
                
                // La notificación al siguiente ocurrirá en CruceManager.liberarCruce()
            } else {
                logger.info("📋 No hay más vehículos en espera");
            }
            
        } catch (Exception e) {
            logger.warning("Error al liberar cruce global para " + vehiculoId + ": " + e.getMessage());
        }
    }
    
    /**
     * Verifica si el cruce está actualmente ocupado.
     */
    public boolean estaCruceOcupado() {
        return cruceOcupado.get();
    }
    
    /**
     * Obtiene el ID del vehículo que está cruzando actualmente.
     */
    public String getVehiculoCruzandoActualmente() {
        return vehiculoActualmenteCruzando;
    }
    
    /**
     * Obtiene el número de vehículos esperando en la cola global.
     */
    public int getVehiculosEnColaGlobal() {
        return colaGlobal.size();
    }
    
    /**
     * Obtiene información del siguiente vehículo en orden.
     */
    public VehiculoEnOrdenGlobal getSiguienteEnOrden() {
        return colaGlobal.peek();
    }
    
    /**
     * Record para representar un vehículo en el orden global.
     */
    public record VehiculoEnOrdenGlobal(
        String vehiculoId,
        long timestampCreacion,
        TipoVehiculo tipo,
        CruceManager.DireccionCruce direccionEntrada,
        long timestampRegistro
    ) {}
} 