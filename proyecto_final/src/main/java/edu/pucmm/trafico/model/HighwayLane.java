package edu.pucmm.trafico.model;

public enum HighwayLane {
    NORTH_LEFT(355, 0, "Norte-Izquierdo", 1, true),
    NORTH_CENTER(385, 0, "Norte-Centro", 2, true),
    NORTH_RIGHT(415, 0, "Norte-Derecho", 3, true),
    SOUTH_LEFT(445, 768, "Sur-Izquierdo", 1, false),
    SOUTH_CENTER(475, 768, "Sur-Centro", 2, false),
    SOUTH_RIGHT(505, 768, "Sur-Derecho", 3, false);
    
    private final double startX;
    private final double startY;
    private final String description;
    private final int laneNumber;
    private final boolean northbound;
    
    HighwayLane(double startX, double startY, String description, int laneNumber, boolean northbound) {
        this.startX = startX;
        this.startY = startY;
        this.description = description;
        this.laneNumber = laneNumber;
        this.northbound = northbound;
    }
    
    public double getStartX() { 
        return startX; 
    }
    
    public double getStartY() { 
        return startY; 
    }
    
    public String getDescription() { 
        return description; 
    }
    
    public int getLaneNumber() { 
        return laneNumber; 
    }
    
    public boolean isNorthbound() { 
        return northbound; 
    }
    
    public boolean isSouthbound() { 
        return !northbound; 
    }
    
    public boolean isLeftLane() { 
        return laneNumber == 1; 
    }
    
    public double[] getStopPosition(int intersectionIndex) {
        double[] intersectionYs = {260, 460, 660};
        
        if (intersectionIndex < 0 || intersectionIndex >= 3) {
            return new double[]{startX, 400};
        }
        
        double y = intersectionYs[intersectionIndex];
        
        if (isNorthbound()) {
            y -= 50;
        } else {
            y += 50;
        }
        
        return new double[]{startX, y};
    }
}