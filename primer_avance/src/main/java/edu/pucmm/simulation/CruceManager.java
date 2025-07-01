package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Manager para el cruce completo de calles (Escenario 1).
 * Agrupa 4 intersecciones individuales para formar un cruce en cruz.
 * 
 * Distribución de intersecciones:
 *     Norte
 *       |
 * Oeste-+-Este
 *       |
 *     Sur
 */
public class CruceManager {
    
    private static final Logger logger = Logger.getLogger(CruceManager.class.getName());
    
    private final String id;
    private final Map<DireccionCruce, InterseccionManager> intersecciones;
    private final ColisionDetector colisionDetector;
    
    /**
     * Direcciones de entrada al cruce.
     */
    public enum DireccionCruce {
        NORTE(0, -50),   // entrada desde arriba
        SUR(0, 50),      // entrada desde abajo  
        ESTE(50, 0),     // entrada desde derecha
        OESTE(-50, 0);   // entrada desde izquierda
        
        public final double posX, posY;
        
        DireccionCruce(double posX, double posY) {
            this.posX = posX;
            this.posY = posY;
        }
    }
    
    public CruceManager(String id) {
        this.id = id;
        this.intersecciones = new ConcurrentHashMap<>();
        this.colisionDetector = new ColisionDetector();
        
        // crear las 4 intersecciones del cruce
        inicializarIntersecciones();
        
        logger.info("✅ CruceManager " + id + " inicializado con 4 intersecciones");
    }
    
    /**
     * Inicializa las 4 intersecciones del cruce.
     */
    private void inicializarIntersecciones() {
        for (DireccionCruce direccion : DireccionCruce.values()) {
            String interseccionId = id + "-" + direccion.name().toLowerCase();
            InterseccionManager interseccion = new InterseccionManager(interseccionId);
            intersecciones.put(direccion, interseccion);
        }
    }
    
    /**
     * Solicita cruzar el cruce desde una dirección específica.
     * 
     * @param vehiculoId ID del vehículo
     * @param tipo tipo del vehículo  
     * @param direccionEntrada dirección desde la que entra el vehículo
     * @throws InterruptedException si el hilo es interrumpido
     */
    public void solicitarCruce(String vehiculoId, TipoVehiculo tipo, 
                              DireccionCruce direccionEntrada) throws InterruptedException {
        
        InterseccionManager interseccion = intersecciones.get(direccionEntrada);
        if (interseccion == null) {
            throw new IllegalArgumentException("dirección de entrada inválida: " + direccionEntrada);
        }
        
        logger.info("🚦 vehículo " + vehiculoId + " solicita cruzar desde " + direccionEntrada + 
                   " en cruce " + id);
        
        // delegar a la intersección específica
        interseccion.solicitarCruce(vehiculoId, tipo);
    }
    
    /**
     * Determina automáticamente la intersección más cercana para un vehículo.
     */
    public DireccionCruce determinarInterseccionMasCercana(double vehiculoPosX, double vehiculoPosY) {
        DireccionCruce masCercana = DireccionCruce.NORTE;
        double menorDistancia = Double.MAX_VALUE;
        
        for (DireccionCruce direccion : DireccionCruce.values()) {
            double distancia = Math.sqrt(
                Math.pow(vehiculoPosX - direccion.posX, 2) + 
                Math.pow(vehiculoPosY - direccion.posY, 2)
            );
            
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                masCercana = direccion;
            }
        }
        
        return masCercana;
    }
    
    /**
     * Verifica si alguna intersección tiene vehículos esperando.
     */
    public boolean tieneVehiculosEsperando() {
        return intersecciones.values().stream()
                .anyMatch(interseccion -> interseccion.getVehiculosEnEspera() > 0);
    }
    
    /**
     * Obtiene el total de vehículos procesados en todo el cruce.
     */
    public int getTotalVehiculosProcessed() {
        return intersecciones.values().stream()
                .mapToInt(InterseccionManager::getVehiculosProcessed)
                .sum();
    }
    
    /**
     * Obtiene información del estado del cruce completo.
     */
    public EstadoCruce getEstadoCruce() {
        Map<DireccionCruce, InterseccionManager.EstadoInterseccion> estadosIntersecciones = 
            new ConcurrentHashMap<>();
        
        for (Map.Entry<DireccionCruce, InterseccionManager> entry : intersecciones.entrySet()) {
            estadosIntersecciones.put(entry.getKey(), entry.getValue().getEstado());
        }
        
        return new EstadoCruce(
            id,
            estadosIntersecciones,
            getTotalVehiculosProcessed(),
            tieneVehiculosEsperando()
        );
    }
    
    /**
     * Obtiene la intersección menos congestionada.
     */
    public DireccionCruce getInterseccionMenosCongestionada() {
        return intersecciones.entrySet().stream()
                .min((e1, e2) -> Integer.compare(
                    e1.getValue().getVehiculosEnEspera(),
                    e2.getValue().getVehiculosEnEspera()
                ))
                .map(Map.Entry::getKey)
                .orElse(DireccionCruce.NORTE);
    }
    
    public String getId() {
        return id;
    }
    
    /**
     * Record para representar el estado completo del cruce.
     */
    public record EstadoCruce(
        String id,
        Map<DireccionCruce, InterseccionManager.EstadoInterseccion> intersecciones,
        int totalVehiculosProcessed,
        boolean tieneVehiculosEsperando
    ) {}
} 