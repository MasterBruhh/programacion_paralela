package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas comprehensivas para el sistema de vehículos como agentes.
 */
class VehiculoAgenteTest {

    private TestSimulationModel testModel;

    @BeforeEach
    void setUp() {
        testModel = new TestSimulationModel();
        VehiculoFactory.resetIdCounter();
    }

    @Test
    @DisplayName("vehículo normal debe funcionar como agente independiente")
    void testVehiculoNormalComoAgente() throws InterruptedException {
        // crear vehículo normal
        VehiculoNormal vehiculo = new VehiculoNormal("TEST-001", 10.0, 20.0,
                                                   Direccion.derecha, testModel, PuntoSalida.IZQUIERDA, 
                                                   System.currentTimeMillis());

        // verificar estado inicial
        assertEquals("TEST-001", vehiculo.getId());
        assertEquals(TipoVehiculo.normal, vehiculo.getTipo());
        assertEquals(10.0, vehiculo.getPosX());
        assertEquals(20.0, vehiculo.getPosY());
        assertFalse(vehiculo.isRunning());

        // ejecutar vehículo en hilo separado
        Thread vehiculoThread = new Thread(vehiculo);
        vehiculoThread.start();

        // esperar a que publique algunos estados
        assertTrue(testModel.waitForStates(3, 5000),
                  "debería publicar al menos 3 estados en 5 segundos");

        // verificar que está ejecutando
        assertTrue(vehiculo.isRunning());

        // detener vehículo
        vehiculo.stop();
        vehiculoThread.join(2000);

        // verificar que se detuvo
        assertFalse(vehiculo.isRunning());
        assertTrue(testModel.getPublishedStates().size() > 0);
    }

    @Test
    @DisplayName("vehículo de emergencia debe tener comportamiento especial")
    void testVehiculoEmergenciaComportamiento() throws InterruptedException {
        VehiculoEmergencia vehiculo = new VehiculoEmergencia("EMG-001", 0.0, 0.0,
                                                            Direccion.recto, testModel, PuntoSalida.ABAJO,
                                                            System.currentTimeMillis());

        assertEquals(TipoVehiculo.emergencia, vehiculo.getTipo());
        assertTrue(vehiculo.isSirenActive());

        Thread vehiculoThread = new Thread(vehiculo);
        vehiculoThread.start();

        // esperar a que publique estados
        assertTrue(testModel.waitForStates(2, 3000));

        // verificar que mantiene velocidad alta
        assertTrue(vehiculo.getVelocidad() >= 1.0,
                  "vehículo de emergencia debe tener velocidad mínima de 1.0");

        vehiculo.stop();
        vehiculoThread.join(2000);
    }

    @Test
    @DisplayName("factory debe crear vehículos configurados correctamente")
    void testVehiculoFactory() {
        // crear vehículo configurado
        Vehiculo vehiculo1 = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.ARRIBA,
            Direccion.recto,
            testModel,
            100.0,
            200.0
        );
        
        assertNotNull(vehiculo1);
        assertNotNull(vehiculo1.getId());
        assertTrue(vehiculo1.getId().startsWith("VEH-"));
        assertEquals(TipoVehiculo.normal, vehiculo1.getTipo());
        assertEquals(100.0, vehiculo1.getPosX());
        assertEquals(200.0, vehiculo1.getPosY());

