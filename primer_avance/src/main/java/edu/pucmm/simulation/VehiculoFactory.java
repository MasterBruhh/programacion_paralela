package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory para crear vehículos con parámetros aleatorios.
 * Implementa el patrón Factory Method para la creación de agentes vehículo.
 */
public class VehiculoFactory {
    
    private static final AtomicInteger vehicleIdCounter = new AtomicInteger(1);
    
    // rangos de posición para generación aleatoria
    private static final double MIN_POS = 0.0;
    private static final double MAX_POS = 100.0;
    
    // probabilidades de tipo de vehículo
    private static final double EMERGENCY_PROBABILITY = 0.1; // 10% emergencia, 90% normal
    
    /**
     * Crea un vehículo con parámetros completamente aleatorios.
     */
    public static Vehiculo createRandomVehicle(ISimulationModel simulationModel) {
        String id = generateVehicleId();
        double posX = generateRandomPosition();
        double posY = generateRandomPosition();
        Direccion direccion = generateRandomDirection();
        TipoVehiculo tipo = generateRandomType();
        
        return createVehicle(id, tipo, posX, posY, direccion, simulationModel);
    }
    
    /**
     * Crea un vehículo de tipo específico con posición aleatoria.
     */
    public static Vehiculo createVehicle(TipoVehiculo tipo, ISimulationModel simulationModel) {
        String id = generateVehicleId();
        double posX = generateRandomPosition();
        double posY = generateRandomPosition();
        Direccion direccion = generateRandomDirection();
        
        return createVehicle(id, tipo, posX, posY, direccion, simulationModel);
    }
    
    /**
     * Crea un vehículo con parámetros específicos.
     */
    public static Vehiculo createVehicle(String id, TipoVehiculo tipo, double posX, double posY, 
                                       Direccion direccion, ISimulationModel simulationModel) {
        return switch (tipo) {
            case normal -> new VehiculoNormal(id, posX, posY, direccion, simulationModel);
            case emergencia -> new VehiculoEmergencia(id, posX, posY, direccion, simulationModel);
        };
    }
    
    /**
     * Crea múltiples vehículos aleatorios.
     */
    public static Vehiculo[] createMultipleVehicles(int count, ISimulationModel simulationModel) {
        Vehiculo[] vehiculos = new Vehiculo[count];
        for (int i = 0; i < count; i++) {
            vehiculos[i] = createRandomVehicle(simulationModel);
        }
        return vehiculos;
    }
    
    /**
     * Crea una flota con proporción específica de vehículos de emergencia.
     */
    public static Vehiculo[] createFleet(int totalVehicles, double emergencyRatio, 
                                       ISimulationModel simulationModel) {
        Vehiculo[] vehiculos = new Vehiculo[totalVehicles];
        int emergencyCount = (int) (totalVehicles * emergencyRatio);
        
        // crear vehículos de emergencia
        for (int i = 0; i < emergencyCount; i++) {
            vehiculos[i] = createVehicle(TipoVehiculo.emergencia, simulationModel);
        }
        
        // crear vehículos normales
        for (int i = emergencyCount; i < totalVehicles; i++) {
            vehiculos[i] = createVehicle(TipoVehiculo.normal, simulationModel);
        }
        
        // mezclar el array para distribución aleatoria
        shuffleArray(vehiculos);
        return vehiculos;
    }
    
    // métodos auxiliares de generación
    private static String generateVehicleId() {
        return "VEH-" + String.format("%04d", vehicleIdCounter.getAndIncrement());
    }
    
    private static double generateRandomPosition() {
        return ThreadLocalRandom.current().nextDouble(MIN_POS, MAX_POS);
    }
    
    private static Direccion generateRandomDirection() {
        Direccion[] direcciones = Direccion.values();
        return direcciones[ThreadLocalRandom.current().nextInt(direcciones.length)];
    }
    
    private static TipoVehiculo generateRandomType() {
        return ThreadLocalRandom.current().nextDouble() < EMERGENCY_PROBABILITY ? 
               TipoVehiculo.emergencia : TipoVehiculo.normal;
    }
    
    /**
     * Mezcla un array usando Fisher-Yates shuffle.
     */
    private static void shuffleArray(Vehiculo[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = ThreadLocalRandom.current().nextInt(i + 1);
            Vehiculo temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }
    
    /**
     * Reinicia el contador de IDs (útil para pruebas).
     */
    public static void resetIdCounter() {
        vehicleIdCounter.set(1);
    }
} 