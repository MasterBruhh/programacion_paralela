package edu.pucmm;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

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
        BlockingQueue<Integer> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

        // Sugerencia: Usar ExecutorService o crear threads manualmente para iniciar Productores y Consumidores
    }

    static class Producer implements Runnable {
        @Override
        public void run() {
            // Generar PRODUCE_COUNT números aleatorios y colocarlos en la cola
            // Sugerencia: usar Thread.sleep(10) para simular tiempo de producción
        }
    }

    static class Consumer implements Runnable {
        @Override
        public void run() {
            // Extraer elementos de la cola y procesarlos (ej: sumarlos)
            // Sugerencia: llevar la suma total por hilo y reportar al final
        }
    }
}