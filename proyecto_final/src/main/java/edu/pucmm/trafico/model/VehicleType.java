package edu.pucmm.trafico.model;

/**
 * Define todos los tipos de vehículos con sus prioridades y características.
 */
public enum VehicleType {
    NORMAL("Normal", 1, 1.0),
    EMERGENCY("Emergencia", 10, 1.5),
    PUBLIC_TRANSPORT("Transporte Público", 5, 0.8),
    HEAVY("Pesado", 2, 0.6);
    
    private final String description;
    private final int priority;
    private final double speedMultiplier;
    
    VehicleType(String description, int priority, double speedMultiplier) {
        this.description = description;
        this.priority = priority;
        this.speedMultiplier = speedMultiplier;
    }
    
    /**
     * Obtiene la descripción del tipo
     */
    public String getDescription() { 
        return description; 
    }
    
    /**
     * Obtiene la prioridad (mayor número = mayor prioridad)
     */
    public int getPriority() { 
        return priority; 
    }
    
    /**
     * Obtiene el multiplicador de velocidad
     */
    public double getSpeedMultiplier() { 
        return speedMultiplier; 
    }
    
    /**
     * Verifica si este tipo tiene prioridad sobre otro
     */
    public boolean hasPriorityOver(VehicleType other) {
        return this.priority > other.priority;
    }
}