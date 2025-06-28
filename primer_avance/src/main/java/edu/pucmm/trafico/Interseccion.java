package edu.pucmm.trafico;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;

public class Interseccion {
    private final String id;
    private final Semaphore acceso;
    private final PriorityBlockingQueue<Vehiculo> colaVehiculos;

    public Interseccion(String id) {
        this.id = id;
        this.acceso = new Semaphore(1, true); // FIFO: primero en llegar, primero en cruzar
        this.colaVehiculos = new PriorityBlockingQueue<>(100, (v1, v2) -> {
            if (v1.getTipo() == TipoVehiculo.EMERGENCIA && v2.getTipo() != TipoVehiculo.EMERGENCIA) return -1;
            if (v1.getTipo() != TipoVehiculo.EMERGENCIA && v2.getTipo() == TipoVehiculo.EMERGENCIA) return 1;
            return 0;
        });
    }

    public String getId() {
        return id;
    }

    public void agregarVehiculo(Vehiculo vehiculo) {
        colaVehiculos.offer(vehiculo);
    }

    public void cruzar() throws InterruptedException {
        acceso.acquire();
        try {
            // lógica de dirección, tipo, etc.
            Thread.sleep(1000); // tiempo de cruce simulado
        } finally {
            acceso.release();
        }
    }

    public Semaphore getAcceso() {
        return acceso;
    }

    public PriorityBlockingQueue<Vehiculo> getColaVehiculos() {
        return colaVehiculos;
    }
}