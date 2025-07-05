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

    private static final double DISTANCIA_PROXIMIDAD_INTERSECCION = 25.0;
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
        // Try to get PuntoSalida from the active vehicles (if available)
        if (!puntoSalidaPorVehiculo.containsKey(estado.id())) {
            // Try to find the Vehiculo instance and get its PuntoSalida
            // This is a workaround: if you have a global list of active vehicles, use it
            for (Vehiculo v : SimulationController.getVehiculosActivos()) {
                if (v.getId().equals(estado.id())) {
                    puntoSalidaPorVehiculo.put(estado.id(), v.getPuntoSalida());
                    break;
                }
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
        double centroX = 400, centroY = 295, halfIntersection = 45, buffer = 10;

        PuntoSalida salida = puntoSalidaPorVehiculo.get(vehiculoId);
        if (salida == null) {
            return false;
        }

        switch (salida) {
            case ARRIBA:
                return posY >= (centroY - halfIntersection - buffer - 5) && posY < (centroY - halfIntersection + buffer);
            case ABAJO:
                return posY <= (centroY + halfIntersection + buffer + 5) && posY > (centroY + halfIntersection - buffer);
            case IZQUIERDA:
                return posX >= (centroX - halfIntersection - buffer - 5) && posX < (centroX - halfIntersection + buffer);
            case DERECHA:
                return posX <= (centroX + halfIntersection + buffer + 5) && posX > (centroX + halfIntersection - buffer);
            default:
                return false;
        }
    }

    @Override
    public boolean esPrimerEnFila(String id, double posX, double posY) {
        CruceManager.DireccionCruce direccion = cruceManager.determinarInterseccionMasCercana(posX, posY);
        return cruceManager.esPrimerEnFila(id, direccion);
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
}