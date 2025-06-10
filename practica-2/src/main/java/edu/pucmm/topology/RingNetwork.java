package edu.pucmm.topology;

import edu.pucmm.core.Node;
import edu.pucmm.core.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RingNetwork implements NetworkTopology {

    private final List<Node> nodes = new ArrayList<>();
    private ExecutorService executor;

    @Override
    public void configureNetwork(int numberOfNodes) {
        for (int i = 0; i < numberOfNodes; i++) {
            Node node = new Node(i);
            nodes.add(node);
        }

        // Establecer vecinos en forma de anillo (cada nodo conoce al siguiente)
        for (int i = 0; i < nodes.size(); i++) {
            Node current = nodes.get(i);
            Node next = nodes.get((i + 1) % nodes.size()); // circular
            current.setNeighbor(next); // Este metodo se implementará en Node
        }
    }

    @Override
    public void sendMessage(int sourceId, int destinationId, String messageContent) {
        Message message = new Message(sourceId, destinationId, messageContent,System.currentTimeMillis());
        nodes.get(sourceId).receiveMessage(message); // El origen inicia el envío
    }

    @Override
    public void startSimulation() {
        executor = Executors.newFixedThreadPool(nodes.size());
        for (Node node : nodes) {
            executor.execute(node);
        }
    }

    @Override
    public void printNetworkState() {
        System.out.println("Ring Network con " + nodes.size() + " nodos.");
        for (Node node : nodes) {
            System.out.println("Nodo: " + node.getId() + " -> Vecino: " + node.getNeighbor().getId());
        }
    }
}
