package edu.pucmm;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        System.out.println("Generando datos...");
        Generador.generarArchivo("numeros.txt", 1_000_000);
    }
}