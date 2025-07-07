package edu.pucmm.simulation;

import edu.pucmm.controller.SimulationController;
import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Demostración simple del sistema de vehículos como agentes.
 * Muestra cómo múltiples vehículos ejecutan concurrentemente y publican sus estados.
 */
public class VehiculoDemoSimple {
    
    private static final Logger logger = Logger.getLogger(VehiculoDemoSimple.class.getName());
    
    public static void main(String[] args) {
        logger.info("🚀 iniciando demostración de vehículos como agentes");
        
        // crear modelo de simulación
        SimulationModel simulationModel = new SimulationModel();
        
        // crear adaptador para la interface
        ISimulationModel adapter = new SimulationModelAdapter(simulationModel);
        
        // registrar observador para mostrar cambios
        simulationModel.addObserver(VehiculoDemoSimple::onStateChange);
        
        // crear flota de vehículos
        Vehiculo[] vehiculos = SimulationController.getVehiculosActivos().toArray(new Vehiculo[0]);
        for (Vehiculo v : vehiculos) {
            new Thread(v).start();
        }

        logger.info("📊 flota creada: " + vehiculos.length + " vehículos");
        for (Vehiculo vehiculo : vehiculos) {
            logger.info("  - " + vehiculo);
        }
        
        // iniciar todos los vehículos como hilos independientes
        Thread[] hilosVehiculos = new Thread[vehiculos.length];
        for (int i = 0; i < vehiculos.length; i++) {
            hilosVehiculos[i] = new Thread(vehiculos[i]);
            hilosVehiculos[i].setName("Vehiculo-" + vehiculos[i].getId());
            hilosVehiculos[i].start();
        }
        
        logger.info("🎯 todos los vehículos iniciados, ejecutando simulación...");
        
        try {
            // ejecutar por 3 segundos
            Thread.sleep(3000);
            
            logger.info("⏸️ pausando todos los vehículos...");
            for (Vehiculo vehiculo : vehiculos) {
                vehiculo.pause();
            }
            
            Thread.sleep(1000);
            
            logger.info("▶️ reanudando vehículos...");
            for (Vehiculo vehiculo : vehiculos) {
                vehiculo.resume();
            }
            
            Thread.sleep(2000);
            
            logger.info("🛑 deteniendo simulación...");
            for (Vehiculo vehiculo : vehiculos) {
                vehiculo.stop();
            }
            
            // esperar a que terminen todos los hilos
            for (Thread hilo : hilosVehiculos) {
                hilo.join(2000); // timeout de 2 segundos
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.severe("demostración interrumpida");
        }
        
        logger.info("✅ demostración completada");
        mostrarEstadisticasFinales(simulationModel);
    }
    
    /**
     * Observer para cambios de estado.
     */
    private static void onStateChange(Map<String, VehiculoState> snapshot) {
        if (snapshot.size() > 0) {
            // logging cada 10 actualizaciones para no saturar la consola
            if (snapshot.hashCode() % 10 == 0) {
                logger.info("📍 actualización de estados (" + snapshot.size() + " vehículos activos)");
                snapshot.values().forEach(estado -> 
                    logger.info("  " + estado)
                );
                logger.info("---");
            }
        }
    }
    
    /**
     * Muestra estadísticas finales de la simulación.
     */
    private static void mostrarEstadisticasFinales(SimulationModel model) {
        Map<String, VehiculoState> estadosFinales = Map.copyOf(model.getVehiculos());
        
        long vehiculosNormales = estadosFinales.values().stream()
                .filter(v -> v.tipo() == TipoVehiculo.normal)
                .count();
                
        long vehiculosEmergencia = estadosFinales.values().stream()
                .filter(v -> v.tipo() == TipoVehiculo.emergencia)
                .count();
        
        logger.info("📈 estadísticas finales:");
        logger.info("  - total vehículos activos: " + estadosFinales.size());
        logger.info("  - vehículos normales: " + vehiculosNormales);
        logger.info("  - vehículos de emergencia: " + vehiculosEmergencia);
        
        if (!estadosFinales.isEmpty()) {
            logger.info("  - posiciones finales:");
            estadosFinales.values().forEach(estado -> 
                logger.info("    " + estado.id() + ": (" + 
                          String.format("%.2f", estado.posX()) + ", " + 
                          String.format("%.2f", estado.posY()) + ")")
            );
        }
    }
    
    /**
     * Adaptador que implementa ISimulationModel usando SimulationModel.
     */
    private static class SimulationModelAdapter implements ISimulationModel {
        private final SimulationModel model;
        
        public SimulationModelAdapter(SimulationModel model) {
            this.model = model;
        }
        
        @Override
        public void publishState(VehiculoState estado) {
            model.updateState(estado);
        }
        
        @Override
        public boolean puedeAvanzar(String vehiculoId, double nextX, double nextY) {
            // implementación simple: verificar que no hay colisión con otros vehículos
            double minDistance = 10.0; // distancia mínima entre vehículos
            
            return model.getVehiculos().values().stream()
                    .filter(v -> !v.id().equals(vehiculoId))
                    .noneMatch(v -> {
                        double distance = Math.sqrt(Math.pow(v.posX() - nextX, 2) + 
                                                  Math.pow(v.posY() - nextY, 2));
                        return distance < minDistance;
                    });
        }
        
        @Override
        public void solicitarCruceInterseccion(String vehiculoId, TipoVehiculo tipo, 
                                              double vehiculoPosX, double vehiculoPosY) throws InterruptedException {
            // implementación vacía para demo simple - no hay cruces en esta demostración
            logger.info("demo simple: ignorando solicitud de cruce para " + vehiculoId);
        }
        
        public void liberarCruceInterseccion(String vehiculoId, double vehiculoPosX, double vehiculoPosY) {
            // implementación vacía para demo simple
            logger.info("demo simple: ignorando liberación de cruce para " + vehiculoId);
        }
        
        @Override
        public boolean estaCercaDeInterseccion(String vehiculoId, double posX, double posY) {
            // en demo simple no hay intersecciones
            return false;
        }

        @Override
        public void eliminarVehiculo(String vehiculoId) {
            model.removeState(vehiculoId);
        }
    }
}
 