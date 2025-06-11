package edu.pucmm.topology;

import edu.pucmm.core.Message;
import edu.pucmm.core.Node;

import java.util.*;
import java.util.concurrent.*;

public class StarNetwork implements NetworkTopology {

    private final Map<Integer, Node> nodes = new HashMap<>();
    private Node centralNode;
    private ExecutorService executor;

    @Override
    public void configureNetwork(int numberOfNodes) {
        // Crear los nodos
        for (int i = 0; i < numberOfNodes; i++) {
            nodes.put(i, new Node(i));
        }

        // Asignar el nodo central (por ejemplo, el nodo 0)
        Random random = new Random();
        int rand = random.nextInt(nodes.size()); // Genera un índice aleatorio entre 0 (inclusive) y nodes.size() (exclusivo)
        centralNode = nodes.get(rand);


        // Conectar cada nodo al nodo central (estrella)
        for (int i = 0; i < numberOfNodes; i++) {
            if(i==rand) {
                continue;
            }
            Node node = nodes.get(i);
            node.addNeighbor(centralNode);
            centralNode.addNeighbor(node);
        }
    }

    @Override
    public void sendMessage(int sourceId, int destinationId, String message) {
        if (!nodes.containsKey(sourceId) || !nodes.containsKey(destinationId)) {
            System.out.println("Nodo no válido.");
            return;
        }

        // Enviar a través del nodo central
        Node source = nodes.get(sourceId);
        Node destination = nodes.get(destinationId);

        if (sourceId != centralNode.getId()) {
            centralNode.receiveMessage(new Message(sourceId, centralNode.getId(), "[Nodo Central] -> " + message,System.currentTimeMillis()));
        }

        if (destinationId != centralNode.getId()) {
            destination.receiveMessage(new Message(centralNode.getId(), destinationId, message,System.currentTimeMillis()));
        }
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
        System.out.println("Topología en Estrella:");
        System.out.println("Nodo central: " + centralNode.getId());
        for (Node node : nodes.values()) {
            System.out.println("Nodo " + node.getId() + " conectado a: " +
                    node.getNeighborIds());
        }
    }
}
