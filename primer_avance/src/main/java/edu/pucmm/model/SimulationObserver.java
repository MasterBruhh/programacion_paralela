package edu.pucmm.model;

import java.util.Map;

/**
 * Interface para observar cambios en el estado de la simulación.
 * Implementa el patrón observer para notificar cambios de estado.
 */
public interface SimulationObserver {
    
    /**
     * Método llamado cuando cambia el estado de la simulación.
     * 
     * @param snapshot instantánea inmutable del estado actual de todos los vehículos
     */
    void onStateChange(Map<String, VehiculoState> snapshot);
} 