package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;
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
 * Test específico para verificar que se respeta el orden de creación global
 * de vehículos en el cruce, independientemente de la dirección de entrada.
 */
class OrdenCreacionTest {

    private SimulationModel baseModel;
    private CruceSimulationModel cruceModel;

    @BeforeEach
    void setUp() {
        baseModel = new SimulationModel();
        cruceModel = new CruceSimulationModel(baseModel, "TEST-ORDEN-CREACION");
        VehiculoFactory.resetIdCounter();
    }

    @Test
    @DisplayName("Primer vehículo creado debe cruzar antes que el segundo, independientemente de la dirección")
    void testOrdenCreacionGlobal() throws InterruptedException {
        // Crear dos vehículos con timestamps específicos para garantizar orden
        long timestamp1 = System.currentTimeMillis();
        long timestamp2 = timestamp1 + 1; // 1ms después

        // Primer vehículo: IZQUIERDA → DERECHA (creado primero)
        VehiculoNormal vehiculo1 = new VehiculoNormal(
            "PRIMERO", 
            10.0, 320.0, // desde izquierda
            Direccion.derecha, 
            cruceModel, 
            PuntoSalida.IZQUIERDA,
            timestamp1
        );

        // Segundo vehículo: DERECHA → IZQUIERDA (creado después)
        VehiculoNormal vehiculo2 = new VehiculoNormal(
            "SEGUNDO", 
            790.0, 270.0, // desde derecha
            Direccion.izquierda, 
            cruceModel, 
            PuntoSalida.DERECHA,
            timestamp2
        );

        // Lista para rastrear el orden de cruce
        List<String> ordenCruce = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger vehiculosQueTerminaron = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(2);

        // Observador para capturar cuando los vehículos cruzan
        baseModel.addObserver(snapshot -> {
            // Detectar cuando un vehículo está en el área del cruce
            snapshot.values().forEach(vehiculo -> {
                double posX = vehiculo.posX();
                double posY = vehiculo.posY();
                
                // Área del cruce: 355-445 X, 250-340 Y
                if (posX >= 355 && posX <= 445 && posY >= 250 && posY <= 340) {
                    if (!ordenCruce.contains(vehiculo.id())) {
                        ordenCruce.add(vehiculo.id());
                        System.out.println("🚗 Vehículo " + vehiculo.id() + " está cruzando");
                    }
                }
            });
        });

        // Ejecutar vehículos en hilos separados
        Thread hilo1 = new Thread(() -> {
            try {
                vehiculo1.run();
            } finally {
                vehiculosQueTerminaron.incrementAndGet();
                latch.countDown();
            }
        });

        Thread hilo2 = new Thread(() -> {
            try {
                vehiculo2.run();
            } finally {
                vehiculosQueTerminaron.incrementAndGet();
                latch.countDown();
            }
        });

        // Iniciar ambos vehículos simultáneamente
        hilo1.start();
        hilo2.start();

        // Esperar un tiempo para que se posicionen y intenten cruzar
        Thread.sleep(10000); // 10 segundos

        // Detener vehículos
        vehiculo1.stop();
        vehiculo2.stop();

        // Esperar a que terminen
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Los vehículos deberían terminar");

        // Verificar el orden de cruce
        System.out.println("Orden de cruce observado: " + ordenCruce);
        System.out.println("Timestamp vehiculo1 (PRIMERO): " + timestamp1);
        System.out.println("Timestamp vehiculo2 (SEGUNDO): " + timestamp2);

        // El primer vehículo creado debe cruzar primero
        if (ordenCruce.size() >= 1) {
            assertEquals("PRIMERO", ordenCruce.get(0), 
                        "El primer vehículo creado debe cruzar primero");
        }

        if (ordenCruce.size() >= 2) {
            assertEquals("SEGUNDO", ordenCruce.get(1), 
                        "El segundo vehículo creado debe cruzar segundo");
        }

        // Verificar que ambos vehículos intentaron cruzar
        assertTrue(ordenCruce.size() > 0, "Al menos un vehículo debería haber cruzado");
    }

