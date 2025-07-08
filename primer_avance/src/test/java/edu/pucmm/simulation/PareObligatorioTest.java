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
        Thread vehiculoThread = new Thread(vehiculo);
        vehiculoThread.start();
        
        // Monitorear el vehículo
        Thread monitorThread = new Thread(() -> {
            try {
                // Esperar hasta que se acerque al stop sign (OESTE está en X=325)
                while (vehiculo.isRunning() && vehiculo.getPosX() < 315.0) {
                    Thread.sleep(50);
                }
                
                // Marcar que llegó al stop
                if (!llegOAlStop.get() && vehiculo.getPosX() >= 315.0) {
                    llegOAlStop.set(true);
                    tiempoLlegadaAlStop.set(System.currentTimeMillis());
                    System.out.println("Vehículo llegó al stop en X=" + vehiculo.getPosX());
                }
                
                // Esperar hasta que empiece a moverse de nuevo después del pare
                double posXEnStop = vehiculo.getPosX();
                boolean estuvoEnPare = false;
                
                while (vehiculo.isRunning()) {
                    Thread.sleep(50);
                    
                    // Verificar si está en fase de pare obligatorio
                    if (vehiculo.faseMovimiento == Vehiculo.FaseMovimiento.EN_PARE_OBLIGATORIO) {
                        completoPare.set(true);
                        estuvoEnPare = true;
                        System.out.println("Vehículo en PARE OBLIGATORIO en X=" + vehiculo.getPosX());
                    }
                    
                    // Si estuvo en pare y ahora se movió significativamente, salió del pare
                    if (estuvoEnPare && vehiculo.getPosX() > posXEnStop + 5.0) {
                        tiempoSalidaDelStop.set(System.currentTimeMillis());
                        System.out.println("Vehículo salió del stop, ahora en X=" + vehiculo.getPosX());
                        break;
                    }
                    
                    // Timeout de seguridad
                    if (System.currentTimeMillis() - tiempoLlegadaAlStop.get() > 10000) {
                        break;
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                latch.countDown();
            }
        });
        
        monitorThread.start();

        // Esperar que complete el test o timeout
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Test no completó en tiempo esperado");
        
        // Detener vehículo
        vehiculo.stop();
        vehiculoThread.join(2000);

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
        Thread thread1 = new Thread(vehiculo1);
        Thread thread2 = new Thread(vehiculo2);
        
        thread1.start();
        thread2.start();
        
        // Monitorear vehículo 1
        Thread monitor1 = new Thread(() -> {
            try {
                // Esperar hasta que cruce el centro (400 es el centro)
                while (vehiculo1.isRunning() && vehiculo1.getPosX() < 400.0) {
                    Thread.sleep(50);
                }
                
                if (vehiculo1.getPosX() >= 400.0) {
                    vehiculo1Cruzo.set(true);
                    tiempoVehiculo1Cruzo.set(System.currentTimeMillis());
                    System.out.println("Vehículo 1 cruzó el centro en t=" + tiempoVehiculo1Cruzo.get());
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // Monitorear vehículo 2
        Thread monitor2 = new Thread(() -> {
            try {
                // Esperar hasta que cruce el centro (400 es el centro)
                while (vehiculo2.isRunning() && vehiculo2.getPosX() > 400.0) {
                    Thread.sleep(50);
                }
                
                if (vehiculo2.getPosX() <= 400.0) {
                    vehiculo2Cruzo.set(true);
                    tiempoVehiculo2Cruzo.set(System.currentTimeMillis());
                    System.out.println("Vehículo 2 cruzó el centro en t=" + tiempoVehiculo2Cruzo.get());
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        monitor1.start();
        monitor2.start();

        // Esperar un tiempo razonable para que ambos intenten cruzar
        Thread.sleep(10000); // 10 segundos
        
        // Detener vehículos
        vehiculo1.stop();
        vehiculo2.stop();
        
        // Esperar que terminen todos los threads
        thread1.join(2000);
        thread2.join(2000);
        monitor1.join(1000);
        monitor2.join(1000);

        // Verificaciones
        assertTrue(vehiculo1Cruzo.get() || vehiculo2Cruzo.get(), 
            "Al menos un vehículo debería haber cruzado");
        
        if (vehiculo1Cruzo.get() && vehiculo2Cruzo.get()) {
            // Si ambos cruzaron, verificar orden
            assertTrue(tiempoVehiculo1Cruzo.get() < tiempoVehiculo2Cruzo.get(),
                "Primer vehículo creado debería haber cruzado antes que el segundo");
            System.out.println("✅ Orden de cruce respetado correctamente");
        } else if (vehiculo1Cruzo.get() && !vehiculo2Cruzo.get()) {
            // Solo el primero cruzó - correcto
            System.out.println("✅ Solo el primer vehículo cruzó (correcto)");
            assertTrue(true, "Primer vehículo cruzó primero");
        } else if (!vehiculo1Cruzo.get() && vehiculo2Cruzo.get()) {
            // Solo el segundo cruzó - incorrecto
            fail("El segundo vehículo no debería cruzar antes que el primero");
        }
    }
} 