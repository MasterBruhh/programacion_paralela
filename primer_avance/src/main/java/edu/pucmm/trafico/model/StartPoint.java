package edu.pucmm.trafico.model;

/**
 * Define los puntos de inicio para los vehículos.
 * Las coordenadas están ajustadas para que los vehículos aparezcan centrados en su carril correcto.
 */
public enum StartPoint {
    // Coordenadas ajustadas para posicionar vehículos en el carril correcto
    NORTH(380, 10),    // Carril derecho de la vía vertical que va de Norte a Sur
    SOUTH(420, 590),   // Carril derecho de la vía vertical que va de Sur a Norte
    EAST(790, 275),    // Carril derecho de la vía horizontal que va de Este a Oeste
    WEST(10, 315);     // Carril derecho de la vía horizontal que va de Oeste a Este

    private final double x;
    private final double y;

    StartPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    /**
     * Obtiene las coordenadas donde se encuentra la línea de pare para este punto de inicio.
     * @return Array [x, y] con la posición de la línea de pare para este punto de inicio
     */
    public double[] getStopCoordinates() {
        return switch (this) {
            case NORTH -> new double[]{380, 250}; // Línea superior de la intersección, carril derecho
            case SOUTH -> new double[]{420, 340}; // Línea inferior de la intersección, carril derecho
            case EAST -> new double[]{445, 275};  // Línea derecha de la intersección, carril derecho
            case WEST -> new double[]{355, 315};  // Línea izquierda de la intersección, carril derecho
        };
    }

    /**
     * Obtiene las coordenadas de destino después de salir de la intersección.
     * @param direction La dirección de giro del vehículo
     * @return Array [x, y] con la posición final después de salir de la intersección
     */
    public double[] getExitCoordinates(Direction direction) {
        return switch (this) {
            case NORTH -> switch (direction) {
                case STRAIGHT -> new double[]{380, 590}; // Recto hacia el Sur, carril derecho
                case LEFT -> new double[]{790, 315};     // Izquierda hacia el Este, carril derecho (CORREGIDO)
                case RIGHT -> new double[]{10, 275};     // Derecha hacia el Oeste, carril derecho (CORREGIDO)
                case U_TURN -> new double[]{420, 10};   // U-turn hacia el Norte, carril derecho
            };
            case SOUTH -> switch (direction) {
                case STRAIGHT -> new double[]{420, 10};   // Recto hacia el Norte, carril derecho
                case LEFT -> new double[]{10, 275};      // Izquierda hacia el Oeste, carril derecho (CORREGIDO)
                case RIGHT -> new double[]{790, 315};    // Derecha hacia el Este, carril derecho (CORREGIDO)
                case U_TURN -> new double[]{380, 590};   // U-turn hacia el Sur, carril derecho
            };
            case EAST -> switch (direction) {
                case STRAIGHT -> new double[]{10, 275};   // Recto hacia el Oeste, carril derecho
                case LEFT -> new double[]{380, 590};     // Izquierda hacia el Sur, carril derecho
                case RIGHT -> new double[]{420, 10};     // Derecha hacia el Norte, carril derecho
                case U_TURN -> new double[]{790, 315};   // U-turn hacia el Este, carril derecho
            };
            case WEST -> switch (direction) {
                case STRAIGHT -> new double[]{790, 315}; // Recto hacia el Este, carril derecho
                case LEFT -> new double[]{420, 10};     // Izquierda hacia el Norte, carril derecho
                case RIGHT -> new double[]{380, 590};   // Derecha hacia el Sur, carril derecho
                case U_TURN -> new double[]{10, 275};   // U-turn hacia el Oeste, carril derecho
            };
        };
    }
}