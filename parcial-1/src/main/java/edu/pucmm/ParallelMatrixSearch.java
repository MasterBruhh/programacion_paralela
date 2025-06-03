package edu.pucmm;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author me@fredpena.dev
 * @created 02/06/2025  - 20:46
 */
public class ParallelMatrixSearch {

    private static final int MATRIX_SIZE = 1000;
    private static final int THREAD_COUNT = 4;
    private static final int[][] matrix = new int[MATRIX_SIZE][MATRIX_SIZE];
    private static final int TARGET = 256; // Número a buscar

    public static void main(String[] args) {
        // Inicializar la matriz con valores aleatorios
        fillMatrixRandom();

        // Medir el tiempo de ejecución de la búsqueda secuencial
        long startTime = System.nanoTime();
        sequentialSearch();
        long endTime = System.nanoTime();
        System.out.println("Tiempo busqueda secuencial: " + ((endTime - startTime) / 1_000_000) + "ms");

        // Medir el tiempo de ejecución de la búsqueda paralela
        startTime = System.nanoTime();
        parallelSearch();
        endTime = System.nanoTime();
        System.out.println("Tiempo busqueda paralela: " + ((endTime - startTime) / 1_000_000) + "ms");

        // Imprimir la matriz (opcional)
        // printMatrix();
    }

    private static void sequentialSearch() {
        // Implementar búsqueda secuencial
        boolean found = false;
        for (int row = 0; row < MATRIX_SIZE && !found; row++) {
            for (int col = 0; col < MATRIX_SIZE && !found; col++) {
                if (matrix[row][col] == TARGET) {
                    found = true;
                    System.out.println("Numero encontrado en la fila " + row + ", columna " + col);
                }
            }
        }
        if (!found) {
            System.out.println("Numero no encontrado en la matriz.");
        }
        System.out.println("Busqueda secuencial completada.");
    }

    private static void parallelSearch() {
        // Implementar búsqueda paralela
        // Sugerencia: usar AtomicBoolean para indicar si ya se encontró el número y detener hilos
        AtomicBoolean found = new AtomicBoolean(false);
        Thread[] threads = new Thread[THREAD_COUNT];
        int rowsPerThread = MATRIX_SIZE / THREAD_COUNT;
        for (int i = 0; i < THREAD_COUNT; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                int startRow = threadIndex * rowsPerThread;
                int endRow = (threadIndex == THREAD_COUNT - 1) ? MATRIX_SIZE : startRow + rowsPerThread;

                for (int row = startRow; row < endRow && !found.get(); row++) {
                    for (int col = 0; col < MATRIX_SIZE && !found.get(); col++) {
                        if (matrix[row][col] == TARGET) {
                            found.set(true);
                            System.out.println("Numero encontrado en la fila " + row + ", columna " + col);
                        }
                    }
                }
            });
            threads[i].start();
        }
    }

    private static void fillMatrixRandom() {
        Random rand = new Random();
        for (int i = 0; i < MATRIX_SIZE; i++) {
            for (int j = 0; j < MATRIX_SIZE; j++) {
                matrix[i][j] = rand.nextInt(1000); // Rango arbitrario
            }
        }
    }

    private static void printMatrix() {
        for (int i = 0; i < MATRIX_SIZE; i++) {
            for (int j = 0; j < MATRIX_SIZE; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.println();
        }
    }
}