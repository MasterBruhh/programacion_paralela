package edu.pucmm.trafico.model;

public enum VehicleType {
    NORMAL("Normal", 5),
    EMERGENCY("Emergencia", 10),
    PUBLIC_TRANSPORT("Transporte Público", 7),
    HEAVY("Pesado", 3);
    
    private final String description;
    private final int priority;
    
    VehicleType(String description, int priority) {
        this.description = description;
        this.priority = priority;
    }
    
    public String getDescription() { 
        return description; 
    }
    
    public int getPriority() { 
        return priority; 
    }
}