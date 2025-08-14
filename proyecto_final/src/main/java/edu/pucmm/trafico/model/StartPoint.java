package edu.pucmm.trafico.model;

public enum StartPoint {
    NORTH(430, 10, "Norte"),
    SOUTH(430, 768, "Sur"),
    EAST(1160, 295, "Este"),
    WEST(0, 295, "Oeste");
    
    private final double x;
    private final double y;
    private final String description;
    
    StartPoint(double x, double y, String description) {
        this.x = x;
        this.y = y;
        this.description = description;
    }
    
    public double getX() { 
        return x; 
    }
    
    public double getY() { 
        return y; 
    }
    
    public String getDescription() { 
        return description; 
    }
    
    public double[] getStopCoordinates() {
        return switch (this) {
            case NORTH -> new double[]{430, 210};
            case SOUTH -> new double[]{430, 410};
            case EAST -> new double[]{540, 295};
            case WEST -> new double[]{320, 295};
        };
    }
    
    public double[] getExitCoordinates(Direction direction) {
        return switch (this) {
            case NORTH -> switch (direction) {
                case STRAIGHT -> new double[]{430, 768};
                case LEFT -> new double[]{0, 295};
                case RIGHT -> new double[]{1160, 295};
                case U_TURN -> new double[]{430, 10};
            };
            case SOUTH -> switch (direction) {
                case STRAIGHT -> new double[]{430, 10};
                case LEFT -> new double[]{1160, 295};
                case RIGHT -> new double[]{0, 295};
                case U_TURN -> new double[]{430, 768};
            };
            case EAST -> switch (direction) {
                case STRAIGHT -> new double[]{0, 295};
                case LEFT -> new double[]{430, 10};
                case RIGHT -> new double[]{430, 768};
                case U_TURN -> new double[]{1160, 295};
            };
            case WEST -> switch (direction) {
                case STRAIGHT -> new double[]{1160, 295};
                case LEFT -> new double[]{430, 768};
                case RIGHT -> new double[]{430, 10};
                case U_TURN -> new double[]{0, 295};
            };
        };
    }
}