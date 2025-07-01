package edu.pucmm.simulation;

import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Demostración del Escenario 1: Cruce de Calles con 4 Intersecciones.
 */
public class CruceEscenario1Demo {
    
    private static final Logger logger = Logger.getLogger(CruceEscenario1Demo.class.getName());
    
    public static void main(String[] args) {
        logger.info("🚦 INICIANDO DEMOSTRACIÓN - ESCENARIO 1: CRUCE DE CALLES");
        logger.info("═══════════════════════════════════════════════════════");
        
        SimulationModel baseModel = new SimulationModel();
        CruceSimulationModel cruceModel = new CruceSimulationModel(baseModel, "CRUCE-PRINCIPAL");
        
        baseModel.addObserver(new CruceObserver());
        
        ScheduledExecutorService monitoringExecutor = Executors.newScheduledThreadPool(2);
        
        try {
            iniciarMonitoreoEstadisticas(cruceModel, monitoringExecutor);
            
            logger.info("📍 FASE 1: Generando vehículos normales...");
            Vehiculo[] vehiculosNormales = crearVehiculosNormales(cruceModel, 8);
            Thread[] hilosNormales = iniciarVehiculos(vehiculosNormales, "Normal");
            
            Thread.sleep(5000);
            
            logger.info("🚨 FASE 2: Agregando vehículos de emergencia...");
            Vehiculo[] vehiculosEmergencia = crearVehiculosEmergencia(cruceModel, 3);
            Thread[] hilosEmergencia = iniciarVehiculos(vehiculosEmergencia, "Emergencia");
            
            Thread.sleep(8000);
            
            logger.info("🛑 FASE 3: Deteniendo simulación...");
            detenerVehiculos(vehiculosNormales);
            detenerVehiculos(vehiculosEmergencia);
            
            esperarFinalizacionHilos(hilosNormales, hilosEmergencia);
            
            mostrarEstadisticasFinales(cruceModel);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.severe("demostración interrumpida");
        } finally {
            monitoringExecutor.shutdown();
            logger.info("✅ DEMOSTRACIÓN COMPLETADA");
        }
    }
    
    private static Vehiculo[] crearVehiculosNormales(CruceSimulationModel cruceModel, int cantidad) {
        Vehiculo[] vehiculos = new Vehiculo[cantidad];
        
        for (int i = 0; i < cantidad; i++) {
            CruceManager.DireccionCruce direccion = CruceManager.DireccionCruce.values()[i % 4];
            
            double posX = direccion.posX + (Math.random() - 0.5) * 20 - (direccion.posX > 0 ? 30 : -30);
            double posY = direccion.posY + (Math.random() - 0.5) * 20 - (direccion.posY > 0 ? 30 : -30);
            
            Direccion direccionMovimiento = determinarDireccionMovimiento(direccion);
            
            vehiculos[i] = new VehiculoNormal(
                "NORMAL-" + String.format("%02d", i + 1),
                posX, posY,
                direccionMovimiento,
                cruceModel
            );
        }
        
        return vehiculos;
    }
    
    private static Vehiculo[] crearVehiculosEmergencia(CruceSimulationModel cruceModel, int cantidad) {
        Vehiculo[] vehiculos = new Vehiculo[cantidad];
        
        for (int i = 0; i < cantidad; i++) {
            CruceManager.DireccionCruce direccion = CruceManager.DireccionCruce.values()[i % 4];
            
            double posX = direccion.posX + (Math.random() - 0.5) * 15 - (direccion.posX > 0 ? 40 : -40);
            double posY = direccion.posY + (Math.random() - 0.5) * 15 - (direccion.posY > 0 ? 40 : -40);
            
            Direccion direccionMovimiento = determinarDireccionMovimiento(direccion);
            
            vehiculos[i] = new VehiculoEmergencia(
                "EMG-" + String.format("%02d", i + 1),
                posX, posY,
                direccionMovimiento,
                cruceModel
            );
        }
        
        return vehiculos;
    }
    
    private static Direccion determinarDireccionMovimiento(CruceManager.DireccionCruce direccionCruce) {
        return switch (direccionCruce) {
            case NORTE -> Direccion.recto;
            case SUR -> Direccion.vuelta_u;
            case ESTE -> Direccion.izquierda;
            case OESTE -> Direccion.derecha;
        };
    }
    
