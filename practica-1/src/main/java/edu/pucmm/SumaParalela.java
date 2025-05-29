package edu.pucmm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SumaParalela {
    public static void ejecutar(String archivo, int hilos) throws IOException, InterruptedException {
        List<String> lineas = Files.readAllLines(Paths.get(archivo));
        int tamano = lineas.size() / hilos;

        long inicio = System.nanoTime();
        List<Sumador> sumadores = new ArrayList<>();

        for (int i = 0; i < hilos; i++) {
            int inicioIdx = i * tamano;
            int finIdx = (i == hilos - 1) ? lineas.size() : (i + 1) * tamano;
            Sumador t = new Sumador(lineas.subList(inicioIdx, finIdx));
            sumadores.add(t);
            t.start();
        }

        long total = 0;
        for (Sumador s : sumadores) {
            s.join();
            total += s.getSuma();
        }
        long fin = System.nanoTime();
        System.out.println("Suma paralela: " + total);
        System.out.println("Tiempo (s): " + (fin - inicio) / 1_000_000_000.0);
    }
}

