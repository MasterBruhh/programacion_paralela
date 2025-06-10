package edu.pucmm.topology;

import edu.pucmm.core.Node;
import edu.pucmm.core.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TreeNetwork implements NetworkTopology {

    private final List<Node> nodes = new ArrayList<>();
    private ExecutorService executor;

    @Override
    public void configureNetwork(int numberOfNodes) {
        for (int i = 0; i < numberOfNodes; i++) {
            nodes.add(new Node(i));
        }

        // Conexiones de árbol binario (cada nodo tiene hijos en 2i+1 y 2i+2)
        for (int i = 0; i < numberOfNodes; i++) {
            Node parent = nodes.get(i);
            int leftIndex = 2 * i + 1;
            int rightIndex = 2 * i + 2;

            if (leftIndex < numberOfNodes) {
                parent.addChild(nodes.get(leftIndex));
                nodes.get(leftIndex).setParent(parent);
            }
            if (rightIndex < numberOfNodes) {
                parent.addChild(nodes.get(rightIndex));
                nodes.get(rightIndex).setParent(parent);
            }
        }
    }

    @Override
    public void sendMessage(int sourceId, int destinationId, String messageContent) {
        Message message = new Message(sourceId, destinationId, messageContent,System.currentTimeMillis());
        nodes.get(sourceId).receiveMessage(message);
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
        System.out.println("Tree Network con " + nodes.size() + " nodos:");
        for (Node node : nodes) {
            String parent = node.getParent() != null ? String.valueOf(node.getParent().getId()) : "null";
            StringBuilder children = new StringBuilder();
            for (Node child : node.getChildren()) {
                children.append(child.getId()).append(" ");
            }
            System.out.println("Nodo " + node.getId() + " -> Padre: " + parent + ", Hijos: " + children);
        }
    }
}