    private static Thread[] iniciarVehiculos(Vehiculo[] vehiculos, String tipo) {
        Thread[] hilos = new Thread[vehiculos.length];
        
        for (int i = 0; i < vehiculos.length; i++) {
            hilos[i] = new Thread(vehiculos[i]);
            hilos[i].setName(tipo + "-" + vehiculos[i].getId());
            hilos[i].start();
        }
        
        logger.info("✅ iniciados " + vehiculos.length + " vehículos " + tipo.toLowerCase());
        return hilos;
    }
    
    private static void detenerVehiculos(Vehiculo[] vehiculos) {
        for (Vehiculo vehiculo : vehiculos) {
            vehiculo.stop();
        }
    }
    
    private static void esperarFinalizacionHilos(Thread[]... gruposHilos) throws InterruptedException {
        for (Thread[] grupo : gruposHilos) {
            for (Thread hilo : grupo) {
                hilo.join(3000);
            }
        }
    }
    
    private static void iniciarMonitoreoEstadisticas(CruceSimulationModel cruceModel, 
                                                    ScheduledExecutorService executor) {
        
        executor.scheduleAtFixedRate(() -> {
            try {
                CruceManager.EstadoCruce estado = cruceModel.getEstadoCruce();
                logger.info("📊 CRUCE " + estado.id() + " - Procesados: " + 
                           estado.totalVehiculosProcessed() + 
                           " | Esperando: " + (estado.tieneVehiculosEsperando() ? "SÍ" : "NO"));
                
                estado.intersecciones().forEach((direccion, interseccion) -> {
                    if (interseccion.vehiculosEnEspera() > 0 || interseccion.vehiculosProcessed() > 0) {
                        logger.info("  " + direccion + " - Esperando: " + 
                                   interseccion.vehiculosEnEspera() + 
                                   " | Procesados: " + interseccion.vehiculosProcessed());
                    }
                });
                
            } catch (Exception e) {
                logger.warning("error en monitoreo de estadísticas: " + e.getMessage());
            }
        }, 2, 3, TimeUnit.SECONDS);
        
        executor.scheduleAtFixedRate(() -> {
            cruceModel.limpiarSolicitudesAntiguas();
        }, 10, 10, TimeUnit.SECONDS);
    }
    
    private static void mostrarEstadisticasFinales(CruceSimulationModel cruceModel) {
        logger.info("📈 ESTADÍSTICAS FINALES");
        logger.info("═══════════════════════");
        
        CruceManager.EstadoCruce estadoFinal = cruceModel.getEstadoCruce();
        ColisionDetector.ReporteColisiones reporteColisiones = cruceModel.generarReporteColisiones();
        
        logger.info("• Total vehículos que cruzaron: " + estadoFinal.totalVehiculosProcessed());
        logger.info("• Vehículos activos al final: " + 
                   cruceModel.getSimulationModel().getVehiculos().size());
        logger.info("• Colisiones potenciales detectadas: " + reporteColisiones.colisionesPotenciales());
        logger.info("• Vehículos en riesgo: " + reporteColisiones.vehiculosEnRiesgo());
        
        logger.info("\n🚦 DETALLES POR INTERSECCIÓN:");
        estadoFinal.intersecciones().forEach((direccion, interseccion) -> {
            logger.info("  " + direccion + ": " + interseccion.vehiculosProcessed() + " vehículos procesados");
        });
        
        var interseccionMasUtilizada = estadoFinal.intersecciones().entrySet().stream()
                .max((e1, e2) -> Integer.compare(
                    e1.getValue().vehiculosProcessed(),
                    e2.getValue().vehiculosProcessed()
                ))
                .orElse(null);
        
        if (interseccionMasUtilizada != null) {
            logger.info("🏆 Intersección más utilizada: " + 
                       interseccionMasUtilizada.getKey() + 
                       " (" + interseccionMasUtilizada.getValue().vehiculosProcessed() + " vehículos)");
        }
    }
    
    private static class CruceObserver implements edu.pucmm.model.SimulationObserver {
        private int updateCount = 0;
        
        @Override
        public void onStateChange(java.util.Map<String, edu.pucmm.model.VehiculoState> snapshot) {
            updateCount++;
            
            if (updateCount % 50 == 0) {
                long vehiculosNormales = snapshot.values().stream()
                        .filter(v -> v.tipo() == TipoVehiculo.normal)
                        .count();
                        
                long vehiculosEmergencia = snapshot.values().stream()
                        .filter(v -> v.tipo() == TipoVehiculo.emergencia)
                        .count();
                
                logger.info("🔄 actualización #" + updateCount + " - Activos: " + 
                           snapshot.size() + " (Normal: " + vehiculosNormales + 
                           ", Emergencia: " + vehiculosEmergencia + ")");
            }
        }
    }
} 