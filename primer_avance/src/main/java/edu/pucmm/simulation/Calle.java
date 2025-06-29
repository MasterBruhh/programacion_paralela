package edu.pucmm.simulation;

import java.util.List;

/**
 * representa una calle con múltiples intersecciones.
 */
public class Calle {
    private final String nombre;
    private final List<Interseccion> intersecciones;

    public Calle(String nombre, List<Interseccion> intersecciones) {
        this.nombre = nombre;
        this.intersecciones = intersecciones;
    }

    public String getNombre() {
        return nombre;
    }

    public List<Interseccion> getIntersecciones() {
        return intersecciones;
    }
} 