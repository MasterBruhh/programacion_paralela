package edu.pucmm.trafico;

import java.util.List;

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
