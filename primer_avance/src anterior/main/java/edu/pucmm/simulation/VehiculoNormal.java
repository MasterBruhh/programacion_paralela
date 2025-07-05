package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementación de vehículo normal con reglas estándar de tráfico.
 * Respeta semáforos y restricciones de velocidad.
 */
public class VehiculoNormal extends Vehiculo {
    
    private static final double VELOCIDAD_MIN = 10.0;
    private static final double VELOCIDAD_MAX = 40.0;

    public VehiculoNormal(String id, double posX, double posY, Direccion direccion,
                          ISimulationModel simulationModel, PuntoSalida puntoSalida) {
        super(id, TipoVehiculo.normal, posX, posY, 
              generateRandomVelocity(), direccion, simulationModel,puntoSalida);
    }
    
    private static double generateRandomVelocity() {
        return ThreadLocalRandom.current().nextDouble(VELOCIDAD_MIN, VELOCIDAD_MAX);
    }
    
    @Override
    protected void executeTypeSpecificLogic() {
        adjustVelocity();
        if (!cruzariaLineaDeParada(posX, posY)) {
            CruceManager.DireccionCruce direccion = ((CruceSimulationModel) simulationModel)
                    .getCruceManager()
                    .determinarInterseccionMasCercana(posX, posY);
            ((CruceSimulationModel) simulationModel)
                    .getCruceManager()
                    .getInterseccionManager(direccion)
                    .removerVehiculoEnEspera(id);
        }
    }

    /**
     * Ajusta la velocidad basándose en condiciones del entorno.
     */
    private void adjustVelocity() {
        // simulación simple: reducir velocidad ocasionalmente (tráfico, semáforos, etc.)
        if (ThreadLocalRandom.current().nextDouble() < 0.05) { // 5% probabilidad
            // reducir velocidad temporalmente
            double factor = ThreadLocalRandom.current().nextDouble(0.5, 0.8);
            this.velocidad = Math.max(VELOCIDAD_MIN, this.velocidad * factor);
        } else if (ThreadLocalRandom.current().nextDouble() < 0.03) { // 3% probabilidad
            // acelerar gradualmente
            double factor = ThreadLocalRandom.current().nextDouble(1.1, 1.3);
            this.velocidad = Math.min(VELOCIDAD_MAX, this.velocidad * factor);
        }
    }
    
    @Override
    protected boolean puedeRealizarMovimiento(MovimientoInfo movimiento) {
        // vehículos normales respetan todas las restricciones
        return super.puedeRealizarMovimiento(movimiento) && respectsTrafficRules(movimiento);
    }
    
    /**
     * Verifica que el movimiento respete las reglas de tráfico.
     */
    private boolean respectsTrafficRules(MovimientoInfo movimiento) {
        // aquí se implementarían verificaciones adicionales:
        // - respeto de semáforos rojos
        // - límites de velocidad por zona
        // - señales de stop
        // por ahora, simplificamos retornando true
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("VehiculoNormal{id='%s', pos=(%.2f, %.2f), vel=%.2f, dir=%s}", 
                           id, posX, posY, velocidad, direccion);
    }
}
