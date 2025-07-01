package edu.pucmm.simulation;

/**
 * estados posibles de un semáforo.
 */
enum EstadoSemaforo {
    verde,
    amarillo,
    rojo
}

/**
 * representa un semáforo en el sistema de tráfico.
 */
public class Semaforo {
    private EstadoSemaforo estado;

    public Semaforo() {
        this.estado = EstadoSemaforo.rojo;
    }

    public synchronized EstadoSemaforo getEstado() {
        return estado;
    }

    public synchronized void cambiarEstado(EstadoSemaforo nuevoEstado) {
        this.estado = nuevoEstado;
    }
} 