    @Test
    @DisplayName("Coordinador global debe mantener orden correcto con múltiples vehículos")
    void testCoordinadorGlobalMultiplesVehiculos() {
        CruceCoordinador coordinador = new CruceCoordinador("TEST-COORDINADOR");
        
        // Registrar vehículos en orden específico
        long baseTime = System.currentTimeMillis();
        
        coordinador.registrarVehiculo("VEH-001", baseTime + 10, TipoVehiculo.normal, CruceManager.DireccionCruce.NORTE);
        coordinador.registrarVehiculo("VEH-002", baseTime + 20, TipoVehiculo.normal, CruceManager.DireccionCruce.SUR);
        coordinador.registrarVehiculo("VEH-003", baseTime + 5, TipoVehiculo.normal, CruceManager.DireccionCruce.ESTE); // Más antiguo
        coordinador.registrarVehiculo("VEH-004", baseTime + 30, TipoVehiculo.normal, CruceManager.DireccionCruce.OESTE);

        // Verificar que el orden es correcto (por timestamp de creación)
        assertEquals(4, coordinador.getVehiculosEnColaGlobal());
        
        // El primero en orden debe ser VEH-003 (timestamp más antiguo)
        CruceCoordinador.VehiculoEnOrdenGlobal siguiente = coordinador.getSiguienteEnOrden();
        assertNotNull(siguiente);
        assertEquals("VEH-003", siguiente.vehiculoId());
        
        // Verificar que solo VEH-003 puede proceder
        assertTrue(coordinador.puedeProcedeSegunOrdenGlobal("VEH-003"));
        assertFalse(coordinador.puedeProcedeSegunOrdenGlobal("VEH-001"));
        assertFalse(coordinador.puedeProcedeSegunOrdenGlobal("VEH-002"));
        assertFalse(coordinador.puedeProcedeSegunOrdenGlobal("VEH-004"));
    }

    @Test
    @DisplayName("Vehículos de emergencia deben tener prioridad pero respetar orden entre ellos")
    void testPrioridadEmergenciaConOrden() {
        CruceCoordinador coordinador = new CruceCoordinador("TEST-EMERGENCIA");
        
        long baseTime = System.currentTimeMillis();
        
        // Registrar vehículos normales y de emergencia
        coordinador.registrarVehiculo("NORMAL-001", baseTime + 10, TipoVehiculo.normal, CruceManager.DireccionCruce.NORTE);
        coordinador.registrarVehiculo("EMG-001", baseTime + 20, TipoVehiculo.emergencia, CruceManager.DireccionCruce.SUR);
        coordinador.registrarVehiculo("NORMAL-002", baseTime + 5, TipoVehiculo.normal, CruceManager.DireccionCruce.ESTE);
        coordinador.registrarVehiculo("EMG-002", baseTime + 15, TipoVehiculo.emergencia, CruceManager.DireccionCruce.OESTE); // Emergencia más antigua

        // El siguiente debe ser la emergencia más antigua (EMG-002)
        CruceCoordinador.VehiculoEnOrdenGlobal siguiente = coordinador.getSiguienteEnOrden();
        assertNotNull(siguiente);
        assertEquals("EMG-002", siguiente.vehiculoId());
        assertEquals(TipoVehiculo.emergencia, siguiente.tipo());
        
        // Solo EMG-002 puede proceder
        assertTrue(coordinador.puedeProcedeSegunOrdenGlobal("EMG-002"));
        assertFalse(coordinador.puedeProcedeSegunOrdenGlobal("EMG-001"));
        assertFalse(coordinador.puedeProcedeSegunOrdenGlobal("NORMAL-001"));
        assertFalse(coordinador.puedeProcedeSegunOrdenGlobal("NORMAL-002"));
    }
} 