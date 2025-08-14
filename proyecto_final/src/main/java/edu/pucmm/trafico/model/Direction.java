package edu.pucmm.trafico.model;

public enum Direction {
    STRAIGHT("Recto"),
    LEFT("Izquierda"),
    RIGHT("Derecha"),
    U_TURN("Vuelta en U");
    
    private final String description;
    
    Direction(String description) {
        this.description = description;
    }
    
    public String getDescription() { 
        return description; 
    }
}