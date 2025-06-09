package edu.pucmm.topology;

import edu.pucmm.core.Node;
import edu.pucmm.core.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BusNetwork implements NetworkTopology {

    private final List<Node> nodes = new ArrayList<>();
    private ExecutorService executor;

    @Override
    public void configureNetwork(int numberOfNodes) {
        for (int i = 0; i < numberOfNodes; i++) {
            Node node = new Node(i);
            nodes.add(node);
        }
    }

    @Override
    public void sendMessage(int sourceId, int destinationId, String messageContent) {
        Message message = new Message(sourceId, destinationId, messageContent);

        // En un bus, el mensaje se transmite a todos, y solo el destino lo procesa
        for (Node node : nodes) {
            node.receiveMessage(message);
        }
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
        System.out.println("Bus Network con " + nodes.size() + " nodos.");
        for (Node node : nodes) {
            System.out.println("Nodo: " + node.getId());
        }
    }
}
