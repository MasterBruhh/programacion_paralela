package edu.pucmm.simulation;

import edu.pucmm.controller.SimulationController;
import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CruceSimulationModel implements ISimulationModel {

    private static final Logger logger = Logger.getLogger(CruceSimulationModel.class.getName());

    private final SimulationModel simulationModel;
    private final CruceManager cruceManager;
    private final ColisionDetector colisionDetector;

    // Distancia utilizada para considerar que un vehículo se encuentra cerca de
    // alguna de las intersecciones.  Al actualizar las coordenadas de las
    // señales de stop se incrementa este valor para que la detección ocurra
    // cuando el vehículo se acerca a dichas señales.
    private static final double DISTANCIA_PROXIMIDAD_INTERSECCION = 110.0;
    private final ConcurrentHashMap<String, Long> solicitudesCrucePendientes = new ConcurrentHashMap<>();
    private static final long TIMEOUT_SOLICITUD_CRUCE = 5000; // 5 segundos

    // New: Map to track PuntoSalida for each vehicle
    private final ConcurrentHashMap<String, PuntoSalida> puntoSalidaPorVehiculo = new ConcurrentHashMap<>();

    public CruceSimulationModel(SimulationModel simulationModel, String cruceId) {
        this.simulationModel = simulationModel;
        this.cruceManager = new CruceManager(cruceId);
        this.colisionDetector = new ColisionDetector();

        logger.info("✅ CruceSimulationModel inicializado con cruce: " + cruceId);
    }

    @Override
    public void publishState(VehiculoState estado) {
        // Only register PuntoSalida if we don't already have it and the vehicle is still active
        if (!puntoSalidaPorVehiculo.containsKey(estado.id())) {
            // Only search for PuntoSalida if this vehicle ID is not marked for deletion
            boolean vehiculoEncontrado = false;
            for (Vehiculo v : SimulationController.getVehiculosActivos()) {
                if (v.getId().equals(estado.id()) && v.isRunning()) {
                    puntoSalidaPorVehiculo.put(estado.id(), v.getPuntoSalida());
                    vehiculoEncontrado = true;
                    break;
                }
            }
            
            // If vehicle not found in active list, don't publish its state
            if (!vehiculoEncontrado) {
                return;
            }
        }
        
        simulationModel.updateState(estado);
    }

    @Override
    public boolean puedeAvanzar(String vehiculoId, double nextX, double nextY) {
        return colisionDetector.puedeMoverse(vehiculoId, nextX, nextY,
                simulationModel.getVehiculos().values());
    }

    @Override
    public void solicitarCruceInterseccion(String vehiculoId, TipoVehiculo tipo,
                                           double vehiculoPosX, double vehiculoPosY) throws InterruptedException {
        Long ultimaSolicitud = solicitudesCrucePendientes.get(vehiculoId);
        long tiempoActual = System.currentTimeMillis();

        if (ultimaSolicitud != null && (tiempoActual - ultimaSolicitud) < TIMEOUT_SOLICITUD_CRUCE) {
            return;
        }

        solicitudesCrucePendientes.put(vehiculoId, tiempoActual);

        try {
            CruceManager.DireccionCruce direccionEntrada =
                    cruceManager.determinarInterseccionMasCercana(vehiculoPosX, vehiculoPosY);

            logger.info("🚦 vehículo " + vehiculoId + " (" + tipo + ") solicita cruzar desde " +
                    direccionEntrada + " en posición (" +
                    String.format("%.2f", vehiculoPosX) + ", " +
                    String.format("%.2f", vehiculoPosY) + ")");

            cruceManager.solicitarCruce(vehiculoId, tipo, direccionEntrada);

        } finally {
            solicitudesCrucePendientes.remove(vehiculoId);
        }
    }

    @Override
    public boolean estaCercaDeInterseccion(String vehiculoId, double posX, double posY) {
        // Distancia utilizada para considerar que un vehículo se encuentra cerca de
        // alguna de las intersecciones
        double distanciaProximidad = 75.0; // Reducida a un valor más preciso
        
        // Verificar proximidad a cualquier intersección usando distancia simple
        for (CruceManager.DireccionCruce direccion : CruceManager.DireccionCruce.values()) {
            double distancia = Math.sqrt(
                Math.pow(posX - direccion.posX, 2) + 
                Math.pow(posY - direccion.posY, 2)
            );
            
            if (distancia <= distanciaProximidad) {
                return true;
            }
        }

        // También verificar si está en el área central del cruce
        double cruceMinX = 355;  // límite izquierdo del cruce
        double cruceMaxX = 445;  // límite derecho del cruce
        double cruceMinY = 250;  // límite superior del cruce
        double cruceMaxY = 340;  // límite inferior del cruce
        
        if (posX >= cruceMinX && posX <= cruceMaxX && posY >= cruceMinY && posY <= cruceMaxY) {
            return true;
        }
        
        return false;
    }

    public CruceManager.EstadoCruce getEstadoCruce() {
        return cruceManager.getEstadoCruce();
    }

    public ColisionDetector.ReporteColisiones generarReporteColisiones() {
        return colisionDetector.generarReporte(simulationModel.getVehiculos().values());
    }

    public int getTotalVehiculosCruzados() {
        return cruceManager.getTotalVehiculosProcessed();
    }

    public boolean hayVehiculosEsperando() {
        return cruceManager.tieneVehiculosEsperando();
    }

    public CruceManager.DireccionCruce getInterseccionMenosCongestionada() {
        return cruceManager.getInterseccionMenosCongestionada();
    }

    public void limpiarSolicitudesAntiguas() {
        long tiempoActual = System.currentTimeMillis();

        solicitudesCrucePendientes.entrySet().removeIf(entry ->
                (tiempoActual - entry.getValue()) > TIMEOUT_SOLICITUD_CRUCE * 2
        );
        
        // También limpiar destinos que llevan mucho tiempo ocupados
        cruceManager.limpiarDestinosAntiguos();
    }

    public SimulationModel getSimulationModel() {
        return simulationModel;
    }

    public CruceManager getCruceManager() {
        return cruceManager;
    }

    public ColisionDetector getColisionDetector() {
        return colisionDetector;
    }

    /**
     * Obtiene el punto de salida registrado para un vehículo.
     *
     * @param vehiculoId id del vehículo
     * @return PuntoSalida asociado o null si no se conoce
     */
    public PuntoSalida getPuntoSalidaVehiculo(String vehiculoId) {
        return puntoSalidaPorVehiculo.get(vehiculoId);
    }

    /**
     * Registra el punto de salida para un vehículo.
     *
     * @param vehiculoId id del vehículo
     * @param puntoSalida punto de salida del vehículo
     */
    public void registrarPuntoSalidaVehiculo(String vehiculoId, PuntoSalida puntoSalida) {
        puntoSalidaPorVehiculo.put(vehiculoId, puntoSalida);
    }

    @Override
    public void eliminarVehiculo(String vehiculoId) {
        simulationModel.removeState(vehiculoId);
        puntoSalidaPorVehiculo.remove(vehiculoId);
        
        // CRITICAL: Also remove from global coordinator to prevent stale state
        cruceManager.getCoordinadorGlobal().removerVehiculoCompletamente(vehiculoId);
    }

    /**
     * Libera el cruce cuando el vehículo termina de cruzar.
     * 
     * @param vehiculoId ID del vehículo
     * @param vehiculoPosX posición X actual
     * @param vehiculoPosY posición Y actual
     */
    public void liberarCruceInterseccion(String vehiculoId, double vehiculoPosX, double vehiculoPosY) {
        CruceManager.DireccionCruce direccionEntrada =
                cruceManager.determinarInterseccionMasCercana(vehiculoPosX, vehiculoPosY);
        
        cruceManager.liberarCruce(vehiculoId, direccionEntrada);
        
        logger.info("🏁 vehículo " + vehiculoId + " liberó cruce en posición (" +
                   String.format("%.2f", vehiculoPosX) + ", " +
                   String.format("%.2f", vehiculoPosY) + ")");
    }
}