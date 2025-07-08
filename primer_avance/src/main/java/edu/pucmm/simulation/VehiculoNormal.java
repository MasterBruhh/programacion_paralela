package edu.pucmm.simulation;

import edu.pucmm.model.PuntoSalida;
import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Implementación de vehículo normal con reglas estándar de tráfico.
 * Respeta semáforos y restricciones de velocidad.
 */
public class VehiculoNormal extends Vehiculo {
    
    private static final double VELOCIDAD_UNIFICADA = 30.0; // misma velocidad para todos

    public VehiculoNormal(String id, double posX, double posY, Direccion direccion,
                          ISimulationModel simulationModel, PuntoSalida puntoSalida, long timestampCreacion) {
        super(id, TipoVehiculo.normal, posX, posY, 
              VELOCIDAD_UNIFICADA, direccion, simulationModel, puntoSalida, timestampCreacion);
    }
    
    private static double generateRandomVelocity() {
        return VELOCIDAD_UNIFICADA; // ya no es aleatoria
    }
    
    @Override
    protected void executeTypeSpecificLogic() {
        // La lógica de ajuste de velocidad ahora es manejada por ajustarVelocidadPreventiva().
        // La lógica anterior aquí reiniciaba la velocidad y removía incorrectamente
        // el vehículo de la cola de espera, causando el bug.
        // Dejar este método vacío asegura que los vehículos normales y de emergencia
        // sigan el mismo flujo de movimiento base.
    }

    /**
     * Ajusta la velocidad basándose en condiciones del entorno.
     */
    private void adjustVelocity() {
        // mantener velocidad constante - todos los vehículos tienen la misma velocidad
        this.velocidad = VELOCIDAD_UNIFICADA;
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
 