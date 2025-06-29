package edu.pucmm.model;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * pruebas unitarias para simulationModel.
 * incluye verificación de thread-safety y funcionalidad básica.
 */
class SimulationModelTest {

    @BeforeAll
    static void initToolkit() {
        // inicializar javafx platform para las pruebas
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // ya estaba inicializado
        }
    }

    @Test
    void testModeloBasico() {
        SimulationModel model = new SimulationModel();
        
        // crear estados de prueba
        VehiculoState vehiculo1 = new VehiculoState("v001", 10.0, 20.0, TipoVehiculo.normal);
        VehiculoState vehiculo2 = new VehiculoState("v002", 30.0, 40.0, TipoVehiculo.emergencia);
        
        // añadir estados
        model.addState(vehiculo1);
        model.addState(vehiculo2);
        
        // verificar que se añadieron correctamente
        assertEquals(2, model.getVehiculos().size());
        assertTrue(model.getVehiculos().containsKey("v001"));
        assertTrue(model.getVehiculos().containsKey("v002"));
        
        // actualizar estado
        VehiculoState vehiculo1Updated = new VehiculoState("v001", 15.0, 25.0, TipoVehiculo.normal);
        model.updateState(vehiculo1Updated);
        
        assertEquals(15.0, model.getVehiculos().get("v001").posX());
        assertEquals(25.0, model.getVehiculos().get("v001").posY());
        
        // remover estado
        model.removeState("v001");
        assertEquals(1, model.getVehiculos().size());
        assertFalse(model.getVehiculos().containsKey("v001"));
    }

    @Test
    void testObserverPattern() throws InterruptedException {
        SimulationModel model = new SimulationModel();
        AtomicInteger notificationCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(3);
        
        // crear observador de prueba
        SimulationObserver observer = snapshot -> {
            notificationCount.incrementAndGet();
            latch.countDown();
        };
        
        model.addObserver(observer);
        
        // generar cambios
        model.addState(new VehiculoState("v001", 0, 0, TipoVehiculo.normal));
        model.updateState(new VehiculoState("v001", 10, 10, TipoVehiculo.normal));
        model.removeState("v001");
        
        // esperar notificaciones
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(3, notificationCount.get());
    }

    @Test 
    void testConcurrenciaBasica(TestInfo testInfo) throws InterruptedException {
        SimulationModel model = new SimulationModel();
        int numThreads = 5;
        int vehiculosPorThread = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(numThreads);
        
        // crear múltiples hilos que añaden vehículos concurrentemente
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await(); // esperar señal de inicio
                    
                    for (int j = 0; j < vehiculosPorThread; j++) {
                        String id = String.format("t%d-v%d", threadId, j);
                        VehiculoState estado = new VehiculoState(
                            id, 
                            threadId * 10.0 + j, 
                            threadId * 20.0 + j, 
                            j % 2 == 0 ? TipoVehiculo.normal : TipoVehiculo.emergencia
                        );
                        
                        model.addState(estado);
                        
                        // pequeña pausa para simular procesamiento
                        Thread.sleep(1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
            thread.start();
        }
        
        // iniciar todos los hilos simultáneamente
        startLatch.countDown();
        
        // esperar a que terminen todos
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), 
                  "las pruebas de concurrencia deberían completarse en 10 segundos");
        
        // verificar que todos los vehículos se añadieron correctamente
        assertEquals(numThreads * vehiculosPorThread, model.getVehiculos().size(),
                    "deberían existir exactamente " + (numThreads * vehiculosPorThread) + " vehículos");
        
        System.out.println("✅ prueba de concurrencia completada exitosamente - " + 
                          model.getVehiculos().size() + " vehículos procesados");
    }

    @Test
    void testVehiculoStateInmutabilidad() {
        // verificar que vehiculoState es inmutable
        VehiculoState estado = new VehiculoState("v001", 10.0, 20.0, TipoVehiculo.normal);
        
        assertEquals("v001", estado.id());
        assertEquals(10.0, estado.posX());
        assertEquals(20.0, estado.posY());
        assertEquals(TipoVehiculo.normal, estado.tipo());
        
        // verificar validación
        assertThrows(IllegalArgumentException.class, 
                    () -> new VehiculoState(null, 0, 0, TipoVehiculo.normal));
        assertThrows(IllegalArgumentException.class, 
                    () -> new VehiculoState("", 0, 0, TipoVehiculo.normal));
        assertThrows(IllegalArgumentException.class, 
                    () -> new VehiculoState("v001", 0, 0, null));
    }
} 