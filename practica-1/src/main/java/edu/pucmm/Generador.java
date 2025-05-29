package edu.pucmm;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class Generador {
    public static void generarArchivo(String nombreArchivo, int cantidad) {
        try (FileWriter writer = new FileWriter(nombreArchivo)) {
            Random rand = new Random();
            for (int i = 0; i < cantidad; i++) {
                int numero = rand.nextInt(10_000) + 1;
                writer.write(numero + "\n");
            }
            System.out.println("Archivo generado con exito: " + nombreArchivo);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
