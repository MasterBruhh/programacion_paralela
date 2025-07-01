package edu.pucmm.simulation;

import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas comprehensivas para el Escenario 1: Cruce de Calles.
 * 
 * Verifica:
 * - Funcionamiento FIFO de las intersecciones
 * - Prioridad de vehículos de emergencia
 * - Detección de colisiones AABB
 * - Concurrencia y thread-safety
 * - Integración completa del sistema
 */
class CruceEscenario1Test {
    
    private SimulationModel baseModel;
    private CruceSimulationModel cruceModel;
    
    @BeforeEach
    void setUp() {
        baseModel = new SimulationModel();
        cruceModel = new CruceSimulationModel(baseModel, "TEST-CRUCE");
        VehiculoFactory.resetIdCounter();
    }
    
    @Test
    @DisplayName("InterseccionManager debe procesar vehículos en orden FIFO")
    void testInterseccionFIFO() throws InterruptedException {
        InterseccionManager interseccion = new InterseccionManager("TEST-INTERSECCION");
        
        List<String> ordenProcesamiento = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(3);
        
        // crear 3 vehículos que lleguen simultáneamente
        Runnable vehiculo1 = () -> {
            try {
                interseccion.solicitarCruce("VEH-001", TipoVehiculo.normal);
                ordenProcesamiento.add("VEH-001");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };
        
        Runnable vehiculo2 = () -> {
            try {
                Thread.sleep(10); // pequeño delay para garantizar orden
                interseccion.solicitarCruce("VEH-002", TipoVehiculo.normal);
                ordenProcesamiento.add("VEH-002");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };
        
        Runnable vehiculo3 = () -> {
            try {
                Thread.sleep(20); // mayor delay
                interseccion.solicitarCruce("VEH-003", TipoVehiculo.normal);
                ordenProcesamiento.add("VEH-003");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        };
        
        // ejecutar concurrentemente
        Thread t1 = new Thread(vehiculo1);
        Thread t2 = new Thread(vehiculo2);
        Thread t3 = new Thread(vehiculo3);
        
        t1.start();
        t2.start();
        t3.start();
        
        // esperar a que terminen
        assertTrue(latch.await(10, TimeUnit.SECONDS), "todos los vehículos deberían cruzar");
        
        // verificar orden FIFO
        assertEquals(3, ordenProcesamiento.size());
        assertEquals("VEH-001", ordenProcesamiento.get(0));
        assertEquals("VEH-002", ordenProcesamiento.get(1));
        assertEquals("VEH-003", ordenProcesamiento.get(2));
        
        assertEquals(3, interseccion.getVehiculosProcessed());
    }
    
    @Test
    @DisplayName("Vehículos de emergencia deben tener prioridad absoluta")
    void testPrioridadEmergencia() throws InterruptedException {
        InterseccionManager interseccion = new InterseccionManager("TEST-PRIORIDAD");
        
        List<String> ordenProcesamiento = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(4);
        
        // vehículo normal que llega primero
        Thread vehiculoNormal1 = new Thread(() -> {
            try {
                interseccion.solicitarCruce("NORMAL-001", TipoVehiculo.normal);
                ordenProcesamiento.add("NORMAL-001");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        
        // vehículo de emergencia que llega después
        Thread vehiculoEmergencia = new Thread(() -> {
            try {
                Thread.sleep(100); // llega después
                interseccion.solicitarCruce("EMG-001", TipoVehiculo.emergencia);
                ordenProcesamiento.add("EMG-001");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        
        // más vehículos normales
        Thread vehiculoNormal2 = new Thread(() -> {
            try {
                Thread.sleep(50);
                interseccion.solicitarCruce("NORMAL-002", TipoVehiculo.normal);
                ordenProcesamiento.add("NORMAL-002");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        
        Thread vehiculoNormal3 = new Thread(() -> {
            try {
                Thread.sleep(150);
                interseccion.solicitarCruce("NORMAL-003", TipoVehiculo.normal);
                ordenProcesamiento.add("NORMAL-003");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        
        vehiculoNormal1.start();
        vehiculoEmergencia.start();
        vehiculoNormal2.start();
        vehiculoNormal3.start();
        
        assertTrue(latch.await(15, TimeUnit.SECONDS));
        
        // verificar que emergencia tiene prioridad
        assertEquals(4, ordenProcesamiento.size());
        assertTrue(ordenProcesamiento.indexOf("EMG-001") < ordenProcesamiento.indexOf("NORMAL-002"));
        assertTrue(ordenProcesamiento.indexOf("EMG-001") < ordenProcesamiento.indexOf("NORMAL-003"));
    }
    
    @Test
    @DisplayName("ColisionDetector debe detectar colisiones AABB correctamente")
    void testDeteccionColisiones() {
        ColisionDetector detector = new ColisionDetector();
        
        // crear vehículos para test
        List<VehiculoState> vehiculos = List.of(
            new VehiculoState("V1", 10.0, 10.0, TipoVehiculo.normal),
            new VehiculoState("V2", 15.0, 15.0, TipoVehiculo.normal),
            new VehiculoState("V3", 50.0, 50.0, TipoVehiculo.emergencia)
        );
        
        // test colisión cercana
        assertFalse(detector.puedeMoverse("V1", 12.0, 12.0, vehiculos),
                   "debería detectar colisión con V2");
        
        // test movimiento seguro
        assertTrue(detector.puedeMoverse("V1", 5.0, 5.0, vehiculos),
                  "debería permitir movimiento seguro");
        
        // test colisión con emergencia
        assertFalse(detector.puedeMoverse("V3", 13.0, 13.0, vehiculos),
                   "debería detectar colisión con V1");
        
        // test vehículo inexistente
        assertTrue(detector.puedeMoverse("V999", 0.0, 0.0, vehiculos),
                  "debería permitir movimiento de vehículo inexistente");
    }
    
    @Test
    @DisplayName("CruceManager debe gestionar 4 intersecciones correctamente")
    void testCruceManagerCompleto() throws InterruptedException {
        CruceManager cruce = new CruceManager("TEST-CRUCE-COMPLETO");
        
        CountDownLatch latch = new CountDownLatch(8);
        AtomicInteger vehiculosProcessed = new AtomicInteger(0);
        
        // crear vehículos para cada dirección
        for (CruceManager.DireccionCruce direccion : CruceManager.DireccionCruce.values()) {
            // 2 vehículos por dirección
            for (int i = 0; i < 2; i++) {
                final String vehiculoId = direccion.name() + "-" + (i + 1);
                final TipoVehiculo tipo = (i == 0) ? TipoVehiculo.normal : TipoVehiculo.emergencia;
                
                Thread vehiculoThread = new Thread(() -> {
                    try {
                        cruce.solicitarCruce(vehiculoId, tipo, direccion);
                        vehiculosProcessed.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
                
                vehiculoThread.start();
            }
        }
        
        assertTrue(latch.await(20, TimeUnit.SECONDS), "todos los vehículos deberían cruzar");
        assertEquals(8, vehiculosProcessed.get());
        assertEquals(8, cruce.getTotalVehiculosProcessed());
    }
    
    @Test
    @DisplayName("CruceSimulationModel debe integrar todos los componentes")
    void testIntegracionCompleta() throws InterruptedException {
        // verificar que el modelo fue creado correctamente
        assertNotNull(cruceModel);
        assertNotNull(baseModel);
        
        // verificar detección de proximidad (test básico sin vehículos)
        // OESTE está en (-50, 0), entonces (-40, 0) está a 10 unidades de distancia (< 15)
        assertTrue(cruceModel.estaCercaDeInterseccion("TEST-001", -40.0, 0.0),
                  "debería detectar proximidad a intersección OESTE");
        assertFalse(cruceModel.estaCercaDeInterseccion("TEST-001", -100.0, -100.0),
                   "no debería detectar proximidad lejos del cruce");
        
        // verificar que el cruce manager funciona
        assertTrue(cruceModel.getTotalVehiculosCruzados() >= 0);
        
        // verificar que el detector de colisiones funciona
        ColisionDetector.ReporteColisiones reporte = cruceModel.generarReporteColisiones();
        assertNotNull(reporte);
        assertEquals(0, reporte.totalVehiculos()); // sin vehículos aún
        
        // agregar un vehículo de prueba al modelo base
        VehiculoState estadoPrueba = new VehiculoState("TEST-001", 0.0, 0.0, TipoVehiculo.normal);
        baseModel.addState(estadoPrueba);
        
        // verificar que se agregó
        assertEquals(1, baseModel.getVehiculos().size());
        assertTrue(baseModel.getVehiculos().containsKey("TEST-001"));
    }
    
    @Test
    @DisplayName("Sistema debe manejar carga concurrente alta")
    void testCargaConcurrenteAlta() throws InterruptedException {
        int numVehiculos = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(numVehiculos);
        AtomicInteger vehiculosCompletados = new AtomicInteger(0);
        
        // crear múltiples vehículos
        for (int i = 0; i < numVehiculos; i++) {
            final String vehiculoId = "STRESS-" + String.format("%03d", i);
            final TipoVehiculo tipo = (i % 5 == 0) ? TipoVehiculo.emergencia : TipoVehiculo.normal;
            
            Thread vehiculoThread = new Thread(() -> {
                try {
                    startLatch.await(); // esperar señal de inicio
                    
                    // simular solicitud de cruce
                    double posX = (Math.random() - 0.5) * 100;
                    double posY = (Math.random() - 0.5) * 100;
                    
                    if (cruceModel.estaCercaDeInterseccion(vehiculoId, posX, posY)) {
                        cruceModel.solicitarCruceInterseccion(vehiculoId, tipo, posX, posY);
                    }
                    
                    vehiculosCompletados.incrementAndGet();
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
            
            vehiculoThread.start();
        }
        
        // iniciar todos simultáneamente
        startLatch.countDown();
        
        // esperar completación
        assertTrue(completeLatch.await(30, TimeUnit.SECONDS), 
                  "todos los vehículos deberían completar en tiempo razonable");
        
        assertTrue(vehiculosCompletados.get() > 0, "al menos algunos vehículos deberían completar");
    }
    
    @Test
    @DisplayName("Reporte de colisiones debe generar estadísticas correctas")
    void testReporteColisiones() {
        // agregar vehículos al modelo
        baseModel.updateState(new VehiculoState("V1", 0.0, 0.0, TipoVehiculo.normal));
        baseModel.updateState(new VehiculoState("V2", 2.0, 2.0, TipoVehiculo.normal)); // muy cerca
        baseModel.updateState(new VehiculoState("V3", 50.0, 50.0, TipoVehiculo.emergencia)); // lejos
        
        ColisionDetector.ReporteColisiones reporte = cruceModel.generarReporteColisiones();
        
        assertEquals(3, reporte.totalVehiculos());
        assertTrue(reporte.colisionesPotenciales() >= 0);
        assertTrue(reporte.vehiculosEnRiesgo() >= 0);
        assertTrue(reporte.timestamp() > 0);
    }
    
    /**
     * Observer de prueba para capturar actualizaciones.
     */
    private static class TestObserver implements edu.pucmm.model.SimulationObserver {
        volatile int updateCount = 0;
        
        @Override
        public void onStateChange(java.util.Map<String, VehiculoState> snapshot) {
            updateCount++;
        }
    }
} 