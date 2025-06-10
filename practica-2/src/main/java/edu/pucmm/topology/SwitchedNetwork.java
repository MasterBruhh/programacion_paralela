package edu.pucmm.topology;

import edu.pucmm.core.Message;
import edu.pucmm.core.Node;
import java.util.*;
import java.util.concurrent.*;

public class SwitchedNetwork implements NetworkTopology {

    private final Map<Integer, Node> nodes = new HashMap<>();
    private final BlockingQueue<Message> switchQueue = new LinkedBlockingQueue<>();
    private ExecutorService executor;

    @Override
    public void configureNetwork(int numberOfNodes) {
        for (int i = 0; i < numberOfNodes; i++) {
            nodes.put(i, new Node(i));
        }

        // Switch no necesita conexión física, solo acceso a la cola
    }

    @Override
    public void sendMessage(int sourceId, int destinationId, String message) {
        if (!nodes.containsKey(sourceId) || !nodes.containsKey(destinationId)) {
            System.out.println("Nodo inválido.");
            return;
        }
        switchQueue.offer(new Message(sourceId, destinationId, message,System.currentTimeMillis()));
    }

    @Override
    public void startSimulation() {
        executor = Executors.newFixedThreadPool(nodes.size() + 1); // +1 para el switch

        // Ejecutar nodos normalmente
        for (Node node : nodes.values()) {
            executor.submit(node);
        }

        // Hilo especial para el switch
        executor.submit(() -> {
            while (true) {
                try {
                    Message msg = switchQueue.take();
                    Node destination = nodes.get(msg.getDestinationId());
                    if (destination != null) {
                        destination.receiveMessage(msg);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @Override
    public void printNetworkState() {
        System.out.println("Topología Switched:");
        for (Node node : nodes.values()) {
            System.out.println("Nodo " + node.getId() + " conectado al Switch virtual");
        }
    }
}
