package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test específico para verificar que los vehículos realizan el pare obligatorio
 * antes de cruzar la intersección.
 */
class PareObligatorioTest {

    private SimulationModel baseModel;
    private CruceSimulationModel cruceModel;
    private CountDownLatch latch;

    @BeforeEach
    void setUp() {
        baseModel = new SimulationModel();
        cruceModel = new CruceSimulationModel(baseModel, "test-cruce");
        latch = new CountDownLatch(1);
    }

    @Test
    @DisplayName("Vehículo debe realizar pare obligatorio de al menos 1.5 segundos en stop sign")
    void testPareObligatorio() throws InterruptedException {
        // Crear vehículo que va de izquierda a derecha
        Vehiculo vehiculo = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.IZQUIERDA,
            Direccion.derecha,
            cruceModel,
            50.0,  // posición inicial lejos del stop
            295.0
        );

        // Variables para trackear el comportamiento
        AtomicBoolean llegOAlStop = new AtomicBoolean(false);
        AtomicLong tiempoLlegadaAlStop = new AtomicLong(0);
        AtomicLong tiempoSalidaDelStop = new AtomicLong(0);
        AtomicBoolean completoPare = new AtomicBoolean(false);

        // Ejecutar vehículo en thread separado
        Thread vehiculoThread = new Thread(() -> {
            try {
                new Thread(vehiculo).start();
                
                // Esperar hasta que se acerque al stop sign
                while (vehiculo.isRunning() && vehiculo.getPosX() < 320.0) {
                    Thread.sleep(50);
                }
                
                // Marcar que llegó al stop
                if (!llegOAlStop.get()) {
                    llegOAlStop.set(true);
                    tiempoLlegadaAlStop.set(System.currentTimeMillis());
                }
                
                // Esperar hasta que empiece a moverse de nuevo (salga del stop)
                double posXAnterior = vehiculo.getPosX();
                while (vehiculo.isRunning() && Math.abs(vehiculo.getPosX() - posXAnterior) < 1.0) {
                    Thread.sleep(50);
                    // Verificar si está en fase de pare obligatorio
                    if (vehiculo.faseMovimiento == Vehiculo.FaseMovimiento.EN_PARE_OBLIGATORIO) {
                        completoPare.set(true);
                    }
                }
                
                // Marcar tiempo de salida del stop
                tiempoSalidaDelStop.set(System.currentTimeMillis());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                vehiculo.stop();
                latch.countDown();
            }
        });

        vehiculoThread.start();

        // Esperar que complete el test o timeout
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Test no completó en tiempo esperado");

        // Verificaciones
        assertTrue(llegOAlStop.get(), "Vehículo debería haber llegado al stop sign");
        assertTrue(completoPare.get(), "Vehículo debería haber entrado en fase de PARE OBLIGATORIO");
        
        if (tiempoLlegadaAlStop.get() > 0 && tiempoSalidaDelStop.get() > 0) {
            long tiempoEnPare = tiempoSalidaDelStop.get() - tiempoLlegadaAlStop.get();
            assertTrue(tiempoEnPare >= 1400, // 1.4 segundos mínimo (tolerancia)
                "Vehículo debería haber estado parado al menos 1.4 segundos, pero estuvo: " + tiempoEnPare + "ms");
            
            System.out.println("✅ Vehículo realizó pare obligatorio correctamente: " + tiempoEnPare + "ms");
        }
    }

    @Test
    @DisplayName("Segundo vehículo debe esperar hasta que el primero complete su cruce")
    void testOrdenConPareObligatorio() throws InterruptedException {
        // Crear primer vehículo (izquierda -> derecha)
        Vehiculo vehiculo1 = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.IZQUIERDA,
            Direccion.derecha,
            cruceModel,
            50.0,
            295.0
        );

        // Crear segundo vehículo (derecha -> izquierda) un poco después
        Thread.sleep(100); // Asegurar orden de creación
        Vehiculo vehiculo2 = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.DERECHA,
            Direccion.izquierda,
            cruceModel,
            750.0,
            295.0
        );

        // Variables para trackear el orden
        AtomicBoolean vehiculo1Cruzo = new AtomicBoolean(false);
        AtomicBoolean vehiculo2Cruzo = new AtomicBoolean(false);
        AtomicLong tiempoVehiculo1Cruzo = new AtomicLong(0);
        AtomicLong tiempoVehiculo2Cruzo = new AtomicLong(0);

        // Ejecutar ambos vehículos
        Thread thread1 = new Thread(() -> {
            try {
                new Thread(vehiculo1).start();
                
                // Esperar hasta que cruce el centro
                while (vehiculo1.isRunning() && vehiculo1.getPosX() < 400.0) {
                    Thread.sleep(50);
                }
                
                vehiculo1Cruzo.set(true);
                tiempoVehiculo1Cruzo.set(System.currentTimeMillis());
                
                // Dejar que termine de cruzar
                Thread.sleep(2000);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                vehiculo1.stop();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                new Thread(vehiculo2).start();
                
                // Esperar hasta que cruce el centro
                while (vehiculo2.isRunning() && vehiculo2.getPosX() > 400.0) {
                    Thread.sleep(50);
                }
                
                vehiculo2Cruzo.set(true);
                tiempoVehiculo2Cruzo.set(System.currentTimeMillis());
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                vehiculo2.stop();
            }
        });

        thread1.start();
        thread2.start();

        // Esperar que ambos terminen
        thread1.join(10000);
        thread2.join(10000);

        // Verificaciones
        assertTrue(vehiculo1Cruzo.get(), "Primer vehículo debería haber cruzado");
        assertTrue(vehiculo2Cruzo.get(), "Segundo vehículo debería haber cruzado");
        
        if (tiempoVehiculo1Cruzo.get() > 0 && tiempoVehiculo2Cruzo.get() > 0) {
            assertTrue(tiempoVehiculo1Cruzo.get() < tiempoVehiculo2Cruzo.get(),
                "Primer vehículo creado debería haber cruzado antes que el segundo");
            
            System.out.println("✅ Orden de cruce respetado correctamente");
        }
    }
} 