        // crear vehículo de emergencia
        Vehiculo vehiculo2 = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.emergencia,
            PuntoSalida.ABAJO,
            Direccion.izquierda,
            testModel,
            300.0,
            400.0
        );
        
        assertEquals(TipoVehiculo.emergencia, vehiculo2.getTipo());
        assertEquals(300.0, vehiculo2.getPosX());
        assertEquals(400.0, vehiculo2.getPosY());
    }

    @Test
    @DisplayName("múltiples vehículos deben ejecutar en paralelo")
    void testEjecucionParalela() throws InterruptedException {
        int numVehiculos = 5;
        Vehiculo[] vehiculos = new Vehiculo[numVehiculos];
        Thread[] hilos = new Thread[numVehiculos];

        // crear vehículos manualmente
        for (int i = 0; i < numVehiculos; i++) {
            PuntoSalida[] puntos = PuntoSalida.values();
            TipoVehiculo tipo = (i % 2 == 0) ? TipoVehiculo.normal : TipoVehiculo.emergencia;
            vehiculos[i] = VehiculoFactory.createConfiguredVehicle(
                tipo,
                puntos[i % puntos.length],
                Direccion.recto,
                testModel,
                50.0 + i * 20,
                100.0 + i * 20
            );
        }

        // iniciar todos los vehículos
        for (int i = 0; i < numVehiculos; i++) {
            hilos[i] = new Thread(vehiculos[i]);
            hilos[i].start();
        }

        // esperar a que todos publiquen estados
        assertTrue(testModel.waitForStates(numVehiculos * 2, 8000),
                  "todos los vehículos deberían publicar estados");

        // verificar que todos están ejecutando
        for (Vehiculo vehiculo : vehiculos) {
            assertTrue(vehiculo.isRunning(),
                      "vehículo " + vehiculo.getId() + " debería estar ejecutando");
        }

        // detener todos
        for (Vehiculo vehiculo : vehiculos) {
            vehiculo.stop();
        }

        // esperar a que terminen
        for (Thread hilo : hilos) {
            hilo.join(2000);
        }
    }

    @Test
    @DisplayName("control de pausa y reanudación debe funcionar")
    void testControlPausaReanudacion() throws InterruptedException {
        VehiculoNormal vehiculo = new VehiculoNormal("PAUSE-TEST", 0.0, 0.0,
                                                   Direccion.derecha, testModel, PuntoSalida.DERECHA,
                                                   System.currentTimeMillis());

        Thread vehiculoThread = new Thread(vehiculo);
        vehiculoThread.start();

        // esperar estados iniciales
        assertTrue(testModel.waitForStates(2, 3000));
        int estadosAntesPausa = testModel.getPublishedStates().size();

        // pausar
        vehiculo.pause();
        assertTrue(vehiculo.isPaused());

        // esperar un poco y verificar que no publique más estados
        Thread.sleep(1000);
        int estadosDurantePausa = testModel.getPublishedStates().size();

        // reanudar
        vehiculo.resume();
        assertFalse(vehiculo.isPaused());

        // esperar más estados
        Thread.sleep(1000);
        int estadosDespuesReanudacion = testModel.getPublishedStates().size();

        // verificar que publicó menos estados durante la pausa
        assertTrue(estadosDespuesReanudacion > estadosDurantePausa,
                  "debería publicar más estados después de reanudar");

        vehiculo.stop();
        vehiculoThread.join(2000);
    }

    @Test
    @DisplayName("vehículos deben calcular movimiento correctamente")
    void testCalculoMovimiento() throws InterruptedException {
        VehiculoNormal vehiculo = new VehiculoNormal("MOV-TEST", 50.0, 50.0,
                                                   Direccion.derecha, testModel, PuntoSalida.ARRIBA,
                                                   System.currentTimeMillis());

        double posXInicial = vehiculo.getPosX();
        double posYInicial = vehiculo.getPosY();

        Thread vehiculoThread = new Thread(vehiculo);
        vehiculoThread.start();

        // esperar a que se mueva
        Thread.sleep(2000);

        // verificar que la posición cambió
        assertTrue(vehiculo.getPosX() != posXInicial || vehiculo.getPosY() != posYInicial,
                  "vehículo debería haberse movido");

        vehiculo.stop();
        vehiculoThread.join(2000);
    }

    /**
     * Implementación de prueba para ISimulationModel.
     */
    private static class TestSimulationModel implements ISimulationModel {
        private final List<VehiculoState> publishedStates = new CopyOnWriteArrayList<>();
        private final AtomicInteger stateCount = new AtomicInteger(0);

        @Override
        public void publishState(VehiculoState estado) {
            publishedStates.add(estado);
            stateCount.incrementAndGet();
        }

        @Override
        public boolean puedeAvanzar(String vehiculoId, double nextX, double nextY) {
            // permitir todos los movimientos para simplificar las pruebas
            return true;
        }

        @Override
        public void solicitarCruceInterseccion(String vehiculoId,
                                              edu.pucmm.model.TipoVehiculo tipo,
                                              double vehiculoPosX,
                                              double vehiculoPosY) throws InterruptedException {
            // implementación vacía para pruebas
        }

        @Override
        public boolean estaCercaDeInterseccion(String vehiculoId, double posX, double posY) {
            // en pruebas simples, no hay intersecciones
            return false;
        }

        public List<VehiculoState> getPublishedStates() {
            return List.copyOf(publishedStates);
        }

        public boolean waitForStates(int expectedCount, long timeoutMs) throws InterruptedException {
            long startTime = System.currentTimeMillis();
            while (stateCount.get() < expectedCount) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    return false;
                }
                Thread.sleep(100);
            }
            return true;
        }
    }
}
 