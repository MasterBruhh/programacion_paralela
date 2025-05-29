package edu.pucmm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class SumaSecuencial {
    public static void ejecutar(String archivo) throws IOException {
        List<String> lineas = Files.readAllLines(Paths.get(archivo));
        long inicio = System.nanoTime();
        long suma = 0;
        for (String linea : lineas) {
            suma += Integer.parseInt(linea);
        }
        long fin = System.nanoTime();
        System.out.println("Suma secuencial: " + suma);
        System.out.println("Tiempo (s): " + (fin - inicio) / 1_000_000_000.0);
    }
}
