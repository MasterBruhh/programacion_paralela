package edu.pucmm.trafico;

public class Semaforo {
    private EstadoSemaforo estado;

    public Semaforo() {
        this.estado = EstadoSemaforo.ROJO;
    }

    public synchronized EstadoSemaforo getEstado() {
        return estado;
    }

    public synchronized void cambiarEstado(EstadoSemaforo nuevoEstado) {
        this.estado = nuevoEstado;
    }
}

