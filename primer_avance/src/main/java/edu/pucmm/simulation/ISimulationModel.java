package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;
import edu.pucmm.model.VehiculoState;

/**
 * Interface que define el contrato para el modelo de simulación.
 * Permite a los agentes vehículo publicar sus estados e interactuar con el cruce.
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
    
    /**
     * Solicita cruzar una intersección del cruce principal.
     * 
     * @param vehiculoId ID del vehículo
     * @param tipo tipo del vehículo (normal o emergencia)
     * @param vehiculoPosX posición X actual del vehículo
     * @param vehiculoPosY posición Y actual del vehículo
     * @throws InterruptedException si el hilo es interrumpido
     */
    void solicitarCruceInterseccion(String vehiculoId, TipoVehiculo tipo, 
                                   double vehiculoPosX, double vehiculoPosY) throws InterruptedException;
    
    /**
     * Verifica si el vehículo está cerca de una intersección y necesita solicitar cruce.
     * 
     * @param vehiculoId ID del vehículo
     * @param posX posición X del vehículo
     * @param posY posición Y del vehículo
     * @return true si está cerca de una intersección, false en caso contrario
     */
    boolean estaCercaDeInterseccion(String vehiculoId, double posX, double posY);
}
