package edu.pucmm.trafico.model;

public enum StartPoint {
    // Calles verticales: cada una es de un solo sentido
    // Calle izquierda (X=390..470) es sentido Sur (desde arriba hacia abajo)
    // Calle derecha (X=690..770) es sentido Norte (desde abajo hacia arriba)
    // Usamos un pequeño offset para no dibujar sobre la línea amarilla central
    NORTH_L(410, 10, "Norte"),      // Calle izquierda, 15px a la derecha del centro (430+15)
    SOUTH_L(450, 758, "Sur"),
    NORTH_D(705, 10, "Norte"),      // Calle izquierda, 15px a la derecha del centro (430+15)
    SOUTH_D(745, 758, "Sur"),    // Calle derecha, 15px a la izquierda del centro (730-15)
    EAST(1160, 260, "Este"),      // Borde derecho, centro de avenida superior
    WEST(0, 360, "Oeste");        // Borde izquierdo, centro de avenida inferior

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

    /**
     * Obtiene las coordenadas del punto de parada antes de la intersección
     */
    public double[] getStopCoordinates() {
        return switch (this) {
            case NORTH_L -> new double[]{410, 200};   // 10px antes de la avenida superior
            case SOUTH_L -> new double[]{450, 420};   // 10px después de la avenida inferior
            case NORTH_D -> new double[]{705, 200};
            case SOUTH_D -> new double[]{745, 420};
            case EAST -> new double[]{540, 295};    // No usado para autopista
            case WEST -> new double[]{320, 295};    // No usado para autopista
        };
    }

    /**
     * Obtiene las coordenadas de salida según la dirección del giro
     */
    public double[] getExitCoordinates(Direction direction) {
        return switch (this) {
            case NORTH_L -> switch (direction) {
                case STRAIGHT -> new double[]{410, 768};    // Continuar hacia el sur por la misma calle
                case RIGHT -> new double[]{10, 260};         // Salir por avenida superior oeste
                case LEFT -> new double[]{1150, 360};      // Salir por avenida inferior este
                case U_TURN -> new double[]{445, 10};       // Regresar al norte por la misma calle
            };
            case SOUTH_L -> switch (direction) {
                case STRAIGHT -> new double[]{460, 10};     // Continuar hacia el norte por la misma calle
                case RIGHT -> new double[]{1150, 360};       // Salir por avenida inferior este
                case LEFT -> new double[]{10, 260};        // Salir por avenida superior oeste
                case U_TURN -> new double[]{715, 758};      // Regresar al sur por la misma calle
            };
            case NORTH_D -> switch (direction) {
                case STRAIGHT -> new double[]{705, 768};    // Continuar hacia el sur por la misma calle
                case RIGHT -> new double[]{10, 260};         // Salir por avenida superior oeste
                case LEFT -> new double[]{1150, 360};      // Salir por avenida inferior este
                case U_TURN -> new double[]{445, 10};       // Regresar al norte por la misma calle
            };
            case SOUTH_D -> switch (direction) {
                case STRAIGHT -> new double[]{745, 10};     // Continuar hacia el norte por la misma calle
                case RIGHT -> new double[]{1150, 360};       // Salir por avenida inferior este
                case LEFT -> new double[]{10, 260};        // Salir por avenida superior oeste
                case U_TURN -> new double[]{715, 758};      // Regresar al sur por la misma calle
            };
            case EAST -> switch (direction) {
                // Los vehículos de autopista no usan estas coordenadas
                case STRAIGHT -> new double[]{0, 360};
                case LEFT -> new double[]{430, 10};
                case RIGHT -> new double[]{430, 758};
                case U_TURN -> new double[]{1160, 360};
            };
            case WEST -> switch (direction) {
                // Los vehículos de autopista no usan estas coordenadas
                case STRAIGHT -> new double[]{1160, 260};
                case LEFT -> new double[]{430, 758};
                case RIGHT -> new double[]{430, 10};
                case U_TURN -> new double[]{0, 260};
            };
        };
    }
}