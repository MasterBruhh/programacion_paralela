package edu.pucmm.simulation;

import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Implementación de ISimulationModel que integra el sistema de cruce
 * con detección de colisiones.
 */
public class CruceSimulationModel implements ISimulationModel {
    
    private static final Logger logger = Logger.getLogger(CruceSimulationModel.class.getName());
    
    private final SimulationModel simulationModel;
    private final CruceManager cruceManager;
    private final ColisionDetector colisionDetector;
    
    // distancia máxima (en unidades) para considerar que un vehículo está lo suficientemente
    // cerca de la intersección como para solicitar cruce. Se ajusta a 60 para dar un
    // margen amplio y asegurar que los vehículos que se aproximan desde las coordenadas
    // iniciales del demo sean detectados correctamente.
    private static final double DISTANCIA_PROXIMIDAD_INTERSECCION = 60.0;
    
    // cache de vehículos que ya han solicitado cruce para evitar solicitudes duplicadas
    private final ConcurrentHashMap<String, Long> solicitudesCrucePendientes = new ConcurrentHashMap<>();
    private static final long TIMEOUT_SOLICITUD_CRUCE = 5000; // 5 segundos
    
    public CruceSimulationModel(SimulationModel simulationModel, String cruceId) {
        this.simulationModel = simulationModel;
        this.cruceManager = new CruceManager(cruceId);
        this.colisionDetector = new ColisionDetector();
        
        logger.info("✅ CruceSimulationModel inicializado con cruce: " + cruceId);
    }
    
    @Override
    public void publishState(VehiculoState estado) {
        // delegar al modelo de simulación existente
        simulationModel.updateState(estado);
    }
    
    @Override
    public boolean puedeAvanzar(String vehiculoId, double nextX, double nextY) {
        // verificar colisiones usando el detector de colisiones
        return colisionDetector.puedeMoverse(vehiculoId, nextX, nextY, 
                                           simulationModel.getVehiculos().values());
    }
    
    @Override
    public void solicitarCruceInterseccion(String vehiculoId, TipoVehiculo tipo, 
                                          double vehiculoPosX, double vehiculoPosY) throws InterruptedException {
        
        // verificar si ya hay una solicitud pendiente para este vehículo
        Long ultimaSolicitud = solicitudesCrucePendientes.get(vehiculoId);
        long tiempoActual = System.currentTimeMillis();
        
        if (ultimaSolicitud != null && (tiempoActual - ultimaSolicitud) < TIMEOUT_SOLICITUD_CRUCE) {
            // solicitud reciente, ignorar para evitar spam
            return;
        }
        
        // registrar nueva solicitud
        solicitudesCrucePendientes.put(vehiculoId, tiempoActual);
        
        try {
            // determinar la intersección más cercana
            CruceManager.DireccionCruce direccionEntrada = 
                cruceManager.determinarInterseccionMasCercana(vehiculoPosX, vehiculoPosY);
            
            logger.info("🚦 vehículo " + vehiculoId + " (" + tipo + ") solicita cruzar desde " + 
                       direccionEntrada + " en posición (" + 
                       String.format("%.2f", vehiculoPosX) + ", " + 
                       String.format("%.2f", vehiculoPosY) + ")");
            
            // solicitar cruce al manager
            cruceManager.solicitarCruce(vehiculoId, tipo, direccionEntrada);
            
        } finally {
            // limpiar solicitud pendiente al completar
            solicitudesCrucePendientes.remove(vehiculoId);
        }
    }
    
    @Override
    public boolean estaCercaDeInterseccion(String vehiculoId, double posX, double posY) {
        // verificar proximidad a cualquiera de las 4 intersecciones
        for (CruceManager.DireccionCruce direccion : CruceManager.DireccionCruce.values()) {
            double distancia = colisionDetector.calcularDistancia(
                posX, posY, direccion.posX, direccion.posY
            );
            
            if (distancia <= DISTANCIA_PROXIMIDAD_INTERSECCION) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Obtiene el estado actual del cruce.
     */
    public CruceManager.EstadoCruce getEstadoCruce() {
        return cruceManager.getEstadoCruce();
    }
    
    /**
     * Genera un reporte de colisiones del sistema.
     */
    public ColisionDetector.ReporteColisiones generarReporteColisiones() {
        return colisionDetector.generarReporte(simulationModel.getVehiculos().values());
    }
    
    /**
     * Obtiene el número total de vehículos que han cruzado.
     */
    public int getTotalVehiculosCruzados() {
        return cruceManager.getTotalVehiculosProcessed();
    }
    
    /**
     * Verifica si hay vehículos esperando en alguna intersección.
     */
    public boolean hayVehiculosEsperando() {
        return cruceManager.tieneVehiculosEsperando();
    }
    
    /**
     * Obtiene la intersección menos congestionada.
     */
    public CruceManager.DireccionCruce getInterseccionMenosCongestionada() {
        return cruceManager.getInterseccionMenosCongestionada();
    }
    
    /**
     * Limpia solicitudes de cruce antiguas para evitar memory leaks.
     */
    public void limpiarSolicitudesAntiguas() {
        long tiempoActual = System.currentTimeMillis();
        
        solicitudesCrucePendientes.entrySet().removeIf(entry -> 
            (tiempoActual - entry.getValue()) > TIMEOUT_SOLICITUD_CRUCE * 2
        );
    }
    
    /**
     * Obtiene el modelo de simulación subyacente.
     */
    public SimulationModel getSimulationModel() {
        return simulationModel;
    }
    
    /**
     * Obtiene el manager del cruce.
     */
    public CruceManager getCruceManager() {
        return cruceManager;
    }
    
    /**
     * Obtiene el detector de colisiones.
     */
    public ColisionDetector getColisionDetector() {
        return colisionDetector;
    }
} 