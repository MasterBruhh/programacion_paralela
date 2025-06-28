package edu.pucmm.trafico;

public class Vehiculo {
    private final String id;
    private final TipoVehiculo tipo;
    private final Direccion direccion;

    public Vehiculo(String id, TipoVehiculo tipo, Direccion direccion) {
        this.id = id;
        this.tipo = tipo;
        this.direccion = direccion;
    }

    public String getId() {
        return id;
    }

    public TipoVehiculo getTipo() {
        return tipo;
    }

    public Direccion getDireccion() {
        return direccion;
    }
}
