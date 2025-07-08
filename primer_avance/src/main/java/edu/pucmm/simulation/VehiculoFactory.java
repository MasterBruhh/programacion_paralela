package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fábrica de vehículos basada en configuración explícita del usuario.
 * Mantiene el orden de creación para implementar FIFO por creación.
 */
public class VehiculoFactory {

    private static final AtomicInteger vehicleIdCounter = new AtomicInteger(1);
    private static final AtomicLong creationTimestamp = new AtomicLong(System.currentTimeMillis());

    /**
     * Crea un vehículo basado en parámetros seleccionados por el usuario.
     * El timestamp de creación se incrementa automáticamente para garantizar orden.
     *
     * @param tipo               Tipo de vehículo (normal o emergencia)
     * @param salida             Punto de salida (ARRIBA, ABAJO, etc.)
     * @param direccion          Dirección deseada (recto, izquierda, derecha, vuelta_u)
     * @param simulationModel    Modelo de simulación (implementa ISimulationModel)
     * @return Vehiculo Runnable listo para ejecutarse con timestamp de creación
     */
    public static Vehiculo createConfiguredVehicle(
            TipoVehiculo tipo,
            PuntoSalida salida,
            Direccion direccion,
            ISimulationModel simulationModel,
            double posX,
            double posY) {

        String id = generateVehicleId(tipo);
        long timestamp = getNextCreationTimestamp();

        Vehiculo vehiculo = switch (tipo) {
            case normal -> new VehiculoNormal(id, posX, posY, direccion, simulationModel, salida, timestamp);
            case emergencia -> new VehiculoEmergencia(id, posX, posY, direccion, simulationModel, salida, timestamp);
        };
        
        return vehiculo;
    }

    /**
     * Genera un ID único para cada vehículo según su tipo.
     */
    private static String generateVehicleId(TipoVehiculo tipo) {
        if (tipo == TipoVehiculo.emergencia) {
            return "EMG-" + String.format("%04d", vehicleIdCounter.getAndIncrement());
        } else {
            return "VEH-" + String.format("%04d", vehicleIdCounter.getAndIncrement());
        }
    }
    
    /**
     * Obtiene el siguiente timestamp de creación.
     * Se incrementa en 1ms para garantizar orden único incluso con creación rápida.
     */
    private static long getNextCreationTimestamp() {
        return creationTimestamp.getAndIncrement();
    }

    /**
     * Devuelve las coordenadas de inicio según el punto de salida.
     */
    public static double[] getStartingCoordinates(PuntoSalida salida) {
        return switch (salida) {
            case ARRIBA -> new double[]{375, 10};      // Entra desde arriba
            case ABAJO -> new double[]{420, 590};     // Entra desde abajo
            case IZQUIERDA -> new double[]{10, 320};   // Entra desde izquierda
            case DERECHA -> new double[]{790, 270};   // Entra desde derecha
        };
    }

    /**
     * Reinicia el contador de IDs (útil para pruebas).
     */
    public static void resetIdCounter() {
        vehicleIdCounter.set(1);
        creationTimestamp.set(System.currentTimeMillis());
    }
}
