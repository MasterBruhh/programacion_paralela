package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.SimulationModel;
import edu.pucmm.model.TipoVehiculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test simple para debuggear el pare obligatorio
 */
class DebugPareTest {

    private SimulationModel baseModel;
    private CruceSimulationModel cruceModel;

    @BeforeEach
    void setUp() {
        baseModel = new SimulationModel();
        cruceModel = new CruceSimulationModel(baseModel, "debug-cruce");
    }

    @Test
    @DisplayName("Debug: Verificar que el vehículo se crea correctamente")
    void testCreacionVehiculo() {
        // Crear vehículo
        Vehiculo vehiculo = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.IZQUIERDA,
            Direccion.derecha,
            cruceModel,
            50.0,
            295.0
        );

        // Verificaciones básicas
        assertNotNull(vehiculo);
        assertEquals(TipoVehiculo.normal, vehiculo.getTipo());
        assertEquals(PuntoSalida.IZQUIERDA, vehiculo.getPuntoSalida());
        assertEquals(50.0, vehiculo.getPosX());
        assertEquals(295.0, vehiculo.getPosY());
        
        System.out.println("✅ Vehículo creado correctamente: " + vehiculo.getId());
        System.out.println("   Posición inicial: (" + vehiculo.getPosX() + ", " + vehiculo.getPosY() + ")");
        System.out.println("   Timestamp creación: " + vehiculo.getTimestampCreacion());
    }

    @Test
    @DisplayName("Debug: Verificar que el vehículo se acerca al stop sign")
    void testAcercamientoAlStop() throws InterruptedException {
        // Crear vehículo
        Vehiculo vehiculo = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.IZQUIERDA,
            Direccion.derecha,
            cruceModel,
            50.0,
            295.0
        );

        // Ejecutar por un tiempo corto
        Thread vehiculoThread = new Thread(vehiculo);
        vehiculoThread.start();
        
        // Esperar un poco para que se mueva
        Thread.sleep(3000);
        
        // Verificar que se movió
        assertTrue(vehiculo.getPosX() > 50.0, "Vehículo debería haberse movido desde posición inicial");
        
        System.out.println("✅ Vehículo se movió correctamente");
        System.out.println("   Posición inicial: (50.0, 295.0)");
        System.out.println("   Posición actual: (" + vehiculo.getPosX() + ", " + vehiculo.getPosY() + ")");
        System.out.println("   Fase actual: " + vehiculo.faseMovimiento);
        
        // Detener vehículo
        vehiculo.stop();
        vehiculoThread.join(1000);
    }

    @Test
    @DisplayName("Debug: Verificar detección de proximidad a intersección")
    void testProximidadInterseccion() {
        // Crear vehículo
        Vehiculo vehiculo = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.IZQUIERDA,
            Direccion.derecha,
            cruceModel,
            50.0,
            295.0
        );

        // Probar diferentes posiciones
        double[] posicionesTest = {50.0, 200.0, 300.0, 330.0, 340.0, 350.0};
        
        for (double posX : posicionesTest) {
            boolean estaCerca = cruceModel.estaCercaDeInterseccion(vehiculo.getId(), posX, 295.0);
            System.out.println("Posición X=" + posX + " -> cerca de intersección: " + estaCerca);
        }
        
        // Verificar que detecta correctamente la proximidad
        assertTrue(cruceModel.estaCercaDeInterseccion(vehiculo.getId(), 330.0, 295.0),
            "Debería detectar proximidad a intersección en posición (330, 295)");
    }

    @Test
    @DisplayName("Debug: Probar pare obligatorio con vehículo cerca del stop")
    void testPareObligatorioConVehiculoCercano() throws InterruptedException {
        // Crear vehículo MUY CERCA del stop sign
        Vehiculo vehiculo = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.IZQUIERDA,
            Direccion.derecha,
            cruceModel,
            320.0,  // Muy cerca del stop sign en X=330
            295.0
        );

        System.out.println("🚗 Vehículo creado cerca del stop: " + vehiculo.getId());
        System.out.println("   Posición inicial: (" + vehiculo.getPosX() + ", " + vehiculo.getPosY() + ")");
        
        // Verificar que está cerca de la intersección
        boolean estaCerca = cruceModel.estaCercaDeInterseccion(vehiculo.getId(), vehiculo.getPosX(), vehiculo.getPosY());
        System.out.println("   Está cerca de intersección: " + estaCerca);

        // Ejecutar vehículo
        Thread vehiculoThread = new Thread(vehiculo);
        vehiculoThread.start();
        
        // Monitorear por varios segundos
        for (int i = 0; i < 10; i++) {
            Thread.sleep(500);
            System.out.println("   Segundo " + (i+1) + ": Posición (" + 
                String.format("%.2f", vehiculo.getPosX()) + ", " + 
                String.format("%.2f", vehiculo.getPosY()) + ") - Fase: " + vehiculo.faseMovimiento);
            
            // Si entra en pare obligatorio, registrarlo
            if (vehiculo.faseMovimiento == Vehiculo.FaseMovimiento.EN_PARE_OBLIGATORIO) {
                System.out.println("   🛑 ¡PARE OBLIGATORIO DETECTADO!");
                break;
            }
        }
        
        // Detener vehículo
        vehiculo.stop();
        vehiculoThread.join(1000);
        
        System.out.println("✅ Test completado - Fase final: " + vehiculo.faseMovimiento);
    }

    @Test
    @DisplayName("Debug: Probar pare obligatorio con vehículo EXACTAMENTE en el stop sign")
    void testPareObligatorioEnStopExacto() throws InterruptedException {
        // Crear vehículo EXACTAMENTE en el stop sign OESTE (340, 295)
        Vehiculo vehiculo = VehiculoFactory.createConfiguredVehicle(
            TipoVehiculo.normal,
            PuntoSalida.IZQUIERDA,
            Direccion.derecha,
            cruceModel,
            340.0,  // EXACTAMENTE en el stop sign OESTE
            295.0
        );

        System.out.println("🚗 Vehículo creado EXACTAMENTE en stop sign: " + vehiculo.getId());
        System.out.println("   Posición inicial: (" + vehiculo.getPosX() + ", " + vehiculo.getPosY() + ")");
        
        // Verificar que está cerca de la intersección
        boolean estaCerca = cruceModel.estaCercaDeInterseccion(vehiculo.getId(), vehiculo.getPosX(), vehiculo.getPosY());
        System.out.println("   Está cerca de intersección: " + estaCerca);

        // Ejecutar vehículo
        Thread vehiculoThread = new Thread(vehiculo);
        vehiculoThread.start();
        
        boolean pareDetectado = false;
        
        // Monitorear por varios segundos
        for (int i = 0; i < 20; i++) {
            Thread.sleep(300);
            System.out.println("   Segundo " + String.format("%.1f", (i+1) * 0.3) + ": Posición (" + 
                String.format("%.2f", vehiculo.getPosX()) + ", " + 
                String.format("%.2f", vehiculo.getPosY()) + ") - Fase: " + vehiculo.faseMovimiento);
            
            // Si entra en pare obligatorio, registrarlo
            if (vehiculo.faseMovimiento == Vehiculo.FaseMovimiento.EN_PARE_OBLIGATORIO) {
                System.out.println("   🛑 ¡PARE OBLIGATORIO DETECTADO!");
                pareDetectado = true;
                break;
            }
            
            // Si entra en otras fases relacionadas con el cruce
            if (vehiculo.faseMovimiento == Vehiculo.FaseMovimiento.EN_COLA ||
                vehiculo.faseMovimiento == Vehiculo.FaseMovimiento.CRUZANDO) {
                System.out.println("   📍 Fase de cruce detectada: " + vehiculo.faseMovimiento);
            }
        }
        
        // Detener vehículo
        vehiculo.stop();
        vehiculoThread.join(1000);
        
        System.out.println("✅ Test completado - Fase final: " + vehiculo.faseMovimiento);
        System.out.println("   Pare obligatorio detectado: " + pareDetectado);
        
        // Asegurar que se detectó el pare obligatorio
        assertTrue(pareDetectado, "El vehículo debería haber entrado en PARE OBLIGATORIO");
    }
} 