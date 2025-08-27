package edu.pucmm.trafico.model;

/**
 * Define los carriles de la autopista con posicionamiento preciso.
 * Cada avenida tiene exactamente 3 carriles con coordenadas Y específicas.
 */
public enum HighwayLane {
    // Avenida Superior (Y=210 a Y=310, va hacia la izquierda/oeste)
    // Dividimos 100px entre 3 carriles = ~33.33px por carril
    EAST_RIGHT(1020, 226.67, "Oeste-Izquierdo", 1, true),     // Y=210 + 16.67
    EAST_CENTER(1020, 260, "Oeste-Centro", 2, true),         // Y=210 + 50 (centro)
    EAST_LEFT(1020, 293.33, "Oeste-Derecho", 3, true),      // Y=210 + 83.33

    // Avenida Inferior (Y=310 a Y=410, va hacia la derecha/este)
    WEST_LEFT(140, 326.67, "Este-Izquierdo", 1, false),        // Y=310 + 16.67
    WEST_CENTER(140, 360, "Este-Centro", 2, false),            // Y=310 + 50 (centro)
    WEST_RIGHT(140, 393.33, "Este-Derecho", 3, false);         // Y=310 + 83.33

    private final double startX;
    private final double startY;
    private final String description;
    private final int laneNumber;
    private final boolean westbound; // true=hacia oeste (izquierda), false=hacia este (derecha)

    HighwayLane(double startX, double startY, String description,
                int laneNumber, boolean westbound) {
        this.startX = startX;
        this.startY = startY;
        this.description = description;
        this.laneNumber = laneNumber;
        this.westbound = westbound;
    }

    /**
     * Obtiene la posición X inicial del carril
     */
    public double getStartX() {
        return startX;
    }

    /**
     * Obtiene la posición Y inicial del carril (centro del carril)
     */
    public double getStartY() {
        return startY;
    }

    /**
     * Obtiene la descripción del carril
     */
    public String getDescription() {
        return description;
    }

    /**
     * Obtiene el número de carril (1=izquierdo, 2=centro, 3=derecho)
     */
    public int getLaneNumber() {
        return laneNumber;
    }

    /**
     * Verifica si es un carril hacia el oeste (izquierda)
     */
    public boolean isWestbound() {
        return westbound;
    }

    /**
     * Verifica si es un carril hacia el este (derecha)
     */
    public boolean isEastbound() {
        return !westbound;
    }

    /**
     * Para compatibilidad con código existente - mapea oeste como "norte"
     */
    @Deprecated
    public boolean isNorthbound() {
        return westbound;
    }

    /**
     * Verifica si es el carril izquierdo (solo para giros a la izquierda y vuelta en U)
     */
    public boolean isLeftLane() {
        return laneNumber == 1;
    }

    /**
     * Verifica si es el carril central
     */
    public boolean isCenterLane() {
        return laneNumber == 2;
    }

    /**
     * Verifica si es el carril derecho
     */
    public boolean isRightLane() {
        return laneNumber == 3;
    }

    /**
     * Obtiene un carril aleatorio para una dirección específica
     * Respeta las reglas del carril izquierdo para giros
     */
    public static HighwayLane getRandomLane(boolean goingWest, Direction direction) {
        java.util.Random random = new java.util.Random();

        // LEFT o U_TURN: carril izquierdo
        if (direction == Direction.LEFT || direction == Direction.U_TURN) {
            return goingWest ? WEST_LEFT : EAST_LEFT;
        }

        // RIGHT: carril derecho
        if (direction == Direction.RIGHT) {
            return goingWest ? WEST_RIGHT : EAST_RIGHT;
        }

        // STRAIGHT u otros: cualquier carril
        int laneNumber = random.nextInt(3) + 1;

        if (goingWest) {
            return switch (laneNumber) {
                case 1 -> WEST_LEFT;
                case 2 -> WEST_CENTER;
                case 3 -> WEST_RIGHT;
                default -> WEST_CENTER;
            };
        } else {
            return switch (laneNumber) {
                case 1 -> EAST_LEFT;
                case 2 -> EAST_CENTER;
                case 3 -> EAST_RIGHT;
                default -> EAST_CENTER;
            };
        }
    }

    /**
     * Obtiene la posición de parada antes de una intersección
     * @param intersectionIndex índice de la intersección (0-3)
     * @return array con [x, y] de la posición de parada
     */
    public double[] getStopPosition(int intersectionIndex) {
        // Las intersecciones están en X=70, 390, 690, 1010
        double[] intersectionXs = {70, 390, 690, 1010};

        if (intersectionIndex < 0 || intersectionIndex > 3) {
            return new double[]{startX, startY};
        }

        double x = intersectionXs[intersectionIndex];

        // Ajustar posición de parada según dirección
        if (isWestbound()) {
            x += 90; // Parar 10px después del inicio de la intersección
        } else {
            x -= 10; // Parar 10px antes de la intersección
        }

        return new double[]{x, startY};
    }

    /**
     * Obtiene el carril correspondiente después de un cambio
     * @param targetLaneNumber número del carril destino
     * @return el HighwayLane correspondiente o null si no es válido
     */
    public HighwayLane getTargetLane(int targetLaneNumber) {
        if (targetLaneNumber < 1 || targetLaneNumber > 3) {
            return null;
        }

        String prefix = westbound ? "WEST_" : "EAST_";
        String suffix = switch (targetLaneNumber) {
            case 1 -> "LEFT";
            case 2 -> "CENTER";
            case 3 -> "RIGHT";
            default -> "";
        };

        try {
            return HighwayLane.valueOf(prefix + suffix);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Calcula la distancia Y para un cambio de carril
     * @param targetLaneNumber número del carril destino
     * @return distancia en Y a recorrer
     */
    public double getLaneChangeDistance(int targetLaneNumber) {
        HighwayLane targetLane = getTargetLane(targetLaneNumber);
        if (targetLane == null) {
            return 0;
        }
        return targetLane.getStartY() - this.startY;
    }
}