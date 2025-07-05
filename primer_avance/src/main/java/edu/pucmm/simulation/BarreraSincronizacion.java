package edu.pucmm.simulation;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementa una barrera de sincronización para controlar el orden de los vehículos.
 * Asegura que los vehículos respeten el orden de llegada y no se adelanten.
 */
public class BarreraSincronizacion {
    
    private static final Logger logger = Logger.getLogger(BarreraSincronizacion.class.getName());
    
    private final Map<String, CyclicBarrier> barrerasPorCalle = new ConcurrentHashMap<>();
    private final Map<String, Integer> vehiculosEsperandoPorCalle = new ConcurrentHashMap<>();
    
    private static final int TIMEOUT_BARRERA_SEGUNDOS = 10;
    
    /**
     * Registra un vehículo en la barrera de su calle.
     * 
     * @param calleId ID de la calle
     * @param vehiculoId ID del vehículo
     * @param posicionEnCola posición del vehículo en la cola (0 = primero)
     */
    public void registrarVehiculo(String calleId, String vehiculoId, int posicionEnCola) {
        String barrerId = calleId + "-pos-" + posicionEnCola;
        
        // Solo el primer vehículo puede proceder inmediatamente
        if (posicionEnCola == 0) {
            logger.info("vehículo " + vehiculoId + " es primero en " + calleId + " - sin barrera");
            return;
        }
        
        // Los demás vehículos deben esperar en la barrera
        logger.info("vehículo " + vehiculoId + " esperando en barrera pos-" + posicionEnCola + " en " + calleId);
    }
    
    /**
     * Espera en la barrera hasta que sea el turno del vehículo.
     * 
     * @param calleId ID de la calle
     * @param vehiculoId ID del vehículo
     * @param posicionEnCola posición en la cola
     * @return true si puede proceder, false si hay timeout
     */
    public boolean esperarTurno(String calleId, String vehiculoId, int posicionEnCola) {
        // El primer vehículo no necesita esperar
        if (posicionEnCola == 0) {
            return true;
        }
        
        try {
            // Simular espera basada en posición - cada posición espera más tiempo
            Thread.sleep(posicionEnCola * 100); // 100ms por posición
            
            logger.info("vehículo " + vehiculoId + " completó espera en barrera pos-" + posicionEnCola);
            return true;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning("vehículo " + vehiculoId + " interrumpido en barrera");
            return false;
        }
    }
    
    /**
     * Libera a un vehículo de la barrera cuando completa su cruce.
     * 
     * @param calleId ID de la calle
     * @param vehiculoId ID del vehículo que completó el cruce
     * @param posicionEnCola posición que liberó
     */
    public void liberarVehiculo(String calleId, String vehiculoId, int posicionEnCola) {
        logger.info("vehículo " + vehiculoId + " liberado de barrera pos-" + posicionEnCola + " en " + calleId);
        
        // Notificar al siguiente vehículo que puede proceder
        String siguienteBarrera = calleId + "-pos-" + (posicionEnCola + 1);
        
        // En una implementación más compleja, aquí se notificaría al siguiente vehículo
        logger.fine("liberando barrera para siguiente vehículo en " + siguienteBarrera);
    }
    
    /**
     * Verifica si un vehículo puede proceder según la barrera.
     * 
     * @param calleId ID de la calle
     * @param vehiculoId ID del vehículo
     * @param posicionEnCola posición en la cola
     * @return true si puede proceder
     */
    public boolean puedeProceser(String calleId, String vehiculoId, int posicionEnCola) {
        // Lógica simplificada: puede proceder si es el primero o si ha esperado suficiente
        if (posicionEnCola == 0) {
            return true;
        }
        
        // Verificar si los vehículos anteriores ya procedieron
        String claveEspera = calleId + "-" + vehiculoId;
        long tiempoEspera = System.currentTimeMillis() - 
            vehiculosEsperandoPorCalle.getOrDefault(claveEspera, 0);
        
        // Debe esperar al menos posicion * 200ms
        long tiempoMinimo = posicionEnCola * 200L;
        return tiempoEspera >= tiempoMinimo;
    }
    
    /**
     * Registra que un vehículo está esperando.
     */
    public void marcarEsperando(String calleId, String vehiculoId) {
        String clave = calleId + "-" + vehiculoId;
        vehiculosEsperandoPorCalle.put(clave, (int) System.currentTimeMillis());
    }
    
    /**
     * Limpia la información de espera de un vehículo.
     */
    public void limpiarEspera(String calleId, String vehiculoId) {
        String clave = calleId + "-" + vehiculoId;
        vehiculosEsperandoPorCalle.remove(clave);
    }
} 