package edu.pucmm;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author me@fredpena.dev
 * @created 02/06/2025  - 20:46
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

        // Iniciar los productores y consumidores
        for (int i = 0; i < PRODUCER_COUNT; i++) {
            productor.execute(new Producer(queue));
        }
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            consumidor.execute(new Consumer(queue));
        }
        productor.shutdown(); // No aceptar más tareas, pero completar las existentes
        consumidor.shutdown(); // No aceptar más tareas, pero completar las existentes
        try {
            if (!productor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                productor.shutdownNow(); // Forzar el cierre si no termina en el tiempo especificado
            }
            if (!consumidor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                consumidor.shutdownNow(); // Forzar el cierre si no termina en el tiempo especificado
            }
        } catch (InterruptedException e) {
            productor.shutdownNow();
            consumidor.shutdownNow();
            System.err.println("Error al esperar la finalización de los hilos: " + e.getMessage());
        }
        System.out.println("Todos los productores y consumidores han terminado.");
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
            try {
                for (int i = 0; i < PRODUCE_COUNT; i++) {
                    int number = (int) (Math.random() * 100); // Generar número aleatorio
                    queue.put(number); // Colocar en la cola
                    System.out.println("Productor " + Thread.currentThread().getName() + " produjo: " + number);
                    Thread.sleep(10); // Simular tiempo de producción
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaurar el estado de interrupción
                System.err.println("Productor interrumpido: " + e.getMessage());
            }
            System.out.println("Productor " + Thread.currentThread().getName() + " ha terminado de producir.");
            try {
                // Asegurarse de que la cola esté vacía al finalizar
                while (!queue.isEmpty()) {
                    queue.take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaurar el estado de interrupción
                System.err.println("Error al limpiar la cola: " + e.getMessage());
            }
            System.out.println("Productor " + Thread.currentThread().getName() + " ha limpiado la cola.");
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
            int sum = 0;
            try {
                for (int i = 0; i < PRODUCE_COUNT; i++) {
                    Integer number = queue.take(); // Extraer de la cola
                    sum += number; // Sumar el número
                    System.out.println("Consumidor " + Thread.currentThread().getName() + " consumio: " + number);
                    Thread.sleep(10); // Simular tiempo de procesamiento
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaurar el estado de interrupción
                System.err.println("Consumidor interrumpido: " + e.getMessage());
            }
            System.out.println("Consumidor " + Thread.currentThread().getName() + " ha terminado de consumir. Suma total: " + sum);
            try {
                // Asegurarse de que la cola esté vacía al finalizar
                while (!queue.isEmpty()) {
                    queue.take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaurar el estado de interrupción
                System.err.println("Error al limpiar la cola: " + e.getMessage());
            }
            System.out.println("Consumidor " + Thread.currentThread().getName() + " ha limpiado la cola.");
        }
    }
}