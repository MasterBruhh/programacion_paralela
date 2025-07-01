package edu.pucmm.simulation;

import edu.pucmm.model.TipoVehiculo;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.Semaphore;

/**
 * representa una intersección en el sistema de tráfico.
 * maneja la cola de vehículos y control de acceso usando semáforos.
 */
public class Interseccion {
    private final String id;
    private final Semaphore acceso;
    private final PriorityBlockingQueue<Vehiculo> colaVehiculos;

    public Interseccion(String id) {
        this.id = id;
        this.acceso = new Semaphore(1, true); // fifo: primero en llegar, primero en cruzar
        this.colaVehiculos = new PriorityBlockingQueue<>(100, (v1, v2) -> {
            // vehículos de emergencia tienen prioridad
            if (v1.getTipo() == TipoVehiculo.emergencia && v2.getTipo() != TipoVehiculo.emergencia) return -1;
            if (v1.getTipo() != TipoVehiculo.emergencia && v2.getTipo() == TipoVehiculo.emergencia) return 1;
            return 0; // mismo tipo: orden de llegada (fifo por el semaphore)
        });
    }

    public String getId() {
        return id;
    }

    public void agregarVehiculo(Vehiculo vehiculo) {
        colaVehiculos.offer(vehiculo);
    }

    /**
     * simula el cruce de un vehículo por la intersección.
     */
    public void cruzar() throws InterruptedException {
        acceso.acquire();
        try {
            // simulación del tiempo de cruce
            Thread.sleep(1000);
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