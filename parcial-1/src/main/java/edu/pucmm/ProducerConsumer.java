package edu.pucmm;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author me@fredpena.dev
 * @created 02/06/2025  - 20:46
 */

/**
 *
 * Implementar el patron clasico de Productor/Consumidor usando BlockingQueue.
 *
 * Requerimientos
 * Crear una cola bloqueante con capacidad limitada (ArrayBlockingQueue).
 * Implementar n productores que generen números aleatorios y los coloquen en la cola.
 * Implementar n consumidores que extraigan y procesen los números de la cola (ej. sumarlos).
 * Ejecutar el sistema con múltiples productores y consumidores simultáneos.
 * Medir el tiempo total de procesamiento.
 * Mostrar cuántos elementos consumió cada hilo consumidor.
 */
public class ProducerConsumer {
    private static final int QUEUE_CAPACITY = 10;
    private static final int PRODUCER_COUNT = 2;
    private static final int CONSUMER_COUNT = 2;
    private static final int PRODUCE_COUNT = 100;

    public static void main(String[] args) {
        // Sugerencia: Usar ExecutorService o crear threads manualmente para iniciar Productores y Consumidores
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        ExecutorService productor = Executors.newFixedThreadPool(PRODUCER_COUNT);
        ExecutorService consumidor = Executors.newFixedThreadPool(CONSUMER_COUNT);

        try {
            for (;;) {
                // Crear instancias de Productor y Consumidor
                Producer producer = new Producer(queue);
                Consumer consumer = new Consumer(queue);

                // Enviar tareas al executor
                productor.execute(producer);
                consumidor.execute(consumer);
            }
        } catch (Exception ex) {
            System.err.println("Error al iniciar los hilos: " + ex.getMessage());
        } finally {
            productor.shutdown();
            consumidor.shutdown();
        }
    }

    static class Producer implements Runnable {
        private final BlockingQueue<Integer> queue;
        Producer (BlockingQueue<Integer> queue) {
            this.queue = queue;
        }
        @Override
        public void run() {
            // Generar PRODUCE_COUNT números aleatorios y colocarlos en la cola
            // Sugerencia: usar Thread.sleep(10) para simular tiempo de producción

            for (int i = 0; i< PRODUCE_COUNT; i++) {
                try {
                    int number = (int) (Math.random() * 100); // Generar número aleatorio
                    queue.put(number); // Colocar en la cola
                    System.out.println("Productor " + Thread.currentThread().getName() + " produjo: " + number);
                    Thread.sleep(10); // Simular tiempo de producción
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restaurar el estado de interrupción
                    System.err.println("Productor interrumpido: " + e.getMessage());
                }
            }
            System.out.println("Productor " + Thread.currentThread().getName() + " ha terminado de producir.");
        }
    }

    static class Consumer implements Runnable {
        private final BlockingQueue<Integer> queue;
        Consumer (BlockingQueue<Integer> queue) {
            this.queue = queue;
        }
        @Override
        public void run() {
            // Extraer elementos de la cola y procesarlos (ej: sumarlos)
            // Sugerencia: llevar la suma total por hilo y reportar al final
        }
    }
}