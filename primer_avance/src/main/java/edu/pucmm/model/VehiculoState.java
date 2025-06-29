package edu.pucmm.model;

/**
 * Estado inmutable de un vehículo en la simulación.
 * Esta clase es thread-safe por diseño al ser inmutable.
 */
public record VehiculoState(
    String id,
    double posX,
    double posY,
    TipoVehiculo tipo
) {
    /**
     * Constructor con validación de parámetros.
     */
    public VehiculoState {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id no puede ser null o vacío");
        }
        if (tipo == null) {
            throw new IllegalArgumentException("tipo no puede ser null");
        }
    }

    @Override
    public String toString() {
        return String.format("vehiculoState{id='%s', pos=(%.2f, %.2f), tipo=%s}", 
                           id, posX, posY, tipo);
    }
} 