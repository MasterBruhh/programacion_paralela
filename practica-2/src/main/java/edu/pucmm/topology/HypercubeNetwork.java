package edu.pucmm.topology;

import java.util.*;
import java.util.concurrent.*;
import edu.pucmm.core.Message;
import edu.pucmm.core.Node;

public class HypercubeNetwork implements NetworkTopology {

    private final Map<Integer, Node> nodes = new HashMap<>();
    private ExecutorService executor;

    @Override
    public void configureNetwork(int numberOfNodes) {
        int dimension = (int) (Math.log(numberOfNodes) / Math.log(2));
        if ((1 << dimension) != numberOfNodes) {
            throw new IllegalArgumentException("Hypercube requiere 2^d nodos. Ej: 2, 4, 8, 16, etc.");
        }

        // Crear nodos
        for (int i = 0; i < numberOfNodes; i++) {
            nodes.put(i, new Node(i));
        }

        // Conectar nodos que difieren en un solo bit
        for (int i = 0; i < numberOfNodes; i++) {
            for (int j = i + 1; j < numberOfNodes; j++) {
                if (Integer.bitCount(i ^ j) == 1) {
                    nodes.get(i).addNeighbor(nodes.get(j));
                    nodes.get(j).addNeighbor(nodes.get(i));
                }
            }
        }
    }

    @Override
    public void sendMessage(int sourceId, int destinationId, String message) {
        if (!nodes.containsKey(sourceId) || !nodes.containsKey(destinationId)) {
            System.out.println("Nodo inválido.");
            return;
        }
        nodes.get(destinationId).receiveMessage(new Message(sourceId, destinationId, message,System.currentTimeMillis()));
    }

    @Override
    public void startSimulation() {
        executor = Executors.newFixedThreadPool(nodes.size());
        for (Node node : nodes.values()) {
            executor.submit(node);
        }
    }

    @Override
    public void printNetworkState() {
        System.out.println("Topología Hypercube:");
        for (Node node : nodes.values()) {
            System.out.println("Nodo " + node.getId() + " conectado a: " + node.getNeighborIds());
        }
    }
}
