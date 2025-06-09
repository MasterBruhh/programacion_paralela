package edu.pucmm.topology;

import edu.pucmm.core.Node;
import edu.pucmm.core.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FullyConnectedNetwork implements NetworkTopology {

    private final List<Node> nodes = new ArrayList<>();
    private ExecutorService executor;

    @Override
    public void configureNetwork(int numberOfNodes) {
        for (int i = 0; i < numberOfNodes; i++) {
            nodes.add(new Node(i));
        }

        // Todos los nodos conocen a los demás
        for (Node node : nodes) {
            for (Node other : nodes) {
                if (node.getId() != other.getId()) {
                    node.addNeighbor(other); // metodo que agregaremos en Node
                }
            }
        }
    }

    @Override
    public void sendMessage(int sourceId, int destinationId, String messageContent) {
        Message message = new Message(sourceId, destinationId, messageContent);
        nodes.get(sourceId).receiveMessage(message); // envío directo desde el origen
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
        System.out.println("Fully Connected Network con " + nodes.size() + " nodos.");
        for (Node node : nodes) {
            StringBuilder neighbors = new StringBuilder();
            for (Node neighbor : node.getNeighbors()) {
                neighbors.append(neighbor.getId()).append(" ");
            }
            System.out.println("Nodo " + node.getId() + " -> Vecinos: " + neighbors);
        }
    }
}
