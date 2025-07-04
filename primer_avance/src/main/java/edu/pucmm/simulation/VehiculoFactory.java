package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fábrica de vehículos basada en configuración explícita del usuario (sin aleatoriedad).
 */
public class VehiculoFactory {

    private static final AtomicInteger vehicleIdCounter = new AtomicInteger(1);

    /**
     * Crea un vehículo basado en parámetros seleccionados por el usuario.
     *
     * @param tipo               Tipo de vehículo (normal o emergencia)
     * @param salida             Punto de salida (ARRIBA, ABAJO, etc.)
     * @param direccion          Dirección deseada (recto, izquierda, derecha, vuelta_u)
     * @param simulationModel    Modelo de simulación (implementa ISimulationModel)
     * @return Vehiculo Runnable listo para ejecutarse
     */
    public static Vehiculo createConfiguredVehicle(
            TipoVehiculo tipo,
            PuntoSalida salida,
            Direccion direccion,
            ISimulationModel simulationModel,
            double posX,
            double posY) {

        String id = generateVehicleId();

        return switch (tipo) {
            case normal -> new VehiculoNormal(id, posX, posY, direccion, simulationModel, salida);
            case emergencia -> new VehiculoEmergencia(id, posX, posY, direccion, simulationModel, salida);
        };
    }

    /**
     * Genera un ID único para cada vehículo.
     */
    private static String generateVehicleId() {
        return "VEH-" + String.format("%04d", vehicleIdCounter.getAndIncrement());
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
    }
}
