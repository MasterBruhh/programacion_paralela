package edu.pucmm.simulation;

import edu.pucmm.model.VehiculoState;

/**
 * Interface que define el contrato para el modelo de simulación.
 * Permite a los agentes vehículo publicar sus estados.
 */
public interface ISimulationModel {
    
    /**
     * Publica el estado actual de un vehículo al modelo central.
     * 
     * @param estado el estado inmutable del vehículo
     */
    void publishState(VehiculoState estado);
    
    /**
     * Verifica si un vehículo puede avanzar a una posición específica.
     * Implementa la lógica de prevención de colisiones.
     * 
     * @param vehiculoId id del vehículo que quiere moverse
     * @param nextX próxima posición x
     * @param nextY próxima posición y
     * @return true si puede avanzar, false si hay obstáculo
     */
    boolean puedeAvanzar(String vehiculoId, double nextX, double nextY);
}
