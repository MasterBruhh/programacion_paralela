package edu.pucmm;

import java.util.List;

public class Sumador extends Thread {
    private final List<String> datos;
    private long suma = 0;

    public Sumador(List<String> datos) {
        this.datos = datos;
    }

    public void run() {
        for (String s : datos) {
            suma += Integer.parseInt(s);
        }
    }

    public long getSuma() {
        return suma;
    }
}
