package edu.pucmm;

import edu.pucmm.core.NetworkManager;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        NetworkManager manager = new NetworkManager();

        System.out.println("Topologías disponibles: bus, ring, tree, fullyconnected, mesh, star, hypercube, switched");
        System.out.print("Selecciona una topología: ");
        String tipo = scanner.nextLine();

        System.out.print("Número de nodos: ");
        int nodos = scanner.nextInt();

        manager.setupTopology(tipo, nodos);
        manager.showState();

        manager.startSimulation();

        System.out.print("ID del nodo origen: ");
        int origen = scanner.nextInt();

        System.out.print("ID del nodo destino: ");
        int destino = scanner.nextInt();
        scanner.nextLine(); // consumir newline

        System.out.print("Mensaje a enviar: ");
        String mensaje = scanner.nextLine();

        manager.sendMessage(origen, destino, mensaje);

        // Esperar para que los hilos impriman los mensajes
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.exit(0); // Finaliza el programa y sus hilos
    }
}
