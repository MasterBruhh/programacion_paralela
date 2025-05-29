package edu.pucmm;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Generando datos...");
        Generador.generarArchivo("numeros.txt", 1_000_000);

        System.out.println("\nEjecutando suma secuencial...");
        SumaSecuencial.ejecutar("numeros.txt");

        System.out.println("\nEjecutando suma paralela con 2 hilos...");
        SumaParalela.ejecutar("numeros.txt", 2);

        System.out.println("\nEjecutando suma paralela con 4 hilos...");
        SumaParalela.ejecutar("numeros.txt", 4);

        System.out.println("\nEjecutando suma paralela con 8 hilos...");
        SumaParalela.ejecutar("numeros.txt", 8);

        System.out.println("\nEjecutando suma paralela con 16 hilos...");
        SumaParalela.ejecutar("numeros.txt", 16);

        System.out.println("\nEjecutando suma paralela con 32 hilos...");
        SumaParalela.ejecutar("numeros.txt", 32);
    }
}